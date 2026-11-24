package main

import (
	"bytes"
	"crypto/sha1"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

const fabricAPIProject = "P7dR8mSH" // Fabric API on Modrinth


func verParts(v string) []int {
	var out []int
	for _, p := range strings.Split(v, ".") {
		n, err := strconv.Atoi(strings.TrimFunc(p, func(r rune) bool { return r < '0' || r > '9' }))
		if err != nil {
			return out
		}
		out = append(out, n)
	}
	return out
}

func verCmp(a, b []int) int {
	for i := 0; i < len(a) || i < len(b); i++ {
		x, y := 0, 0
		if i < len(a) {
			x = a[i]
		}
		if i < len(b) {
			y = b[i]
		}
		if x != y {
			if x < y {
				return -1
			}
			return 1
		}
	}
	return 0
}

// mcMatches implements the common fabric.mod.json version predicates.
func mcMatches(rng, mc string) bool {
	rng = strings.TrimSpace(rng)
	if rng == "" || rng == "*" {
		return true
	}
	v := verParts(mc)
	if len(v) == 0 {
		return false
	}
	for _, part := range strings.Fields(rng) {
		switch {
		case strings.HasPrefix(part, "~"):
			want := verParts(part[1:])
			if len(want) < 2 || verCmp(v, want) < 0 || len(v) < 2 || v[0] != want[0] || v[1] != want[1] {
				return false
			}
		case strings.HasPrefix(part, "^"):
			want := verParts(part[1:])
			if len(want) < 1 || verCmp(v, want) < 0 || v[0] != want[0] {
				return false
			}
		case strings.HasPrefix(part, ">="):
			if verCmp(v, verParts(part[2:])) < 0 {
				return false
			}
		case strings.HasPrefix(part, "<="):
			if verCmp(v, verParts(part[2:])) > 0 {
				return false
			}
		case strings.HasPrefix(part, ">"):
			if verCmp(v, verParts(part[1:])) <= 0 {
				return false
			}
		case strings.HasPrefix(part, "<"):
			if verCmp(v, verParts(part[1:])) >= 0 {
				return false
			}
		default:
			if strings.TrimPrefix(part, "=") != mc {
				return false
			}
		}
	}
	return true
}


// disableSameMod switches off every other enabled jar in the same folder that has the
// same mod id as the jar that was just switched on. Returns their display names.
func disableSameMod(p *Profile, on string) []string {
	dir, me := filepath.Dir(on), filepath.Base(on)
	ents, _ := os.ReadDir(dir)
	names := []string{me}
	for _, e := range ents {
		if !e.IsDir() && strings.HasSuffix(strings.ToLower(e.Name()), ".jar") && e.Name() != me {
			names = append(names, e.Name())
		}
	}
	cache := scanJars(p, dir, names)
	id := cache[me].ModID
	out := []string{}
	if id == "" {
		return out
	}
	for _, n := range names[1:] {
		if cache[n].ModID != id {
			continue
		}
		if err := os.Rename(filepath.Join(dir, n), filepath.Join(dir, n+".disabled")); err == nil {
			logf("switched off %s (same mod as %s)", n, me)
			out = append(out, n)
		}
	}
	return out
}


func hasFabricAPI(p *Profile) bool {
	for _, m := range readMeta(p) {
		if m.Project == fabricAPIProject {
			return true
		}
	}
	ents, _ := os.ReadDir(filepath.Join(profileDir(p), "mods"))
	for _, e := range ents {
		n := strings.ToLower(e.Name())
		if strings.HasPrefix(n, "fabric-api") && strings.HasSuffix(n, ".jar") {
			return true
		}
	}
	return false
}


// ---------------------------------------------------------------------------
// Modrinth icons for mods that weren't installed through the launcher
// ---------------------------------------------------------------------------

type hashEntry struct {
	Size    int64  `json:"size"`
	Mod     int64  `json:"mod"`
	SHA1    string `json:"sha1"`
	Checked int64  `json:"checkedAt"`
	ModID   string `json:"modId"`
	Name    string `json:"name"`
	HasIcon bool   `json:"hasIcon"`
}

var hashMu sync.Mutex

func hashCachePath(p *Profile) string { return filepath.Join(profileDir(p), ".topclient", "hashes.json") }

// scanJars refreshes cached sha1 + fabric.mod.json info for files in dir.
func scanJars(p *Profile, dir string, names []string) map[string]hashEntry {
	hashMu.Lock()
	defer hashMu.Unlock()
	cache := map[string]hashEntry{}
	readJSON(hashCachePath(p), &cache)
	changed := false
	for _, n := range names {
		base := strings.TrimSuffix(n, ".disabled")
		full := filepath.Join(dir, n)
		st, err := os.Stat(full)
		if err != nil {
			continue
		}
		e, ok := cache[base]
		if !ok || e.Size != st.Size() || e.Mod != st.ModTime().Unix() || e.SHA1 == "" {
			h, err := hashFile(full, sha1.New())
			if err != nil {
				continue
			}
			e = hashEntry{Size: st.Size(), Mod: st.ModTime().Unix(), SHA1: h}
			if strings.HasSuffix(strings.ToLower(base), ".jar") || strings.HasSuffix(strings.ToLower(base), ".zip") {
				ji := readJarInfo(full)
				e.ModID, e.Name = ji.ID, ji.Name
				if _, err := jarIcon(full); err == nil {
					e.HasIcon = true
				}
			}
			cache[base], changed = e, true
		}
	}
	if changed {
		writeJSON(hashCachePath(p), cache)
	}
	return cache
}

func identifyContent(p *Profile, dir string, names []string) {
	cache := scanJars(p, dir, names)
	meta := readMeta(p)
	byHash := map[string]string{}
	now := time.Now().Unix()
	for _, n := range names {
		base := strings.TrimSuffix(n, ".disabled")
		if m, ok := meta[base]; ok && m.Icon != "" {
			continue
		}
		e, ok := cache[base]
		if !ok || (e.Checked > 0 && now-e.Checked < 3*86400) {
			continue
		}
		byHash[e.SHA1] = base
	}
	if len(byHash) == 0 {
		return
	}
	var hashes []string
	for h := range byHash {
		hashes = append(hashes, h)
	}
	body, _ := json.Marshal(map[string]any{"hashes": hashes, "algorithm": "sha1"})
	req, _ := http.NewRequest("POST", modrinthAPI+"/version_files", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	resp, err := netClient.Do(req)
	if err != nil {
		logf("modrinth lookup: %v", err)
		return
	}
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
	resp.Body.Close()
	if resp.StatusCode != 200 {
		logf("modrinth lookup: HTTP %d", resp.StatusCode)
		return
	}
	var found map[string]struct {
		ID        string `json:"id"`
		ProjectID string `json:"project_id"`
	}
	if err := json.Unmarshal(raw, &found); err != nil {
		logf("modrinth lookup decode: %v", err)
		return
	}
	idset := map[string]bool{}
	for _, v := range found {
		idset[v.ProjectID] = true
	}
	projects := map[string]mrProject{}
	ids := []string{}
	for id := range idset {
		ids = append(ids, id)
	}
	for i := 0; i < len(ids); i += 100 {
		end := min(i+100, len(ids))
		q, _ := json.Marshal(ids[i:end])
		var list []mrProject
		if err := getJSON(modrinthAPI+"/projects?ids="+url.QueryEscape(string(q)), &list); err != nil {
			logf("modrinth projects: %v", err)
		}
		for _, pr := range list {
			projects[pr.ID] = pr
		}
	}
	for h, base := range byHash {
		if v, ok := found[h]; ok {
			pr := projects[v.ProjectID]
			setMeta(p, base, installedMeta{Project: v.ProjectID, Version: v.ID, Title: pr.Title, Icon: pr.IconURL, Banner: pr.banner()})
		}
	}
	hashMu.Lock()
	c2 := map[string]hashEntry{}
	readJSON(hashCachePath(p), &c2)
	for _, base := range byHash {
		if e, ok := c2[base]; ok {
			e.Checked = now
			c2[base] = e
		}
	}
	writeJSON(hashCachePath(p), c2)
	hashMu.Unlock()
}
