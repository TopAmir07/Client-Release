package dev.topclient.client.clover.module;

import dev.topclient.client.clover.spotify.media.ArtProcessor;
import dev.topclient.client.clover.spotify.media.MediaService;
import dev.topclient.client.clover.spotify.media.MediaState;
import dev.topclient.client.clover.spotify.render.Gfx;
import dev.topclient.client.clover.ui.CloverFont;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * "Video" lyric look: a compact pill at the top of the screen (album art, the current line,
 * the word being sung highlighted in a coloured pill, little equalizer bars) plus the
 * floating sky words (see {@link VideoSky}).
 *
 * Style values (the "Style" setting):
 *   0 = Video: sky words + pill   1 = Sky words only   2 = Pill only
 *   3 = On screen (middle)		4 = On screen + pill  5 = One line karaoke (screen)
 */
public final class LyricsLook {
	private LyricsLook() {}

	// ---- extra settings (created in the module constructor) ----
	static Setting pillSize, pillY, pillOpacity, pillArt, pillBars, pillMaxWidth, pillRound;
	static Setting skyVariety, skySlant, skyReveal, skyPrev, skySpread, skyHighlight, skyWordSize, skyThemeColour;

	static final String[] STYLES = {
			"Video: sky words + pill", "Sky words only", "Pill only",
			"On screen (middle)", "On screen + pill", "One line karaoke (screen)"
	};

	/** Called at the end of the SpotifyLyrics constructor. */
	public static void addSettings(SpotifyLyrics m) {
		m.header("Pill (top bar)");
		pillSize = m.number("pillSize", "Pill size", 0.6f, 2.5f, 0.05f, 1.0f);
		pillY = m.number("pillY", "Pill height from top (%)", 0f, 60f, 0.5f, 1.5f);
		pillOpacity = m.number("pillOpacity", "Pill background opacity", 0f, 1f, 0.05f, 0.82f);
		pillMaxWidth = m.number("pillMaxWidth", "Pill max width (% of screen)", 20f, 90f, 1f, 45f);
		pillRound = m.number("pillRound", "Pill roundness", 0f, 1f, 0.05f, 1f);
		pillArt = m.bool("pillArt", "Show album art in the pill", true);
		pillBars = m.bool("pillBars", "Show equalizer bars in the pill", true);
		m.header("Sky words (video look)");
		skyReveal = m.bool("skyReveal", "Words pop in as they are sung", true);
		skyPrev = m.bool("skyPrev", "Keep the previous line above", true);
		skyWordSize = m.number("skyWordSize", "Sky word size", 0.5f, 5f, 0.1f, 2.4f);
		skyThemeColour = m.bool("skyThemeColour", "Use theme colour (off = clean white like the video)", false);
		skyVariety = m.number("skyVariety", "Word size variety", 0f, 1f, 0.05f, 0.6f);
		skySlant = m.number("skySlant", "Slant / tilt of lines", 0f, 1f, 0.05f, 0.5f);
		skySpread = m.number("skySpread", "Move lines around the sky", 0f, 1f, 0.05f, 0.45f);
		skyHighlight = m.bool("skyHighlight", "Highlight the word being sung", false);
	}

	static int mode(SpotifyLyrics m) { return m.style.getMode(); }

	/** Old screen renderer still handles styles 3-5; tell it which of its own modes to use. */
	public static int legacyMode(SpotifyLyrics m) {
		switch (mode(m)) {
			case 3: return 2;   // on screen (middle)
			case 4: return 2;   // on screen (+ our pill, drawn separately)
			case 5: return 5;   // one line karaoke
			default: return -1; // nothing from the old renderer
		}
	}

	static boolean pillOn(SpotifyLyrics m) { int s = mode(m); return s == 0 || s == 2 || s == 4; }
	static boolean skyWordsOn(SpotifyLyrics m) { int s = mode(m); return s == 0 || s == 1; }

	// ---------------------------------------------------------------- pill

	private static String lastLine = "";
	private static long lineChangedAt;
	private static float shownWidth = -1;

	/** Drawn from the HUD callback, before the old renderer runs. */
	public static void render(SpotifyLyrics m, GuiGraphicsExtractor c, Minecraft mc) {
		try {
			if (mc == null || c == null) return;
			if (mc.gui != null && mc.gui.hud.isHidden()) return;		   // F1
			if (m.hideInMenus.getBool() && mc.gui.screen() != null) return;		   // menu open
			if (!pillOn(m)) return;
			drawPill(m, c, mc);
		} catch (Throwable t) {
			// never break the HUD
		}
	}

	static boolean isPause(SpotifyLyrics.Now n) {
		if (n == null) return true;
		String s = n.cur();
		return n.pause() || s == null || s.isBlank() || s.equals("♪") || s.replace("♪", "").isBlank();
	}

	private static void drawPill(SpotifyLyrics m, GuiGraphicsExtractor c, Minecraft mc) {
		MediaState st = MediaService.get();
		if (st == null || !st.hasMedia) return;
		SpotifyLyrics.Now n = m.now();
		int[] col = m.colors();
		int textCol = col[0] & 0xFFFFFF, hlCol = col[1] & 0xFFFFFF, barCol = col[2] & 0xFFFFFF;

		float S = pillSize.getFloat();
		int fam = m.fam();
		float ts = 0.66f * S;				 // font scale
		float th = 9f * ts;				   // text line height
		int h = Math.round(th + 7f * S);	  // pill height
		int pad = Math.round(4f * S);
		int artD = pillArt.getBool() ? h - Math.round(4 * S) : 0;
		int barsW = pillBars.getBool() ? Math.round(10 * S) : 0;
		int sw = c.guiWidth(), sh = c.guiHeight();
		float maxText = sw * pillMaxWidth.getFloat() / 100f - (artD + barsW + pad * 4);

		// what to show
		boolean pause = isPause(n);
		String line;
		if (n == null) line = m.status == null ? "" : m.status;
		else line = pause ? "" : n.cur();
		if (!line.equals(lastLine)) { lastLine = line; lineChangedAt = System.currentTimeMillis(); }
		float fadeIn = Math.min(1f, (System.currentTimeMillis() - lineChangedAt) / 220f);

		List<String> words = split(line);
		float space = CloverFont.width(famFor(m, " "), " ", ts);
		if (space <= 0) space = 3 * ts;
		float[] ww = new float[words.size()];
		float total = 0;
		for (int i = 0; i < ww.length; i++) { ww[i] = width(m, words.get(i), ts); total += ww[i] + (i > 0 ? space : 0); }

		// which word is being sung
		int active = -1;
		if (n != null && !pause && !words.isEmpty() && m.synced) active = wordAt(words, n.progress());

		// window of words that fits, keeping the active word visible
		int from = 0, to = words.size();
		if (total > maxText && !words.isEmpty()) {
			int anchor = Math.max(0, active);
			from = anchor; to = anchor + 1;
			float wsum = ww[anchor];
			boolean grew = true;
			while (grew) {
				grew = false;
				if (to < words.size() && wsum + space + ww[to] <= maxText) { wsum += space + ww[to]; to++; grew = true; }
				if (from > 0 && wsum + space + ww[from - 1] <= maxText) { from--; wsum += space + ww[from]; grew = true; }
			}
			total = wsum;
		}
		float textW = words.isEmpty() ? (pause ? 18 * S : 0) : total;
		if (words.isEmpty() && !pause && line.isEmpty()) return;

		float targetW = artD + barsW + textW + pad * 2 + (artD > 0 ? pad : 0) + (barsW > 0 ? pad + 2 : 0);
		if (shownWidth < 0) shownWidth = targetW;
		shownWidth += (targetW - shownWidth) * 0.25f;	 // smooth resize
		int w = Math.round(shownWidth);
		int x = sw / 2 - w / 2;
		int y = Math.round(sh * pillY.getFloat() / 100f);

		Gfx.begin(c, mc.font, 1f, false);
		int radius = Math.round(h / 2f * pillRound.getFloat());
		Gfx.roundRect(x, y, w, h, radius, argb(barCol, pillOpacity.getFloat()));

		int cx = x + pad;
		if (artD > 0) {
			int ay = y + (h - artD) / 2;
			Identifier art = artTexture(st);
			if (art != null) Gfx.texture(art, cx, ay, artD, artD, ART, ART, 1f);
			else Gfx.circle(cx + artD / 2f, ay + artD / 2f, artD / 2f, argb(hlCol, 0.9f));
			cx += artD + pad;
		}

		float ty = y + (h - th) / 2f + 0.5f * S;
		if (pause) {
			// three bouncing dots while there are no words
			long t = System.currentTimeMillis();
			for (int i = 0; i < 3; i++) {
				float b = (float) Math.sin(t / 180.0 - i * 0.9) * 1.2f * S;
				Gfx.circle(cx + 3 * S + i * 6 * S, y + h / 2f + b, 1.6f * S, argb(textCol, 0.85f));
			}
		} else {
			float tx = cx;
			for (int i = from; i < to; i++) {
				String word = words.get(i);
				if (i == active) {
					int px = Math.round(tx - 2.5f * S), py = Math.round(y + 2 * S);
					int pw = Math.round(ww[i] + 5 * S), ph = h - Math.round(4 * S);
					Gfx.roundRect(px, py, pw, ph, Math.round(ph / 2f * pillRound.getFloat()), argb(hlCol, 0.95f * fadeIn));
				}
				int wc = argb(i == active ? 0xFFFFFF : textCol, (i <= active || active < 0 ? 1f : 0.82f) * fadeIn);
				CloverFont.draw(c, famFor(m, word), word, tx, ty, ts, wc, m.shadowOn());
				tx += ww[i] + space;
			}
			if (words.isEmpty()) CloverFont.draw(c, famFor(m, line), line, tx, ty, ts, argb(textCol, 0.7f), false);
		}

		if (barsW > 0) {
			float bx = x + w - pad - barsW / 2f - 1;
			Gfx.equalizer(bx, y + (h - barsW) / 2f, barsW, argb(textCol, 0.9f), st.playing);
		}
	}

	// ---------------------------------------------------------------- helpers

	static List<String> split(String s) {
		List<String> out = new ArrayList<>();
		if (s == null) return out;
		for (String w : s.trim().split("\\s+")) if (!w.isEmpty()) out.add(w);
		return out;
	}

	/** Index of the word being sung, estimating word timing from letter counts. */
	static int wordAt(List<String> words, float progress) {
		int total = 0;
		for (String w : words) total += w.length() + 1;
		float at = Math.max(0f, Math.min(0.999f, progress)) * total;
		int run = 0;
		for (int i = 0; i < words.size(); i++) {
			run += words.get(i).length() + 1;
			if (at < run) return i;
		}
		return words.size() - 1;
	}

	/** Start fraction (0..1) of each word inside its line. */
	static float[] starts(List<String> words) {
		float[] r = new float[words.size()];
		int total = 0;
		for (String w : words) total += w.length() + 1;
		int run = 0;
		for (int i = 0; i < r.length; i++) { r[i] = total == 0 ? 0 : run / (float) total; run += words.get(i).length() + 1; }
		return r;
	}

	static int famFor(SpotifyLyrics m, String s) { return m.famFor(s); }

	static float width(SpotifyLyrics m, String s, float scale) { return CloverFont.width(famFor(m, s), s, scale); }

	static int argb(int rgb, float a) {
		int al = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	// ---------------------------------------------------------------- round album art

	private static final int ART = 64;
	private static Identifier artId;
	private static Object artFor;
	private static int artCounter;

	private static Identifier artTexture(MediaState st) {
		BufferedImage src = st.art != null ? st.art : st.artFlat;
		if (src == null) return null;
		if (src == artFor && artId != null) return artId;
		artFor = src;
		try {
			BufferedImage round = new BufferedImage(ART, ART, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = round.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setClip(new Ellipse2D.Float(0, 0, ART, ART));
			g.drawImage(src, 0, 0, ART, ART, null);
			g.dispose();
			byte[] png = ArtProcessor.toPng(round);
			NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
			String name = "lyricsart" + (artCounter++);
			DynamicTexture tex = new DynamicTexture(() -> name, img);
			tex.upload();
			Identifier id = Identifier.fromNamespaceAndPath("topclient", name);
			Minecraft mc = Minecraft.getInstance();
			if (artId != null) { try { mc.getTextureManager().release(artId); } catch (Throwable ignored) {} }
			mc.getTextureManager().register(id, tex);
			artId = id;
			return id;
		} catch (Throwable t) {
			artId = null;
			return null;
		}
	}
}
