package main

// Servers: your saved Java servers with live status (the same "server list ping" the
// Multiplayer screen uses: players, ping, message of the day, icon) and a Join button
// that starts a profile straight into the server.

import (
	"bufio"
	"bytes"
	"encoding/binary"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

type SavedServer struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Address string `json:"address"`
	Profile string `json:"profile,omitempty"`
}

type ServerStatus struct {
	Online  bool   `json:"online"`
	Players int    `json:"players"`
	Max     int    `json:"max"`
	Ping    int    `json:"ping"`
	Version string `json:"version,omitempty"`
	MOTD    string `json:"motd,omitempty"`
	Icon    string `json:"icon,omitempty"`
	Error   string `json:"error,omitempty"`
}

var (
	serversMu sync.Mutex
	joinMu    sync.Mutex
	joins     = map[string]string{}
	reColor   = regexp.MustCompile(`§.`)
)

func serversPath() string { return filepath.Join(dataDir, "servers.json") }

func loadServers() []SavedServer {
	var l []SavedServer
	readJSON(serversPath(), &l)
	if l == nil {
		l = []SavedServer{}
	}
	return l
}

// splitAddr: "host", "host:port" → host, port (25565 by default).
func splitAddr(a string) (string, int, error) {
	a = strings.TrimSpace(a)
	host, port := a, 25565
	if i := strings.LastIndex(a, ":"); i > 0 && !strings.Contains(a[i+1:], "]") {
		n, err := strconv.Atoi(a[i+1:])
		if err != nil || n < 1 || n > 65535 {
			return "", 0, errors.New("bad port")
		}
		host, port = a[:i], n
	}
	if !hostRe.MatchString(host) {
		return "", 0, errors.New("that isn't a server address")
	}
	return host, port, nil
}

func setJoin(profile, addr string) { joinMu.Lock(); joins[profile] = addr; joinMu.Unlock() }
func joinFor(profile string) string {
	joinMu.Lock()
	defer joinMu.Unlock()
	return joins[profile]
}
func takeJoin(profile string) string {
	joinMu.Lock()
	defer joinMu.Unlock()
	a := joins[profile]
	delete(joins, profile)
	return a
}

// quickJoinArgs: 1.20+ has --quickPlayMultiplayer; older versions use --server/--port.
func quickJoinArgs(addr, releaseTime string) []string {
	if addr == "" {
		return nil
	}
	if releaseTime >= "2023-04-05" {
		return []string{"--quickPlayMultiplayer", addr}
	}
	host, port, err := splitAddr(addr)
	if err != nil {
		return nil
	}
	return []string{"--server", host, "--port", strconv.Itoa(port)}
}

// ---- server list ping ----

func writeVarInt(w *bytes.Buffer, v int) {
	u := uint32(v)
	for {
		if u&^0x7F == 0 {
			w.WriteByte(byte(u))
			return
		}
		w.WriteByte(byte(u&0x7F | 0x80))
		u >>= 7
	}
}

func readVarInt(r io.ByteReader) (int, error) {
	var n uint32
	for i := 0; i < 5; i++ {
		b, err := r.ReadByte()
		if err != nil {
			return 0, err
		}
		n |= uint32(b&0x7F) << (7 * i)
		if b&0x80 == 0 {
			return int(int32(n)), nil
		}
	}
	return 0, errors.New("bad varint")
}

func packet(id int, body []byte) []byte {
	var inner bytes.Buffer
	writeVarInt(&inner, id)
	inner.Write(body)
	var out bytes.Buffer
	writeVarInt(&out, inner.Len())
	out.Write(inner.Bytes())
	return out.Bytes()
}

// chatText flattens a chat component (string or {"text","extra"}) to plain text.
func chatText(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case []any:
		var b strings.Builder
		for _, x := range t {
			b.WriteString(chatText(x))
		}
		return b.String()
	case map[string]any:
		s := ""
		if x, ok := t["text"].(string); ok {
			s = x
		}
		if ex, ok := t["extra"]; ok {
			s += chatText(ex)
		}
		return s
	}
	return ""
}

func pingServer(addr string) ServerStatus {
	host, port, err := splitAddr(addr)
	if err != nil {
		return ServerStatus{Error: err.Error()}
	}
	dialHost, dialPort := host, port
	if !strings.Contains(addr, ":") {
		if _, srv, err := net.LookupSRV("minecraft", "tcp", host); err == nil && len(srv) > 0 {
			dialHost, dialPort = strings.TrimSuffix(srv[0].Target, "."), int(srv[0].Port)
		}
	}
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(dialHost, strconv.Itoa(dialPort)), 5*time.Second)
	if err != nil {
		return ServerStatus{Error: "offline or unreachable"}
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(6 * time.Second))
	var hs bytes.Buffer
	writeVarInt(&hs, 767)
	writeVarInt(&hs, len(host))
	hs.WriteString(host)
	binary.Write(&hs, binary.BigEndian, uint16(port))
	writeVarInt(&hs, 1)
	conn.Write(packet(0, hs.Bytes()))
	conn.Write(packet(0, nil))
	r := bufio.NewReader(conn)
	if _, err := readVarInt(r); err != nil {
		return ServerStatus{Error: "no answer"}
	}
	if id, err := readVarInt(r); err != nil || id != 0 {
		return ServerStatus{Error: "not a Minecraft server"}
	}
	n, err := readVarInt(r)
	if err != nil || n < 2 || n > 1<<20 {
		return ServerStatus{Error: "bad answer"}
	}
	raw := make([]byte, n)
	if _, err := io.ReadFull(r, raw); err != nil {
		return ServerStatus{Error: "bad answer"}
	}
	var st struct {
		Version struct {
			Name string `json:"name"`
		} `json:"version"`
		Players struct {
			Max    int `json:"max"`
			Online int `json:"online"`
		} `json:"players"`
		Description any    `json:"description"`
		Favicon     string `json:"favicon"`
	}
	if json.Unmarshal(raw, &st) != nil {
		return ServerStatus{Error: "bad answer"}
	}
	out := ServerStatus{Online: true, Players: st.Players.Online, Max: st.Players.Max, Version: cleanText(reColor.ReplaceAllString(st.Version.Name, ""), 40)}
	out.MOTD = strings.TrimSpace(reColor.ReplaceAllString(chatText(st.Description), ""))
	if len(out.MOTD) > 160 {
		out.MOTD = out.MOTD[:160]
	}
	if strings.HasPrefix(st.Favicon, "data:image/png;base64,") && len(st.Favicon) < 64<<10 {
		out.Icon = st.Favicon
	}
	// ping: 0x01 with a number, the server echoes it
	t0 := time.Now()
	var pb bytes.Buffer
	binary.Write(&pb, binary.BigEndian, t0.UnixMilli())
	conn.Write(packet(1, pb.Bytes()))
	if _, err := readVarInt(r); err == nil {
		out.Ping = int(time.Since(t0).Milliseconds())
	}
	return out
}

func serverRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/servers", func(w http.ResponseWriter, r *http.Request) {
		serversMu.Lock()
		defer serversMu.Unlock()
		jsonOut(w, loadServers())
	})
	mux.HandleFunc("POST /api/servers", func(w http.ResponseWriter, r *http.Request) {
		var in []SavedServer
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		out := []SavedServer{}
		for _, s := range in {
			if _, _, err := splitAddr(s.Address); err != nil || len(out) >= 60 {
				continue
			}
			if s.ID == "" {
				s.ID = randHex(5)
			}
			s.Name = cleanText(s.Name, 40)
			if s.Name == "" {
				s.Name = s.Address
			}
			out = append(out, s)
		}
		serversMu.Lock()
		err := writeJSON(serversPath(), out)
		serversMu.Unlock()
		if err != nil {
			jsonErr(w, err)
			return
		}
		jsonOut(w, out)
	})
	mux.HandleFunc("GET /api/servers/ping", func(w http.ResponseWriter, r *http.Request) {
		jsonOut(w, pingServer(r.URL.Query().Get("addr")))
	})
	mux.HandleFunc("POST /api/servers/join", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, Address string }
		if err := body(r, &in); err != nil {
			jsonErr(w, err)
			return
		}
		p := findProfile(in.Profile)
		if p == nil {
			jsonErr(w, errors.New("pick a profile first"))
			return
		}
		if isBedrock(p) {
			jsonErr(w, errors.New("this is a Java server: pick a Java profile"))
			return
		}
		if _, _, err := splitAddr(in.Address); err != nil {
			jsonErr(w, err)
			return
		}
		setJoin(p.ID, strings.TrimSpace(in.Address))
		if err := launch(p, false, false); err != nil {
			takeJoin(p.ID)
			jsonErr(w, err)
			return
		}
		recordStat("joins", 1)

		jsonOut(w, map[string]any{"ok": true})
	})
}
