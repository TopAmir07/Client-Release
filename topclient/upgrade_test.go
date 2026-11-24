package main

import (
	"crypto/sha1"
	"crypto/sha512"
	"encoding/hex"
	"encoding/json"
	"io/fs"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
	"time"
)

func TestUpgradeName(t *testing.T) {
	cases := [][3]string{
		{"My Pack", "1.21.4", "My Pack (1.21.4)"},
		{"My Pack (1.20.1)", "1.21.4", "My Pack (1.21.4)"},
		{"Fun (beta)", "1.21.4", "Fun (beta) (1.21.4)"},
		{strings.Repeat("x", 40), "1.21.4", strings.Repeat("x", 31) + " (1.21.4)"},
	}
	for _, c := range cases {
		if got := upgradeName(c[0], c[1], ""); got != c[2] || len(got) > 40 {
			t.Errorf("upgradeName(%q,%q) = %q, want %q", c[0], c[1], got, c[2])
		}
	}
	if upgradeName("a", "1.21", " Mine ") != "Mine" {
		t.Error("custom name not used")
	}
}

func TestSensitiveNames(t *testing.T) {
	for _, n := range []string{"accounts.dat", "_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE", "Tokens.json", "session.txt", "authme.json"} {
		if !sensitiveName(n) {
			t.Errorf("%s should be skipped", n)
		}
	}
	for _, n := range []string{"sodium-options.json", "options.txt", "iris.properties"} {
		if sensitiveName(n) {
			t.Errorf("%s should be copied", n)
		}
	}
}

func treeSums(t *testing.T, root string) map[string]string {
	out := map[string]string{}
	filepath.WalkDir(root, func(p string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		rel, _ := filepath.Rel(root, p)
		if strings.HasPrefix(rel, ".topclient") { // the launcher's own notes about the mods
			return nil
		}
		b, _ := os.ReadFile(p)
		h := sha1.Sum(b)
		out[rel] = hex.EncodeToString(h[:])
		return nil
	})
	return out
}

func TestUpgradeProfileMakesNewProfileAndLeavesOldAlone(t *testing.T) {
	dataDir = t.TempDir()
	profiles = nil
	oldMR, oldFab := modrinthAPI, fabricMeta
	defer func() { modrinthAPI, fabricMeta = oldMR, oldFab }()

	jarA, jarB, jarC, packR := []byte("mod A for 1.20"), []byte("mod B for 1.20"), []byte("my own mod"), []byte("pack R")
	newA, newD := []byte("mod A for 1.21"), []byte("library D for 1.21")
	s1 := func(b []byte) string { h := sha1.Sum(b); return hex.EncodeToString(h[:]) }
	s5 := func(b []byte) string { h := sha512.Sum512(b); return hex.EncodeToString(h[:]) }

	var srv *httptest.Server
	ver := func(id, proj, file string, data []byte, deps ...string) map[string]any {
		d := []map[string]string{}
		for _, x := range deps {
			d = append(d, map[string]string{"project_id": x, "dependency_type": "required"})
		}
		return map[string]any{"id": id, "project_id": proj, "version_number": id, "game_versions": []string{"1.21.4"}, "loaders": []string{"fabric"},
			"files": []map[string]any{{"url": srv.URL + "/files/" + file, "filename": file, "primary": true, "hashes": map[string]string{"sha512": s5(data)}}}, "dependencies": d}
	}
	srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p := r.URL.Path
		switch {
		case p == "/mr/version_files":
			json.NewEncoder(w).Encode(map[string]any{
				s1(jarA):  map[string]string{"id": "a1", "project_id": "AAA"},
				s1(jarB):  map[string]string{"id": "b1", "project_id": "BBB"},
				s1(packR): map[string]string{"id": "r1", "project_id": "RRR"},
			})
		case p == "/mr/projects":
			json.NewEncoder(w).Encode([]map[string]string{{"id": "AAA", "title": "Mod A", "project_type": "mod"}, {"id": "BBB", "title": "Mod B", "project_type": "mod"}, {"id": "RRR", "title": "Pack R", "project_type": "resourcepack"}})
		case strings.HasSuffix(p, "/version") && strings.HasPrefix(p, "/mr/project/"):
			if !strings.Contains(r.URL.RawQuery, "1.21.4") {
				t.Errorf("version query without target mc: %s", r.URL.RawQuery)
			}
			switch strings.Split(p, "/")[3] {
			case "AAA":
				json.NewEncoder(w).Encode([]any{ver("a2", "AAA", "a-1.21.jar", newA, "DDD")})
			case "DDD":
				json.NewEncoder(w).Encode([]any{ver("d2", "DDD", "d-1.21.jar", newD)})
			default:
				w.Write([]byte("[]"))
			}
		case strings.HasPrefix(p, "/mr/project/"):
			id := strings.Split(p, "/")[3]
			typ := "mod"
			if id == "RRR" {
				typ = "resourcepack"
			}
			json.NewEncoder(w).Encode(map[string]string{"id": id, "title": "Mod " + id, "project_type": typ})
		case p == "/mr/version/a2":
			json.NewEncoder(w).Encode(ver("a2", "AAA", "a-1.21.jar", newA, "DDD"))
		case p == "/files/a-1.21.jar":
			w.Write(newA)
		case p == "/files/d-1.21.jar":
			w.Write(newD)
		case strings.HasPrefix(p, "/fab/versions/loader/1.21.4"):
			w.Write([]byte(`[{"loader":{"version":"0.16.9","stable":true}}]`))
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()
	modrinthAPI, fabricMeta = srv.URL+"/mr", srv.URL+"/fab"
	oldNet := netClient
	netClient = srv.Client() // the real one only talks to trusted https hosts
	defer func() { netClient = oldNet }()
	manifestMu.Lock()
	manifestCache = &Manifest{Versions: []ManifestEntry{{ID: "1.21.4", Type: "release"}, {ID: "1.20.1", Type: "release"}}}
	manifestTime = time.Now()
	manifestMu.Unlock()

	old := newProfile("Survival", "1.20.1", "fabric")
	od := profileDir(old)
	write := func(rel string, b []byte) {
		os.MkdirAll(filepath.Dir(filepath.Join(od, rel)), 0o755)
		os.WriteFile(filepath.Join(od, rel), b, 0o644)
	}
	write("mods/a-1.20.jar", jarA)
	write("mods/b-1.20.jar.disabled", jarB)
	write("mods/mine.jar", jarC)
	write("resourcepacks/r.zip", packR)
	write("options.txt", []byte("fov:0.5\n"))
	write("config/sodium-options.json", []byte("{}"))
	write("config/accounts.json", []byte("SECRET"))
	write("config/_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/x.dat", []byte("SECRET"))
	write("saves/World/level.dat", []byte("world"))
	before := treeSums(t, od)

	if !task.begin("upgrade") {
		t.Fatal("task busy")
	}
	res, err := upgradeProfile(UpgradeOptions{ID: old.ID, MC: "1.21.4", Settings: true})
	task.end(err)
	if err != nil {
		t.Fatal(err)
	}

	// old profile untouched
	if after := treeSums(t, od); len(after) != len(before) {
		t.Fatalf("old profile changed: %v -> %v", before, after)
	} else {
		for k, v := range before {
			if after[k] != v {
				t.Fatalf("old file %s changed", k)
			}
		}
	}
	if old.MC != "1.20.1" || len(profiles) != 2 {
		t.Fatalf("old profile edited or profile not added: %+v", old)
	}

	np := res.Profile
	nd := profileDir(np)
	if np.MC != "1.21.4" || np.Loader != "fabric" || np.Name != "Survival (1.21.4)" || np.ID == old.ID {
		t.Fatalf("new profile: %+v", np)
	}
	if b, _ := os.ReadFile(filepath.Join(nd, "mods", "a-1.21.jar")); string(b) != string(newA) {
		t.Fatal("mod A not updated")
	}
	if b, _ := os.ReadFile(filepath.Join(nd, "mods", "d-1.21.jar")); string(b) != string(newD) {
		t.Fatal("dependency D not added")
	}
	if _, err := os.Stat(filepath.Join(nd, "resourcepacks", "r.zip")); err != nil {
		t.Fatal("pack with no new build should be copied")
	}
	if b, _ := os.ReadFile(filepath.Join(nd, "options.txt")); string(b) != "fov:0.5\n" {
		t.Fatal("options not copied")
	}
	if _, err := os.Stat(filepath.Join(nd, "config", "sodium-options.json")); err != nil {
		t.Fatal("config not copied")
	}
	for _, bad := range []string{"config/accounts.json", "config/_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE", "saves", "mods/mine.jar", "mods/a-1.20.jar"} {
		if _, err := os.Stat(filepath.Join(nd, bad)); err == nil {
			t.Fatalf("%s should not be in the new profile", bad)
		}
	}
	var miss []string
	for _, m := range res.Missing {
		miss = append(miss, m.Name)
	}
	sort.Strings(miss)
	if strings.Join(miss, ",") != "Mod B,mine" {
		t.Fatalf("missing = %v", miss)
	}
	if len(res.Updated) != 1 || res.Updated[0].Name != "Mod A" || len(res.Copied) != 1 {
		t.Fatalf("updated %+v copied %+v", res.Updated, res.Copied)
	}

	// off mods stay off; already on the version -> refused
	if _, err := upgradeProfile(UpgradeOptions{ID: old.ID, MC: "1.20.1"}); err == nil {
		t.Fatal("same version accepted")
	}
	if _, err := upgradeProfile(UpgradeOptions{ID: old.ID, MC: "9.9.9"}); err == nil {
		t.Fatal("unknown version accepted")
	}
}
