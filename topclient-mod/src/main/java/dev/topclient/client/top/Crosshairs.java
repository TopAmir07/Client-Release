package dev.topclient.client.top;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.UtilityModules.CrosshairColor;
import dev.topclient.client.clover.module.UtilityModules.CrosshairColor.Look;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;

/**
 * Crosshair presets and your own drawings. A drawing is a 15x15 grid of pixels stored as four
 * longs (hex text in the config). Saved drawings live in the config too, so profiles keep them.
 */
public final class Crosshairs {
	private Crosshairs() {
	}

	/** Maximum backing canvas. The editor defaults to a centred 7x7 window. */
	public static final int GRID = CrosshairColor.GRID;
	private static final Deque<long[]> UNDO = new ArrayDeque<>();
	private static final Deque<long[]> REDO = new ArrayDeque<>();
	private static final int HISTORY_LIMIT = 60;

	public static int gridSize() {
		int value = Config.getInt("top.xhair.gridSize", 7);
		return value == 7 || value == 9 || value == 11 || value == 13 || value == 15 ? value : 7;
	}

	public static int gridOffset() {
		return (GRID - gridSize()) / 2;
	}

	public static void setGridSize(int size) {
		if (size != 7 && size != 9 && size != 11 && size != 13 && size != 15) return;
		int oldSize = gridSize();
		if (oldSize == size) return;
		long[] before = currentBits().clone();
		int oldOffset = (GRID - oldSize) / 2;
		int newOffset = (GRID - size) / 2;
		long[] resized = empty();
		int copy = Math.min(oldSize, size);
		int oldStart = oldOffset + (oldSize - copy) / 2;
		int newStart = newOffset + (size - copy) / 2;
		for (int y = 0; y < copy; y++) {
			for (int x = 0; x < copy; x++) {
				set(resized, newStart + x, newStart + y, get(before, oldStart + x, oldStart + y));
			}
		}
		pushUndo(before);
		setCurrentRaw(resized);
		Config.setInt("top.xhair.gridSize", size);
		Config.saveIfNeeded(true);
	}

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
		long[] before = currentBits().clone();
		if (!java.util.Arrays.equals(before, b)) pushUndo(before);
		setCurrentRaw(b);
	}

	private static void setCurrentRaw(long[] b) {
		Config.set("top.xhair.current", hex(b));
		cachedHex = null;
	}

	private static void pushUndo(long[] bits) {
		UNDO.push(bits.clone());
		while (UNDO.size() > HISTORY_LIMIT) UNDO.removeLast();
		REDO.clear();
	}

	public static boolean canUndo() { return !UNDO.isEmpty(); }
	public static boolean canRedo() { return !REDO.isEmpty(); }

	public static void undo() {
		if (UNDO.isEmpty()) return;
		REDO.push(currentBits().clone());
		setCurrentRaw(UNDO.pop());
		Config.saveIfNeeded(true);
	}

	public static void redo() {
		if (REDO.isEmpty()) return;
		UNDO.push(currentBits().clone());
		setCurrentRaw(REDO.pop());
		Config.saveIfNeeded(true);
	}

	/** Compact, versioned share code. It contains the grid and the visible crosshair settings. */
	public static String exportShareCode() {
		Look l = CrosshairColor.current();
		String payload = "1|" + gridSize() + "|" + hex(currentBits()) + "|" + l.shape() + "|"
				+ l.size() + "|" + l.gap() + "|" + l.thick() + "|" + l.rgb() + "|" + l.alpha()
				+ "|" + l.outline() + "|" + l.dot();
		return "TCX1-" + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/** Validates the complete payload before changing the active crosshair. */
	public static boolean importShareCode(String code) {
		try {
			if (code == null || !code.startsWith("TCX1-") || code.length() > 2048) return false;
			String decoded = new String(Base64.getUrlDecoder().decode(code.substring(5)), StandardCharsets.UTF_8);
			String[] p = decoded.split("\\|", -1);
			if (p.length != 11 || !p[0].equals("1") || !p[2].matches("[0-9a-fA-F]{64}")
					|| !(p[9].equals("true") || p[9].equals("false"))
					|| !(p[10].equals("true") || p[10].equals("false"))) return false;
			int size = Integer.parseInt(p[1]);
			if (size != 7 && size != 9 && size != 11 && size != 13 && size != 15) return false;
			long[] bits = parse(p[2]);
			int shape = Integer.parseInt(p[3]);
			float crossSize = Float.parseFloat(p[4]), gap = Float.parseFloat(p[5]), thick = Float.parseFloat(p[6]);
			int rgb = Integer.parseInt(p[7]);
			float alpha = Float.parseFloat(p[8]);
			boolean outline = Boolean.parseBoolean(p[9]), dot = Boolean.parseBoolean(p[10]);
			if (shape < 0 || shape > CrosshairColor.SHAPE_DRAWING || !Float.isFinite(crossSize)
					|| !Float.isFinite(gap) || !Float.isFinite(thick) || !Float.isFinite(alpha)
					|| crossSize < 2 || crossSize > 20 || gap < 0 || gap > 10 || thick < 1 || thick > 6
					|| alpha < 0 || alpha > 1 || rgb < 0 || rgb > 0xFFFFFF) return false;
			setGridSize(size);
			setCurrent(bits);
			CrosshairColor.apply(new Look(shape, crossSize, gap, thick, outline, dot, rgb, alpha, bits));
			CrosshairColor.setting("color").setColor(rgb);
			CrosshairColor.setting("alpha").setFloat(alpha);
			Config.saveIfNeeded(true);
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
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
