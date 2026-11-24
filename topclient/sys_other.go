//go:build !windows

package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
)

// Non-Windows builds exist only for development/testing.
func protect(d []byte) ([]byte, error)   { return d, nil }
func unprotect(d []byte) ([]byte, error) { return d, nil }
func totalRAMMB() int                    { return 16384 }
func hideWindow(cmd *exec.Cmd)           {}
func gameWindow(cmd *exec.Cmd)           {}
func appDataDir() string {
	if d := os.Getenv("TOPCLIENT_DATA"); d != "" {
		return d
	}
	h, _ := os.UserHomeDir()
	return filepath.Join(h, ".topclient")
}
func openWindow(url string) error { fmt.Println("UI:", url); return nil }
func openFolder(path string) error {
	if !insideData(path) {
		return errors.New("not allowed")
	}
	fmt.Println("open folder:", path)
	return nil
}
func showError(msg string) { fmt.Println("ERROR:", msg) }

func findBrowser() string {
	for _, b := range []string{"google-chrome", "chromium", "chromium-browser", "microsoft-edge"} {
		if p, err := exec.LookPath(b); err == nil {
			return p
		}
	}
	return ""
}

func runNativeWindow(url string) error { return errors.New("not supported here") }
func showUI(url string)                { openWindow(url) }

func openExternal(u string) error { fmt.Println("OPEN:", u); return nil }

func findChrome() string { return "" }

func openDefault(u string) error { fmt.Println("OPEN:", u); return nil }

func appStatus() map[string]any     { return map[string]any{"supported": false} }
func installApp(desktop bool) error { return errors.New("only on Windows") }
func uninstallApp()                 {}

func updateSupported() bool              { return false }
func swapAndRestart(newExe string) error { return errors.New("only on Windows") }

func windowAction(a string) bool { return false }
func windowMaximized() bool      { return false }

var bedrockTestInstalled = false
var bedrockTestOpened string

func bedrockInstalled() bool { return bedrockTestInstalled }
func openMinecraftURI(u string) error {
	bedrockTestOpened = u
	return nil
}

func dialDiscord(i int) (io.ReadWriteCloser, error) { return nil, errors.New("not on Windows") }

func shellOpenFile(path string) error    { bedrockTestOpened = path; return nil }
func openSystemFolder(path string) error { return nil }
