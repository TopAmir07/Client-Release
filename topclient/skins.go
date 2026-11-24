package main

// Skin Changer.
//
// Skins live in a small local library (dataDir/skins). They come from a PNG the
// user picks, or from looking up a public Minecraft username through Mojang's
// own API. Applying a skin sends it only to Mojang (api.minecraftservices.com)
// with the active account's token — the same request the official launcher
// makes. Tokens are never logged or sent to the UI.

import (
	"bytes"
	"crypto/sha1"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"image/png"
	"io"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
)

type skinEntry struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Variant string `json:"variant"` // "classic" | "slim"
	Source  string `json:"source"`  // "file" | "user"
	Added   int64  `json:"added"`
}

var (
	skinMu        sync.Mutex
	validUsername = regexp.MustCompile(`^[A-Za-z0-9_]{1,16}$`)
	validSkinID   = regexp.MustCompile(`^[a-f0-9]{40}$`)
)

func skinsDir() string          { return filepath.Join(dataDir, "skins") }
func skinsIndex() string        { return filepath.Join(skinsDir(), "library.json") }
func skinFile(id string) string { return filepath.Join(skinsDir(), id+".png") }

func loadSkinLib() []skinEntry {
	var lib []skinEntry
	if b, err := os.ReadFile(skinsIndex()); err == nil {
		json.Unmarshal(b, &lib)
	}
	return lib
}

func saveSkinLib(lib []skinEntry) error {
	os.MkdirAll(skinsDir(), 0o755)
	b, _ := json.MarshalIndent(lib, "", "  ")
	return os.WriteFile(skinsIndex(), b, 0o644)
}

func normVariant(v string) string {
	if strings.EqualFold(v, "slim") {
		return "slim"
	}
	return "classic"
}

// checkSkinPNG makes sure the bytes are a real Minecraft skin (PNG, 64x64 or 64x32).
func checkSkinPNG(b []byte) error {
	if len(b) == 0 || len(b) > 1<<20 {
		return errors.New("the skin file must be a PNG under 1 MB")
	}
	if http.DetectContentType(b) != "image/png" {
		return errors.New("that file isn't a PNG image")
	}
	cfg, err := png.DecodeConfig(bytes.NewReader(b))
	if err != nil {
		return errors.New("that PNG couldn't be read")
	}
	if cfg.Width != 64 || (cfg.Height != 64 && cfg.Height != 32) {
		return fmt.Errorf("skins must be 64×64 or 64×32 pixels (this one is %d×%d)", cfg.Width, cfg.Height)
	}
	return nil
}

// addSkin stores a skin in the library (same picture = same entry).
func addSkin(b []byte, name, variant, source string) (skinEntry, error) {
	if err := checkSkinPNG(b); err != nil {
		return skinEntry{}, err
	}
	sum := sha1.Sum(b)
	id := hex.EncodeToString(sum[:])
	skinMu.Lock()
	defer skinMu.Unlock()
	os.MkdirAll(skinsDir(), 0o755)
	if err := os.WriteFile(skinFile(id), b, 0o644); err != nil {
		return skinEntry{}, err
	}
	lib := loadSkinLib()
	e := skinEntry{ID: id, Name: name, Variant: normVariant(variant), Source: source, Added: time.Now().Unix()}
	out := []skinEntry{e}
	for _, x := range lib {
		if x.ID != id {
			out = append(out, x)
		}
	}
	if len(out) > 60 {
		for _, x := range out[60:] {
			os.Remove(skinFile(x.ID))
		}
		out = out[:60]
	}
	return e, saveSkinLib(out)
}

// lookupUserSkin finds a player's public skin through Mojang's API.
func lookupUserSkin(name string) ([]byte, string, string, error) {
	if !validUsername.MatchString(name) {
		return nil, "", "", errors.New("usernames are 1–16 letters, numbers or _")
	}
	var prof struct{ ID, Name string }
	err := getJSON("https://api.minecraftservices.com/minecraft/profile/lookup/name/"+name, &prof)
	if err != nil || prof.ID == "" {
		prof.ID = ""
		if err2 := getJSON("https://api.mojang.com/users/profiles/minecraft/"+name, &prof); err2 != nil || prof.ID == "" {
			return nil, "", "", fmt.Errorf("no Minecraft player called %q", name)
		}
	}
	var sess struct {
		Properties []struct{ Name, Value string } `json:"properties"`
	}
	if err := getJSON("https://sessionserver.mojang.com/session/minecraft/profile/"+prof.ID, &sess); err != nil {
		return nil, "", "", errors.New("Mojang didn't answer — try again in a moment")
	}
	var tex struct {
		Textures struct {
			SKIN struct {
				URL      string `json:"url"`
				Metadata struct {
					Model string `json:"model"`
				} `json:"metadata"`
			} `json:"SKIN"`
		} `json:"textures"`
	}
	for _, p := range sess.Properties {
		if p.Name == "textures" {
			raw, _ := base64.StdEncoding.DecodeString(p.Value)
			json.Unmarshal(raw, &tex)
		}
	}
	u := strings.Replace(tex.Textures.SKIN.URL, "http://", "https://", 1)
	if u == "" {
		return nil, "", "", fmt.Errorf("%s uses a default skin", prof.Name)
	}
	resp, err := netClient.Get(u)
	if err != nil {
		return nil, "", "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, "", "", fmt.Errorf("skin download failed (HTTP %d)", resp.StatusCode)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, "", "", err
	}
	return b, prof.Name, normVariant(tex.Textures.SKIN.Metadata.Model), nil
}

// uploadSkin sends the PNG to Mojang as the active account's skin.
func uploadSkin(b []byte, variant string) error {
	if err := checkSkinPNG(b); err != nil {
		return err
	}
	acc, err := ensureFreshAccount()
	if err != nil {
		return err
	}
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	mw.WriteField("variant", normVariant(variant))
	h := textproto.MIMEHeader{}
	h.Set("Content-Disposition", `form-data; name="file"; filename="skin.png"`)
	h.Set("Content-Type", "image/png")
	part, _ := mw.CreatePart(h)
	part.Write(b)
	mw.Close()
	req, _ := http.NewRequest("POST", "https://api.minecraftservices.com/minecraft/profile/skins", &buf)
	req.Header.Set("Authorization", "Bearer "+acc.AccessToken)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	resp, err := netClient.Do(req)
	if err != nil {
		return errors.New("couldn't reach Mojang — check your internet")
	}
	defer resp.Body.Close()
	io.Copy(io.Discard, io.LimitReader(resp.Body, 1<<20))
	switch {
	case resp.StatusCode == 200 || resp.StatusCode == 204:
	case resp.StatusCode == 401:
		return errors.New("your sign-in expired — log in again")
	case resp.StatusCode == 429:
		return errors.New("Mojang says too many skin changes — wait a minute and try again")
	default:
		return fmt.Errorf("Mojang refused the skin (HTTP %d)", resp.StatusCode)
	}
	if err := fetchProfile(acc); err == nil {
		storeAccount(acc)
	}
	return nil
}

func skinRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/skins", func(w http.ResponseWriter, r *http.Request) {
		skinMu.Lock()
		lib := loadSkinLib()
		skinMu.Unlock()
		sort.SliceStable(lib, func(i, j int) bool { return lib[i].Added > lib[j].Added })
		cur := map[string]any{}
		if a := activeAccount(); a != nil {
			cur = map[string]any{"uuid": a.UUID, "name": a.Name, "variant": normVariant(a.SkinVariant), "hasSkin": a.SkinURL != ""}
		}
		jsonOut(w, map[string]any{"library": lib, "current": cur})
	})
	mux.HandleFunc("GET /api/skins/file", func(w http.ResponseWriter, r *http.Request) {
		id := r.URL.Query().Get("id")
		if !validSkinID.MatchString(id) {
			http.Error(w, "bad id", 400)
			return
		}
		b, err := os.ReadFile(skinFile(id))
		if err != nil {
			http.Error(w, "not found", 404)
			return
		}
		w.Header().Set("Content-Type", "image/png")
		w.Header().Set("Cache-Control", "max-age=86400")
		w.Write(b)
	})
	mux.HandleFunc("POST /api/skins/upload", func(w http.ResponseWriter, r *http.Request) {
		b, err := io.ReadAll(io.LimitReader(r.Body, 1<<20+1))
		if err != nil {
			jsonErr(w, err)
			return
		}
		name := strings.TrimSpace(r.URL.Query().Get("name"))
		name = strings.TrimSuffix(name, filepath.Ext(name))
		if len(name) > 40 {
			name = name[:40]
		}
		if name == "" {
			name = "My skin"
		}
		e, err := addSkin(b, name, r.URL.Query().Get("variant"), "file")
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, e)
	})
	mux.HandleFunc("POST /api/skins/lookup", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Name string }
		body(r, &in)
		b, name, variant, err := lookupUserSkin(strings.TrimSpace(in.Name))
		if err != nil {
			jsonErr(w, err)
			return
		}
		e, err := addSkin(b, name, variant, "user")
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, e)
	})
	mux.HandleFunc("POST /api/skins/delete", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID string }
		body(r, &in)
		if !validSkinID.MatchString(in.ID) {
			jsonErr(w, errors.New("bad id"))
			return
		}
		skinMu.Lock()
		defer skinMu.Unlock()
		lib, out := loadSkinLib(), []skinEntry{}
		for _, x := range lib {
			if x.ID != in.ID {
				out = append(out, x)
			}
		}
		os.Remove(skinFile(in.ID))
		saveSkinLib(out)
		jsonOut(w, map[string]any{"ok": true})
	})
	// Apply a library skin, or (id "current") just switch the current skin between Slim and Wide.
	mux.HandleFunc("POST /api/skins/apply", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID, Variant string }
		body(r, &in)
		var b []byte
		var err error
		if in.ID == "current" {
			a := activeAccount()
			if a == nil {
				jsonErr(w, errors.New("log in first"))
				return
			}
			b, err = skinBytes(a.UUID)
			if err != nil {
				jsonErr(w, errors.New("your account uses a default skin — pick one first"))
				return
			}
		} else {
			if !validSkinID.MatchString(in.ID) {
				jsonErr(w, errors.New("bad id"))
				return
			}
			if b, err = os.ReadFile(skinFile(in.ID)); err != nil {
				jsonErr(w, errors.New("that skin is no longer in your library"))
				return
			}
		}
		if err := uploadSkin(b, in.Variant); err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, map[string]any{"ok": true})
	})
}
