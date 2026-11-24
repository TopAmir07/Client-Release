package main

// Crash Helper: when Minecraft closes because of a crash, read its crash report (or the
// end of its log) and point at the mod that caused it, in plain words.

import (
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
)

type Suspect struct {
	ModID   string `json:"modId"`
	Name    string `json:"name"`
	File    string `json:"file,omitempty"`
	Reason  string `json:"reason"`
	Enabled bool   `json:"enabled"`
}

type CrashInfo struct {
	Profile  string    `json:"profile"`
	Name     string    `json:"name"`
	At       int64     `json:"at"`
	Code     int       `json:"code"`
	Title    string    `json:"title"`
	Hint     string    `json:"hint"`
	Detail   string    `json:"detail,omitempty"`
	Suspects []Suspect `json:"suspects"`
	Report   string    `json:"report,omitempty"` // crash report file name, if there is one
}

var (
	crashMu  sync.Mutex
	crashes  = map[string]*CrashInfo{}
	coreMods = map[string]bool{"minecraft": true, "java": true, "fabricloader": true, "fabric-loader": true, "mixinextras": true, "fabric-api": false}

	reSuspects  = regexp.MustCompile(`(?m)^\s*Suspected Mods?:\s*(.+)$`)
	reSuspectID = regexp.MustCompile(`([^,]+?)\s*\(([a-z0-9_\-.]+)\)`)
	reMixinMod  = regexp.MustCompile(`(?i)(?:Mixin apply for mod|from mod|provided by ')\s*'?([a-z0-9_\-.]+)'?`)
	reMixinCfg  = regexp.MustCompile(`(?i)mixin(?:s)? config ([a-z0-9_\-.]+?)\.mixins?\.json`)
	reEntry     = regexp.MustCompile(`(?i)provided by '([a-z0-9_\-.]+)'`)
	reNeeds     = regexp.MustCompile(`(?m)^\s*- (?:Mod|Installed) '([^']+)' \(([a-z0-9_\-.]+)\)[^\n]*?(requires|is incompatible with|conflicts with|breaks)[^\n]*`)
	reDesc      = regexp.MustCompile(`(?m)^Description:\s*(.+)$`)
	reCause     = regexp.MustCompile(`(?m)^(?:Caused by: )?([a-zA-Z0-9_.$]+(?:Exception|Error))(?::\s*(.{0,200}))?$`)
)

// crashSince finds a crash report written after t (newest first) and returns its name + text.
func crashSince(dir string, t time.Time) (string, string) {
	ents, _ := os.ReadDir(filepath.Join(dir, "crash-reports"))
	sort.Slice(ents, func(i, j int) bool { return ents[i].Name() > ents[j].Name() })
	for _, e := range ents {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".txt") {
			continue
		}
		info, err := e.Info()
		if err != nil || info.ModTime().Before(t.Add(-5*time.Second)) {
			continue
		}
		return e.Name(), readTail(filepath.Join(dir, "crash-reports", e.Name()), 512<<10)
	}
	return "", ""
}

func readTail(path string, max int64) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	if st, err := f.Stat(); err == nil && st.Size() > max {
		f.Seek(st.Size()-max, io.SeekStart)
	}
	b, _ := io.ReadAll(io.LimitReader(f, max))
	return string(b)
}

// analyzeCrash works out what went wrong from the report/log text.
func analyzeCrash(text string, mods map[string]modFile) (title, hint, detail string, sus []Suspect) {
	seen := map[string]bool{}
	add := func(id, name, reason string) {
		id = strings.ToLower(strings.TrimSpace(id))
		if id == "" || coreMods[id] || seen[id] {
			return
		}
		seen[id] = true
		s := Suspect{ModID: id, Name: strings.TrimSpace(name), Reason: reason}
		if m, ok := mods[id]; ok {
			s.File, s.Enabled = m.File, m.Enabled
			if s.Name == "" {
				s.Name = m.Name
			}
		}
		if s.Name == "" {
			s.Name = id
		}
		sus = append(sus, s)
	}
	for _, m := range reNeeds.FindAllStringSubmatch(text, 10) {
		r := "needs another mod or a different version"
		if m[3] != "requires" {
			r = "doesn't work together with another mod"
		}
		add(m[2], m[1], r)
	}
	if len(sus) > 0 {
		title = "Two of your mods don't fit together"
		hint = "Minecraft stopped before starting because of the mods below. Turn one off, or get the version it asks for."
		detail = firstMatch(reNeeds, text)
		return
	}
	for _, m := range reSuspects.FindAllStringSubmatch(text, 3) {
		if strings.EqualFold(strings.TrimSpace(m[1]), "none") {
			continue
		}
		for _, x := range reSuspectID.FindAllStringSubmatch(m[1], 6) {
			add(x[2], x[1], "named by Minecraft's crash report")
		}
	}
	for _, re := range []*regexp.Regexp{reEntry, reMixinMod, reMixinCfg} {
		for _, m := range re.FindAllStringSubmatch(text, 6) {
			add(m[1], "", "its code was running when the game crashed")
		}
	}
	if d := reDesc.FindStringSubmatch(text); d != nil {
		detail = strings.TrimSpace(d[1])
	}
	if c := reCause.FindAllStringSubmatch(text, -1); len(c) > 0 {
		last := c[len(c)-1]
		if detail == "" {
			detail = strings.TrimSpace(last[1] + " " + last[2])
		}
	}
	switch {
	case strings.Contains(text, "OutOfMemoryError"):
		title, hint = "Minecraft ran out of memory", "Give this profile more RAM in its settings (4 GB or more), or remove some heavy mods or shaders."
	case strings.Contains(text, "UnsupportedClassVersionError") || strings.Contains(text, "has been compiled by a more recent version of the Java Runtime"):
		title, hint = "A mod needs a newer Java", "One of your mods was made for a newer Minecraft/Java. Check that every mod is for this profile's Minecraft version."
	case len(sus) > 0:
		title, hint = "A mod crashed the game", "This is most likely the mod that caused it. Turn it off and press Play again."
	default:
		title, hint = "Minecraft crashed", "No single mod was named. If it keeps happening, turn off the mods you added last, then try again."
	}
	if len(detail) > 220 {
		detail = detail[:220] + "…"
	}
	return
}

func firstMatch(re *regexp.Regexp, s string) string {
	m := re.FindString(s)
	if len(m) > 220 {
		m = m[:220] + "…"
	}
	return strings.TrimSpace(m)
}

type modFile struct {
	File, Name string
	Enabled    bool
}

func modsByID(p *Profile) map[string]modFile {
	out := map[string]modFile{}
	list, _, err := listContent(p, "mods")
	if err != nil {
		return out
	}
	for _, c := range list {
		if c.ModID != "" {
			name := c.Title
			if name == "" {
				name = strings.TrimSuffix(strings.TrimSuffix(c.File, ".disabled"), ".jar")
			}
			out[strings.ToLower(c.ModID)] = modFile{File: c.File, Name: name, Enabled: c.Enabled}
		}
	}
	return out
}

// noteGameExit runs when a game closes: was it a crash, and why?
func noteGameExit(p *Profile, dir string, started time.Time, code int, killed bool) {
	report, text := crashSince(dir, started)
	if report == "" {
		if killed || code == 0 {
			return
		}
		text = readTail(filepath.Join(dir, "logs", "latest.log"), 400<<10)
	}
	title, hint, detail, sus := analyzeCrash(text, modsByID(p))
	crashMu.Lock()
	crashes[p.ID] = &CrashInfo{Profile: p.ID, Name: p.Name, At: time.Now().Unix(), Code: code, Title: title, Hint: hint, Detail: detail, Suspects: sus, Report: report}
	crashMu.Unlock()
}

func pendingCrashes() []string {
	crashMu.Lock()
	defer crashMu.Unlock()
	out := []string{}
	for id := range crashes {
		out = append(out, id)
	}
	sort.Strings(out)
	return out
}

func crashRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/crash", func(w http.ResponseWriter, r *http.Request) {
		crashMu.Lock()
		c := crashes[r.URL.Query().Get("id")]
		crashMu.Unlock()
		if c == nil {
			jsonOut(w, map[string]any{"none": true})
			return
		}
		if p := findProfile(c.Profile); p != nil { // refresh on/off state
			mods := modsByID(p)
			for i := range c.Suspects {
				if m, ok := mods[c.Suspects[i].ModID]; ok {
					c.Suspects[i].File, c.Suspects[i].Enabled = m.File, m.Enabled
				}
			}
		}
		jsonOut(w, c)
	})
	mux.HandleFunc("POST /api/crash/dismiss", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID string }
		body(r, &in)
		crashMu.Lock()
		delete(crashes, in.ID)
		crashMu.Unlock()
		jsonOut(w, map[string]any{"ok": true})
	})
}
