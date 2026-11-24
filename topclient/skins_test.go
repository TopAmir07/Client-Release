package main

import (
	"bytes"
	"image"
	"image/png"
	"testing"
)

func pngOf(w, h int) []byte {
	var b bytes.Buffer
	png.Encode(&b, image.NewNRGBA(image.Rect(0, 0, w, h)))
	return b.Bytes()
}

func TestSkinLibrary(t *testing.T) {
	old := dataDir
	dataDir = t.TempDir()
	defer func() { dataDir = old }()
	if err := checkSkinPNG(pngOf(64, 64)); err != nil {
		t.Fatal(err)
	}
	if err := checkSkinPNG(pngOf(64, 32)); err != nil {
		t.Fatal(err)
	}
	if checkSkinPNG(pngOf(128, 128)) == nil || checkSkinPNG([]byte("GIF89a....")) == nil {
		t.Fatal("bad skins should be refused")
	}
	e, err := addSkin(pngOf(64, 64), "Test", "SLIM", "file")
	if err != nil || e.Variant != "slim" || !validSkinID.MatchString(e.ID) {
		t.Fatalf("%v %+v", err, e)
	}
	addSkin(pngOf(64, 64), "Again", "classic", "file") // same picture -> one entry
	if lib := loadSkinLib(); len(lib) != 1 || lib[0].Name != "Again" {
		t.Fatalf("%+v", lib)
	}
	if validUsername.MatchString("../x") || !validUsername.MatchString("Notch_1") {
		t.Fatal("username check")
	}
}

