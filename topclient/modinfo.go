package main

import (
	"archive/zip"
	"encoding/json"
	"io"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// jarInfo reads a mod's own name / id / icon from its fabric.mod.json.
type jarInfo struct {
	ID, Name, Icon string
}

func readJarInfo(path string) jarInfo {
	var out jarInfo
	zr, err := zip.OpenReader(path)
	if err != nil {
		return out
	}
	defer zr.Close()
	for _, f := range zr.File {
		if f.Name != "fabric.mod.json" && f.Name != "quilt.mod.json" {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return out
		}
		b, _ := io.ReadAll(io.LimitReader(rc, 1<<20))
		rc.Close()
		var fm struct {
			ID   string          `json:"id"`
			Name string          `json:"name"`
			Icon json.RawMessage `json:"icon"`
		}
		// some mods put raw newlines in strings; be lenient
		if json.Unmarshal(b, &fm) != nil {
			json.Unmarshal([]byte(strings.ReplaceAll(strings.ReplaceAll(string(b), "\r", ""), "\n", " ")), &fm)
		}
		out.ID, out.Name = fm.ID, fm.Name
		var s string
		if json.Unmarshal(fm.Icon, &s) == nil {
			out.Icon = s
		} else {
			var m map[string]string
			if json.Unmarshal(fm.Icon, &m) == nil {
				best := -1
				keys := make([]string, 0, len(m))
				for k := range m {
					keys = append(keys, k)
				}
				sort.Strings(keys)
				for _, k := range keys {
					if n, _ := strconv.Atoi(k); n > best {
						best, out.Icon = n, m[k]
					}
				}
			}
		}
		break
	}
	return out
}

// jarIcon returns the PNG icon bytes stored inside a mod jar.
func jarIcon(path string) ([]byte, error) {
	info := readJarInfo(path)
	zr, err := zip.OpenReader(path)
	if err != nil {
		return nil, err
	}
	defer zr.Close()
	want := strings.TrimPrefix(info.Icon, "/")
	for _, f := range zr.File {
		if (want != "" && f.Name == want) || (want == "" && f.Name == "pack.png") {
			rc, err := f.Open()
			if err != nil {
				return nil, err
			}
			defer rc.Close()
			return io.ReadAll(io.LimitReader(rc, 4<<20))
		}
	}
	return nil, os.ErrNotExist
}

// ---------------------------------------------------------------------------
// Background identification
// ---------------------------------------------------------------------------

var (
	identMu     sync.Mutex
	identActive = map[string]time.Time{}
)

// identifyAsync looks mods up on Modrinth in the background (once at a time per profile).
func identifyAsync(p *Profile, dir string, names []string) bool {
	identMu.Lock()
	if t, ok := identActive[dir]; ok && time.Since(t) < 2*time.Minute {
		identMu.Unlock()
		return true
	}
	identActive[dir] = time.Now()
	identMu.Unlock()
	go func() {
		defer func() { identMu.Lock(); delete(identActive, dir); identMu.Unlock() }()
		identifyContent(p, dir, names)
	}()
	return true
}
