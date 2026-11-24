package dev.topclient.client.gui;

import dev.topclient.client.clover.ui.Textures;
import dev.topclient.client.module.modules.client.ClickGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class Backdrop {
	private static final int GLOW_SIZE = 256;
	private static Identifier glow;
	private static boolean glowBroken;

	private Backdrop() {
	}

	private static Identifier glow() {
		if (glow != null || glowBroken) {
			return glow;
		}
		try {
			NativeImage n = new NativeImage(GLOW_SIZE, GLOW_SIZE, false);
			float c = (GLOW_SIZE - 1) / 2F;
			for (int y = 0; y < GLOW_SIZE; y++) {
				for (int x = 0; x < GLOW_SIZE; x++) {
					double d = Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
					double a = d >= 1 ? 0 : Math.pow(1 - d * d, 2.2);
					n.setPixel(x, y, ((int) Math.round(a * 255) << 24) | 0xFFFFFF);
				}
			}
			glow = Identifier.fromNamespaceAndPath("topclient", "ui_backdrop/glow");
			Minecraft.getInstance().getTextureManager().register(glow, new DynamicTexture(glow::toString, n));
		} catch (Throwable t) {
			glowBroken = true;
		}
		return glow;
	}

	public static void blob(GuiGraphicsExtractor ctx, float cx, float cy, float r, int argb) {
		Identifier g = glow();
		if (g == null) {
			Draw.circle(ctx, cx, cy, r * 0.5F, Draw.withAlpha(argb, ((argb >>> 24) & 0xFF) / 3));
			return;
		}
		int s = Math.round(r * 2);
		ctx.blit(RenderPipelines.GUI_TEXTURED, g, Math.round(cx - r), Math.round(cy - r), 0F, 0F, s, s,
				GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, Draw.fade(argb));
	}

	private static float rnd(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	public static void draw(GuiGraphicsExtractor ctx, int W, int H) {
		ClickGUI.Backdrop mode = ClickGUI.backdrop.getMode();
		if (mode == ClickGUI.Backdrop.None) {
			return;
		}
		double time = System.currentTimeMillis() / 1000.0;
		int dim = (int) Math.round(ClickGUI.backdropDim.getValue() * 2.4);
		int accent = GuiTheme.accent();
		int accent2 = GuiTheme.accent2();

		// The picture is drawn inside the menu window (see windowPicture), not over the whole screen.

		Draw.rect(ctx, 0, 0, W, H, Draw.argb(dim, Draw.mix(0x07050A, GuiTheme.TINT, 0.7F)));
		switch (mode) {
			case Gradient -> Draw.gradientV(ctx, 0, 0, W, H, Draw.argb(110, Draw.mix(0x0E0C10, accent, 0.45F)), Draw.argb(60, Draw.mix(0x07050A, GuiTheme.TINT, 0.7F)));
			case Aurora -> {
				float a = (float) time;
				blob(ctx, W * (0.25F + 0.10F * (float) Math.sin(a * 0.21)), H * (0.30F + 0.08F * (float) Math.cos(a * 0.17)), H * 0.55F, Draw.argb(70, accent));
				blob(ctx, W * (0.75F + 0.08F * (float) Math.cos(a * 0.19)), H * (0.70F + 0.10F * (float) Math.sin(a * 0.23)), H * 0.60F, Draw.argb(60, accent2));
				blob(ctx, W * (0.55F + 0.15F * (float) Math.sin(a * 0.13 + 2)), H * (0.20F + 0.06F * (float) Math.sin(a * 0.29)), H * 0.40F, Draw.argb(40, Draw.mix(accent, accent2, 0.5F)));
			}
			case Fireflies -> {
				for (int i = 0; i < 38; i++) {
					float sp = 0.15F + rnd(i, 3) * 0.35F;
					float x = (rnd(i, 1) * W + (float) Math.sin(time * sp + i) * 40F + (float) time * 6F * (rnd(i, 4) - 0.5F)) % W;
					float y = (rnd(i, 2) * H + (float) Math.cos(time * sp * 0.8 + i * 1.7) * 30F) % H;
					if (x < 0) {
						x += W;
					}
					float pulse = 0.45F + 0.55F * (float) Math.max(0, Math.sin(time * (0.8 + rnd(i, 5)) + i));
					int c = i % 3 == 0 ? accent2 : accent;
					blob(ctx, x, y, 18 + rnd(i, 6) * 10, Draw.argb(Math.round(90 * pulse), c));
					Draw.circle(ctx, x, y, 1.6F, Draw.argb(Math.round(230 * pulse), Draw.mix(c, 0xFFFFFF, 0.6F)));
				}
			}
			case Starfield -> {
				Draw.gradientV(ctx, 0, 0, W, H, Draw.argb(70, Draw.mix(0x0E0C10, accent, 0.25F)), Draw.argb(0, 0x000000));
				for (int i = 0; i < 150; i++) {
					float depth = 0.3F + rnd(i, 7) * 0.7F;
					float x = (rnd(i, 1) * W + (float) time * 4F * depth) % W;
					float y = (rnd(i, 2) * H + (float) time * 1.5F * depth) % H;
					float tw = 0.5F + 0.5F * (float) Math.sin(time * (1.5 + rnd(i, 3) * 2) + i);
					int s = depth > 0.85F ? 2 : 1;
					Draw.rect(ctx, Math.round(x), Math.round(y), s, s, Draw.argb(Math.round((80 + 150 * depth) * tw), 0xFFFFFF));
				}
			}
			default -> {
			}
		}
	}

	/** True while the background picture is being shown inside the menu window this frame. */
	public static boolean inWindow;

	/**
	 * Draws the "Picture" background inside the menu window only (like Afrika Visuals), inset a little so
	 * its square corners stay inside the rounded window. Called by ClickGui right after the window shadow.
	 */
	public static void windowPicture(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius) {
		inWindow = false;
		if (!ClickGUI.backdrop.isMode(ClickGUI.Backdrop.Picture)) {
			return;
		}
		int inset = Math.max(2, (radius * 3 + 9) / 10);
		inWindow = pictureIn(ctx, x + inset, y + inset, w - inset * 2, h - inset * 2);
	}

	/** Window/sidebar fill alpha: see-through enough to show the picture when one is shown. */
	public static int windowAlpha() {
		int a = GuiTheme.windowAlpha();
		return inWindow ? a * 55 / 100 : a;
	}

	/** Module card fill alpha from the "Card Opacity" slider (0-100). */
	public static int cardAlpha() {
		return 255 * ClickGUI.cardOpacity.getValueInt() / 100;
	}

	/** Scales an alpha value by the "Preview Opacity" slider (0-100). */
	public static int previewScale(int alpha) {
		return alpha * ClickGUI.previewOpacity.getValueInt() / 100;
	}

	/** Scales only the alpha byte of an ARGB colour by the "Preview Opacity" slider. */
	public static int previewColor(int argb) {
		return (argb & 0x00FFFFFF) | (previewScale(argb >>> 24) << 24);
	}

	private static boolean picture(GuiGraphicsExtractor ctx, int W, int H) {
		return pictureIn(ctx, 0, 0, W, H);
	}

	private static boolean pictureIn(GuiGraphicsExtractor ctx, int X, int Y, int W, int H) {
		String path = ClickGUI.backdropPicture.getValue();
		if (path == null || path.isBlank()) {
			return false;
		}
		Textures.Tex t = Textures.load(path.strip().replace("\"", ""));
		if (t == null || t.w() <= 0 || t.h() <= 0) {
			return false;
		}
		float box = W / (float) H, tex = t.w() / (float) t.h();
		int rw, rh, ru, rv;
		if (tex > box) {
			rh = t.h();
			rw = Math.round(t.h() * box);
			ru = (t.w() - rw) / 2;
			rv = 0;
		} else {
			rw = t.w();
			rh = Math.round(t.w() / box);
			ru = 0;
			rv = (t.h() - rh) / 2;
		}
		ctx.blit(RenderPipelines.GUI_TEXTURED, t.id(), X, Y, ru, rv, W, H, rw, rh, t.w(), t.h(), Draw.fade(0xFFFFFFFF));
		return true;
	}
}
