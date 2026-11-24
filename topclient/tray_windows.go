//go:build windows

package main

// Tray icon: TopClient next to the clock. Click it to open the launcher; right-click for
// quick actions (play your last profile, join your first saved server, and more).
// Settings > Game > "Tray icon" turns it off; "Minimise to tray" hides the window when minimised.

import (
	"syscall"
	"unsafe"
)

const (
	wmAppTray   = 0x8030
	trayID      = 1
	nimAdd      = 0
	nimModify   = 1
	nimDelete   = 2
	nifMessage  = 1
	nifIcon     = 2
	nifTip      = 4
	nifInfo     = 0x10
	niifUser    = 4
	mfString    = 0
	mfGrayed    = 1
	mfSeparator = 0x800
	tpmReturn   = 0x100
	tpmRight    = 2
	tpmNoNotify = 0x80
)

var (
	pShellNotify     = syscall.NewLazyDLL("shell32.dll").NewProc("Shell_NotifyIconW")
	pCreatePopupMenu = wvUser32.NewProc("CreatePopupMenu")
	pAppendMenuW     = wvUser32.NewProc("AppendMenuW")
	pTrackPopupMenu  = wvUser32.NewProc("TrackPopupMenu")
	pDestroyMenu     = wvUser32.NewProc("DestroyMenu")
	trayIcon         uintptr
	trayOn           bool
)

type notifyIconData struct {
	cbSize           uint32
	hWnd             uintptr
	uID              uint32
	uFlags           uint32
	uCallbackMessage uint32
	hIcon            uintptr
	szTip            [128]uint16
	dwState          uint32
	dwStateMask      uint32
	szInfo           [256]uint16
	uVersion         uint32
	szInfoTitle      [64]uint16
	dwInfoFlags      uint32
	guidItem         [16]byte
	hBalloonIcon     uintptr
}

func copyUTF16(dst []uint16, s string) {
	u, _ := syscall.UTF16FromString(s)
	if len(u) > len(dst) {
		u = u[:len(dst)]
		u[len(u)-1] = 0
	}
	copy(dst, u)
}

func trayData(hwnd uintptr) notifyIconData {
	var n notifyIconData
	n.cbSize = uint32(unsafe.Sizeof(n))
	n.hWnd, n.uID = hwnd, trayID
	return n
}

func trayAdd(hwnd, icon uintptr) {
	if !settingBool("trayIcon", true) || trayOn {
		return
	}
	trayIcon = icon
	n := trayData(hwnd)
	n.uFlags = nifMessage | nifIcon | nifTip
	n.uCallbackMessage = wmAppTray
	n.hIcon = icon
	copyUTF16(n.szTip[:], "TopClient")
	if r, _, _ := pShellNotify.Call(nimAdd, uintptr(unsafe.Pointer(&n))); r != 0 {
		trayOn = true
	}
}

func trayRemove(hwnd uintptr) {
	if !trayOn {
		return
	}
	n := trayData(hwnd)
	pShellNotify.Call(nimDelete, uintptr(unsafe.Pointer(&n)))
	trayOn = false
}

// trayNotify shows a small pop-up message from the tray icon.
func trayNotify(title, text string) {
	wvMu.Lock()
	h := wvHwnd
	wvMu.Unlock()
	if h == 0 || !trayOn {
		return
	}
	n := trayData(h)
	n.uFlags = nifInfo
	n.dwInfoFlags = niifUser
	n.hBalloonIcon = trayIcon
	copyUTF16(n.szInfoTitle[:], title)
	copyUTF16(n.szInfo[:], text)
	pShellNotify.Call(nimModify, uintptr(unsafe.Pointer(&n)))
}

func trayShowWindow(hwnd uintptr) {
	pShowWindow.Call(hwnd, 9) // SW_RESTORE (also shows a hidden window)
	pSetForegroundWindow.Call(hwnd)
}

// trayMessage handles clicks on the tray icon (called from the window procedure).
func trayMessage(hwnd, lp uintptr) {
	switch lp & 0xFFFF {
	case 0x0202: // left click
		trayShowWindow(hwnd)
	case 0x0205, 0x007B: // right click / context menu
		trayMenu(hwnd)
	}
}

func trayMenu(hwnd uintptr) {
	last := lastJavaProfile()
	servers := loadServers()

	m, _, _ := pCreatePopupMenu.Call()
	add := func(id uintptr, text string, on bool) {
		f := uintptr(mfString)
		if !on {
			f |= mfGrayed
		}
		pAppendMenuW.Call(m, f, id, uintptr(unsafe.Pointer(utf16(text))))
	}
	add(1, "Open TopClient", true)
	pAppendMenuW.Call(m, mfSeparator, 0, 0)
	if last != nil {
		add(2, "Play "+last.Name, true)
	} else {
		add(2, "Play", false)
	}
	if len(servers) > 0 {
		add(3, "Join "+servers[0].Name, last != nil)
	} else {
		add(3, "Join a server (add one in Servers)", false)
	}
	pAppendMenuW.Call(m, mfSeparator, 0, 0)
	add(9, "Quit", true)

	var pt struct{ X, Y int32 }
	pGetCursorPos.Call(uintptr(unsafe.Pointer(&pt)))
	pSetForegroundWindow.Call(hwnd) // so the menu closes when you click elsewhere
	cmd, _, _ := pTrackPopupMenu.Call(m, tpmReturn|tpmRight|tpmNoNotify, uintptr(pt.X), uintptr(pt.Y), 0, hwnd, 0)
	pDestroyMenu.Call(m)
	pPostMessageW.Call(hwnd, 0, 0, 0) // WM_NULL

	switch cmd {
	case 1:
		trayShowWindow(hwnd)
	case 2, 3:
		if last == nil {
			return
		}
		go func() {
			if cmd == 3 && len(servers) > 0 {
				setJoin(last.ID, servers[0].Address)
			}
			if err := launch(last, false, false); err != nil {
				takeJoin(last.ID)
				trayNotify("Couldn't start Minecraft", err.Error())
				return
			}
			if cmd == 3 {
				recordStat("joins", 1)
				trayNotify("Starting "+last.Name, "Joining "+servers[0].Name+"…")
			} else {
				trayNotify("Starting "+last.Name, "Minecraft is on its way.")
			}
		}()
	case 9:
		pPostMessageW.Call(hwnd, wmAppCmd, 0xF060, 0) // close
	}
}

// trayMinimized: with "Minimise to tray" on, a minimised launcher hides in the tray.
func trayMinimized(hwnd uintptr) {
	if trayOn && settingBool("minimizeToTray", false) {
		pShowWindow.Call(hwnd, 0) // SW_HIDE
	}
}

// trayApply switches the tray icon on/off when the setting changes.
func trayApply(on bool) {
	wvMu.Lock()
	h := wvHwnd
	wvMu.Unlock()
	if h == 0 {
		return
	}
	if on {
		trayAdd(h, trayIcon)
	} else {
		trayRemove(h)
	}
}
