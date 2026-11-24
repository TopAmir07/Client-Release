package dev.topclient.client.clover.spotify.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class Gfx {
    public static GuiGraphicsExtractor c;
    public static Font tr;
    public static float alpha = 1f;
    public static boolean shadow = false;

    private Gfx() {}

    public static void begin(GuiGraphicsExtractor ctx, Font renderer, float globalAlpha, boolean textShadow) {
        c = ctx;
        tr = renderer;
        alpha = globalAlpha;
        shadow = textShadow;
    }

    public static int col(int argb, float mul) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, Math.round(a * mul)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, float a) {
        int na = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        int al = Math.round(aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    public static void rect(float x, float y, float w, float h, int argb) {
        int x0 = Math.round(x), y0 = Math.round(y);
        int x1 = Math.round(x + w), y1 = Math.round(y + h);
        if (x1 <= x0 || y1 <= y0) return;
        c.fill(x0, y0, x1, y1, col(argb, alpha));
    }

    public static void roundRect(int x, int y, int w, int h, int r, int argb) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int body = col(argb, alpha);
        if (r == 0) {
            c.fill(x, y, x + w, y + h, body);
            return;
        }
        if (h > 2 * r) c.fill(x, y + r, x + w, y + h - r, body);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double ins = r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
            int full = (int) Math.ceil(ins);
            int x0 = x + full;
            int x1 = x + w - full;
            if (x1 > x0) {
                c.fill(x0, y + i, x1, y + i + 1, body);
                c.fill(x0, y + h - 1 - i, x1, y + h - i, body);
            }
            double cover = full - ins;
            if (full > 0 && cover > 0.04) {
                int edge = col(argb, alpha * (float) cover);
                c.fill(x + full - 1, y + i, x + full, y + i + 1, edge);
                c.fill(x + w - full, y + i, x + w - full + 1, y + i + 1, edge);
                c.fill(x + full - 1, y + h - 1 - i, x + full, y + h - i, edge);
                c.fill(x + w - full, y + h - 1 - i, x + w - full + 1, y + h - i, edge);
            }
        }
    }

    private static int inset(int row, int h, int r) {
        if (r <= 0) return 0;
        int i;
        if (row < r) i = row;
        else if (row >= h - r) i = h - 1 - row;
        else return 0;
        double dy = r - i - 0.5;
        double ins = r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
        return (int) Math.ceil(ins);
    }

    public static void ring(int x, int y, int w, int h, int r, int t, int argb) {
        if (t <= 0 || w <= 2 * t || h <= 2 * t) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int ri = Math.max(0, r - t);
        int color = col(argb, alpha);
        for (int row = 0; row < h; row++) {
            int io = inset(row, h, r);
            if (row < t || row >= h - t) {
                if (w - 2 * io > 0) c.fill(x + io, y + row, x + w - io, y + row + 1, color);
            } else {
                int ii = t + inset(row - t, h - 2 * t, ri);
                if (ii < io + 1) ii = io + 1;
                c.fill(x + io, y + row, x + ii, y + row + 1, color);
                c.fill(x + w - ii, y + row, x + w - io, y + row + 1, color);
            }
        }
    }

    public static void circle(float cx, float cy, float r, int argb) {
        int color = col(argb, alpha);
        int top = (int) Math.floor(cy - r);
        int bottom = (int) Math.ceil(cy + r);
        for (int py = top; py < bottom; py++) {
            double dy = (py + 0.5) - cy;
            double d2 = (double) r * r - dy * dy;
            if (d2 <= 0) continue;
            double dx = Math.sqrt(d2);
            int x0 = (int) Math.round(cx - dx);
            int x1 = (int) Math.round(cx + dx);
            if (x1 > x0) c.fill(x0, py, x1, py + 1, color);
        }
    }

    public static void texture(Identifier id, int x, int y, int w, int h, int texW, int texH, float a) {
        c.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH,
                col(0xFFFFFFFF, alpha * a));
    }

    public static float width(String s, float sc) {
        return tr.width(s) * sc;
    }

    public static void text(String s, float x, float y, int argb, float sc) {
        text(s, x, y, argb, sc, 1f);
    }

    public static void text(String s, float x, float y, int argb, float sc, float a) {
        if (s == null || s.isEmpty()) return;
        int color = col(argb, alpha * a);
        if (((color >>> 24) & 0xFF) < 4) return;
        var m = c.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sc, sc);
        c.text(tr, s, 0, 0, color, shadow);
        m.popMatrix();
    }

    public static void textCentered(String s, float cx, float y, int argb, float sc, float a) {
        text(s, cx - width(s, sc) / 2f, y, argb, sc, a);
    }

    public static void textRight(String s, float rx, float y, int argb, float sc, float a) {
        text(s, rx - width(s, sc), y, argb, sc, a);
    }

    public static String trim(String s, float maxW, float sc) {
        return tr.plainSubstrByWidth(s, Math.max(1, (int) (maxW / sc)));
    }

    public static String scrollFit(String s, float maxW, float sc) {
        if (width(s, sc) <= maxW) return s;
        String loop = s + "   \u2022   ";
        int hold = 8;
        int cycle = loop.length() + hold;
        int step = (int) ((System.currentTimeMillis() / 150L) % cycle);
        int off = Math.max(0, step - hold);
        String rotated = loop.substring(off) + loop.substring(0, off);
        return trim(rotated, maxW, sc);
    }

    public static String ellipsize(String s, float maxW, float sc) {
        if (width(s, sc) <= maxW) return s;
        int ell = tr.width("\u2026");
        String t = tr.plainSubstrByWidth(s, Math.max(1, (int) (maxW / sc) - ell));
        return t + "\u2026";
    }

    public static void iconPlay(float cx, float cy, float size, int argb) {
        int h = Math.max(3, Math.round(size));
        int w = Math.max(3, Math.round(size * 0.85f));
        int x0 = Math.round(cx - w * 0.38f);
        for (int i = 0; i < w; i++) {
            float frac = 1f - (i + 0.5f) / w;
            int hh = Math.max(1, Math.round(h * frac));
            int top = Math.round(cy - hh / 2f);
            c.fill(x0 + i, top, x0 + i + 1, top + hh, col(argb, alpha));
        }
    }

    public static void iconPause(float cx, float cy, float size, int argb) {
        int h = Math.max(3, Math.round(size));
        int bw = Math.max(1, Math.round(size * 0.28f));
        int gap = Math.max(1, Math.round(size * 0.22f));
        int top = Math.round(cy - h / 2f);
        int left = Math.round(cx - gap / 2f - bw);
        int right = Math.round(cx + gap / 2f);
        int color = col(argb, alpha);
        c.fill(left, top, left + bw, top + h, color);
        c.fill(right, top, right + bw, top + h, color);
    }

    public static void iconNext(float cx, float cy, float size, int argb) {
        int h = Math.max(3, Math.round(size));
        int w = Math.max(3, Math.round(size * 0.7f));
        int bw = Math.max(1, Math.round(size * 0.18f));
        int total = w + bw + 1;
        int x0 = Math.round(cx - total / 2f);
        int color = col(argb, alpha);
        for (int i = 0; i < w; i++) {
            float frac = 1f - (i + 0.5f) / w;
            int hh = Math.max(1, Math.round(h * frac));
            int top = Math.round(cy - hh / 2f);
            c.fill(x0 + i, top, x0 + i + 1, top + hh, color);
        }
        int top = Math.round(cy - h / 2f);
        c.fill(x0 + w + 1, top, x0 + w + 1 + bw, top + h, color);
    }

    public static void iconPrev(float cx, float cy, float size, int argb) {
        int h = Math.max(3, Math.round(size));
        int w = Math.max(3, Math.round(size * 0.7f));
        int bw = Math.max(1, Math.round(size * 0.18f));
        int total = w + bw + 1;
        int x0 = Math.round(cx - total / 2f);
        int color = col(argb, alpha);
        int top = Math.round(cy - h / 2f);
        c.fill(x0, top, x0 + bw, top + h, color);
        for (int i = 0; i < w; i++) {
            float frac = (i + 0.5f) / w;
            int hh = Math.max(1, Math.round(h * frac));
            int t = Math.round(cy - hh / 2f);
            c.fill(x0 + bw + 1 + i, t, x0 + bw + 2 + i, t + hh, color);
        }
    }

    public static void equalizer(float cx, float cy, float size, int argb, boolean animated) {
        int bw = Math.max(2, Math.round(size * 0.22f));
        int gap = Math.max(1, Math.round(size * 0.14f));
        int total = bw * 3 + gap * 2;
        int x0 = Math.round(cx - total / 2f);
        int base = Math.round(cy + size / 2f);
        double t = System.nanoTime() / 1_000_000_000.0;
        float[] fixed = {0.55f, 1.0f, 0.75f};
        int color = col(argb, alpha);
        for (int i = 0; i < 3; i++) {
            float f = fixed[i];
            if (animated) {
                f = 0.55f + 0.45f * (float) Math.sin(t * (2.6 + i * 0.9) + i * 1.7);
            }
            int bh = Math.max(2, Math.round(size * f));
            int bx = x0 + i * (bw + gap);
            c.fill(bx, base - bh, bx + bw, base, color);
        }
    }

    public static void spotifyMark(float cx, float cy, float r) {
        circle(cx, cy, r, 0xFF1ED760);
        int dark = 0xFF101010;
        float[] hw = {r * 0.72f, r * 0.60f, r * 0.46f};
        float[] yb = {cy - r * 0.40f, cy - r * 0.02f, cy + r * 0.34f};
        float curve = r * 0.16f;
        int th = Math.max(1, Math.round(r * 0.24f));
        int color = col(dark, alpha);
        for (int i = 0; i < 3; i++) {
            int half = Math.round(hw[i]);
            for (int dx = -half; dx <= half; dx++) {
                float k = half == 0 ? 0f : (float) dx / half;
                int y = Math.round(yb[i] + curve * k * k);
                int x = Math.round(cx) + dx;
                c.fill(x, y, x + 1, y + th, color);
            }
        }
    }
}
