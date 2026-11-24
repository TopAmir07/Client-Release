package main

// Extra copies of a running profile ("Launch another" / the + button) get their
// own game folder, so the two games never fight over the same log, options or
// lock files. Mods and packs are hard-linked from the main profile (no extra disk
// space), and settings files are copied. Worlds are not shared, and the in-game
// account switcher's folder is never touched.

import (
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

func copyDirFor(p *Profile, key string) string {
	n := strings.TrimPrefix(key, p.ID+"~")
	return filepath.Join(dataDir, "instances", cleanName(p.ID)+"-"+cleanName(n))
}

// linkOrCopy makes dst the same file as src (hard link when possible).
func linkOrCopy(src, dst string) error {
	if !insideData(dst) {
		return os.ErrPermission
	}
	os.Remove(dst)
	if err := os.Link(src, dst); err == nil {
		return nil
	}
	return copyFile(src, dst)
}

// mirrorDir makes dst/ hold exactly the files of src/ (top level only, for mods/packs).
func mirrorDir(src, dst string) {
	os.MkdirAll(dst, 0o755)
	want := map[string]bool{}
	if ents, err := os.ReadDir(src); err == nil {
		for _, e := range ents {
			if e.Type().IsRegular() {
				want[e.Name()] = true
				s, d := filepath.Join(src, e.Name()), filepath.Join(dst, e.Name())
				if a, err1 := os.Stat(s); err1 == nil {
					if b, err2 := os.Stat(d); err2 == nil && os.SameFile(a, b) {
						continue
					}
				}
				linkOrCopy(s, d)
			}
		}
	}
	if ents, err := os.ReadDir(dst); err == nil {
		for _, e := range ents {
			if e.Type().IsRegular() && !want[e.Name()] && insideData(dst) {
				os.Remove(filepath.Join(dst, e.Name()))
			}
		}
	}
}

// copyTree copies every file under src into dst (used for the small config folder).
func copyTree(src, dst string) {
	filepath.WalkDir(src, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		rel, _ := filepath.Rel(src, path)
		target := filepath.Join(dst, rel)
		if d.IsDir() {
			os.MkdirAll(target, 0o755)
			return nil
		}
		if d.Type().IsRegular() {
			if st, e := d.Info(); e == nil && st.Size() > 64<<20 {
				return nil // skip anything huge
			}
			copyFile(path, target)
		}
		return nil
	})
}

// prepareCopyDir sets up (or refreshes) the folder for an extra copy of p.
func prepareCopyDir(p *Profile, key string) (string, error) {
	src, dir := profileDir(p), copyDirFor(p, key)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	for _, sub := range []string{"mods", "resourcepacks", "shaderpacks"} {
		if _, err := os.Stat(filepath.Join(src, sub)); err == nil {
			mirrorDir(filepath.Join(src, sub), filepath.Join(dir, sub))
		}
	}
	if _, err := os.Stat(filepath.Join(src, "config")); err == nil {
		copyTree(filepath.Join(src, "config"), filepath.Join(dir, "config"))
	}
	for _, f := range []string{"options.txt", "optionsshaders.txt", "servers.dat"} {
		if _, err := os.Stat(filepath.Join(src, f)); err == nil {
			copyFile(filepath.Join(src, f), filepath.Join(dir, f))
		}
	}
	return dir, nil
}
