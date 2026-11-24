package main

import (
	"archive/zip"
	"os"
	"path/filepath"
	"testing"
)

func TestJarInfoAndDuplicates(t *testing.T) {
	dataDir = t.TempDir()
	p := &Profile{ID: "imp", Loader: "fabric", MC: "1.21.11"}
	mods := filepath.Join(profileDir(p), "mods")
	os.MkdirAll(mods, 0o755)
	// a third-party mod with an icon map
	f, _ := os.Create(filepath.Join(mods, "Ambience-2.1.1.jar"))
	zw := zip.NewWriter(f)
	w, _ := zw.Create("fabric.mod.json")
	w.Write([]byte(`{"id":"ambience","name":"Ambience","icon":{"16":"a/s.png","128":"a/big.png"}}`))
	w, _ = zw.Create("a/big.png")
	w.Write([]byte("\x89PNGbig"))
	zw.Close()
	f.Close()

	list, _, err := listContent(p, "mods")
	if err != nil {
		t.Fatal(err)
	}
	var sawAmb bool
	for _, c := range list {
		t.Logf("%+v", c)
		switch c.File {
		case "Ambience-2.1.1.jar":
			sawAmb = c.Title == "Ambience" && c.Icon != ""
		}
	}
	if !sawAmb {
		t.Fatalf("amb=%v", sawAmb)
	}
	ic, err := jarIcon(filepath.Join(mods, "Ambience-2.1.1.jar"))
	if err != nil || string(ic) != "\x89PNGbig" {
		t.Fatalf("icon %q %v", ic, err)
	}
}

