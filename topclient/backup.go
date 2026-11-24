package main

// World backups: zip copies of your worlds, kept in the launcher's own folder.
//   Java:    a profile's saves/<world>  ->  backups/<profile>/<world>__<time>.zip
//            restoring puts it back as a new folder (never over an existing world)
//   Bedrock: your Bedrock worlds are only read. Backups are .mcworld files; restoring one
//            hands it to Bedrock, which imports it as a copy (Bedrock's own import).

import (
	"archive/zip"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

type WorldInfo struct {
	Folder  string `json:"folder"`
	Name    string `json:"name"`
	Size    int64  `json:"size"`
	Played  int64  `json:"played"`
	HasIcon bool   `json:"hasIcon"`
}

type BackupInfo struct {
	File  string `json:"file"`
	World string `json:"world"`
	At    int64  `json:"at"`
	Size  int64  `json:"size"`
}

var reStamp = regexp.MustCompile(`^(.+)__(\d{8}-\d{6})\.(zip|mcworld)$`)

const keepBackups = 10

func backupDir(owner string) string { return filepath.Join(dataDir, "backups", cleanName(owner)) }

func dirSize(dir string) (size int64, newest int64) {
	filepath.WalkDir(dir, func(_ string, d os.DirEntry, err error) error {
		if err == nil && !d.IsDir() {
			if info, err := d.Info(); err == nil {
				size += info.Size()
				if t := info.ModTime().Unix(); t > newest {
					newest = t
				}
			}
		}
		return nil
	})
	return
}

// zipDir writes dir's contents into a zip (paths relative to dir, under prefix).
func zipDir(dir, dest, prefix string) error {
	os.MkdirAll(filepath.Dir(dest), 0o755)
	tmp := dest + ".tmp"
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	zw := zip.NewWriter(f)
	err = filepath.WalkDir(dir, func(path string, d os.DirEntry, err error) error {
		if err != nil || d.IsDir() || d.Name() == "session.lock" {
			return nil
		}
		rel, _ := filepath.Rel(dir, path)
		w, err := zw.Create(filepath.ToSlash(filepath.Join(prefix, rel)))
		if err != nil {
			return err
		}
		src, err := os.Open(path)
		if err != nil {
			return nil // a file the game has locked: skip it
		}
		defer src.Close()
		_, err = io.Copy(w, src)
		return err
	})
	if cerr := zw.Close(); err == nil {
		err = cerr
	}
	f.Close()
	if err != nil {
		os.Remove(tmp)
		return err
	}
	return os.Rename(tmp, dest)
}

// unzipTo extracts a backup into dest (which must not exist yet). Refuses unsafe paths.
func unzipTo(src, dest string) error {
	zr, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer zr.Close()
	for _, f := range zr.File {
		name := filepath.FromSlash(f.Name)
		if strings.Contains(name, "..") || filepath.IsAbs(name) {
			return errors.New("this backup has unsafe file names")
		}
		out := filepath.Join(dest, name)
		if f.FileInfo().IsDir() {
			os.MkdirAll(out, 0o755)
			continue
		}
		os.MkdirAll(filepath.Dir(out), 0o755)
		r, err := f.Open()
		if err != nil {
			return err
		}
		w, err := os.Create(out)
		if err != nil {
			r.Close()
			return err
		}
		_, err = io.Copy(w, io.LimitReader(r, 4<<30))
		r.Close()
		w.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

func listBackups(owner string) []BackupInfo {
	ents, _ := os.ReadDir(backupDir(owner))
	out := []BackupInfo{}
	for _, e := range ents {
		m := reStamp.FindStringSubmatch(e.Name())
		if m == nil {
			continue
		}
		t, _ := time.ParseInLocation("20060102-150405", m[2], time.Local)
		info, _ := e.Info()
		var size int64
		if info != nil {
			size = info.Size()
		}
		out = append(out, BackupInfo{File: e.Name(), World: m[1], At: t.Unix(), Size: size})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].At > out[j].At })
	return out
}

func pruneBackups(owner, world string) {
	n := 0
	for _, b := range listBackups(owner) {
		if b.World == world {
			n++
			if n > keepBackups {
				os.Remove(filepath.Join(backupDir(owner), b.File))
			}
		}
	}
}

// ---- Java ----

func javaWorlds(p *Profile) []WorldInfo {
	dir := filepath.Join(profileDir(p), "saves")
	ents, _ := os.ReadDir(dir)
	out := []WorldInfo{}
	for _, e := range ents {
		if !e.IsDir() {
			continue
		}
		wd := filepath.Join(dir, e.Name())
		if _, err := os.Stat(filepath.Join(wd, "level.dat")); err != nil {
			continue
		}
		size, played := dirSize(wd)
		_, ierr := os.Stat(filepath.Join(wd, "icon.png"))
		out = append(out, WorldInfo{Folder: e.Name(), Name: e.Name(), Size: size, Played: played, HasIcon: ierr == nil})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Played > out[j].Played })
	return out
}

func backupJavaWorld(p *Profile, folder string) (BackupInfo, error) {
	folder = cleanName(folder)
	src := filepath.Join(profileDir(p), "saves", folder)
	if _, err := os.Stat(filepath.Join(src, "level.dat")); err != nil {
		return BackupInfo{}, errors.New("world not found")
	}
	now := time.Now()
	name := folder + "__" + now.Format("20060102-150405") + ".zip"
	dest := filepath.Join(backupDir(p.ID), name)
	if err := zipDir(src, dest, ""); err != nil {
		return BackupInfo{}, err
	}
	pruneBackups(p.ID, folder)
	recordStat("backups", 1)
	info, _ := os.Stat(dest)
	return BackupInfo{File: name, World: folder, At: now.Unix(), Size: info.Size()}, nil
}

func backupAllJava(p *Profile) {
	for _, w := range javaWorlds(p) {
		backupJavaWorld(p, w.Folder)
	}
}

func restoreJavaBackup(p *Profile, file string) (string, error) {
	m := reStamp.FindStringSubmatch(file)
	if m == nil || m[3] != "zip" || cleanName(file) != file {
		return "", errors.New("backup not found")
	}
	src := filepath.Join(backupDir(p.ID), file)
	if _, err := os.Stat(src); err != nil {
		return "", errors.New("backup not found")
	}
	saves := filepath.Join(profileDir(p), "saves")
	t, _ := time.ParseInLocation("20060102-150405", m[2], time.Local)
	name := m[1]
	if _, err := os.Stat(filepath.Join(saves, name)); err == nil {
		name = fmt.Sprintf("%s (backup %s)", m[1], t.Format("2006-01-02 15.04"))
	}
	for i := 2; ; i++ {
		if _, err := os.Stat(filepath.Join(saves, name)); err != nil {
			break
		}
		name = fmt.Sprintf("%s (backup %s) %d", m[1], t.Format("2006-01-02 15.04"), i)
	}
	if err := unzipTo(src, filepath.Join(saves, name)); err != nil {
		os.RemoveAll(filepath.Join(saves, name))
		return "", err
	}
	return name, nil
}

// ---- Bedrock ----

// bedrockWorldDirs: where Minecraft for Windows keeps worlds (new GDK version and the older Store version).
func bedrockWorldDirs() []string {
	var out []string
	if a := os.Getenv("APPDATA"); a != "" {
		users, _ := filepath.Glob(filepath.Join(a, "Minecraft Bedrock", "Users", "*", "games", "com.mojang", "minecraftWorlds"))
		out = append(out, users...)
	}
	if l := os.Getenv("LOCALAPPDATA"); l != "" {
		uwp := filepath.Join(l, "Packages", "Microsoft.MinecraftUWP_8wekyb3d8bbwe", "LocalState", "games", "com.mojang", "minecraftWorlds")
		if st, err := os.Stat(uwp); err == nil && st.IsDir() {
			out = append(out, uwp)
		}
	}
	return out
}

type bedrockWorld struct {
	WorldInfo
	dir string
}

func bedrockWorlds() []bedrockWorld {
	out := []bedrockWorld{}
	for _, root := range bedrockWorldDirs() {
		ents, _ := os.ReadDir(root)
		for _, e := range ents {
			wd := filepath.Join(root, e.Name())
			if !e.IsDir() {
				continue
			}
			if _, err := os.Stat(filepath.Join(wd, "level.dat")); err != nil {
				continue
			}
			name := e.Name()
			if b, err := os.ReadFile(filepath.Join(wd, "levelname.txt")); err == nil && len(strings.TrimSpace(string(b))) > 0 {
				name = cleanText(strings.TrimSpace(string(b)), 60)
			}
			size, played := dirSize(wd)
			_, ierr := os.Stat(filepath.Join(wd, "world_icon.jpeg"))
			out = append(out, bedrockWorld{WorldInfo{Folder: e.Name(), Name: name, Size: size, Played: played, HasIcon: ierr == nil}, wd})
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Played > out[j].Played })
	return out
}

func findBedrockWorld(folder string) *bedrockWorld {
	for _, w := range bedrockWorlds() {
		if w.Folder == folder {
			return &w
		}
	}
	return nil
}

func backupBedrockWorld(folder string) (BackupInfo, error) {
	w := findBedrockWorld(folder)
	if w == nil {
		return BackupInfo{}, errors.New("world not found")
	}
	now := time.Now()
	base := cleanName(w.Name)
	if base == "" {
		base = "World"
	}
	name := base + "__" + now.Format("20060102-150405") + ".mcworld"
	dest := filepath.Join(backupDir("bedrock"), name)
	if err := zipDir(w.dir, dest, ""); err != nil {
		return BackupInfo{}, err
	}
	pruneBackups("bedrock", base)
	recordStat("backups", 1)
	info, _ := os.Stat(dest)
	return BackupInfo{File: name, World: base, At: now.Unix(), Size: info.Size()}, nil
}

var bedrockExts = map[string]bool{".mcpack": true, ".mcworld": true, ".mcaddon": true, ".mctemplate": true}

// openBedrockFile asks Windows to open a pack/world file from the launcher's own folder
// with Minecraft for Windows, which imports it.
func openBedrockFile(path string) error {
	if !insideData(path) || !bedrockExts[strings.ToLower(filepath.Ext(path))] {
		return errors.New("not allowed")
	}
	if !bedrockInstalled() {
		return errBedrockMissing
	}
	return shellOpenFile(path)
}

func backupRoutes(mux *http.ServeMux) {
	prof := func(w http.ResponseWriter, r *http.Request, id string) *Profile {
		p := findProfile(id)
		if p == nil || isBedrock(p) {
			jsonErr(w, errors.New("profile not found"))
			return nil
		}
		return p
	}
	mux.HandleFunc("GET /api/worlds", func(w http.ResponseWriter, r *http.Request) {
		p := prof(w, r, r.URL.Query().Get("id"))
		if p == nil {
			return
		}
		jsonOut(w, map[string]any{"worlds": javaWorlds(p), "backups": listBackups(p.ID), "auto": settingBool("autoBackup", false)})
	})
	mux.HandleFunc("GET /api/worlds/icon", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		var path string
		if q.Get("id") == "bedrock" {
			if bw := findBedrockWorld(q.Get("world")); bw != nil {
				path = filepath.Join(bw.dir, "world_icon.jpeg")
			}
		} else if p := findProfile(q.Get("id")); p != nil {
			path = filepath.Join(profileDir(p), "saves", cleanName(q.Get("world")), "icon.png")
		}
		if path == "" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Cache-Control", "max-age=300")
		http.ServeFile(w, r, path)
	})
	mux.HandleFunc("POST /api/worlds/backup", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, World string }
		body(r, &in)
		if in.ID == "bedrock" {
			b, err := backupBedrockWorld(in.World)
			if err != nil {
				jsonErr(w, err)
				return
			}
			jsonOut(w, b)
			return
		}
		p := prof(w, r, in.ID)
		if p == nil {
			return
		}
		b, err := backupJavaWorld(p, in.World)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, b)
	})
	mux.HandleFunc("POST /api/worlds/restore", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, File string }
		body(r, &in)
		if in.ID == "bedrock" {
			if cleanName(in.File) != in.File || !strings.HasSuffix(in.File, ".mcworld") {
				jsonErr(w, errors.New("backup not found"))
				return
			}
			if err := openBedrockFile(filepath.Join(backupDir("bedrock"), in.File)); err != nil {
				jsonErr(w, err)
				return
			}
			jsonOut(w, map[string]any{"ok": true})
			return
		}
		p := prof(w, r, in.ID)
		if p == nil {
			return
		}
		if profileRunning(p.ID) {
			jsonErr(w, errors.New("close the game first"))
			return
		}
		name, err := restoreJavaBackup(p, in.File)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true, "world": name})
	})
	mux.HandleFunc("POST /api/worlds/delete-backup", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, File string }
		body(r, &in)
		if cleanName(in.File) != in.File || reStamp.FindStringSubmatch(in.File) == nil {
			jsonErr(w, errors.New("backup not found"))
			return
		}
		owner := in.ID
		if owner != "bedrock" {
			if p := findProfile(in.ID); p != nil {
				owner = p.ID
			} else {
				jsonErr(w, errors.New("profile not found"))
				return
			}
		}
		os.Remove(filepath.Join(backupDir(owner), in.File))
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/worlds/open-backups", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID string }
		body(r, &in)
		owner := "bedrock"
		if in.ID != "bedrock" {
			p := findProfile(in.ID)
			if p == nil {
				jsonErr(w, errors.New("profile not found"))
				return
			}
			owner = p.ID
		}
		if err := openFolder(backupDir(owner)); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	// Bedrock
	mux.HandleFunc("GET /api/bedrock/worlds", func(w http.ResponseWriter, r *http.Request) {
		ws := bedrockWorlds()
		list := make([]WorldInfo, 0, len(ws))
		for _, x := range ws {
			list = append(list, x.WorldInfo)
		}
		jsonOut(w, map[string]any{"worlds": list, "backups": listBackups("bedrock"), "found": len(bedrockWorldDirs()) > 0})
	})
	mux.HandleFunc("POST /api/bedrock/open-worlds", func(w http.ResponseWriter, r *http.Request) {
		dirs := bedrockWorldDirs()
		if len(dirs) == 0 {
			jsonErr(w, errors.New("Couldn't find Bedrock's worlds folder. Open Bedrock once first"))
			return
		}
		if err := openSystemFolder(dirs[0]); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/bedrock/import", func(w http.ResponseWriter, r *http.Request) {
		name := filepath.Base(r.URL.Query().Get("name"))
		ext := strings.ToLower(filepath.Ext(name))
		if !bedrockExts[ext] {
			jsonErr(w, errors.New("pick a .mcpack, .mcworld, .mcaddon or .mctemplate file"))
			return
		}
		dir := filepath.Join(dataDir, "cache", "bedrock-import")
		os.MkdirAll(dir, 0o755)
		dest := filepath.Join(dir, randHex(4)+"-"+cleanName(strings.TrimSuffix(name, filepath.Ext(name)))+ext)
		f, err := os.Create(dest)
		if err != nil {
			jsonErr(w, err)
			return
		}
		n, err := io.Copy(f, io.LimitReader(r.Body, 2<<30))
		f.Close()
		if err != nil || n == 0 {
			os.Remove(dest)
			jsonErr(w, errors.New("couldn't read the file"))
			return
		}
		if zr, err := zip.OpenReader(dest); err != nil {
			os.Remove(dest)
			jsonErr(w, errors.New("that file isn't a Minecraft pack"))
			return
		} else {
			zr.Close()
		}
		if err := openBedrockFile(dest); err != nil {
			jsonErr(w, err)
			return
		}
		// old imports are cleaned up after a day
		if ents, err := os.ReadDir(dir); err == nil {
			for _, e := range ents {
				if info, err := e.Info(); err == nil && time.Since(info.ModTime()) > 24*time.Hour {
					os.Remove(filepath.Join(dir, e.Name()))
				}
			}
		}
		jsonOut(w, map[string]any{"ok": true})
	})
}
