package main

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha1"
	"crypto/sha512"
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
	"strings"
	"sync/atomic"
	"time"
)

var modrinthAPI = "https://api.modrinth.com/v2"

type mrVersion struct {
	ID           string   `json:"id"`
	ProjectID    string   `json:"project_id"`
	Name         string   `json:"name"`
	VersionNum   string   `json:"version_number"`
	GameVersions []string `json:"game_versions"`
	Loaders      []string `json:"loaders"`
	Files        []struct {
		URL      string            `json:"url"`
		Filename string            `json:"filename"`
		Primary  bool              `json:"primary"`
		Size     int64             `json:"size"`
		Hashes   map[string]string `json:"hashes"`
	} `json:"files"`
	Dependencies []struct {
		ProjectID string `json:"project_id"`
		VersionID string `json:"version_id"`
		Type      string `json:"dependency_type"`
	} `json:"dependencies"`
}

type mrProject struct {
	ID          string `json:"id"`
	Slug        string `json:"slug"`
	Title       string `json:"title"`
	ProjectType string `json:"project_type"`
	IconURL     string `json:"icon_url"`
	Gallery     []struct {
		URL      string `json:"url"`
		Featured bool   `json:"featured"`
	} `json:"gallery"`
}

func (p mrProject) banner() string {
	for _, g := range p.Gallery {
		if g.Featured {
			return g.URL
		}
	}
	if len(p.Gallery) > 0 {
		return p.Gallery[0].URL
	}
	return ""
}

func mrGetProject(id string) (*mrProject, error) {
	var p mrProject
	err := getJSON(modrinthAPI+"/project/"+url.PathEscape(id), &p)
	return &p, err
}

func mrPickVersion(projectID, mc, loader, ptype string) (*mrVersion, error) {
	q := url.Values{}
	if mc != "" {
		gv, _ := json.Marshal([]string{mc})
		q.Set("game_versions", string(gv))
	}
	if ptype == "mod" {
		l, _ := json.Marshal([]string{loader})
		q.Set("loaders", string(l))
	}
	var list []mrVersion
	if err := getJSON(modrinthAPI+"/project/"+url.PathEscape(projectID)+"/version?"+q.Encode(), &list); err != nil {
		return nil, err
	}
	if len(list) == 0 {
		return nil, fmt.Errorf("no version available for Minecraft %s", mc)
	}
	return &list[0], nil
}

func primaryFile(v *mrVersion) (u, name, sha string, err error) {
	for _, f := range v.Files {
		if f.Primary {
			return f.URL, f.Filename, f.Hashes["sha512"], nil
		}
	}
	if len(v.Files) > 0 {
		return v.Files[0].URL, v.Files[0].Filename, v.Files[0].Hashes["sha512"], nil
	}
	return "", "", "", errors.New("version has no files")
}

func folderFor(ptype string) string {
	switch ptype {
	case "resourcepack":
		return "resourcepacks"
	case "shader":
		return "shaderpacks"
	}
	return "mods"
}

// installProject installs a Modrinth project (and required dependencies)
// into a profile.
func installProject(p *Profile, projectID, versionID string, depth int, done map[string]bool) error {
	if isBedrock(p) {
		return errors.New("Modrinth content is for Java profiles. Bedrock add-ons come from the in-game Marketplace")
	}
	if done[projectID] || depth > 6 {
		return nil
	}
	done[projectID] = true
	proj, err := mrGetProject(projectID)
	if err != nil {
		return err
	}
	if proj.ProjectType == "modpack" {
		return installModpackProject(proj.ID, versionID)
	}
	if proj.ProjectType == "mod" && p.Loader != "fabric" {
		return errors.New("mods need a Fabric profile — pick or create one with the Fabric loader")
	}
	// Skip if already installed
	meta := readMeta(p)
	for _, m := range meta {
		if m.Project == proj.ID && depth > 0 {
			return nil
		}
	}
	mc := p.MC
	if mc == "latest" {
		mc = ""
	}
	var v *mrVersion
	if versionID != "" {
		v = &mrVersion{}
		if err := getJSON(modrinthAPI+"/version/"+url.PathEscape(versionID), v); err != nil {
			return err
		}
	} else {
		v, err = mrPickVersion(proj.ID, mc, "fabric", proj.ProjectType)
		if err != nil {
			return fmt.Errorf("%s: %v", proj.Title, err)
		}
	}
	u, name, sha, err := primaryFile(v)
	if err != nil {
		return err
	}
	dir := filepath.Join(profileDir(p), folderFor(proj.ProjectType))
	// remove older file from same project
	for file, m := range meta {
		if m.Project == proj.ID && file != name {
			os.Remove(filepath.Join(dir, cleanName(file)))
			os.Remove(filepath.Join(dir, cleanName(file)+".disabled"))
		}
	}
	dest := filepath.Join(dir, cleanName(name))
	task.set("Downloading "+proj.Title, 0, 0)
	if err := download(u, dest, "", sha); err != nil {
		return err
	}
	setMeta(p, cleanName(name), installedMeta{Project: proj.ID, Version: v.ID, Title: proj.Title, Icon: proj.IconURL, Banner: proj.banner()})
	for _, d := range v.Dependencies {
		if d.Type == "required" && d.ProjectID != "" {
			if err := installProject(p, d.ProjectID, "", depth+1, done); err != nil {
				logf("dependency %s: %v", d.ProjectID, err)
			}
		}
	}
	return nil
}

// ---------------------------------------------------------------------------
// .mrpack (Modrinth modpack) import
// ---------------------------------------------------------------------------

var mrpackHosts = map[string]bool{"cdn.modrinth.com": true, "github.com": true, "raw.githubusercontent.com": true, "gitlab.com": true}

func installModpackProject(projectID, versionID string) error {
	var v *mrVersion
	var err error
	if versionID != "" {
		v = &mrVersion{}
		err = getJSON(modrinthAPI+"/version/"+url.PathEscape(versionID), v)
	} else {
		v, err = mrPickVersion(projectID, "", "", "modpack")
	}
	if err != nil {
		return err
	}
	u, name, sha, err := primaryFile(v)
	if err != nil {
		return err
	}
	tmp := filepath.Join(dataDir, "cache", "packs", cleanName(name))
	task.set("Downloading modpack", 0, 0)
	if err := download(u, tmp, "", sha); err != nil {
		return err
	}
	defer os.Remove(tmp)
	_, err = importMrpack(tmp)
	return err
}

func importMrpack(path string) (*Profile, error) {
	zr, err := zip.OpenReader(path)
	if err != nil {
		return nil, errors.New("that file isn't a valid .mrpack")
	}
	defer zr.Close()
	var index struct {
		Name  string `json:"name"`
		Files []struct {
			Path      string            `json:"path"`
			Hashes    map[string]string `json:"hashes"`
			Downloads []string          `json:"downloads"`
			Env       map[string]string `json:"env"`
			Size      int64             `json:"fileSize"`
		} `json:"files"`
		Deps map[string]string `json:"dependencies"`
	}
	found := false
	for _, f := range zr.File {
		if f.Name == "modrinth.index.json" {
			rc, _ := f.Open()
			err = json.NewDecoder(rc).Decode(&index)
			rc.Close()
			found = err == nil
		}
	}
	if !found {
		return nil, errors.New("modrinth.index.json missing from pack")
	}
	mc := index.Deps["minecraft"]
	loader := "vanilla"
	if index.Deps["fabric-loader"] != "" {
		loader = "fabric"
	} else if index.Deps["forge"] != "" || index.Deps["neoforge"] != "" || index.Deps["quilt-loader"] != "" {
		return nil, errors.New("this pack needs Forge/NeoForge/Quilt — TopClient supports Vanilla and Fabric")
	}
	if mc == "" {
		return nil, errors.New("pack doesn't say which Minecraft version it needs")
	}
	p := newProfile(index.Name, mc, loader)
	p.LoaderVer = index.Deps["fabric-loader"]
	p.Icon = "pack"
	saveProfiles()
	root := profileDir(p)
	var jobs []dlJob
	for _, f := range index.Files {
		if f.Env != nil && f.Env["client"] == "unsupported" {
			continue
		}
		dst, err := safeJoin(root, f.Path)
		if err != nil || len(f.Downloads) == 0 {
			continue
		}
		pu, err := url.Parse(f.Downloads[0])
		if err != nil || pu.Scheme != "https" || !mrpackHosts[pu.Hostname()] {
			continue
		}
		if f.Hashes["sha1"] == "" && f.Hashes["sha512"] == "" {
			continue // every pack file must come with a checksum
		}
		jobs = append(jobs, dlJob{URL: f.Downloads[0], Dest: dst, SHA1: f.Hashes["sha1"], SHA512: f.Hashes["sha512"], Size: f.Size})
	}
	if err := runDownloads(context.Background(), "Downloading pack files", jobs, false); err != nil {
		return p, err
	}
	// Files packed inside the pack itself didn't come from Modrinth, so they were never
	// checked. Mods among them are saved switched off (the player can turn them on if they
	// trust the pack), and program files are never written at all.
	var total int64
	held := 0
	for _, f := range zr.File {
		var rel string
		switch {
		case strings.HasPrefix(f.Name, "overrides/"):
			rel = strings.TrimPrefix(f.Name, "overrides/")
		case strings.HasPrefix(f.Name, "client-overrides/"):
			rel = strings.TrimPrefix(f.Name, "client-overrides/")
		default:
			continue
		}
		if rel == "" || f.FileInfo().IsDir() {
			continue
		}
		low := strings.ToLower(rel)
		if blockedPackFile(low) {
			logf("pack: skipped program file %s", rel)
			continue
		}
		if strings.HasSuffix(low, ".jar") {
			rel += ".disabled"
			held++
		}
		dst, err := safeJoin(root, rel)
		if err != nil {
			continue
		}
		if total += int64(f.UncompressedSize64); total > 4<<30 {
			break // refuse zip bombs
		}
		os.MkdirAll(filepath.Dir(dst), 0o755)
		rc, err := f.Open()
		if err != nil {
			continue
		}
		out, err := os.Create(dst)
		if err == nil {
			io.Copy(out, io.LimitReader(rc, 1<<30))
			out.Close()
		}
		rc.Close()
	}
	if held > 0 {
		lastPackHeldBack.Store(int64(held))
	} else {
		lastPackHeldBack.Store(0)
	}
	return p, nil
}

var lastPackHeldBack atomic.Int64

func blockedPackFile(low string) bool {
	for _, ext := range []string{".exe", ".dll", ".bat", ".cmd", ".ps1", ".psm1", ".vbs", ".vbe", ".js", ".jse", ".wsf", ".wsh",
		".scr", ".msi", ".msp", ".com", ".lnk", ".hta", ".cpl", ".reg", ".sys", ".jnlp", ".sh"} {
		if strings.HasSuffix(low, ext) {
			return true
		}
	}
	return false
}

// ---------------------------------------------------------------------------
// Share: export a profile as .mrpack (mods referenced from Modrinth when found)
// ---------------------------------------------------------------------------

func exportMrpack(p *Profile) ([]byte, error) {
	root := profileDir(p)
	type entry struct {
		rel  string
		path string
		sha1 string
	}
	var mods []entry
	for _, sub := range []string{"mods", "resourcepacks", "shaderpacks"} {
		ents, _ := os.ReadDir(filepath.Join(root, sub))
		for _, e := range ents {
			if e.IsDir() || strings.HasSuffix(e.Name(), ".disabled") {
				continue
			}
			full := filepath.Join(root, sub, e.Name())
			h, err := hashFile(full, sha1.New())
			if err == nil {
				mods = append(mods, entry{sub + "/" + e.Name(), full, h})
			}
		}
	}
	var hashes []string
	for _, m := range mods {
		hashes = append(hashes, m.sha1)
	}
	known := map[string]mrVersion{}
	if len(hashes) > 0 {
		b, _ := json.Marshal(map[string]any{"hashes": hashes, "algorithm": "sha1"})
		req, _ := http.NewRequest("POST", modrinthAPI+"/version_files", bytes.NewReader(b))
		req.Header.Set("Content-Type", "application/json")
		if resp, err := netClient.Do(req); err == nil {
			json.NewDecoder(resp.Body).Decode(&known)
			resp.Body.Close()
		}
	}
	type file struct {
		Path      string            `json:"path"`
		Hashes    map[string]string `json:"hashes"`
		Env       map[string]string `json:"env"`
		Downloads []string          `json:"downloads"`
		Size      int64             `json:"fileSize"`
	}
	files := []file{}
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	addOverride := func(rel, full string) {
		f, err := os.Open(full)
		if err != nil {
			return
		}
		defer f.Close()
		w, err := zw.Create("overrides/" + rel)
		if err == nil {
			io.Copy(w, f)
		}
	}
	for _, m := range mods {
		v, ok := known[m.sha1]
		matched := false
		if ok {
			for _, f := range v.Files {
				if strings.EqualFold(f.Hashes["sha1"], m.sha1) {
					sha512h, _ := hashFile(m.path, sha512.New())
					files = append(files, file{Path: m.rel, Hashes: map[string]string{"sha1": m.sha1, "sha512": sha512h},
						Env: map[string]string{"client": "required", "server": "optional"}, Downloads: []string{f.URL}, Size: f.Size})
					matched = true
					break
				}
			}
		}
		if !matched {
			addOverride(m.rel, m.path)
		}
	}
	// config + options travel as overrides
	filepath.Walk(filepath.Join(root, "config"), func(path string, info os.FileInfo, err error) error {
		if err == nil && !info.IsDir() && info.Size() < 8<<20 {
			rel, _ := filepath.Rel(root, path)
			addOverride(filepath.ToSlash(rel), path)
		}
		return nil
	})
	for _, f := range []string{"options.txt", "optionsof.txt", "optionsshaders.txt", "servers.dat"} {
		if _, err := os.Stat(filepath.Join(root, f)); err == nil {
			addOverride(f, filepath.Join(root, f))
		}
	}
	deps := map[string]string{"minecraft": p.MC}
	if p.Loader == "fabric" && p.LoaderVer != "" {
		deps["fabric-loader"] = p.LoaderVer
	}
	idx := map[string]any{"formatVersion": 1, "game": "minecraft", "versionId": time.Now().Format("2006.01.02"),
		"name": p.Name, "summary": "Shared from TopClient", "files": files, "dependencies": deps}
	w, _ := zw.Create("modrinth.index.json")
	json.NewEncoder(w).Encode(idx)
	zw.Close()
	return buf.Bytes(), nil
}

// ---------------------------------------------------------------------------
// Share links
// ---------------------------------------------------------------------------

var mrLinkRe = regexp.MustCompile(`modrinth\.com/(mod|modpack|resourcepack|shader|plugin|datapack)/([A-Za-z0-9_.-]+)(?:/version/([A-Za-z0-9_.-]+))?`)

var dawnLinkRe = regexp.MustCompile(`(?:dawn\.gg/share/|dawn://share/)([A-Za-z0-9_-]{8,128})`)

type dawnShare struct {
	Name, MC, Loader, LoaderVer, Owner string
}

// parseDawnShare reads the public profile details from a dawn.gg share page.
func parseDawnShare(html string) (*dawnShare, error) {
	html = strings.ReplaceAll(html, "<!-- -->", "")
	tag := regexp.MustCompile(`<[^>]+>`)
	clean := func(s string) string {
		s = tag.ReplaceAllString(s, "")
		r := strings.NewReplacer("&amp;", "&", "&lt;", "<", "&gt;", ">", "&quot;", `"`, "&#x27;", "'", "&#39;", "'")
		return strings.TrimSpace(r.Replace(s))
	}
	field := func(label string) string {
		re := regexp.MustCompile(`(?s)<dt[^>]*>\s*` + label + `\s*</dt>\s*<dd[^>]*>(.*?)</dd>`)
		if m := re.FindStringSubmatch(html); m != nil {
			return clean(m[1])
		}
		return ""
	}
	d := &dawnShare{MC: field("Minecraft"), Owner: field("Owner")}
	if m := regexp.MustCompile(`(?s)<h1[^>]*id="share-title"[^>]*>(.*?)</h1>`).FindStringSubmatch(html); m != nil {
		d.Name = clean(m[1])
	}
	loader := strings.Fields(field("Loader"))
	if len(loader) > 0 {
		d.Loader = strings.ToLower(loader[0])
	}
	if len(loader) > 1 {
		d.LoaderVer = loader[1]
	}
	if d.MC == "" || !regexp.MustCompile(`^[0-9A-Za-z._-]{1,40}$`).MatchString(d.MC) {
		return nil, errors.New("couldn't read that Dawn profile — the link may have expired")
	}
	if d.LoaderVer != "" && !regexp.MustCompile(`^[0-9A-Za-z.+_-]{1,40}$`).MatchString(d.LoaderVer) {
		d.LoaderVer = ""
	}
	if d.Name == "" {
		d.Name = "Dawn profile"
	}
	return d, nil
}

func importDawn(id string) (*Profile, *dawnShare, error) {
	resp, err := netClient.Get("https://dawn.gg/share/" + id)
	if err != nil {
		return nil, nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, nil, fmt.Errorf("Dawn couldn't find that share link (HTTP %d)", resp.StatusCode)
	}
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	d, err := parseDawnShare(string(b))
	if err != nil {
		return nil, nil, err
	}
	loader := "vanilla"
	switch d.Loader {
	case "fabric":
		loader = "fabric"
	case "", "vanilla":
	default:
		return nil, nil, fmt.Errorf("that Dawn profile uses %s — TopClient supports Vanilla and Fabric", strings.ToUpper(d.Loader))
	}
	p := newProfile(d.Name, d.MC, loader)
	if loader == "fabric" {
		p.LoaderVer = d.LoaderVer
	}
	p.Icon = "dawn-import"
	saveProfiles()
	return p, d, nil
}

func importLink(link string, p *Profile) (string, error) {
	link = strings.TrimSpace(link)
	if m := dawnLinkRe.FindStringSubmatch(link); m != nil {
		np, d, err := importDawn(m[1])
		if err != nil {
			return "", err
		}
		lv := ""
		if d.LoaderVer != "" {
			lv = " " + d.LoaderVer
		}
		return "DAWN:" + np.ID + ":Created “" + np.Name + "” (" + strings.Title(np.Loader) + lv + ", Minecraft " + np.MC + ") from " + d.Owner + "'s Dawn profile", nil
	}
	if m := mrLinkRe.FindStringSubmatch(link); m != nil {
		proj, err := mrGetProject(m[2])
		if err != nil {
			return "", errors.New("couldn't find that Modrinth project")
		}
		ver := ""
		if m[3] != "" {
			var v mrVersion
			if getJSON(modrinthAPI+"/project/"+proj.ID+"/version/"+url.PathEscape(m[3]), &v) == nil {
				ver = v.ID
			}
		}
		if proj.ProjectType == "modpack" {
			return proj.Title + " installed as a new profile", installModpackProject(proj.ID, ver)
		}
		if p == nil {
			return "", errors.New("pick a profile first")
		}
		return proj.Title + " installed to " + p.Name, installProject(p, proj.ID, ver, 0, map[string]bool{})
	}
	u, err := url.Parse(link)
	if err == nil && u.Scheme == "https" && mrpackHosts[u.Hostname()] && strings.HasSuffix(strings.ToLower(u.Path), ".mrpack") {
		tmp := filepath.Join(dataDir, "cache", "packs", "link-"+randHex(4)+".mrpack")
		if err := download(link, tmp, "", ""); err != nil {
			return "", err
		}
		defer os.Remove(tmp)
		np, err := importMrpack(tmp)
		if err != nil {
			return "", err
		}
		return np.Name + " imported", nil
	}
	return "", errors.New("that link type isn't supported yet — Modrinth links and .mrpack links work. Send me an example of the other link and I can add it")
}

func hexSum(b []byte) string { h := sha1.Sum(b); return hex.EncodeToString(h[:]) }
