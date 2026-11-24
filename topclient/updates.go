package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Launcher updates. New versions are published as GitHub Releases in the update repo
// (Settings → Advanced → Update source, e.g. "yourname/TopClient"). Each release
// carries TopClient.exe and TopClient.exe.sha256. The launcher checks the latest
// release, and "Update launcher" downloads only the new exe, checks it, swaps it in and
// restarts. Profiles, worlds, accounts and settings are untouched.

var repoRe = regexp.MustCompile(`^[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}$`)

type releaseInfo struct {
	Version, Notes, ExeURL, ShaURL, Page string
	ExeAPI, ShaAPI                       string // same files via api.github.com (other servers)
	ExeSHA                               string // SHA-256 GitHub reports for the exe (no extra download needed)
	Published                            string
}

var upd struct {
	mu      sync.Mutex
	checked time.Time
	latest  *releaseInfo
	err     string
	busy    bool
}

func updateRepo() string {
	r := strings.TrimSpace(settingString("updateRepo"))
	if r == "" {
		r = defaultUpdateRepo
	}
	r = strings.TrimSuffix(strings.TrimPrefix(strings.TrimPrefix(r, "https://github.com/"), "github.com/"), "/")
	if !repoRe.MatchString(r) {
		return ""
	}
	return r
}

// defaultUpdateRepo is where updates come from unless the player picks another repo.
const defaultUpdateRepo = "TopAmir07/Client-Release" // where TopClient looks for new launcher versions (GitHub Releases)

func parseVer(v string) []int {
	v = strings.TrimPrefix(strings.TrimSpace(v), "v")
	var out []int
	for _, p := range strings.Split(v, ".") {
		n, err := strconv.Atoi(strings.TrimFunc(p, func(r rune) bool { return r < '0' || r > '9' }))
		if err != nil {
			n = 0
		}
		out = append(out, n)
	}
	return out
}

func newerThan(a, b string) bool {
	x, y := parseVer(a), parseVer(b)
	for i := 0; i < len(x) || i < len(y); i++ {
		var p, q int
		if i < len(x) {
			p = x[i]
		}
		if i < len(y) {
			q = y[i]
		}
		if p != q {
			return p > q
		}
	}
	return false
}

// fetchLatestRelease asks GitHub's API for the newest release (with a User-Agent and a 10 s
// timeout). The API only allows 60 requests an hour per internet connection, so updateStatus
// caches the answer for 30 minutes. If the API says no (for example 403 when that allowance is
// used up), the github.com "latest" page is tried instead, which has no such limit.
func fetchLatestRelease(repo string) (*releaseInfo, error) {
	ri, apiErr := fetchLatestFromAPI(repo)
	if apiErr == nil {
		return ri, nil
	}
	if strings.Contains(apiErr.Error(), "no releases found") {
		return nil, apiErr
	}
	logf("update check via the GitHub API: %v (trying github.com)", apiErr)
	ri, err := fetchLatestFromWeb(repo)
	if err == nil {
		return ri, nil
	}
	if strings.Contains(apiErr.Error(), "403") {
		return nil, errors.New("GitHub is busy right now (too many checks from your internet connection). Try again in a few minutes")
	}
	return nil, apiErr
}

var tagFromURL = regexp.MustCompile(`/releases/tag/([^/?#]+)$`)

func fetchLatestFromWeb(repo string) (*releaseInfo, error) {
	c := &http.Client{Transport: netClient.Transport, Timeout: 20 * time.Second,
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	req, _ := http.NewRequest("GET", "https://github.com/"+repo+"/releases/latest", nil)
	req.Header.Set("User-Agent", "TopClient-Launcher/"+AppVersion)
	resp, err := c.Do(req)
	if err != nil {
		return nil, err
	}
	io.Copy(io.Discard, io.LimitReader(resp.Body, 1<<20))
	resp.Body.Close()
	loc := resp.Header.Get("Location")
	m := tagFromURL.FindStringSubmatch(loc)
	if resp.StatusCode < 300 || resp.StatusCode >= 400 || m == nil {
		if resp.StatusCode == 404 {
			return nil, errors.New("no releases found in " + repo)
		}
		return nil, fmt.Errorf("github.com said %s", resp.Status)
	}
	tag, _ := url.PathUnescape(m[1])
	if !regexp.MustCompile(`^[A-Za-z0-9._-]{1,40}$`).MatchString(tag) {
		return nil, errors.New("unexpected release name")
	}
	base := "https://github.com/" + repo + "/releases/download/" + tag + "/"
	ri := &releaseInfo{Version: strings.TrimPrefix(tag, "v"), Page: "https://github.com/" + repo + "/releases/tag/" + tag,
		ExeURL: base + "TopClient.exe", ShaURL: base + "TopClient.exe.sha256"}
	return ri, nil
}

func fetchLatestFromAPI(repo string) (*releaseInfo, error) {
	req, _ := http.NewRequest("GET", "https://api.github.com/repos/"+repo+"/releases/latest", nil)
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "TopClient-Launcher/"+AppVersion)
	c := &http.Client{Transport: netClient.Transport, Timeout: 10 * time.Second}
	resp, err := c.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == 404 {
		return nil, errors.New("no releases found in " + repo)
	}
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("GitHub said %s", resp.Status)
	}
	var r struct {
		Tag       string `json:"tag_name"`
		Body      string `json:"body"`
		HTMLURL   string `json:"html_url"`
		Published string `json:"published_at"`
		Assets    []struct {
			Name   string `json:"name"`
			URL    string `json:"browser_download_url"`
			APIURL string `json:"url"`
			Digest string `json:"digest"` // "sha256:<hex>", worked out by GitHub itself
		} `json:"assets"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 4<<20)).Decode(&r); err != nil {
		return nil, err
	}
	ri := &releaseInfo{Version: strings.TrimPrefix(r.Tag, "v"), Notes: r.Body, Page: r.HTMLURL, Published: r.Published}
	// Any file ending in .exe is the launcher, any file ending in .sha256 is its checksum.
	// If there are several, the ones named TopClient.exe / TopClient.exe.sha256 win.
	for _, a := range r.Assets {
		n := strings.ToLower(a.Name)
		switch {
		case strings.HasSuffix(n, ".exe") && (ri.ExeURL == "" || n == "topclient.exe"):
			ri.ExeURL, ri.ExeAPI, ri.ExeSHA = a.URL, a.APIURL, ""
			if d := strings.ToLower(strings.TrimPrefix(a.Digest, "sha256:")); len(d) == 64 && a.Digest != d {
				ri.ExeSHA = d
			}
		case strings.HasSuffix(n, ".sha256") && (ri.ShaURL == "" || n == "topclient.exe.sha256"):
			ri.ShaURL, ri.ShaAPI = a.URL, a.APIURL
		}
	}
	return ri, nil
}

var (
	selfOnce sync.Once
	selfHash string
)

// selfSHA is the sha256 of the running TopClient.exe (worked out once).
func selfSHA() string {
	selfOnce.Do(func() {
		exe, err := os.Executable()
		if err != nil {
			return
		}
		f, err := os.Open(exe)
		if err != nil {
			return
		}
		defer f.Close()
		h := sha256.New()
		if _, err := io.Copy(h, f); err == nil {
			selfHash = hex.EncodeToString(h.Sum(nil))
		}
	})
	return selfHash
}

func updateStatus(force bool) map[string]any {
	repo := updateRepo()
	out := map[string]any{"current": AppVersion, "repo": repo, "supported": updateSupported()}
	if repo == "" {
		out["state"] = "unconfigured"
		return out
	}
	upd.mu.Lock()
	stale := force || time.Since(upd.checked) > 30*time.Minute
	upd.mu.Unlock()
	if stale {
		ri, err := fetchLatestRelease(repo)
		if err == nil && ri != nil && ri.ExeSHA == "" && ri.ShaURL != "" && newerThan(ri.Version, AppVersion) {
			if b, e := httpGetBytes(ri.ShaURL, 4096); e == nil { // fill in the release's hash from its .sha256 file
				if f := strings.Fields(string(b)); len(f) > 0 && len(f[0]) == 64 {
					ri.ExeSHA = strings.ToLower(f[0])
				}
			}
		}
		upd.mu.Lock()
		upd.checked = time.Now()
		upd.latest, upd.err = ri, ""
		if err != nil {
			upd.err = err.Error()
		}
		upd.mu.Unlock()
	}
	upd.mu.Lock()
	defer upd.mu.Unlock()
	out["busy"] = upd.busy
	if upd.err != "" {
		out["state"], out["error"] = "error", upd.err
		return out
	}
	if upd.latest == nil {
		out["state"] = "error"
		return out
	}
	out["latest"], out["notes"], out["page"], out["published"] = upd.latest.Version, upd.latest.Notes, upd.latest.Page, upd.latest.Published
	switch {
	case !newerThan(upd.latest.Version, AppVersion):
		out["state"] = "uptodate"
	case upd.latest.ExeSHA != "" && upd.latest.ExeSHA == selfSHA():
		// The release's exe is this exact file (its tag just says a different version):
		// nothing to install, so no endless "update available" loop.
		out["state"] = "uptodate"
	case upd.latest.ExeURL == "" || (upd.latest.ShaURL == "" && upd.latest.ExeSHA == ""):
		out["state"], out["error"] = "error", "the newest release is missing TopClient.exe or TopClient.exe.sha256"
	default:
		out["state"] = "available"
	}
	return out
}

func looksLikeExe(b []byte) bool {
	return len(b) > 2<<20 && len(b) < 300<<20 && bytes.HasPrefix(b, []byte("MZ"))
}

// downloadUpdate fetches the new exe and checks it against the release's SHA-256.
func downloadUpdate() (string, error) {
	upd.mu.Lock()
	ri := upd.latest
	upd.mu.Unlock()
	if ri == nil || ri.ExeURL == "" || (ri.ShaURL == "" && ri.ExeSHA == "") {
		return "", errors.New("no update to download")
	}
	// The checksum: GitHub's own SHA-256 of the uploaded exe when it reports one; otherwise the
	// TopClient.exe.sha256 file. When both exist they must agree.
	want := ri.ExeSHA
	if ri.ShaURL != "" {
		shaTxt, err := assetBytes(ri.ShaURL, ri.ShaAPI, 4096)
		switch {
		case err == nil:
			fileSum := strings.ToLower(strings.Fields(string(shaTxt) + " x")[0])
			if len(fileSum) != 64 {
				if want == "" {
					return "", errors.New("the release checksum file is not valid")
				}
			} else if want != "" && fileSum != want {
				return "", errors.New("the release's checksum file doesn't match the uploaded exe, so nothing was installed")
			} else {
				want = fileSum
			}
		case want == "":
			return "", fmt.Errorf("couldn't get the checksum (%v). GitHub may be busy: try again in a minute, or use Settings → Advanced → Update from file", err)
		}
	}
	task.set("Downloading launcher "+ri.Version, 0, 0)
	data, err := assetBytes(ri.ExeURL, ri.ExeAPI, 300<<20)
	if err != nil {
		return "", fmt.Errorf("couldn't download the update (%v). Try again in a minute, or use Settings → Advanced → Update from file", err)
	}
	sum := sha256.Sum256(data)
	if hex.EncodeToString(sum[:]) != want {
		return "", errors.New("the download didn't match its checksum, so it was thrown away")
	}
	if !looksLikeExe(data) {
		return "", errors.New("the download is not a Windows program")
	}
	dir := filepath.Join(dataDir, "updates")
	os.MkdirAll(dir, 0o755)
	path := filepath.Join(dir, "TopClient-"+cleanName(ri.Version)+".exe")
	return path, os.WriteFile(path, data, 0o755)
}

// assetBytes downloads a release file. GitHub's download servers sometimes answer 502/503/504,
// so it retries, and also tries GitHub's API address for the same file (a different server).
func assetBytes(browserURL, apiURL string, limit int64) ([]byte, error) {
	var last error
	for attempt := 0; attempt < 4; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt) * 2 * time.Second)
		}
		urls := []string{browserURL}
		if apiURL != "" {
			urls = append(urls, apiURL)
		}
		for _, u := range urls {
			if u == "" {
				continue
			}
			var b []byte
			var err error
			if u == apiURL {
				b, err = httpGetBytesAccept(u, "application/octet-stream", limit)
			} else {
				b, err = httpGetBytes(u, limit)
			}
			if err == nil {
				return b, nil
			}
			last = err
			if strings.Contains(err.Error(), "404") {
				break
			}
		}
	}
	return nil, last
}

func httpGetBytesAccept(u, accept string, limit int64) ([]byte, error) {
	req, err := http.NewRequest("GET", u, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", accept)
	resp, err := netClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("download failed: %s", resp.Status)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, limit+1))
	if int64(len(b)) > limit {
		return nil, errors.New("download too big")
	}
	return b, err
}

func httpGetBytes(u string, limit int64) ([]byte, error) {
	resp, err := netClient.Get(u)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("download failed: %s", resp.Status)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, limit+1))
	if int64(len(b)) > limit {
		return nil, errors.New("download too big")
	}
	return b, err
}

func updateRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/update", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, updateStatus(r.URL.Query().Get("force") == "1"))
	})
	mux.HandleFunc("POST /api/update/install", func(w http.ResponseWriter, r *http.Request) {
		if !updateSupported() {
			jsonErr(w, errors.New("updates work in the Windows app"))
			return
		}
		upd.mu.Lock()
		if upd.busy {
			upd.mu.Unlock()
			jsonErr(w, errors.New("already updating"))
			return
		}
		upd.busy = true
		upd.mu.Unlock()
		defer func() { upd.mu.Lock(); upd.busy = false; upd.mu.Unlock() }()
		path, err := downloadUpdate()
		task.end(nil)
		if err != nil {
			jsonErr(w, err)
			return
		}
		if err := swapAndRestart(path); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	// Update from a file the player picked (for when there's no update repo yet).
	mux.HandleFunc("POST /api/update/file", func(w http.ResponseWriter, r *http.Request) {
		if !updateSupported() {
			jsonErr(w, errors.New("updates work in the Windows app"))
			return
		}
		data, err := io.ReadAll(io.LimitReader(r.Body, 200<<20+1))
		if err != nil || !looksLikeExe(data) {
			jsonErr(w, errors.New("pick the new TopClient.exe"))
			return
		}
		dir := filepath.Join(dataDir, "updates")
		os.MkdirAll(dir, 0o755)
		path := filepath.Join(dir, "TopClient-from-file.exe")
		if err := os.WriteFile(path, data, 0o755); err != nil {
			jsonErr(w, err)
			return
		}
		if err := swapAndRestart(path); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
}
