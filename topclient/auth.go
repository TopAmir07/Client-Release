package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Accounts are stored encrypted with Windows DPAPI, so only the signed-in
// Windows user on this PC can read them. Tokens are never sent to the UI.

type Account struct {
	UUID         string `json:"uuid"`
	Name         string `json:"name"`
	AccessToken  string `json:"accessToken"`
	ExpiresAt    int64  `json:"expiresAt"`
	RefreshToken string `json:"refreshToken"`
	Kind         string `json:"kind"` // "live" | "azure"
	ClientID     string `json:"clientId,omitempty"`
	XUID         string `json:"xuid"`
	SkinURL      string `json:"skinUrl"`
	SkinVariant  string `json:"skinVariant"`
}

type accountStore struct {
	Active   string     `json:"active"`
	Accounts []*Account `json:"accounts"`
}

var (
	accMu  sync.Mutex
	accs   accountStore
	accDat = func() string { return filepath.Join(dataDir, "accounts.dat") }
)

func loadAccounts() {
	b, err := os.ReadFile(accDat())
	if err != nil {
		return
	}
	plain, err := unprotect(b)
	if err != nil {
		logf("could not decrypt accounts (different Windows user?)")
		return
	}
	json.Unmarshal(plain, &accs)
}

func saveAccounts() error {
	b, _ := json.Marshal(accs)
	enc, err := protect(b)
	if err != nil {
		return err
	}
	return os.WriteFile(accDat(), enc, 0o600)
}

func activeAccount() *Account {
	accMu.Lock()
	defer accMu.Unlock()
	for _, a := range accs.Accounts {
		if a.UUID == accs.Active {
			return a
		}
	}
	return nil
}

func publicAccounts() []map[string]any {
	accMu.Lock()
	defer accMu.Unlock()
	out := []map[string]any{}
	for _, a := range accs.Accounts {
		out = append(out, map[string]any{"uuid": a.UUID, "name": a.Name, "active": a.UUID == accs.Active, "variant": a.SkinVariant})
	}
	return out
}

func storeAccount(a *Account) {
	accMu.Lock()
	defer accMu.Unlock()
	replaced := false
	for i, x := range accs.Accounts {
		if x.UUID == a.UUID {
			accs.Accounts[i] = a
			replaced = true
		}
	}
	if !replaced {
		accs.Accounts = append(accs.Accounts, a)
	}
	accs.Active = a.UUID
	saveAccounts()
}

func selectAccount(uuid string) {
	accMu.Lock()
	defer accMu.Unlock()
	for _, a := range accs.Accounts {
		if a.UUID == uuid {
			accs.Active = uuid
		}
	}
	saveAccounts()
}

func removeAccount(uuid string) {
	accMu.Lock()
	defer accMu.Unlock()
	var keep []*Account
	for _, a := range accs.Accounts {
		if a.UUID != uuid {
			keep = append(keep, a)
		}
	}
	accs.Accounts = keep
	if accs.Active == uuid {
		accs.Active = ""
		if len(keep) > 0 {
			accs.Active = keep[0].UUID
		}
	}
	saveAccounts()
	os.Remove(filepath.Join(dataDir, "cache", "skin-"+cleanName(uuid)+".png"))
}

// ---------------------------------------------------------------------------
// Microsoft sign-in
// ---------------------------------------------------------------------------

const (
	liveClientID = "00000000402b5328" // Minecraft's public sign-in app
	liveRedirect = "https://login.live.com/oauth20_desktop.srf"
	liveScope    = "service::user.auth.xboxlive.com::MBI_SSL"
	// Public Microsoft-account app that supports sign-in codes (microsoft.com/link).
	deviceClientID = "00000000441cc96b"
)

func liveAuthorizeURL() string {
	q := url.Values{
		"client_id":     {liveClientID},
		"response_type": {"code"},
		"scope":         {liveScope},
		"redirect_uri":  {liveRedirect},
		"prompt":        {"select_account"},
	}
	return "https://login.live.com/oauth20_authorize.srf?" + q.Encode()
}

type msToken struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	Error        string `json:"error"`
	ErrorDesc    string `json:"error_description"`
}

func postForm(u string, form url.Values, out any) (int, error) {
	req, _ := http.NewRequest("POST", u, strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")
	resp, err := netClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	json.Unmarshal(b, out)
	return resp.StatusCode, nil
}

func postJSON(u string, body any, bearer string, out any) (int, error) {
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", u, bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	if bearer != "" {
		req.Header.Set("Authorization", "Bearer "+bearer)
	}
	resp, err := netClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	rb, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	json.Unmarshal(rb, out)
	return resp.StatusCode, nil
}

func finishLive(redirected string) (*Account, error) {
	redirected = strings.TrimSpace(redirected)
	code := ""
	if u, err := url.Parse(redirected); err == nil {
		code = u.Query().Get("code")
		if e := u.Query().Get("error"); e != "" {
			return nil, fmt.Errorf("sign-in was cancelled (%s)", e)
		}
	}
	if code == "" && strings.HasPrefix(redirected, "M.") {
		code = redirected
	}
	if code == "" {
		return nil, errors.New("that link doesn't contain a sign-in code — copy the full address after signing in")
	}
	var t msToken
	st, err := postForm("https://login.live.com/oauth20_token.srf", url.Values{
		"client_id": {liveClientID}, "code": {code}, "grant_type": {"authorization_code"},
		"redirect_uri": {liveRedirect}, "scope": {liveScope},
	}, &t)
	if err != nil {
		return nil, err
	}
	if st != 200 || t.AccessToken == "" {
		return nil, fmt.Errorf("Microsoft rejected the code (%s) — codes expire quickly, try again", t.Error)
	}
	acc, err := minecraftLogin(t.AccessToken, false)
	if err != nil {
		return nil, err
	}
	acc.RefreshToken, acc.Kind = t.RefreshToken, "live"
	storeAccount(acc)
	return acc, nil
}

// Device-code flow, for people who registered their own Azure app.
type deviceState struct {
	mu       sync.Mutex
	Code     string `json:"userCode"`
	URL      string `json:"verificationUri"`
	Status   string `json:"status"` // pending | done | error
	Error    string `json:"error"`
	Name     string `json:"name"`
	gen      int
	deadline time.Time
}

var device = &deviceState{}

func startDevice(clientID string) (map[string]any, error) {
	var r struct {
		DeviceCode      string `json:"device_code"`
		UserCode        string `json:"user_code"`
		VerificationURI string `json:"verification_uri"`
		Interval        int    `json:"interval"`
		ExpiresIn       int    `json:"expires_in"`
		Error           string `json:"error_description"`
	}
	live := clientID == ""
	var st int
	var err error
	if live {
		st, err = postForm("https://login.live.com/oauth20_connect.srf", url.Values{
			"client_id": {deviceClientID}, "scope": {liveScope}, "response_type": {"device_code"}}, &r)
	} else {
		st, err = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
			url.Values{"client_id": {clientID}, "scope": {"XboxLive.signin offline_access"}}, &r)
	}
	if err != nil {
		return nil, err
	}
	if st != 200 || r.DeviceCode == "" {
		return nil, fmt.Errorf("could not start sign-in: %s", r.Error)
	}
	if r.VerificationURI == "" {
		r.VerificationURI = "https://www.microsoft.com/link"
	}
	if r.ExpiresIn <= 0 {
		r.ExpiresIn = 900
	}
	device.mu.Lock()
	device.gen++
	gen := device.gen
	device.Code, device.URL, device.Status, device.Error, device.Name = r.UserCode, r.VerificationURI, "pending", "", ""
	device.deadline = time.Now().Add(time.Duration(r.ExpiresIn) * time.Second)
	device.mu.Unlock()
	interval := r.Interval
	if interval < 2 {
		interval = 5
	}
	go func() {
		for time.Now().Before(device.deadline) {
			time.Sleep(time.Duration(interval) * time.Second)
			device.mu.Lock()
			stale := device.gen != gen
			device.mu.Unlock()
			if stale {
				return // a newer code was requested
			}
			var t msToken
			var err error
			if live {
				_, err = postForm("https://login.live.com/oauth20_token.srf", url.Values{
					"client_id": {deviceClientID}, "grant_type": {"urn:ietf:params:oauth:grant-type:device_code"}, "device_code": {r.DeviceCode},
				}, &t)
			} else {
				_, err = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", url.Values{
					"client_id": {clientID}, "grant_type": {"urn:ietf:params:oauth:grant-type:device_code"}, "device_code": {r.DeviceCode},
				}, &t)
			}
			if err != nil {
				continue
			}
			if t.Error == "authorization_pending" {
				continue
			}
			if t.Error == "slow_down" {
				interval += 5
				continue
			}
			device.mu.Lock()
			if t.AccessToken == "" {
				msg := t.ErrorDesc
				if t.Error == "expired_token" {
					msg = "The code expired. Get a new one and try again."
				} else if t.Error == "authorization_declined" || t.Error == "access_denied" {
					msg = "Sign-in was declined."
				} else if msg == "" {
					msg = "Sign-in failed (" + t.Error + ")"
				}
				device.Status, device.Error = "error", msg
				device.mu.Unlock()
				return
			}
			device.mu.Unlock()
			acc, err := minecraftLogin(t.AccessToken, !live)
			device.mu.Lock()
			if err != nil {
				device.Status, device.Error = "error", err.Error()
			} else {
				if live {
					acc.RefreshToken, acc.Kind = t.RefreshToken, "devlive"
				} else {
					acc.RefreshToken, acc.Kind, acc.ClientID = t.RefreshToken, "azure", clientID
				}
				storeAccount(acc)
				device.Status, device.Name = "done", acc.Name
			}
			device.mu.Unlock()
			return
		}
		device.mu.Lock()
		if device.Status == "pending" {
			device.Status, device.Error = "error", "The code expired. Try again."
		}
		device.mu.Unlock()
	}()
	return map[string]any{"userCode": r.UserCode, "verificationUri": r.VerificationURI}, nil
}

func deviceStatus() map[string]any {
	device.mu.Lock()
	defer device.mu.Unlock()
	return map[string]any{"status": device.Status, "error": device.Error, "name": device.Name, "userCode": device.Code, "verificationUri": device.URL}
}

// minecraftLogin: Microsoft token -> Xbox Live -> XSTS -> Minecraft.
func minecraftLogin(msAccess string, azure bool) (*Account, error) {
	tickets := []string{msAccess, "t=" + msAccess, "d=" + msAccess}
	if azure {
		tickets = []string{"d=" + msAccess}
	}
	var xbl struct {
		Token         string
		DisplayClaims struct {
			Xui []struct {
				Uhs string `json:"uhs"`
			} `json:"xui"`
		}
	}
	var st int
	var err error
	for _, ticket := range tickets {
		st, err = postJSON("https://user.auth.xboxlive.com/user/authenticate", map[string]any{
			"Properties":   map[string]any{"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": ticket},
			"RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT",
		}, "", &xbl)
		if err != nil {
			return nil, err
		}
		if st == 200 && xbl.Token != "" {
			break
		}
	}
	if st != 200 || xbl.Token == "" || len(xbl.DisplayClaims.Xui) == 0 {
		return nil, fmt.Errorf("Xbox Live sign-in failed (HTTP %d)", st)
	}
	uhs := xbl.DisplayClaims.Xui[0].Uhs

	var xsts struct {
		Token string
		XErr  int64
	}
	st, err = postJSON("https://xsts.auth.xboxlive.com/xsts/authorize", map[string]any{
		"Properties":   map[string]any{"SandboxId": "RETAIL", "UserTokens": []string{xbl.Token}},
		"RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT",
	}, "", &xsts)
	if err != nil {
		return nil, err
	}
	if st != 200 || xsts.Token == "" {
		switch xsts.XErr {
		case 2148916233:
			return nil, errors.New("this Microsoft account has no Xbox profile — sign in once at minecraft.net first")
		case 2148916235:
			return nil, errors.New("Xbox Live isn't available in your country")
		case 2148916236, 2148916237:
			return nil, errors.New("this account needs adult verification on xbox.com")
		case 2148916238:
			return nil, errors.New("this is a child account — an adult must add it to a Microsoft Family")
		}
		return nil, fmt.Errorf("Xbox security sign-in failed (HTTP %d)", st)
	}
	var mc struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int64  `json:"expires_in"`
	}
	st, err = postJSON("https://api.minecraftservices.com/authentication/login_with_xbox",
		map[string]string{"identityToken": "XBL3.0 x=" + uhs + ";" + xsts.Token}, "", &mc)
	if err != nil {
		return nil, err
	}
	if st != 200 || mc.AccessToken == "" {
		if st == 403 {
			return nil, errors.New("Minecraft rejected this sign-in app (HTTP 403). If you set a custom Azure client ID, it needs Mojang approval")
		}
		return nil, fmt.Errorf("Minecraft sign-in failed (HTTP %d)", st)
	}
	acc := &Account{AccessToken: mc.AccessToken, ExpiresAt: time.Now().Unix() + mc.ExpiresIn - 300}
	if err := fetchProfile(acc); err != nil {
		return nil, err
	}
	return acc, nil
}

func fetchProfile(acc *Account) error {
	req, _ := http.NewRequest("GET", "https://api.minecraftservices.com/minecraft/profile", nil)
	req.Header.Set("Authorization", "Bearer "+acc.AccessToken)
	resp, err := netClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode == 404 {
		return errors.New("this account doesn't own Minecraft: Java Edition")
	}
	if resp.StatusCode != 200 {
		return fmt.Errorf("could not load Minecraft profile (HTTP %d)", resp.StatusCode)
	}
	var p struct {
		ID    string `json:"id"`
		Name  string `json:"name"`
		Skins []struct {
			State   string `json:"state"`
			URL     string `json:"url"`
			Variant string `json:"variant"`
		} `json:"skins"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&p); err != nil {
		return err
	}
	acc.UUID, acc.Name = p.ID, p.Name
	for _, s := range p.Skins {
		if s.State == "ACTIVE" {
			acc.SkinURL, acc.SkinVariant = strings.Replace(s.URL, "http://", "https://", 1), s.Variant
		}
	}
	os.Remove(filepath.Join(dataDir, "cache", "skin-"+cleanName(acc.UUID)+".png"))
	return nil
}

// ensureFreshAccount refreshes the Minecraft token if it is about to expire.
func ensureFreshAccount() (*Account, error) {
	return refreshAccountIf(0)
}

var refreshMu sync.Mutex

// refreshAccountIf renews the active account's sign-in when it expires within `ahead`
// seconds. The launcher does this quietly in the background while it is open, so pressing
// Play doesn't have to wait for Microsoft first.
func refreshAccountIf(ahead int64) (*Account, error) {
	refreshMu.Lock()
	defer refreshMu.Unlock()
	acc := activeAccount()
	if acc == nil {
		return nil, errors.New("Log in with your Microsoft account first")
	}
	if time.Now().Unix()+ahead < acc.ExpiresAt {
		return acc, nil
	}
	var t msToken
	var st int
	var err error
	if acc.Kind == "azure" {
		st, err = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", url.Values{
			"client_id": {acc.ClientID}, "grant_type": {"refresh_token"}, "refresh_token": {acc.RefreshToken},
			"scope": {"XboxLive.signin offline_access"},
		}, &t)
	} else if acc.Kind == "devlive" {
		st, err = postForm("https://login.live.com/oauth20_token.srf", url.Values{
			"client_id": {deviceClientID}, "grant_type": {"refresh_token"}, "refresh_token": {acc.RefreshToken},
			"scope": {liveScope},
		}, &t)
	} else {
		st, err = postForm("https://login.live.com/oauth20_token.srf", url.Values{
			"client_id": {liveClientID}, "grant_type": {"refresh_token"}, "refresh_token": {acc.RefreshToken},
			"redirect_uri": {liveRedirect}, "scope": {liveScope},
		}, &t)
	}
	if err != nil {
		return nil, err
	}
	if st != 200 || t.AccessToken == "" {
		return nil, errors.New("your sign-in expired — please log in again")
	}
	fresh, err := minecraftLogin(t.AccessToken, acc.Kind == "azure")
	if err != nil {
		return nil, err
	}
	fresh.Kind, fresh.ClientID = acc.Kind, acc.ClientID
	fresh.RefreshToken = t.RefreshToken
	if fresh.RefreshToken == "" {
		fresh.RefreshToken = acc.RefreshToken
	}
	storeAccount(fresh)
	return fresh, nil
}

// skinBytes returns the PNG of an account's current skin (cached on disk).
func skinBytes(uuid string) ([]byte, error) {
	accMu.Lock()
	var acc *Account
	for _, a := range accs.Accounts {
		if a.UUID == uuid || (uuid == "" && a.UUID == accs.Active) {
			acc = a
		}
	}
	accMu.Unlock()
	if acc == nil || acc.SkinURL == "" {
		return nil, errors.New("no skin")
	}
	cache := filepath.Join(dataDir, "cache", "skin-"+cleanName(acc.UUID)+".png")
	if b, err := os.ReadFile(cache); err == nil {
		return b, nil
	}
	resp, err := netClient.Get(acc.SkinURL)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return nil, err
	}
	os.MkdirAll(filepath.Dir(cache), 0o755)
	os.WriteFile(cache, b, 0o644)
	return b, nil
}
