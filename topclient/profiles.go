package main

import (
	"bufio"
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Profile struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	MC         string `json:"mc"`
	Loader     string `json:"loader"` // "vanilla" | "fabric"
	LoaderVer  string `json:"loaderVer"`
	RamMB      int    `json:"ramMB"`
	JavaArgs   string `json:"javaArgs"`
	Width      int    `json:"width"`
	Height     int    `json:"height"`
	Icon       string `json:"icon"`
	Created    int64  `json:"created"`
	LastPlayed int64  `json:"lastPlayed"`
	PlayTime   int64  `json:"playTime"`
	FPSBoost   string `json:"fpsBoost,omitempty"` // "" (off) | "light" | "balanced" | "max"
	PerfMods   bool   `json:"perfMods,omitempty"`
	Server     string `json:"server,omitempty"` // Bedrock profiles: optional server to add (host[:port])
}

var (
	stateMu  sync.Mutex
	profiles []*Profile
	settings = map[string]any{}
)

func profileDir(p *Profile) string {
	return filepath.Join(dataDir, "profiles", cleanName(p.ID))
}

func findProfile(id string) *Profile {
	stateMu.Lock()
	defer stateMu.Unlock()
	for _, p := range profiles {
		if p.ID == id {
			return p
		}
	}
	return nil
}

func loadState() {
	readJSON(filepath.Join(dataDir, "settings.json"), &settings)
	if settings == nil {
		settings = map[string]any{}
	}
	applyDefaultSettings(settings)
	readJSON(filepath.Join(dataDir, "profiles.json"), &profiles)
	if len(profiles) == 0 {
		profiles = []*Profile{{ID: "main", Name: "Main", MC: "latest", Loader: "fabric", Icon: "grass", Created: time.Now().Unix()}}
	}
	saveProfiles()
	for _, p := range profiles {
		os.MkdirAll(profileDir(p), 0o755)
	}
	loadAccounts()
}

func saveProfiles() {
	stateMu.Lock()
	defer stateMu.Unlock()
	writeJSON(filepath.Join(dataDir, "profiles.json"), profiles)
}

func saveSettings() {
	stateMu.Lock()
	defer stateMu.Unlock()
	writeJSON(filepath.Join(dataDir, "settings.json"), settings)
}

func settingString(k string) string {
	stateMu.Lock()
	defer stateMu.Unlock()
	s, _ := settings[k].(string)
	return s
}

func settingInt(k string, def int) int {
	stateMu.Lock()
	defer stateMu.Unlock()
	if f, ok := settings[k].(float64); ok && f > 0 {
		return int(f)
	}
	return def
}

func newProfile(name, mc, loader string) *Profile {
	if loader != "fabric" {
		loader = "vanilla"
	}
	name = strings.TrimSpace(name)
	if name == "" {
		name = "Profile"
	}
	if len(name) > 40 {
		name = name[:40]
	}
	p := &Profile{ID: randHex(6), Name: name, MC: mc, Loader: loader, Icon: "grass", Created: time.Now().Unix()}
	stateMu.Lock()
	profiles = append(profiles, p)
	stateMu.Unlock()
	os.MkdirAll(profileDir(p), 0o755)
	saveProfiles()
	return p
}

// ---------------------------------------------------------------------------
// Content (mods, resource packs, shaders)
// ---------------------------------------------------------------------------

var contentDirs = map[string]string{"mods": "mods", "resourcepacks": "resourcepacks", "shaderpacks": "shaderpacks"}

type ContentFile struct {
	File    string `json:"file"`
	Enabled bool   `json:"enabled"`
	Size    int64  `json:"size"`
	Project string `json:"project,omitempty"`
	Title   string `json:"title,omitempty"`
	Icon    string `json:"icon,omitempty"`
	Banner  string `json:"banner,omitempty"`
	Bundled bool   `json:"bundled,omitempty"`
	ModID   string `json:"modId,omitempty"`
}

type installedMeta struct {
	Project string `json:"project"`
	Version string `json:"version"`
	Title   string `json:"title"`
	Icon    string `json:"icon"`
	Banner  string `json:"banner,omitempty"`
}

func metaPath(p *Profile) string { return filepath.Join(profileDir(p), ".topclient", "installed.json") }

func readMeta(p *Profile) map[string]installedMeta {
	m := map[string]installedMeta{}
	readJSON(metaPath(p), &m)
	return m
}

var metaMu sync.Mutex

func setMeta(p *Profile, file string, im installedMeta) {
	metaMu.Lock()
	defer metaMu.Unlock()
	m := readMeta(p)
	m[file] = im
	writeJSON(metaPath(p), m)
}

func listContent(p *Profile, kind string) ([]ContentFile, bool, error) {
	sub, ok := contentDirs[kind]
	if !ok {
		return nil, false, errors.New("unknown content type")
	}
	dir := filepath.Join(profileDir(p), sub)
	entries, _ := os.ReadDir(dir)
	var names []string
	for _, e := range entries {
		if !e.IsDir() {
			names = append(names, e.Name())
		}
	}
	cache := scanJars(p, dir, names)
	meta := readMeta(p)
	out := []ContentFile{}
	pending := false
	now := time.Now().Unix()
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		enabled := !strings.HasSuffix(name, ".disabled")
		base := strings.TrimSuffix(name, ".disabled")
		info, _ := e.Info()
		cf := ContentFile{File: name, Enabled: enabled}
		if info != nil {
			cf.Size = info.Size()
		}
		h := cache[base]
		cf.Title, cf.ModID = h.Name, h.ModID
		if h.HasIcon {
			cf.Icon = "/api/profiles/modicon?id=" + url.QueryEscape(p.ID) + "&kind=" + kind + "&file=" + url.QueryEscape(name) + "&v=" + strconv.FormatInt(h.Mod, 10)
		}
		if m, ok := meta[base]; ok {
			cf.Project = m.Project
			if m.Title != "" {
				cf.Title = m.Title
			}
			if m.Icon != "" {
				cf.Icon = m.Icon
			}
			cf.Banner = m.Banner
		} else if h.SHA1 != "" && (h.Checked == 0 || now-h.Checked > 3*86400) {
			pending = true
		}
		if kind == "mods" && strings.TrimSuffix(name, ".disabled") == topClientFile {
			v, _ := topClientInfo()
			cf.Bundled, cf.Title, cf.ModID = true, "TopClient "+v+" (built in)", "topclient"
		}
		out = append(out, cf)
	}
	if pending {
		identifyAsync(p, dir, names)
	}
	sort.Slice(out, func(i, j int) bool {
		ti, tj := out[i].Title, out[j].Title
		if ti == "" {
			ti = out[i].File
		}
		if tj == "" {
			tj = out[j].File
		}
		return strings.ToLower(ti) < strings.ToLower(tj)
	})
	return out, pending, nil
}

func contentPath(p *Profile, kind, file string) (string, error) {
	sub, ok := contentDirs[kind]
	if !ok {
		return "", errors.New("unknown content type")
	}
	file = cleanName(file)
	return safeJoin(filepath.Join(profileDir(p), sub), file)
}

// ---------------------------------------------------------------------------
// Launch
// ---------------------------------------------------------------------------

var launchCancel context.CancelFunc

// instanceKey picks the console/log slot for a launch: the profile id for the first
// copy, then "id~2", "id~3"... when more instances are allowed. "" means no slot.
func instanceKey(p *Profile, another bool) string {
	gamesMu.Lock()
	defer gamesMu.Unlock()
	if g := games[p.ID]; g == nil || !g.Running {
		return p.ID
	}
	if !another {
		return ""
	}
	for n := 2; n <= 8; n++ {
		k := p.ID + "~" + strconv.Itoa(n)
		if g := games[k]; g == nil || !g.Running {
			return k
		}
	}
	return ""
}

func launch(p *Profile, deep, another bool) error {
	acc := activeAccount()
	if acc == nil {
		return errors.New("Log in with your Microsoft account first")
	}
	if another {
		stateMu.Lock()
		allowed, _ := settings["multiInstance"].(bool)
		stateMu.Unlock()
		another = allowed
	}
	key := instanceKey(p, another)
	if key == "" {
		if another {
			return errors.New("That's the most copies of one profile at once (8)")
		}
		return errors.New("This profile is already running. Turn on \"More than one game at once\" in Settings to open another copy")
	}
	if !task.begin(p.ID) {
		return errors.New("Another install is in progress")
	}
	go func() {
		ctx, cancel := context.WithCancel(context.Background())
		launchCancel = cancel
		defer cancel()
		err := doLaunch(ctx, p, deep, key)
		task.end(err)
	}()
	return nil
}

func doLaunch(ctx context.Context, p *Profile, deep bool, key string) error {
	task.set("Signing in", 0, 0)
	acc, err := ensureFreshAccount()
	if err != nil {
		return err
	}
	gameDir := ""
	if key != p.ID {
		task.set("Setting up another window", 0, 0)
		if gameDir, err = prepareCopyDir(p, key); err != nil {
			return err
		}
	}
	java, args, err := buildCommand(ctx, p, acc, deep, gameDir)
	if err != nil {
		return err
	}
	if ctx.Err() != nil {
		return errors.New("cancelled")
	}
	// Another copy of this profile is already playing: its files are in use and
	// already set up, so leave them alone.
	if key == p.ID && settingBool("autoBackup", false) {
		task.set("Backing up your worlds", 0, 0)
		backupAllJava(p)
	}
	if key == p.ID {
		applyFPSBoost(p)
	}
	tcNote := ""
	if key == p.ID {
		task.set("Adding the TopClient mod", 0, 0)
		tcNote = syncTopClientMod(p)
	}
	task.set("Starting Minecraft", 0, 0)
	cmd := exec.Command(java, args...)
	cmd.Dir = profileDir(p)
	if gameDir != "" {
		cmd.Dir = gameDir
	}
	gameWindow(cmd)
	stdout, _ := cmd.StdoutPipe()
	cmd.Stderr = cmd.Stdout
	g := &GameProc{Running: true, Started: time.Now()}
	if err := cmd.Start(); err != nil {
		cleanArgFiles(args)
		return fmt.Errorf("could not start Java: %v", err)
	}
	cleanArgFiles(args)
	g.kill = func() { cmd.Process.Kill() }
	if settingBool("gameSmooth", true) {
		ram := p.RamMB
		if ram <= 0 {
			ram = settingInt("ramMB", 4096)
		}
		prio := settingString("gamePriority")
		go tuneGameProcess(cmd.Process.Pid, ram, prio)
	}
	gamesMu.Lock()
	games[key] = g
	gamesMu.Unlock()
	copyNote := ""
	if key != p.ID {
		copyNote = " - copy " + strings.TrimPrefix(key, p.ID+"~")
	}
	g.add(fmt.Sprintf("[TopClient] Launching %s%s (%s %s) as %s", p.Name, copyNote, p.Loader, p.MC, acc.Name))
	if tcNote != "" {
		g.add("[TopClient] " + tcNote)
	}
	p.LastPlayed = time.Now().Unix()
	saveProfiles()
	takeJoin(p.ID)
	go func() {
		sc := bufio.NewScanner(stdout)
		sc.Buffer(make([]byte, 64*1024), 1024*1024)
		for sc.Scan() {
			g.add(redactSecrets(strings.ReplaceAll(sc.Text(), acc.AccessToken, "<token>")))
		}
		io.Copy(io.Discard, stdout)
		err := cmd.Wait()
		code := 0
		if cmd.ProcessState != nil {
			code = cmd.ProcessState.ExitCode()
		}
		g.add(fmt.Sprintf("[TopClient] Game closed (exit code %d)", code))
		_ = err
		g.mu.Lock()
		g.Running = false
		killed := g.killed
		g.mu.Unlock()
		played := int64(time.Since(g.Started).Seconds())
		p.PlayTime += played
		saveProfiles()
		noteGameExit(p, cmd.Dir, g.Started, code, killed)
	}()
	return nil
}

func gameLogs(id string, since int) (lines []string, next int, running bool) {
	gamesMu.Lock()
	g := games[id]
	gamesMu.Unlock()
	if g == nil {
		return nil, 0, false
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	start := since - g.Base
	if start < 0 {
		start = 0
	}
	if start < len(g.Lines) {
		lines = append(lines, g.Lines[start:]...)
	}
	return lines, g.Base + len(g.Lines), g.Running
}

func killGame(id string) {
	gamesMu.Lock()
	g := games[id]
	gamesMu.Unlock()
	if g != nil && g.kill != nil {
		g.mu.Lock()
		g.killed = true
		g.mu.Unlock()
		g.kill()
	}
}

// Export settings (no account data, ever).
func exportSettings() ([]byte, error) {
	stateMu.Lock()
	defer stateMu.Unlock()
	cp := map[string]any{}
	for k, v := range settings {
		if k == "background" {
			continue
		}
		cp[k] = v
	}
	return json.MarshalIndent(map[string]any{"topclient": AppVersion, "settings": cp}, "", "  ")
}

//go:embed defaults.json
var defaultSettingsJSON []byte

// applyDefaultSettings fills in the launcher's default look (theme, transparency,
// HUD layout, cape) for anything the player has not set yet. Personal things such
// as accounts, RAM or the selected profile are never part of the defaults.
func applyDefaultSettings(m map[string]any) {
	var d map[string]any
	if json.Unmarshal(defaultSettingsJSON, &d) != nil {
		return
	}
	for k, v := range d {
		if _, ok := m[k]; !ok {
			m[k] = v
		}
	}
}

// lastJavaProfile: the selected profile if it's a Java one, otherwise the Java profile played last.
func lastJavaProfile() *Profile {
	if p := findProfile(settingString("selectedProfile")); p != nil && !isBedrock(p) {
		return p
	}
	stateMu.Lock()
	defer stateMu.Unlock()
	var best *Profile
	for _, p := range profiles {
		if !isBedrock(p) && (best == nil || p.LastPlayed > best.LastPlayed) {
			best = p
		}
	}
	return best
}
