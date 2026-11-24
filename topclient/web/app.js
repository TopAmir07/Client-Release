// TopClient by TopAmir_ — launcher interface. Plain JS, no libraries, nothing loaded from the web
// except Modrinth's public mod search and its pictures.
'use strict';

const TOKEN = (location.hash.match(/t=([0-9a-f]+)/) || [])[1] || '';
history.replaceState(null, '', location.pathname);
const MR = 'https://api.modrinth.com/v2';
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function api(path, data, opts = {}) {
  const init = { method: data !== undefined ? 'POST' : 'GET', headers: { 'X-Token': TOKEN } };
  if (data !== undefined) {
    if (data instanceof Blob || data instanceof ArrayBuffer) { init.body = data; if (opts.type) init.headers['Content-Type'] = opts.type; }
    else { init.body = JSON.stringify(data); init.headers['Content-Type'] = 'application/json'; }
  }
  const r = await fetch(path, init);
  const ct = r.headers.get('content-type') || '';
  const out = ct.includes('json') ? await r.json() : await r.text();
  if (!r.ok) throw new Error((out && out.error) || ('HTTP ' + r.status));
  return out;
}

// ---------- icons ----------
const sv = p => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`;
const I = {
  chev: sv('<path d="m6 9 6 6 6-6"/>'), up: sv('<path d="m6 15 6-6 6 6"/>'),
  plus: sv('<path d="M12 5v14M5 12h14"/>'), x: sv('<path d="M18 6 6 18M6 6l12 12"/>'),
  folder: sv('<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>'),
  edit: sv('<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13 7 4 4"/>'),
  trash: sv('<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>'),
  dl: sv('<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>'), up2: sv('<path d="M12 20V9M7 14l5-5 5 5M5 4h14"/>'),
  check: sv('<path d="m5 12 5 5 9-10"/>'), user: sv('<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4.5-6 8-6s7 2 8 6"/>'),
  out: sv('<path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/>'), log: sv('<path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5"/>'),
  search: sv('<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>'), bolt: sv('<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>'),
  play: sv('<path d="M7 4v16l13-8z"/>'), refresh: sv('<path d="M20 11a8 8 0 1 0-2 6M20 5v6h-6"/>'),
};
const MSLOGO = '<span class="ms"><i style="background:#f25022"></i><i style="background:#7fba00"></i><i style="background:#00a4ef"></i><i style="background:#ffb900"></i></span>';

// ---------- state ----------
let S = { profiles: [], settings: {}, accounts: [] };
let ST = { task: {}, running: [] };
let PAGE = 'home', MANIFEST = null;
const SKINS = new Map(); // uuid -> Image (or null)
const set = (k, d) => (S.settings && k in S.settings) ? S.settings[k] : d;
async function setSetting(k, v) { S.settings[k] = v; await api('/api/settings', { [k]: v }); }
async function refreshState() { S = await api('/api/state'); }
const activeAcc = () => (S.accounts || []).find(a => a.active) || null;
const profile = id => S.profiles.find(p => p.id === id);
function selected() { return profile(set('selectedProfile', '')) || S.profiles[0] || null; }
async function selectProfile(id) { await setSetting('selectedProfile', id); renderDock(); if (PAGE === 'home' || PAGE === 'profiles' || PAGE === 'mods') go(PAGE); }
async function manifest() { if (!MANIFEST) MANIFEST = await api('/api/versions'); return MANIFEST; }
function realMC(p) { return p.mc === 'latest' ? ((MANIFEST && MANIFEST.latest.release) || 'latest') : p.mc; }
const loaderName = l => l === 'fabric' ? 'Fabric' : 'Vanilla';
const HUES = ['#ff9a2e', '#ffc53d', '#ff5f3a', '#f97bb0', '#7aa7ff', '#5de0c0', '#c38bff', '#9be15d'];
function profColor(p) { let h = 0; for (const c of p.id) h = (h * 31 + c.charCodeAt(0)) >>> 0; return HUES[h % HUES.length]; }
function profIcon(p) {
  if ((p.icon || '').startsWith('custom')) return `<span class="ic"><img src="/api/profiles/icon?id=${encodeURIComponent(p.id)}&t=${TOKEN}&v=${encodeURIComponent(p.icon)}" alt=""></span>`;
  const st = set('colorIcons', false) ? `background:${profColor(p)};color:#111` : 'background:var(--acg);color:var(--onac)';
  return `<span class="ic" style="${st}">${esc((p.name || '?').trim()[0] || '?').toUpperCase()}</span>`;
}
const hrs = s => s >= 3600 ? (s / 3600).toFixed(s >= 36000 ? 0 : 1) + 'h' : Math.round(s / 60) + 'm';
function ago(t) { if (!t) return 'never'; const d = Date.now() / 1000 - t; if (d < 120) return 'just now'; if (d < 3600) return Math.round(d / 60) + ' min ago'; if (d < 86400) return Math.round(d / 3600) + ' h ago'; return Math.round(d / 86400) + ' days ago'; }
const isRunning = id => (ST.running || []).includes(id);

// ---------- toasts / modals / menus ----------
function toast(msg, kind = '', ms = 3600) {
  const t = document.createElement('div'); t.className = 'toast ' + kind; t.textContent = msg;
  $('#toasts').appendChild(t); setTimeout(() => t.remove(), ms);
}
function modal(html, cls = '') {
  const s = document.createElement('div'); s.className = 'scrim';
  s.innerHTML = `<div class="modal ${cls}">${html}</div>`;
  document.body.appendChild(s);
  const m = s.firstChild; m.close = () => { s.remove(); document.removeEventListener('keydown', key); };
  const key = e => { if (e.key === 'Escape') m.close(); };
  document.addEventListener('keydown', key);
  s.addEventListener('pointerdown', e => { if (e.target === s) m.close(); });
  $$('[data-x]', m).forEach(b => b.onclick = () => m.close());
  return m;
}
function confirmBox(title, text, yes = 'OK', danger = false) {
  return new Promise(res => {
    const m = modal(`<h2>${esc(title)}</h2><p class="desc">${esc(text)}</p><div class="foot"><button class="btn ghost" data-n>Cancel</button><button class="btn ${danger ? 'danger' : 'pri'}" data-y>${esc(yes)}</button></div>`);
    $('[data-n]', m).onclick = () => { m.close(); res(false); };
    $('[data-y]', m).onclick = () => { m.close(); res(true); };
  });
}
function menu(anchor, html, { up = false, align = 'left' } = {}) {
  $$('.menu').forEach(x => x.remove());
  const r = anchor.getBoundingClientRect();
  const m = document.createElement('div'); m.className = 'menu'; m.innerHTML = html;
  document.body.appendChild(m);
  m.style.minWidth = Math.max(260, r.width) + 'px';
  const h = m.offsetHeight, w = m.offsetWidth;
  m.style.top = (up ? r.top - h - 6 : r.bottom + 6) + 'px';
  m.style.left = Math.min(innerWidth - w - 8, align === 'right' ? r.right - w : r.left) + 'px';
  setTimeout(() => {
    const off = e => { if (!m.contains(e.target)) { m.remove(); removeEventListener('pointerdown', off, true); } };
    addEventListener('pointerdown', off, true);
  });
  return m;
}

// ---------- skins (flat renders, no 3D needed) ----------
function loadImg(src) { return new Promise(res => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = src; }); }
async function skinOf(uuid) {
  if (!uuid) return null;
  if (!SKINS.has(uuid)) SKINS.set(uuid, loadImg(`/api/skin?uuid=${encodeURIComponent(uuid)}&t=${TOKEN}`));
  return SKINS.get(uuid);
}
function defaultSkin() {
  const c = document.createElement('canvas'); c.width = 64; c.height = 64; const g = c.getContext('2d');
  const R = (x, y, w, h, col) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
  R(0, 8, 32, 8, '#c58c62'); R(8, 0, 16, 8, '#2a1d17'); R(0, 8, 32, 2, '#2a1d17'); R(9, 12, 2, 1, '#fff'); R(13, 12, 2, 1, '#fff'); R(10, 12, 1, 1, '#222'); R(13, 12, 1, 1, '#222'); R(11, 14, 2, 1, '#8a5a3c');
  R(16, 20, 24, 12, '#ff8a1f'); R(20, 16, 16, 4, '#ff8a1f'); R(40, 20, 16, 12, '#ff8a1f'); R(44, 16, 8, 4, '#ff8a1f'); R(40, 29, 16, 3, '#c58c62');
  R(0, 20, 16, 12, '#26282e'); R(4, 16, 8, 4, '#26282e'); R(0, 29, 16, 3, '#111'); R(16, 52, 16, 12, '#26282e'); R(16, 61, 16, 3, '#111'); R(32, 52, 16, 12, '#ff8a1f'); R(32, 61, 16, 3, '#c58c62');
  return c;
}
// draws the front (or back) of a skin, 16x32 pixels, scaled up crisp
function drawBody(img, slim, back = false, scale = 8) {
  const c = document.createElement('canvas'); c.width = 16 * scale; c.height = 32 * scale;
  const g = c.getContext('2d'); g.imageSmoothingEnabled = false;
  const src = img || defaultSkin();
  const legacy = (src.height || src.naturalHeight) === 32;
  const aw = slim ? 3 : 4;
  const part = (sx, sy, w, h, dx, dy, flip) => {
    if (flip) { g.save(); g.translate((dx + w) * scale, dy * scale); g.scale(-1, 1); g.drawImage(src, sx, sy, w, h, 0, 0, w * scale, h * scale); g.restore(); }
    else g.drawImage(src, sx, sy, w, h, dx * scale, dy * scale, w * scale, h * scale);
  };
  const f = back ? 1 : 0; // back faces sit one face-width further right
  // legs
  part(4 + f * 8, 20, 4, 12, back ? 8 : 4, 20);
  if (legacy) part(4 + f * 8, 20, 4, 12, back ? 4 : 8, 20, true); else part(20 + f * 8, 52, 4, 12, back ? 4 : 8, 20);
  // body
  part(20 + f * 12, 20, 8, 12, 4, 8);
  // arms
  const rx = back ? 12 : 4 - aw, lx = back ? 4 - aw : 12;
  part(44 + f * (aw + 4), 20, aw, 12, rx, 8);
  if (legacy) part(44 + f * (aw + 4), 20, aw, 12, lx, 8, true); else part(36 + f * (aw + 4), 52, aw, 12, lx, 8);
  // head
  part(8 + f * 16, 8, 8, 8, 4, 0);
  if (!legacy) { // second layer
    part(4 + f * 8, 36, 4, 12, back ? 8 : 4, 20); part(4 + f * 8, 52, 4, 12, back ? 4 : 8, 20);
    part(20 + f * 12, 36, 8, 12, 4, 8);
    part(44 + f * (aw + 4), 36, aw, 12, rx, 8); part(52 + f * (aw + 4), 52, aw, 12, lx, 8);
  }
  part(40 + f * 16, 8, 8, 8, 4, 0);
  return c;
}
function drawHead(img, size = 30) {
  const c = document.createElement('canvas'); c.width = c.height = 64; const g = c.getContext('2d'); g.imageSmoothingEnabled = false;
  const src = img || defaultSkin();
  g.drawImage(src, 8, 8, 8, 8, 0, 0, 64, 64); g.drawImage(src, 40, 8, 8, 8, 0, 0, 64, 64);
  c.style.width = c.style.height = size + 'px'; c.className = 'head';
  return c;
}

// ---------- top bar ----------
const TABS = [['home', 'PLAY'], ['profiles', 'PROFILES'], ['mods', 'MODS'], ['skins', 'SKINS'], ['settings', 'SETTINGS']];
function buildTabs() {
  $('#tabs').innerHTML = TABS.map(([k, n]) => `<button data-p="${k}">${n}</button>`).join('');
  $$('#tabs button').forEach(b => b.onclick = () => go(b.dataset.p));
}
async function renderAcct() {
  const a = activeAcc(), el = $('#acct');
  if (!a) { el.innerHTML = `${I.user.replace('<svg', '<svg width="22"')}<div class="nm"><b>Sign in</b><small>Microsoft account</small></div>`; return; }
  el.innerHTML = `<span class="hd"></span><div class="nm"><b>${esc(a.name)}</b><small>${(S.accounts || []).length > 1 ? (S.accounts.length + ' accounts') : 'Minecraft'}</small></div><span class="chev">${I.chev}</span>`;
  $('.hd', el).replaceWith(drawHead(await skinOf(a.uuid)));
}
$('#acct').onclick = async () => {
  if (!activeAcc()) return showLogin();
  const m = menu($('#acct'), (S.accounts || []).map(a => `<button class="mi ${a.active ? 'on' : ''}" data-u="${esc(a.uuid)}"><span class="hh"></span><div style="flex:1"><b>${esc(a.name)}</b><small>${a.active ? 'Playing as this account' : 'Switch to this account'}</small></div>${a.active ? I.check : ''}</button>`).join('') +
    `<hr><button class="mi" data-add>${I.plus}<b>Add another account</b></button><button class="mi" data-out>${I.out}<b>Log out of ${esc(activeAcc().name)}</b></button>`, { align: 'right' });
  for (const b of $$('[data-u]', m)) b.querySelector('.hh').replaceWith(drawHead(await skinOf(b.dataset.u)));
  $$('[data-u]', m).forEach(b => b.onclick = async () => {
    m.remove(); if (b.classList.contains('on')) return;
    try { await api('/api/accounts/select', { uuid: b.dataset.u }); await refreshState(); renderAcct(); toast('Now playing as ' + activeAcc().name, 'ok'); go(PAGE); } catch (e) { toast(e.message, 'err'); }
  });
  $('[data-add]', m).onclick = () => { m.remove(); showLogin(); };
  $('[data-out]', m).onclick = () => { m.remove(); logout(activeAcc()); };
};
async function logout(a) {
  if (!(await confirmBox('Log out?', `${a.name} will be removed from TopClient on this PC. You can sign in again any time.`, 'Log out', true))) return;
  await api('/api/accounts/remove', { uuid: a.uuid }); SKINS.delete(a.uuid);
  await refreshState(); renderAcct(); go(PAGE); toast('Logged out');
}

// window controls (only inside the TopClient app window)
function initWindow() {
  if (!(window.chrome && window.chrome.webview)) return;
  const w = $('#win'); w.hidden = false;
  const act = a => api('/api/window/action?a=' + a, {}).catch(() => ({}));
  $$('[data-wa]', w).forEach(b => b.onclick = () => act(b.dataset.wa));
  $$('.drag').forEach(d => {
    d.addEventListener('pointerdown', e => { if (e.button === 0 && e.detail < 2) act('drag'); });
    d.addEventListener('dblclick', () => act('max'));
  });
  let maxed = false;
  const refresh = () => api('/api/window/state').then(r => { maxed = !!r.max; }).catch(() => {});
  refresh(); addEventListener('resize', () => setTimeout(refresh, 120));
  const B = 6, cur = { l: 'ew-resize', r: 'ew-resize', t: 'ns-resize', b: 'ns-resize', tl: 'nwse-resize', br: 'nwse-resize', tr: 'nesw-resize', bl: 'nesw-resize' };
  const edge = e => maxed ? '' : (e.clientY < B ? 't' : e.clientY > innerHeight - B ? 'b' : '') + (e.clientX < B ? 'l' : e.clientX > innerWidth - B ? 'r' : '');
  addEventListener('pointermove', e => { const d = edge(e); document.documentElement.style.cursor = d ? cur[d] : ''; }, true);
  addEventListener('pointerdown', e => { const d = edge(e); if (d && e.button === 0) { e.preventDefault(); e.stopPropagation(); act('resize-' + d); } }, true);
}

// ---------- Microsoft sign-in ----------
async function showLogin() {
  const m = modal(`
    <div class="kicker">Account</div><h2 style="margin-top:8px">Sign in with Microsoft</h2>
    <p class="desc">Microsoft's own sign-in page opens in a small window. Your password only goes to Microsoft, and your account is saved encrypted on this PC only.</p>
    <div id="lgBox"><button class="btn pri" id="lgGo" style="width:100%;height:52px;font-size:15px">${MSLOGO}&nbsp; Sign in with Microsoft</button></div>
    <label class="f" style="margin-top:18px">Sign-in window<select class="input" id="lgBr"><option>Loading…</option></select></label>
    <div class="foot"><button class="btn ghost" data-cancel>Cancel</button></div>`);
  let run = 0;
  const box = $('#lgBox', m);
  api('/api/browsers').then(list => {
    const cur = set('signinBrowser', 'auto');
    $('#lgBr', m).innerHTML = list.map(b => `<option value="${esc(b.id)}" ${b.id === cur ? 'selected' : ''}>${esc(b.name)}</option>`).join('');
  }).catch(() => {});
  $('#lgBr', m).onchange = e => setSetting('signinBrowser', e.target.value);
  $('[data-cancel]', m).onclick = () => { run++; api('/api/auth/live/cancel', {}).catch(() => {}); m.close(); };
  const fail = msg => { toast(msg, 'err', 8000); box.innerHTML = `<button class="btn pri" id="lgGo" style="width:100%;height:52px;font-size:15px">${MSLOGO}&nbsp; Try again</button>`; $('#lgGo', box).onclick = start; };
  async function start() {
    const my = ++run;
    try { await api('/api/auth/live/start', {}); } catch (e) { return fail(e.message); }
    box.innerHTML = `<div class="row" style="padding:16px;background:var(--bg);border:1px solid var(--line2);gap:14px"><span class="spin"></span><div><b>Waiting for you to sign in…</b><br><span class="muted" style="font-size:12.5px">Finish on the Microsoft window (it may be behind this one). It closes by itself.</span></div></div>`;
    while (my === run && document.body.contains(m)) {
      await sleep(1000);
      const st = await api('/api/auth/live/status').catch(() => ({}));
      if (my !== run) return;
      if (st.status === 'done') { run++; m.close(); await refreshState(); renderAcct(); toast(`Welcome, ${st.name}!`, 'ok'); go(PAGE); return; }
      if (st.status === 'error') return fail(st.error || 'Sign-in failed');
    }
  }
  $('#lgGo', box).onclick = start;
}

// ---------- dock (profile picker + PLAY) ----------
function renderDock() {
  const p = selected();
  $('#dockProf').innerHTML = p ? `${profIcon(p)}<div style="min-width:0"><b>${esc(p.name)}</b><small>${loaderName(p.loader)} · ${esc(realMC(p))}</small></div><span class="chev">${I.up}</span>` : '<div><b>No profile</b><small>Create one</small></div>';
  renderStatus();
}
$('#dockProf').onclick = () => {
  const cur = selected();
  const m = menu($('#dockProf'), S.profiles.map(p => `<button class="mi ${cur && p.id === cur.id ? 'on' : ''}" data-id="${esc(p.id)}">${profIcon(p)}<div style="flex:1"><b>${esc(p.name)}</b><small>${loaderName(p.loader)} · ${esc(realMC(p))}${isRunning(p.id) ? ' · running' : ''}</small></div></button>`).join('') +
    `<hr><button class="mi" data-new>${I.plus}<b>New profile</b></button>`, { up: true });
  $$('[data-id]', m).forEach(b => b.onclick = () => { m.remove(); selectProfile(b.dataset.id); });
  $('[data-new]', m).onclick = () => { m.remove(); profileEditor(); };
};
function renderStatus() {
  const p = selected(), t = ST.task || {}, play = $('#play'), stage = $('#dockStage'), bar = $('#dockBar');
  const busy = t.active && (!t.profile || !p || t.profile === p.id || t.profile === 'upgrade');
  bar.parentElement.classList.toggle('ind', !!(busy && !t.total));
  if (busy) {
    const pct = t.total ? Math.min(100, Math.round(t.done / t.total * 100)) : 0;
    bar.style.width = pct + '%';
    stage.innerHTML = `<b>${esc(t.stage || 'Working')}</b>${t.total ? ` · ${pct}%` : ''}`;
    play.className = 'play busy'; play.innerHTML = '<span>CANCEL</span>'; play.dataset.mode = 'cancel';
  } else if (p && isRunning(p.id)) {
    bar.style.width = '100%';
    stage.innerHTML = `<b>${esc(p.name)} is running</b> · have fun`;
    play.className = 'play stop'; play.innerHTML = '<span>STOP</span>'; play.dataset.mode = 'stop';
  } else {
    bar.style.width = '0%';
    const a = activeAcc();
    stage.innerHTML = !a ? 'Sign in with Microsoft to play' : p ? `Ready · <b>${esc(a.name)}</b> on Minecraft ${esc(realMC(p))}${p.lastPlayed ? ' · last played ' + ago(p.lastPlayed) : ''}` : 'Create a profile to play';
    play.className = 'play'; play.innerHTML = '<span>PLAY</span>'; play.dataset.mode = 'play';
  }
}
$('#play').onclick = async () => {
  const p = selected(), mode = $('#play').dataset.mode;
  if (mode === 'cancel') { api('/api/cancel', {}).catch(() => {}); toast('Cancelling…'); return; }
  if (mode === 'stop') { if (await confirmBox('Stop Minecraft?', 'The game closes straight away. Unsaved progress in the last few seconds can be lost.', 'Stop game', true)) api('/api/kill', { id: p.id }); return; }
  if (!activeAcc()) return showLogin();
  if (!p) return profileEditor();
  try { await api('/api/launch', { id: p.id }); poll(true); if (set('minimizeOnPlay', false)) setTimeout(() => api('/api/window/action?a=min', {}).catch(() => {}), 3000); }
  catch (e) { toast(e.message, 'err', 7000); }
};
let pollT = 0, lastRunning = '', lastErr = '';
async function poll(now) {
  clearTimeout(pollT);
  try {
    ST = await api('/api/status');
    const r = (ST.running || []).join(',');
    if (r !== lastRunning) { lastRunning = r; if (PAGE === 'home' || PAGE === 'profiles') go(PAGE, true); }
    const e = (ST.task || {}).error || '';
    if (e && e !== lastErr && e !== 'cancelled') toast(e, 'err', 8000);
    lastErr = e;
    renderStatus();
    for (const c of (ST.crashes || [])) {
      const id = typeof c === 'string' ? c : (c && (c.profile || c.id)); if (!id || crashSeen.has(id)) continue;
      crashSeen.add(id); const pp = profile(id);
      toast(`${pp ? pp.name : 'Minecraft'} crashed. Open its game log from the Play page.`, 'err', 9000);
      api('/api/crash/dismiss', { id }).catch(() => {});
    }
  } catch { }
  pollT = setTimeout(poll, (ST.task || {}).active ? 600 : 2500);
}
const crashSeen = new Set();

// ---------- router ----------
const PAGES = {};
function go(page, quiet) {
  PAGE = page;
  $$('#tabs button').forEach(b => b.classList.toggle('on', b.dataset.p === page));
  const v = $('#view'); const keep = quiet ? v.scrollTop : 0;
  const el = document.createElement('div'); el.className = 'page' + (page === 'home' && activeAcc() ? ' page-stage' : '');
  if (quiet) { el.style.animation = 'none'; el.classList.add('quiet'); }
  v.replaceChildren(el);
  PAGES[page](el);
  v.scrollTop = keep;
  moveInk();
}
function moveInk() {
  const nav = $('#tabs'), on = $('#tabs button.on');
  let ink = $('#tabInk'); if (!ink) { ink = document.createElement('i'); ink.id = 'tabInk'; nav.appendChild(ink); }
  if (!on) { ink.style.opacity = 0; return; }
  const vertical = document.body.classList.contains('nav-side');
  ink.classList.toggle('v', vertical);
  if (vertical) { ink.style.top = on.offsetTop + 'px'; ink.style.height = on.offsetHeight + 'px'; ink.style.left = '0'; ink.style.width = '3px'; }
  else { ink.style.left = (on.offsetLeft + 14) + 'px'; ink.style.width = (on.offsetWidth - 28) + 'px'; ink.style.top = ''; ink.style.height = ''; }
  ink.style.opacity = 1;
}
addEventListener('resize', () => moveInk());

// ---------- PLAY (home) ----------
PAGES.home = async el => {
  const a = activeAcc(), p = selected();
  if (!a) {
    el.innerHTML = `<div class="welcome"><div class="box stag"><div class="wlogo">${LOGO()}</div><h1>TOP<span>CLIENT</span></h1>
      <p>A fast, clean Minecraft launcher by TopAmir_. Sign in with your Microsoft account to start playing. Your login stays on this PC.</p>
      <button class="btn pri shine" id="wGo" style="height:50px;padding:0 28px;font-size:15px">${MSLOGO}&nbsp; Sign in with Microsoft</button>
      <div class="feats"><span>${I.bolt} TopClient mod built in</span><span>${I.dl} Modrinth mods in one click</span><span>${I.user} Skin changer</span><span>${I.folder} Profiles for every version</span></div></div></div>`;
    $('#wGo', el).onclick = showLogin; return;
  }
  const others = (S.accounts || []).filter(x => !x.active).slice(0, 4);
  const recent = [...S.profiles].sort((x, y) => (y.lastPlayed || 0) - (x.lastPlayed || 0)).slice(0, 4);
  const total = S.profiles.reduce((n, x) => n + (x.playTime || 0), 0);
  const tc = tcState(p);
  const left = others.filter((_, i) => i % 2 === 0), right = others.filter((_, i) => i % 2 === 1);
  const alt = x => `<button class="sc-alt" data-u="${esc(x.uuid)}" title="Play as ${esc(x.name)}"><div class="v"></div><span>${esc(x.name)}</span></button>`;
  el.innerHTML = `<div class="sc">
    <div class="sc-beam"></div><div class="sc-floor"></div><div class="sc-word">TOPCLIENT</div>
    <aside class="sc-info">
      <div class="kicker">${p && isRunning(p.id) ? '<i class="live"></i> Now running' : 'Ready to launch'}</div>
      <h2>${esc(p ? p.name : 'No profile')}</h2>
      ${p ? `<div class="meta"><span class="chip ac">Minecraft ${esc(realMC(p))}</span><span class="chip">${loaderName(p.loader)}</span><span class="chip">${((p.ramMB || set('ramMB', 4096)) / 1024).toFixed(1).replace('.0', '')} GB</span>${p.fpsBoost ? '<span class="chip good">Boost</span>' : ''}</div>` : ''}
      ${tc ? `<button class="sc-tc ${tc.k}" id="scTc"><i></i><div><b>TopClient ${esc(tc.tc.version)} · ${esc(tc.t)}</b><small>${esc(tc.b || (tc.k === 'ok' ? 'Added when you press PLAY' : tc.d))}</small></div></button>` : ''}
      <div class="sc-acts">${p ? `<button class="btn" id="hEdit">${I.edit} Edit</button>${p.loader === 'fabric' ? `<button class="btn" id="hMods">${I.dl} Mods</button>` : ''}<button class="btn ghost" id="hFolder">${I.folder}</button><button class="btn ghost" id="hLog" title="Game log">${I.log}</button>` : `<button class="btn pri" id="hNew">${I.plus} New profile</button>`}</div>
      ${set('showStats', true) ? `<div class="sc-stats"><div><b>${hrs(total)}</b><span>played</span></div><div><b>${S.profiles.length}</b><span>profiles</span></div><div><b>${(S.accounts || []).length}</b><span>accounts</span></div></div>` : ''}
    </aside>
    <div class="sc-row">
      <div class="sc-side l">${left.map(alt).join('')}</div>
      <div class="sc-me" id="scMe"></div>
      <div class="sc-side r">${right.map(alt).join('')}<button class="sc-add" id="scAdd"><div class="ghost"><b>+</b></div><span>Add account</span></button></div>
    </div>
    ${set('showRecent', true) ? `<aside class="sc-recent"><div class="kicker">Jump back in</div>${recent.map(x => `<button class="sc-pr ${p && p.id === x.id ? 'on' : ''}" data-id="${esc(x.id)}">${profIcon(x)}<div><b>${esc(x.name)}</b><small>${loaderName(x.loader)} ${esc(realMC(x))} · ${isRunning(x.id) ? 'running' : x.lastPlayed ? ago(x.lastPlayed) : 'never played'}</small></div></button>`).join('')}<button class="sc-pr new" id="scNew">${I.plus}<div><b>New profile</b><small>Any version, Fabric or Vanilla</small></div></button></aside>` : ''}
  </div>`;
  // your skin, big and in 3D (drag to turn it); the other accounts stand at the sides
  const me = $('#scMe', el);
  if (set('showSkin', true) && window.SkinViewer) {
    const H = me.clientHeight || 560;
    const sc = Math.max(8, Math.min(22, H * 0.026));
    const v = new SkinViewer(me, { scale: sc, pose: 'hero', yaw: -10, baseYaw: -10, pitch: -3, slim: a.variant === 'SLIM', nameplate: a.name });
    v.scene.style.top = (H * 0.30 + 16 * sc) + 'px';
    v.load(`/api/skin?uuid=${encodeURIComponent(a.uuid)}&t=${TOKEN}`, null);
  }
  $$('.sc-alt', el).forEach((b, i) => {
    const x = (S.accounts || []).find(q => q.uuid === b.dataset.u); if (!x) return;
    if (window.SkinViewer) {
      const sv = new SkinViewer($('.v', b), { scale: 4.2, pose: 'hero', noWave: true, phase: i * 1.7, baseYaw: b.closest('.l') ? 26 : -40, slim: x.variant === 'SLIM', drag: false });
      sv.load(`/api/skin?uuid=${encodeURIComponent(x.uuid)}&t=${TOKEN}`, null);
    }
    b.onclick = async () => { try { await api('/api/accounts/select', { uuid: x.uuid }); await refreshState(); renderAcct(); renderDock(); toast('Now playing as ' + x.name, 'ok'); go('home'); } catch (e) { toast(e.message, 'err'); } };
  });
  $('#scAdd', el).onclick = showLogin;
  $$('.sc-pr[data-id]', el).forEach(b => b.onclick = () => selectProfile(b.dataset.id));
  const nw = $('#scNew', el); if (nw) nw.onclick = () => profileEditor();
  const tcb = $('#scTc', el); if (tcb) tcb.onclick = async () => {
    const st = tcState(p);
    if (st.k === 'ok') { toast(st.d, 'ok', 6000); return; }
    if (st.k === 'none') { profileEditor(); return; }
    try {
      if (st.k === 'off') await setSetting('topclientMod', true);
      else { await api('/api/profiles/update', { ...p, mc: st.tc.mc, loader: 'fabric' }); await refreshState(); renderDock(); toast(`${p.name} now uses Fabric ${st.tc.mc}`, 'ok'); }
      go('home', true);
    } catch (e) { toast(e.message, 'err', 7000); }
  };
  if (!p) { $('#hNew', el).onclick = () => profileEditor(); return; }
  $('#hEdit', el).onclick = () => profileEditor(p);
  if ($('#hMods', el)) $('#hMods', el).onclick = () => go('mods');
  $('#hFolder', el).onclick = () => api('/api/profiles/open', { id: p.id }).catch(e => toast(e.message, 'err'));
  $('#hLog', el).onclick = () => gameLog(p);
};

// ---------- the built-in TopClient mod ----------
function tcState(p) {
  const tc = S.topclientMod || {};
  if (!tc.version) return null;
  if (!set('topclientMod', true)) return { tc, k: 'off', t: 'Turned off', d: 'The TopClient mod is switched off in Settings, so profiles play without it.', b: 'Turn it on' };
  if (!p) return { tc, k: 'none', t: 'Make a profile', d: `Create a Fabric profile on Minecraft ${tc.mc} and TopClient is added by itself.`, b: '' };
  if (p.loader !== 'fabric') return { tc, k: 'loader', t: 'Needs Fabric', d: `${p.name} is a Vanilla profile. Switch it to Fabric on Minecraft ${tc.mc} to get TopClient.`, b: 'Switch to Fabric ' + tc.mc };
  const mc = realMC(p);
  if (mc !== tc.mc && !mc.startsWith(tc.mc + '.')) return { tc, k: 'ver', t: 'Different version', d: `TopClient ${tc.version} is made for Minecraft ${tc.mc}. ${p.name} is on ${mc}.`, b: 'Switch to ' + tc.mc };
  return { tc, k: 'ok', t: 'Ready', d: `TopClient ${tc.version} goes into ${p.name} when you press PLAY: mod menu on Right Shift, HUD, crosshair, Fullbright and more.`, b: '' };
}
function tcCardHTML(p) {
  const s = tcState(p); if (!s) return '';
  return `<section class="card tcmod stagin ${s.k}"><div class="tcl">${LOGO()}</div><div class="tct"><div class="kicker">TopClient mod ${esc(s.tc.version)} · Minecraft ${esc(s.tc.mc)}</div>
    <h3>${s.k === 'ok' ? '<i class="dot"></i>' : ''}${esc(s.t)}</h3><p>${esc(s.d)}</p></div>${s.b ? `<button class="btn pri" id="tcFix">${esc(s.b)}</button>` : ''}</section>`;
}
function wireTcCard(el, p) {
  const b = $('#tcFix', el); if (!b) return;
  const s = tcState(p);
  b.onclick = async () => {
    b.disabled = true;
    try {
      if (s.k === 'off') await setSetting('topclientMod', true);
      else if (s.k === 'loader' || s.k === 'ver') { await api('/api/profiles/update', { ...p, mc: s.tc.mc, loader: 'fabric' }); await refreshState(); renderDock(); toast(`${p.name} now uses Fabric ${s.tc.mc}`, 'ok'); }
      go('home', true);
    } catch (e) { b.disabled = false; toast(e.message, 'err', 7000); }
  };
}

function tileHTML(p) {
  const sel = selected();
  return `<div class="card tile ${sel && sel.id === p.id ? 'sel' : ''}" data-id="${esc(p.id)}">
    ${profIcon(p)}<h4>${esc(p.name)}</h4><div class="sub">${loaderName(p.loader)} · Minecraft ${esc(realMC(p))}</div>
    <div class="bot">${isRunning(p.id) ? '<span class="chip good">Running</span>' : `<span class="dl">${p.lastPlayed ? 'Played ' + ago(p.lastPlayed) : 'Never played'}</span>`}<span class="grow"></span>
      <button class="ibtn" data-edit title="Edit">${I.edit}</button><button class="ibtn" data-go title="Play">${I.play}</button></div></div>`;
}
function wireTiles(el) {
  $$('.tile[data-id]', el).forEach(t => {
    const id = t.dataset.id;
    t.onclick = e => { if (e.target.closest('button')) return; selectProfile(id); };
    $('[data-edit]', t).onclick = () => profileEditor(profile(id));
    $('[data-go]', t).onclick = async () => { await selectProfile(id); $('#play').click(); };
  });
}

// ---------- game log ----------
function gameLog(p) {
  const m = modal(`<div class="kicker">${esc(p.name)}</div><h2 style="margin-top:8px">Game log</h2><div class="log" id="lg">Loading…</div>
    <div class="foot"><button class="btn ghost" id="lgCopy">Copy</button><button class="btn" data-x>Close</button></div>`, 'wide');
  let since = 0, alive = true, lines = [];
  const out = $('#lg', m);
  const close = m.close; m.close = () => { alive = false; close(); };
  $('#lgCopy', m).onclick = () => navigator.clipboard.writeText(lines.join('\n')).then(() => toast('Log copied', 'ok'));
  (async function tick() {
    while (alive) {
      try {
        const r = await api(`/api/logs?id=${encodeURIComponent(p.id)}&since=${since}`);
        if (r.lines && r.lines.length) { lines.push(...r.lines); if (lines.length > 4000) lines = lines.slice(-4000); out.textContent = lines.join('\n'); out.scrollTop = out.scrollHeight; }
        else if (!lines.length) out.textContent = 'Nothing yet. Press PLAY and the game\'s output shows up here.';
        since = r.next || since;
      } catch { }
      await sleep(900);
    }
  })();
}

// ---------- PROFILES ----------
PAGES.profiles = el => {
  el.innerHTML = `<div class="ph"><div><div class="kicker">Instances</div><h1>Profiles</h1><p>Each profile has its own version, mods, worlds and settings.</p></div><div class="grow"></div>
    <button class="btn pri" id="pNew">${I.plus} New profile</button></div>
    <div class="tiles">${S.profiles.map(tileHTML).join('')}<button class="card tile new" id="pNew2">${I.plus}<b>New profile</b></button></div>`;
  wireTiles(el);
  $('#pNew', el).onclick = $('#pNew2', el).onclick = () => profileEditor();
};

async function profileEditor(p, focusVersion) {
  const isNew = !p;
  const d = p ? { ...p } : { name: '', mc: 'latest', loader: 'fabric', ramMB: 0, fpsBoost: '', perfMods: false };
  const m = modal(`<div class="kicker">${isNew ? 'Create' : 'Edit'}</div><h2 style="margin-top:8px">${isNew ? 'New profile' : esc(p.name)}</h2><p class="desc">${isNew ? 'Pick a name, a Minecraft version and whether you want mods.' : 'Changes are saved when you press Save.'}</p>
    <div class="stack">
      <label class="f">Name<input class="input" id="eName" maxlength="40" placeholder="My profile" value="${esc(d.name)}"></label>
      <label class="f">Minecraft version<select class="input" id="eVer"><option>Loading versions…</option></select></label>
      <label class="row" style="gap:10px;font-size:12.5px;color:var(--mut);cursor:pointer"><span class="sw" id="eSnap"></span>Show snapshots and old versions</label>
      <label class="f">Mod loader<div class="seg" id="eLoad"><button data-l="fabric">Fabric (mods)</button><button data-l="vanilla">Vanilla</button></div></label>
      <label class="f">Memory for this profile<div class="rng"><input type="range" id="eRam" min="0" max="${Math.max(2048, Math.min(32768, (S.ramMB || 8192) - 1024))}" step="512" value="${d.ramMB || 0}"><output id="eRamO"></output></div></label>
      <label class="f">Speed boost<div class="seg" id="eFps"><button data-v="">Off</button><button data-v="light">Light</button><button data-v="balanced">Balanced</button><button data-v="max">Max FPS</button></div></label>
      <label class="row" style="gap:10px;font-size:13px;cursor:pointer" id="ePerfRow"><span class="sw ${d.perfMods ? 'on' : ''}" id="ePerf"></span>Add performance mods (Sodium, Lithium and friends from Modrinth)</label>
    </div>
    <div class="foot">${isNew ? '' : `<button class="btn ghost danger" id="eDel" style="margin-right:auto">${I.trash} Delete</button>`}<button class="btn ghost" data-x>Cancel</button><button class="btn pri" id="eSave">${isNew ? 'Create profile' : 'Save'}</button></div>`);
  let snaps = false, loader = d.loader === 'vanilla' ? 'vanilla' : 'fabric', fps = d.fpsBoost || '', perf = !!d.perfMods;
  const segs = () => {
    $$('#eLoad button', m).forEach(b => b.classList.toggle('on', b.dataset.l === loader));
    $$('#eFps button', m).forEach(b => b.classList.toggle('on', b.dataset.v === fps));
    $('#ePerfRow', m).style.display = loader === 'fabric' ? '' : 'none';
  };
  segs();
  $$('#eLoad button', m).forEach(b => b.onclick = () => { loader = b.dataset.l; segs(); });
  $$('#eFps button', m).forEach(b => b.onclick = () => { fps = b.dataset.v; segs(); });
  $('#ePerf', m).parentElement.onclick = e => { e.preventDefault(); perf = !perf; $('#ePerf', m).classList.toggle('on', perf); };
  const ram = $('#eRam', m), ramO = $('#eRamO', m);
  const ramTxt = () => ramO.textContent = +ram.value ? (ram.value / 1024).toFixed(1).replace('.0', '') + ' GB' : 'Default';
  ram.oninput = ramTxt; ramTxt();
  const verSel = $('#eVer', m);
  const fill = async () => {
    try {
      const man = await manifest();
      const list = man.versions.filter(v => snaps || v.type === 'release');
      verSel.innerHTML = `<option value="latest">Latest release (${esc(man.latest.release)})</option>` +
        list.map(v => `<option value="${esc(v.id)}">${esc(v.id)}${v.type !== 'release' ? ' · ' + esc(v.type.replace('old_', 'old ')) : ''}</option>`).join('');
      if (![...verSel.options].some(o => o.value === d.mc)) verSel.insertAdjacentHTML('beforeend', `<option value="${esc(d.mc)}">${esc(d.mc)}</option>`);
      verSel.value = d.mc;
    } catch (e) { verSel.innerHTML = `<option value="${esc(d.mc)}">${esc(d.mc)}</option>`; toast('Could not load the version list: ' + e.message, 'err'); }
  };
  verSel.onchange = () => d.mc = verSel.value;
  $('#eSnap', m).parentElement.onclick = e => { e.preventDefault(); snaps = !snaps; $('#eSnap', m).classList.toggle('on', snaps); fill(); };
  await fill();
  if (focusVersion) verSel.focus(); else $('#eName', m).focus();
  if (!isNew) $('#eDel', m).onclick = async () => {
    if (!(await confirmBox(`Delete ${p.name}?`, 'This deletes the profile and everything in its folder: mods, worlds, screenshots and settings. This cannot be undone.', 'Delete forever', true))) return;
    try { await api('/api/profiles/delete', { id: p.id }); await refreshState(); m.close(); renderDock(); go(PAGE); toast('Profile deleted'); } catch (e) { toast(e.message, 'err'); }
  };
  $('#eSave', m).onclick = async () => {
    const btn = $('#eSave', m); btn.disabled = true;
    try {
      const name = $('#eName', m).value.trim() || 'Profile';
      let q = p;
      if (isNew) q = await api('/api/profiles', { name, mc: verSel.value, loader });
      q = await api('/api/profiles/update', { ...q, name, mc: verSel.value, loader, ramMB: +ram.value });
      if ((q.fpsBoost || '') !== fps || !!q.perfMods !== (perf && loader === 'fabric')) await api('/api/profiles/fps', { id: q.id, level: fps, perfMods: perf && loader === 'fabric' });
      await refreshState();
      if (isNew) await setSetting('selectedProfile', q.id);
      m.close(); renderDock(); go(PAGE); toast(isNew ? 'Profile created' : 'Saved', 'ok');
    } catch (e) { btn.disabled = false; toast(e.message, 'err', 7000); }
  };
}

// ---------- MODS (Modrinth style) ----------
const MODS = { tab: 'browse', kind: 'mod', q: '', installed: {}, sort: '', cat: '', offset: 0 };
const KINDS = { mod: ['mods', 'Mods'], resourcepack: ['resourcepacks', 'Resource packs'], shader: ['shaderpacks', 'Shaders'] };
const MR_SORTS = [['', 'Relevance'], ['downloads', 'Downloads'], ['follows', 'Followers'], ['newest', 'Newest'], ['updated', 'Recently updated']];
const MR_CATS = {
  mod: [['optimization', 'Optimization'], ['utility', 'Utility'], ['decoration', 'Decoration'], ['adventure', 'Adventure'], ['equipment', 'Equipment'], ['library', 'Library'], ['magic', 'Magic'], ['mobs', 'Mobs'], ['storage', 'Storage'], ['technology', 'Technology'], ['transportation', 'Transportation'], ['worldgen', 'World generation'], ['social', 'Social'], ['food', 'Food']],
  resourcepack: [['16x', '16x'], ['32x', '32x'], ['64x', '64x'], ['realistic', 'Realistic'], ['simplistic', 'Simplistic'], ['themed', 'Themed'], ['vanilla-like', 'Vanilla-like'], ['combat', 'Combat'], ['gui', 'GUI'], ['fonts', 'Fonts']],
  shader: [['cartoon', 'Cartoon'], ['cursed', 'Cursed'], ['fantasy', 'Fantasy'], ['realistic', 'Realistic'], ['semi-realistic', 'Semi-realistic'], ['vanilla-like', 'Vanilla-like'], ['low', 'Low-end PC'], ['medium', 'Medium PC'], ['high', 'High-end PC']],
};
const agoISO = d => { const t = Date.parse(d); return isNaN(t) ? '' : ago(t / 1000); };
const skel = n => `<div class="mr-list">${Array.from({ length: n }, () => '<div class="mr-card sk"><i class="a"></i><div><i class="b"></i><i class="c"></i><i class="d"></i></div></div>').join('')}</div>`;
// Modrinth descriptions are markdown: escape everything, then allow headings, bold, italics, lists and plain links (shown as text, opened in your browser).
function mdLite(md) {
  return esc(md || '').replace(/!\[[^\]]*\]\([^)]*\)/g, '').replace(/&lt;[^&]*?&gt;/g, '')
    .split(/\n{2,}/).map(b => {
      b = b.trim(); if (!b) return '';
      const h = b.match(/^(#{1,4})\s+(.*)$/); if (h) return `<h${Math.min(4, h[1].length + 2)}>${h[2]}</h${Math.min(4, h[1].length + 2)}>`;
      if (/^[-*]\s/m.test(b)) return '<ul>' + b.split('\n').filter(l => /^\s*[-*]\s/.test(l)).map(l => `<li>${l.replace(/^\s*[-*]\s/, '')}</li>`).join('') + '</ul>';
      return `<p>${b.replace(/\n/g, '<br>')}</p>`;
    }).join('')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/(^|[^*])\*([^*]+)\*/g, '$1<i>$2</i>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a data-href="$2">$1</a>');
}
PAGES.mods = async el => {
  const p = selected();
  if (!p) { el.innerHTML = `<div class="empty"><b>No profile yet</b>Create a profile first.</div>`; return; }
  if (p.loader !== 'fabric' && MODS.kind !== 'resourcepack') MODS.kind = 'resourcepack';
  const cats = () => MR_CATS[MODS.kind] || [];
  el.innerHTML = `<div class="ph"><div><div class="kicker">${esc(p.name)} · ${loaderName(p.loader)} ${esc(realMC(p))}</div><h1>Mods &amp; packs</h1><p>${p.loader === 'fabric' ? 'Everything from Modrinth, filtered to what fits this profile. One click to install.' : 'Vanilla profiles can use resource packs. Switch the profile to Fabric for mods and shaders.'}</p></div></div>
    <div class="toolbar">
      <div class="seg" id="mTab"><button data-t="browse">${I.dl} Browse</button><button data-t="installed">Installed</button></div>
      <div class="seg" id="mKind">${Object.entries(KINDS).map(([k, v]) => `<button data-k="${k}" ${p.loader !== 'fabric' && k !== 'resourcepack' ? 'disabled style="opacity:.35"' : ''}>${v[1]}</button>`).join('')}</div>
      <div class="grow"></div>
      <button class="btn ghost" id="mFolder">${I.folder} Folder</button>
    </div>
    <div class="mr-wrap"><aside class="mr-side" id="mSide"></aside><div class="mr-main"><div class="mr-bar" id="mBar"></div><div id="mList"></div></div></div>`;
  const side = $('#mSide', el), bar = $('#mBar', el), list = $('#mList', el);
  const drawSide = () => {
    if (MODS.tab !== 'browse') { side.innerHTML = `<div class="mr-box"><b>Installed</b><small>Switch things on or off, or remove them. Built-in TopClient is managed in Settings → Game.</small></div>`; return; }
    side.innerHTML = `<div class="mr-box"><div class="mr-h">Categories</div>
      <button class="mr-cat ${MODS.cat ? '' : 'on'}" data-c="">All</button>${cats().map(([k, n]) => `<button class="mr-cat ${MODS.cat === k ? 'on' : ''}" data-c="${k}">${n}</button>`).join('')}</div>
      <div class="mr-box"><div class="mr-h">Fits this profile</div><small>Minecraft ${esc(realMC(p))}${MODS.kind === 'mod' ? ' · Fabric' : MODS.kind === 'shader' ? ' · Iris' : ''}</small></div>`;
    $$('[data-c]', side).forEach(b => b.onclick = () => { MODS.cat = b.dataset.c; MODS.offset = 0; drawSide(); load(); });
  };
  const drawBar = () => {
    if (MODS.tab !== 'browse') { bar.innerHTML = ''; return; }
    bar.innerHTML = `<div class="mr-search">${I.search || ''}<input class="input" id="mQ" placeholder="Search ${KINDS[MODS.kind][1].toLowerCase()}…" value="${esc(MODS.q)}"></div>
      <label class="mr-sort">Sort by<select class="input" id="mSort">${MR_SORTS.map(([k, n]) => `<option value="${k}" ${MODS.sort === k ? 'selected' : ''}>${n}</option>`).join('')}</select></label>`;
    let qt = 0; $('#mQ', bar).oninput = e => { MODS.q = e.target.value; MODS.offset = 0; clearTimeout(qt); qt = setTimeout(load, 280); };
    $('#mSort', bar).onchange = e => { MODS.sort = e.target.value; MODS.offset = 0; load(); };
  };
  const segs = () => { $$('#mTab button', el).forEach(b => b.classList.toggle('on', b.dataset.t === MODS.tab)); $$('#mKind button', el).forEach(b => b.classList.toggle('on', b.dataset.k === MODS.kind)); };
  const redraw = () => { segs(); drawSide(); drawBar(); load(); };
  $$('#mTab button', el).forEach(b => b.onclick = () => { MODS.tab = b.dataset.t; redraw(); });
  $$('#mKind button', el).forEach(b => b.onclick = () => { if (b.disabled) return; MODS.kind = b.dataset.k; MODS.cat = ''; MODS.offset = 0; redraw(); });
  $('#mFolder', el).onclick = () => api('/api/profiles/open', { id: p.id, sub: KINDS[MODS.kind][0] }).catch(e => toast(e.message, 'err'));
  let seq = 0;
  async function load(more) {
    const my = ++seq;
    if (!more) list.innerHTML = skel(6);
    if (MODS.tab === 'installed') return installed(my);
    MODS.installed = await api('/api/profiles/installed?id=' + encodeURIComponent(p.id)).catch(() => ({}));
    const facets = [[`project_type:${MODS.kind}`], [`versions:${realMC(p)}`]];
    if (MODS.kind === 'mod') facets.push(['categories:fabric']);
    if (MODS.kind === 'shader') facets.push(['categories:iris']);
    if (MODS.cat) facets.push([`categories:${MODS.cat}`]);
    const index = MODS.sort || (MODS.q ? 'relevance' : 'downloads');
    let r;
    try { r = await (await fetch(`${MR}/search?limit=24&offset=${MODS.offset}&index=${index}&query=${encodeURIComponent(MODS.q)}&facets=${encodeURIComponent(JSON.stringify(facets))}`)).json(); }
    catch { if (my === seq) list.innerHTML = '<div class="empty"><b>Modrinth can\'t be reached</b>Check your internet and try again.</div>'; return; }
    if (my !== seq) return;
    const hits = r.hits || [];
    if (!hits.length && !more) { list.innerHTML = `<div class="empty"><b>Nothing found</b>No ${KINDS[MODS.kind][1].toLowerCase()} match for Minecraft ${esc(realMC(p))}.</div>`; return; }
    const cards = hits.map((h, i) => `<div class="mr-card" style="--i:${i}" data-pid="${esc(h.project_id)}">
        <img class="pic" src="${esc(h.icon_url || 'logo.svg')}" alt="" loading="lazy">
        <div class="t"><div class="nm"><b>${esc(h.title)}</b><span>by ${esc(h.author)}</span></div><p>${esc(h.description)}</p>
          <div class="tags">${(h.display_categories || h.categories || []).filter(c => c !== 'fabric' && c !== 'iris').slice(0, 4).map(c => `<span class="tag">${esc(c)}</span>`).join('')}</div></div>
        <div class="st"><span>${I.dl} <b>${fmtNum(h.downloads)}</b></span><span>♥ <b>${fmtNum(h.follows)}</b></span><small>${agoISO(h.date_modified)}</small>
          ${MODS.installed[h.project_id] ? '<span class="chip good">Installed</span>' : `<button class="btn sm pri" data-i="${esc(h.project_id)}">${I.dl} Install</button>`}</div></div>`).join('');
    const total = r.total_hits || 0, shown = MODS.offset + hits.length;
    const moreBtn = shown < total ? `<button class="btn ghost mr-more" id="mMore">Show more (${fmtNum(total - shown)} left)</button>` : '';
    if (more) { $('#mMore', list)?.remove(); $('.mr-list', list).insertAdjacentHTML('beforeend', cards); list.insertAdjacentHTML('beforeend', moreBtn); }
    else list.innerHTML = `<div class="mr-count">${fmtNum(total)} results</div><div class="mr-list">${cards}</div>${moreBtn}`;
    const mb = $('#mMore', list); if (mb) mb.onclick = () => { MODS.offset = shown; mb.innerHTML = '<span class="spin"></span> Loading'; load(true); };
    wire();
  }
  function wire() {
    $$('.mr-card[data-pid]', list).forEach(c => {
      if (c.dataset.w) return; c.dataset.w = 1;
      c.onclick = e => { if (e.target.closest('button')) return; project(c.dataset.pid); };
      const b = $('[data-i]', c); if (b) b.onclick = () => install(b, b.dataset.i, '');
    });
  }
  async function install(b, project, version) {
    b.disabled = true; const was = b.innerHTML; b.innerHTML = '<span class="spin"></span> Installing';
    try { await api('/api/modrinth/install', { profile: p.id, project, version }); MODS.installed[project] = true; b.outerHTML = '<span class="chip good">Installed</span>'; toast('Installed', 'ok', 1800); }
    catch (e) { b.disabled = false; b.innerHTML = was; toast(e.message, 'err', 7000); }
  }
  async function project(id) {
    const m = modal(`<div class="mr-proj"><div class="row" style="padding:40px;justify-content:center"><span class="spin"></span></div></div>`, 'wide mr-modal');
    let pr, vs;
    try {
      [pr, vs] = await Promise.all([
        fetch(`${MR}/project/${encodeURIComponent(id)}`).then(r => r.json()),
        fetch(`${MR}/project/${encodeURIComponent(id)}/version?game_versions=${encodeURIComponent(JSON.stringify([realMC(p)]))}${MODS.kind === 'mod' ? '&loaders=' + encodeURIComponent('["fabric"]') : ''}`).then(r => r.json())]);
    } catch { $('.mr-proj', m).innerHTML = '<div class="empty"><b>Couldn\'t load this project</b>Modrinth can\'t be reached.</div>'; return; }
    const gal = (pr.gallery || []).slice(0, 8);
    $('.mr-proj', m).innerHTML = `<div class="mr-ph">${pr.icon_url ? `<img src="${esc(pr.icon_url)}" alt="">` : ''}<div style="flex:1;min-width:0"><h2>${esc(pr.title)}</h2><p class="desc" style="margin:4px 0 0">${esc(pr.description)}</p>
        <div class="tags" style="margin-top:10px">${(pr.categories || []).map(c => `<span class="tag">${esc(c)}</span>`).join('')}</div></div>
        <div class="mr-pst"><b>${fmtNum(pr.downloads)}</b><small>downloads</small><b>${fmtNum(pr.followers)}</b><small>followers</small></div></div>
      ${gal.length ? `<div class="mr-gal">${gal.map(g => `<img src="${esc(g.url)}" alt="" loading="lazy" title="${esc(g.title || '')}">`).join('')}</div>` : ''}
      <div class="mr-tabs seg"><button class="on" data-pt="d">Description</button><button data-pt="v">Versions (${vs.length || 0})</button></div>
      <div class="mr-pbody" id="prD">${mdLite(pr.body)}</div>
      <div class="mr-pbody" id="prV" hidden>${(vs || []).slice(0, 30).map(v => `<div class="li"><div class="t"><b>${esc(v.name || v.version_number)}</b><small>${esc(v.version_number)} · ${esc((v.game_versions || []).slice(-3).join(', '))} · ${agoISO(v.date_published)}</small></div><span class="chip ${v.version_type === 'release' ? 'good' : ''}">${esc(v.version_type)}</span><button class="btn sm" data-v="${esc(v.id)}">${I.dl} Install</button></div>`).join('') || '<div class="empty"><b>No version fits</b>Nothing for this Minecraft version yet.</div>'}</div>
      <div class="foot"><button class="btn ghost" data-site>Open on Modrinth</button><button class="btn ghost" data-x>Close</button>${MODS.installed[pr.id] ? '<span class="chip good">Installed</span>' : (vs.length ? `<button class="btn pri" data-inst>${I.dl} Install latest</button>` : '')}</div>`;
    $$('[data-pt]', m).forEach(b => b.onclick = () => { $$('[data-pt]', m).forEach(x => x.classList.toggle('on', x === b)); $('#prD', m).hidden = b.dataset.pt !== 'd'; $('#prV', m).hidden = b.dataset.pt !== 'v'; });
    $$('[data-href]', m).forEach(a => a.onclick = () => api('/api/open-url', { url: a.dataset.href }).catch(() => {}));
    $('[data-site]', m).onclick = () => api('/api/open-url', { url: `https://modrinth.com/${pr.project_type}/${pr.slug}` }).catch(e => toast(e.message, 'err'));
    const ib = $('[data-inst]', m); if (ib) ib.onclick = () => install(ib, pr.id, '').then(() => load());
    $$('[data-v]', m).forEach(b => b.onclick = () => install(b, pr.id, b.dataset.v));
  }
  async function installed(my) {
    const kind = KINDS[MODS.kind][0];
    let items;
    try { items = await api(`/api/profiles/content?id=${encodeURIComponent(p.id)}&kind=${kind}`); } catch (e) { list.innerHTML = `<div class="empty"><b>Couldn't read the folder</b>${esc(e.message)}</div>`; return; }
    if (my !== seq) return;
    if (!items.length) { list.innerHTML = `<div class="empty"><b>Nothing installed</b>Browse to add ${KINDS[MODS.kind][1].toLowerCase()}, or drop files into the folder.</div>`; return; }
    items.sort((a, b) => (b.bundled ? 1 : 0) - (a.bundled ? 1 : 0));
    list.innerHTML = `<div class="mr-count">${items.length} installed · ${items.filter(c => c.enabled).length} on</div><div class="list">${items.map((c, i) => `<div class="li mr-li ${c.enabled ? '' : 'off'} ${c.bundled ? 'builtin' : ''}" style="--i:${i}" data-f="${esc(c.file)}"><img class="pic" src="${esc(c.bundled ? 'logo.svg' : c.icon ? (c.icon.startsWith('/api/') ? c.icon + '&t=' + TOKEN : c.icon) : 'logo.svg')}" alt="" loading="lazy">
      <div class="t"><b>${esc(c.title || c.file.replace(/\.disabled$/, ''))}</b><small>${c.bundled ? 'Built into the launcher · added when you press PLAY on a Minecraft ' + esc((S.topclientMod || {}).mc || '') + ' Fabric profile' : esc(c.file) + ' · ' + (c.size / 1048576).toFixed(c.size > 10485760 ? 0 : 1) + ' MB'}</small></div>
      ${c.bundled ? `<span class="chip ac">Built in</span><button class="btn sm ghost" data-set>Settings</button>` : `<span class="sw ${c.enabled ? 'on' : ''}" data-tg title="${c.enabled ? 'Turn off' : 'Turn on'}"></span><button class="ibtn" data-rm title="Remove">${I.trash}</button>`}</div>`).join('')}</div>`;
    $$('.li', list).forEach(row => {
      const file = row.dataset.f;
      const st = $('[data-set]', row); if (st) { st.onclick = () => { SET_TAB = 'game'; go('settings'); }; return; }
      $('[data-tg]', row).onclick = async () => { try { const r = await api('/api/profiles/toggle', { id: p.id, kind, file }); if (r.disabled && r.disabled.length) toast('Switched off a duplicate: ' + r.disabled.join(', ')); load(); } catch (e) { toast(e.message, 'err'); } };
      $('[data-rm]', row).onclick = async () => { if (await confirmBox('Remove this file?', file, 'Remove', true)) { await api('/api/profiles/remove', { id: p.id, kind, file }).catch(e => toast(e.message, 'err')); load(); } };
    });
  }
  if (p.mc === 'latest' && !MANIFEST) { segs(); list.innerHTML = skel(6); await manifest().catch(() => {}); }
  redraw();
};
const fmtNum = n => n >= 1e6 ? (n / 1e6).toFixed(1).replace('.0', '') + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'K' : String(n);

// ---------- SKINS ----------
PAGES.skins = async el => {
  const a = activeAcc();
  if (!a) { el.innerHTML = `<div class="empty"><b>Sign in first</b>Skins are saved to your Minecraft account.</div><div style="text-align:center"><button class="btn pri" id="sIn">${MSLOGO}&nbsp; Sign in</button></div>`; $('#sIn', el).onclick = showLogin; return; }
  let data;
  try { data = await api('/api/skins'); } catch (e) { el.innerHTML = `<div class="empty"><b>Couldn't load skins</b>${esc(e.message)}</div>`; return; }
  const slim = (data.current.variant || '').toLowerCase() === 'slim';
  el.innerHTML = `<div class="ph"><div><div class="kicker">${esc(a.name)}</div><h1>Skins</h1><p>Upload a skin or copy one from any player name, then wear it with one click.</p></div></div>
    <div class="skinwrap">
      <div class="card skinstage"><div class="kicker">Wearing now</div><div class="views"><span id="sF"></span><span id="sB"></span></div>
        <div class="seg" id="sVar"><button data-v="classic" class="${slim ? '' : 'on'}">Wide arms</button><button data-v="slim" class="${slim ? 'on' : ''}">Slim arms</button></div></div>
      <div>
        <div class="toolbar"><button class="btn pri" id="sUp">${I.up2} Upload skin</button><input class="input" id="sName" placeholder="Copy from player name…" style="max-width:240px" maxlength="16"><button class="btn" id="sLook">${I.search} Copy</button></div>
        <div class="skins" id="sLib">${data.library.length ? '' : '<div class="empty" style="grid-column:1/-1"><b>Your skin library is empty</b>Uploaded and copied skins appear here.</div>'}</div>
      </div>
    </div>`;
  const cur = await skinOf(a.uuid);
  $('#sF', el).replaceWith(drawBody(cur, slim, false, 8));
  const bk = drawBody(cur, slim, true, 6); bk.className = 'back'; $('#sB', el).replaceWith(bk);
  $$('#sVar button', el).forEach(b => b.onclick = async () => {
    if (b.classList.contains('on')) return;
    try { await api('/api/skins/apply', { id: 'current', variant: b.dataset.v }); await afterSkin(); toast('Arm style changed', 'ok'); } catch (e) { toast(e.message, 'err', 7000); }
  });
  const lib = $('#sLib', el);
  for (const s of data.library) {
    const c = document.createElement('div'); c.className = 'card sk'; c.title = 'Wear this skin';
    c.innerHTML = `<span class="cv"></span><b>${esc(s.name)}</b><div class="acts"><button class="btn sm pri" data-w>Wear</button><button class="ibtn" data-d title="Delete">${I.trash}</button></div>`;
    lib.appendChild(c);
    loadImg(`/api/skins/file?id=${encodeURIComponent(s.id)}&t=${TOKEN}`).then(img => $('.cv', c).replaceWith(drawBody(img, s.variant === 'slim', false, 4)));
    $('[data-w]', c).onclick = async e => { e.stopPropagation(); const b = e.currentTarget; b.disabled = true; b.innerHTML = '<span class="spin"></span>';
      try { await api('/api/skins/apply', { id: s.id, variant: s.variant }); await afterSkin(); toast('Skin changed. It shows in-game the next time you join a world.', 'ok', 5000); } catch (err) { b.disabled = false; b.textContent = 'Wear'; toast(err.message, 'err', 7000); } };
    $('[data-d]', c).onclick = async e => { e.stopPropagation(); if (await confirmBox('Delete this skin?', `"${s.name}" is removed from your library. The skin you're wearing doesn't change.`, 'Delete', true)) { await api('/api/skins/delete', { id: s.id }); go('skins'); } };
  }
  $('#sUp', el).onclick = () => {
    const inp = document.createElement('input'); inp.type = 'file'; inp.accept = 'image/png';
    inp.onchange = async () => {
      const f = inp.files[0]; if (!f) return;
      const m = modal(`<h2>Arm style</h2><p class="desc">Which arms does this skin use? Pick Slim for skins drawn with 3-pixel arms (like Alex).</p><div class="foot"><button class="btn" data-v="classic">Wide</button><button class="btn pri" data-v="slim">Slim</button></div>`);
      $$('[data-v]', m).forEach(b => b.onclick = async () => {
        m.close();
        try { await api(`/api/skins/upload?name=${encodeURIComponent(f.name)}&variant=${b.dataset.v}`, f, { type: 'image/png' }); toast('Added to your library', 'ok'); go('skins'); } catch (e) { toast(e.message, 'err', 7000); }
      });
    };
    inp.click();
  };
  const look = async () => {
    const n = $('#sName', el).value.trim(); if (!n) return;
    $('#sLook', el).disabled = true;
    try { await api('/api/skins/lookup', { name: n }); toast(`Copied ${n}'s skin to your library`, 'ok'); go('skins'); } catch (e) { $('#sLook', el).disabled = false; toast(e.message, 'err', 7000); }
  };
  $('#sLook', el).onclick = look; $('#sName', el).onkeydown = e => { if (e.key === 'Enter') look(); };
};
async function afterSkin() { await refreshState(); const a = activeAcc(); if (a) SKINS.delete(a.uuid); renderAcct(); go('skins'); }

// ---------- SETTINGS ----------
let SET_TAB = 'accounts';
PAGES.settings = el => {
  const tabs = [['accounts', 'Accounts'], ['appearance', 'Appearance'], ['layout', 'Layout'], ['game', 'Game'], ['launcher', 'Launcher'], ['about', 'About']];
  el.innerHTML = `<div class="ph"><div><div class="kicker">Preferences</div><h1>Settings</h1></div></div>
    <div class="set"><nav>${tabs.map(([k, n]) => `<button data-s="${k}" class="${k === SET_TAB ? 'on' : ''}">${n}</button>`).join('')}</nav><div id="setBody"></div></div>`;
  $$('[data-s]', el).forEach(b => b.onclick = () => { SET_TAB = b.dataset.s; go('settings'); });
  SETTINGS[SET_TAB]($('#setBody', el));
};
const opt = (title, sub, ctl) => `<div class="opt"><div class="t"><b>${title}</b><small>${sub}</small></div><div class="ctl">${ctl}</div></div>`;
const swHTML = (k, d) => `<span class="sw ${set(k, d) ? 'on' : ''}" data-sw="${k}" data-d="${d ? 1 : 0}"></span>`;
function wireSwitches(el, after) {
  $$('[data-sw]', el).forEach(s => s.onclick = async () => {
    const k = s.dataset.sw, v = !set(k, s.dataset.d === '1');
    s.classList.toggle('on', v);
    try { await setSetting(k, v); after && after(k, v); } catch (e) { toast(e.message, 'err'); }
  });
}
const SETTINGS = {
  accounts: async el => {
    const accs = S.accounts || [];
    el.innerHTML = `<div class="card">${accs.map(a => `<div class="opt" data-u="${esc(a.uuid)}"><span class="hh"></span><div class="t"><b>${esc(a.name)}</b><small>${a.active ? 'Playing as this account' : 'Microsoft account'}</small></div><div class="row">
      ${a.active ? '<span class="chip ac">Active</span>' : '<button class="btn sm" data-use>Use</button>'}<button class="btn sm ghost" data-rm>${I.out} Log out</button></div></div>`).join('') || '<div class="empty"><b>No accounts</b>Sign in to play.</div>'}</div>
      <div style="margin-top:14px" class="row"><button class="btn pri" id="aAdd">${MSLOGO}&nbsp; Add Microsoft account</button>${accs.length ? `<button class="btn ghost" id="aRef">${I.refresh} Refresh name &amp; skin</button>` : ''}</div>
      <p class="muted" style="font-size:12.5px;margin-top:16px">Accounts are stored encrypted on this PC only and are never sent anywhere except Microsoft and Mojang.</p>`;
    for (const r of $$('[data-u]', el)) r.querySelector('.hh').replaceWith(drawHead(await skinOf(r.dataset.u), 36));
    $$('[data-u]', el).forEach(r => {
      const a = accs.find(x => x.uuid === r.dataset.u);
      const use = $('[data-use]', r); if (use) use.onclick = async () => { await api('/api/accounts/select', { uuid: a.uuid }); await refreshState(); renderAcct(); renderDock(); go('settings'); toast('Now playing as ' + a.name, 'ok'); };
      $('[data-rm]', r).onclick = () => logout(a);
    });
    $('#aAdd', el).onclick = showLogin;
    const rf = $('#aRef', el); if (rf) rf.onclick = async () => { try { await api('/api/accounts/refresh', {}); SKINS.clear(); await refreshState(); renderAcct(); go('settings'); toast('Profile refreshed', 'ok'); } catch (e) { toast(e.message, 'err'); } };
  },
  game: el => {
    const max = Math.max(2048, Math.min(32768, (S.ramMB || 8192) - 1024));
    el.innerHTML = `<div class="card">
      ${opt('Memory (RAM)', `How much memory Minecraft may use. Your PC has ${((S.ramMB || 0) / 1024).toFixed(0)} GB. 4–6 GB is plenty for most modpacks.`, `<div class="rng" style="width:100%"><input type="range" id="gRam" min="1024" max="${max}" step="512" value="${set('ramMB', 4096)}"><output id="gRamO"></output></div>`)}
      ${opt('TopClient mod', `Adds the built-in TopClient mod${(S.topclientMod || {}).version ? ' ' + esc(S.topclientMod.version) : ''} to Fabric profiles on Minecraft ${esc((S.topclientMod || {}).mc || '')} when you press Play. Off takes it out again.`, swHTML('topclientMod', true))}
      ${opt('Smooth gameplay', 'Gives Minecraft a higher priority and keeps Windows from slowing it down while you play.', swHTML('gameSmooth', true))}
      ${opt('More than one game at once', 'Lets you start the same profile again in another window.', swHTML('multiInstance', false))}
      ${opt('Back up worlds before playing', 'Keeps a copy of your worlds every time you press Play.', swHTML('autoBackup', false))}
      ${opt('Java path', 'Leave empty and TopClient downloads the right Java for each version by itself.', `<input class="input" id="gJava" placeholder="Automatic" value="${esc(set('javaPath', ''))}">`)}
      ${opt('Extra Java arguments', 'For advanced players only.', `<input class="input" id="gArgs" placeholder="None" value="${esc(set('javaArgs', ''))}">`)}
    </div>`;
    const r = $('#gRam', el), o = $('#gRamO', el), t = () => o.textContent = (r.value / 1024).toFixed(1).replace('.0', '') + ' GB';
    t(); r.oninput = t; r.onchange = () => setSetting('ramMB', +r.value).then(() => toast('Memory saved', 'ok', 1500));
    $('#gJava', el).onchange = e => setSetting('javaPath', e.target.value.trim());
    $('#gArgs', el).onchange = e => setSetting('javaArgs', e.target.value.trim());
    wireSwitches(el);
  },
  launcher: async el => {
    el.innerHTML = `<div class="card">
      ${opt('Minimise when the game starts', 'Gets the launcher out of the way after you press Play.', swHTML('minimizeOnPlay', false))}
      ${opt('Icon next to the clock', 'Keep TopClient in the system tray for quick access.', swHTML('trayIcon', false))}
      ${opt('Minimise to the tray', 'With the tray icon on, minimising hides the window there.', swHTML('minimizeToTray', false))}
      ${opt('Update source', 'The GitHub repo TopClient checks for new versions (owner/name). Empty means no update checks.', `<input class="input" id="lRepo" placeholder="e.g. TopAmir/TopClient" value="${esc(set('updateRepo', ''))}">`)}
      ${opt('Check for updates', 'Looks for a newer TopClient release.', `<button class="btn" id="lUpd">${I.refresh} Check now</button>`)}
      ${opt('Launcher folder', 'Profiles, worlds, skins and settings live here.', `<button class="btn ghost" id="lData">${I.folder} Open folder</button>`)}
    </div>`;
    wireSwitches(el, k => { if (k === 'trayIcon') api('/api/settings', { trayIcon: set('trayIcon', false) }); });
    $('#lRepo', el).onchange = e => setSetting('updateRepo', e.target.value.trim());
    $('#lData', el).onclick = () => api('/api/open-data', {}).catch(e => toast(e.message, 'err'));
    $('#lUpd', el).onclick = async () => {
      const b = $('#lUpd', el); b.disabled = true;
      try { await updManual(); } catch (e) { toast(e.message, 'err'); }
      b.disabled = false;
    };
  },
  about: el => {
    el.innerHTML = `<div class="card" style="padding:34px;display:flex;gap:28px;align-items:center"><div style="width:92px;height:92px;flex:none">${LOGO()}</div>
      <div><div class="word"><b style="font-size:32px">TOP<span>CLIENT</span></b><small style="font-size:13px;margin-top:6px">by TopAmir_ · version ${esc(S.version || '')}</small></div>
      <p class="muted" style="margin:14px 0 0;max-width:560px">A fast Minecraft launcher. Sign-in goes only to Microsoft and Mojang, game files come from Mojang, Fabric and Modrinth only, and your accounts never leave this PC. No ads, no tracking.</p></div></div>`;
  },
};

// ---------- launcher updates ----------
// The launcher asks /api/update (the Go side caches GitHub's answer for 30 minutes, so checking
// often never hits GitHub's rate limit). ~3 s after start and then every 10 min a popup shows when
// a newer release exists. "Later" hides it for 2 hours for that version.
const UPD_SNOOZE_MS = 2 * 3600 * 1000;
const updKey = v => 'topclient.updLater.' + v;
function updSnoozed(v) { try { return Date.now() < +(localStorage.getItem(updKey(v)) || 0); } catch { return false; } }
function updSnooze(v) { try { localStorage.setItem(updKey(v), String(Date.now() + UPD_SNOOZE_MS)); } catch { /* private mode: ignore */ } }
function updMark(u) {
  const b = $('#updBtn'); if (!b) return;
  const on = !!u && u.state === 'available';
  b.classList.toggle('pri', on); b.classList.toggle('shine', on);
  b.title = on ? `Update available: v${u.current} \u2192 v${u.latest}` : 'Check for updates';
  b.innerHTML = `${I.refresh}${on ? 'Update v' + esc(u.latest) : 'Update'}`;
}
async function updCheck(force = false) {
  try { const u = await api('/api/update' + (force ? '?force=1' : '')); updMark(u); return u; } catch { return null; }
}
function updPopup(u) {
  if ($('.modal.upd')) return;
  const notes = esc((u.notes || '').trim()) || 'No release notes.';
  const m = modal(`<h2>Update available: v${esc(u.current)} \u2192 v${esc(u.latest)}</h2><pre class="updnotes">${notes}</pre><div class="foot"><button class="btn ghost" data-later>Later</button><button class="btn pri" data-now>Update now</button></div>`, 'upd');
  $('[data-later]', m).onclick = () => { updSnooze(u.latest); m.close(); };
  $('[data-now]', m).onclick = async () => {
    const now = $('[data-now]', m), later = $('[data-later]', m); now.disabled = later.disabled = true; now.textContent = 'Downloading\u2026';
    try { await api('/api/update/install', {}); toast('Updating \u2013 the launcher restarts by itself', 'ok', 10000); }
    catch (e) { toast(e.message, 'err', 6000); now.disabled = later.disabled = false; now.textContent = 'Update now'; }
  };
}
async function updAuto() {
  const u = await updCheck(false);
  if (u && u.state === 'available' && !updSnoozed(u.latest)) updPopup(u);
}
async function updManual() {
  const u = await updCheck(true);
  if (!u) return toast('Could not check for updates', 'err');
  if (u.state === 'available') updPopup(u);
  else if (u.state === 'uptodate') toast('You have the newest version', 'ok');
  else if (u.state === 'unconfigured') toast('Set an update source first (Settings \u2192 Launcher)');
  else toast(u.error || 'Could not check for updates', 'err');
}
function startUpdates() {
  const b = $('#updBtn'); if (b) { updMark(null); b.onclick = () => updManual().catch(e => toast(e.message, 'err')); }
  setTimeout(updAuto, 3000);
  setInterval(updAuto, 10 * 60 * 1000);
}

// ---------- start ----------
async function boot() {
  if (!TOKEN) { document.body.innerHTML = '<div class="empty" style="padding-top:30vh"><b>Open TopClient from TopClient.exe</b></div>'; return; }
  buildTabs(); initWindow();
  await refreshState();
  applyLook(); applyBackground();
  manifest().then(() => { renderDock(); if (PAGE === 'home') go('home', true); }).catch(() => {});
  renderAcct(); renderDock(); go(set('startPage', 'home')); poll();
  setInterval(() => api('/api/ping').catch(() => {}), 20000);
  startUpdates();
  addEventListener('keydown', e => { if (e.ctrlKey && /^[1-5]$/.test(e.key)) { e.preventDefault(); go(TABS[+e.key - 1][0]); } });
}
addEventListener('DOMContentLoaded', boot);
