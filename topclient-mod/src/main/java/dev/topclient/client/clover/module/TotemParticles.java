package dev.topclient.client.clover.module;

import dev.topclient.client.clover.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TotemParticles extends Module {
	public static TotemParticles instance;

	public static final int MAX_COLOURS = 8;
	private static final String COLOURS_KEY = "mod.totemparticles.colours";
	private static final String DEFAULT_COLOURS = "FFD54A,4AE36B";

	public final Setting colourMode;
	public final Setting multiplier;
	public final Setting size;
	public final Setting speed;
	public final Setting lifetime;
	public final Setting glow;
	public final Setting ownTextures;

	public static int colourVersion;

	public TotemParticles() {
		super("totemparticles", "Totem Particles", "Totem pop particles in your own colours (2, 3 or more), more of them, "
				+ "bigger and glowing. Beats texture packs and other mods. Only you see it.", Category.VISUAL);
		instance = this;
		isNew = true;
		header("Look");
		colourMode = mode("colourMode", "Colours", 0, "Mix (random)", "Fade through", "Rainbow", "Vanilla");
		multiplier = number("multiplier", "Amount (multiplier)", 0.25F, 5F, 0.25F, 1F);
		size = number("size", "Size", 0.5F, 2.5F, 0.1F, 1F);
		speed = number("speed", "Speed", 0.4F, 2F, 0.1F, 1F);
		lifetime = number("lifetime", "How long they last", 0.5F, 2.5F, 0.1F, 1F);
		glow = bool("glow", "Glow in the dark", true);
		ownTextures = bool("ownTextures", "Override texture packs", true);
	}

	@Override
	protected boolean defaultEnabled() {
		return false;
	}

	public static TotemParticles active() {
		TotemParticles t = instance;
		return t != null && t.enabled() ? t : null;
	}

	private static String cacheKey;
	private static List<Integer> cache;

	public static List<Integer> colours() {
		String raw = Config.get(COLOURS_KEY, DEFAULT_COLOURS);
		if (raw.equals(cacheKey) && cache != null) {
			return cache;
		}
		List<Integer> out = new ArrayList<>();
		for (String part : raw.split(",")) {
			try {
				String p = part.trim();
				if (!p.isEmpty()) {
					out.add(Integer.parseInt(p, 16) & 0xFFFFFF);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		if (out.isEmpty()) {
			out.add(0xFFD54A);
		}
		while (out.size() > MAX_COLOURS) {
			out.remove(out.size() - 1);
		}
		cache = java.util.Collections.unmodifiableList(out);
		cacheKey = raw;
		return cache;
	}

	private static void save(List<Integer> list) {
		StringBuilder sb = new StringBuilder();
		for (int c : list) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(String.format(Locale.ROOT, "%06X", c & 0xFFFFFF));
		}
		Config.set(COLOURS_KEY, sb.toString());
		Config.saveIfNeeded(false);
	}

	public static void setColour(int i, int rgb) {
		List<Integer> l = new ArrayList<>(colours());
		if (i >= 0 && i < l.size()) {
			l.set(i, rgb & 0xFFFFFF);
			save(l);
		}
	}

	public static void addColour() {
		List<Integer> l = new ArrayList<>(colours());
		if (l.size() < MAX_COLOURS) {
			float h = (float) Math.random();
			l.add(java.awt.Color.HSBtoRGB(h, 0.75F, 1F) & 0xFFFFFF);
			save(l);
			colourVersion++;
		}
	}

	public static void removeColour(int i) {
		List<Integer> l = new ArrayList<>(colours());
		if (l.size() > 1 && i >= 0 && i < l.size()) {
			l.remove(i);
			save(l);
			colourVersion++;
		}
	}

	public static void resetColours() {
		Config.set(COLOURS_KEY, DEFAULT_COLOURS);
		Config.saveIfNeeded(false);
		colourVersion++;
	}

	@Override
	public void resetSettings() {
		super.resetSettings();
		resetColours();
	}

	public int colourFor(float seed, float life) {
		List<Integer> l = colours();
		switch (colourMode.getMode()) {
			case 1 -> {
				float f = Math.max(0F, Math.min(0.9999F, life)) * (l.size() - 1);
				int i = (int) f;
				if (l.size() == 1) {
					return l.get(0);
				}
				return mix(l.get(i), l.get(Math.min(l.size() - 1, i + 1)), f - i);
			}
			case 2 -> {
				float h = ((System.currentTimeMillis() % 3000L) / 3000F + seed) % 1F;
				return java.awt.Color.HSBtoRGB(h, 0.75F, 1F) & 0xFFFFFF;
			}
			default -> {
				return l.get(Math.min(l.size() - 1, (int) (seed * l.size())));
			}
		}
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static final int FRAMES = 8;
	private static TextureAtlasSprite[] sprites;
	private static TextureAtlas spritesFrom;

	@SuppressWarnings("deprecation")
	public static TextureAtlasSprite sprite(float life) {
		try {
			TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_PARTICLES);
			if (atlas == null) {
				return null;
			}
			if (atlas != spritesFrom || sprites == null) {
				TextureAtlasSprite missing = atlas.missingSprite();
				TextureAtlasSprite[] found = new TextureAtlasSprite[FRAMES];
				for (int i = 0; i < FRAMES; i++) {
					TextureAtlasSprite s = atlas.getSprite(Identifier.fromNamespaceAndPath("topclient", "totem_" + i));
					found[i] = s == null || s == missing ? null : s;
				}
				sprites = found;
				spritesFrom = atlas;
			}
			int i = Math.max(0, Math.min(FRAMES - 1, (int) (life * FRAMES)));
			return sprites[i];
		} catch (RuntimeException e) {
			return null;
		}
	}
}
