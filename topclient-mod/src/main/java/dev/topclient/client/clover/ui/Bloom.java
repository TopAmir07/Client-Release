package dev.topclient.client.clover.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Afrika "Bloom" look shared by the title screen and the account switcher: deep plum
 * background, glowing spinning flower, drifting petals and soft pink buttons.
 */
public final class Bloom {
	public static final int PINK = 0xFF5FA8;
	public static final int PINK_DEEP = 0xE0438F;
	public static final int LILAC = 0xB46CFF;
	public static final int TEXT = 0xFFFFF3F8; // text colours need the alpha byte or Minecraft draws them invisible
	public static final int TEXT_DIM = 0xFFD9A9C4;
	public static final int PANEL = 0x24101F;
	public static final int CARD = 0x341729;
	public static final int CARD_HOVER = 0x4A1F3A;

	private static final float[][] PETALS = new float[34][5];
	private static final long START = System.currentTimeMillis();

	static {
		Random r = new Random(11);
		for (float[] p : PETALS) {
			p[0] = r.nextFloat();
			p[1] = r.nextFloat();
			p[2] = 0.5F + r.nextFloat();
			p[3] = r.nextFloat() * 6.28F;
			p[4] = 0.6F + r.nextFloat() * 0.9F;
		}
	}

	private Bloom() {
	}

	public static float time() {
		return (System.currentTimeMillis() - START) / 1000F;
	}

	public static int argb(int rgb, float alpha) {
		return (Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24) | (rgb & 0xFFFFFF);
	}

	/** Full-screen animated background. flowerX/Y/size place the big faint flower. */
	public static void background(GuiGraphicsExtractor ctx, int w, int h, float flowerX, float flowerY, float flowerSize) {
		float t = time();
		ctx.fillGradient(0, 0, w, h, 0xFF2A0F25, 0xFF0D0610);
		// soft pink haze at the bottom and a lilac one at the top
		ctx.fillGradient(0, h / 2, w, h, 0x00FF5FA8, 0x33FF5FA8);
		ctx.fillGradient(0, 0, w, h / 3, 0x22B46CFF, 0x00B46CFF);
		LoadingScreen.image(ctx, LoadingScreen.brand("glow"), flowerX, flowerY, flowerSize * 2.4F * (1F + (float) Math.sin(t * 1.4F) * 0.04F), 0F, argb(0xFFFFFF, 0.45F));
		LoadingScreen.image(ctx, LoadingScreen.brand("flower"), flowerX, flowerY, flowerSize * 2.2F, -t * 0.05F, argb(0xFFFFFF, 0.05F));
		LoadingScreen.Img petal = LoadingScreen.brand("petal");
		for (float[] q : PETALS) {
			float y = ((q[1] + t * 0.035F * q[2]) % 1.1F) * (h + 40) - 20;
			float x = q[0] * w + (float) Math.sin(t * 0.7F + q[3]) * 22F;
			LoadingScreen.image(ctx, petal, x, y, 8F * q[4], q[3] + t * q[2], argb(0xFFFFFF, 0.45F));
		}
	}

	/** Spinning logo flower with orbiting petals. */
	public static void logo(GuiGraphicsExtractor ctx, float cx, float cy, float size) {
		float t = time();
		float intro = Math.min(1F, t / 1.2F);
		float ease = 1F - (float) Math.pow(1F - intro, 3);
		float pulse = 1F + (float) Math.sin(t * 2F) * 0.035F;
		LoadingScreen.image(ctx, LoadingScreen.brand("glow"), cx, cy, size * 2.2F * pulse, 0F, argb(0xFFFFFF, 0.7F));
		LoadingScreen.image(ctx, LoadingScreen.brand("flower"), cx, cy, size * (0.4F + 0.6F * ease) * pulse, (1F - ease) * -6.3F + t * 0.3F, 0xFFFFFFFF);
		LoadingScreen.Img petal = LoadingScreen.brand("petal");
		for (int i = 0; i < 8; i++) {
			float a = t * 0.7F + i * 0.785F;
			float r = size * (0.68F + 0.05F * (float) Math.sin(t * 1.6F + i));
			LoadingScreen.image(ctx, petal, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.9F, 8F, a + 1.57F, argb(0xFFFFFF, 0.8F * ease));
		}
	}

	public static void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		Gfx.rect(ctx, x - 1, y - 1, w + 2, h + 2, 8, argb(PINK, 0.35F));
		Gfx.rect(ctx, x, y, w, h, 7, argb(PANEL, 0.92F));
		ctx.fillGradient(x + 6, y, x + w - 6, y + 1, argb(PINK, 0.0F), argb(LILAC, 0.6F));
	}

	/** Pill button. Returns true when the mouse is over it. */
	public static boolean button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean primary, int mx, int my) {
		boolean over = Gfx.inside(mx, my, x, y, w, h);
		int r = Math.min(h / 2, 9);
		if (primary) {
			Gfx.rect(ctx, x, y, w, h, r, argb(over ? PINK : PINK_DEEP, 1F));
			ctx.fillGradient(x + r, y + 1, x + w - r, y + h / 2, 0x33FFFFFF, 0x00FFFFFF);
		} else {
			Gfx.rect(ctx, x, y, w, h, r, argb(over ? PINK : 0x6A3359, over ? 0.95F : 0.7F));
			Gfx.rect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), argb(over ? CARD_HOVER : CARD, 0.95F));
		}
		Gfx.centered(ctx, label, x + w / 2, y + (h - 8) / 2, TEXT);
		return over;
	}
}
