package dev.topclient.client.visuals;

import dev.topclient.client.clover.Config;
import dev.topclient.client.gui.Draw;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class VisualsLook {

	public enum Look {
		MINECRAFT("Minecraft"), CLASSIC("Classic"), MODERN("Modern UI"), GLASS("Glass"), FLAT("Flat");

		public final String label;

		Look(String label) {
			this.label = label;
		}
	}

	public enum Size {
		SMALL("Small", 0.85F), MEDIUM("Medium", 1.0F), LARGE("Large", 1.2F);

		public final String label;
		public final float scale;

		Size(String label, float scale) {
			this.label = label;
			this.scale = scale;
		}
	}

	public record Theme(String id, String name, int base, int accent, int accent2, String deco, String icon) {
	}

	public static final List<Theme> THEMES = List.of(
			new Theme("glass", "Glass", 0x10141C, 0x75D4FF, 0x4F8CFF, null, "2728"),
			new Theme("halloween", "Halloween", 0x160E08, 0xFF8A1F, 0xB24BFF, "halloween", "1f383"),
			new Theme("crimson", "Crimson", 0x180A0D, 0xFF5A6E, 0xC2185B, null, "1f525"),
			new Theme("amethyst", "Amethyst", 0x130E1C, 0xB388FF, 0x7C4DFF, null, "1f48e"),
			new Theme("amber", "Amber", 0x17110A, 0xFFB547, 0xFF7A1F, null, "2b50"),
			new Theme("sakura", "Sakura", 0x1A0E15, 0xFF9CCF, 0xFF6FA8, "sakura", "1f338"),
			new Theme("mono", "Mono", 0x111113, 0xE8E8EA, 0x9A9AA2, null, "1f319"),
			new Theme("ocean", "Ocean", 0x08131C, 0x3FC8FF, 0x1E6BFF, "bubbles", "1f30a"),
			new Theme("emerald", "Emerald", 0x0A1611, 0x34D399, 0x0EA371, null, "1f340"),
			new Theme("sunset", "Sunset", 0x1A0D10, 0xFF7E5F, 0xFEB47B, null, "1f305"),
			new Theme("midnight", "Midnight", 0x070912, 0x6E7BFF, 0x2B2FA8, "stars", "1f319"),
			new Theme("neon", "Neon", 0x0B0714, 0x00F5D4, 0xF15BB5, null, "26a1"),
			new Theme("christmas", "Christmas", 0x0B140E, 0xE53935, 0x43A047, "snow", "1f384"),
			new Theme("lava", "Lava", 0x160805, 0xFF4500, 0xFFB000, "embers", "1f30b"),
			new Theme("ice", "Ice", 0x0B1219, 0xA5F3FC, 0x60A5FA, "snow", "2744"),
			new Theme("gold", "Gold", 0x15120A, 0xFFD54F, 0xC8A132, null, "1f451"),
			new Theme("savanna", "Savanna", 0x120F0A, 0xFFB300, 0x2E9D4F, null, "1f30d"),
			new Theme("galaxy", "Galaxy", 0x0A0716, 0xC77DFF, 0x5A67F2, "stars", "1f30c"),
			new Theme("mint", "Mint", 0x0B1513, 0x7CF5C4, 0x3DD6B5, null, "1f33f")
	);

	public static final int[] SWATCHES = {0x75D4FF, 0x4F8CFF, 0xB388FF, 0xFF9CCF, 0xFF5A6E, 0xFFB547, 0xFFE45C, 0xE8E8EA};
	public static final int MAX_PICTURES = 8;
	public static final long MAX_PICTURE_BYTES = 25L * 1024 * 1024;

	public static Look look = Look.GLASS;
	public static Size size = Size.MEDIUM;
	public static int opacity = 92;
	public static int accent = 0x75D4FF;
	public static String theme = "glass";
	public static int darken = 47;
	public static int picture = -1;
	public static final List<String> pictures = new ArrayList<>();
	public static boolean decorations = true;

	private static boolean loaded;

	private VisualsLook() {
	}

	private static Path file() {
		return Config.dir().resolve("visuals-customize.properties");
	}

	public static Path pictureDir() {
		return Config.dir().resolve("visuals").resolve("backgrounds");
	}

	public static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(file())) {
			p.load(in);
		} catch (Exception ignored) {
			return;
		}
		try {
			look = Look.valueOf(p.getProperty("look", look.name()));
		} catch (Exception ignored) {
		}
		try {
			size = Size.valueOf(p.getProperty("size", size.name()));
		} catch (Exception ignored) {
		}
		opacity = clamp(num(p, "opacity", opacity), 30, 100);
		darken = clamp(num(p, "darken", darken), 0, 90);
		accent = parseHex(p.getProperty("accent"), accent);
		String t = p.getProperty("theme", theme);
		if (themeById(t) != null) {
			theme = t;
		}
		decorations = !"false".equals(p.getProperty("decorations"));
		pictures.clear();
		for (int i = 0; i < MAX_PICTURES; i++) {
			String name = p.getProperty("picture." + i);
			if (name != null && name.matches("[A-Za-z0-9_.-]+") && Files.isRegularFile(pictureDir().resolve(name))) {
				pictures.add(name);
			}
		}
		picture = clamp(num(p, "picture", -1), -1, pictures.size() - 1);
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("look", look.name());
		p.setProperty("size", size.name());
		p.setProperty("opacity", String.valueOf(opacity));
		p.setProperty("darken", String.valueOf(darken));
		p.setProperty("accent", hex(accent));
		p.setProperty("theme", theme);
		p.setProperty("decorations", String.valueOf(decorations));
		p.setProperty("picture", String.valueOf(picture));
		for (int i = 0; i < pictures.size(); i++) {
			p.setProperty("picture." + i, pictures.get(i));
		}
		try {
			Files.createDirectories(Config.dir());
			try (OutputStream out = Files.newOutputStream(file())) {
				p.store(out, "TopClient Visuals look (stays on this PC)");
			}
		} catch (Exception ignored) {
		}
	}

	public static void reset() {
		look = Look.GLASS;
		size = Size.MEDIUM;
		opacity = 92;
		accent = 0x75D4FF;
		theme = "glass";
		darken = 47;
		picture = -1;
		decorations = true;
		save();
	}

	public static void useTheme(Theme t) {
		theme = t.id();
		accent = t.accent();
		save();
	}

	public static Theme themeById(String id) {
		for (Theme t : THEMES) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	public static Theme theme() {
		Theme t = themeById(theme);
		return t == null ? THEMES.get(0) : t;
	}

	public static String addPicture(Path source) throws Exception {
		if (pictures.size() >= MAX_PICTURES) {
			return "You can keep up to " + MAX_PICTURES + " pictures. Remove one first.";
		}
		if (!Files.isRegularFile(source)) {
			return "That file does not exist";
		}
		String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
		String ext = lower.endsWith(".png") ? ".png" : lower.endsWith(".jpg") ? ".jpg" : lower.endsWith(".jpeg") ? ".jpg" : null;
		if (ext == null) {
			return "Only PNG or JPG pictures";
		}
		if (Files.size(source) > MAX_PICTURE_BYTES) {
			return "That picture is bigger than 25 MB";
		}
		Files.createDirectories(pictureDir());
		String name = "bg_" + Long.toString(System.currentTimeMillis(), 36) + ext;
		Files.copy(source, pictureDir().resolve(name));
		pictures.add(name);
		picture = pictures.size() - 1;
		save();
		return null;
	}

	public static void removePicture(int i) {
		if (i < 0 || i >= pictures.size()) {
			return;
		}
		String name = pictures.remove(i);
		try {
			Files.deleteIfExists(pictureDir().resolve(name));
		} catch (Exception ignored) {
		}
		if (picture == i) {
			picture = -1;
		} else if (picture > i) {
			picture--;
		}
		save();
	}

	public static Path picturePath() {
		return picture >= 0 && picture < pictures.size() ? pictureDir().resolve(pictures.get(picture)) : null;
	}

	public static final class Palette {
		public int backdrop, window, header, rail, surface, surface2, field, border, borderHi;
		public int text, textSoft, textDim, accent, accent2, onAccent, ok, danger;
		public int alpha, radius, small;
		public boolean minecraft, flat, glass, gradient;
	}

	private static final Palette P = new Palette();

	public static Palette palette() {
		load();
		Theme t = theme();
		Palette c = P;
		int base = t.base();
		c.accent = accent;
		c.accent2 = accent == t.accent() ? t.accent2() : Draw.mix(accent, 0x000000, 0.28F);
		c.minecraft = look == Look.MINECRAFT;
		c.flat = look == Look.FLAT;
		c.glass = look == Look.GLASS;
		c.gradient = look == Look.MODERN || look == Look.GLASS;
		c.alpha = Math.round(255 * opacity / 100F);
		switch (look) {
			case MINECRAFT -> {
				c.window = Draw.mix(0x2B2B2B, base, 0.25F);
				c.header = 0x1E1E1E;
				c.rail = 0x232323;
				c.surface = 0x3A3A3A;
				c.surface2 = 0x4A4A4A;
				c.field = 0x000000;
				c.border = 0x0F0F0F;
				c.borderHi = 0xA0A0A0;
				c.radius = 0;
				c.small = 0;
			}
			case CLASSIC -> {
				c.window = Draw.mix(0x1B1B20, base, 0.6F);
				c.header = Draw.mix(c.window, 0x000000, 0.25F);
				c.rail = Draw.mix(c.window, 0x000000, 0.15F);
				c.surface = Draw.mix(c.window, 0xFFFFFF, 0.05F);
				c.surface2 = Draw.mix(c.window, 0xFFFFFF, 0.1F);
				c.field = Draw.mix(c.window, 0x000000, 0.35F);
				c.border = Draw.mix(c.window, 0xFFFFFF, 0.12F);
				c.borderHi = Draw.mix(c.border, accent, 0.6F);
				c.radius = 4;
				c.small = 3;
			}
			case FLAT -> {
				c.window = Draw.mix(base, 0x202026, 0.35F);
				c.header = c.window;
				c.rail = Draw.mix(c.window, 0x000000, 0.18F);
				c.surface = Draw.mix(c.window, 0xFFFFFF, 0.06F);
				c.surface2 = Draw.mix(c.window, 0xFFFFFF, 0.11F);
				c.field = Draw.mix(c.window, 0x000000, 0.25F);
				c.border = c.surface;
				c.borderHi = accent;
				c.radius = 6;
				c.small = 5;
			}
			case MODERN -> {
				c.window = Draw.mix(base, accent, 0.05F);
				c.header = Draw.mix(c.window, 0x000000, 0.15F);
				c.rail = Draw.mix(c.window, 0x000000, 0.22F);
				c.surface = Draw.mix(c.window, 0xFFFFFF, 0.07F);
				c.surface2 = Draw.mix(c.window, 0xFFFFFF, 0.13F);
				c.field = Draw.mix(c.window, 0x000000, 0.3F);
				c.border = Draw.mix(c.window, 0xFFFFFF, 0.1F);
				c.borderHi = Draw.mix(c.border, accent, 0.7F);
				c.radius = 16;
				c.small = 11;
			}
			default -> {
				c.window = Draw.mix(base, accent, 0.07F);
				c.header = Draw.mix(c.window, 0xFFFFFF, 0.04F);
				c.rail = Draw.mix(c.window, 0x000000, 0.2F);
				c.surface = Draw.mix(c.window, 0xFFFFFF, 0.09F);
				c.surface2 = Draw.mix(c.window, 0xFFFFFF, 0.16F);
				c.field = Draw.mix(c.window, 0x000000, 0.28F);
				c.border = Draw.mix(c.window, 0xFFFFFF, 0.16F);
				c.borderHi = Draw.mix(c.border, accent, 0.75F);
				c.radius = 13;
				c.small = 9;
			}
		}
		c.backdrop = Draw.mix(base, 0x000000, 0.5F);
		c.text = 0xF6F4F8;
		c.textSoft = 0xD8D4DE;
		c.textDim = 0xAAA4B4;
		c.ok = 0x34D399;
		c.danger = 0xF87171;
		float lum = ((accent >> 16 & 0xFF) * 0.299F + (accent >> 8 & 0xFF) * 0.587F + (accent & 0xFF) * 0.114F) / 255F;
		c.onAccent = lum > 0.6F ? 0x101014 : 0xFFFFFF;
		return c;
	}

	private static int num(Properties p, String key, int def) {
		try {
			return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
		} catch (Exception e) {
			return def;
		}
	}

	public static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	public static int parseHex(String s, int def) {
		if (s == null) {
			return def;
		}
		String t = s.trim();
		if (t.startsWith("#")) {
			t = t.substring(1);
		}
		if (t.length() == 3) {
			t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
		}
		if (!t.matches("[0-9a-fA-F]{6}")) {
			return def;
		}
		return Integer.parseInt(t, 16);
	}
}
