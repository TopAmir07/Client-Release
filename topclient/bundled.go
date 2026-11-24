package main

// The TopClient mod ships inside the launcher. Fabric profiles on a Minecraft version the mod is
// made for get it put in their mods folder when you press PLAY (plus Fabric API from Modrinth if
// the profile doesn't have it yet). Nothing is downloaded for the mod itself. "TopClient mod" in
// Settings turns this off, and then the launcher takes its copy out again.

import (
	"archive/zip"
	"bytes"
	"crypto/sha1"
	_ "embed"
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

//go:embed bundled/TopClient.jar
var topClientJar []byte

const topClientFile = "TopClient.jar"

var (
	tcInfoOnce sync.Once
	tcVersion  string
	tcMCRange  string
)

func topClientInfo() (version, mcRange string) {
	tcInfoOnce.Do(func() {
		zr, err := zip.NewReader(bytes.NewReader(topClientJar), int64(len(topClientJar)))
		if err != nil {
			return
		}
		for _, f := range zr.File {
			if f.Name != "fabric.mod.json" {
				continue
			}
			rc, err := f.Open()
			if err != nil {
				return
			}
			var fm struct {
				Version string         `json:"version"`
				Depends map[string]any `json:"depends"`
			}
			json.NewDecoder(rc).Decode(&fm)
			rc.Close()
			tcVersion = fm.Version
			if s, ok := fm.Depends["minecraft"].(string); ok {
				tcMCRange = s
			}
		}
	})
	return tcVersion, tcMCRange
}

// topClientFits says if the mod is made for this Minecraft version ("~26.2" fits 26.2 and 26.2.x).
func topClientFits(mc string) bool {
	_, rng := topClientInfo()
	r := strings.TrimSpace(strings.TrimLeft(strings.TrimSpace(rng), "~^>="))
	if r == "" || r == "*" {
		return true
	}
	return mc == r || strings.HasPrefix(mc, r+".")
}

// TopClientMC is the Minecraft version the built-in mod is for (shown in the UI).
func topClientMC() string {
	_, rng := topClientInfo()
	return strings.TrimLeft(strings.TrimSpace(rng), "~^>=")
}

func profileMC(p *Profile) string {
	mc := p.MC
	if mc == "" || mc == "latest" {
		if p.Loader == "fabric" {
			if v, err := latestFabricGame(); err == nil {
				return v
			}
		}
		if m, err := getManifest(); err == nil && m != nil {
			return m.Latest.Release
		}
	}
	return mc
}

// syncTopClientMod puts the built-in mod in (or takes it out of) the profile's mods folder.
// Returns a note for the game log, or "".
func syncTopClientMod(p *Profile) string {
	dir := filepath.Join(profileDir(p), "mods")
	ours := filepath.Join(dir, topClientFile)
	want := p.Loader == "fabric" && settingBool("topclientMod", true) && topClientFits(profileMC(p))
	if !want {
		if _, err := os.Stat(ours); err == nil {
			os.Remove(ours)
			return "TopClient mod taken out (off in Settings, or not made for this Minecraft version)"
		}
		return ""
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return ""
	}
	if have, err := os.ReadFile(ours); err != nil || sha1.Sum(have) != sha1.Sum(topClientJar) {
		tmp := ours + ".part"
		if err := os.WriteFile(tmp, topClientJar, 0o644); err == nil {
			os.Remove(ours)
			os.Rename(tmp, ours)
		}
	}
	// any other copy of the TopClient mod (an older jar you put there yourself) is switched off
	ents, _ := os.ReadDir(dir)
	for _, e := range ents {
		n := e.Name()
		if n == topClientFile || !strings.HasSuffix(strings.ToLower(n), ".jar") {
			continue
		}
		if jarModID(filepath.Join(dir, n)) == "topclient" {
			if os.Rename(filepath.Join(dir, n), filepath.Join(dir, n+".disabled")) == nil {
				logf("switched off %s (the launcher has the built-in TopClient mod)", n)
			}
		}
	}
	if !hasFabricAPI(p) {
		if err := installProject(p, fabricAPIProject, "", 0, map[string]bool{}); err != nil {
			logf("Fabric API for TopClient: %v", err)
			return "TopClient needs Fabric API, which couldn't be downloaded: " + err.Error()
		}
	}
	v, _ := topClientInfo()
	return "TopClient mod " + v + " is in this profile"
}

// jarModID reads the mod id from a jar's fabric.mod.json ("" if it has none).
func jarModID(path string) string {
	zr, err := zip.OpenReader(path)
	if err != nil {
		return ""
	}
	defer zr.Close()
	for _, f := range zr.File {
		if f.Name != "fabric.mod.json" {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return ""
		}
		b, _ := io.ReadAll(io.LimitReader(rc, 1<<20))
		rc.Close()
		var fm struct {
			ID string `json:"id"`
		}
		json.Unmarshal(b, &fm)
		return fm.ID
	}
	return ""
}
