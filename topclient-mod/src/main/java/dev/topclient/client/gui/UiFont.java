package dev.topclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiFont {
	public enum Family { INTER, QUICKSAND, SYSTEM, MINECRAFT }

	private static final int FIRST = 32, LAST = 255;
	private static final int[] GAMMA = new int[256];

	static {
		for (int i = 0; i < 256; i++) {
			GAMMA[i] = i == 0 || i == 255 ? i : Math.min(255, (int) Math.round(255.0 * Math.pow(i / 255.0, 0.82)));
		}
	}

	private static final class Atlas {
		Identifier id;
		int texW, texH, cellH, pad, ascent;
		final int[] u = new int[LAST + 1], v = new int[LAST + 1], w = new int[LAST + 1];
		final float[] adv = new float[LAST + 1];
	}

	private static final Map<String, Atlas> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Atlas> e) {
			if (size() > 40) {
				try {
					Minecraft.getInstance().getTextureManager().release(e.getValue().id);
				} catch (RuntimeException ignored) {
				}
				return true;
			}
			return false;
		}
	};
	private static final Map<String, Font> BASE = new LinkedHashMap<>();
	private static int counter;
	private static boolean broken;

	private UiFont() {
	}

	public static boolean usable(Family f) {
		return f != Family.MINECRAFT && !broken;
	}

	private static Font base(Family f, boolean bold) {
		String key = f + (bold ? "/b" : "/r");
		Font have = BASE.get(key);
		if (have != null) {
			return have;
		}
		Font font;
		try {
			font = switch (f) {
				case INTER -> load(bold ? "Inter-SemiBold.otf" : "Inter-Regular.otf");
				case QUICKSAND -> {
					Font q = load("font.ttf");
					yield bold ? q.deriveFont(Font.BOLD) : q;
				}
				default -> new Font(systemFamily(), bold ? Font.BOLD : Font.PLAIN, 12);
			};
		} catch (Exception e) {
			font = new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 12);
		}
		BASE.put(key, font);
		return font;
	}

	private static Font load(String file) throws Exception {
		try (InputStream in = UiFont.class.getResourceAsStream("/assets/topclient/font/" + file)) {
			if (in == null) {
				throw new IllegalStateException("missing " + file);
			}
			return Font.createFont(Font.TRUETYPE_FONT, in);
		}
	}

	private static String systemFamily() {
		try {
			java.util.Set<String> have = new java.util.HashSet<>();
			for (String n : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(java.util.Locale.ROOT)) {
				have.add(n);
			}
			for (String c : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Ubuntu", "Arial"}) {
				if (have.contains(c)) {
					return c;
				}
			}
		} catch (Throwable ignored) {
		}
		return "SansSerif";
	}

	private static Atlas atlas(Family f, boolean bold, int px) {
		String key = f + (bold ? "b" : "r") + px;
		Atlas have = CACHE.get(key);
		if (have != null) {
			return have;
		}
		Font probeFont = base(f, bold).deriveFont(100F);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		FontMetrics pm = pg.getFontMetrics(probeFont);
		float size = 100F * px / Math.max(1, pm.getAscent() + pm.getDescent());
		Font font = base(f, bold).deriveFont(size);
		pg.setFont(font);
		FontMetrics fm = pg.getFontMetrics();
		pg.dispose();

		Atlas a = new Atlas();
		a.pad = Math.max(2, px / 6);
		a.ascent = fm.getAscent();
		a.cellH = fm.getAscent() + fm.getDescent() + a.pad * 2;
		int texW = 1024;
		int x = 0, y = 0;
		for (int c = FIRST; c <= LAST; c++) {
			if (c == 127 || (c > 127 && c < 160)) {
				continue;
			}
			float adv = (float) font.getStringBounds(String.valueOf((char) c), fm.getFontRenderContext()).getWidth();
			int cw = (int) Math.ceil(adv) + a.pad * 2;
			if (x + cw > texW) {
				x = 0;
				y += a.cellH;
			}
			a.u[c] = x;
			a.v[c] = y;
			a.w[c] = cw;
			a.adv[c] = adv;
			x += cw;
		}
		int texH = Integer.highestOneBit(Math.max(16, y + a.cellH) - 1) << 1;
		BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setFont(font);
		g.setColor(java.awt.Color.WHITE);
		for (int c = FIRST; c <= LAST; c++) {
			if (a.w[c] > 0) {
				g.drawString(String.valueOf((char) c), (float) (a.u[c] + a.pad), (float) (a.v[c] + a.pad + a.ascent));
			}
		}
		g.dispose();

		NativeImage n = new NativeImage(texW, texH, false);
		int[] pixels = img.getRGB(0, 0, texW, texH, null, 0, texW);
		for (int yy = 0; yy < texH; yy++) {
			for (int xx = 0; xx < texW; xx++) {
				int al = (pixels[yy * texW + xx] >>> 24) & 0xFF;
				n.setPixel(xx, yy, (GAMMA[al] << 24) | 0xFFFFFF);
			}
		}
		a.id = Identifier.fromNamespaceAndPath("topclient", "ui_font/" + (counter++));
		Minecraft.getInstance().getTextureManager().register(a.id, new DynamicTexture(a.id::toString, n));
		a.texW = texW;
		a.texH = texH;
		CACHE.put(key, a);
		return a;
	}

	private static int glyph(char c) {
		return c >= FIRST && c <= LAST && !(c == 127 || (c > 127 && c < 160)) ? c : '?';
	}

	private static Atlas safe(Family f, boolean bold, float devicePx) {
		if (broken) {
			return null;
		}
		try {
			return atlas(f, bold, Math.max(6, Math.min(160, Math.round(devicePx))));
		} catch (Throwable t) {
			broken = true;
			return null;
		}
	}

	public static float width(Family f, boolean bold, String s, float px, float ui) {
		Atlas a = safe(f, bold, px * ui);
		if (a == null) {
			return -1;
		}
		float w = 0;
		for (int i = 0; i < s.length(); i++) {
			w += a.adv[glyph(s.charAt(i))];
		}
		return w / ui;
	}

	public static boolean draw(GuiGraphicsExtractor ctx, Family f, boolean bold, String s, float x, float y, float px, float ui, int argb) {
		Atlas a = safe(f, bold, px * ui);
		if (a == null) {
			return false;
		}
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(Math.round(x * ui) / ui, Math.round(y * ui) / ui);
		m.scale(1F / ui, 1F / ui);
		float cx = 0;
		for (int i = 0; i < s.length(); i++) {
			int c = glyph(s.charAt(i));
			if (c != ' ') {
				ctx.blit(RenderPipelines.GUI_TEXTURED, a.id, Math.round(cx) - a.pad, -a.pad, a.u[c], a.v[c], a.w[c], a.cellH,
						a.w[c], a.cellH, a.texW, a.texH, argb);
			}
			cx += a.adv[c];
		}
		m.popMatrix();
		return true;
	}
}
