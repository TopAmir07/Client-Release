//go:build windows

package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

var (
	crypt32    = syscall.NewLazyDLL("crypt32.dll")
	kernel32   = syscall.NewLazyDLL("kernel32.dll")
	pProtect   = crypt32.NewProc("CryptProtectData")
	pUnprotect = crypt32.NewProc("CryptUnprotectData")
	pLocalFree = kernel32.NewProc("LocalFree")
	pMemStatus = kernel32.NewProc("GlobalMemoryStatusEx")
)

type dataBlob struct {
	cb uint32
	pb *byte
}

func newBlob(d []byte) *dataBlob {
	if len(d) == 0 {
		return &dataBlob{}
	}
	return &dataBlob{cb: uint32(len(d)), pb: &d[0]}
}

func (b *dataBlob) bytes() []byte {
	out := make([]byte, b.cb)
	copy(out, unsafe.Slice(b.pb, b.cb))
	return out
}

// DPAPI: encrypted so only this Windows user on this PC can decrypt.
func protect(d []byte) ([]byte, error) {
	var out dataBlob
	r, _, err := pProtect.Call(uintptr(unsafe.Pointer(newBlob(d))), 0, 0, 0, 0, 1, uintptr(unsafe.Pointer(&out)))
	if r == 0 {
		return nil, err
	}
	defer pLocalFree.Call(uintptr(unsafe.Pointer(out.pb)))
	return out.bytes(), nil
}

func unprotect(d []byte) ([]byte, error) {
	var out dataBlob
	r, _, err := pUnprotect.Call(uintptr(unsafe.Pointer(newBlob(d))), 0, 0, 0, 0, 1, uintptr(unsafe.Pointer(&out)))
	if r == 0 {
		return nil, err
	}
	defer pLocalFree.Call(uintptr(unsafe.Pointer(out.pb)))
	return out.bytes(), nil
}

func totalRAMMB() int {
	var m struct {
		Length                                                  uint32
		Load                                                    uint32
		Total, Avail, TotalPF, AvailPF, TotalV, AvailV, AvailEx uint64
	}
	m.Length = uint32(unsafe.Sizeof(m))
	if r, _, _ := pMemStatus.Call(uintptr(unsafe.Pointer(&m))); r == 0 {
		return 8192
	}
	return int(m.Total / 1024 / 1024)
}

func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
}

// gameWindow: no console window for Java, but the game's own window must NOT be told to start
// hidden. New Minecraft ignores that, but old versions (1.8.9-1.12, LWJGL 2) obey it and the
// game runs with an invisible window.
func gameWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: 0x08000000}
}

func appDataDir() string {
	base := os.Getenv("APPDATA")
	if base == "" {
		base, _ = os.UserConfigDir()
	}
	return filepath.Join(base, ".topclient")
}

func findBrowser() string {
	var cands []string
	for _, env := range []string{"ProgramFiles(x86)", "ProgramFiles", "LocalAppData"} {
		if b := os.Getenv(env); b != "" {
			cands = append(cands, filepath.Join(b, "Microsoft", "Edge", "Application", "msedge.exe"))
		}
	}
	for _, env := range []string{"ProgramFiles", "ProgramFiles(x86)", "LocalAppData"} {
		if b := os.Getenv(env); b != "" {
			cands = append(cands, filepath.Join(b, "Google", "Chrome", "Application", "chrome.exe"))
		}
	}
	for _, c := range cands {
		if _, err := os.Stat(c); err == nil {
			return c
		}
	}
	return ""
}

// openWindow shows the launcher UI in its own app window (Edge/Chrome "app
// mode" with a private profile kept inside the launcher folder).
func openWindow(url string) error {
	if b := findBrowser(); b != "" {
		cmd := exec.Command(b, "--app="+url,
			"--user-data-dir="+filepath.Join(dataDir, "window"),
			"--window-size=1360,860", "--no-first-run", "--no-default-browser-check",
			"--disable-background-timer-throttling", "--disable-renderer-backgrounding",
			"--disable-features=Translate,msEdgeSidebarV2,msHubApps")
		return cmd.Start()
	}
	return shellOpen(url)
}

func openFolder(path string) error {
	if !insideData(path) {
		return errors.New("not allowed")
	}
	os.MkdirAll(path, 0o755)
	return exec.Command("explorer.exe", path).Start()
}

func showError(msg string) {
	u32 := syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW")
	t, _ := syscall.UTF16PtrFromString("TopClient")
	m, _ := syscall.UTF16PtrFromString(msg)
	u32.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x10)
}

// openExternal opens an already-checked https link in Google Chrome when it is installed,
// otherwise in the default browser.
func openExternal(u string) error {
	if isSignInURL(u) {
		return openSignIn(u)
	}
	if c := findChrome(); c != "" {
		return exec.Command(c, "--new-window", u).Start()
	}
	return openDefault(u)
}

func openDefault(u string) error { return shellOpen(u) }

// shellOpen asks Windows to open an https link in the default browser (ShellExecute).
func shellOpen(u string) error {
	if !strings.HasPrefix(strings.ToLower(u), "https://") && !strings.HasPrefix(strings.ToLower(u), "http://127.0.0.1:") {
		return errors.New("only web links can be opened")
	}
	verb, _ := syscall.UTF16PtrFromString("open")
	file, _ := syscall.UTF16PtrFromString(u)
	r, _, _ := syscall.NewLazyDLL("shell32.dll").NewProc("ShellExecuteW").Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(file)), 0, 0, 1)
	if r <= 32 {
		return fmt.Errorf("could not open the link (%d)", r)
	}
	return nil
}

func findChrome() string {
	for _, env := range []string{"ProgramFiles", "ProgramFiles(x86)", "LocalAppData"} {
		if b := os.Getenv(env); b != "" {
			c := filepath.Join(b, "Google", "Chrome", "Application", "chrome.exe")
			if _, err := os.Stat(c); err == nil {
				return c
			}
		}
	}
	return ""
}

func syscallUser32MessageBox() func(string, uintptr) error {
	return func(msg string, flags uintptr) error {
		u32 := syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW")
		t, _ := syscall.UTF16PtrFromString("TopClient")
		m, _ := syscall.UTF16PtrFromString(msg)
		u32.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), flags)
		return nil
	}
}

func updateSupported() bool { return true }

// swapAndRestart puts the new exe where the running one is (and over the installed app,
// if that's a different copy), then starts it. The new launcher closes this one.
func swapAndRestart(newExe string) error {
	self, err := os.Executable()
	if err != nil {
		return err
	}
	data, err := os.ReadFile(newExe)
	if err != nil {
		return err
	}
	// 1. Can Windows start the new version at all? (Smart App Control / App Control can block
	//    new unsigned programs.) Test it before touching the installed launcher.
	test := exec.Command(newExe, "--selftest")
	test.Dir = filepath.Dir(newExe)
	if err := test.Start(); err != nil {
		os.Remove(newExe)
		if blockedByPolicy(err) {
			return errors.New("Windows (Smart App Control) blocked the new version, so the update was stopped. Your launcher is unchanged and still works")
		}
		return fmt.Errorf("the new version couldn't start (%v), so the update was stopped. Your launcher is unchanged", err)
	}
	done := make(chan error, 1)
	go func() { done <- test.Wait() }()
	select {
	case <-done:
	case <-time.After(15 * time.Second):
		test.Process.Kill()
	}
	// 2. Swap it in, keeping the old one next to it until the new one has started.
	var swapped []string
	replace := func(target string) error {
		os.Remove(target + ".old")
		if err := os.Rename(target, target+".old"); err != nil && !os.IsNotExist(err) {
			return err
		}
		if err := os.WriteFile(target, data, 0o755); err != nil {
			os.Rename(target+".old", target) // put the old one back
			return err
		}
		swapped = append(swapped, target)
		return nil
	}
	rollback := func() {
		for _, t := range swapped {
			os.Remove(t)
			os.Rename(t+".old", t)
		}
	}
	if err := replace(self); err != nil {
		return fmt.Errorf("couldn't replace the launcher: %v", err)
	}
	inst := installedExe()
	if exists(inst) && !strings.EqualFold(filepath.Clean(inst), filepath.Clean(self)) {
		replace(inst)
	}
	os.Remove(newExe)
	cmd := exec.Command(self, "--updated")
	cmd.Dir = filepath.Dir(self)
	if err := cmd.Start(); err != nil {
		rollback() // 3. it didn't start after all: put the working launcher back
		return fmt.Errorf("the new version couldn't start, so your old launcher was put back (%v)", err)
	}
	return nil
}

// blockedByPolicy: Windows refused to run the file (Smart App Control / App Control).
func blockedByPolicy(err error) bool {
	var en syscall.Errno
	if errors.As(err, &en) && (en == 4551 || en == 1260) { // ERROR_SYSTEM_INTEGRITY_POLICY_VIOLATION, ERROR_ACCESS_DISABLED_BY_POLICY
		return true
	}
	m := strings.ToLower(err.Error())
	return strings.Contains(m, "application control") || strings.Contains(m, "policy")
}

// bedrockInstalled: is something registered to open "minecraft:" links? That's
// Minecraft for Windows (Bedrock) when it's installed from the Store / Xbox app.
func bedrockInstalled() bool {
	const assocfIsProtocol, assocstrExecutable = 0x1000, 2
	proto, _ := syscall.UTF16PtrFromString("minecraft")
	var size uint32 = 1024
	buf := make([]uint16, size)
	r, _, _ := syscall.NewLazyDLL("shlwapi.dll").NewProc("AssocQueryStringW").Call(assocfIsProtocol, assocstrExecutable,
		uintptr(unsafe.Pointer(proto)), 0, uintptr(unsafe.Pointer(&buf[0])), uintptr(unsafe.Pointer(&size)))
	return r == 0 && size > 1
}

// openMinecraftURI opens a "minecraft:" link (only those) with ShellExecute.
func openMinecraftURI(u string) error {
	if !strings.HasPrefix(u, "minecraft:") {
		return errors.New("not a Minecraft link")
	}
	verb, _ := syscall.UTF16PtrFromString("open")
	file, _ := syscall.UTF16PtrFromString(u)
	r, _, _ := syscall.NewLazyDLL("shell32.dll").NewProc("ShellExecuteW").Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(file)), 0, 0, 1)
	if r == 31 { // SE_ERR_NOASSOC
		return errBedrockMissing
	}
	if r <= 32 {
		return fmt.Errorf("Windows couldn't open Minecraft (%d)", r)
	}
	return nil
}

// dialDiscord opens the Discord app's local connection (a named pipe on this PC).
func dialDiscord(i int) (io.ReadWriteCloser, error) {
	return os.OpenFile(fmt.Sprintf(`\\.\pipe\discord-ipc-%d`, i), os.O_RDWR, 0)
}

// shellOpenFile opens one of the launcher's own Bedrock pack/world files with its app.
func shellOpenFile(path string) error {
	if !insideData(path) || !bedrockExts[strings.ToLower(filepath.Ext(path))] {
		return errors.New("not allowed")
	}
	verb, _ := syscall.UTF16PtrFromString("open")
	file, _ := syscall.UTF16PtrFromString(path)
	r, _, _ := syscall.NewLazyDLL("shell32.dll").NewProc("ShellExecuteW").Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(file)), 0, 0, 1)
	if r <= 32 {
		return fmt.Errorf("Windows couldn't open it with Minecraft (%d)", r)
	}
	return nil
}

// openSystemFolder opens Bedrock's worlds folder in Explorer (only that folder).
func openSystemFolder(path string) error {
	for _, d := range bedrockWorldDirs() {
		if d == path {
			return exec.Command("explorer.exe", path).Start()
		}
	}
	return errors.New("not allowed")
}
