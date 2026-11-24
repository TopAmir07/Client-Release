package main

import (
	"os"
	"os/exec"
	"path/filepath"
)

// Browsers the player can pick for Microsoft sign-in. Each one opens a private window,
// so it can't log straight into an account the browser already remembers.
type browserInfo struct {
	ID, Name string
	paths    [][2]string // env var, path inside it
	args     []string
}

var signInBrowsers = []browserInfo{
	{"chrome", "Google Chrome", [][2]string{{"ProgramFiles", `Google\Chrome\Application\chrome.exe`}, {"ProgramFiles(x86)", `Google\Chrome\Application\chrome.exe`}, {"LocalAppData", `Google\Chrome\Application\chrome.exe`}}, []string{"--incognito", "--new-window"}},
	{"edge", "Microsoft Edge", [][2]string{{"ProgramFiles(x86)", `Microsoft\Edge\Application\msedge.exe`}, {"ProgramFiles", `Microsoft\Edge\Application\msedge.exe`}}, []string{"--inprivate"}},
	{"firefox", "Firefox", [][2]string{{"ProgramFiles", `Mozilla Firefox\firefox.exe`}, {"ProgramFiles(x86)", `Mozilla Firefox\firefox.exe`}}, []string{"-private-window"}},
	{"brave", "Brave", [][2]string{{"ProgramFiles", `BraveSoftware\Brave-Browser\Application\brave.exe`}, {"ProgramFiles(x86)", `BraveSoftware\Brave-Browser\Application\brave.exe`}, {"LocalAppData", `BraveSoftware\Brave-Browser\Application\brave.exe`}}, []string{"--incognito"}},
	{"operagx", "Opera GX", [][2]string{{"LocalAppData", `Programs\Opera GX\opera.exe`}, {"LocalAppData", `Programs\Opera GX\launcher.exe`}}, []string{"--private"}},
	{"opera", "Opera", [][2]string{{"LocalAppData", `Programs\Opera\opera.exe`}, {"LocalAppData", `Programs\Opera\launcher.exe`}}, []string{"--private"}},
	{"vivaldi", "Vivaldi", [][2]string{{"LocalAppData", `Vivaldi\Application\vivaldi.exe`}, {"ProgramFiles", `Vivaldi\Application\vivaldi.exe`}}, []string{"--incognito"}},
}

func (b browserInfo) exe() string {
	for _, p := range b.paths {
		if base := os.Getenv(p[0]); base != "" {
			f := filepath.Join(base, filepath.FromSlash(filepath.ToSlash(p[1])))
			if st, err := os.Stat(f); err == nil && !st.IsDir() {
				return f
			}
		}
	}
	return ""
}

// installedBrowsers lists the browsers that can show the Microsoft sign-in window
// (Chromium-based ones: the launcher watches that window for Microsoft's "done" address).
func installedBrowsers() []map[string]string {
	out := []map[string]string{{"id": "auto", "name": "Automatic (Chrome, or Edge)"}}
	for _, b := range signInBrowsers {
		if windowCapable[b.ID] && b.exe() != "" {
			out = append(out, map[string]string{"id": b.ID, "name": b.Name})
		}
	}
	return out
}

var windowCapable = map[string]bool{"chrome": true, "edge": true, "brave": true, "vivaldi": true}

// windowBrowser picks the browser for the sign-in window: the player's choice when it can
// do it, otherwise Chrome, then Edge (always on Windows).
func windowBrowser() string {
	want := settingString("signinBrowser")
	if windowCapable[want] {
		for _, b := range signInBrowsers {
			if b.ID == want {
				if e := b.exe(); e != "" {
					return e
				}
			}
		}
	}
	if c := findChrome(); c != "" {
		return c
	}
	return findBrowser()
}

func openSignIn(u string) error {
	want := settingString("signinBrowser")
	if want == "" {
		want = "auto"
	}
	if want != "default" {
		for _, b := range signInBrowsers {
			if want != "auto" && b.ID != want {
				continue
			}
			if e := b.exe(); e != "" {
				return exec.Command(e, append(append([]string{}, b.args...), u)...).Start()
			}
		}
	}
	return openDefault(u)
}
