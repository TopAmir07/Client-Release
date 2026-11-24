package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.Config;

/**
 * Looks for the Afrika main menu (Singleplayer / Multiplayer screen): button colour, shape, size,
 * see-through, text colour, background colour and which extras show. Saved in the mod's own
 * settings file (ui.ts*). Change them from the palette button on the menu's icon bar.
 */
public final class TitleStyle {
	/** name, idle fill, hover fill, border, glow */
	public static final Object[][] COLORS = {
			{"Pink", 0x3A1235, 0xE0438F, 0xFF7DBA, 0xFF4FA8},
			{"Purple", 0x24123F, 0x8B4FE0, 0xC09BFF, 0xA36CFF},
			{"Blue", 0x0F2440, 0x2F7FE0, 0x8CC4FF, 0x4FA8FF},
			{"Aqua", 0x0D3036, 0x1FB5B0, 0x8CF5EE, 0x4FFFF2},
			{"Green", 0x10301C, 0x2FB86A, 0x8CF0B0, 0x4FFF9A},
			{"Red", 0x3A0F14, 0xE03A4A, 0xFF8C96, 0xFF4F5E},
			{"Orange", 0x3A220F, 0xE07A2F, 0xFFBE8C, 0xFF9A4F},
			{"Gold", 0x3A300F, 0xD4A82F, 0xFFE08C, 0xFFD24F},
			{"Grey", 0x1A1A1A, 0x5A5A5A, 0xDDDDDD, 0xFFFFFF},
			{"Black", 0x050505, 0x222222, 0x777777, 0x999999}};
	public static final Object[][] TEXT = {{"White", 0xFFFFFF}, {"Pink", 0xFFB3D6}, {"Gold", 0xFFD86B}, {"Sky", 0x9AD8FF}, {"Mint", 0xA8FFCF}, {"Black", 0x111111}};
	public static final String[] SHAPES = {"Rounded", "Square", "Pill"};
	public static final String[] SEE = {"Normal", "Solid", "Glass"};
	public static final float[] SEE_A = {0.55F, 0.9F, 0.28F};
	public static final String[] SIZES = {"Normal", "Small", "Big"};
	public static final int[][] SIZE_WH = {{200, 20}, {160, 18}, {240, 24}};
	public static final String[] PLACES = {"Centre", "Left", "Right"};
	/** name, top, middle, bottom (index 0 keeps each background's own colours) */
	public static final Object[][] SKY = {
			{"Background's own", 0, 0, 0},
			{"Pink", 0xFF3A0D3A, 0xFFB8336E, 0xFFFF9A6B},
			{"Purple", 0xFF120826, 0xFF4A1C7A, 0xFF1A0A30},
			{"Ocean", 0xFF04142A, 0xFF0F4C7A, 0xFF0A2A40},
			{"Forest", 0xFF06180E, 0xFF145A34, 0xFF0A2414},
			{"Sunset", 0xFF2A0F1F, 0xFFC2452E, 0xFFFFB45A},
			{"Blood", 0xFF1A0306, 0xFF6A0F1C, 0xFF14040A},
			{"Midnight", 0xFF020208, 0xFF0E1230, 0xFF04040C},
			{"Grey", 0xFF101010, 0xFF3A3A3A, 0xFF141414}};

	private TitleStyle() {
	}

	private static int idx(String k, int n) {
		return Math.floorMod(Config.getInt(k, 0), n);
	}

	public static int color() {
		return idx("ui.tsColor", COLORS.length);
	}

	public static int idle() {
		return (int) COLORS[color()][1];
	}

	public static int hover() {
		return (int) COLORS[color()][2];
	}

	public static int border() {
		return (int) COLORS[color()][3];
	}

	public static int glow() {
		return (int) COLORS[color()][4];
	}

	public static int text() {
		return (int) TEXT[idx("ui.tsText", TEXT.length)][1];
	}

	public static int radius(int h) {
		return switch (idx("ui.tsShape", SHAPES.length)) {
			case 1 -> 1;
			case 2 -> h / 2;
			default -> Math.min(h / 2, 6);
		};
	}

	public static float see() {
		return SEE_A[idx("ui.tsSee", SEE.length)];
	}

	public static int[] size() {
		return SIZE_WH[idx("ui.tsSize", SIZES.length)];
	}

	public static int place() {
		return idx("ui.tsPlace", PLACES.length);
	}

	/** {top, middle, bottom} or null for the background's own colours. */
	public static int[] sky() {
		int i = idx("ui.tsSky", SKY.length);
		return i == 0 ? null : new int[]{(int) SKY[i][1], (int) SKY[i][2], (int) SKY[i][3]};
	}

	public static boolean on(String key) {
		return Config.getBool("ui.ts" + key, true);
	}

	/** The rows of the Customize menu: label, current value, action on click. */
	public static String[][] rows() {
		return new String[][]{
				{"Button colour", (String) COLORS[color()][0], "cycle:ui.tsColor:" + COLORS.length},
				{"Text colour", (String) TEXT[idx("ui.tsText", TEXT.length)][0], "cycle:ui.tsText:" + TEXT.length},
				{"Button shape", SHAPES[idx("ui.tsShape", SHAPES.length)], "cycle:ui.tsShape:" + SHAPES.length},
				{"See-through", SEE[idx("ui.tsSee", SEE.length)], "cycle:ui.tsSee:" + SEE.length},
				{"Button size", SIZES[idx("ui.tsSize", SIZES.length)], "cycle:ui.tsSize:" + SIZES.length},
				{"Buttons", PLACES[place()], "cycle:ui.tsPlace:" + PLACES.length},
				{"Background colour", (String) SKY[idx("ui.tsSky", SKY.length)][0], "cycle:ui.tsSky:" + SKY.length},
				{"Your skin", on("Skin") ? "On" : "Off", "flip:ui.tsSkin"},
				{"Minecraft logo", on("Logo") ? "On" : "Off", "flip:ui.tsLogo"},
				{"Stars", on("Stars") ? "On" : "Off", "flip:ui.tsStars"},
				{"Shooting stars", on("Shooting") ? "On" : "Off", "flip:ui.tsShooting"},
				{"Petals", on("Petals") ? "On" : "Off", "flip:ui.tsPetals"},
				{"Corner text", on("Corners") ? "On" : "Off", "flip:ui.tsCorners"},
				{"Reset all", "", "reset"}};
	}

	/** Runs a row's action. back = true goes one step back (right click). */
	public static void apply(String action, boolean back) {
		String[] a = action.split(":");
		switch (a[0]) {
			case "cycle" -> Config.setInt(a[1], Math.floorMod(Config.getInt(a[1], 0) + (back ? -1 : 1), Integer.parseInt(a[2])));
			case "flip" -> Config.setBool(a[1], !Config.getBool(a[1], true));
			case "reset" -> {
				for (String k : new String[]{"ui.tsColor", "ui.tsText", "ui.tsShape", "ui.tsSee", "ui.tsSize", "ui.tsPlace", "ui.tsSky"}) {
					Config.setInt(k, 0);
				}
				for (String k : new String[]{"ui.tsSkin", "ui.tsLogo", "ui.tsStars", "ui.tsShooting", "ui.tsPetals", "ui.tsCorners"}) {
					Config.setBool(k, true);
				}
			}
			default -> {
			}
		}
		Config.saveIfNeeded(true);
	}
}
