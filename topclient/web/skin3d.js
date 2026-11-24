// TopClient — dependency-free 3D Minecraft skin renderer (CSS 3D), shared with AfrikaClient.
'use strict';

const SkinGen = {
  // Original default character drawn in code (used when logged out).
  defaultSkin() {
    const c = document.createElement('canvas'); c.width = 64; c.height = 64;
    const g = c.getContext('2d', { willReadFrequently: true });
    const R = (x, y, w, h, col) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
    const skin = '#7a4a2e', skinD = '#653a22', hair = '#1b1210', hood = '#ff7a1a', hoodD = '#d9560c', pants = '#2a2733', pantsD = '#1d1b24', shoe = '#121114';
    // head (0,0) 8x8x8 faces
    R(0, 8, 32, 8, skin); R(8, 0, 16, 8, hair);
    R(0, 8, 32, 3, hair); R(8, 8, 8, 2, hair);
    R(9, 12, 2, 1, '#fff'); R(13, 12, 2, 1, '#fff'); R(10, 12, 1, 1, '#2b1b12'); R(13, 12, 1, 1, '#2b1b12');
    R(11, 14, 2, 1, skinD); R(10, 15, 4, 1, '#4b2616');
    R(24, 8, 8, 8, hair);
    // body (16,16) 8x12x4
    R(16, 20, 24, 12, hood); R(20, 16, 16, 4, hood);
    R(23, 20, 2, 5, '#fff3'); R(20, 26, 8, 3, hoodD); R(22, 20, 4, 1, hoodD);
    // right arm (40,16)
    R(40, 20, 16, 12, hood); R(44, 16, 8, 4, hood); R(40, 29, 16, 3, skin); R(48, 16, 4, 4, skin);
    // right leg (0,16)
    R(0, 20, 16, 12, pants); R(4, 16, 8, 4, pants); R(0, 29, 16, 3, shoe); R(8, 16, 4, 4, shoe); R(4, 20, 4, 1, pantsD);
    // left leg (16,48) & left arm (32,48)
    R(16, 52, 16, 12, pants); R(20, 48, 8, 4, pants); R(16, 61, 16, 3, shoe); R(24, 48, 4, 4, shoe);
    R(32, 52, 16, 12, hood); R(36, 48, 8, 4, hood); R(32, 61, 16, 3, skin); R(40, 48, 4, 4, skin);
    // hood overlay (32,0)
    R(40, 0, 8, 8, hood);                       // top
    R(56, 8, 8, 8, hood);                       // back
    R(32, 8, 7, 8, hood); R(49, 8, 7, 8, hood); // sides (open at the face)
    R(40, 8, 8, 2, hood); R(40, 10, 1, 4, hood); R(47, 10, 1, 4, hood); // front rim
    R(41, 9, 6, 1, hoodD);
    return c;
  }
};

class SkinViewer {
  constructor(el, opts = {}) {
    this.el = el;
    this.opts = Object.assign({ scale: 9, pose: 'hero', yaw: -22, pitch: -6, autoSpin: false, drag: true, nameplate: '' }, opts);
    this.s = this.opts.scale;
    this.yaw = this.opts.yaw; this.pitch = this.opts.pitch;
    this.parts = {}; this.cur = {}; this.t0 = performance.now();
    this.dragging = false; this.lastInteract = 0;
    el.classList.add('sv-stage');
    this.scene = document.createElement('div'); this.scene.className = 'sv-scene';
    this.root = document.createElement('div'); this.root.className = 'sv-root';
    this.scene.appendChild(this.root); el.appendChild(this.scene);
    this.shadow = document.createElement('div'); this.shadow.className = 'sv-shadow'; el.appendChild(this.shadow);
    if (this.opts.drag) this._bindDrag();
    this._loop = this._loop.bind(this);
    this.visible = true;
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(e => { this.visible = e[0].isIntersecting; }).observe(el);
    }
    SkinViewer._add(this);
  }

  _bindDrag() {
    let sx = 0, sy = 0, syaw = 0, spitch = 0;
    this.el.addEventListener('pointerdown', e => {
      this.dragging = true; sx = e.clientX; sy = e.clientY; syaw = this.yaw; spitch = this.pitch;
      this.el.setPointerCapture(e.pointerId); this.el.classList.add('grabbing');
    });
    this.el.addEventListener('pointermove', e => {
      if (!this.dragging) return;
      this.yaw = syaw + (e.clientX - sx) * 0.6;
      this.pitch = Math.max(-35, Math.min(25, spitch - (e.clientY - sy) * 0.3));
    });
    const up = () => { this.dragging = false; this.lastInteract = performance.now(); this.el.classList.remove('grabbing'); };
    this.el.addEventListener('pointerup', up); this.el.addEventListener('pointercancel', up);
  }

  async load(skinSrc, capeSrc) {
    const skin = skinSrc ? await SkinViewer.img(skinSrc).catch(() => null) : null;
    const NC = SkinViewer._norm || (SkinViewer._norm = new Map());
    let tex = skinSrc && NC.get(skinSrc);
    if (tex && SkinViewer.lost(tex)) { NC.clear(); SkinViewer._crops && SkinViewer._crops.clear(); tex = null; }
    if (!tex) { tex = SkinViewer.normalize(skin || SkinGen.defaultSkin()); if (skinSrc && skin) { if (NC.size > 40) NC.clear(); NC.set(skinSrc, tex); } }
    const slim = this.opts.slim != null ? this.opts.slim : SkinViewer.isSlim(tex);
    let cape = null;
    if (capeSrc) cape = await SkinViewer.img(capeSrc).catch(() => null);
    this._build(tex, slim, cape);
  }

  async setCape(src) {
    this.capeImg = src ? await SkinViewer.img(src).catch(() => null) : null;
    this._buildCape();
  }

  static img(src) {
    return new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
  }

  // Convert any skin to a 64x64 canvas (legacy 64x32 skins get mirrored limbs).
  static normalize(img) {
    const c = document.createElement('canvas'); c.width = 64; c.height = 64;
    // willReadFrequently keeps it in normal memory: graphics-card canvases can be wiped
    // by the browser while the launcher sits in the background (skins went invisible).
    const g = c.getContext('2d', { willReadFrequently: true }); g.imageSmoothingEnabled = false;
    const k = img.width / 64;
    if (Math.abs(img.height / img.width - 0.5) < 0.01) {
      g.drawImage(img, 0, 0, img.width, img.height, 0, 0, 64, 32);
      const flip = (sx, sy, w, h, dx, dy) => {
        g.save(); g.translate(dx + w, dy); g.scale(-1, 1);
        g.drawImage(c, sx, sy, w, h, 0, 0, w, h); g.restore();
      };
      const limb = (u, v, du, dv) => {
        flip(u + 4, v, 4, 4, du + 4, dv); flip(u + 8, v, 4, 4, du + 8, dv);
        flip(u + 8, v + 4, 4, 12, du, dv + 4); flip(u + 4, v + 4, 4, 12, du + 4, dv + 4);
        flip(u, v + 4, 4, 12, du + 8, dv + 4); flip(u + 12, v + 4, 4, 12, du + 12, dv + 4);
      };
      limb(0, 16, 16, 48); limb(40, 16, 32, 48);
    } else {
      g.drawImage(img, 0, 0, img.width, img.height, 0, 0, 64, 64);
    }
    void k; return c;
  }

  // True when a canvas's pixels were thrown away by the browser (context lost).
  static lost(c) {
    try { if (!c || !c.getContext) return false; const g = c.getContext('2d'); return !g || (g.isContextLost ? g.isContextLost() : false); } catch (e) { return true; }
  }
  // Forget every cached skin picture (rebuilt on the next draw).
  static flush() { SkinViewer._norm && SkinViewer._norm.clear(); SkinViewer._crops && SkinViewer._crops.clear(); }

  static isSlim(c) {
    const a = c.getContext('2d').getImageData(54, 20, 2, 12).data;
    for (let i = 3; i < a.length; i += 4) if (a[i] > 0) return false;
    return true;
  }

  // Cut one face texture out of a canvas; returns dataURL or null if empty.
  static crop(src, x, y, w, h, scale = 1) {
    const key = src._ck || (src._ck = 'k' + (SkinViewer._ckn = (SkinViewer._ckn || 0) + 1));
    const ck = key + '|' + x + '|' + y + '|' + w + '|' + h + '|' + scale;
    const C = SkinViewer._crops || (SkinViewer._crops = new Map());
    if (C.has(ck)) return C.get(ck);
    const out = SkinViewer._crop(src, x, y, w, h, scale);
    if (out === undefined) return null; // source was wiped: don't remember a blank face
    if (C.size > 4000) C.clear();
    C.set(ck, out); return out;
  }
  static _crop(src, x, y, w, h, scale = 1) {
    if (SkinViewer.lost(src)) { SkinViewer._sawLost = true; return undefined; }
    const c = document.createElement('canvas'); c.width = Math.max(1, w * scale); c.height = Math.max(1, h * scale);
    const g = c.getContext('2d', { willReadFrequently: true }); g.imageSmoothingEnabled = false;
    g.drawImage(src, x * scale, y * scale, w * scale, h * scale, 0, 0, w * scale, h * scale);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    let any = false; for (let i = 3; i < d.length; i += 4) if (d[i] > 8) { any = true; break; }
    return any ? c.toDataURL() : null;
  }

  // Build a textured cuboid. uv = [u,v], dims = [w,h,d] in texture px.
  _box(tex, uv, dims, center, inflate = 0, texScale = 1, cls = '') {
    const s = this.s, [u, v] = uv, [w, h, d] = dims;
    const W = (w + inflate * 2) * s, H = (h + inflate * 2) * s, D = (d + inflate * 2) * s;
    const [cx, cy, cz] = center.map(n => n * s);
    const box = document.createElement('div'); box.className = 'sv-box ' + cls;
    const faces = {
      front: [u + d, v + d, w, h, W, H, `translate3d(${cx - W / 2}px,${cy - H / 2}px,${cz + D / 2}px)`],
      back: [u + d + w + d, v + d, w, h, W, H, `translate3d(${cx - W / 2}px,${cy - H / 2}px,${cz - D / 2}px) rotateY(180deg)`],
      right: [u, v + d, d, h, D, H, `translate3d(${cx - W / 2 - D / 2}px,${cy - H / 2}px,${cz}px) rotateY(-90deg)`],
      left: [u + d + w, v + d, d, h, D, H, `translate3d(${cx + W / 2 - D / 2}px,${cy - H / 2}px,${cz}px) rotateY(90deg)`],
      top: [u + d, v, w, d, W, D, `translate3d(${cx - W / 2}px,${cy - H / 2 - D / 2}px,${cz}px) rotateX(90deg)`],
      bottom: [u + d + w, v, w, d, W, D, `translate3d(${cx - W / 2}px,${cy + H / 2 - D / 2}px,${cz}px) rotateX(-90deg)`],
    };
    for (const [name, [x, y, tw, th, pw, ph, tf]] of Object.entries(faces)) {
      const url = SkinViewer.crop(tex, x, y, tw, th, texScale);
      if (!url) continue;
      const f = document.createElement('div'); f.className = 'sv-face sv-' + name;
      f.style.width = pw + 'px'; f.style.height = ph + 'px';
      f.style.backgroundImage = `url(${url})`; f.style.transform = tf;
      box.appendChild(f);
    }
    return box;
  }

  _part(name, pivot) {
    const p = document.createElement('div'); p.className = 'sv-part sv-p-' + name;
    p._pivot = pivot; this.root.appendChild(p); this.parts[name] = p; return p;
  }

  _build(tex, slim, capeImg) {
    this.root.innerHTML = ''; this.parts = {};
    this.tex = tex; this.slim = slim;
    const aw = slim ? 3 : 4;
    const head = this._part('head', [0, 0, 0]);
    head.append(this._box(tex, [0, 0], [8, 8, 8], [0, -4, 0]), this._box(tex, [32, 0], [8, 8, 8], [0, -4, 0], 0.5, 1, 'ov'));
    const body = this._part('body', [0, 0, 0]);
    body.append(this._box(tex, [16, 16], [8, 12, 4], [0, 6, 0]), this._box(tex, [16, 32], [8, 12, 4], [0, 6, 0], 0.25, 1, 'ov'));
    // Arm boxes: right spans x -8..-4 (slim -7..-4), left 4..8 (slim 4..7).
    const rc = 1 - aw / 2, lc = aw / 2 - 1;
    const ra = this._part('rarm', [-5, 2, 0]);
    ra.append(this._box(tex, [40, 16], [aw, 12, 4], [rc, 4, 0]), this._box(tex, [40, 32], [aw, 12, 4], [rc, 4, 0], 0.25, 1, 'ov'));
    const la = this._part('larm', [5, 2, 0]);
    la.append(this._box(tex, [32, 48], [aw, 12, 4], [lc, 4, 0]), this._box(tex, [48, 48], [aw, 12, 4], [lc, 4, 0], 0.25, 1, 'ov'));
    const rl = this._part('rleg', [-1.95, 12, 0]);
    rl.append(this._box(tex, [0, 16], [4, 12, 4], [0, 6, 0]), this._box(tex, [0, 32], [4, 12, 4], [0, 6, 0], 0.25, 1, 'ov'));
    const ll = this._part('lleg', [1.95, 12, 0]);
    ll.append(this._box(tex, [16, 48], [4, 12, 4], [0, 6, 0]), this._box(tex, [0, 48], [4, 12, 4], [0, 6, 0], 0.25, 1, 'ov'));
    if (this.opts.nameplate) {
      const n = document.createElement('div'); n.className = 'sv-nameplate'; n.textContent = this.opts.nameplate;
      this.root.appendChild(n); this.nameplate = n;
    }
    this.capeImg = capeImg; this._buildCape();
    if ((this._hatId || this._cos) && window.Hats) Hats.apply(this);
  }

  _buildCape() {
    if (this.parts.cape) { this.parts.cape.remove(); delete this.parts.cape; }
    this._capeAnim = null;
    if (!this.capeImg) return;
    const img = this.capeImg, k = img.width / 64, fh = img.width / 2;
    const n = img.height >= fh * 2 ? Math.floor(img.height / fh) : 1;   // animated capes are a strip of frames
    const frameCanvas = f => {
      const c = document.createElement('canvas'); c.width = img.width; c.height = fh;
      const g = c.getContext('2d', { willReadFrequently: true }); g.imageSmoothingEnabled = false;
      g.drawImage(img, 0, f * fh, img.width, fh, 0, 0, img.width, fh); return c;
    };
    const cape = this._part('cape', [0, 0, -2]);
    // Every frame is built once as its own box; animating only flips which box is visible
    // (swapping pictures each frame made the browser decode them again and again).
    const boxes = [];
    for (let f = 0; f < n; f++) {
      const b = this._box(frameCanvas(f), [0, 0], [10, 16, 1], [0, 8, 0.5], 0, k, 'svcape');
      if (f) b.style.visibility = 'hidden';
      cape.appendChild(b); boxes.push(b);
    }
    if (n > 1) this._capeAnim = { boxes, n, last: 0 };
  }
  _tickCape() {
    const a = this._capeAnim; if (!a) return;
    const f = Math.floor(Date.now() / 100) % a.n; if (f === a.last) return;
    a.boxes[a.last].style.visibility = 'hidden'; a.boxes[f].style.visibility = ''; a.last = f;
  }

  setNameplate(text) {
    this.opts.nameplate = text;
    if (this.nameplate) this.nameplate.textContent = text;
  }

  // ---- poses -------------------------------------------------------------
  _pose(t) {
    const P = { head: [0, 0, 0], body: [0, 0, 0], rarm: [0, 0, 0], larm: [0, 0, 0], rleg: [0, 0, 0], lleg: [0, 0, 0], cape: [6, 0, 0], bob: 0 };
    const breathe = Math.sin(t * 1.6);
    if (this.opts.pose === 'intro') {
      // Spin in with arms out, a little hop, then a greeting wave.
      const e = Math.min(1, t / 1.8);
      if (t < 1.8) {
        P.rarm = [0, 0, 70 * (1 - e) + 12]; P.larm = [0, 0, -70 * (1 - e) - 12];
        P.cape = [30 * (1 - e) + 8, 0, 0];
        P.head = [-8, 0, 0];
      } else if (t < 2.4) {
        const k = (t - 1.8) / 0.6, hop = Math.sin(k * Math.PI);
        P.bob = -hop * 2.2;
        P.rleg = [hop * 18, 0, 0]; P.lleg = [-hop * 18, 0, 0];
        P.rarm = [-hop * 30, 0, 20]; P.larm = [-hop * 30, 0, -20];
        P.cape = [8 + hop * 20, 0, 0];
      } else {
        this.waving = true;
        P.head = [-4, 6 * Math.sin(t * 1.2), 6];
        P.rarm = [-12, 0, 158 + Math.sin(t * 9) * 14];
        P.larm = [-6, 0, -16]; P.rleg = [-2, 0, 5]; P.lleg = [3, 0, -4];
        P.cape = [6 + Math.sin(t * 2.1) * 1.5, 0, 0];
        P.bob = Math.sin(t * 1.6) * .25;
      }
      return P;
    }
    if (this.opts.pose === 'hero') {
      // Greeting wave for ~3.2s every 11s, otherwise a confident stance.
      const cycle = t % 11, waving = !this.opts.noWave && cycle < 3.2;
      this.waving = waving;
      P.head = [-4 + breathe * 1.5, 10 * Math.sin(t * 0.5), waving ? 6 : 2];
      P.body = [0, 0, 0];
      P.larm = [-6, 0, -16 - breathe * 2];             // relaxed, slightly out
      P.rleg = [-2, 0, 5]; P.lleg = [3, 0, -4];         // wide stance
      if (waving) {
        const w = Math.sin(t * 9);
        P.rarm = [-12, 0, 158 + w * 14];
      } else {
        P.rarm = [8, 0, 14 + breathe * 2];
      }
      P.cape = [6 + Math.sin(t * 2.1) * 1.5, 0, 0];
      P.bob = breathe * 0.25;
    } else if (this.opts.pose === 'showcase') {
      const sw = Math.sin(t * 2.4);
      P.head = [-3, 8 * Math.sin(t * 0.6), 0];
      P.rarm = [sw * 18, 0, 6]; P.larm = [-sw * 18, 0, -6];
      P.rleg = [-sw * 16, 0, 1]; P.lleg = [sw * 16, 0, -1];
      P.cape = [16 + Math.sin(t * 2.6) * 8 + Math.abs(sw) * 4, 0, Math.sin(t * 1.3) * 2];
      P.bob = Math.abs(sw) * -0.35;
    } else if (this.opts.pose === 'display') {
      // Calm, symmetrical stance so the cape hangs straight and centred.
      P.head = [breathe * .6, 0, 0];
      P.rarm = [0, 0, 3]; P.larm = [0, 0, -3];
      P.rleg = [0, 0, 0]; P.lleg = [0, 0, 0];
      P.cape = [6 + Math.sin(t * 1.4) * 1.2, 0, 0];
    } else {
      P.head = [breathe * 2, 0, 0];
      P.rarm = [0, 0, 4 + breathe * 2]; P.larm = [0, 0, -4 - breathe * 2];
      P.cape = [8 + Math.sin(t * 2) * 3, 0, 0];
    }
    return P;
  }

  // One animation loop for every viewer on the page (cheaper than one each).
  static _add(v) {
    (SkinViewer._all || (SkinViewer._all = new Set())).add(v);
    if (!SkinViewer._raf) SkinViewer._raf = requestAnimationFrame(SkinViewer._tick);
  }
  static _tick(now) {
    SkinViewer._raf = 0;
    for (const v of SkinViewer._all) {
      if (!v.el.isConnected) { if (v._seen) { SkinViewer._all.delete(v); continue; } } else v._seen = true;
      v._loop(now);
    }
    if (SkinViewer._all.size) SkinViewer._raf = requestAnimationFrame(SkinViewer._tick);
  }

  _loop(now) {
    if (!this.visible || !this.parts.head || (window.__idle && window.__idle())) return;
    this._tickCape();
    // Full speed while you drag it (and just after); otherwise 30 fps: the idle sway looks the same.
    const busy = this.dragging || now - this.lastInteract < 2000 || this.opts.pose === 'intro';
    const gap = busy ? 0 : 30; // 60 fps while dragging, otherwise 30
    if (gap && now - (this._lastDraw || 0) < gap) return;
    this._lastDraw = now;
    if (this.opts.still) { if (this._stillDone > 2) return; this._stillDone = (this._stillDone || 0) + 1; }
    const t = (now - this.t0) / 1000;
    const P = this._pose(t);
    const s = this.s;
    for (const name of ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg', 'cape']) {
      const el = this.parts[name]; if (!el) continue;
      const tgt = P[name]; const c = this.cur[name] || (this.cur[name] = tgt.slice());
      for (let i = 0; i < 3; i++) c[i] += (tgt[i] - c[i]) * 0.12;
      const [x, y, z] = el._pivot;
      const extra = name === 'cape' ? ' rotateY(180deg)' : '';
      const tf = `translate3d(${x * s}px,${(y + P.bob) * s}px,${z * s}px)${extra} rotateY(${c[1].toFixed(2)}deg) rotateX(${(name === 'cape' ? c[0] : -c[0]).toFixed(2)}deg) rotateZ(${c[2].toFixed(2)}deg)`;
      if (el._tf !== tf) { el._tf = tf; el.style.transform = tf; } // unchanged: no restyle
    }
    if (this.opts.pose === 'intro') {
      const e = Math.min(1, t / 1.8), ease = 1 - Math.pow(1 - e, 3);
      this.yaw = 712 * (1 - ease) - 8;
    } else if (this.opts.autoSpin && !this.dragging && now - this.lastInteract > 2500) this.yaw += 0.35;
    else if (this.opts.pose === 'hero' && !this.dragging && now - this.lastInteract > 2500) {
      const base = typeof this.opts.baseYaw === 'number' ? this.opts.baseYaw : -8;
      this.yaw += ((base + Math.sin(t * 0.35 + (this.opts.phase || 0)) * 5) - this.yaw) * 0.02;
    }
    const st = `translate(-50%,-50%) rotateX(${this.pitch}deg) rotateY(${this.yaw.toFixed(2)}deg)`;
    if (this._st !== st) { this._st = st; this.scene.style.transform = st; }
    if (!this._rootSet) { this.root.style.transform = `translate3d(0,${-8 * s}px,0)`; this._rootSet = true; }
    if (!this._shadowSet) { this.shadow.style.bottom = 'auto'; this.shadow.style.top = `calc(50% + ${16 * s - 12}px)`; this._shadowSet = true; }
    if (this.nameplate) this.nameplate.style.transform = `translate3d(-50%,${-8 * s - 30}px,0) rotateY(${-this.yaw}deg)`;
  }

  faceYaw(y) { this.yaw = y; this.lastInteract = performance.now(); }
}

window.SkinViewer = SkinViewer;
window.SkinGen = SkinGen;
