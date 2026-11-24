//go:build windows

package main

import (
	"bytes"
	"crypto/sha256"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

// Installing as an app (only when the player clicks Install): the exe is copied to
// %LOCALAPPDATA%\Programs\TopClient, a Start menu shortcut is made (so Windows search
// finds "TopClient"), optionally a Desktop shortcut, and an entry in Windows'
// "Installed apps" list so it can be uninstalled like any other app.

const uninstallSubkey = `Software\Microsoft\Windows\CurrentVersion\Uninstall\TopClient` // under HKEY_CURRENT_USER

func installDir() string {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base, _ = os.UserConfigDir()
	}
	return filepath.Join(base, "Programs", "TopClient")
}

func installedExe() string { return filepath.Join(installDir(), "TopClient.exe") }

func startMenuLink() string {
	return filepath.Join(os.Getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs", "TopClient.lnk")
}

func desktopLink() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Desktop", "TopClient.lnk")
}

func fileHash(p string) []byte {
	f, err := os.Open(p)
	if err != nil {
		return nil
	}
	defer f.Close()
	h := sha256.New()
	io.Copy(h, f)
	return h.Sum(nil)
}

func exists(p string) bool { _, err := os.Stat(p); return err == nil }

func appStatus() map[string]any {
	self, _ := os.Executable()
	inst := installedExe()
	installed := exists(inst) && exists(startMenuLink())
	same := installed && strings.EqualFold(filepath.Clean(self), filepath.Clean(inst))
	outdated := installed && !same && !bytes.Equal(fileHash(self), fileHash(inst))
	return map[string]any{"supported": true, "installed": installed, "runningInstalled": same,
		"outdated": outdated, "desktop": exists(desktopLink()), "folder": installDir()}
}

func makeShortcut(link, target string) error {
	os.MkdirAll(filepath.Dir(link), 0o755)
	cmd := exec.Command("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
		`$s=(New-Object -ComObject WScript.Shell).CreateShortcut($env:AFK_LNK);$s.TargetPath=$env:AFK_TGT;$s.WorkingDirectory=$env:AFK_WD;$s.IconLocation=$env:AFK_TGT+',0';$s.Description='TopClient - Minecraft launcher';$s.Save()`)
	cmd.Env = append(os.Environ(), "AFK_LNK="+link, "AFK_TGT="+target, "AFK_WD="+filepath.Dir(target))
	hideWindow(cmd)
	return cmd.Run()
}

func installApp(desktop bool) error {
	self, err := os.Executable()
	if err != nil {
		return err
	}
	inst := installedExe()
	if !strings.EqualFold(filepath.Clean(self), filepath.Clean(inst)) {
		os.MkdirAll(installDir(), 0o755)
		in, err := os.Open(self)
		if err != nil {
			return err
		}
		tmp := inst + ".new"
		out, err := os.Create(tmp)
		if err != nil {
			in.Close()
			return err
		}
		_, err = io.Copy(out, in)
		in.Close()
		out.Close()
		if err != nil {
			os.Remove(tmp)
			return err
		}
		os.Remove(inst + ".old")
		if exists(inst) {
			// a running copy can be renamed but not overwritten
			if err := os.Rename(inst, inst+".old"); err != nil {
				os.Remove(tmp)
				return errors.New("close the installed TopClient first, then try again")
			}
		}
		if err := os.Rename(tmp, inst); err != nil {
			return err
		}
	}
	if err := makeShortcut(startMenuLink(), inst); err != nil {
		return errors.New("could not add TopClient to the Start menu")
	}
	if desktop {
		makeShortcut(desktopLink(), inst)
	} else {
		os.Remove(desktopLink())
	}
	k, err := regCreate(uninstallSubkey)
	if err != nil {
		return nil // the Start menu entry works anyway; only the "Installed apps" entry is missing
	}
	defer regClose(k)
	regSetString(k, "DisplayName", "TopClient")
	regSetString(k, "DisplayIcon", inst+",0")
	regSetString(k, "DisplayVersion", AppVersion)
	regSetString(k, "Publisher", "TopAmir_")
	regSetString(k, "InstallLocation", installDir())
	regSetString(k, "UninstallString", `"`+inst+`" --uninstall`)
	regSetDWORD(k, "NoModify", 1)
	regSetDWORD(k, "NoRepair", 1)
	return nil
}

// uninstallApp removes the shortcuts, the Installed apps entry and the installed exe.
// Profiles, worlds and settings in %APPDATA%\.topclient are kept.
func uninstallApp() {
	os.Remove(startMenuLink())
	os.Remove(desktopLink())
	regDelete(uninstallSubkey)
	dir := installDir()
	if strings.EqualFold(filepath.Base(dir), "TopClient") {
		// A running exe can't be deleted, but it can be moved: park it in the Windows temp
		// folder (cleaned up by Windows), then remove the install folder.
		if self, err := os.Executable(); err == nil && strings.HasPrefix(strings.ToLower(self), strings.ToLower(dir)) {
			os.Rename(self, filepath.Join(os.TempDir(), "TopClient-removed-"+strconv.FormatInt(time.Now().Unix(), 10)+".exe"))
		}
		os.RemoveAll(dir)
	}
	showInfo("TopClient was removed.\n\nYour profiles, worlds and screenshots are still in %APPDATA%\\.topclient — delete that folder too if you don't need them.")
}

func showInfo(msg string) {
	u32 := syscallUser32MessageBox()
	_ = u32(msg, 0x40)
}

// --- small registry helpers (HKEY_CURRENT_USER only), straight Windows API -------------
var (
	advapi          = syscall.NewLazyDLL("advapi32.dll")
	pRegCreateKeyEx = advapi.NewProc("RegCreateKeyExW")
	pRegSetValueEx  = advapi.NewProc("RegSetValueExW")
	pRegCloseKey    = advapi.NewProc("RegCloseKey")
	pRegDeleteTree  = advapi.NewProc("RegDeleteTreeW")
)

const hkeyCurrentUser = uintptr(0x80000001)

func regCreate(sub string) (uintptr, error) {
	var k uintptr
	p, _ := syscall.UTF16PtrFromString(sub)
	r, _, _ := pRegCreateKeyEx.Call(hkeyCurrentUser, uintptr(unsafe.Pointer(p)), 0, 0, 0, 0x20006 /*KEY_WRITE*/, 0, uintptr(unsafe.Pointer(&k)), 0)
	if r != 0 {
		return 0, syscall.Errno(r)
	}
	return k, nil
}

func regClose(k uintptr) { pRegCloseKey.Call(k) }

func regSetString(k uintptr, name, val string) {
	n, _ := syscall.UTF16PtrFromString(name)
	v, _ := syscall.UTF16FromString(val)
	pRegSetValueEx.Call(k, uintptr(unsafe.Pointer(n)), 0, 1 /*REG_SZ*/, uintptr(unsafe.Pointer(&v[0])), uintptr(len(v)*2))
}

func regSetDWORD(k uintptr, name string, val uint32) {
	n, _ := syscall.UTF16PtrFromString(name)
	pRegSetValueEx.Call(k, uintptr(unsafe.Pointer(n)), 0, 4 /*REG_DWORD*/, uintptr(unsafe.Pointer(&val)), 4)
}

func regDelete(sub string) {
	p, _ := syscall.UTF16PtrFromString(sub)
	pRegDeleteTree.Call(hkeyCurrentUser, uintptr(unsafe.Pointer(p)))
}
