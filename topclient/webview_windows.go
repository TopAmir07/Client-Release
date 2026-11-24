//go:build windows

package main

// A real app window for the launcher, made with Microsoft's WebView2 (the Edge engine
// that ships with Windows 10 and 11). It is a normal Windows window with our own icon
// and title bar, not a browser tab. If WebView2 can't be found, the launcher falls back
// to the old Edge app window.

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
	"unsafe"
)

var (
	wvUser32   = syscall.NewLazyDLL("user32.dll")
	wvKernel32 = syscall.NewLazyDLL("kernel32.dll")
	wvOle32    = syscall.NewLazyDLL("ole32.dll")
	wvDwm      = syscall.NewLazyDLL("dwmapi.dll")
	wvGdi32    = syscall.NewLazyDLL("gdi32.dll")

	pRegisterClassExW    = wvUser32.NewProc("RegisterClassExW")
	pCreateWindowExW     = wvUser32.NewProc("CreateWindowExW")
	pDefWindowProcW      = wvUser32.NewProc("DefWindowProcW")
	pGetMessageW         = wvUser32.NewProc("GetMessageW")
	pTranslateMessage    = wvUser32.NewProc("TranslateMessage")
	pDispatchMessageW    = wvUser32.NewProc("DispatchMessageW")
	pPostQuitMessage     = wvUser32.NewProc("PostQuitMessage")
	pShowWindow          = wvUser32.NewProc("ShowWindow")
	pUpdateWindow        = wvUser32.NewProc("UpdateWindow")
	pGetClientRect       = wvUser32.NewProc("GetClientRect")
	pLoadCursorW         = wvUser32.NewProc("LoadCursorW")
	pLoadImageW          = wvUser32.NewProc("LoadImageW")
	pDestroyWindow       = wvUser32.NewProc("DestroyWindow")
	pPostMessageW        = wvUser32.NewProc("PostMessageW")
	pSetForegroundWindow = wvUser32.NewProc("SetForegroundWindow")
	pGetSystemMetrics    = wvUser32.NewProc("GetSystemMetrics")
	pSetWindowPos        = wvUser32.NewProc("SetWindowPos")
	pGetDpiForSystem     = wvUser32.NewProc("GetDpiForSystem")
	pGetDpiForWindow     = wvUser32.NewProc("GetDpiForWindow")
	pSetDpiAwarenessCtx  = wvUser32.NewProc("SetProcessDpiAwarenessContext")
	pGetModuleHandleW    = wvKernel32.NewProc("GetModuleHandleW")
	pLoadLibraryExW      = wvKernel32.NewProc("LoadLibraryExW")
	pGetProcAddress      = wvKernel32.NewProc("GetProcAddress")
	pCoInitializeEx      = wvOle32.NewProc("CoInitializeEx")
	pDwmSetWindowAttr    = wvDwm.NewProc("DwmSetWindowAttribute")
	pCreateSolidBrush    = wvGdi32.NewProc("CreateSolidBrush")
	pReleaseCapture      = wvUser32.NewProc("ReleaseCapture")
	pSendMessageW        = wvUser32.NewProc("SendMessageW")
	pIsZoomed            = wvUser32.NewProc("IsZoomed")
	pGetWindowPlacement  = wvUser32.NewProc("GetWindowPlacement")
	pGetCursorPos        = wvUser32.NewProc("GetCursorPos")
)

const (
	wmDestroy       = 0x0002
	wmSize          = 0x0005
	wmSetFocus      = 0x0007
	wmGetMinMaxInfo = 0x0024
	wmDpiChanged    = 0x02E0
	wmAppShow       = 0x8001
	wmAppFail       = 0x8002
	wmAppDrag       = 0x8003 // wParam = hit-test code (HTCAPTION, HTLEFT, ...): start a move/resize
	wmAppCmd        = 0x8004 // wParam = SC_* system command (minimise, maximise, restore, close)
)

type wndClassEx struct {
	cbSize        uint32
	style         uint32
	lpfnWndProc   uintptr
	cbClsExtra    int32
	cbWndExtra    int32
	hInstance     uintptr
	hIcon         uintptr
	hCursor       uintptr
	hbrBackground uintptr
	lpszMenuName  *uint16
	lpszClassName *uint16
	hIconSm       uintptr
}

type winMsg struct {
	hwnd    uintptr
	message uint32
	wParam  uintptr
	lParam  uintptr
	time    uint32
	ptX     int32
	ptY     int32
	private uint32
}

type winRect struct{ Left, Top, Right, Bottom int32 }

// A tiny COM object: just a vtable pointer. WebView2 calls Invoke when it's ready.
type comHandler struct{ vtbl *comVtbl }
type comVtbl struct{ QueryInterface, AddRef, Release, Invoke uintptr }

// comCall calls method number idx on a COM object we were given by WebView2.
func comCall(obj uintptr, idx int, args ...uintptr) uintptr {
	vtbl := *(*uintptr)(unsafe.Pointer(obj))
	fn := *(*uintptr)(unsafe.Pointer(vtbl + uintptr(idx)*unsafe.Sizeof(uintptr(0))))
	r, _, _ := syscall.SyscallN(fn, append([]uintptr{obj}, args...)...)
	return r
}

// ICoreWebView2Controller / ICoreWebView2 / ICoreWebView2Settings vtable slots.
const (
	ctrlPutIsVisible   = 4
	ctrlPutBounds      = 6
	ctrlMoveFocus      = 12
	ctrlNotifyMoved    = 23
	ctrlClose          = 24
	ctrlGetCoreWebView = 25
	envCreateCtrl      = 3
	wvGetSettings      = 3
	wvNavigate         = 5
	setPutStatusBar    = 10
	setPutDevTools     = 12
	setPutZoomControl  = 18
)

var (
	wvOnce      sync.Once
	wvInitErr   error
	wndProcCB   uintptr
	envHandler  *comHandler
	ctrlHandler *comHandler
	handlerVt   [2]*comVtbl

	wvMu         sync.Mutex
	wvHwnd       uintptr
	wvController uintptr
	wvURL        string
	wvReady      atomic.Bool
	wvFailed     atomic.Bool
	wvOpen       atomic.Bool
	wvGen        atomic.Int64
)

func mkGUID(d1 uint32, d2, d3 uint16, d4 [8]byte) (g [16]byte) {
	g[0], g[1], g[2], g[3] = byte(d1), byte(d1>>8), byte(d1>>16), byte(d1>>24)
	g[4], g[5], g[6], g[7] = byte(d2), byte(d2>>8), byte(d3), byte(d3>>8)
	copy(g[8:], d4[:])
	return
}

var (
	iidIUnknown      = mkGUID(0x00000000, 0x0000, 0x0000, [8]byte{0xC0, 0, 0, 0, 0, 0, 0, 0x46})
	iidEnvCompleted  = mkGUID(0x4e8a3389, 0xc9d8, 0x4bd2, [8]byte{0xb6, 0xb5, 0x12, 0x4f, 0xee, 0x6c, 0xc1, 0x4d})
	iidCtrlCompleted = mkGUID(0x6c4819f3, 0xc9b7, 0x4260, [8]byte{0x81, 0x27, 0xc9, 0xf5, 0xbd, 0xe7, 0xf6, 0x8c})
)

func utf16(s string) *uint16 { p, _ := syscall.UTF16PtrFromString(s); return p }

// findWebView2 finds EmbeddedBrowserWebView.dll from the WebView2 Runtime that Windows
// keeps up to date (the newest version folder wins).
func findWebView2() string {
	arch := "x64"
	if runtime.GOARCH == "arm64" {
		arch = "arm64"
	}
	var found []string
	for _, env := range []string{"ProgramFiles(x86)", "ProgramFiles", "LocalAppData"} {
		base := os.Getenv(env)
		if base == "" {
			continue
		}
		for _, pat := range []string{
			filepath.Join(base, "Microsoft", "EdgeWebView", "Application", "*", "EBWebView", arch, "EmbeddedBrowserWebView.dll"),
			filepath.Join(base, "Microsoft", "EdgeWebView", "Application", "*", "EmbeddedBrowserWebView.dll"),
		} {
			m, _ := filepath.Glob(pat)
			found = append(found, m...)
		}
	}
	if len(found) == 0 {
		return ""
	}
	ver := func(p string) []int {
		rel := p[strings.Index(p, "Application")+len("Application")+1:]
		v, _, _ := strings.Cut(rel, string(filepath.Separator))
		var out []int
		for _, part := range strings.Split(v, ".") {
			n, _ := strconv.Atoi(part)
			out = append(out, n)
		}
		return out
	}
	sort.Slice(found, func(i, j int) bool {
		a, b := ver(found[i]), ver(found[j])
		for k := 0; k < len(a) && k < len(b); k++ {
			if a[k] != b[k] {
				return a[k] > b[k]
			}
		}
		return len(a) > len(b)
	})
	return found[0]
}

func wvInit() error {
	wvOnce.Do(func() {
		if pSetDpiAwarenessCtx.Find() == nil {
			pSetDpiAwarenessCtx.Call(^uintptr(3)) // PER_MONITOR_AWARE_V2 (-4)
		}
		// QueryInterface: only IUnknown and the two handler interfaces are answered.
		qi := syscall.NewCallback(func(this, riid, ppv uintptr) uintptr {
			if ppv == 0 {
				return 0x80004003 // E_POINTER
			}
			want := *(*[16]byte)(unsafe.Pointer(riid))
			if want == iidIUnknown || want == iidEnvCompleted || want == iidCtrlCompleted {
				*(*uintptr)(unsafe.Pointer(ppv)) = this
				return 0
			}
			*(*uintptr)(unsafe.Pointer(ppv)) = 0
			return 0x80004002 // E_NOINTERFACE
		})
		ref := syscall.NewCallback(func(this uintptr) uintptr { return 1 })
		envInvoke := syscall.NewCallback(func(this, hr, env uintptr) uintptr {
			if hr != 0 || env == 0 {
				wvFail()
				return 0
			}
			comCall(env, 1) // AddRef: we keep the environment
			if r := comCall(env, envCreateCtrl, wvHwnd, uintptr(unsafe.Pointer(ctrlHandler))); r != 0 {
				wvFail()
			}
			return 0
		})
		ctrlInvoke := syscall.NewCallback(func(this, hr, ctrl uintptr) uintptr {
			if hr != 0 || ctrl == 0 {
				wvFail()
				return 0
			}
			comCall(ctrl, 1)
			var wv uintptr
			comCall(ctrl, ctrlGetCoreWebView, uintptr(unsafe.Pointer(&wv)))
			if wv == 0 {
				wvFail()
				return 0
			}
			var st uintptr
			comCall(wv, wvGetSettings, uintptr(unsafe.Pointer(&st)))
			if st != 0 {
				comCall(st, setPutStatusBar, 0)
				comCall(st, setPutZoomControl, 0)
				comCall(st, setPutDevTools, 0)
			}
			wvMu.Lock()
			wvController = ctrl
			wvMu.Unlock()
			wvResize()
			comCall(ctrl, ctrlPutIsVisible, 1)
			comCall(wv, wvNavigate, uintptr(unsafe.Pointer(utf16(wvURL))))
			comCall(ctrl, ctrlMoveFocus, 0)
			wvReady.Store(true)
			return 0
		})
		handlerVt[0] = &comVtbl{qi, ref, ref, envInvoke}
		handlerVt[1] = &comVtbl{qi, ref, ref, ctrlInvoke}
		envHandler = &comHandler{handlerVt[0]}
		ctrlHandler = &comHandler{handlerVt[1]}
		wndProcCB = syscall.NewCallback(wvWndProc)

		hinst, _, _ := pGetModuleHandleW.Call(0)
		icon, _, _ := pLoadImageW.Call(hinst, 1, 1, 0, 0, 0x8040) // group icon 1, default size, shared
		trayIcon = icon
		cursor, _, _ := pLoadCursorW.Call(0, 32512)
		brush, _, _ := pCreateSolidBrush.Call(0x000E0C0B) // TopClient graphite (BGR)
		wc := wndClassEx{
			lpfnWndProc: wndProcCB, hInstance: hinst, hIcon: icon, hIconSm: icon,
			hCursor: cursor, hbrBackground: brush, lpszClassName: utf16("TopClientWindow"),
		}
		wc.cbSize = uint32(unsafe.Sizeof(wc))
		if r, _, err := pRegisterClassExW.Call(uintptr(unsafe.Pointer(&wc))); r == 0 {
			wvInitErr = err
		}
	})
	return wvInitErr
}

func wvFail() {
	wvFailed.Store(true)
	wvMu.Lock()
	h := wvHwnd
	wvMu.Unlock()
	if h != 0 {
		pPostMessageW.Call(h, wmAppFail, 0, 0)
	}
}

func wvResize() {
	wvMu.Lock()
	h, c := wvHwnd, wvController
	wvMu.Unlock()
	if h == 0 || c == 0 {
		return
	}
	var r winRect
	pGetClientRect.Call(h, uintptr(unsafe.Pointer(&r)))
	comCall(c, ctrlPutBounds, uintptr(unsafe.Pointer(&r)))
}

func dpiScale(hwnd uintptr) int {
	if hwnd != 0 && pGetDpiForWindow.Find() == nil {
		if d, _, _ := pGetDpiForWindow.Call(hwnd); d > 0 {
			return int(d)
		}
	}
	if pGetDpiForSystem.Find() == nil {
		if d, _, _ := pGetDpiForSystem.Call(); d > 0 {
			return int(d)
		}
	}
	return 96
}

func wvWndProc(hwnd, msg, wp, lp uintptr) uintptr {
	switch msg {
	case 0x0083: // WM_NCCALCSIZE: the whole window is ours (the launcher draws its own title bar)
		if wp == 1 {
			if z, _, _ := pIsZoomed.Call(hwnd); z != 0 {
				// maximised windows hang over the screen edge by the frame size: pull them back in
				r := (*winRect)(unsafe.Pointer(lp))
				d := dpiScale(hwnd)
				f := int32(8 * d / 96)
				r.Left += f
				r.Top += f
				r.Right -= f
				r.Bottom -= f
			}
			return 0
		}
	case wmAppDrag:
		pReleaseCapture.Call()
		pSendMessageW.Call(hwnd, 0x00A1, wp, 0) // WM_NCLBUTTONDOWN: Windows moves/resizes the window
		return 0
	case wmAppCmd:
		if wp == 0xF030 { // maximise toggles
			if z, _, _ := pIsZoomed.Call(hwnd); z != 0 {
				wp = 0xF120
			}
		}
		pPostMessageW.Call(hwnd, 0x0112, wp, 0) // WM_SYSCOMMAND
		return 0
	case 0x0232: // WM_EXITSIZEMOVE: you finished resizing or moving the window
		saveWindowSize(hwnd)
	case wmSize:
		if wp == 2 || (wp == 0 && settingBool("winMax", false)) { // maximised, or restored from maximised
			defer saveWindowSize(hwnd)
		}
		// Minimised: tell WebView2 it's hidden, so it stops drawing and slows its timers right
		// down (frees the CPU/GPU for Minecraft). Shown again as soon as the window comes back.
		wvMu.Lock()
		c := wvController
		wvMu.Unlock()
		if c != 0 {
			if wp == 1 { // SIZE_MINIMIZED
				// Only rest WebView while Minecraft is running (and that setting is on);
				// otherwise it keeps animating so it never comes back frozen.
				if len(runningList()) > 0 && settingBool("pauseWhilePlaying", true) {
					comCall(c, ctrlPutIsVisible, 0)
				}
				trayMinimized(hwnd)
				return 0
			}
			comCall(c, ctrlPutIsVisible, 1)
		}
		wvResize()
		return 0
	case wmSetFocus:
		wvMu.Lock()
		c := wvController
		wvMu.Unlock()
		if c != 0 {
			comCall(c, ctrlMoveFocus, 0)
		}
	case wmGetMinMaxInfo:
		d := dpiScale(hwnd)
		mm := (*[10]int32)(unsafe.Pointer(lp))
		mm[6], mm[7] = int32(980*d/96), int32(620*d/96) // ptMinTrackSize
		return 0
	case wmDpiChanged:
		r := (*winRect)(unsafe.Pointer(lp))
		pSetWindowPos.Call(hwnd, 0, uintptr(r.Left), uintptr(r.Top), uintptr(r.Right-r.Left), uintptr(r.Bottom-r.Top), 0x0014)
		return 0
	case 0x0003: // WM_MOVE
		wvMu.Lock()
		c := wvController
		wvMu.Unlock()
		if c != 0 {
			comCall(c, ctrlNotifyMoved)
		}
	case wmAppShow:
		pShowWindow.Call(hwnd, 9) // SW_RESTORE
		pSetForegroundWindow.Call(hwnd)
		return 0
	case wmAppFail:
		pDestroyWindow.Call(hwnd)
		return 0
	case wmAppTray:
		trayMessage(hwnd, lp)
		return 0
	case wmDestroy:
		saveWindowSize(hwnd)
		trayRemove(hwnd)
		wvMu.Lock()
		c := wvController
		wvController, wvHwnd = 0, 0
		wvMu.Unlock()
		if c != 0 {
			comCall(c, ctrlClose)
			comCall(c, 2) // Release
		}
		pPostQuitMessage.Call(0)
		return 0
	}
	r, _, _ := pDefWindowProcW.Call(hwnd, msg, wp, lp)
	return r
}

// runNativeWindow opens the launcher in its own window and returns when it is closed.
// It returns an error straight away (without showing anything) if WebView2 is missing.
func runNativeWindow(url string) error {
	dll := findWebView2()
	if dll == "" {
		return errors.New("WebView2 runtime not found")
	}
	if !wvOpen.CompareAndSwap(false, true) {
		showNativeWindow()
		return nil
	}
	defer wvOpen.Store(false)
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err := wvInit(); err != nil {
		return err
	}
	pCoInitializeEx.Call(0, 2) // apartment threaded, as WebView2 wants
	wvURL = url
	wvReady.Store(false)
	wvFailed.Store(false)

	mod, _, err := pLoadLibraryExW.Call(uintptr(unsafe.Pointer(utf16(dll))), 0, 0x00000008) // LOAD_WITH_ALTERED_SEARCH_PATH
	if mod == 0 {
		return err
	}
	create, _, err := pGetProcAddress.Call(mod, uintptr(unsafe.Pointer(syscall.StringBytePtr("CreateWebViewEnvironmentWithOptionsInternal"))))
	if create == 0 {
		return err
	}

	d := dpiScale(0)
	w, h := 1511*d/96, 928*d/96 // default size (default pick); smaller screens get it shrunk to fit below
	// The size you left it at last time (saved in "1x" pixels, so it fits any display scale).
	if sw0, sh0 := settingInt("winW", 0), settingInt("winH", 0); sw0 >= 980 && sh0 >= 620 {
		w, h = sw0*d/96, sh0*d/96
	}
	sw, _, _ := pGetSystemMetrics.Call(0)
	sh, _, _ := pGetSystemMetrics.Call(1)
	if int(sw) > 0 && w > int(sw)-40 {
		w = int(sw) - 40
	}
	if int(sh) > 0 && h > int(sh)-80 {
		h = int(sh) - 80
	}
	x, y := (int(sw)-w)/2, (int(sh)-h)/2-10
	hinst, _, _ := pGetModuleHandleW.Call(0)
	hwnd, _, err := pCreateWindowExW.Call(0, uintptr(unsafe.Pointer(utf16("TopClientWindow"))), uintptr(unsafe.Pointer(utf16("TopClient"))),
		0x00CF0000, uintptr(x), uintptr(y), uintptr(w), uintptr(h), 0, 0, hinst, 0) // WS_OVERLAPPEDWINDOW
	if hwnd == 0 {
		return err
	}
	wvMu.Lock()
	wvHwnd = hwnd
	wvMu.Unlock()
	// Dark title bar in the TopClient colours (Windows 11; ignored elsewhere).
	if pDwmSetWindowAttr.Find() == nil {
		on := int32(1)
		pDwmSetWindowAttr.Call(hwnd, 20, uintptr(unsafe.Pointer(&on)), 4)
		caption := uint32(0x000E0C0B)
		pDwmSetWindowAttr.Call(hwnd, 35, uintptr(unsafe.Pointer(&caption)), 4)
	}
	pSetWindowPos.Call(hwnd, 0, 0, 0, 0, 0, 0x0027) // SWP_FRAMECHANGED|NOMOVE|NOSIZE|NOZORDER: apply our frame
	if settingBool("winMax", false) {
		pShowWindow.Call(hwnd, 3) // SW_SHOWMAXIMIZED: it was maximised last time
	} else {
		pShowWindow.Call(hwnd, 5)
	}
	pUpdateWindow.Call(hwnd)
	pSetForegroundWindow.Call(hwnd)
	trayAdd(hwnd, trayIcon)

	userData := filepath.Join(dataDir, "webview")
	os.MkdirAll(userData, 0o755)
	hr, _, _ := syscall.SyscallN(create, 1, 0, uintptr(unsafe.Pointer(utf16(userData))), 0, uintptr(unsafe.Pointer(envHandler)))
	if hr != 0 {
		pDestroyWindow.Call(hwnd)
		drainMessages()
		return errors.New("WebView2 could not start")
	}
	// If WebView2 never answers, give up and use the fallback window.
	gen := wvGen.Add(1)
	go func() {
		time.Sleep(20 * time.Second)
		if wvGen.Load() == gen && !wvReady.Load() {
			wvFail()
		}
	}()

	drainMessages()
	if wvFailed.Load() && !wvReady.Load() {
		return errors.New("WebView2 did not start")
	}
	return nil
}

func drainMessages() {
	var m winMsg
	for {
		r, _, _ := pGetMessageW.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(r) <= 0 {
			return
		}
		pTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		pDispatchMessageW.Call(uintptr(unsafe.Pointer(&m)))
	}
}

// showNativeWindow brings the window to the front (used when the exe is started again).
func showNativeWindow() bool {
	wvMu.Lock()
	h := wvHwnd
	wvMu.Unlock()
	if h == 0 {
		return false
	}
	pPostMessageW.Call(h, wmAppShow, 0, 0)
	return true
}

// showUI opens (or brings back) the launcher window.
func showUI(url string) {
	if showNativeWindow() {
		return
	}
	go func() {
		if err := runNativeWindow(url); err != nil {
			logf("native window: %v (using Edge window)", err)
			openWindow(url)
		}
	}()
}

// windowAction runs a title-bar action from the page: "drag", "resize-<edge>", "min", "max", "close".
func windowAction(a string) bool {
	wvMu.Lock()
	h := wvHwnd
	wvMu.Unlock()
	if h == 0 {
		return false
	}
	hits := map[string]uintptr{"drag": 2, "resize-l": 10, "resize-r": 11, "resize-t": 12, "resize-tl": 13, "resize-tr": 14, "resize-b": 15, "resize-bl": 16, "resize-br": 17}
	if hit, ok := hits[a]; ok {
		pPostMessageW.Call(h, wmAppDrag, hit, 0)
		return true
	}
	cmds := map[string]uintptr{"min": 0xF020, "max": 0xF030, "close": 0xF060}
	if c, ok := cmds[a]; ok {
		pPostMessageW.Call(h, wmAppCmd, c, 0)
		return true
	}
	return false
}

func windowMaximized() bool {
	wvMu.Lock()
	h := wvHwnd
	wvMu.Unlock()
	if h == 0 {
		return false
	}
	z, _, _ := pIsZoomed.Call(h)
	return z != 0
}

// saveWindowSize remembers the window's normal size (and whether it's maximised) for next time.
func saveWindowSize(hwnd uintptr) {
	var wp struct {
		length, flags, showCmd uint32
		minPos, maxPos         [2]int32
		normal                 winRect
	}
	wp.length = uint32(unsafe.Sizeof(wp))
	if r, _, _ := pGetWindowPlacement.Call(hwnd, uintptr(unsafe.Pointer(&wp))); r == 0 {
		return
	}
	d := dpiScale(hwnd)
	if d <= 0 {
		d = 96
	}
	w := int(wp.normal.Right-wp.normal.Left) * 96 / d
	h := int(wp.normal.Bottom-wp.normal.Top) * 96 / d
	isMax := wp.showCmd == 3
	if w < 980 || h < 620 {
		return
	}
	stateMu.Lock()
	same := settings["winW"] == float64(w) && settings["winH"] == float64(h) && settings["winMax"] == isMax
	settings["winW"], settings["winH"], settings["winMax"] = float64(w), float64(h), isMax
	stateMu.Unlock()
	if !same {
		saveSettings()
	}
}
