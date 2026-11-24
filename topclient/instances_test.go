package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestPrepareCopyDir(t *testing.T) {
	old := dataDir
	dataDir = t.TempDir()
	defer func() { dataDir = old }()
	p := &Profile{ID: "abc", Loader: "fabric"}
	src := profileDir(p)
	for _, f := range []string{"mods/a.jar", "mods/b.jar", "config/x/y.json", "options.txt", "saves/w/level.dat", "private_accounts/acc.json"} {
		os.MkdirAll(filepath.Dir(filepath.Join(src, f)), 0o755)
		os.WriteFile(filepath.Join(src, f), []byte(f), 0o644)
	}
	dir, err := prepareCopyDir(p, "abc~2")
	if err != nil {
		t.Fatal(err)
	}
	for _, f := range []string{"mods/a.jar", "mods/b.jar", "config/x/y.json", "options.txt"} {
		if _, err := os.Stat(filepath.Join(dir, f)); err != nil {
			t.Fatal(f, err)
		}
	}
	for _, f := range []string{"saves", "private_accounts"} {
		if _, err := os.Stat(filepath.Join(dir, f)); err == nil {
			t.Fatal(f, "should not be copied")
		}
	}
	os.Remove(filepath.Join(src, "mods/b.jar"))
	prepareCopyDir(p, "abc~2")
	if _, err := os.Stat(filepath.Join(dir, "mods/b.jar")); err == nil {
		t.Fatal("removed mod still in copy")
	}
}
