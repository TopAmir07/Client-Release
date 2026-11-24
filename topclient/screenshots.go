package main

import (
	"bytes"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/png"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

// Screenshots tab: every profile's screenshots folder, newest first. Only .png/.jpg
// files inside the launcher's own profile folders are ever read.

type shot struct {
	Profile     string `json:"profile"`
	ProfileName string `json:"profileName"`
	File        string `json:"file"`
	Time        int64  `json:"time"`
	Size        int64  `json:"size"`
}

func shotPath(p *Profile, file string) (string, error) {
	low := strings.ToLower(file)
	if file != filepath.Base(file) || !(strings.HasSuffix(low, ".png") || strings.HasSuffix(low, ".jpg") || strings.HasSuffix(low, ".jpeg")) {
		return "", fmt.Errorf("not a screenshot")
	}
	return safeJoin(profileDir(p), filepath.Join("screenshots", file))
}

func listShots() []shot {
	stateMu.Lock()
	ps := append([]*Profile(nil), profiles...)
	stateMu.Unlock()
	out := []shot{}
	for _, p := range ps {
		ents, _ := os.ReadDir(filepath.Join(profileDir(p), "screenshots"))
		for _, e := range ents {
			if e.IsDir() {
				continue
			}
			if _, err := shotPath(p, e.Name()); err != nil {
				continue
			}
			info, err := e.Info()
			if err != nil {
				continue
			}
			out = append(out, shot{p.ID, p.Name, e.Name(), info.ModTime().Unix(), info.Size()})
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Time > out[j].Time })
	return out
}

// thumbnail makes a small JPEG preview (cached) so the grid loads fast.
func thumbnail(path string, maxW int) ([]byte, error) {
	st, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	key := strconv.FormatUint(uint64(fnvHash(path+strconv.FormatInt(st.ModTime().UnixNano(), 10))), 16)
	cache := filepath.Join(dataDir, "cache", "thumbs", key+".jpg")
	if b, err := os.ReadFile(cache); err == nil {
		return b, nil
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	src, _, err := image.Decode(f)
	if err != nil {
		return nil, err
	}
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	if w > maxW {
		h = h * maxW / w
		w = maxW
	}
	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	// box filter: average the source pixels that fall into each output pixel
	for y := 0; y < h; y++ {
		sy0, sy1 := b.Min.Y+y*b.Dy()/h, b.Min.Y+(y+1)*b.Dy()/h
		for x := 0; x < w; x++ {
			sx0, sx1 := b.Min.X+x*b.Dx()/w, b.Min.X+(x+1)*b.Dx()/w
			var r, g, bl, n uint32
			for yy := sy0; yy < sy1; yy += 2 {
				for xx := sx0; xx < sx1; xx += 2 {
					cr, cg, cb, _ := src.At(xx, yy).RGBA()
					r, g, bl, n = r+cr, g+cg, bl+cb, n+1
				}
			}
			if n == 0 {
				cr, cg, cb, _ := src.At(sx0, sy0).RGBA()
				r, g, bl, n = cr, cg, cb, 1
			}
			i := dst.PixOffset(x, y)
			dst.Pix[i], dst.Pix[i+1], dst.Pix[i+2], dst.Pix[i+3] = uint8(r/n>>8), uint8(g/n>>8), uint8(bl/n>>8), 255
		}
	}
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, dst, &jpeg.Options{Quality: 85}); err != nil {
		return nil, err
	}
	os.MkdirAll(filepath.Dir(cache), 0o755)
	os.WriteFile(cache, buf.Bytes(), 0o644)
	return buf.Bytes(), nil
}

func fnvHash(s string) uint32 {
	h := uint32(2166136261)
	for i := 0; i < len(s); i++ {
		h ^= uint32(s[i])
		h *= 16777619
	}
	return h
}

func shotRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/screenshots", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, listShots())
	})
	mux.HandleFunc("GET /api/screenshots/img", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		p := findProfile(q.Get("profile"))
		if p == nil {
			http.Error(w, "no", 404)
			return
		}
		path, err := shotPath(p, q.Get("file"))
		if err != nil {
			http.Error(w, "no", 404)
			return
		}
		var b []byte
		if q.Get("thumb") == "1" {
			b, err = thumbnail(path, 480)
		} else {
			b, err = os.ReadFile(path)
		}
		if err != nil {
			http.Error(w, "no", 404)
			return
		}
		w.Header().Set("Content-Type", http.DetectContentType(b))
		w.Header().Set("Cache-Control", "max-age=86400")
		w.Write(b)
	})
	mux.HandleFunc("POST /api/screenshots/delete", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, File string }
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		p := findProfile(in.Profile)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		path, err := shotPath(p, in.File)
		if err != nil || !insideData(path) {
			jsonErr(w, fmt.Errorf("not allowed"))
			return
		}
		if err := os.Remove(path); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
	mux.HandleFunc("POST /api/screenshots/folder", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile string }
		body(r, &in)
		p := findProfile(in.Profile)
		if p == nil {
			jsonErr(w, fmt.Errorf("profile not found"))
			return
		}
		if err := openFolder(filepath.Join(profileDir(p), "screenshots")); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
}
