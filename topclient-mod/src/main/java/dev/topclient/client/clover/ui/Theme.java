package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.Config;

public final class Theme {
	/** Default colour for mod settings: the TopClient (Obsidian) accent. Kept under its old name. */
	public static final int CLOVER_GREEN = 0x7C3AED;
	public static final int CLOVER_DARK = 0x0B1F14;

	public record Preset(String id, String name, int accent, int panel, int panel2, int card, int cardHover,
						 int text, int textDim, int bgTop, int bgBottom, int font, int corners) {
	}

	public static final Preset[] PRESETS = {
			// TopClient default: OLED black, follows the TopClient accent colour
			new Preset("obsidian", "Obsidian", 0x7C3AED, 0x000000, 0x08080B, 0x0A0A0E, 0x15131C, 0xF4F4F6, 0x9A98A6, 0x120A22, 0x000000, 0, 1),
			new Preset("clover", "Clover", 0x2ECC71, 0x0A1A10, 0x10281A, 0x0E2418, 0x163826, 0xE9F7EE, 0x8DB59C, 0x0F3B22, 0x04140B, 0, 1),
			new Preset("crimson", "Crimson Dawn", 0xD7263D, 0x140A0D, 0x1E1014, 0x1A0D11, 0x2A141A, 0xF3E6E8, 0xB08A90, 0x3A0F16, 0x0E0507, 2, 1),
			new Preset("midnight", "Midnight", 0x4DA3FF, 0x0B1020, 0x121A30, 0x0F1628, 0x1A2440, 0xE6EEFF, 0x8C9BC0, 0x14204A, 0x050812, 1, 2),
			new Preset("sakura", "Sakura", 0xFF7EB6, 0x1E1018, 0x2A1622, 0x24131D, 0x34202C, 0xFFE9F3, 0xC99AB2, 0x4A2036, 0x12060C, 4, 2),
			new Preset("sunset", "Sunset", 0xFF8A3D, 0x1E120A, 0x2A1A10, 0x24160D, 0x352214, 0xFFF0E2, 0xC9A585, 0x5A2A10, 0x140804, 1, 1),
			new Preset("royal", "Royal Gold", 0xFFC83C, 0x160F24, 0x201634, 0x1B122C, 0x2A1E42, 0xF4EEFF, 0xA99BC8, 0x3A1F66, 0x0C0618, 2, 1),
			new Preset("ocean", "Ocean", 0x00C2A8, 0x06181A, 0x0B2427, 0x091F22, 0x113236, 0xE2FBF8, 0x86B8B2, 0x0C3D44, 0x03100F, 1, 2),
			new Preset("mono", "Mono", 0xFFFFFF, 0x0E0E0E, 0x161616, 0x131313, 0x222222, 0xF2F2F2, 0x9A9A9A, 0x2A2A2A, 0x050505, 3, 0),
			new Preset("neon", "Neon", 0x00F0FF, 0x0A0614, 0x140C24, 0x100A1E, 0x1E1236, 0xEFFFFF, 0x9AA6D6, 0x2A0A4A, 0x05020C, 6, 0),
			new Preset("forest", "Deep Forest", 0x8BC34A, 0x101A0C, 0x182612, 0x142010, 0x22341A, 0xEEF6E6, 0xA1B88E, 0x23401A, 0x070D05, 2, 1),
			new Preset("coffee", "Coffee", 0xC8925A, 0x1A120D, 0x241913, 0x20160F, 0x30221A, 0xF5ECE3, 0xBBA38D, 0x3D2A1C, 0x0D0805, 2, 1),
			new Preset("lava", "Lava", 0xFF3B1F, 0x180A06, 0x24100A, 0x1E0D08, 0x30160E, 0xFFEDE6, 0xC49284, 0x4A1408, 0x0E0302, 5, 0),
			new Preset("arctic", "Arctic", 0x9AD8FF, 0x0E1820, 0x16242E, 0x122028, 0x1C3040, 0xF0FAFF, 0x9DB6C6, 0x2C4A60, 0x0A1218, 7, 2),
			new Preset("savanna", "Savanna", 0xFCD116, 0x0B140D, 0x13221A, 0x0F1C15, 0x1B3226, 0xF4F7EC, 0xA8B89A, 0x00451F, 0x1A0507, 7, 1),
	};

	private Theme() {
	}

	public static Preset preset() {
		String id = Config.get("ui.theme", "obsidian");
		for (Preset p : PRESETS) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return PRESETS[0];
	}

	public static void applyPreset(Preset p) {
		Config.set("ui.theme", p.id());
		Config.set("ui.accent", null);
		Config.setInt("ui.font", p.font());
		Config.setInt("ui.corners", p.corners());
		Config.saveIfNeeded(true);
	}

	public static int accent() {
		Preset p = preset();
		int def = p.id().equals("obsidian") ? dev.topclient.client.top.TopTheme.ACCENT : p.accent();
		return Config.getInt("ui.accent", def) & 0xFFFFFF;
	}

	public static void setAccent(int rgb) {
		Config.setInt("ui.accent", rgb & 0xFFFFFF);
	}

	public static int font() {
		int f = Config.getInt("ui.font", preset().font());
		return f < 0 || f >= CloverFont.FAMILIES.length ? 0 : f;
	}

	public static void setFont(int f) {
		Config.setInt("ui.font", Math.floorMod(f, CloverFont.FAMILIES.length));
	}

	public static int opacity() {
		return Math.max(30, Math.min(100, Config.getInt("ui.opacity", 92)));
	}

	public static void setOpacity(int v) {
		Config.setInt("ui.opacity", Math.max(30, Math.min(100, v)));
	}

	public static int darken() {
		return Math.max(0, Math.min(100, Config.getInt("ui.darken", 45)));
	}

	public static void setDarken(int v) {
		Config.setInt("ui.darken", Math.max(0, Math.min(100, v)));
	}

	public static String backgroundPath() {
		return Config.get("ui.bg", "");
	}

	public static void setBackgroundPath(String p) {
		Config.set("ui.bg", p == null ? "" : p);
	}

	public static int corners() {
		return Math.max(0, Math.min(2, Config.getInt("ui.corners", preset().corners())));
	}

	public static void setCorners(int v) {
		Config.setInt("ui.corners", Math.max(0, Math.min(2, v)));
	}

	public static int size() {
		return Math.max(0, Math.min(2, Config.getInt("ui.size", 1)));
	}

	public static void setSize(int v) {
		Config.setInt("ui.size", Math.max(0, Math.min(2, v)));
	}

	public static int argb(int rgb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	public static int panelRgb() {
		return preset().panel();
	}

	public static int panel() {
		return argb(preset().panel(), 255 * opacity() / 100);
	}

	public static int panel2() {
		return argb(preset().panel2(), 255 * opacity() / 100);
	}

	public static int card() {
		return argb(preset().card(), Math.min(255, 255 * opacity() / 100));
	}

	public static int cardHover() {
		return argb(preset().cardHover(), Math.min(255, 255 * opacity() / 100));
	}

	public static int border() {
		return 0xFF000000 | mix(accent(), preset().panel(), 0.45F);
	}

	public static int borderBright() {
		return 0xFF000000 | accent();
	}

	public static int text() {
		return 0xFF000000 | preset().text();
	}

	public static int textDim() {
		return 0xFF000000 | preset().textDim();
	}

	public static int accentArgb() {
		return 0xFF000000 | accent();
	}

	public static int accentDark() {
		return 0xFF000000 | mix(accent(), 0x000000, 0.55F);
	}

	public static int bgTop() {
		return 0xFF000000 | preset().bgTop();
	}

	public static int bgBottom() {
		return 0xFF000000 | preset().bgBottom();
	}

	public static int popup() {
		return argb(mix(preset().panel2(), 0x000000, 0.2F), 240);
	}

	public static int field() {
		return 0xFF000000 | mix(preset().panel(), 0x000000, 0.4F);
	}

	public static int track() {
		return 0xFF000000 | mix(preset().cardHover(), 0x555555, 0.25F);
	}

	public static int light() {
		return mix(preset().text(), accent(), 0.15F);
	}

	public static int mix(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = Math.round(ar + (br - ar) * t);
		int g = Math.round(ag + (bg - ag) * t);
		int bl = Math.round(ab + (bb - ab) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static int hsb(float h, float s, float b) {
		return java.awt.Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
	}
}
