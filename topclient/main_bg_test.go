package main

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSaveBackgroundStreams(t *testing.T) {
	old := dataDir
	dataDir = t.TempDir()
	defer func() { dataDir = old }()
	head := append([]byte{0, 0, 0, 24}, []byte("ftypisom\\x00\\x00\\x02\\x00iso2")...)
	big := bytes.NewReader(append(head, make([]byte, 5<<20)...))
	ct, err := saveBackground(big)
	if err != nil || ct != "video/mp4" {
		t.Fatalf("mp4: %v %q", err, ct)
	}
	if st, _ := os.Stat(bgPath()); st == nil || st.Size() != int64(len(head))+5<<20 {
		t.Fatalf("wrong size")
	}
	if _, err := saveBackground(strings.NewReader("hello not a video")); err == nil {
		t.Fatal("text accepted")
	}
	webm := append([]byte{0x1A, 0x45, 0xDF, 0xA3}, make([]byte, 100)...)
	if ct, err := saveBackground(bytes.NewReader(webm)); err != nil || ct != "video/webm" {
		t.Fatalf("webm: %v %q", err, ct)
	}
	left, _ := filepath.Glob(filepath.Join(filepath.Dir(bgPath()), "*.part"))
	if len(left) != 0 {
		t.Fatalf("temp files left: %v", left)
	}
}
