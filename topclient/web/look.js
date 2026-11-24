// TopClient appearance: themes, colours, fonts, corners, layout, backgrounds and effects.
// Everything is a CSS variable or a body class, so changes apply instantly.
'use strict';

const THEMES = {
  yinyang: { name: 'Yin Yang', bg: '#000000', s1: '#0c0c0c', tx: '#f5f5f5', ac: '#ffffff', ac2: '#a8a8a8', split: true },
  yang:    { name: 'Yang (light)', bg: '#f2f2f0', s1: '#ffffff', tx: '#0a0a0a', ac: '#0a0a0a', ac2: '#4a4a4a', split: true },
  graphite:{ name: 'Graphite', bg: '#0b0c0e', s1: '#121316', tx: '#f3f2ef', ac: '#e8e8e8', ac2: '#8f939b' },
  ignition:{ name: 'Ignition', bg: '#0b0c0e', s1: '#121316', tx: '#f3f2ef', ac: '#ffb43d', ac2: '#ff4d1f' },
  crimson: { name: 'Crimson', bg: '#0c0809', s1: '#151012', tx: '#f7eef0', ac: '#ff4766', ac2: '#b0122f' },
  ocean:   { name: 'Ocean', bg: '#060b12', s1: '#0d1520', tx: '#eaf3ff', ac: '#4cc3ff', ac2: '#2a6bff' },
  forest:  { name: 'Forest', bg: '#070c09', s1: '#0e1611', tx: '#ecf6ee', ac: '#5fe08e', ac2: '#1c9a55' },
  violet:  { name: 'Violet', bg: '#0b0812', s1: '#14101e', tx: '#f2eeff', ac: '#b68cff', ac2: '#6a3dff' },
  sakura:  { name: 'Sakura', bg: '#120b10', s1: '#1c1219', tx: '#fff0f6', ac: '#ff9cc9', ac2: '#e0508f' },
  gold:    { name: 'Gold', bg: '#0a0907', s1: '#14120d', tx: '#f8f3e6', ac: '#ffd76a', ac2: '#c9962b' },
  paper:   { name: 'Paper', bg: '#eeebe4', s1: '#faf8f3', tx: '#1c1a16', ac: '#d0461f', ac2: '#8f2a10' },
};
const FONTS = {
  condensed: { name: 'Condensed', disp: "Bahnschrift, 'DIN Alternate', 'Arial Narrow', Inter, sans-serif", body: "'Segoe UI Variable Text', 'Segoe UI', Inter, system-ui, sans-serif" },
  modern:    { name: 'Modern', disp: "Inter, 'Segoe UI', system-ui, sans-serif", body: "Inter, 'Segoe UI', system-ui, sans-serif" },
  rounded:   { name: 'Rounded', disp: "Poppins, Inter, sans-serif", body: "Poppins, Inter, sans-serif" },
  mono:      { name: 'Mono', disp: "Consolas, 'Cascadia Mono', monospace", body: "'Segoe UI', Inter, sans-serif" },
};
// every setting that belongs to the look (used for reset / share codes)
const LOOK_KEYS = ['theme', 'accent', 'colors', 'font', 'corners', 'uiScale', 'animations', 'pattern', 'fx', 'bgDim', 'bgBlur', 'panelAlpha',
  'navPos', 'dockPos', 'density', 'showSkin', 'showRecent', 'showStats', 'colorIcons', 'startPage'];

// ---------- colour maths ----------
function hexRGB(h) { h = String(h || '#000').replace('#', ''); if (h.length === 3) h = h.replace(/./g, c => c + c); const n = parseInt(h.slice(0, 6), 16) || 0; return [n >> 16 & 255, n >> 8 & 255, n & 255]; }
const rgbHex = c => '#' + c.map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => { const x = hexRGB(a), y = hexRGB(b); return rgbHex(x.map((v, i) => v + (y[i] - v) * t)); };
const rgba = (h, a) => `rgba(${hexRGB(h).join(',')},${a})`;
function lum(h) { const [r, g, b] = hexRGB(h).map(v => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }); return .2126 * r + .7152 * g + .0722 * b; }

function themeColors() {
  const t = THEMES[set('theme', 'yinyang')] || THEMES.yinyang;
  const c = { ...t, ...(set('colors', null) || {}) };
  const acc = set('accent', '');
  if (acc) { c.ac = acc; c.ac2 = mix(acc, c.bg, .35); }
  return c;
}
function applyLook() {
  const c = themeColors(), r = document.documentElement.style, B = document.body;
  const light = lum(c.bg) > .4;
  const v = {
    '--bg': c.bg, '--s1': c.s1, '--s2': mix(c.s1, c.tx, .045), '--s3': mix(c.s1, c.tx, .09), '--s4': mix(c.s1, c.tx, .16),
    '--tx': c.tx, '--mut': mix(c.bg, c.tx, .56), '--dim': mix(c.bg, c.tx, .36),
    '--line': rgba(c.tx, light ? .1 : .07), '--line2': rgba(c.tx, light ? .18 : .13),
    '--ac': c.ac, '--ac2': c.ac2, '--onac': lum(mix(c.ac, c.ac2, .4)) > .38 ? '#000000' : '#ffffff',
    '--acs': rgba(c.ac, .14), '--glow': rgba(c.ac, light ? .08 : .07), '--log': mix(c.bg, '#000000', light ? .85 : .4),
  };
  const f = FONTS[set('font', 'condensed')] || FONTS.condensed;
  v['--disp'] = f.disp; v['--body'] = f.body;
  const hasBg = !!(S && S.hasBackground);
  v['--bgdim'] = (set('bgDim', 45) / 100).toFixed(2);
  v['--panel'] = set('panelAlpha', 85) + '%';
  v['--chrome'] = Math.min(100, set('panelAlpha', 85) + 5) + '%';
  v['--pblur'] = set('bgBlur', 10) + 'px';
  for (const k in v) r.setProperty(k, v[k]);
  const cls = (n, on) => B.classList.toggle(n, !!on);
  cls('split', c.split && !set('accent', '') && !set('colors', null));
  cls('corners-round', set('corners', 'angular') === 'round');
  cls('corners-square', set('corners', 'angular') === 'square');
  cls('compact', set('density', 'comfy') === 'compact');
  cls('nav-side', set('navPos', 'top') === 'side');
  cls('dock-top', set('dockPos', 'bottom') === 'top');
  cls('no-pattern', set('pattern', true) === false);
  cls('no-anim', set('animations', true) === false);
  cls('has-bg', hasBg);
  B.style.zoom = (set('uiScale', 100) / 100);
  placeNav();
  $$('[data-logo]').forEach(e => e.innerHTML = LOGO());
  FX.set(set('fx', 'off'));
}
function placeNav() {
  const tabs = $('#tabs'); if (!tabs) return;
  const side = set('navPos', 'top') === 'side';
  if (side && tabs.parentElement.id !== 'side') $('#side').appendChild(tabs);
  if (!side && tabs.parentElement.id === 'side') $('.spacer').before(tabs);
}
// the TopClient mark, coloured by the theme
function LOGO(size) {
  return `<svg class="logo-svg" viewBox="0 0 100 100" ${size ? `width="${size}" height="${size}"` : 'width="100%" height="100%"'}><defs><linearGradient id="lg${size || 0}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" style="stop-color:var(--ac)"/><stop offset="1" style="stop-color:var(--ac2)"/></linearGradient></defs><path fill="url(#lg${size || 0})" d="M6 33 50 11l44 22v12L50 25 6 45z"/><path fill="url(#lg${size || 0})" d="M41 37h18l-4 47-5 9-5-9z"/></svg>`;
}

// ---------- background picture / video ----------
let BGV = 1;
async function applyBackground() {
  const box = $('#bg'), img = $('img', box), vid = $('video', box);
  img.style.display = vid.style.display = 'none';
  if (!S.hasBackground) { vid.removeAttribute('src'); applyLook(); return; }
  const url = `/api/background?v=${BGV}&t=${TOKEN}`;
  const r = await fetch(url, { method: 'HEAD', headers: { 'X-Token': TOKEN } }).catch(() => null);
  const ct = (r && r.headers.get('content-type')) || '';
  if (ct.startsWith('video')) { vid.src = url; vid.style.display = 'block'; vid.play().catch(() => {}); }
  else { img.src = url; img.style.display = 'block'; }
  applyLook();
}

// ---------- light ambient effects (paused while the window is hidden) ----------
const FX = (() => {
  const c = $('#fx'), g = c.getContext('2d');
  let kind = 'off', parts = [], raf = 0, W = 0, H = 0;
  const KINDS = { off: 'Off', stars: 'Stars', snow: 'Snow', dust: 'Dust', rain: 'Rain' };
  const size = () => { W = c.width = innerWidth; H = c.height = innerHeight; };
  const R = (a, b) => a + Math.random() * (b - a);
  function spawn(fresh) {
    const n = { stars: 120, snow: 110, dust: 60, rain: 140 }[kind] || 0;
    parts = Array.from({ length: n }, () => ({ x: R(0, W), y: fresh ? R(0, H) : R(-H, 0), s: kind === 'rain' ? R(10, 22) : kind === 'snow' ? R(1, 3.6) : R(.5, 1.8), v: kind === 'rain' ? R(9, 15) : kind === 'snow' ? R(.4, 1.3) : kind === 'dust' ? R(-.25, .25) : 0, p: R(0, 6.3), a: R(.2, .9) }));
  }
  function frame(t) {
    raf = requestAnimationFrame(frame);
    if (document.hidden) return;
    g.clearRect(0, 0, W, H);
    const col = getComputedStyle(document.documentElement).getPropertyValue('--tx').trim() || '#fff';
    g.fillStyle = col; g.strokeStyle = col;
    for (const q of parts) {
      if (kind === 'stars') { g.globalAlpha = q.a * (.55 + .45 * Math.sin(t / 700 + q.p)); g.fillRect(q.x, q.y, q.s, q.s); continue; }
      if (kind === 'rain') { q.y += q.v; q.x -= q.v * .15; if (q.y > H) { q.y = R(-40, 0); q.x = R(0, W + 100); } g.globalAlpha = .22; g.lineWidth = 1; g.beginPath(); g.moveTo(q.x, q.y); g.lineTo(q.x + q.s * .15, q.y - q.s); g.stroke(); continue; }
      if (kind === 'snow') { q.y += q.v; q.x += Math.sin(t / 1400 + q.p) * .4; if (q.y > H + 5) { q.y = -5; q.x = R(0, W); } }
      if (kind === 'dust') { q.x += q.v; q.y += Math.sin(t / 2000 + q.p) * .15 - .05; if (q.x < -5) q.x = W + 5; if (q.x > W + 5) q.x = -5; if (q.y < -5) q.y = H + 5; }
      g.globalAlpha = q.a * (kind === 'dust' ? .35 : .7); g.beginPath(); g.arc(q.x, q.y, q.s, 0, 6.3); g.fill();
    }
    g.globalAlpha = 1;
  }
  addEventListener('resize', () => { if (kind !== 'off') { size(); spawn(true); } });
  return {
    KINDS,
    set(k) {
      if (set('animations', true) === false) k = 'off';
      if (k === kind) return; kind = KINDS[k] ? k : 'off';
      cancelAnimationFrame(raf); g.clearRect(0, 0, W, H); c.style.display = kind === 'off' ? 'none' : 'block';
      if (kind !== 'off') { size(); spawn(true); raf = requestAnimationFrame(frame); }
    },
  };
})();

// ---------- Settings → Appearance and Layout ----------
async function setLook(k, v) { S.settings[k] = v; applyLook(); await api('/api/settings', { [k]: v }).catch(e => toast(e.message, 'err')); }
function segHTML(key, def, opts) { const cur = set(key, def); return `<div class="seg" data-seg="${key}" data-def="${esc(def)}">${Object.entries(opts).map(([k, n]) => `<button data-v="${esc(k)}" class="${String(cur) === k ? 'on' : ''}">${esc(n)}</button>`).join('')}</div>`; }
function wireSegs(el, after) {
  $$('[data-seg]', el).forEach(sg => $$('button', sg).forEach(b => b.onclick = async () => {
    $$('button', sg).forEach(x => x.classList.toggle('on', x === b));
    await setLook(sg.dataset.seg, b.dataset.v); after && after(sg.dataset.seg);
  }));
}
function rangeHTML(key, def, min, max, unit = '%', step = 1) { return `<div class="rng" style="width:100%"><input type="range" data-rng="${key}" min="${min}" max="${max}" step="${step}" value="${set(key, def)}"><output>${set(key, def)}${unit}</output></div>`; }
function wireRanges(el) {
  $$('[data-rng]', el).forEach(r => {
    const o = r.nextElementSibling, unit = o.textContent.replace(/^[\d.]+/, '');
    r.oninput = () => { o.textContent = r.value + unit; S.settings[r.dataset.rng] = +r.value; applyLook(); };
    r.onchange = () => setLook(r.dataset.rng, +r.value);
  });
}
function lookSwitches(el) {
  $$('[data-lsw]', el).forEach(s => s.onclick = async () => {
    const k = s.dataset.lsw, v = !set(k, s.dataset.d === '1'); s.classList.toggle('on', v); await setLook(k, v);
  });
}
const lsw = (k, d) => `<span class="sw ${set(k, d) ? 'on' : ''}" data-lsw="${k}" data-d="${d ? 1 : 0}"></span>`;

SETTINGS.appearance = el => {
  const c = themeColors(), cur = set('theme', 'yinyang');
  el.innerHTML = `<div class="card">
    <div class="subh">Theme</div>
    <div class="themes">${Object.entries(THEMES).map(([k, t]) => `<button class="th ${k === cur ? 'on' : ''}" data-th="${k}"><div class="pv"><i style="background:${t.bg}"></i><i style="background:${t.split ? t.tx : t.s1}"></i><b style="background:linear-gradient(115deg,${t.ac},${t.ac2})"></b></div><span>${esc(t.name)}</span></button>`).join('')}</div>
    <div class="subh">Colours</div>
    ${opt('Custom colours', 'Change any colour of the current theme. Reset brings the theme back.', `<div class="colors">
      <label class="cpk"><input type="color" data-col="ac" value="${c.ac}">Accent</label><label class="cpk"><input type="color" data-col="ac2" value="${c.ac2}">Accent 2</label>
      <label class="cpk"><input type="color" data-col="bg" value="${c.bg}">Background</label><label class="cpk"><input type="color" data-col="s1" value="${c.s1}">Panels</label>
      <label class="cpk"><input type="color" data-col="tx" value="${c.tx}">Text</label></div>`)}
    ${opt('', '', `<button class="btn ghost sm" id="apReset">${I.refresh} Reset colours</button>`)}
    ${opt('Colourful profile icons', 'Give every profile its own colour instead of the theme colour.', lsw('colorIcons', false))}
    <div class="subh">Text &amp; shape</div>
    ${opt('Font', 'The style of headings and text.', segHTML('font', 'condensed', Object.fromEntries(Object.entries(FONTS).map(([k, f]) => [k, f.name]))))}
    ${opt('Corners', 'Sharp cuts, soft rounded corners or plain squares.', segHTML('corners', 'angular', { angular: 'Angular', round: 'Rounded', square: 'Square' }))}
    ${opt('Interface size', 'Makes everything bigger or smaller.', rangeHTML('uiScale', 100, 80, 125))}
    ${opt('Animations', 'Page transitions and moving effects.', lsw('animations', true))}
    <div class="subh">Background</div>
    ${opt('Custom background', 'A picture, GIF or video (MP4, WEBM) behind the launcher. Stays on this PC.', `<div class="row"><button class="btn pri sm" id="bgUp">${I.up2} Choose file</button>${S.hasBackground ? `<button class="btn ghost sm" id="bgRm">${I.trash} Remove</button>` : ''}</div>`)}
    ${opt('Background dimming', 'Darkens (or lightens) the background so text stays readable.', rangeHTML('bgDim', 45, 0, 90))}
    ${opt('Panel opacity', 'How see-through the panels are over a custom background.', rangeHTML('panelAlpha', 85, 30, 100))}
    ${opt('Panel blur', 'Frosted-glass blur behind panels.', rangeHTML('bgBlur', 10, 0, 30, 'px'))}
    ${opt('Pattern', 'The thin diagonal lines and glow behind everything.', lsw('pattern', true))}
    ${opt('Effects', 'Light ambient particles. They pause when the window is hidden.', segHTML('fx', 'off', FX.KINDS))}
    <div class="subh">Share</div>
    ${opt('Theme code', 'Copy your whole look as a code, or paste a friend\'s code.', `<div class="row"><button class="btn sm" id="apCopy">Copy code</button><button class="btn sm ghost" id="apPaste">Paste code</button><button class="btn sm ghost danger" id="apAll">Reset everything</button></div>`)}
  </div>`;
  $$('[data-th]', el).forEach(b => b.onclick = async () => {
    S.settings.accent = ''; S.settings.colors = null;
    await api('/api/settings', { accent: null, colors: null });
    await setLook('theme', b.dataset.th); go('settings');
  });
  $$('[data-col]', el).forEach(inp => {
    inp.oninput = () => { const cols = { ...(set('colors', null) || {}) }; cols[inp.dataset.col] = inp.value; S.settings.colors = cols; if (inp.dataset.col.startsWith('ac')) S.settings.accent = ''; applyLook(); };
    inp.onchange = () => api('/api/settings', { colors: S.settings.colors, accent: null }).catch(() => {});
  });
  $('#apReset', el).onclick = async () => { S.settings.colors = null; S.settings.accent = ''; await api('/api/settings', { colors: null, accent: null }); applyLook(); go('settings'); };
  wireSegs(el); wireRanges(el); lookSwitches(el);
  $('[data-lsw="colorIcons"]', el).addEventListener('click', () => setTimeout(renderDock, 50));
  $('#bgUp', el).onclick = () => {
    const inp = document.createElement('input'); inp.type = 'file'; inp.accept = 'image/*,video/mp4,video/webm,video/quicktime,.mkv';
    inp.onchange = async () => {
      const f = inp.files[0]; if (!f) return;
      toast('Setting background…');
      try { await api('/api/background', f, { type: f.type || 'application/octet-stream' }); S.hasBackground = true; BGV++; await applyBackground(); go('settings'); toast('Background set', 'ok'); }
      catch (e) { toast(e.message, 'err', 7000); }
    };
    inp.click();
  };
  const rm = $('#bgRm', el); if (rm) rm.onclick = async () => { await api('/api/background/clear', {}); S.hasBackground = false; await applyBackground(); go('settings'); };
  $('#apCopy', el).onclick = () => {
    const o = {}; LOOK_KEYS.forEach(k => { if (k in S.settings) o[k] = S.settings[k]; });
    navigator.clipboard.writeText('TOPCLIENT:' + btoa(unescape(encodeURIComponent(JSON.stringify(o))))).then(() => toast('Theme code copied', 'ok'));
  };
  $('#apPaste', el).onclick = () => {
    const m = modal(`<h2>Paste theme code</h2><p class="desc">Only colours and layout are changed. Accounts, profiles and backgrounds are never part of a code.</p><textarea class="input" id="tcCode" placeholder="TOPCLIENT:…"></textarea><div class="foot"><button class="btn ghost" data-x>Cancel</button><button class="btn pri" id="tcGo">Apply</button></div>`);
    $('#tcGo', m).onclick = async () => {
      try {
        const raw = $('#tcCode', m).value.trim().replace(/^TOPCLIENT:/, '');
        const o = JSON.parse(decodeURIComponent(escape(atob(raw)))), clean = {};
        for (const k of LOOK_KEYS) if (k in o && (typeof o[k] !== 'object' || o[k] === null || k === 'colors')) clean[k] = o[k];
        if (clean.colors) for (const [ck, cv] of Object.entries(clean.colors)) if (!/^#[0-9a-f]{6}$/i.test(cv) || !['ac', 'ac2', 'bg', 's1', 'tx'].includes(ck)) delete clean.colors[ck];
        Object.assign(S.settings, clean); await api('/api/settings', clean); m.close(); applyLook(); go('settings'); toast('Theme applied', 'ok');
      } catch { toast('That code doesn\'t look right', 'err'); }
    };
  };
  $('#apAll', el).onclick = async () => {
    if (!(await confirmBox('Reset the whole look?', 'Theme, colours, fonts, layout and effects go back to the black and white default. Your background picture stays.', 'Reset', true))) return;
    const o = {}; LOOK_KEYS.forEach(k => { o[k] = null; delete S.settings[k]; });
    await api('/api/settings', o); applyLook(); go('settings');
  };
};

SETTINGS.layout = el => {
  el.innerHTML = `<div class="card">
    ${opt('Navigation', 'Page tabs across the top, or down the left side.', segHTML('navPos', 'top', { top: 'Top bar', side: 'Side bar' }))}
    ${opt('Play bar', 'Where the profile picker and PLAY button sit.', segHTML('dockPos', 'bottom', { bottom: 'Bottom', top: 'Top' }))}
    ${opt('Density', 'Compact fits more on the screen.', segHTML('density', 'comfy', { comfy: 'Comfortable', compact: 'Compact' }))}
    ${opt('Start page', 'The page TopClient opens on.', segHTML('startPage', 'home', { home: 'Play', profiles: 'Profiles', mods: 'Mods', skins: 'Skins' }))}
    <div class="subh">Play page</div>
    ${opt('Show my skin', 'Your character next to the profile name.', lsw('showSkin', true))}
    ${opt('Show "Jump back in"', 'Your recently played profiles.', lsw('showRecent', true))}
    ${opt('Show stats', 'Play time, profiles and accounts.', lsw('showStats', true))}
  </div>`;
  wireSegs(el); lookSwitches(el);
};
