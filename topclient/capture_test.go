package main

import (
	"context"
	"net"
	"net/http"
	"testing"
	"time"
)

func TestCaptureRedirect(t *testing.T) {
	dataDir = t.TempDir()
	ln, _ := net.Listen("tcp", "127.0.0.1:0")
	base := "http://" + ln.Addr().String()
	mux := http.NewServeMux()
	mux.HandleFunc("/login", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`<html><body>signing in<script>setTimeout(()=>location.href='/post',500)</script></body></html>`))
	})
	mux.HandleFunc("/post", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/oauth20_desktop.srf?code=M.TEST_CODE_123&lc=1033", 302)
	})
	mux.HandleFunc("/oauth20_desktop.srf", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`<html><script>history.replaceState(null,'','/oauth20_desktop.srf?removed=true')</script>done</html>`))
	})
	go http.Serve(ln, mux)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	u, err := captureRedirect(ctx, base+"/login", base+"/oauth20_desktop.srf", []string{"--headless=new", "--no-sandbox", base + "/login"})
	if err != nil {
		t.Fatal(err)
	}
	t.Log("captured:", u)
	if u != base+"/oauth20_desktop.srf?code=M.TEST_CODE_123&lc=1033" {
		t.Fatal("wrong url")
	}
}
