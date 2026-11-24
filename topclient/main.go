package main

import (
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const AppVersion = "1.2.1"

//go:embed web
var webFS embed.FS

var (
	dataDir  string
	apiToken string
	hostPort string
	lastPing atomic.Int64
	uiURL    string
	logger   *log.Logger
)

func logf(format string, a ...any) {
	if logger != nil {
		logger.Print(redactSecrets(fmt.Sprintf(format, a...)))
	}
}

func main() {
	// The updater runs a new download once with --selftest before installing it, to be sure
	// Windows lets it start (Smart App Control can block new versions). It does nothing else.
	for _, a := range os.Args[1:] {
		if a == "--selftest" {
			os.Exit(0)
		}
	}
	dataDir = appDataDir()
	if self, err := os.Executable(); err == nil {
		os.Remove(self + ".old") // left over from the last update
	}
	for _, a := range os.Args[1:] {
		if a == "--uninstall" {
			uninstallApp()
			return
		}
	}
	if err := os.MkdirAll(dataDir, 0o755); err != nil {
		showError("Could not create the launcher folder: " + err.Error())
		return
	}
	if lf, err := os.OpenFile(filepath.Join(dataDir, "launcher.log"), os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644); err == nil {
		logger = log.New(lf, "", log.LstdFlags)
	}

	// Single instance: if already running, just show its window.
	lockPath := filepath.Join(dataDir, "instance.json")
	var prev struct{ Addr, Token string }
	if readJSON(lockPath, &prev) == nil && prev.Addr != "" {
		c := &http.Client{Timeout: 2 * time.Second}
		call := func(method, path string) (map[string]any, bool) {
			req, _ := http.NewRequest(method, "http://"+prev.Addr+path, nil)
			req.Header.Set("X-Token", prev.Token)
			resp, err := c.Do(req)
			if err != nil {
				return nil, false
			}
			defer resp.Body.Close()
			var out map[string]any
			json.NewDecoder(resp.Body).Decode(&out)
			return out, resp.StatusCode == 200
		}
		if info, alive := call("GET", "/api/ping"); alive {
			if v, _ := info["version"].(string); v == AppVersion {
				call("POST", "/api/window/show") // same version: just bring it to the front
				return
			}
			// A different (older) launcher is still running in the background: close it so
			// this new version opens instead of the old window. Running games keep running.
			logf("replacing running launcher %v with %s", info["version"], AppVersion)
			if _, ok := call("POST", "/api/quit"); !ok {
				// Very old launchers can't be asked to close: never force-kill anything.
				showError("An older TopClient is still open in the background.\n\nClose it (or restart your PC), then open TopClient again.")
				return
			}
			for i := 0; i < 30; i++ {
				if _, still := call("GET", "/api/ping"); !still {
					break
				}
				time.Sleep(100 * time.Millisecond)
			}
		}
	}

	loadState()
	os.RemoveAll(filepath.Join(dataDir, "cache", "launch")) // leftover argument files from a crash
	apiToken = randHex(24)
	ln, err := net.Listen("tcp", "127.0.0.1:0") // local only, never exposed to network
	if err != nil {
		showError("Could not start: " + err.Error())
		return
	}
	hostPort = ln.Addr().String()
	writeJSON(lockPath, map[string]string{"Addr": hostPort, "Token": apiToken})
	defer os.Remove(lockPath)

	srv := &http.Server{Handler: guard(routes()), ReadHeaderTimeout: 10 * time.Second}
	go srv.Serve(ln)
	installDefaultBackground()
	// Keep the sign-in fresh in the background so Play starts straight away.
	go func() {
		for {
			if _, err := refreshAccountIf(3 * 3600); err != nil {
				logf("background sign-in refresh: %v", err)
			}
			time.Sleep(30 * time.Minute)
		}
	}()
	lastPing.Store(time.Now().Unix())
	url := "http://" + hostPort + "/#t=" + apiToken
	uiURL = url
	logf("listening on %s", hostPort)
	// A real app window (WebView2). Falls back to an Edge app window if needed.
	if err := runNativeWindow(url); err != nil {
		logf("native window: %v (using Edge window)", err)
		if err := openWindow(url); err != nil {
			showError("Could not open the launcher window: " + err.Error())
		}
	} else if !anyGameRunning() && !task.snapshot()["active"].(bool) {
		logf("window closed, exiting")
		return
	}

	// Quit once the window is closed (no heartbeat) and no game is running.
	for {
		time.Sleep(5 * time.Second)
		idle := time.Now().Unix() - lastPing.Load()
		if idle > 90 && !anyGameRunning() && !task.snapshot()["active"].(bool) {
			logf("window closed, exiting")
			return
		}
	}
}

// guard blocks anything that isn't our own window talking to us.
func guard(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Host != hostPort { // DNS-rebinding protection
			http.Error(w, "forbidden", 403)
			return
		}
		if o := r.Header.Get("Origin"); o != "" && o != "http://"+hostPort {
			http.Error(w, "forbidden", 403)
			return
		}
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("Referrer-Policy", "no-referrer")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "+
			"img-src 'self' data: blob: https://cdn.modrinth.com https://*.modrinth.com; media-src 'self' blob:; "+
			"connect-src 'self' https://api.modrinth.com; font-src 'self' data:; frame-ancestors 'none'")
		if strings.HasPrefix(r.URL.Path, "/api/") {
			tok := r.Header.Get("X-Token")
			if tok == "" {
				tok = r.URL.Query().Get("t")
			}
			if subtle.ConstantTimeCompare([]byte(tok), []byte(apiToken)) != 1 {
				http.Error(w, "forbidden", 403)
				return
			}
			h.Set("Cache-Control", "no-store")
		}
		next.ServeHTTP(w, r)
	})
}

func jsonOut(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func jsonErr(w http.ResponseWriter, err error) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(400)
	json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
}

func body(r *http.Request, v any) error {
	return json.NewDecoder(io.LimitReader(r.Body, 4<<20)).Decode(v)
}

var staticTags sync.Map

func staticTag(fsys fs.FS, urlPath string) string {
	name := strings.TrimPrefix(path.Clean("/"+urlPath), "/")
	if name == "" {
		name = "index.html"
	}
	if v, ok := staticTags.Load(name); ok {
		return v.(string)
	}
	b, err := fs.ReadFile(fsys, name)
	if err != nil {
		return ""
	}
	sum := sha256.Sum256(b)
	tag := `"` + hex.EncodeToString(sum[:8]) + `"`
	staticTags.Store(name, tag)
	return tag
}

func routes() *http.ServeMux {
	mux := http.NewServeMux()
	sub, _ := fs.Sub(webFS, "web")
	static := http.FileServer(http.FS(sub))
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache")
		// The interface files are built into the exe, so a fingerprint of each file lets the
		// window reuse what it already has (fonts, pictures, cosmetics) instead of loading it again.
		if tag := staticTag(sub, r.URL.Path); tag != "" {
			w.Header().Set("ETag", tag)
			if r.Header.Get("If-None-Match") == tag {
				w.WriteHeader(http.StatusNotModified)
				return
			}
		}
		static.ServeHTTP(w, r)
	}))

	shotRoutes(mux)
	updateRoutes(mux)
	skinRoutes(mux)
	crashRoutes(mux)
	serverRoutes(mux)
	backupRoutes(mux)
	mux.HandleFunc("GET /api/app", func(w http.ResponseWriter, r *http.Request) { jsonOut(w, appStatus()) })
	mux.HandleFunc("POST /api/app/install", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Desktop bool }
		body(r, &in)
		if err := installApp(in.Desktop); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, appStatus())
	})
	mux.HandleFunc("GET /api/browsers", func(w http.ResponseWriter, r *http.Request) { jsonOut(w, installedBrowsers()) })
	// The launcher's own title bar: move, resize, minimise, maximise, close.
	mux.HandleFunc("POST /api/window/action", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, map[string]any{"ok": windowAction(r.URL.Query().Get("a")), "max": windowMaximized()})
	})
	mux.HandleFunc("GET /api/window/state", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, map[string]any{"max": windowMaximized()})
	})
	mux.HandleFunc("POST /api/window/show", func(w http.ResponseWriter, r *http.Request) {
		showUI(uiURL)
		jsonOut(w, map[string]any{"ok": true})
	})
	// Opens a web page in Google Chrome (or the normal browser if Chrome isn't installed).
	// Only well-known https sites are allowed, so nothing else can be started this way.
	mux.HandleFunc("POST /api/open-url", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ URL string }
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		u, ok := safeExternalURL(in.URL)
		if !ok {
			jsonErr(w, fmt.Errorf("that link can't be opened from the launcher"))
			return
		}
		if err := openExternal(u); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/ping", func(w http.ResponseWriter, r *http.Request) {
		lastPing.Store(time.Now().Unix())
		jsonOut(w, map[string]any{"ok": true, "version": AppVersion})
	})
	// Used when a newer TopClient.exe is opened: this one steps aside.
	mux.HandleFunc("POST /api/quit", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, map[string]any{"ok": true})
		go func() {
			time.Sleep(200 * time.Millisecond)
			os.Remove(filepath.Join(dataDir, "instance.json"))
			os.Exit(0)
		}()
	})

	mux.HandleFunc("GET /api/state", func(w http.ResponseWriter, r *http.Request) {
		stateMu.Lock()
		s := map[string]any{"settings": settings, "profiles": profiles}
		b, _ := json.Marshal(s)
		stateMu.Unlock()
		var out map[string]any
		json.Unmarshal(b, &out)
		out["accounts"] = publicAccounts()
		out["ramMB"] = totalRAMMB()
		out["version"] = AppVersion
		tv, _ := topClientInfo()
		out["topclientMod"] = map[string]any{"version": tv, "mc": topClientMC()}
			_, err := os.Stat(bgPath())
		out["hasBackground"] = err == nil
		jsonOut(w, out)
	})

	mux.HandleFunc("POST /api/settings", func(w http.ResponseWriter, r *http.Request) {
		var in map[string]any
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		if v, ok := in["trayIcon"].(bool); ok {
			defer trayApply(v)
		}
		stateMu.Lock()
		for k, v := range in {
			if v == nil {
				delete(settings, k)
			} else {
				settings[k] = v
			}
		}
		stateMu.Unlock()
		saveSettings()
		jsonOut(w, map[string]any{"ok": true})
	})

	mux.HandleFunc("GET /api/settings/export", func(w http.ResponseWriter, r *http.Request) {
		b, _ := exportSettings()
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Content-Disposition", `attachment; filename="topclient-settings.json"`)
		w.Write(b)
	})

	mux.HandleFunc("POST /api/settings/import", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			Settings map[string]any `json:"settings"`
		}
		if err := body(r, &in); err != nil || in.Settings == nil {
			jsonErr(w, fmt.Errorf("not a TopClient settings file"))
			return
		}
		stateMu.Lock()
		for k, v := range in.Settings {
			// Never take Java paths or Java arguments from a shared file: those could
			// make the game run something else. Accounts are never in settings at all.
			if k != "background" && k != "javaPath" && k != "javaArgs" && k != "selectedProfile" {
				settings[k] = v
			}
		}
		stateMu.Unlock()
		saveSettings()
		jsonOut(w, map[string]any{"ok": true})
	})

	mux.HandleFunc("GET /api/versions", func(w http.ResponseWriter, r *http.Request) {
		m, err := getManifest()
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, m)
	})

	// --- profiles ---------------------------------------------------------
	mux.HandleFunc("POST /api/profiles", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Name, MC, Loader string }
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, newProfile(in.Name, in.MC, in.Loader))
	})
	mux.HandleFunc("POST /api/profiles/update", func(w http.ResponseWriter, r *http.Request) {
		var in Profile
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		stateMu.Lock()
		if strings.TrimSpace(in.Name) != "" {
			p.Name = strings.TrimSpace(in.Name)
		}
		if in.MC != "" && in.MC != p.MC {
			p.MC, p.LoaderVer = in.MC, ""
		}
		if in.Loader != "" && in.Loader != p.Loader && (in.Loader == "fabric" || in.Loader == "vanilla") {
			p.Loader, p.LoaderVer = in.Loader, ""
		}
		p.RamMB, p.JavaArgs, p.Width, p.Height = in.RamMB, in.JavaArgs, in.Width, in.Height
		if in.Icon != "" && !strings.HasPrefix(in.Icon, "custom") && len(in.Icon) < 32 {
			if p.Icon != "" && strings.HasPrefix(p.Icon, "custom") {
				os.Remove(profileIconPath(p))
			}
			p.Icon = in.Icon
		}
		stateMu.Unlock()
		saveProfiles()
		jsonOut(w, p)
	})
	mux.HandleFunc("POST /api/profiles/fps", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			ID       string
			Level    string
			PerfMods bool
		}
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		if err := setFPSBoost(p, in.Level, in.PerfMods); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, p)
	})
	mux.HandleFunc("POST /api/profiles/delete", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID string }
		body(r, &in)
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		stateMu.Lock()
		var keep []*Profile
		for _, x := range profiles {
			if x.ID != in.ID {
				keep = append(keep, x)
			}
		}
		profiles = keep
		stateMu.Unlock()
		saveProfiles()
		dir := profileDir(p)
		if insideData(dir) && filepath.Base(filepath.Dir(dir)) == "profiles" {
			os.RemoveAll(dir)
		}
		for n := 2; n <= 8; n++ { // folders of extra windows
			if d := copyDirFor(p, p.ID+"~"+strconv.Itoa(n)); insideData(d) {
				os.RemoveAll(d)
			}
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/profiles/open", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, Sub string }
		body(r, &in)
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		dir := profileDir(p)
		if in.Sub != "" {
			if d, err := safeJoin(dir, in.Sub); err == nil {
				dir = d
			}
		}
		if err := openFolder(dir); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/profiles/content", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("id"))
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		list, pending, err := listContent(p, r.URL.Query().Get("kind"))
		if err != nil {
			jsonErr(w, err)
			return
		}
		w.Header().Set("X-Identifying", strconv.FormatBool(pending))
		jsonOut(w, list)
	})
	// Custom profile pictures: checked to really be a picture, kept in the launcher's own folder.
	mux.HandleFunc("POST /api/profiles/icon", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("id"))
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		data, err := io.ReadAll(io.LimitReader(r.Body, 6<<20+1))
		if err != nil || len(data) > 6<<20 {
			jsonErr(w, fmt.Errorf("pick a picture smaller than 6 MB"))
			return
		}
		if !isImage(data) {
			jsonErr(w, fmt.Errorf("use a PNG, JPG, GIF or WEBP picture"))
			return
		}
		os.MkdirAll(filepath.Dir(profileIconPath(p)), 0o755)
		if err := os.WriteFile(profileIconPath(p), data, 0o644); err != nil {
			jsonErr(w, err)
			return
		}
		stateMu.Lock()
		p.Icon = "custom:" + strconv.FormatInt(time.Now().Unix(), 10)
		stateMu.Unlock()
		saveProfiles()
		jsonOut(w, p)
	})
	mux.HandleFunc("GET /api/profiles/icon", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("id"))
		if p == nil {
			http.Error(w, "no", 404)
			return
		}
		b, err := os.ReadFile(profileIconPath(p))
		if err != nil || !isImage(b) {
			http.Error(w, "no", 404)
			return
		}
		w.Header().Set("Content-Type", http.DetectContentType(b))
		w.Header().Set("Cache-Control", "max-age=86400")
		w.Write(b)
	})
	mux.HandleFunc("GET /api/profiles/modicon", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("id"))
		if p == nil {
			http.Error(w, "no", 404)
			return
		}
		path, err := contentPath(p, r.URL.Query().Get("kind"), r.URL.Query().Get("file"))
		if err != nil {
			http.Error(w, "no", 404)
			return
		}
		b, err := jarIcon(path)
		if err != nil {
			http.Error(w, "no", 404)
			return
		}
		w.Header().Set("Content-Type", http.DetectContentType(b))
		w.Header().Set("Cache-Control", "max-age=86400")
		w.Write(b)
	})
	mux.HandleFunc("POST /api/profiles/toggle", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, Kind, File string }
		body(r, &in)
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		src, err := contentPath(p, in.Kind, in.File)
		if err != nil {
			jsonErr(w, err)
			return
		}
		dst := src + ".disabled"
		if strings.HasSuffix(src, ".disabled") {
			dst = strings.TrimSuffix(src, ".disabled")
		}
		if err := os.Rename(src, dst); err != nil {
			jsonErr(w, err)
			return
		}
		// Turning a mod on switches off any other copy of the same mod (same mod id),
		// e.g. two versions of one mod: Minecraft refuses to start with both.
		off := []string{}
		if in.Kind == "mods" && strings.HasSuffix(strings.ToLower(dst), ".jar") {
			off = disableSameMod(p, dst)
		}
		jsonOut(w, map[string]any{"ok": true, "disabled": off})
	})
	mux.HandleFunc("POST /api/profiles/remove", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, Kind, File string }
		body(r, &in)
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		path, err := contentPath(p, in.Kind, in.File)
		if err != nil {
			jsonErr(w, err)
			return
		}
		os.Remove(path)
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/profiles/export", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("id"))
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		b, err := exportMrpack(p)
		if err != nil {
			jsonErr(w, err)
			return
		}
		w.Header().Set("Content-Type", "application/x-modrinth-modpack+zip")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s.mrpack"`, cleanName(p.Name)))
		w.Write(b)
	})
	mux.HandleFunc("GET /api/profiles/installed", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("id"))
		out := map[string]bool{}
		if p != nil {
			for _, m := range readMeta(p) {
				out[m.Project] = true
			}
		}
		jsonOut(w, out)
	})

	// --- launch -----------------------------------------------------------
	mux.HandleFunc("POST /api/launch", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			ID      string
			Repair  bool
			Another bool
		}
		body(r, &in)
		p := findProfile(in.ID)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		if err := launch(p, in.Repair, in.Another); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/cancel", func(w http.ResponseWriter, r *http.Request) {
		if launchCancel != nil {
			launchCancel()
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/kill", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID string }
		body(r, &in)
		killGame(in.ID)
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/status", func(w http.ResponseWriter, r *http.Request) {
		lastPing.Store(time.Now().Unix())
		jsonOut(w, map[string]any{"task": task.snapshot(), "running": runningList(), "instances": instanceList(), "crashes": pendingCrashes()})
	})
	mux.HandleFunc("GET /api/logs", func(w http.ResponseWriter, r *http.Request) {
		var since int
		fmt.Sscan(r.URL.Query().Get("since"), &since)
		lines, next, running := gameLogs(r.URL.Query().Get("id"), since)
		jsonOut(w, map[string]any{"lines": lines, "next": next, "running": running})
	})

	// --- accounts ---------------------------------------------------------
	mux.HandleFunc("GET /api/auth/live/url", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, map[string]string{"url": liveAuthorizeURL()})
	})
	mux.HandleFunc("POST /api/auth/live/start", func(w http.ResponseWriter, r *http.Request) {
		startLiveLogin()
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/auth/live/status", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, liveLoginStatus())
	})
	mux.HandleFunc("POST /api/auth/live/cancel", func(w http.ResponseWriter, r *http.Request) {
		cancelLiveLogin()
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/auth/live/finish", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ URL string }
		body(r, &in)
		acc, err := finishLive(in.URL)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]string{"name": acc.Name})
	})
	mux.HandleFunc("POST /api/auth/device/start", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Azure bool }
		body(r, &in)
		// Only the player's own Azure app uses codes now; normal sign-in is the Microsoft window.
		cid := strings.TrimSpace(settingString("clientId"))
		if cid == "" {
			jsonErr(w, fmt.Errorf("use Sign in with Microsoft"))
			return
		}
		res, err := startDevice(cid)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, res)
	})
	mux.HandleFunc("GET /api/auth/device/status", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, deviceStatus())
	})
	mux.HandleFunc("POST /api/accounts/select", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ UUID string }
		body(r, &in)
		selectAccount(in.UUID)
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/accounts/remove", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ UUID string }
		body(r, &in)
		removeAccount(in.UUID)
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/accounts/refresh", func(w http.ResponseWriter, r *http.Request) {
		acc := activeAccount()
		if acc == nil {
			jsonErr(w, fmt.Errorf("not logged in"))
			return
		}
		if _, err := ensureFreshAccount(); err != nil {
			jsonErr(w, err)
			return
		}
		acc = activeAccount()
		if err := fetchProfile(acc); err != nil {
			jsonErr(w, err)
			return
		}
		storeAccount(acc)
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/skin", func(w http.ResponseWriter, r *http.Request) {
		b, err := skinBytes(r.URL.Query().Get("uuid"))
		if err != nil {
			http.Error(w, "no skin", 404)
			return
		}
		w.Header().Set("Content-Type", "image/png")
		w.Write(b)
	})

	// --- Modrinth ---------------------------------------------------------
	mux.HandleFunc("POST /api/modrinth/install", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, Project, Version string }
		body(r, &in)
		p := findProfile(in.Profile)
		if p == nil {
			jsonErr(w, fmt.Errorf("pick a profile first"))
			return
		}
		if !task.begin(p.ID) {
			jsonErr(w, fmt.Errorf("another download is in progress — try again in a moment"))
			return
		}
		err := installProject(p, in.Project, in.Version, 0, map[string]bool{})
		task.end(err)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})

	mux.HandleFunc("POST /api/profiles/upgrade", func(w http.ResponseWriter, r *http.Request) {
		var in UpgradeOptions
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		if !task.begin("upgrade") {
			jsonErr(w, fmt.Errorf("another download is in progress — try again in a moment"))
			return
		}
		res, err := upgradeProfile(in)
		task.end(err)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, res)
	})

	// --- imports ----------------------------------------------------------
	mux.HandleFunc("POST /api/import/upload", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		kind, name := q.Get("kind"), cleanName(q.Get("name"))
		data, err := io.ReadAll(io.LimitReader(r.Body, 1<<30))
		if err != nil {
			jsonErr(w, err)
			return
		}
		if kind == "mrpack" {
			tmp := filepath.Join(dataDir, "cache", "packs", "upload-"+randHex(4)+".mrpack")
			os.MkdirAll(filepath.Dir(tmp), 0o755)
			os.WriteFile(tmp, data, 0o644)
			defer os.Remove(tmp)
			if !task.begin("import") {
				jsonErr(w, fmt.Errorf("another download is in progress"))
				return
			}
			p, err := importMrpack(tmp)
			task.end(err)
			if err != nil {
				jsonErr(w, err)
				return
			}
			msg := p.Name + " imported as a new profile"
			if n := lastPackHeldBack.Load(); n > 0 {
				msg += fmt.Sprintf(". %d mod(s) came inside the pack file instead of from Modrinth, so they were switched off — turn them on in the profile only if you trust the pack", n)
			}
			jsonOut(w, map[string]string{"message": msg, "profile": p.ID})
			return
		}
		p := findProfile(q.Get("profile"))
		if p == nil {
			jsonErr(w, fmt.Errorf("pick a profile first"))
			return
		}
		var dest string
		low := strings.ToLower(name)
		switch kind {
		case "resourcepack":
			if !strings.HasSuffix(low, ".zip") {
				jsonErr(w, fmt.Errorf("resource packs must be .zip files"))
				return
			}
			dest = filepath.Join(profileDir(p), "resourcepacks", name)
		case "shaderpack":
			if !strings.HasSuffix(low, ".zip") {
				jsonErr(w, fmt.Errorf("shader packs must be .zip files"))
				return
			}
			dest = filepath.Join(profileDir(p), "shaderpacks", name)
		case "mod":
			if !strings.HasSuffix(low, ".jar") {
				jsonErr(w, fmt.Errorf("mods must be .jar files"))
				return
			}
			dest = filepath.Join(profileDir(p), "mods", name)
		case "options":
			if low != "options.txt" && low != "optionsof.txt" && low != "optionsshaders.txt" {
				name = "options.txt"
			}
			dest = filepath.Join(profileDir(p), name)
		default:
			jsonErr(w, fmt.Errorf("unknown import type"))
			return
		}
		if !insideData(dest) {
			jsonErr(w, fmt.Errorf("not allowed"))
			return
		}
		os.MkdirAll(filepath.Dir(dest), 0o755)
		if err := os.WriteFile(dest, data, 0o644); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]string{"message": name + " added to " + p.Name})
	})

	// Import one file of an instance folder the user picked (Modrinth App,
	// Prism, Dawn, .minecraft ...). Only known game folders are accepted.
	mux.HandleFunc("POST /api/import/folderfile", func(w http.ResponseWriter, r *http.Request) {
		p := findProfile(r.URL.Query().Get("profile"))
		rel := strings.ReplaceAll(r.URL.Query().Get("path"), "\\", "/")
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		top := strings.SplitN(rel, "/", 2)[0]
		allowed := map[string]bool{"mods": true, "config": true, "resourcepacks": true, "shaderpacks": true,
			"options.txt": true, "optionsof.txt": true, "optionsshaders.txt": true, "servers.dat": true}
		if !allowed[top] {
			jsonErr(w, fmt.Errorf("skipped"))
			return
		}
		dest, err := safeJoin(profileDir(p), rel)
		if err != nil {
			jsonErr(w, err)
			return
		}
		os.MkdirAll(filepath.Dir(dest), 0o755)
		f, err := os.Create(dest)
		if err != nil {
			jsonErr(w, err)
			return
		}
		io.Copy(f, io.LimitReader(r.Body, 1<<30))
		f.Close()
		jsonOut(w, map[string]any{"ok": true})
	})

	mux.HandleFunc("POST /api/import/link", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ URL, Profile string }
		body(r, &in)
		if !task.begin("import") {
			jsonErr(w, fmt.Errorf("another download is in progress"))
			return
		}
		msg, err := importLink(in.URL, findProfile(in.Profile))
		task.end(err)
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]string{"message": msg})
	})

	mux.HandleFunc("POST /api/open-data", func(w http.ResponseWriter, r *http.Request) {
		if err := openFolder(dataDir); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})

	// --- background -------------------------------------------------------
	mux.HandleFunc("POST /api/background", func(w http.ResponseWriter, r *http.Request) {
		ct, err := saveBackground(r.Body)
		if err != nil {
			io.Copy(io.Discard, io.LimitReader(r.Body, maxBackground)) // let the window finish sending, so it sees the message
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true, "type": ct})
	})
	mux.HandleFunc("POST /api/background/clear", func(w http.ResponseWriter, r *http.Request) {
		os.Remove(bgPath())
		os.Remove(bgPath() + ".type")
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("GET /api/background", func(w http.ResponseWriter, r *http.Request) {
		ct, _ := os.ReadFile(bgPath() + ".type")
		if len(ct) > 0 {
			w.Header().Set("Content-Type", string(ct))
		}
		http.ServeFile(w, r, bgPath())
	})
	return mux
}

var externalHosts = []string{
	"modrinth.com", "minecraft.net", "microsoft.com", "login.live.com", "account.live.com",
	"xbox.com", "open.spotify.com", "spotify.com", "dawn.gg", "fabricmc.net", "mojang.com", "discord.gg", "discord.com",
}

func safeExternalURL(raw string) (string, bool) {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || u.Scheme != "https" || u.User != nil || u.Host == "" || len(raw) > 2048 {
		return "", false
	}
	h := strings.ToLower(u.Hostname())
	for _, ok := range externalHosts {
		if h == ok || strings.HasSuffix(h, "."+ok) {
			return u.String(), true
		}
	}
	return "", false
}

func isSignInURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil {
		return false
	}
	h := strings.ToLower(u.Hostname())
	return h == "microsoft.com" || h == "www.microsoft.com" || h == "login.live.com" || h == "login.microsoftonline.com" || h == "account.live.com"
}

func profileIconPath(p *Profile) string {
	return filepath.Join(dataDir, "ui", "icons", cleanName(p.ID)+".bin")
}

func isImage(b []byte) bool {
	switch http.DetectContentType(b) {
	case "image/png", "image/jpeg", "image/gif", "image/webp":
		return true
	}
	return false
}

func bgPath() string { return filepath.Join(dataDir, "ui", "background.bin") }

// sniffVideo recognises video files by their first bytes: MP4/MOV/M4V (an ISO "ftyp" box,
// any brand) and WEBM/MKV (Matroska). Returns "" for anything else.
func sniffVideo(b []byte) string {
	if len(b) >= 12 && string(b[4:8]) == "ftyp" {
		return "video/mp4"
	}
	if len(b) >= 4 && b[0] == 0x1A && b[1] == 0x45 && b[2] == 0xDF && b[3] == 0xA3 {
		return "video/webm"
	}
	return ""
}

// Backgrounds can be big videos: they are streamed straight to disk (never held in memory).
const maxBackground = 4 << 30 // 4 GB

// saveBackground writes the upload to a temp file next to the background, checks from the
// file's own first bytes that it really is a picture or video, then swaps it in.
func saveBackground(body io.Reader) (string, error) {
	dir := filepath.Dir(bgPath())
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	head := make([]byte, 512)
	n, err := io.ReadFull(body, head)
	if err != nil && err != io.ErrUnexpectedEOF {
		if err == io.EOF {
			return "", fmt.Errorf("that file is empty")
		}
		return "", err
	}
	head = head[:n]
	ct := http.DetectContentType(head)
	if !isImage(head) {
		ct = sniffVideo(head)
	}
	if ct == "" {
		return "", fmt.Errorf("use a PNG, JPG, GIF, WEBP, MP4, MOV, WEBM or MKV file")
	}
	tmp, err := os.CreateTemp(dir, "background-*.part")
	if err != nil {
		return "", err
	}
	defer os.Remove(tmp.Name()) // no-op once renamed
	if _, err := tmp.Write(head); err != nil {
		tmp.Close()
		return "", err
	}
	written, err := io.Copy(tmp, io.LimitReader(body, maxBackground+1-int64(len(head))))
	if cerr := tmp.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		return "", fmt.Errorf("could not save the file: %v", err)
	}
	if written+int64(len(head)) > maxBackground {
		return "", fmt.Errorf("that file is over 4 GB, pick a smaller one")
	}
	// Windows can't replace a file that's still open (the old video may still be playing):
	// retry for a few seconds while the window lets go of it.
	var rerr error
	for i := 0; i < 40; i++ {
		os.Remove(bgPath())
		if rerr = os.Rename(tmp.Name(), bgPath()); rerr == nil {
			break
		}
		time.Sleep(150 * time.Millisecond)
	}
	if rerr != nil {
		return "", fmt.Errorf("the old background is still in use, try again: %v", rerr)
	}
	os.WriteFile(bgPath()+".type", []byte(ct), 0o644)
	return ct, nil
}
