package main

// Automatic Microsoft sign-in.
//
// Microsoft's sign-in finishes on a page whose address carries a one-time
// code, and that page immediately erases the code from the address bar. So
// the launcher opens the sign-in page in its own small browser window (a
// separate, launcher-only profile) and watches that window through the
// browser's local debugging connection (127.0.0.1 only) to catch the code.
// The window is closed as soon as the code arrives; the code is sent only to
// Microsoft.

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// ---------------------------------------------------------------------------
// Minimal WebSocket client (localhost only)
// ---------------------------------------------------------------------------

type wsConn struct {
	c  net.Conn
	r  *bufio.Reader
	mu sync.Mutex
}

func wsDial(raw string) (*wsConn, error) {
	u, err := url.Parse(raw)
	if err != nil {
		return nil, err
	}
	if h := u.Hostname(); h != "127.0.0.1" && h != "localhost" {
		return nil, errors.New("refusing non-local debug connection")
	}
	c, err := net.DialTimeout("tcp", u.Host, 5*time.Second)
	if err != nil {
		return nil, err
	}
	k := make([]byte, 16)
	rand.Read(k)
	req := "GET " + u.RequestURI() + " HTTP/1.1\r\nHost: " + u.Host +
		"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " +
		base64.StdEncoding.EncodeToString(k) + "\r\nSec-WebSocket-Version: 13\r\n\r\n"
	if _, err := c.Write([]byte(req)); err != nil {
		c.Close()
		return nil, err
	}
	br := bufio.NewReaderSize(c, 1<<16)
	resp, err := http.ReadResponse(br, nil)
	if err != nil {
		c.Close()
		return nil, err
	}
	if resp.StatusCode != 101 {
		c.Close()
		return nil, fmt.Errorf("debug connection refused (%d)", resp.StatusCode)
	}
	return &wsConn{c: c, r: br}, nil
}

func (w *wsConn) writeFrame(op byte, p []byte) error {
	hdr := []byte{0x80 | op}
	n := len(p)
	switch {
	case n < 126:
		hdr = append(hdr, 0x80|byte(n))
	case n < 65536:
		hdr = append(hdr, 0x80|126, byte(n>>8), byte(n))
	default:
		b := make([]byte, 8)
		binary.BigEndian.PutUint64(b, uint64(n))
		hdr = append(append(hdr, 0x80|127), b...)
	}
	mask := make([]byte, 4)
	rand.Read(mask)
	hdr = append(hdr, mask...)
	body := make([]byte, n)
	for i := range p {
		body[i] = p[i] ^ mask[i%4]
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	_, err := w.c.Write(append(hdr, body...))
	return err
}

func (w *wsConn) send(v any) error {
	b, _ := json.Marshal(v)
	return w.writeFrame(1, b)
}

func (w *wsConn) read() ([]byte, error) {
	var msg []byte
	for {
		h := make([]byte, 2)
		if _, err := io.ReadFull(w.r, h); err != nil {
			return nil, err
		}
		fin, op, masked := h[0]&0x80 != 0, h[0]&0x0f, h[1]&0x80 != 0
		n := uint64(h[1] & 0x7f)
		if n == 126 {
			b := make([]byte, 2)
			if _, err := io.ReadFull(w.r, b); err != nil {
				return nil, err
			}
			n = uint64(binary.BigEndian.Uint16(b))
		} else if n == 127 {
			b := make([]byte, 8)
			if _, err := io.ReadFull(w.r, b); err != nil {
				return nil, err
			}
			n = binary.BigEndian.Uint64(b)
		}
		if n > 64<<20 {
			return nil, errors.New("frame too large")
		}
		var mask []byte
		if masked {
			mask = make([]byte, 4)
			if _, err := io.ReadFull(w.r, mask); err != nil {
				return nil, err
			}
		}
		p := make([]byte, n)
		if _, err := io.ReadFull(w.r, p); err != nil {
			return nil, err
		}
		if masked {
			for i := range p {
				p[i] ^= mask[i%4]
			}
		}
		switch op {
		case 8:
			return nil, io.EOF
		case 9:
			w.writeFrame(10, p)
			continue
		case 10:
			continue
		}
		msg = append(msg, p...)
		if fin {
			return msg, nil
		}
	}
}

// ---------------------------------------------------------------------------
// Capture the redirect
// ---------------------------------------------------------------------------

var (
	loginWinMu  sync.Mutex
	loginWinCmd *exec.Cmd
)

// captureRedirect opens startURL in a launcher-only browser window and returns
// the first address that starts with prefix and contains "code=".
func captureRedirect(ctx context.Context, startURL, prefix string, extra []string) (string, error) {
	browser := windowBrowser()
	if browser == "" {
		return "", errors.New("Google Chrome or Microsoft Edge is needed for this sign-in window")
	}
	loginWinMu.Lock()
	if loginWinCmd != nil && loginWinCmd.Process != nil {
		loginWinCmd.Process.Kill()
		time.Sleep(700 * time.Millisecond)
	}
	// A brand-new, empty browser profile for every sign-in, deleted afterwards: no cookies or
	// saved logins stay behind, and the player's normal browser is never touched.
	base := filepath.Join(dataDir, "login-window")
	os.RemoveAll(base)
	dir := filepath.Join(base, randHex(8))
	os.MkdirAll(dir, 0o755)
	portFile := filepath.Join(dir, "DevToolsActivePort")
	defer func() {
		go func() {
			for i := 0; i < 10; i++ { // the browser may hold files for a moment after closing
				time.Sleep(time.Second)
				if os.RemoveAll(dir) == nil {
					return
				}
			}
		}()
	}()
	os.Remove(portFile)
	args := append([]string{
		"--user-data-dir=" + dir, "--remote-debugging-port=0", "--remote-debugging-address=127.0.0.1",
		"--no-first-run", "--no-default-browser-check", "--window-size=520,760",
		"--disable-features=Translate,msEdgeSidebarV2,msHubApps",
		"--app=" + startURL,
	}, extra...)
	cmd := exec.Command(browser, args...)
	if err := cmd.Start(); err != nil {
		loginWinMu.Unlock()
		return "", fmt.Errorf("could not open sign-in window: %v", err)
	}
	loginWinCmd = cmd
	loginWinMu.Unlock()

	exited := make(chan struct{})
	go func() { cmd.Wait(); close(exited) }()
	closeWin := func(port, path string) {
		if port != "" && path != "" {
			if c, err := wsDial("ws://127.0.0.1:" + port + path); err == nil {
				c.send(map[string]any{"id": 1, "method": "Browser.close"})
				c.c.Close()
			}
		}
		select {
		case <-exited:
		case <-time.After(2 * time.Second):
			cmd.Process.Kill()
		}
	}

	var port, browserPath string
	for i := 0; port == ""; i++ {
		if b, err := os.ReadFile(portFile); err == nil {
			lines := strings.Split(strings.ReplaceAll(string(b), "\r", ""), "\n")
			if len(lines) >= 2 && strings.TrimSpace(lines[0]) != "" {
				port, browserPath = strings.TrimSpace(lines[0]), strings.TrimSpace(lines[1])
				break
			}
		}
		if i > 200 {
			closeWin("", "")
			return "", errors.New("the sign-in window didn't start")
		}
		select {
		case <-exited:
			return "", errors.New("the sign-in window was closed")
		case <-ctx.Done():
			closeWin("", "")
			return "", errors.New("sign-in cancelled")
		case <-time.After(100 * time.Millisecond):
		}
	}

	found := make(chan string, 1)
	check := func(u string) {
		if strings.HasPrefix(u, prefix) && strings.Contains(u, "code=") {
			select {
			case found <- u:
			default:
			}
		}
	}
	local := &http.Client{Timeout: 2 * time.Second}
	attached := map[string]bool{}
	deadline := time.After(15 * time.Minute)
	for {
		if resp, err := local.Get("http://127.0.0.1:" + port + "/json/list"); err == nil {
			var targets []struct {
				ID   string `json:"id"`
				Type string `json:"type"`
				URL  string `json:"url"`
				WS   string `json:"webSocketDebuggerUrl"`
			}
			json.NewDecoder(resp.Body).Decode(&targets)
			resp.Body.Close()
			for _, t := range targets {
				if t.Type != "page" {
					continue
				}
				check(t.URL)
				if !attached[t.ID] && t.WS != "" {
					attached[t.ID] = true
					go watchTarget(t.WS, check)
				}
			}
		}
		select {
		case u := <-found:
			closeWin(port, browserPath)
			return u, nil
		case <-exited:
			select {
			case u := <-found:
				return u, nil
			default:
			}
			return "", errors.New("the sign-in window was closed before finishing")
		case <-ctx.Done():
			closeWin(port, browserPath)
			return "", errors.New("sign-in cancelled")
		case <-deadline:
			closeWin(port, browserPath)
			return "", errors.New("sign-in timed out")
		case <-time.After(300 * time.Millisecond):
		}
	}
}

func watchTarget(ws string, check func(string)) {
	c, err := wsDial(ws)
	if err != nil {
		return
	}
	defer c.c.Close()
	c.send(map[string]any{"id": 1, "method": "Network.enable"})
	c.send(map[string]any{"id": 2, "method": "Page.enable"})
	for {
		msg, err := c.read()
		if err != nil {
			return
		}
		var ev struct {
			Params struct {
				Request struct {
					URL string `json:"url"`
				} `json:"request"`
				Frame struct {
					URL string `json:"url"`
				} `json:"frame"`
				URL string `json:"url"`
			} `json:"params"`
		}
		if json.Unmarshal(msg, &ev) == nil {
			check(ev.Params.Request.URL)
			check(ev.Params.Frame.URL)
			check(ev.Params.URL)
		}
	}
}

// ---------------------------------------------------------------------------
// Login state for the UI
// ---------------------------------------------------------------------------

var liveLogin = struct {
	mu     sync.Mutex
	Status string
	Error  string
	Name   string
	gen    int
	cancel context.CancelFunc
}{}

func startLiveLogin() {
	liveLogin.mu.Lock()
	if liveLogin.Status == "pending" && liveLogin.cancel != nil {
		liveLogin.cancel()
	}
	ctx, cancel := context.WithCancel(context.Background())
	liveLogin.Status, liveLogin.Error, liveLogin.Name, liveLogin.cancel = "pending", "", "", cancel
	liveLogin.gen++
	gen := liveLogin.gen
	liveLogin.mu.Unlock()
	go func() {
		defer cancel()
		u, err := captureRedirect(ctx, liveAuthorizeURL(), liveRedirect, nil)
		var acc *Account
		if err == nil {
			acc, err = finishLive(u)
		}
		liveLogin.mu.Lock()
		defer liveLogin.mu.Unlock()
		if gen != liveLogin.gen || liveLogin.Status == "cancelled" {
			return // superseded or cancelled
		}
		if err != nil {
			liveLogin.Status, liveLogin.Error = "error", err.Error()
			logf("login: %v", err)
			return
		}
		liveLogin.Status, liveLogin.Name = "done", acc.Name
	}()
}

func cancelLiveLogin() {
	liveLogin.mu.Lock()
	defer liveLogin.mu.Unlock()
	if liveLogin.cancel != nil {
		liveLogin.cancel()
	}
	liveLogin.Status = "cancelled"
}

func liveLoginStatus() map[string]string {
	liveLogin.mu.Lock()
	defer liveLogin.mu.Unlock()
	return map[string]string{"status": liveLogin.Status, "error": liveLogin.Error, "name": liveLogin.Name}
}
