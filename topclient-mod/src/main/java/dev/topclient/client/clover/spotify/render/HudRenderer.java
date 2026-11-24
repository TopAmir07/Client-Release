package dev.topclient.client.clover.spotify.render;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.Background;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.Theme;
import dev.topclient.client.clover.spotify.media.ArtProcessor;
import dev.topclient.client.clover.spotify.media.MediaState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class HudRenderer {
    public enum Action { PREVIOUS, TOGGLE, NEXT, SEEK }

    public record HitBox(Action action, float x, float y, float w, float h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public static final List<HitBox> HITS = new ArrayList<>();

    /** While the player drags the time bar: where it would jump to (0..1), or -1. */
    public static float seekPreview = -1f;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int SUB = 0xFFB3B3B3;
    private static final int DIM = 0xFF7C7C7C;
    private static final int TRACK = 0xFF3E3E3E;
    private static final int SURFACE = 0xFF121212;

    private static SpotifyUIConfig cfg;
    private static MediaState st;
    private static long now;
    private static float ox, oy, sc;
    private static boolean live;
    private static int mx, my;
    private static int accent;
    private static float fade;
    private static long changeNanos;
    private static Identifier artId;

    private HudRenderer() {}

    public static int width(Theme t) {
        return switch (t) {
            case IPOD -> 124;
            case PHONE -> 104;
            case CARD -> 200;
            case COMPACT -> 160;
            case MINIMAL -> 150;
        };
    }

    public static int height(Theme t) {
        return switch (t) {
            case IPOD -> 200;
            case PHONE -> 196;
            case CARD -> 72;
            case COMPACT -> 40;
            case MINIMAL -> 46;
        };
    }

    public static float autoScale(Theme t, int guiW, int guiH) {
        boolean tall = t == Theme.IPOD || t == Theme.PHONE;
        float target = tall ? 0.30f * guiH / height(t) : 0.24f * guiW / width(t);
        float rounded = Math.round(target * 20f) / 20f;
        return Math.max(0.1f, Math.min(2.0f, rounded));
    }

    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, MediaState state,
                            float x, float y, float scale, float masterAlpha,
                            boolean liveMode, int mouseX, int mouseY, long changeNano) {
        cfg = SpotifyUIConfig.get();
        st = state;
        now = System.nanoTime();
        ox = Math.round(x);
        oy = Math.round(y);
        sc = scale;
        live = liveMode;
        mx = mouseX;
        my = mouseY;
        changeNanos = changeNano;

        accent = 0xFF000000 | (cfg.accent == SpotifyUIConfig.Accent.ALBUM
                ? (state.hasMedia ? state.accentRgb : MediaState.DEFAULT_ACCENT)
                : cfg.accent.rgb);
        float f = Math.max(0f, Math.min(1f, (now - changeNano) / 350_000_000f));
        fade = f * f * (3f - 2f * f);

        boolean shadow = cfg.textShadow || cfg.background == Background.NONE || cfg.backgroundOpacity < 0.35f;
        Gfx.begin(ctx, mc.font, cfg.opacity * masterAlpha, shadow);
        artId = cfg.showAlbumArt ? Textures.art(state) : null;

        if (live) HITS.clear();

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(scale, scale);
        try {
            switch (cfg.theme) {
                case IPOD -> ipod();
                case PHONE -> phone();
                case CARD -> card();
                case COMPACT -> compact();
                case MINIMAL -> minimal();
            }
        } finally {
            m.popMatrix();
        }
    }

    private static void ipod() {
        final int W = 124, H = 200, R = 11;
        boolean shell = cfg.background != Background.NONE;
        float a = cfg.backgroundOpacity;

        if (shell) {
            Gfx.roundRect(0, 0, W, H, R, Gfx.withAlpha(0xFF9C9C9F, a));
            Gfx.rect(R, 1, W - 2 * R, 1, Gfx.withAlpha(0xFFC4C4C7, a));
        }
        border(0, 0, W, H, R);

        final int sx = 10, sy = 10, sw = 104, sh = 76;
        if (shell) Gfx.roundRect(sx - 2, sy - 2, sw + 4, sh + 4, 6, Gfx.withAlpha(0xFF2E2E30, a));
        surface(sx, sy, sw, sh, 4, 0xFF0C0C0C);

        final int pad = 6;
        final int left = sx + pad;
        final int right = sx + sw - pad;

        Gfx.spotifyMark(left + 4, sy + 9, 4f);
        Gfx.text("Spotify", left + 11, sy + 6, WHITE, 0.85f);
        if (st.hasMedia) {
            if (st.playing) Gfx.iconPlay(right - 3, sy + 9, 6, accent);
            else Gfx.iconPause(right - 3, sy + 9, 6, accent);
        }
        Gfx.rect(left, sy + 17, right - left, 1, 0xFF262626);

        if (st.hasMedia) {
            int top = sy + 23;
            int art = 32;
            int tx = left;
            if (cfg.showAlbumArt) {
                artBox(left, top, art);
                tx = left + art + 6;
            }
            float tw = right - tx;
            Gfx.text(shown(st.title, tw, 0.9f), tx, top + 1, WHITE, 0.9f, fade);
            Gfx.text(shown(st.artist, tw, 0.8f), tx, top + 12.5f, SUB, 0.8f, fade);
            if (cfg.showAlbumName && !st.album.isBlank()) {
                Gfx.text(shown(st.album, tw, 0.8f), tx, top + 23, DIM, 0.8f, fade);
            }
            if (cfg.showProgress) {
                progressRow(left, right, sy + 60, 0.75f, 2, false);
            }
        } else {
            float cx = sx + sw / 2f;
            Gfx.equalizer(cx, sy + 36, 14, 0xFF3A3A3A, false);
            Gfx.textCentered("No song playing", cx, sy + 49, WHITE, 0.9f, 1f);
            Gfx.textCentered("Open Spotify and press play", cx, sy + 61, DIM, 0.6f, 1f);
        }

        final float wx = W / 2f, wy = 142f;
        int glyph = shell ? 0xFF6B6657 : WHITE;
        int hoverBg = shell ? 0x22000000 : 0x33FFFFFF;
        if (shell) {
            Gfx.circle(wx, wy, 45, Gfx.withAlpha(0xFFD9D4BE, a));
            Gfx.circle(wx, wy, 17, Gfx.withAlpha(0xFF6F6F72, a));
        }
        Gfx.textCentered("MENU", wx, wy - 39, glyph, 0.7f, 1f);
        if (cfg.showControls) {
            control(Action.PREVIOUS, wx - 32, wy, 9, 9, glyph, hoverBg);
            control(Action.NEXT, wx + 32, wy, 9, 9, glyph, hoverBg);
            control(Action.TOGGLE, wx, wy + 33, 9, 9, glyph, hoverBg);
            hitbox(Action.TOGGLE, wx, wy, 14);
        }
    }

    private static void phone() {
        final int W = 104, H = 196, R = 15;
        boolean shell = cfg.background != Background.NONE;
        float a = cfg.backgroundOpacity;

        if (shell) Gfx.roundRect(0, 0, W, H, R, Gfx.withAlpha(0xFF0A0A0A, a));
        border(0, 0, W, H, R);

        final int sx = 4, sy = 4, sw = 96, sh = 188;
        surface(sx, sy, sw, sh, 12, 0xFF161616);
        if (shell) Gfx.roundRect(36, 9, 32, 9, 4, 0xFF000000);

        float cx = W / 2f;
        LocalTime t = LocalTime.now();
        String clock = String.format("%02d:%02d", t.getHour(), t.getMinute());
        Gfx.textCentered(clock, cx, 24, WHITE, 1.5f, 1f);

        artBox(20, 46, 64);

        if (st.hasMedia) {
            Gfx.textCentered(shown(st.title, 84, 1.0f), cx, 116, WHITE, 1.0f, fade);
            Gfx.textCentered(shown(st.artist, 84, 0.85f), cx, 128, SUB, 0.85f, fade);
            if (cfg.showProgress) {
                progressRow(14, 90, 150, 0.7f, 3, true);
            }
        } else {
            Gfx.textCentered("No song playing", cx, 116, WHITE, 1.0f, 1f);
            Gfx.textCentered("Nothing detected", cx, 128, DIM, 0.85f, 1f);
        }

        if (cfg.showControls) {
            float cy = 170;
            control(Action.PREVIOUS, cx - 25, cy, 9, 10, WHITE, 0x33FFFFFF);
            boolean hov = hovered(cx, cy, 11);
            Gfx.circle(cx, cy, 10, hov ? 0xFFE6E6E6 : WHITE);
            if (st.playing) Gfx.iconPause(cx, cy, 9, 0xFF101010);
            else Gfx.iconPlay(cx + 0.5f, cy, 9, 0xFF101010);
            hitbox(Action.TOGGLE, cx, cy, 11);
            control(Action.NEXT, cx + 25, cy, 9, 10, WHITE, 0x33FFFFFF);
            if (shell) Gfx.roundRect(35, 185, 34, 3, 1, 0x88FFFFFF);
        }
    }

    private static void card() {
        final int W = 200, H = 72;
        int r = cfg.cornerRadius;
        surface(0, 0, W, H, r, SURFACE);
        border(0, 0, W, H, r);

        final int pad = 8;
        int tx = pad + 2;
        if (cfg.showAlbumArt) {
            artBox(pad, pad, 56);
            tx = pad + 56 + 8;
        }
        float tw = W - pad - tx;
        float cx = tx + tw / 2f;

        if (st.hasMedia) {
            Gfx.text(shown(st.title, tw - 12, 1.0f), tx, 9, WHITE, 1.0f, fade);
            Gfx.text(shown(st.artist, tw - 12, 0.85f), tx, 21, SUB, 0.85f, fade);
            if (cfg.showProgress) progressRow(tx, tx + tw, 41, 0.7f, 3, false);
            if (cfg.showControls) {
                float cy = 60;
                control(Action.PREVIOUS, cx - 28, cy, 8, 8, SUB, 0x26FFFFFF);
                control(Action.TOGGLE, cx, cy, 10, 9, WHITE, 0x26FFFFFF);
                control(Action.NEXT, cx + 28, cy, 8, 8, SUB, 0x26FFFFFF);
            }
            if (st.source.equalsIgnoreCase("Spotify")) Gfx.spotifyMark(W - 13, 13, 4.5f);
        } else {
            Gfx.text("No song playing", tx, 24, WHITE, 1.0f);
            Gfx.text(Gfx.ellipsize("Open Spotify and press play", tw, 0.7f), tx, 37, DIM, 0.7f);
        }
    }

    private static void compact() {
        final int W = 160, H = 40;
        int r = cfg.cornerRadius;
        surface(0, 0, W, H, r, SURFACE);
        border(0, 0, W, H, r);

        int tx = 9;
        if (cfg.showAlbumArt) {
            artBox(5, 5, 30);
            tx = 42;
        }
        int right = W - 8;
        float tw = right - tx;

        if (st.hasMedia) {
            boolean bar = cfg.showProgress;
            Gfx.text(shown(st.title, tw, 0.9f), tx, bar ? 6 : 11, WHITE, 0.9f, fade);
            Gfx.text(shown(st.artist, tw, 0.8f), tx, bar ? 16.5f : 22, SUB, 0.8f, fade);
            if (bar) progressRow(tx, right, 28, 0.65f, 2, false);
        } else {
            Gfx.text("No song playing", tx, 11, WHITE, 0.9f);
            Gfx.text(Gfx.ellipsize("Nothing detected", tw, 0.7f), tx, 22, DIM, 0.7f);
        }
    }

    private static void minimal() {
        final int W = 150, H = 46;
        int r = cfg.cornerRadius;
        surface(0, 0, W, H, r, SURFACE);
        border(0, 0, W, H, r);

        float cx = W / 2f;
        if (st.hasMedia) {
            Gfx.textCentered(shown(st.title, W - 16, 1.0f), cx, 6, WHITE, 1.0f, fade);
            Gfx.textCentered(shown(st.artist, W - 16, 0.85f), cx, 18, SUB, 0.85f, fade);
            String time = timeLeft() + " / " + timeRight();
            Gfx.textCentered(time, cx, 30, DIM, 0.75f, 1f);
            if (cfg.showProgress && st.durationMs > 0) {
                Gfx.rect(10, 41, W - 20, 2, TRACK);
                Gfx.rect(10, 41, (W - 20) * progress(), 2, accent);
            }
        } else {
            Gfx.textCentered("No song playing", cx, 12, WHITE, 1.0f, 1f);
            Gfx.textCentered("Nothing detected", cx, 25, DIM, 0.8f, 1f);
        }
    }

    private static void progressRow(float left, float right, float y, float tsc, int barH, boolean knob) {
        String l = timeLeft();
        String r = timeRight();
        float p = progress();
        if (knob) {
            float by = y - 5;
            seekBox(left, right, by + barH / 2f);
            Gfx.rect(left, by, right - left, barH, TRACK);
            if (st.durationMs > 0) {
                Gfx.rect(left, by, (right - left) * p, barH, WHITE);
                Gfx.circle(left + (right - left) * p, by + barH / 2f, 3f, WHITE);
            }
            Gfx.text(l, left, y, SUB, tsc);
            Gfx.textRight(r, right, y, SUB, tsc, 1f);
            return;
        }
        float lw = Gfx.width(l, tsc);
        float rw = Gfx.width(r, tsc);
        float textH = 7f * tsc;
        Gfx.text(l, left, y, SUB, tsc);
        Gfx.textRight(r, right, y, SUB, tsc, 1f);
        float x0 = left + lw + 4;
        float x1 = right - rw - 4;
        float by = y + textH / 2f - barH / 2f;
        seekBox(x0, x1, by + barH / 2f);
        boolean grab = live && st.durationMs > 0 && (seekPreview >= 0f || seekHover(x0, x1, by + barH / 2f));
        Gfx.rect(x0, by, x1 - x0, barH, TRACK);
        if (st.durationMs > 0) Gfx.rect(x0, by, (x1 - x0) * p, barH, accent);
        if (grab) Gfx.circle(x0 + (x1 - x0) * p, by + barH / 2f, barH / 2f + 1.6f, WHITE); // handle while you can drag
    }

    // The time bar is a seek slider when the mouse is free (chat or inventory open).
    private static void seekBox(float x0, float x1, float cy) {
        if (!live || !cfg.clickableControls || st.durationMs <= 0) return;
        float pad = 4f;
        HITS.add(new HitBox(Action.SEEK, ox + (x0 - 2) * sc, oy + (cy - pad) * sc, (x1 - x0 + 4) * sc, 2 * pad * sc));
        seekX0 = ox + x0 * sc;
        seekX1 = ox + x1 * sc;
    }

    private static boolean seekHover(float x0, float x1, float cy) {
        float sx0 = ox + (x0 - 2) * sc, sx1 = ox + (x1 + 2) * sc, sy = oy + cy * sc, pad = 4f * sc;
        return mx >= sx0 && mx < sx1 && my >= sy - pad && my < sy + pad;
    }

    /** Screen x range of the time bar drawn last (for dragging). */
    public static float seekX0, seekX1;

    /** Where on the bar (0..1) a screen x falls. */
    public static float seekFraction(double screenX) {
        if (seekX1 <= seekX0) return 0f;
        return (float) Math.max(0.0, Math.min(1.0, (screenX - seekX0) / (seekX1 - seekX0)));
    }

    private static void artBox(int x, int y, int size) {
        if (artId != null) {
            Gfx.texture(artId, x, y, size, size, 128, 128, 1f);
        } else {
            Gfx.roundRect(x, y, size, size, Math.max(2, size / 9), 0xFF262626);
            Gfx.equalizer(x + size / 2f, y + size / 2f, size * 0.36f,
                    st.hasMedia ? accent : 0xFF4A4A4A, st.hasMedia && st.playing);
        }
    }

    private static void control(Action action, float cx, float cy, float size, float half, int color, int hoverBg) {
        boolean hov = hovered(cx, cy, half);
        if (hov) Gfx.circle(cx, cy, half + 1, hoverBg);
        switch (action) {
            case PREVIOUS -> Gfx.iconPrev(cx, cy, size, color);
            case NEXT -> Gfx.iconNext(cx, cy, size, color);
            case TOGGLE -> {
                if (st.playing) Gfx.iconPause(cx, cy, size, color);
                else Gfx.iconPlay(cx, cy, size, color);
            }
        }
        hitbox(action, cx, cy, half);
    }

    private static boolean hovered(float cx, float cy, float half) {
        if (!live || !cfg.clickableControls) return false;
        float x = ox + (cx - half) * sc;
        float y = oy + (cy - half) * sc;
        float w = 2 * half * sc;
        return mx >= x && mx < x + w && my >= y && my < y + w;
    }

    private static void hitbox(Action action, float cx, float cy, float half) {
        if (!live || !cfg.showControls) return;
        float x = ox + (cx - half) * sc;
        float y = oy + (cy - half) * sc;
        float w = 2 * half * sc;
        HITS.add(new HitBox(action, x, y, w, w));
    }

    private static void border(int x, int y, int w, int h, int r) {
        if (cfg.borderThickness <= 0) return;
        int color;
        switch (cfg.borderColor) {
            case ACCENT -> color = accent;
            case CLEAR -> color = 0x55FFFFFF;
            default -> color = 0xFF000000 | cfg.borderColor.rgb;
        }
        Gfx.ring(x, y, w, h, r, cfg.borderThickness, color);
    }

    private static void surface(int x, int y, int w, int h, int r, int solid) {
        Background bg = cfg.background;
        if (bg == Background.NONE) return;
        float a = cfg.backgroundOpacity;
        Identifier tex = null;
        final float dim = cfg.imageDim;

        if (bg == Background.ALBUM && st.artFlat != null) {
            final BufferedImage flat = st.artFlat;
            String key = "album:" + System.identityHashCode(flat) + ":" + w + "x" + h + ":" + r + ":" + dim;
            tex = Textures.background(key, () -> ArtProcessor.bake(flat, true, w * 2, h * 2, r * 2, dim));
        } else if (bg == Background.IMAGE && !cfg.backgroundImage.isBlank()) {
            final BufferedImage img = Textures.customImage(cfg.backgroundImage);
            if (img != null) {
                String key = "img:" + Textures.customKey(cfg.backgroundImage) + ":" + w + "x" + h + ":" + r + ":" + dim;
                tex = Textures.background(key, () -> ArtProcessor.bake(img, false, w * 2, h * 2, r * 2, dim));
            }
        }

        if (tex != null) {
            Gfx.texture(tex, x, y, w, h, w * 2, h * 2, a);
        } else {
            Gfx.roundRect(x, y, w, h, r, Gfx.withAlpha(solid, a));
        }
    }

    private static String shown(String s, float maxW, float tsc) {
        if (Gfx.width(s, tsc) <= maxW) return s;
        if (!cfg.scrollLongText) return Gfx.ellipsize(s, maxW, tsc);
        String loop = s + "   \u2022   ";
        long since = (now - changeNanos) / 1_000_000L - 1500L;
        int off = since <= 0 ? 0 : (int) ((since / 170L) % loop.length());
        String rotated = loop.substring(off) + loop.substring(0, off);
        return Gfx.trim(rotated, maxW, tsc);
    }

    private static long elapsed() {
        if (live && seekPreview >= 0f && st.durationMs > 0) return (long) (seekPreview * st.durationMs);
        return st.elapsedMs(now);
    }

    private static float progress() {
        if (st.durationMs <= 0) return 0f;
        return Math.max(0f, Math.min(1f, elapsed() / (float) st.durationMs));
    }

    private static String timeLeft() {
        return fmt(elapsed());
    }

    private static String timeRight() {
        if (st.durationMs <= 0) return "--:--";
        if (cfg.timeMode == SpotifyUIConfig.TimeMode.REMAINING) {
            return "-" + fmt(Math.max(0L, st.durationMs - elapsed()));
        }
        return fmt(st.durationMs);
    }

    private static String fmt(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
