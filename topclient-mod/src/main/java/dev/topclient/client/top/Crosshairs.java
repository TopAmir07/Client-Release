package dev.topclient.client.top;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.UtilityModules.CrosshairColor;
import dev.topclient.client.clover.module.UtilityModules.CrosshairColor.Look;

import java.util.ArrayList;
import java.util.List;

/**
 * Crosshair presets and your own drawings. A drawing is a 15x15 grid of pixels stored as four
 * longs (hex text in the config). Saved drawings live in the config too, so profiles keep them.
 */
public final class Crosshairs {
	private Crosshairs() {
	}

	public static final int GRID = CrosshairColor.GRID;

	public record Preset(String name, Look look) {
	}

	public record Drawing(String id, String name, long[] bits) {
	}

	// ------------------------------------------------------------------ bits

	public static boolean get(long[] b, int x, int y) {
		if (b == null || x < 0 || y < 0 || x >= GRID || y >= GRID) {
			return false;
		}
		int i = y * GRID + x;
		return (b[i >> 6] >>> (i & 63) & 1L) != 0;
	}

	public static void set(long[] b, int x, int y, boolean on) {
		if (x < 0 || y < 0 || x >= GRID || y >= GRID) {
			return;
		}
		int i = y * GRID + x;
		if (on) {
			b[i >> 6] |= 1L << (i & 63);
		} else {
			b[i >> 6] &= ~(1L << (i & 63));
		}
	}

	public static long[] empty() {
		return new long[4];
	}

	public static String hex(long[] b) {
		StringBuilder sb = new StringBuilder();
		for (long v : b) {
			sb.append(String.format("%016x", v));
		}
		return sb.toString();
	}

	public static long[] parse(String s) {
		long[] b = empty();
		if (s == null || s.length() != 64) {
			return b;
		}
		try {
			for (int i = 0; i < 4; i++) {
				b[i] = Long.parseUnsignedLong(s.substring(i * 16, i * 16 + 16), 16);
			}
		} catch (NumberFormatException e) {
			return empty();
		}
		return b;
	}

	/** Pixel art, 15 rows of 15 characters ('#' = on). */
	private static long[] art(String... rows) {
		long[] b = empty();
		for (int y = 0; y < rows.length && y < GRID; y++) {
			for (int x = 0; x < rows[y].length() && x < GRID; x++) {
				if (rows[y].charAt(x) == '#') {
					set(b, x, y, true);
				}
			}
		}
		return b;
	}

	// ------------------------------------------------------------------ the drawing in use

	private static String cachedHex;
	private static long[] cachedBits = empty();

	public static long[] currentBits() {
		String h = Config.get("top.xhair.current", null);
		if (h == null) {
			return BUILT_IN.get(0).bits();
		}
		if (!h.equals(cachedHex)) {
			cachedHex = h;
			cachedBits = parse(h);
		}
		return cachedBits;
	}

	public static void setCurrent(long[] b) {
		Config.set("top.xhair.current", hex(b));
	}

	// ------------------------------------------------------------------ presets

	private static Look look(int shape, float size, float gap, float thick, boolean outline, boolean dot) {
		return new Look(shape, size, gap, thick, outline, dot, 0xFFFFFF, 1F, null);
	}

	public static final List<Preset> PRESETS = List.of(
			new Preset("Game default", look(0, 6, 3, 2, true, false)),
			new Preset("Classic", look(1, 6, 0, 2, true, false)),
			new Preset("Gap cross", look(2, 5, 3, 2, true, false)),
			new Preset("Gap + dot", look(2, 5, 3, 2, true, true)),
			new Preset("Small", look(2, 3, 2, 1, true, false)),
			new Preset("Dot", look(3, 4, 0, 3, true, false)),
			new Preset("Circle", look(4, 4, 3, 1, true, true)),
			new Preset("T-shape", look(5, 5, 3, 2, true, false)),
			new Preset("X", look(6, 4, 1, 1, true, false)),
			new Preset("Thick", look(2, 7, 4, 4, true, false)));

	public static final List<Drawing> BUILT_IN = List.of(
			new Drawing("b:diamond", "Diamond", art(
					"", "", "", "", "",
					"       #       ",
					"      # #      ",
					"     #   #     ",
					"      # #      ",
					"       #       ")),
			new Drawing("b:brackets", "Brackets", art(
					"", "", "", "",
					"    ##   ##    ",
					"    #     #    ",
					"               ",
					"       #       ",
					"               ",
					"    #     #    ",
					"    ##   ##    ")),
			new Drawing("b:arrow", "Arrow", art(
					"", "", "", "", "", "",
					"       #       ",
					"      ###      ",
					"     ## ##     ",
					"    ##   ##    ")),
			new Drawing("b:square", "Square", art(
					"", "", "", "", "",
					"     #####     ",
					"     #   #     ",
					"     # # #     ",
					"     #   #     ",
					"     #####     ")),
			new Drawing("b:plus", "Big plus", art(
					"", "", "",
					"       #       ",
					"       #       ",
					"       #       ",
					"               ",
					"   ###   ###   ",
					"               ",
					"       #       ",
					"       #       ",
					"       #       ")));

	// ------------------------------------------------------------------ saved drawings

	public static List<Drawing> saved() {
		List<Drawing> out = new ArrayList<>();
		for (String id : Config.get("top.xhair.saved", "").split(",")) {
			if (id.isBlank()) {
				continue;
			}
			String bits = Config.get("top.xhair.s." + id + ".bits", null);
			if (bits == null) {
				continue;
			}
			out.add(new Drawing(id, Config.get("top.xhair.s." + id + ".name", "Drawing"), parse(bits)));
		}
		return out;
	}

	/** Saves a new drawing; returns its id. At most 40 are kept. */
	public static String saveNew(String name, long[] bits) {
		List<Drawing> all = saved();
		if (all.size() >= 40) {
			return null;
		}
		String id = Long.toString(System.currentTimeMillis(), 36);
		Config.set("top.xhair.s." + id + ".name", clean(name));
		Config.set("top.xhair.s." + id + ".bits", hex(bits));
		StringBuilder sb = new StringBuilder(id);
		for (Drawing d : all) {
			sb.append(',').append(d.id());
		}
		Config.set("top.xhair.saved", sb.toString());
		Config.saveIfNeeded(true);
		return id;
	}

	public static void update(String id, String name, long[] bits) {
		Config.set("top.xhair.s." + id + ".name", clean(name));
		Config.set("top.xhair.s." + id + ".bits", hex(bits));
		Config.saveIfNeeded(true);
	}

	public static void delete(String id) {
		StringBuilder sb = new StringBuilder();
		for (Drawing d : saved()) {
			if (!d.id().equals(id)) {
				if (sb.length() > 0) {
					sb.append(',');
				}
				sb.append(d.id());
			}
		}
		Config.set("top.xhair.saved", sb.toString());
		Config.set("top.xhair.s." + id + ".name", null);
		Config.set("top.xhair.s." + id + ".bits", null);
		Config.saveIfNeeded(true);
	}

	public static String clean(String name) {
		String n = name == null ? "" : name.replaceAll("[\\r\\n,]", " ").trim();
		if (n.isEmpty()) {
			n = "My crosshair";
		}
		return n.length() > 24 ? n.substring(0, 24) : n;
	}
}
