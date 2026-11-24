package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestFPSBoostRoundTrip(t *testing.T) {
	dataDir = t.TempDir()
	p := &Profile{ID: "t1", Loader: "fabric"}
	os.MkdirAll(profileDir(p), 0o755)
	orig := "version:4440\nrenderDistance:16\nfov:0.5\nmaxFps:120\n"
	os.WriteFile(optionsPath(p), []byte(orig), 0o644)
	p.FPSBoost = "max"
	applyFPSBoost(p)
	b, _ := os.ReadFile(optionsPath(p))
	if !strings.Contains(string(b), "renderDistance:6") || !strings.Contains(string(b), "fov:0.5") || !strings.Contains(string(b), "ao:false") {
		t.Fatalf("not applied: %s", b)
	}
	p.FPSBoost = "light"
	applyFPSBoost(p) // second apply must keep the very first backup
	if err := restoreFPSBoost(p); err != nil {
		t.Fatal(err)
	}
	b, _ = os.ReadFile(optionsPath(p))
	got := string(b)
	for _, want := range []string{"renderDistance:16", "maxFps:120", "fov:0.5", "version:4440"} {
		if !strings.Contains(got, want) {
			t.Fatalf("missing %s after restore: %s", want, got)
		}
	}
	if strings.Contains(got, "ao:") || strings.Contains(got, "particles:") {
		t.Fatalf("booster keys left behind: %s", got)
	}
	if _, err := os.Stat(filepath.Join(profileDir(p), "topclient-fps-backup.json")); err == nil {
		t.Fatal("backup not removed")
	}
}

func TestMemoryArgs(t *testing.T) {
	settings = map[string]any{}
	a := strings.Join(memoryArgs(4096, false, 21), " ")
	if !strings.Contains(a, "-Xms4096M") || strings.Contains(a, "AlwaysPreTouch") {
		t.Fatal(a)
	}
	if a := strings.Join(memoryArgs(4096, true, 21), " "); strings.Contains(a, "AlwaysPreTouch") || !strings.Contains(a, "-Xms2048M") {
		t.Fatal("custom args must keep the old memory start", a)
	}
	settings["gameSmooth"] = false
	if a := strings.Join(memoryArgs(4096, false, 21), " "); strings.Contains(a, "AlwaysPreTouch") {
		t.Fatal(a)
	}
	settings = map[string]any{"gcMode": "zgc"}
	if !strings.Contains(gcArgs(21), "UseZGC") || strings.Contains(gcArgs(17), "UseZGC") {
		t.Fatal("zgc only on java 21+")
	}
}
