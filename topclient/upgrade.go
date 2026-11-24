package main

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// ---------------------------------------------------------------------------
// "Update to another version"
//
// Makes a brand-new profile on the chosen Minecraft version and fills it with
// the newest Modrinth build of every mod / resource pack / shader the old
// profile has. The old profile is only ever READ: nothing in it is moved,
// changed or deleted.
// ---------------------------------------------------------------------------

type UpgradeItem struct {
	Kind string `json:"kind"` // mods | resourcepacks | shaderpacks
	Name string `json:"name"`
	File string `json:"file"`
	Note string `json:"note,omitempty"`
	Off  bool   `json:"off,omitempty"` // was switched off in the old profile (kept off)
}

type UpgradeResult struct {
	Profile  *Profile      `json:"profile"`
	Updated  []UpgradeItem `json:"updated"`  // newest Modrinth build for the new version
	Copied   []UpgradeItem `json:"copied"`   // packs that are not on Modrinth, copied as they were
	Missing  []UpgradeItem `json:"missing"`  // no build for the new version (or not on Modrinth)
	Settings bool          `json:"settings"` // options / servers / configs copied
	Worlds   int           `json:"worlds"`   // worlds copied (only when asked)
}

type UpgradeOptions struct {
	ID       string `json:"id"`
	MC       string `json:"mc"`
	Name     string `json:"name"`
	Settings bool   `json:"settings"`
	Worlds   bool   `json:"worlds"`
}

// upgradeName makes "My Pack (1.21.4)", keeping it inside the 40 character limit.
func upgradeName(old, mc, want string) string {
	if w := strings.TrimSpace(want); w != "" {
		return w
	}
	old = strings.TrimSpace(old)
	// drop an earlier " (1.x.y)" so repeated upgrades don't stack up
	if i := strings.LastIndex(old, " ("); i > 0 && strings.HasSuffix(old, ")") && len(verParts(old[i+2:len(old)-1])) >= 2 {
		old = old[:i]
	}
	suffix := " (" + mc + ")"
	if max := 40 - len(suffix); len(old) > max {
		old = strings.TrimSpace(old[:max])
	}
	return old + suffix
}

// sensitiveName is true for anything that might hold sign-in data. Those are
// never read or copied, whatever folder they sit in.
func sensitiveName(name string) bool {
	l := strings.ToLower(name)
	for _, s := range []string{"account", "_ias_", "token", "session", "auth", "credential", "password", "secret", "login"} {
		if strings.Contains(l, s) {
			return true
		}
	}
	return false
}

// safeCopyTree copies src into dst like copyTree, but skips anything that
// could hold sign-in data, never follows links, and never overwrites a file
// that already exists in dst.
func safeCopyTree(src, dst string, maxFile int64) int {
	n := 0
	filepath.WalkDir(src, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if path != src && sensitiveName(d.Name()) {
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		rel, err := filepath.Rel(src, path)
		if err != nil || strings.HasPrefix(rel, "..") {
			return nil
		}
		target := filepath.Join(dst, rel)
		if d.IsDir() {
			os.MkdirAll(target, 0o755)
			return nil
		}
		if !d.Type().IsRegular() {
			return nil // links, devices etc.
		}
		if st, e := d.Info(); e != nil || (maxFile > 0 && st.Size() > maxFile) {
			return nil
		}
		if _, e := os.Stat(target); e == nil {
			return nil
		}
		if copyFile(path, target) == nil {
			n++
		}
		return nil
	})
	return n
}

func copyOneFile(src, dst string) bool {
	st, err := os.Lstat(src)
	if err != nil || !st.Mode().IsRegular() {
		return false
	}
	if _, e := os.Stat(dst); e == nil {
		return false
	}
	os.MkdirAll(filepath.Dir(dst), 0o755)
	return copyFile(src, dst) == nil
}

func checkUpgradeTarget(old *Profile, mc string) error {
	mc = strings.TrimSpace(mc)
	if mc == "" || mc == "latest" {
		return errors.New("pick the Minecraft version to update to")
	}
	if mc == old.MC {
		return fmt.Errorf("this profile is already on %s", mc)
	}
	m, err := getManifest()
	if err != nil {
		return fmt.Errorf("couldn't load the Minecraft version list: %v", err)
	}
	ok := false
	for _, v := range m.Versions {
		if v.ID == mc {
			ok = true
			break
		}
	}
	if !ok {
		return fmt.Errorf("Minecraft %s doesn't exist", mc)
	}
	if old.Loader == "fabric" {
		if _, err := latestFabricLoader(mc); err != nil {
			return err
		}
	}
	return nil
}

func upgradeProfile(in UpgradeOptions) (*UpgradeResult, error) {
	old := findProfile(in.ID)
	if old == nil {
		return nil, errors.New("profile not found")
	}
	if isBedrock(old) {
		return nil, errors.New("Bedrock updates itself through the Microsoft Store")
	}
	mc := strings.TrimSpace(in.MC)
	task.set("Backing up your worlds", 0, 0)
	backupAllJava(old) // a safety copy before anything moves
	task.set("Checking Minecraft "+mc, 0, 0)
	if err := checkUpgradeTarget(old, mc); err != nil {
		return nil, err
	}
	oldDir := profileDir(old)

	// 1. Work out what's in the old profile (read only).
	type item struct {
		kind, file, base string
		off              bool
	}
	var items []item
	bundled := map[string]bool{}
	for _, kind := range []string{"mods", "resourcepacks", "shaderpacks"} {
		dir := filepath.Join(oldDir, contentDirs[kind])
		ents, _ := os.ReadDir(dir)
		var names []string
		for _, e := range ents {
			if !e.Type().IsRegular() {
				continue
			}
			base := strings.TrimSuffix(e.Name(), ".disabled")
			if kind == "mods" && bundled[base] {
				continue // built-in mod placeholder
			}
			names = append(names, e.Name())
			items = append(items, item{kind, e.Name(), base, base != e.Name()})
		}
		if len(names) > 0 {
			task.set("Looking up "+kind+" on Modrinth", 0, 0)
			identifyContent(old, dir, names) // only adds names/links to the launcher's own notes
		}
	}
	meta := readMeta(old)

	// 2. Make the new profile, copying the old one's launcher settings.
	stateMu.Lock()
	name, loader := upgradeName(old.Name, mc, in.Name), old.Loader
	ram, args, w, h, icon, fps, perf := old.RamMB, old.JavaArgs, old.Width, old.Height, old.Icon, old.FPSBoost, old.PerfMods
	stateMu.Unlock()
	np := newProfile(name, mc, loader)
	stateMu.Lock()
	np.RamMB, np.JavaArgs, np.Width, np.Height, np.PerfMods = ram, args, w, h, perf
	if icon != "" && !strings.HasPrefix(icon, "custom") {
		np.Icon = icon
	}
	stateMu.Unlock()
	if strings.HasPrefix(icon, "custom") {
		if copyOneFile(profileIconPath(old), profileIconPath(np)) {
			stateMu.Lock()
			np.Icon = icon
			stateMu.Unlock()
		}
	}
	saveProfiles()
	newDir := profileDir(np)
	res := &UpgradeResult{Profile: np, Updated: []UpgradeItem{}, Copied: []UpgradeItem{}, Missing: []UpgradeItem{}}

	// 3. Settings (optional, copy only).
	if in.Settings {
		task.set("Copying your settings", 0, 0)
		for _, f := range []string{"options.txt", "servers.dat", "optionsof.txt", "optionsshaders.txt"} {
			copyOneFile(filepath.Join(oldDir, f), filepath.Join(newDir, f))
		}
		safeCopyTree(filepath.Join(oldDir, "config"), filepath.Join(newDir, "config"), 16<<20)
		if fps != "" {
			// keep the FPS Booster on, with the same "your own settings" backup so turning it off still works
			if copyOneFile(fpsBackupPath(old), fpsBackupPath(np)) {
				stateMu.Lock()
				np.FPSBoost = fps
				stateMu.Unlock()
				saveProfiles()
			}
		}
		res.Settings = true
	}
	if in.Worlds {
		task.set("Copying your worlds", 0, 0)
		if ents, err := os.ReadDir(filepath.Join(oldDir, "saves")); err == nil {
			for _, e := range ents {
				if e.IsDir() && !sensitiveName(e.Name()) {
					safeCopyTree(filepath.Join(oldDir, "saves", e.Name()), filepath.Join(newDir, "saves", e.Name()), 0)
					res.Worlds++
				}
			}
		}
	}

	// 4. Content: newest Modrinth build for the new version.
	done := map[string]bool{}
	ptype := map[string]string{"mods": "mod", "resourcepacks": "resourcepack", "shaderpacks": "shader"}
	for i, it := range items {
		task.set(fmt.Sprintf("Updating %d of %d", i+1, len(items)), int64(i), int64(len(items)))
		m, known := meta[it.base]
		label := m.Title
		if label == "" {
			label = strings.TrimSuffix(strings.TrimSuffix(it.base, ".jar"), ".zip")
		}
		ui := UpgradeItem{Kind: it.kind, Name: label, File: it.base, Off: it.off}
		if !known || m.Project == "" {
			if it.kind == "mods" {
				ui.Note = "not on Modrinth, so it can't be updated automatically"
				res.Missing = append(res.Missing, ui)
			} else if copyOneFile(filepath.Join(oldDir, contentDirs[it.kind], it.file), filepath.Join(newDir, contentDirs[it.kind], it.file)) {
				ui.Note = "not on Modrinth — copied as it was"
				res.Copied = append(res.Copied, ui)
			}
			continue
		}
		if done[m.Project] {
			// already pulled in (e.g. as another mod's dependency)
			for f, am := range readMeta(np) {
				if am.Project == m.Project {
					ui.File, ui.Note = f, "added with another mod"
					res.Updated = append(res.Updated, ui)
					break
				}
			}
			continue
		}
		if it.kind == "mods" && loader != "fabric" {
			ui.Note = "mods need a Fabric profile"
			res.Missing = append(res.Missing, ui)
			continue
		}
		v, err := mrPickVersion(m.Project, mc, "fabric", ptype[it.kind])
		if err != nil || v == nil {
			if it.kind != "mods" && copyOneFile(filepath.Join(oldDir, contentDirs[it.kind], it.file), filepath.Join(newDir, contentDirs[it.kind], it.file)) {
				ui.Note = "no " + mc + " build — copied as it was (packs usually still work)"
				res.Copied = append(res.Copied, ui)
				continue
			}
			ui.Note = "no build for " + mc + " on Modrinth yet"
			res.Missing = append(res.Missing, ui)
			continue
		}
		before := readMeta(np)
		if err := installProject(np, m.Project, v.ID, 0, done); err != nil {
			ui.Note = err.Error()
			res.Missing = append(res.Missing, ui)
			continue
		}
		// find the file it just added so we can report it and keep "off" mods off
		after := readMeta(np)
		for f, am := range after {
			if am.Project == m.Project {
				if _, had := before[f]; !had || before[f].Version != am.Version {
					ui.File = f
				}
			}
		}
		if it.off && ui.File != "" {
			p := filepath.Join(newDir, contentDirs[it.kind], cleanName(ui.File))
			os.Rename(p, p+".disabled")
		}
		ui.Note = v.VersionNum
		res.Updated = append(res.Updated, ui)
	}
	sort.SliceStable(res.Missing, func(i, j int) bool {
		return strings.ToLower(res.Missing[i].Name) < strings.ToLower(res.Missing[j].Name)
	})
	logf("upgrade: %s -> %s (%s): %d updated, %d copied, %d missing", old.ID, np.ID, mc, len(res.Updated), len(res.Copied), len(res.Missing))
	return res, nil
}
