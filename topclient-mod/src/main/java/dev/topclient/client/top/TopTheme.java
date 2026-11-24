package dev.topclient.client.top;

import dev.topclient.client.clover.Config;

/**
 * The one place every TopClient colour comes from. Screens read the public fields; nothing else
 * hard-codes a colour. {@link #refresh()} runs once per frame (cheap: a few integer mixes, no
 * allocations) and again whenever a setting changes, so edits show up live.
 *
 * All values are 0xRRGGBB unless the name says ARGB. Settings live in the normal config under
 * "top.theme.*" and stay the same across profiles.
 */
public final class TopTheme {
	private TopTheme() {
	}

	/** id, name, accent, second accent (for gradients). Obsidian is the default. */
	public record Preset(String id, String name, int accent, int accent2) {
	}

	public static final Preset[] PRESETS = {
			new Preset("obsidian", "Obsidian", 0x7C3AED, 0x4C1D95),
			new Preset("sapphire", "Sapphire", 0x3B82F6, 0x1E40AF),
			new Preset("emerald", "Emerald", 0x10B981, 0x047857),
			new Preset("crimson", "Crimson", 0xEF4444, 0x991B1B),
			new Preset("snow", "Snow", 0xF5F5F5, 0x9CA3AF),
			new Preset("amber", "Amber", 0xF97316, 0xC2410C),
			new Preset("rose", "Rose", 0xF472B6, 0xBE185D),
			new Preset("cyan", "Cyan", 0x22D3EE, 0x0E7490),
			new Preset("gold", "Gold", 0xFACC15, 0xA16207),
	};

	public static final int DEF_ENABLED = 0x22C55E, DEF_DISABLED = 0xEF4444, DEF_LOCKED = 0x52525B;

	// ---- live colours (read these) ----
	public static int ACCENT, ACCENT2, ON_ACCENT;
	public static int BG, PANEL, PANEL2, CARD, CARD_HOVER, FIELD, BORDER, BORDER_HOVER;
	public static int TEXT, TEXT_DIM, TEXT_MUTE;
	public static int ENABLED, DISABLED, LOCKED;
	/** 0..255 opacity of the menu window and the darkening behind it. */
	public static int WINDOW_ALPHA, DIM_ALPHA;
	/** Background blur strength 0..10 (0 = off). */
	public static int BLUR;
	public static boolean RAINBOW, GRADIENT;

	private static long rainbowT;

	static {
		refresh();
	}

	// ---- settings ----
	public static Preset preset() {
		String id = Config.get("top.theme.preset", "obsidian");
		for (Preset p : PRESETS) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return PRESETS[0];
	}

	public static void applyPreset(Preset p) {
		Config.set("top.theme.preset", p.id());
		Config.set("top.theme.accent", null);
		Config.set("top.theme.accent2", null);
		refresh();
	}

	public static int baseAccent() {
		return Config.getInt("top.theme.accent", preset().accent()) & 0xFFFFFF;
	}

	public static void setAccent(int rgb) {
		Config.setInt("top.theme.accent", rgb & 0xFFFFFF);
		refresh();
	}

	public static void setAccent2(int rgb) {
		Config.setInt("top.theme.accent2", rgb & 0xFFFFFF);
		refresh();
	}

	public static int baseAccent2() {
		String own = Config.get("top.theme.accent2", null);
		if (own != null) {
			return Config.getInt("top.theme.accent2", 0) & 0xFFFFFF;
		}
		if (Config.get("top.theme.accent", null) == null) {
			return preset().accent2();
		}
		return mix(baseAccent(), 0x000000, 0.45F);
	}

	public static int status(String which, int def) {
		return Config.getInt("top.theme.status." + which, def) & 0xFFFFFF;
	}

	public static void setStatus(String which, int rgb) {
		Config.setInt("top.theme.status." + which, rgb & 0xFFFFFF);
		refresh();
	}

	public static int windowOpacity() {
		return clamp(Config.getInt("top.theme.opacity", 92), 40, 100);
	}

	public static void setWindowOpacity(int v) {
		Config.setInt("top.theme.opacity", clamp(v, 40, 100));
		refresh();
	}

	public static int dim() {
		return clamp(Config.getInt("top.theme.dim", 45), 0, 90);
	}

	public static void setDim(int v) {
		Config.setInt("top.theme.dim", clamp(v, 0, 90));
		refresh();
	}

	public static int blur() {
		return clamp(Config.getInt("top.theme.blur", 5), 0, 10);
	}

	public static void setBlur(int v) {
		Config.setInt("top.theme.blur", clamp(v, 0, 10));
		refresh();
	}

	public static boolean rainbow() {
		return Config.getBool("top.theme.rainbow", false);
	}

	public static void setRainbow(boolean on) {
		Config.setBool("top.theme.rainbow", on);
		refresh();
	}

	public static boolean gradient() {
		return Config.getBool("top.theme.gradient", true);
	}

	public static void setGradient(boolean on) {
		Config.setBool("top.theme.gradient", on);
		refresh();
	}

	public static float rainbowSpeed() {
		return Math.max(0.1F, Math.min(3F, Config.getFloat("top.theme.rainbowSpeed", 1F)));
	}

	public static void setRainbowSpeed(float v) {
		Config.setFloat("top.theme.rainbowSpeed", Math.max(0.1F, Math.min(3F, v)));
	}

	/** Back to Obsidian with every option at its default. */
	public static void reset() {
		Config.resetAll("top.theme.");
		refresh();
	}

	// ---- derived colours ----
	public static void refresh() {
		RAINBOW = rainbow();
		GRADIENT = gradient();
		int a = baseAccent();
		int a2 = baseAccent2();
		if (RAINBOW) {
			float hue = (System.currentTimeMillis() % 100000L) / 1000F * 0.12F * rainbowSpeed();
			a = hsv(hue % 1F, 0.75F, 1F);
			a2 = hsv((hue + 0.12F) % 1F, 0.8F, 0.7F);
		}
		ACCENT = a;
		ACCENT2 = GRADIENT ? a2 : a;
		ON_ACCENT = luminance(a) > 0.6F ? 0x0A0A0A : 0xFFFFFF;
		// OLED black with a faint tint of the accent so panels read as one family
		BG = 0x000000;
		PANEL = mix(0x08080A, a, 0.035F);
		PANEL2 = mix(0x0E0E12, a, 0.05F);
		CARD = mix(0x111115, a, 0.05F);
		CARD_HOVER = mix(0x18181E, a, 0.09F);
		FIELD = mix(0x060608, a, 0.03F);
		BORDER = mix(0x24242C, a, 0.18F);
		BORDER_HOVER = mix(0x34343E, a, 0.55F);
		TEXT = 0xF4F4F6;
		TEXT_DIM = 0xB4B4BE;
		TEXT_MUTE = 0x7A7A86;
		ENABLED = status("enabled", DEF_ENABLED);
		DISABLED = status("disabled", DEF_DISABLED);
		LOCKED = status("locked", DEF_LOCKED);
		WINDOW_ALPHA = Math.round(windowOpacity() * 2.55F);
		DIM_ALPHA = Math.round(dim() * 2.55F);
		BLUR = blur();
	}

	/** Called by screens once per frame: only does work when the rainbow is moving. */
	public static void tick() {
		if (RAINBOW) {
			long now = System.currentTimeMillis();
			if (now - rainbowT > 30) {
				rainbowT = now;
				refresh();
			}
		}
	}

	/** Accent for something at position t (0..1) along a gradient: used for bars and the logo. */
	public static int accentAt(float t) {
		return GRADIENT ? mix(ACCENT, ACCENT2, Math.max(0F, Math.min(1F, t))) : ACCENT;
	}

	// ---- colour maths (no allocations) ----
	public static int argb(int a, int rgb) {
		return (clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	public static int mix(int c1, int c2, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
		int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
		int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	public static float luminance(int c) {
		return (0.2126F * ((c >> 16) & 0xFF) + 0.7152F * ((c >> 8) & 0xFF) + 0.0722F * (c & 0xFF)) / 255F;
	}

	public static int hsv(float h, float s, float v) {
		return java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
	}

	/** h, s, v in 0..1 written into out (so callers can reuse one array). */
	public static void toHsv(int rgb, float[] out) {
		java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, out);
	}

	public static String hex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	/** Parses "#RRGGBB", "RRGGBB" or "#RGB"; -1 when it isn't a colour. */
	public static int parseHex(String s) {
		if (s == null) {
			return -1;
		}
		s = s.strip();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (s.length() == 3) {
			s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
		}
		if (s.length() != 6) {
			return -1;
		}
		try {
			return Integer.parseInt(s, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
