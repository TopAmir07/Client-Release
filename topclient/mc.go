package main

import (
	"archive/zip"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	manifestURL    = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
	javaRuntimeURL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
)

var fabricMeta = "https://meta.fabricmc.net/v2"

type Rule struct {
	Action string `json:"action"`
	OS     *struct {
		Name string `json:"name"`
		Arch string `json:"arch"`
	} `json:"os"`
	Features map[string]bool `json:"features"`
}

type Artifact struct {
	Path string `json:"path"`
	URL  string `json:"url"`
	SHA1 string `json:"sha1"`
	Size int64  `json:"size"`
}

type Library struct {
	Name      string `json:"name"`
	URL       string `json:"url"`
	SHA1      string `json:"sha1"`
	Size      int64  `json:"size"`
	Downloads *struct {
		Artifact    *Artifact            `json:"artifact"`
		Classifiers map[string]*Artifact `json:"classifiers"`
	} `json:"downloads"`
	Natives map[string]string `json:"natives"`
	Extract *struct {
		Exclude []string `json:"exclude"`
	} `json:"extract"`
	Rules []Rule `json:"rules"`
}

type VersionJSON struct {
	ID                 string `json:"id"`
	InheritsFrom       string `json:"inheritsFrom"`
	Type               string `json:"type"`
	MainClass          string `json:"mainClass"`
	MinecraftArguments string `json:"minecraftArguments"`
	Assets             string `json:"assets"`
	ReleaseTime        string `json:"releaseTime"`
	Arguments          *struct {
		Game []json.RawMessage `json:"game"`
		JVM  []json.RawMessage `json:"jvm"`
	} `json:"arguments"`
	AssetIndex *struct {
		ID   string `json:"id"`
		URL  string `json:"url"`
		SHA1 string `json:"sha1"`
		Size int64  `json:"size"`
	} `json:"assetIndex"`
	Downloads   map[string]*Artifact `json:"downloads"`
	Libraries   []Library            `json:"libraries"`
	JavaVersion *struct {
		Component    string `json:"component"`
		MajorVersion int    `json:"majorVersion"`
	} `json:"javaVersion"`
	Logging *struct {
		Client *struct {
			Argument string `json:"argument"`
			File     struct {
				ID   string `json:"id"`
				URL  string `json:"url"`
				SHA1 string `json:"sha1"`
				Size int64  `json:"size"`
			} `json:"file"`
		} `json:"client"`
	} `json:"logging"`
}

type ManifestEntry struct {
	ID          string `json:"id"`
	Type        string `json:"type"`
	URL         string `json:"url"`
	SHA1        string `json:"sha1"`
	ReleaseTime string `json:"releaseTime"`
}

type Manifest struct {
	Latest struct {
		Release  string `json:"release"`
		Snapshot string `json:"snapshot"`
	} `json:"latest"`
	Versions []ManifestEntry `json:"versions"`
}

var (
	manifestMu    sync.Mutex
	manifestCache *Manifest
	manifestTime  time.Time
)

func getManifest() (*Manifest, error) {
	manifestMu.Lock()
	defer manifestMu.Unlock()
	if manifestCache != nil && time.Since(manifestTime) < 20*time.Minute {
		return manifestCache, nil
	}
	cache := filepath.Join(dataDir, "meta", "version_manifest_v2.json")
	var m Manifest
	if err := getJSON(manifestURL, &m); err != nil {
		if readJSON(cache, &m) == nil { // offline fallback
			return &m, nil
		}
		return nil, err
	}
	writeJSON(cache, &m)
	manifestCache, manifestTime = &m, time.Now()
	return &m, nil
}

// Files checked once while the launcher is open don't need checking again on
// every Play press (a Repair launch still checks everything).
var (
	checkedMu  sync.Mutex
	checkedSet = map[string]bool{}
)

func checkedThisSession(kind, key string) bool {
	checkedMu.Lock()
	defer checkedMu.Unlock()
	return checkedSet[kind+":"+key]
}

func markChecked(kind, key string) {
	checkedMu.Lock()
	checkedSet[kind+":"+key] = true
	checkedMu.Unlock()
}

// ---------------------------------------------------------------------------
// Rules / OS helpers
// ---------------------------------------------------------------------------

func osName() string {
	switch runtime.GOOS {
	case "windows":
		return "windows"
	case "darwin":
		return "osx"
	}
	return "linux"
}

func rulesAllow(rules []Rule, features map[string]bool) bool {
	if len(rules) == 0 {
		return true
	}
	allowed := false
	for _, r := range rules {
		match := true
		if r.OS != nil {
			if r.OS.Name != "" && r.OS.Name != osName() {
				match = false
			}
			if r.OS.Arch == "x86" && runtime.GOARCH != "386" {
				match = false
			}
		}
		for f, want := range r.Features {
			if features[f] != want {
				match = false
			}
		}
		if match {
			allowed = r.Action == "allow"
		}
	}
	return allowed
}

// mavenPath converts "group:artifact:version[:classifier][@ext]" to a repo path.
func mavenPath(name string) string {
	ext := "jar"
	if i := strings.Index(name, "@"); i > 0 {
		ext = name[i+1:]
		name = name[:i]
	}
	p := strings.Split(name, ":")
	if len(p) < 3 {
		return ""
	}
	file := p[1] + "-" + p[2]
	if len(p) > 3 {
		file += "-" + p[3]
	}
	return strings.ReplaceAll(p[0], ".", "/") + "/" + p[1] + "/" + p[2] + "/" + file + "." + ext
}

func libKey(name string) string {
	p := strings.Split(strings.Split(name, "@")[0], ":")
	if len(p) < 2 {
		return name
	}
	k := p[0] + ":" + p[1]
	if len(p) > 3 {
		k += ":" + p[3]
	}
	return k
}

// ---------------------------------------------------------------------------
// Version resolution
// ---------------------------------------------------------------------------

func loadVanilla(id string, deep bool) (*VersionJSON, error) {
	path := filepath.Join(dataDir, "versions", id, id+".json")
	// Fast path: a version's JSON never changes once released, so when it is already
	// here we skip the (1 MB) version manifest download. Repair still re-checks it.
	if !deep {
		var v VersionJSON
		if readJSON(path, &v) == nil && v.ID == id && len(v.Libraries) > 0 {
			return &v, nil
		}
	}
	m, err := getManifest()
	if err != nil {
		var v VersionJSON
		if readJSON(path, &v) == nil {
			return &v, nil
		}
		return nil, err
	}
	var entry *ManifestEntry
	for i := range m.Versions {
		if m.Versions[i].ID == id {
			entry = &m.Versions[i]
			break
		}
	}
	if entry == nil {
		return nil, fmt.Errorf("Minecraft version %s not found", id)
	}
	if !fileMatches(path, 0, entry.SHA1, true) {
		if err := download(entry.URL, path, entry.SHA1, ""); err != nil {
			return nil, err
		}
	}
	var v VersionJSON
	if err := readJSON(path, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

type fabricLoader struct {
	Loader struct {
		Version string `json:"version"`
		Stable  bool   `json:"stable"`
	} `json:"loader"`
}

func latestFabricLoader(mc string) (string, error) {
	var list []fabricLoader
	if err := getJSON(fabricMeta+"/versions/loader/"+mc, &list); err != nil {
		return "", err
	}
	if len(list) == 0 {
		return "", fmt.Errorf("Fabric does not support Minecraft %s yet", mc)
	}
	for _, l := range list {
		if l.Loader.Stable {
			return l.Loader.Version, nil
		}
	}
	return list[0].Loader.Version, nil
}

func latestFabricGame() (string, error) {
	var list []struct {
		Version string `json:"version"`
		Stable  bool   `json:"stable"`
	}
	if err := getJSON(fabricMeta+"/versions/game", &list); err != nil {
		return "", err
	}
	for _, g := range list {
		if g.Stable {
			return g.Version, nil
		}
	}
	return "", errors.New("no Fabric game versions available")
}

func loadFabric(mc, loader string) (*VersionJSON, error) {
	id := "fabric-loader-" + loader + "-" + mc
	path := filepath.Join(dataDir, "versions", id, id+".json")
	var v VersionJSON
	if readJSON(path, &v) != nil {
		url := fmt.Sprintf("%s/versions/loader/%s/%s/profile/json", fabricMeta, mc, loader)
		if err := getJSON(url, &v); err != nil {
			return nil, err
		}
		writeJSON(path, &v)
	}
	return &v, nil
}

func merge(child, parent *VersionJSON) *VersionJSON {
	out := *parent
	out.ID = child.ID
	if child.MainClass != "" {
		out.MainClass = child.MainClass
	}
	if child.MinecraftArguments != "" {
		out.MinecraftArguments = child.MinecraftArguments
	}
	keys := map[string]bool{}
	var libs []Library
	for _, l := range child.Libraries {
		keys[libKey(l.Name)] = true
		libs = append(libs, l)
	}
	for _, l := range parent.Libraries {
		if !keys[libKey(l.Name)] {
			libs = append(libs, l)
		}
	}
	out.Libraries = libs
	if child.Arguments != nil {
		args := &struct {
			Game []json.RawMessage `json:"game"`
			JVM  []json.RawMessage `json:"jvm"`
		}{}
		if parent.Arguments != nil {
			args.Game = append(args.Game, parent.Arguments.Game...)
			args.JVM = append(args.JVM, parent.Arguments.JVM...)
		}
		args.Game = append(args.Game, child.Arguments.Game...)
		args.JVM = append(args.JVM, child.Arguments.JVM...)
		out.Arguments = args
	}
	return &out
}

// ---------------------------------------------------------------------------
// Install
// ---------------------------------------------------------------------------

type resolvedLib struct {
	Path   string
	Native bool // extract DLLs from this jar
	OnCP   bool // put on classpath
	Excl   []string
}

func nativeClassifierOK(classifier string) bool {
	want := "natives-" + osName()
	if classifier == want {
		return true
	}
	if runtime.GOARCH == "arm64" {
		return classifier == want+"-arm64"
	}
	return false
}

func installVersion(ctx context.Context, p *Profile, deep bool) (*VersionJSON, []resolvedLib, error) {
	mc := p.MC
	if mc == "" || mc == "latest" {
		var err error
		if p.Loader == "fabric" {
			mc, err = latestFabricGame()
		} else {
			var m *Manifest
			m, err = getManifest()
			if m != nil {
				mc = m.Latest.Release
			}
		}
		if err != nil {
			return nil, nil, err
		}
		p.MC = mc
		saveProfiles()
	}
	task.set("Fetching Minecraft "+mc, 0, 0)
	v, err := loadVanilla(mc, deep)
	if err != nil {
		return nil, nil, err
	}
	// Fabric only exists for Minecraft 1.14 and newer (snapshots from late 2018). Older versions
	// like 1.8.9 and 1.12.2 play as normal Minecraft instead of failing.
	if p.Loader == "fabric" && !fabricSupports(v.ReleaseTime) {
		logf("Fabric doesn't support Minecraft %s — playing it without a mod loader", mc)
		p.Loader, p.LoaderVer = "vanilla", ""
		saveProfiles()
	}
	if p.Loader == "fabric" {
		task.set("Fetching Fabric", 0, 0)
		if p.LoaderVer == "" {
			lv, err := latestFabricLoader(mc)
			if err != nil {
				return nil, nil, err
			}
			p.LoaderVer = lv
			saveProfiles()
		}
		fv, err := loadFabric(mc, p.LoaderVer)
		if err != nil {
			return nil, nil, err
		}
		v = merge(fv, v)
	}

	libDir := filepath.Join(dataDir, "libraries")
	var jobs []dlJob
	var libs []resolvedLib
	for _, l := range v.Libraries {
		if !rulesAllow(l.Rules, nil) {
			continue
		}
		// Old-style natives (classifier map)
		if l.Natives != nil {
			cls, ok := l.Natives[osName()]
			if ok && l.Downloads != nil && l.Downloads.Classifiers != nil {
				cls = strings.ReplaceAll(cls, "${arch}", "64")
				if a := l.Downloads.Classifiers[cls]; a != nil {
					dest := filepath.Join(libDir, filepath.FromSlash(a.Path))
					jobs = append(jobs, dlJob{URL: a.URL, Dest: dest, SHA1: a.SHA1, Size: a.Size})
					var ex []string
					if l.Extract != nil {
						ex = l.Extract.Exclude
					}
					libs = append(libs, resolvedLib{Path: dest, Native: true, Excl: ex})
				}
			}
			if l.Downloads == nil || l.Downloads.Artifact == nil {
				continue
			}
		}
		var a *Artifact
		if l.Downloads != nil && l.Downloads.Artifact != nil {
			a = l.Downloads.Artifact
		} else if l.URL != "" || l.Downloads == nil {
			base := l.URL
			if base == "" {
				base = "https://libraries.minecraft.net/"
			}
			if !strings.HasSuffix(base, "/") {
				base += "/"
			}
			mp := mavenPath(l.Name)
			if mp == "" {
				continue
			}
			a = &Artifact{Path: mp, URL: base + mp, SHA1: l.SHA1, Size: l.Size}
		}
		if a == nil || a.URL == "" {
			continue
		}
		path := a.Path
		if path == "" {
			path = mavenPath(l.Name)
		}
		dest := filepath.Join(libDir, filepath.FromSlash(path))
		jobs = append(jobs, dlJob{URL: a.URL, Dest: dest, SHA1: a.SHA1, Size: a.Size})
		parts := strings.Split(l.Name, ":")
		isNative := len(parts) > 3 && strings.HasPrefix(parts[3], "natives-")
		libs = append(libs, resolvedLib{Path: dest, OnCP: true, Native: isNative && nativeClassifierOK(parts[3])})
	}
	// client jar
	if c := v.Downloads["client"]; c != nil {
		dest := filepath.Join(dataDir, "versions", mc, mc+".jar")
		jobs = append(jobs, dlJob{URL: c.URL, Dest: dest, SHA1: c.SHA1, Size: c.Size})
		libs = append(libs, resolvedLib{Path: dest, OnCP: true})
	}
	// logging config (only needed for the log4j fix on old versions)
	if v.Logging != nil && v.Logging.Client != nil && v.ReleaseTime < "2021-12-10" {
		f := v.Logging.Client.File
		jobs = append(jobs, dlJob{URL: f.URL, Dest: filepath.Join(dataDir, "assets", "log_configs", cleanName(f.ID)), SHA1: f.SHA1, Size: f.Size})
	}
	libKey := mc + "|" + p.Loader + "|" + p.LoaderVer
	if deep || !checkedThisSession("libs", libKey) {
		if err := runDownloads(ctx, "Downloading libraries", jobs, deep); err != nil {
			return nil, nil, err
		}
		markChecked("libs", libKey)
	}
	if err := installAssets(ctx, v, p, deep); err != nil {
		return nil, nil, err
	}
	return v, libs, nil
}

func installAssets(ctx context.Context, v *VersionJSON, p *Profile, deep bool) error {
	if v.AssetIndex == nil {
		return nil
	}
	task.set("Checking assets", 0, 0)
	idxPath := filepath.Join(dataDir, "assets", "indexes", cleanName(v.AssetIndex.ID)+".json")
	if !fileMatches(idxPath, v.AssetIndex.Size, v.AssetIndex.SHA1, true) {
		if err := download(v.AssetIndex.URL, idxPath, v.AssetIndex.SHA1, ""); err != nil {
			return err
		}
	}
	var idx struct {
		Objects map[string]struct {
			Hash string `json:"hash"`
			Size int64  `json:"size"`
		} `json:"objects"`
		Virtual        bool `json:"virtual"`
		MapToResources bool `json:"map_to_resources"`
	}
	if err := readJSON(idxPath, &idx); err != nil {
		return err
	}
	objDir := filepath.Join(dataDir, "assets", "objects")
	var jobs []dlJob
	for _, o := range idx.Objects {
		if len(o.Hash) < 2 {
			continue
		}
		jobs = append(jobs, dlJob{
			URL:  "https://resources.download.minecraft.net/" + o.Hash[:2] + "/" + o.Hash,
			Dest: filepath.Join(objDir, o.Hash[:2], o.Hash), SHA1: o.Hash, Size: o.Size,
		})
	}
	if deep || !checkedThisSession("assets", v.AssetIndex.ID) {
		if err := runDownloads(ctx, "Downloading assets", jobs, deep); err != nil {
			return err
		}
		markChecked("assets", v.AssetIndex.ID)
	}
	// Very old versions read assets from a flat folder.
	if idx.Virtual || idx.MapToResources {
		var base string
		if idx.MapToResources {
			base = filepath.Join(profileDir(p), "resources")
		} else {
			base = filepath.Join(dataDir, "assets", "virtual", cleanName(v.AssetIndex.ID))
		}
		for name, o := range idx.Objects {
			dst, err := safeJoin(base, name)
			if err != nil || fileMatches(dst, o.Size, "", false) {
				continue
			}
			os.MkdirAll(filepath.Dir(dst), 0o755)
			copyFile(filepath.Join(objDir, o.Hash[:2], o.Hash), dst)
		}
	}
	return nil
}

func copyFile(src, dst string) error {
	if !insideData(dst) {
		return errors.New("refusing to write outside launcher folder")
	}
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	_, err = io.Copy(out, in)
	out.Close()
	return err
}

// ---------------------------------------------------------------------------
// Java runtime (Mojang's own official builds)
// ---------------------------------------------------------------------------

func javaPlatform() string {
	switch runtime.GOOS {
	case "windows":
		if runtime.GOARCH == "arm64" {
			return "windows-arm64"
		}
		if runtime.GOARCH == "386" {
			return "windows-x86"
		}
		return "windows-x64"
	case "darwin":
		if runtime.GOARCH == "arm64" {
			return "mac-os-arm64"
		}
		return "mac-os"
	}
	return "linux"
}

func installJava(ctx context.Context, component string, deep bool) (string, error) {
	if component == "" {
		component = "jre-legacy"
	}
	component = cleanName(component)
	root := filepath.Join(dataDir, "runtime", component)
	exe := filepath.Join(root, "bin", "java")
	if runtime.GOOS == "windows" {
		exe = filepath.Join(root, "bin", "javaw.exe")
	}
	marker := filepath.Join(root, ".installed")

	// Fast path: Java checked within the last 3 days -> no network request at all.
	if !deep {
		if st, err := os.Stat(marker); err == nil && time.Since(st.ModTime()) < 72*time.Hour {
			if _, e := os.Stat(exe); e == nil {
				return exe, nil
			}
		}
	}
	task.set("Checking Java", 0, 0)
	var all map[string]map[string][]struct {
		Manifest struct {
			SHA1 string `json:"sha1"`
			URL  string `json:"url"`
		} `json:"manifest"`
	}
	if err := getJSON(javaRuntimeURL, &all); err != nil {
		if _, e := os.Stat(exe); e == nil {
			return exe, nil // offline but already installed
		}
		return "", err
	}
	entries := all[javaPlatform()][component]
	if len(entries) == 0 {
		return "", fmt.Errorf("no official Java runtime %s for this system", component)
	}
	mf := entries[0].Manifest
	if b, err := os.ReadFile(marker); err == nil && string(b) == mf.SHA1 && !deep {
		if _, e := os.Stat(exe); e == nil {
			now := time.Now()
			os.Chtimes(marker, now, now)
			return exe, nil
		}
	}
	var files struct {
		Files map[string]struct {
			Type       string `json:"type"`
			Executable bool   `json:"executable"`
			Downloads  struct {
				Raw *Artifact `json:"raw"`
			} `json:"downloads"`
		} `json:"files"`
	}
	if err := getJSON(mf.URL, &files); err != nil {
		return "", err
	}
	var jobs []dlJob
	var execs []string
	for name, f := range files.Files {
		dst, err := safeJoin(root, name)
		if err != nil {
			continue
		}
		switch f.Type {
		case "directory":
			os.MkdirAll(dst, 0o755)
		case "file":
			if f.Downloads.Raw != nil {
				jobs = append(jobs, dlJob{URL: f.Downloads.Raw.URL, Dest: dst, SHA1: f.Downloads.Raw.SHA1, Size: f.Downloads.Raw.Size})
				if f.Executable {
					execs = append(execs, dst)
				}
			}
		}
	}
	if err := runDownloads(ctx, "Downloading Java ("+component+")", jobs, true); err != nil {
		return "", err
	}
	for _, e := range execs {
		os.Chmod(e, 0o755)
	}
	os.WriteFile(marker, []byte(mf.SHA1), 0o644)
	return exe, nil
}

// ---------------------------------------------------------------------------
// Natives
// ---------------------------------------------------------------------------

func extractNatives(dir string, libs []resolvedLib) error {
	var sig strings.Builder
	for _, l := range libs {
		if l.Native {
			if st, err := os.Stat(l.Path); err == nil {
				fmt.Fprintf(&sig, "%s|%d\n", l.Path, st.Size())
			}
		}
	}
	done := filepath.Join(dir, ".extracted")
	if b, err := os.ReadFile(done); err == nil && string(b) == sig.String() {
		return nil // same natives as last time: nothing to do
	}
	defer func() { os.WriteFile(done, []byte(sig.String()), 0o644) }()
	os.RemoveAll(dir)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	for _, l := range libs {
		if !l.Native {
			continue
		}
		zr, err := zip.OpenReader(l.Path)
		if err != nil {
			continue
		}
		for _, f := range zr.File {
			skip := f.FileInfo().IsDir()
			for _, ex := range l.Excl {
				if strings.HasPrefix(f.Name, ex) {
					skip = true
				}
			}
			low := strings.ToLower(f.Name)
			if !(strings.HasSuffix(low, ".dll") || strings.HasSuffix(low, ".so") || strings.HasSuffix(low, ".dylib") || strings.HasSuffix(low, ".jnilib")) {
				skip = true
			}
			if skip {
				continue
			}
			dst := filepath.Join(dir, cleanName(f.Name))
			rc, err := f.Open()
			if err != nil {
				continue
			}
			out, err := os.Create(dst)
			if err == nil {
				io.Copy(out, rc)
				out.Close()
			}
			rc.Close()
		}
		zr.Close()
	}
	return nil
}

// ---------------------------------------------------------------------------
// Arguments
// ---------------------------------------------------------------------------

func expandArgs(list []json.RawMessage, features map[string]bool, vars map[string]string) []string {
	var out []string
	for _, raw := range list {
		var s string
		if json.Unmarshal(raw, &s) == nil {
			out = append(out, subst(s, vars))
			continue
		}
		var obj struct {
			Rules []Rule          `json:"rules"`
			Value json.RawMessage `json:"value"`
		}
		if json.Unmarshal(raw, &obj) != nil || !rulesAllow(obj.Rules, features) {
			continue
		}
		var one string
		var many []string
		if json.Unmarshal(obj.Value, &one) == nil {
			out = append(out, subst(one, vars))
		} else if json.Unmarshal(obj.Value, &many) == nil {
			for _, m := range many {
				out = append(out, subst(m, vars))
			}
		}
	}
	return out
}

func subst(s string, vars map[string]string) string {
	for k, v := range vars {
		s = strings.ReplaceAll(s, "${"+k+"}", v)
	}
	return s
}

// buildCommand returns the java command line for a profile.
func buildCommand(ctx context.Context, p *Profile, acc *Account, deep bool, gameDir string) (string, []string, error) {
	v, libs, err := installVersion(ctx, p, deep)
	if err != nil {
		return "", nil, err
	}
	component := ""
	if v.JavaVersion != nil {
		component = v.JavaVersion.Component
	}
	java := strings.TrimSpace(settingString("javaPath"))
	if java == "" {
		java, err = installJava(ctx, component, deep)
		if err != nil {
			return "", nil, err
		}
	}
	task.set("Preparing game", 0, 0)
	nativesDir := filepath.Join(dataDir, "natives", cleanName(v.ID))
	if err := extractNatives(nativesDir, libs); err != nil {
		return "", nil, err
	}
	sep := ":"
	if runtime.GOOS == "windows" {
		sep = ";"
	}
	var cp []string
	seen := map[string]bool{}
	for _, l := range libs {
		if l.OnCP && !seen[l.Path] {
			seen[l.Path] = true
			cp = append(cp, l.Path)
		}
	}
	extraWindow := gameDir != ""
	if gameDir == "" {
		gameDir = profileDir(p)
	}
	assetsRoot := filepath.Join(dataDir, "assets")
	assetID := v.Assets
	if v.AssetIndex != nil {
		assetID = v.AssetIndex.ID
	}
	width, height := p.Width, p.Height
	vars := map[string]string{
		"auth_player_name":    acc.Name,
		"version_name":        v.ID,
		"game_directory":      gameDir,
		"assets_root":         assetsRoot,
		"game_assets":         filepath.Join(assetsRoot, "virtual", cleanName(assetID)),
		"assets_index_name":   assetID,
		"auth_uuid":           acc.UUID,
		"auth_access_token":   acc.AccessToken,
		"auth_session":        "token:" + acc.AccessToken + ":" + acc.UUID,
		"auth_xuid":           acc.XUID,
		"clientid":            "",
		"user_type":           "msa",
		"user_properties":     "{}",
		"version_type":        "TopClient",
		"natives_directory":   nativesDir,
		"launcher_name":       "TopClient",
		"launcher_version":    AppVersion,
		"classpath":           strings.Join(cp, sep),
		"classpath_separator": sep,
		"library_directory":   filepath.Join(dataDir, "libraries"),
		"resolution_width":    fmt.Sprint(width),
		"resolution_height":   fmt.Sprint(height),
	}
	features := map[string]bool{"has_custom_resolution": width > 0 && height > 0}

	ram := p.RamMB
	if ram <= 0 {
		ram = settingInt("ramMB", 4096)
	}
	if ram < 1024 {
		ram = 1024
	}
	extra := p.JavaArgs
	if extra == "" {
		extra = settingString("javaArgs")
	}
	custom := strings.TrimSpace(extra) != ""
	javaMajor := 0
	if v.JavaVersion != nil {
		javaMajor = v.JavaVersion.MajorVersion
	}
	args := memoryArgs(ram, custom, javaMajor)
	if !custom {
		extra = gcArgs(javaMajor)
	}
	args = append(args, strings.Fields(extra)...)
	// Faster starts: Java remembers the classes it loaded last time in a cache file (inside the
	// launcher folder) and maps them straight in next launch. Java 19+ only; rebuilt by itself
	// when mods or versions change. Not for extra windows, so two games never write it at once.
	if v.JavaVersion != nil && v.JavaVersion.MajorVersion >= 19 && !extraWindow && !strings.Contains(extra, "SharedArchiveFile") && settingBool("fastStart", true) {
		cds := filepath.Join(dataDir, "cache", "cds")
		if os.MkdirAll(cds, 0o755) == nil {
			name := cleanName(p.MC+"-"+p.Loader+"-"+p.LoaderVer+"-"+p.ID) + ".jsa"
			args = append(args, "-XX:+AutoCreateSharedArchive", "-XX:SharedArchiveFile="+filepath.Join(cds, name), "-Xshare:auto")
		}
	}
	if v.Arguments != nil && len(v.Arguments.JVM) > 0 {
		args = append(args, expandArgs(v.Arguments.JVM, features, vars)...)
	} else {
		args = append(args, "-Djava.library.path="+nativesDir, "-cp", vars["classpath"])
	}
	if v.Logging != nil && v.Logging.Client != nil && v.ReleaseTime < "2021-12-10" {
		lp := filepath.Join(dataDir, "assets", "log_configs", cleanName(v.Logging.Client.File.ID))
		args = append(args, strings.ReplaceAll(v.Logging.Client.Argument, "${path}", lp))
	}
	args = append(args, v.MainClass)
	if v.Arguments != nil && len(v.Arguments.Game) > 0 {
		args = append(args, expandArgs(v.Arguments.Game, features, vars)...)
	} else {
		for _, a := range strings.Fields(v.MinecraftArguments) {
			args = append(args, subst(a, vars))
		}
		if features["has_custom_resolution"] {
			args = append(args, "--width", vars["resolution_width"], "--height", vars["resolution_height"])
		}
	}
	args = append(args, quickJoinArgs(joinFor(p.ID), v.ReleaseTime)...)
	// Java 9+ reads its arguments from a file ("java @file"), so the sign-in token is not in the
	// game's command line, where Task Manager and other programs can see it. The file is in
	// the launcher's own folder and is deleted again right after Java has read it.
	if javaMajor >= 9 {
		if f, err := writeArgFile(args); err == nil {
			return java, []string{"@" + f}, nil
		} else {
			logf("arg file: %v", err)
		}
	}
	return java, args, nil
}

func argFileQuote(a string) string {
	return `"` + strings.NewReplacer(`\`, `\\`, `"`, `\"`, "\r", "", "\n", " ").Replace(a) + `"`
}

func writeArgFile(args []string) (string, error) {
	dir := filepath.Join(dataDir, "cache", "launch")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	var b strings.Builder
	for _, a := range args {
		b.WriteString(argFileQuote(a))
		b.WriteByte('\n')
	}
	f := filepath.Join(dir, "args-"+randHex(12)+".txt")
	if err := os.WriteFile(f, []byte(b.String()), 0o600); err != nil {
		return "", err
	}
	return f, nil
}

// cleanArgFiles removes the arguments file of a game that just started (and any left over
// from a crash). Java reads it within the first moment of starting.
func cleanArgFiles(args []string) {
	if len(args) == 1 && strings.HasPrefix(args[0], "@") {
		f := args[0][1:]
		time.AfterFunc(60*time.Second, func() { os.Remove(f) })
	}
	dir := filepath.Join(dataDir, "cache", "launch")
	if ents, err := os.ReadDir(dir); err == nil {
		for _, e := range ents {
			if info, err := e.Info(); err == nil && time.Since(info.ModTime()) > 2*time.Minute {
				os.Remove(filepath.Join(dir, e.Name()))
			}
		}
	}
}

// Smooth mode (Settings > Performance, on by default): all the game's memory is set aside
// from the start, so Java never has to grow or shrink it mid-game (that causes hitches). Off: the old behaviour
// (start with half). Custom Java arguments always win.
func memoryArgs(ram int, custom bool, javaMajor int) []string {
	args := []string{fmt.Sprintf("-Xmx%dM", ram)}
	// Only when the PC has room to spare, so a big memory setting can never stop the game starting.
	if settingBool("gameSmooth", true) && !custom && ram <= totalRAMMB()*7/10 {
		args = append(args, fmt.Sprintf("-Xms%dM", ram)) // reserved up front, filled in as the game needs it (fast start)
	} else {
		args = append(args, fmt.Sprintf("-Xms%dM", max(1024, min(ram, ram/2))))
	}
	return args
}

// gcArgs picks the garbage collector. "g1" (default): short, frequent collections tuned for
// a game client. "zgc": Java 21's generational ZGC, pauses well under a millisecond (uses a
// bit more CPU and memory) - only used when the game's Java is new enough.
func gcArgs(javaMajor int) string {
	if settingString("gcMode") == "zgc" && javaMajor >= 21 {
		return "-XX:+UseZGC -XX:+ZGenerational -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -XX:+UseStringDeduplication"
	}
	return "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=37 -XX:G1NewSizePercent=23 -XX:G1MaxNewSizePercent=50 " +
		"-XX:G1ReservePercent=20 -XX:G1HeapRegionSize=16M -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 " +
		"-XX:G1MixedGCLiveThresholdPercent=90 -XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1 " +
		"-XX:+ParallelRefProcEnabled -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -XX:+UseStringDeduplication"
}

// ---------------------------------------------------------------------------
// Running games
// ---------------------------------------------------------------------------

type GameProc struct {
	mu      sync.Mutex
	Lines   []string
	Base    int // index of Lines[0]
	Running bool
	Started time.Time
	kill    func()
	killed  bool
}

var (
	gamesMu sync.Mutex
	games   = map[string]*GameProc{}
)

func (g *GameProc) add(line string) {
	g.mu.Lock()
	g.Lines = append(g.Lines, line)
	if len(g.Lines) > 3000 {
		drop := len(g.Lines) - 3000
		g.Lines = g.Lines[drop:]
		g.Base += drop
	}
	g.mu.Unlock()
}

func anyGameRunning() bool {
	gamesMu.Lock()
	defer gamesMu.Unlock()
	for _, g := range games {
		if g.Running {
			return true
		}
	}
	return false
}

func runningList() []string {
	gamesMu.Lock()
	defer gamesMu.Unlock()
	out := []string{}
	seen := map[string]bool{}
	for id, g := range games {
		if g.Running {
			base, _, _ := strings.Cut(id, "~")
			if !seen[base] {
				seen[base] = true
				out = append(out, base)
			}
		}
	}
	sort.Strings(out)
	return out
}

// instanceList lists every running game slot (extra copies are "id~2", "id~3"...).
func instanceList() []string {
	gamesMu.Lock()
	defer gamesMu.Unlock()
	out := []string{}
	for id, g := range games {
		if g.Running {
			out = append(out, id)
		}
	}
	sort.Strings(out)
	return out
}

// fabricSupports: Fabric's first supported version is snapshot 18w43b (2018-10-24); 1.14 came after.
func fabricSupports(releaseTime string) bool {
	return releaseTime == "" || releaseTime >= "2018-10-24"
}
