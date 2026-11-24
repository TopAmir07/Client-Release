package dev.topclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Pictures {
	private static final Map<String, BufferedImage> MASTERS = new HashMap<>();
	private static final Map<String, Identifier> CACHE = new LinkedHashMap<String, Identifier>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Identifier> e) {
			if (size() > 300) {
				try {
					Minecraft.getInstance().getTextureManager().release(e.getValue());
				} catch (RuntimeException ignored) {
				}
				return true;
			}
			return false;
		}
	};
	private static int counter;

	private Pictures() {
	}

	private static BufferedImage master(String key) throws Exception {
		BufferedImage have = MASTERS.get(key);
		if (have != null) {
			return have;
		}
		try (InputStream in = Pictures.class.getResourceAsStream("/assets/topclient/emoji/" + key + ".png")) {
			if (in == null) {
				throw new IllegalStateException("missing picture " + key);
			}
			BufferedImage img = ImageIO.read(in);
			BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = argb.createGraphics();
			g.drawImage(img, 0, 0, null);
			g.dispose();
			MASTERS.put(key, argb);
			return argb;
		}
	}

	private static BufferedImage scale(BufferedImage src, int px) {
		BufferedImage cur = src;
		int w = src.getWidth();
		while (w / 2 >= px * 2) {
			w /= 2;
			cur = step(cur, w);
		}
		return step(cur, px);
	}

	private static BufferedImage step(BufferedImage src, int px) {
		BufferedImage out = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, px, px, null);
		g.dispose();
		return out;
	}

	private static Identifier texture(String key, int px) {
		String id = key + "@" + px;
		Identifier have = CACHE.get(id);
		if (have != null) {
			return have;
		}
		try {
			BufferedImage img = scale(master(key), px);
			int[] rgb = img.getRGB(0, 0, px, px, null, 0, px);
			NativeImage n = new NativeImage(px, px, false);
			for (int y = 0; y < px; y++) {
				for (int x = 0; x < px; x++) {
					n.setPixel(x, y, rgb[y * px + x]);
				}
			}
			Identifier tex = Identifier.fromNamespaceAndPath("topclient", "ui_picture/" + (counter++));
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(tex::toString, n));
			CACHE.put(id, tex);
			return tex;
		} catch (Throwable t) {
			CACHE.put(id, null);
			return null;
		}
	}

	public static boolean draw(GuiGraphicsExtractor ctx, String key, float cx, float cy, float size, float rotation, int alpha) {
		if (key == null || alpha <= 2) {
			return key != null;
		}
		float ui = Draw.ui;
		int px = Math.max(8, Math.min(192, Math.round(size * ui / 4F) * 4));
		Identifier id = texture(key, px);
		if (id == null) {
			return false;
		}
		int color = (Math.round(Math.min(255, alpha) * Draw.alpha) << 24) | 0xFFFFFF;
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		if (rotation == 0F) {
			m.translate(Math.round(cx * ui - px / 2F) / ui, Math.round(cy * ui - px / 2F) / ui);
			m.scale(1F / ui, 1F / ui);
		} else {
			m.translate(cx, cy);
			m.rotate(rotation);
			m.scale(1F / ui, 1F / ui);
			m.translate(-px / 2F, -px / 2F);
		}
		ctx.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0F, 0F, px, px, px, px, px, px, color);
		m.popMatrix();
		return true;
	}
}
