package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Afrika loading screen, drawn over Minecraft's own (Mojang) loading screen: a spinning pink
 * flower with a glow, orbiting and falling petals, the Afrika wordmark and a progress bar.
 * Pictures are read straight from the mod jar, because while the game is still loading its
 * resources (fonts, textures) are not ready yet. Turn it off with ui.loadingScreen=false.
 */
public final class LoadingScreen {
	public record Img(Identifier id, int w, int h) {
	}

	private static final Map<String, Img> IMAGES = new HashMap<>();
	private static final Random RANDOM = new Random(7);
	private static final float[][] PETALS = new float[28][5];
	private static long startedAt = -1, doneAt = -1;
	private static Object lastOverlay;
	private static Field progressField;
	private static final java.util.List<Field> TIME_FIELDS = new java.util.ArrayList<>();
	private static int baseline = -1;
	private static boolean searched, broken;

	static {
		for (float[] p : PETALS) {
			p[0] = RANDOM.nextFloat();
			p[1] = RANDOM.nextFloat();
			p[2] = 0.5F + RANDOM.nextFloat();
			p[3] = RANDOM.nextFloat() * 6.28F;
			p[4] = 0.6F + RANDOM.nextFloat() * 0.8F;
		}
	}

	private LoadingScreen() {
	}

	public static boolean enabled() {
		try {
			return Config.getBool("ui.loadingScreen", true);
		} catch (RuntimeException e) {
			return true;
		}
	}

	/** A picture from textures/brand inside the mod jar (flower, glow, petal, wordmark, client). */
	public static Img brand(String name) {
		return img(name);
	}

	/** Any picture inside assets/topclient, e.g. asset("icons/cog-outline"). */
	public static Img asset(String path) {
		return img("@" + path);
	}

	private static Img img(String name) {
		Img have = IMAGES.get(name);
		if (have != null || IMAGES.containsKey(name)) {
			return have;
		}
		Img made = null;
		try (InputStream in = LoadingScreen.class.getResourceAsStream(name.startsWith("@") ? "/assets/topclient/" + name.substring(1) + ".png" : "/assets/topclient/textures/brand/" + name + ".png")) {
			if (in != null) {
				NativeImage image = Textures.fromBuffered(ImageIO.read(in));
				if (image != null) {
					Identifier id = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "loading/" + name.replace("@", "a/").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
					made = new Img(id, image.getWidth(), image.getHeight());
				}
			}
		} catch (Exception e) {
			CloverClient.LOGGER.warn("Loading screen picture {} failed: {}", name, e.toString());
		}
		IMAGES.put(name, made);
		return made;
	}

	/** Reads Minecraft's loading progress (0..1) from the overlay, whatever the field is called. */
	private static float progress(Object overlay) {
		if (!searched) {
			searched = true;
			for (Class<?> c = overlay.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					if (f.getType() == float.class && progressField == null) {
						f.setAccessible(true);
						progressField = f;
					} else if (f.getType() == long.class) {
						f.setAccessible(true);
						TIME_FIELDS.add(f);
					}
				}
				if (progressField != null) {
					break;
				}
			}
		}
		try {
			return progressField == null ? -1F : Math.max(0F, Math.min(1F, progressField.getFloat(overlay)));
		} catch (Exception e) {
			return -1F;
		}
	}

	/** Minecraft stores "-1" in its start / finish times until they happen. */
	private static int timesSet(Object overlay) {
		int n = 0;
		for (Field f : TIME_FIELDS) {
			try {
				if (f.getLong(overlay) != -1L) {
					n++;
				}
			} catch (Exception ignored) {
			}
		}
		return n;
	}

	private static int argb(int rgb, float alpha) {
		return (Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24) | (rgb & 0xFFFFFF);
	}

	public static void image(GuiGraphicsExtractor ctx, Img img, float cx, float cy, float size, float rotation, int color) {
		if (img == null) {
			return;
		}
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(cx, cy);
		if (rotation != 0F) {
			m.rotate(rotation);
		}
		float k = size / Math.max(img.w(), img.h());
		m.scale(k, k);
		m.translate(-img.w() / 2F, -img.h() / 2F);
		ctx.blit(RenderPipelines.GUI_TEXTURED, img.id(), 0, 0, 0F, 0F, img.w(), img.h(), img.w(), img.h(), img.w(), img.h(), color);
		m.popMatrix();
	}

	public static void draw(GuiGraphicsExtractor ctx, Object overlay) {
		if (broken || !enabled()) {
			return;
		}
		try {
			drawNow(ctx, overlay);
		} catch (Throwable t) {
			broken = true;
			CloverClient.LOGGER.warn("TopClient loading screen turned off: {}", t.toString());
		}
	}

	private static void drawNow(GuiGraphicsExtractor ctx, Object overlay) {
		long now = System.currentTimeMillis();
		if (overlay != lastOverlay) {
			lastOverlay = overlay;
			startedAt = now;
			doneAt = -1;
			baseline = -1;
		}
		float p = progress(overlay);
		int set = timesSet(overlay);
		if (baseline < 0) {
			baseline = set;
		}
		if (doneAt < 0 && (p >= 0.999F || set > baseline)) {
			doneAt = now;
		}
		float t = (now - startedAt) / 1000F;
		float alpha = Math.min(1F, t / 0.25F);
		if (doneAt > 0) {
			alpha *= Math.max(0F, 1F - Math.max(0, now - doneAt - 150) / 800F);
		}
		if (alpha <= 0.01F) {
			return;
		}
		int w = ctx.guiWidth(), h = ctx.guiHeight();
		ctx.fillGradient(0, 0, w, h, argb(0x22101F, alpha), argb(0x0E0811, alpha));

		float cx = w / 2F, cy = h * 0.38F;
		float size = Math.min(w, h) * 0.30F;
		float pulse = 1F + (float) Math.sin(t * 2.2F) * 0.04F;

		image(ctx, img("glow"), cx, cy, size * 2.6F * pulse, 0F, argb(0xFFFFFF, alpha * 0.55F));
		image(ctx, img("flower"), cx, cy, size * 2.1F, -t * 0.18F, argb(0xFFFFFF, alpha * 0.06F));

		// petals falling across the whole screen
		Img petal = img("petal");
		for (float[] q : PETALS) {
			float y = ((q[1] + t * 0.06F * q[2]) % 1.1F) * (h + 40) - 20;
			float x = q[0] * w + (float) Math.sin(t * 0.9F + q[3]) * 18F;
			image(ctx, petal, x, y, 9F * q[4], q[3] + t * 1.3F * q[2], argb(0xFFFFFF, alpha * 0.55F));
		}

		// spin in, then keep turning slowly
		float intro = Math.min(1F, t / 1.4F);
		float ease = 1F - (float) Math.pow(1F - intro, 3);
		float rot = (1F - ease) * -9.4F + t * 0.45F;
		image(ctx, img("flower"), cx, cy, size * (0.3F + 0.7F * ease) * pulse, rot, argb(0xFFFFFF, alpha));

		// petals orbiting the flower
		for (int i = 0; i < 10; i++) {
			float a = t * 0.9F + i * 0.628F;
			float r = size * (0.72F + 0.06F * (float) Math.sin(t * 1.7F + i));
			image(ctx, petal, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.92F, 10F, a + 1.57F, argb(0xFFFFFF, alpha * 0.85F));
		}

		// wordmark
		Img word = img("wordmark");
		float wordY = cy + size * 0.68F + 18;
		if (word != null) {
			float ww = Math.min(w * 0.34F, 180F);
			image(ctx, word, cx, wordY, ww, 0F, argb(0xFFFFFF, alpha));
			image(ctx, img("client"), cx, wordY + ww * 0.24F, ww * 0.38F, 0F, argb(0xFFFFFF, alpha * 0.8F));
			wordY += ww * 0.24F;
		}

		// progress bar
		int bw = Math.round(Math.min(w * 0.34F, 200F)), bh = 3;
		int bx = Math.round(cx - bw / 2F), by = Math.min(h - 12, Math.round(wordY + 18));
		ctx.fill(bx, by, bx + bw, by + bh, argb(0xFFFFFF, alpha * 0.12F));
		float shown = p < 0 ? (t * 0.25F) % 1F : p;
		int fw = Math.round(bw * shown);
		if (fw > 0) {
			ctx.fillGradient(bx, by, bx + fw, by + bh, argb(0xFF4F9A, alpha), argb(0xB43CFF, alpha));
			ctx.fill(bx + fw - 1, by - 1, bx + fw + 1, by + bh + 1, argb(0xFFFFFF, alpha));
		}
	}
}
