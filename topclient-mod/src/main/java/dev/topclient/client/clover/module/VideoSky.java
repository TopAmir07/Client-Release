package dev.topclient.client.clover.module;

import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Display;

import java.util.ArrayList;
import java.util.List;

/**
 * Sky lyrics like the video: every word is its own floating 3D text, words pop in as they
 * are sung, each word gets its own size, lines are slanted and wander around the sky a bit,
 * and the previous line drifts up and fades out above the current one.
 */
public final class VideoSky {
	private VideoSky() {}

	private static final int MAX_WORDS = 22;			// per line
	private static final float PX = 0.025f;			 // one font pixel in blocks at scale 1
	private static final Display.TextDisplay[] POOL = new Display.TextDisplay[MAX_WORDS * 2];
	private static ClientLevel world;
	private static float yaw = Float.NaN;

	// line tracking for animations
	private static String curLine = null, prevLine = null;
	private static int lineSeed = 0, prevSeed = 0;
	private static long lineAt = 0;
	private static final long[] revealAt = new long[MAX_WORDS];

	/** Replaces SkyLyrics.tick in SpotifyLyrics.onTick. */
	public static void tick(Minecraft mc, SpotifyLyrics m) {
		try {
			SkyLyrics.tick(mc, m);   // old 3D style (now always off) just tidies itself up
		} catch (Throwable ignored) {}
		try {
			run(mc, m);
		} catch (Throwable t) {
			clearMine();
		}
	}

	/** Replaces SkyLyrics.clear in SpotifyLyrics. */
	public static void clear() {
		try { SkyLyrics.clear(); } catch (Throwable ignored) {}
		clearMine();
	}

	private static void clearMine() {
		for (int i = 0; i < POOL.length; i++) {
			if (POOL[i] != null && world != null) {
				try { world.removeEntity(POOL[i].getId(), Entity.RemovalReason.DISCARDED); } catch (Throwable ignored) {}
			}
			POOL[i] = null;
		}
		world = null;
		curLine = prevLine = null;
	}

	private static SpotifyLyrics inst;

	private static void run(Minecraft mc, SpotifyLyrics m) {
		inst = m;
		if (mc == null || mc.level == null || mc.player == null || !LyricsLook.skyWordsOn(m)) { clearMine(); return; }
		SpotifyLyrics.Now n = m.now();
		if (n == null) { clearMine(); return; }
		if (world != mc.level) { clearMine(); world = mc.level; }
		for (int i = 0; i < POOL.length; i++) {
			if (POOL[i] == null || POOL[i].isRemoved()) {
				POOL[i] = SkyLyrics.make(world);
				if (POOL[i] == null) { clearMine(); return; }
			}
		}

		// ---- where in the sky
		LocalPlayer p = mc.player;
		float pyaw = p.getYRot();
		int follow = m.skyFollow.getMode();
		if (Float.isNaN(yaw) || follow == 1) yaw = pyaw;
		else if (follow == 0) {
			float d = ((pyaw - yaw) % 360f + 540f) % 360f - 180f;
			yaw += d * 0.03f;
		}
		double dist = m.skyDistance.getFloat();
		double elev = Math.toRadians(m.skyElevation.getFloat());
		double yr = Math.toRadians(yaw);
		double fx = -Math.sin(yr) * Math.cos(elev), fz = Math.cos(yr) * Math.cos(elev), fy = Math.sin(elev);
		double cx = p.getX() + fx * dist, cy = p.getEyeY() + fy * dist, cz = p.getZ() + fz * dist;
		// screen-right and screen-up directions of that spot
		double rx = -Math.cos(yr), rz = -Math.sin(yr);
		double ux = Math.sin(yr) * Math.sin(elev), uy = Math.cos(elev), uz = -Math.cos(yr) * Math.sin(elev);

		float base = (float) (dist * m.sizeSetting() / 6.5 * 0.55) * LyricsLook.skyWordSize.getFloat() / 2.2f;
		int[] colors = m.colorsForSky();
		int textCol = LyricsLook.skyThemeColour.getBool() ? colors[0] & 0xFFFFFF : 0xF4F8FF;
		int hlCol = colors[1] & 0xFFFFFF;

		// ---- which lines
		boolean pause = LyricsLook.isPause(n);
		String cur = pause ? "♪ ♪ ♪" : n.cur();
		if (!cur.equals(curLine)) {
			if (curLine != null && !curLine.isEmpty()) { prevLine = curLine; prevSeed = lineSeed; }
			curLine = cur;
			lineSeed = cur.hashCode() * 31 + (int) (System.currentTimeMillis() / 997);
			lineAt = System.currentTimeMillis();
			java.util.Arrays.fill(revealAt, 0L);
		}
		long now = System.currentTimeMillis();
		float move = ease(Math.min(1f, (now - lineAt) / 450f));	 // previous line drifting up
		if (!LyricsLook.skyPrev.getBool() || pause) prevLine = null;

		Font font = mc.font;
		int used = 0;
		// current line
		used = layLine(m, font, curLine, lineSeed, 0f, 1f, true, pause, n.progress(), base,
				cx, cy, cz, rx, rz, ux, uy, uz, textCol, hlCol, colors, used, now);
		// previous line: starts where the current line was, floats up and fades
		if (prevLine != null) {
			float fade = 0.55f * (1f - Math.max(0f, (now - lineAt - 2600) / 900f));
			if (fade > 0.02f)
				used = layLine(m, font, prevLine, prevSeed, 1f * move, Math.max(0f, fade), false, false, 1f, base,
						cx, cy, cz, rx, rz, ux, uy, uz, textCol, hlCol, colors, used, now);
		}
		// hide unused
		for (int i = used; i < POOL.length; i++) {
			SkyLyrics.text(POOL[i], SkyLyrics.plain("", textCol, m), SkyLyrics.alpha(0.11f), m, colors);
			SkyLyrics.place(POOL[i], cx, cy - 500, cz, 0.01f);
		}
	}

	private static int layLine(SpotifyLyrics m, Font font, String line, int seed, float liftLines, float alpha,
							   boolean current, boolean pause, float progress, float base,
							   double cx, double cy, double cz, double rx, double rz, double ux, double uy, double uz,
							   int textCol, int hlCol, int[] colors, int used, long now) {
		List<String> words = LyricsLook.split(line);
		if (words.size() > MAX_WORDS) words = new ArrayList<>(words.subList(0, MAX_WORDS));
		if (words.isEmpty()) return used;

		java.util.Random rnd = new java.util.Random(seed);
		float variety = LyricsLook.skyVariety.getFloat();
		float slantAmt = LyricsLook.skySlant.getFloat();
		float spread = LyricsLook.skySpread.getFloat();

		// per-word size: some words big, some small (like the video), longer words a bit bigger
		float[] k = new float[words.size()];
		float[] w = new float[words.size()];
		float total = 0, avgK = 0;
		for (int i = 0; i < k.length; i++) {
			String s = words.get(i);
			float r = rnd.nextFloat();
			float emph = r < 0.22f ? 1.45f : r < 0.6f ? 1.0f : 0.8f;
			if (s.replaceAll("[^A-Za-z]", "").length() >= 6) emph = Math.max(emph, 1.2f);
			k[i] = 1f + (emph - 1f) * variety;
			// bold adds 1px per letter and the shadow 1px: count them so words never overlap
			float px = font.width(s) + (m.skyBold.getBool() ? s.length() : 0) + 1;
			w[i] = px * k[i];
			avgK += k[i];
		}
		avgK /= k.length;
		float gap = 5f;
		for (int i = 0; i < k.length; i++) total += w[i] + (i > 0 ? gap * (k[i] + k[i - 1]) / 2f : 0);

		float slope = (-0.20f + rnd.nextFloat() * 0.24f) * slantAmt;	   // mostly falling to the right
		float shiftX = (rnd.nextFloat() - 0.5f) * 60f * spread;			// in font px
		float shiftY = (rnd.nextFloat() - 0.5f) * 16f * spread;
		float lineH = 14f * avgK;
		float yBase = shiftY + liftLines * (lineH + 10f);

		float[] st = LyricsLook.starts(words);
		int active = current && !pause ? LyricsLook.wordAt(words, progress) : -1;
		boolean reveal = current && LyricsLook.skyReveal.getBool() && m.synced && !pause;

		float x = -total / 2f + shiftX;
		float unit = PX * base;	// blocks per font px
		for (int i = 0; i < words.size() && used < POOL.length; i++) {
			float wx = x + w[i] / 2f;
			x += w[i] + (i + 1 < k.length ? gap * (k[i] + k[i + 1]) / 2f : 0);
			float wy = yBase - wx * slope;		  // slant
			float a = alpha, pop = 1f;
			if (reveal) {
				boolean shown = progress >= st[i] - 0.01f;
				if (!shown) { a = 0f; revealAt[i] = 0; }
				else {
					if (revealAt[i] == 0) revealAt[i] = now;
					float t = Math.min(1f, (now - revealAt[i]) / 170f);
					a *= t;
					pop = 0.75f + 0.25f * ease(t);
				}
			}
			int color = (LyricsLook.skyHighlight.getBool() && i == active) ? hlCol : textCol;
			Display.TextDisplay e = POOL[used++];
			double ox = rx * wx * unit + ux * wy * unit;
			double oy = uy * wy * unit;
			double oz = rz * wx * unit + uz * wy * unit;
			SkyLyrics.place(e, cx + ox, cy + oy, cz + oz, base * k[i] * pop);
			// text alpha below ~10% is drawn fully opaque by Minecraft, so hidden words get empty text
			boolean hidden = a <= 0.1f;
			Component txt = SkyLyrics.plain(hidden ? "" : words.get(i), color, m);
			SkyLyrics.text(e, txt, SkyLyrics.alpha(hidden ? 0.11f : a), m, colors);
		}
		return used;
	}

	private static float ease(float t) { t = Math.max(0f, Math.min(1f, t)); return 1f - (1f - t) * (1f - t) * (1f - t); }
}
