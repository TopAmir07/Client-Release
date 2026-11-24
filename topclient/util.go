package main

import (
	"context"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha512"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"hash"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// ---------------------------------------------------------------------------
// Trusted network access
//
// Every outbound request the launcher makes goes through netClient, which
// refuses anything that is not HTTPS to one of the hosts below. These are
// Mojang/Microsoft (game files + login), FabricMC (mod loader), Modrinth
// (mods) and the GitHub/GitLab hosts that the Modrinth modpack spec permits.
// ---------------------------------------------------------------------------

var trustedHosts = map[string]bool{
	// Mojang / Minecraft
	"piston-meta.mojang.com":           true,
	"piston-data.mojang.com":           true,
	"launchermeta.mojang.com":          true,
	"launcher.mojang.com":              true,
	"libraries.minecraft.net":          true,
	"resources.download.minecraft.net": true,
	"textures.minecraft.net":           true,
	"sessionserver.mojang.com":         true,
	"api.minecraftservices.com":        true,
	"api.mojang.com":                   true, // username → skin lookup (Skin Changer)
	// Microsoft / Xbox sign-in
	"login.live.com":            true,
	"login.microsoftonline.com": true,
	"user.auth.xboxlive.com":    true,
	"xsts.auth.xboxlive.com":    true,
	// Fabric
	"meta.fabricmc.net":  true,
	"maven.fabricmc.net": true,
	"repo1.maven.org":    true,
	// Modrinth
	"api.modrinth.com": true,
	// Lyrics on the Home page (only when switched on)
	"lrclib.net": true,
	// Launcher updates (GitHub Releases of the update repo set in Settings)
	"api.github.com": true,

	"cdn.modrinth.com": true,
	// Dawn share pages (public profile info only)
	"dawn.gg": true,
	// Hosts allowed by the .mrpack specification
	"github.com":                           true,
	"raw.githubusercontent.com":            true,
	"objects.githubusercontent.com":        true,
	"release-assets.githubusercontent.com": true,
	"gitlab.com":                           true,
}

const userAgent = "TopClient-Launcher/" + AppVersion

type guardedTransport struct{ base http.RoundTripper }

func (g guardedTransport) RoundTrip(r *http.Request) (*http.Response, error) {
	if err := checkTrusted(r.URL.Scheme, r.URL.Hostname()); err != nil {
		return nil, err
	}
	r = r.Clone(r.Context())
	if r.Header.Get("User-Agent") == "" {
		r.Header.Set("User-Agent", userAgent)
	}
	return g.base.RoundTrip(r)
}

func checkTrusted(scheme, host string) error {
	if scheme != "https" || !trustedHosts[strings.ToLower(host)] {
		return fmt.Errorf("blocked request to untrusted host %q", host)
	}
	return nil
}

var netClient = &http.Client{
	Transport: guardedTransport{http.DefaultTransport},
	Timeout:   10 * time.Minute,
	CheckRedirect: func(req *http.Request, via []*http.Request) error {
		if len(via) > 5 {
			return errors.New("too many redirects")
		}
		return checkTrusted(req.URL.Scheme, req.URL.Hostname())
	},
}

func getJSON(url string, out any) error {
	var last error
	for attempt := 0; attempt < 3; attempt++ {
		req, _ := http.NewRequest("GET", url, nil)
		req.Header.Set("Accept", "application/json")
		resp, err := netClient.Do(req)
		if err != nil {
			last = err
			time.Sleep(time.Duration(attempt+1) * time.Second)
			continue
		}
		body, err := io.ReadAll(io.LimitReader(resp.Body, 256<<20))
		resp.Body.Close()
		if err != nil {
			last = err
			continue
		}
		if resp.StatusCode >= 500 {
			last = fmt.Errorf("%s: HTTP %d", url, resp.StatusCode)
			time.Sleep(time.Duration(attempt+1) * time.Second)
			continue
		}
		if resp.StatusCode != 200 {
			return fmt.Errorf("%s: HTTP %d", shortURL(url), resp.StatusCode)
		}
		return json.Unmarshal(body, out)
	}
	return last
}

func shortURL(u string) string {
	if i := strings.Index(u, "?"); i > 0 {
		u = u[:i]
	}
	return u
}

// fileMatches reports whether path exists and (optionally) matches size/sha1.
func fileMatches(path string, size int64, sha1hex string, deep bool) bool {
	st, err := os.Stat(path)
	if err != nil || st.IsDir() {
		return false
	}
	if size > 0 && st.Size() != size {
		return false
	}
	if deep && sha1hex != "" {
		h, err := hashFile(path, sha1.New())
		return err == nil && strings.EqualFold(h, sha1hex)
	}
	return true
}

func hashFile(path string, h hash.Hash) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// download fetches url into dest (which must be inside the data folder),
// verifying sha1 or sha512 when given. Writes are atomic via a temp file.
func download(url, dest, sha1hex, sha512hex string) error {
	if !insideData(dest) {
		return fmt.Errorf("refusing to write outside launcher folder: %s", dest)
	}
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	var last error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt) * 1500 * time.Millisecond)
		}
		last = downloadOnce(url, dest, sha1hex, sha512hex)
		if last == nil {
			return nil
		}
	}
	return last
}

func downloadOnce(url, dest, sha1hex, sha512hex string) error {
	resp, err := netClient.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("%s: HTTP %d", shortURL(url), resp.StatusCode)
	}
	tmp := dest + ".part"
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	h1, h512 := sha1.New(), sha512.New()
	_, err = io.Copy(io.MultiWriter(f, h1, h512), resp.Body)
	f.Close()
	if err != nil {
		os.Remove(tmp)
		return err
	}
	if sha1hex != "" && !strings.EqualFold(hex.EncodeToString(h1.Sum(nil)), sha1hex) {
		os.Remove(tmp)
		return fmt.Errorf("checksum mismatch for %s", filepath.Base(dest))
	}
	if sha512hex != "" && !strings.EqualFold(hex.EncodeToString(h512.Sum(nil)), sha512hex) {
		os.Remove(tmp)
		return fmt.Errorf("checksum mismatch for %s", filepath.Base(dest))
	}
	os.Remove(dest)
	return os.Rename(tmp, dest)
}

// ---------------------------------------------------------------------------
// Filesystem sandbox: the launcher only ever writes inside dataDir.
// ---------------------------------------------------------------------------

func insideData(p string) bool {
	abs, err := filepath.Abs(p)
	if err != nil {
		return false
	}
	base, _ := filepath.Abs(dataDir)
	rel, err := filepath.Rel(base, abs)
	return err == nil && rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator)) && !filepath.IsAbs(rel)
}

// safeJoin joins an untrusted relative path onto base, rejecting traversal.
func safeJoin(base, rel string) (string, error) {
	rel = strings.ReplaceAll(rel, "\\", "/")
	if rel == "" || strings.HasPrefix(rel, "/") || strings.Contains(rel, ":") {
		return "", fmt.Errorf("invalid path %q", rel)
	}
	for _, part := range strings.Split(rel, "/") {
		if part == ".." {
			return "", fmt.Errorf("invalid path %q", rel)
		}
	}
	p := filepath.Join(base, filepath.FromSlash(rel))
	r, err := filepath.Rel(base, p)
	if err != nil || strings.HasPrefix(r, "..") {
		return "", fmt.Errorf("invalid path %q", rel)
	}
	if !insideData(p) {
		return "", fmt.Errorf("invalid path %q", rel)
	}
	return p, nil
}

func cleanName(s string) string {
	s = filepath.Base(strings.ReplaceAll(s, "\\", "/"))
	var b strings.Builder
	for _, r := range s {
		if r < 32 || strings.ContainsRune(`<>:"/\|?*`, r) {
			continue
		}
		b.WriteRune(r)
	}
	out := strings.TrimSpace(b.String())
	if out == "" || out == "." || out == ".." {
		return "file"
	}
	return out
}

func randHex(n int) string {
	b := make([]byte, n)
	rand.Read(b)
	return hex.EncodeToString(b)
}

func writeJSON(path string, v any) error {
	if !insideData(path) {
		return errors.New("refusing to write outside launcher folder")
	}
	os.MkdirAll(filepath.Dir(path), 0o755)
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func readJSON(path string, v any) error {
	b, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, v)
}

// ---------------------------------------------------------------------------
// Parallel download helper with progress
// ---------------------------------------------------------------------------

type dlJob struct {
	URL, Dest, SHA1, SHA512 string
	Size                    int64
}

func runDownloads(ctx context.Context, stage string, jobs []dlJob, deep bool) error {
	var todo []dlJob
	seen := map[string]bool{}
	for _, j := range jobs {
		if seen[j.Dest] {
			continue
		}
		seen[j.Dest] = true
		if fileMatches(j.Dest, j.Size, j.SHA1, deep) {
			continue
		}
		todo = append(todo, j)
	}
	if len(todo) == 0 {
		return nil
	}
	task.set(stage, 0, int64(len(todo)))
	var done int64
	var firstErr error
	var mu sync.Mutex
	ch := make(chan dlJob)
	var wg sync.WaitGroup
	for w := 0; w < 12; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := range ch {
				if err := download(j.URL, j.Dest, j.SHA1, j.SHA512); err != nil {
					mu.Lock()
					if firstErr == nil {
						firstErr = err
					}
					mu.Unlock()
				}
				task.progress(atomic.AddInt64(&done, 1))
			}
		}()
	}
	for _, j := range todo {
		mu.Lock()
		failed := firstErr != nil
		mu.Unlock()
		if failed || ctx.Err() != nil {
			break
		}
		ch <- j
	}
	close(ch)
	wg.Wait()
	return firstErr
}

// ---------------------------------------------------------------------------
// Task progress (shown in the UI while installing / launching)
// ---------------------------------------------------------------------------

type Task struct {
	mu      sync.Mutex
	Active  bool   `json:"active"`
	Profile string `json:"profile"`
	Stage   string `json:"stage"`
	Done    int64  `json:"done"`
	Total   int64  `json:"total"`
	Error   string `json:"error"`
}

var task = &Task{}

func (t *Task) begin(profile string) bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.Active {
		return false
	}
	t.Active, t.Profile, t.Stage, t.Done, t.Total, t.Error = true, profile, "Preparing", 0, 0, ""
	return true
}
func (t *Task) set(stage string, done, total int64) {
	t.mu.Lock()
	t.Stage, t.Done, t.Total = stage, done, total
	t.mu.Unlock()
}
func (t *Task) progress(done int64) {
	t.mu.Lock()
	t.Done = done
	t.mu.Unlock()
}
func (t *Task) end(err error) {
	t.mu.Lock()
	t.Active = false
	if err != nil {
		t.Error = err.Error()
		logf("task error: %v", err)
	} else {
		t.Error = ""
	}
	t.mu.Unlock()
}
func (t *Task) snapshot() map[string]any {
	t.mu.Lock()
	defer t.mu.Unlock()
	return map[string]any{"active": t.Active, "profile": t.Profile, "stage": t.Stage, "done": t.Done, "total": t.Total, "error": t.Error}
}

// redactSecrets hides anything that looks like a sign-in token (Microsoft / Xbox / Minecraft
// tokens are JWTs starting with "eyJ"; Microsoft refresh tokens start with "M.C" or "M.R"), so
// a token can never end up in launcher.log or the game console, even if a mod prints one.
var secretRe = regexp.MustCompile(`eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}|\bM\.[CR][0-9]{2,3}_[A-Za-z0-9!*$._\-]{20,}|(?i)(access_?token|refresh_?token|accessToken|session(?:id)?)(["':= ]+)[A-Za-z0-9._\-]{24,}`)

func redactSecrets(s string) string {
	if !strings.ContainsAny(s, ".=:") {
		return s
	}
	// Quick check first: almost no log line has anything token-like, so skip the slow search.
	if !strings.Contains(s, "eyJ") && !strings.Contains(s, "M.C") && !strings.Contains(s, "M.R") {
		l := strings.ToLower(s)
		if !strings.Contains(l, "token") && !strings.Contains(l, "session") {
			return s
		}
	}
	return secretRe.ReplaceAllStringFunc(s, func(m string) string {
		if sm := secretRe.FindStringSubmatch(m); len(sm) > 3 && sm[1] != "" {
			return sm[1] + sm[2] + "<hidden>"
		}
		return "<hidden>"
	})
}
