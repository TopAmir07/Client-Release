package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.CloverClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CloverFont {
	public record Family(String name, String[] candidates, boolean bold) {
	}

	public static final Family[] FAMILIES = {
			new Family("Minecraft", new String[0], false),
			new Family("Clean", new String[]{"Segoe UI", "Helvetica Neue", "Arial", "SansSerif"}, false),
			new Family("Elegant", new String[]{"Georgia", "Cambria", "Times New Roman", "Serif"}, false),
			new Family("Mono", new String[]{"Consolas", "Menlo", "Courier New", "Monospaced"}, false),
			new Family("Rounded", new String[]{"Trebuchet MS", "Verdana", "SansSerif"}, false),
			new Family("Heavy", new String[]{"Arial Black", "Segoe UI Black", "Impact", "SansSerif"}, true),
			new Family("Tech", new String[]{"Bahnschrift", "Agency FB", "Segoe UI Semibold", "SansSerif"}, false),
			new Family("Bold Clean", new String[]{"Segoe UI", "Helvetica Neue", "Arial", "SansSerif"}, true),
	};

	private static final int FIRST = 32, LAST = 255;
	private static final float LINE = 9F, BASELINE = 7F;

	private static final class Atlas {
		Identifier id;
		int texW, texH;
		int[] u = new int[LAST + 1], v = new int[LAST + 1], w = new int[LAST + 1], adv = new int[LAST + 1];
		int cellH, baseline;
	}

	private static final Map<String, Atlas> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Atlas> e) {
			if (size() > 24) {
				Minecraft.getInstance().getTextureManager().release(e.getValue().id);
				return true;
			}
			return false;
		}
	};
	private static Set<String> installed;
	private static int counter;
	private static boolean broken;

	private CloverFont() {
	}

	private static String pick(Family f) {
		if (installed == null) {
			installed = new HashSet<>();
			try {
				for (String n : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)) {
					installed.add(n.toLowerCase(Locale.ROOT));
				}
			} catch (Throwable ignored) {
			}
		}
		for (String c : f.candidates()) {
			if (installed.contains(c.toLowerCase(Locale.ROOT)) || c.equals("SansSerif") || c.equals("Serif") || c.equals("Monospaced")) {
				return c;
			}
		}
		return "SansSerif";
	}

	private static int guiScale() {
		try {
			return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		} catch (RuntimeException e) {
			return 2;
		}
	}

	private static Atlas atlas(int family, int px) {
		String key = family + "@" + px;
		Atlas have = CACHE.get(key);
		if (have != null) {
			return have;
		}
		Family f = FAMILIES[family];
		float size = px / 1.18F;
		Font font = new Font(pick(f), f.bold() ? Font.BOLD : Font.PLAIN, 12).deriveFont(size);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		pg.setFont(font);
		FontMetrics fm = pg.getFontMetrics();
		pg.dispose();
		int pad = Math.max(1, px / 10);
		int cellH = fm.getAscent() + fm.getDescent() + pad * 2;
		int texW = 1024;
		Atlas a = new Atlas();
		int x = 0, y = 0;
		for (int c = FIRST; c <= LAST; c++) {
			if (c == 127 || (c > 127 && c < 160)) {
				continue;
			}
			int adv = Math.max(1, fm.charWidth((char) c));
			int cw = adv + pad * 2;
			if (x + cw > texW) {
				x = 0;
				y += cellH;
			}
			a.u[c] = x;
			a.v[c] = y;
			a.w[c] = cw;
			a.adv[c] = adv;
			x += cw;
		}
		int texH = Integer.highestOneBit(Math.max(16, y + cellH) - 1) << 1;
		BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setFont(font);
		g.setColor(java.awt.Color.WHITE);
		for (int c = FIRST; c <= LAST; c++) {
			if (a.w[c] > 0) {
				g.drawString(String.valueOf((char) c), a.u[c] + pad, a.v[c] + pad + fm.getAscent());
			}
		}
		g.dispose();
		NativeImage n = new NativeImage(texW, texH, false);
		for (int yy = 0; yy < texH; yy++) {
			for (int xx = 0; xx < texW; xx++) {
				n.setPixel(xx, yy, img.getRGB(xx, yy));
			}
		}
		a.id = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "font/f" + (counter++));
		Minecraft.getInstance().getTextureManager().register(a.id, new DynamicTexture(a.id::toString, n));
		a.texW = texW;
		a.texH = texH;
		a.cellH = cellH;
		a.baseline = pad + fm.getAscent();
		CACHE.put(key, a);
		return a;
	}

	private static int glyph(char c) {
		return c >= FIRST && c <= LAST && !(c == 127 || (c > 127 && c < 160)) ? c : '?';
	}

	private static Atlas safeAtlas(int family, float k) {
		if (broken) {
			return null;
		}
		try {
			int px = Math.max(6, Math.min(140, Math.round(LINE * guiScale() * k)));
			return atlas(family, px);
		} catch (Throwable t) {
			broken = true;
			CloverClient.LOGGER.warn("Clover fonts are not available here, using the Minecraft font: {}", t.toString());
			return null;
		}
	}

	public static boolean usable(int family) {
		return family > 0 && family < FAMILIES.length && !broken;
	}

	public static float width(int family, String s, float k) {
		Atlas a = safeAtlas(family, k);
		if (a == null) {
			return Gfx.font().width(s) * k;
		}
		int px = 0;
		for (int i = 0; i < s.length(); i++) {
			px += a.adv[glyph(s.charAt(i))];
		}
		return px / (float) guiScale();
	}

	public static void draw(GuiGraphicsExtractor ctx, int family, String s, float x, float y, float k, int color, boolean shadow) {
		Atlas a = safeAtlas(family, k);
		if (a == null) {
			Gfx.vanillaScaled(ctx, s, x, y, k, color, shadow);
			return;
		}
		if ((color & 0xFC000000) == 0) {
			color |= 0xFF000000;
		}
		int gs = guiScale();
		var m = ctx.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(1F / gs, 1F / gs);
		int top = Math.round(BASELINE * gs * k) - a.baseline;
		if (shadow) {
			int sc = (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2);
			int off = Math.max(1, Math.round(gs * k));
			run(ctx, a, s, off, top + off, sc);
		}
		run(ctx, a, s, 0, top, color);
		m.popMatrix();
	}

	private static void run(GuiGraphicsExtractor ctx, Atlas a, String s, int x, int top, int color) {
		int cx = x;
		for (int i = 0; i < s.length(); i++) {
			int c = glyph(s.charAt(i));
			if (c != ' ') {
				int padX = (a.w[c] - a.adv[c]) / 2;
				ctx.blit(RenderPipelines.GUI_TEXTURED, a.id, cx - padX, top, a.u[c], a.v[c], a.w[c], a.cellH,
						a.w[c], a.cellH, a.texW, a.texH, color);
			}
			cx += a.adv[c];
		}
	}

	public static String[] names() {
		return Arrays.stream(FAMILIES).map(Family::name).toArray(String[]::new);
	}
}
