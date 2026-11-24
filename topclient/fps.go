package main

import (
	"bufio"
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// FPS Booster. Off unless the player turns it on for a profile. It only ever changes
// that profile's own options.txt (inside the launcher's folder), remembers the old
// values first and puts them back when the booster is turned off. Nothing else on the
// computer is touched: no drivers, no Windows settings, no other programs.

var fpsPresets = map[string][][2]string{
	"light": {
		{"renderDistance", "10"}, {"simulationDistance", "8"}, {"particles", "1"},
		{"biomeBlendRadius", "1"}, {"renderClouds", "\"fast\""}, {"entityShadows", "false"},
		{"enableVsync", "false"}, {"maxFps", "260"}, {"menuBackgroundBlurriness", "0"},
	},
	"balanced": {
		{"renderDistance", "8"}, {"simulationDistance", "6"}, {"graphicsMode", "0"}, {"particles", "1"},
		{"biomeBlendRadius", "0"}, {"mipmapLevels", "2"}, {"renderClouds", "\"false\""},
		{"entityShadows", "false"}, {"entityDistanceScaling", "0.75"}, {"enableVsync", "false"}, {"maxFps", "260"}, {"menuBackgroundBlurriness", "0"},
	},
	"max": {
		{"renderDistance", "6"}, {"simulationDistance", "5"}, {"graphicsMode", "0"}, {"ao", "false"},
		{"particles", "2"}, {"biomeBlendRadius", "0"}, {"mipmapLevels", "0"}, {"renderClouds", "\"false\""},
		{"entityShadows", "false"}, {"entityDistanceScaling", "0.5"}, {"enableVsync", "false"}, {"maxFps", "260"}, {"menuBackgroundBlurriness", "0"},
	},
}

// Well-known open-source performance mods from Modrinth (only added when the player asks).
var perfModIDs = []struct{ ID, Name string }{
	{"AANobbMI", "Sodium"},
	{"gvQqBUqZ", "Lithium"},
	{"uXXizFIs", "FerriteCore"},
	{"NNAgCjsB", "Entity Culling"},
	{"5ZwdcRci", "ImmediatelyFast"},
}

func optionsPath(p *Profile) string   { return filepath.Join(profileDir(p), "options.txt") }
func fpsBackupPath(p *Profile) string { return filepath.Join(profileDir(p), "topclient-fps-backup.json") }

func readOptions(path string) ([]string, map[string]string) {
	var lines []string
	vals := map[string]string{}
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, vals
	}
	sc := bufio.NewScanner(bytes.NewReader(b))
	sc.Buffer(make([]byte, 64*1024), 1024*1024)
	for sc.Scan() {
		l := sc.Text()
		lines = append(lines, l)
		if k, v, ok := strings.Cut(l, ":"); ok {
			vals[k] = v
		}
	}
	return lines, vals
}

func writeOptions(path string, lines []string, set map[string]string, remove map[string]bool) error {
	done := map[string]bool{}
	var out []string
	for _, l := range lines {
		k, _, ok := strings.Cut(l, ":")
		if ok {
			if remove[k] {
				continue
			}
			if v, has := set[k]; has {
				out = append(out, k+":"+v)
				done[k] = true
				continue
			}
		}
		out = append(out, l)
	}
	for k, v := range set {
		if !done[k] {
			out = append(out, k+":"+v)
		}
	}
	os.MkdirAll(filepath.Dir(path), 0o755)
	tmp := path + ".topclient-tmp"
	if err := os.WriteFile(tmp, []byte(strings.Join(out, "\n")+"\n"), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// applyFPSBoost runs right before the game starts.
func applyFPSBoost(p *Profile) {
	preset, ok := fpsPresets[p.FPSBoost]
	if !ok {
		return
	}
	lines, vals := readOptions(optionsPath(p))
	// Remember what the player had, once, so turning the booster off restores it.
	var backup map[string]*string
	if readJSON(fpsBackupPath(p), &backup) != nil || backup == nil {
		backup = map[string]*string{}
		for _, kv := range fpsPresets["max"] {
			if v, has := vals[kv[0]]; has {
				vv := v
				backup[kv[0]] = &vv
			} else {
				backup[kv[0]] = nil
			}
		}
		for _, kv := range fpsPresets["balanced"] {
			if _, seen := backup[kv[0]]; !seen {
				if v, has := vals[kv[0]]; has {
					vv := v
					backup[kv[0]] = &vv
				} else {
					backup[kv[0]] = nil
				}
			}
		}
		writeJSON(fpsBackupPath(p), backup)
	}
	set := map[string]string{}
	for _, kv := range preset {
		set[kv[0]] = kv[1]
	}
	if err := writeOptions(optionsPath(p), lines, set, nil); err != nil {
		logf("fps boost: %v", err)
	}
}

// restoreFPSBoost puts the player's own settings back.
func restoreFPSBoost(p *Profile) error {
	var backup map[string]*string
	if readJSON(fpsBackupPath(p), &backup) != nil || backup == nil {
		return nil
	}
	lines, _ := readOptions(optionsPath(p))
	set, remove := map[string]string{}, map[string]bool{}
	for k, v := range backup {
		if v == nil {
			remove[k] = true
		} else {
			set[k] = *v
		}
	}
	if lines != nil {
		if err := writeOptions(optionsPath(p), lines, set, remove); err != nil {
			return err
		}
	}
	return os.Remove(fpsBackupPath(p))
}

func setFPSBoost(p *Profile, level string, perfMods bool) error {
	if level != "" {
		if _, ok := fpsPresets[level]; !ok {
			return fmt.Errorf("unknown booster level")
		}
	}
	if isBedrock(p) {
		return fmt.Errorf("the FPS booster is for Java profiles")
	}
	if profileRunning(p.ID) {
		return fmt.Errorf("close this profile's game first")
	}
	stateMu.Lock()
	p.FPSBoost, p.PerfMods = level, perfMods && p.Loader == "fabric"
	stateMu.Unlock()
	saveProfiles()
	if level == "" {
		if err := restoreFPSBoost(p); err != nil {
			return err
		}
	}
	if p.PerfMods {
		go func() {
			done := map[string]bool{}
			for _, m := range perfModIDs {
				if err := installProject(p, m.ID, "", 0, done); err != nil {
					logf("perf mod %s: %v", m.Name, err)
				}
			}
		}()
	}
	return nil
}

func profileRunning(id string) bool {
	gamesMu.Lock()
	defer gamesMu.Unlock()
	for k, g := range games {
		base, _, _ := strings.Cut(k, "~")
		if base == id && g.Running {
			return true
		}
	}
	return false
}
