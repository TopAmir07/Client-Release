package dev.topclient.client.clover.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class Gfx {
	private Gfx() {
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r == 0) {
			ctx.fill(x, y, x + w, y + h, color);
			return;
		}
		ctx.fill(x + r, y, x + w - r, y + h, color);
		for (int i = 0; i < r; i++) {
			int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - (r - i - 0.5) * (r - i - 0.5))));
			ctx.fill(x + inset, y + i, x + r, y + i + 1, color);
			ctx.fill(x + w - r, y + i, x + w - inset, y + i + 1, color);
			ctx.fill(x + inset, y + h - 1 - i, x + r, y + h - i, color);
			ctx.fill(x + w - r, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
		ctx.fill(x, y + r, x + r, y + h - r, color);
		ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
	}

	public static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
		if (w <= 1 || h <= 1) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		ctx.fill(x + r, y, x + w - r, y + 1, color);
		ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
		ctx.fill(x, y + r, x + 1, y + h - r, color);
		ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - (r - i - 0.5) * (r - i - 0.5))));
			int next = i + 1 < r ? r - (int) Math.round(Math.sqrt(Math.max(0, r * r - (r - i - 1.5) * (r - i - 1.5)))) : 0;
			int x0 = x + Math.min(inset, next), x1 = x + Math.max(inset, next) + 1;
			ctx.fill(x0, y + i, x1, y + i + 1, color);
			ctx.fill(x + w - (x1 - x), y + i, x + w - (x0 - x), y + i + 1, color);
			ctx.fill(x0, y + h - 1 - i, x1, y + h - i, color);
			ctx.fill(x + w - (x1 - x), y + h - 1 - i, x + w - (x0 - x), y + h - i, color);
		}
	}

	public static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int border, int inside) {
		rect(ctx, x, y, w, h, r, border);
		rect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), inside);
	}

	private static int themeFont() {
		int f = Theme.font();
		return CloverFont.usable(f) ? f : 0;
	}

	public static int width(String s) {
		int f = themeFont();
		return f == 0 ? font().width(s) : (int) Math.ceil(CloverFont.width(f, s, 1F));
	}

	public static void text(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
		draw(ctx, s, x, y, 1F, color, false);
	}

	public static void shadow(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
		draw(ctx, s, x, y, 1F, color, true);
	}

	public static void centered(GuiGraphicsExtractor ctx, String s, int cx, int y, int color) {
		draw(ctx, s, cx - width(s) / 2F, y, 1F, color, false);
	}

	public static void centeredShadow(GuiGraphicsExtractor ctx, String s, int cx, int y, int color) {
		draw(ctx, s, cx - width(s) / 2F, y, 1F, color, true);
	}

	public static void scaled(GuiGraphicsExtractor ctx, String s, float x, float y, float k, int color, boolean shadow) {
		draw(ctx, s, x, y, k, color, shadow);
	}

	public static void scaledCentered(GuiGraphicsExtractor ctx, String s, float cx, float y, float k, int color, boolean shadow) {
		int f = themeFont();
		float w = f == 0 ? font().width(s) * k : CloverFont.width(f, s, k);
		draw(ctx, s, cx - w / 2F, y, k, color, shadow);
	}

	public static void drawInFont(GuiGraphicsExtractor ctx, int fontIndex, String s, float x, float y, float k, int color, boolean shadow) {
		if (CloverFont.usable(fontIndex)) {
			CloverFont.draw(ctx, fontIndex, s, x, y, k, color, shadow);
		} else {
			vanillaScaled(ctx, s, x, y, k, color, shadow);
		}
	}

	private static void draw(GuiGraphicsExtractor ctx, String s, float x, float y, float k, int color, boolean shadow) {
		drawInFont(ctx, themeFont(), s, x, y, k, color, shadow);
	}

	public static void vanillaScaled(GuiGraphicsExtractor ctx, String s, float x, float y, float k, int color, boolean shadow) {
		if (k == 1F && x == Math.round(x) && y == Math.round(y)) {
			ctx.text(font(), s, Math.round(x), Math.round(y), color, shadow);
			return;
		}
		var m = ctx.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(k, k);
		ctx.text(font(), s, 0, 0, color, shadow);
		m.popMatrix();
	}

	public static String fit(String s, int maxW) {
		if (width(s) <= maxW) {
			return s;
		}
		String dots = "...";
		int w = maxW - width(dots);
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (width(b.toString() + c) > w) {
				break;
			}
			b.append(c);
		}
		return b + dots;
	}

	public static void image(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int texW, int texH, int tint) {
		ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, texW, texH, texW, texH, tint);
	}

	public static void image(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int texW, int texH) {
		ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, texW, texH, texW, texH);
	}

	public static void cover(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int texW, int texH, int tint) {
		if (texW <= 0 || texH <= 0) {
			return;
		}
		float boxRatio = w / (float) h;
		float texRatio = texW / (float) texH;
		int rw, rh;
		int ru, rv;
		if (texRatio > boxRatio) {
			rh = texH;
			rw = Math.round(texH * boxRatio);
			ru = (texW - rw) / 2;
			rv = 0;
		} else {
			rw = texW;
			rh = Math.round(texW / boxRatio);
			ru = 0;
			rv = (texH - rh) / 2;
		}
		ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, ru, rv, w, h, rw, rh, texW, texH, tint);
	}

	public static void slider(GuiGraphicsExtractor ctx, int x, int y, int w, float value, int accent) {
		ctx.fill(x, y + 3, x + w, y + 5, Theme.track());
		int fill = Math.round(w * Math.max(0F, Math.min(1F, value)));
		ctx.fill(x, y + 3, x + fill, y + 5, accent);
		rect(ctx, x + fill - 3, y, 6, 8, 2, 0xFFFFFFFF);
	}

	public static void toggle(GuiGraphicsExtractor ctx, int x, int y, boolean on, int accent) {
		rect(ctx, x, y, 22, 11, 5, on ? accent : Theme.track());
		rect(ctx, on ? x + 12 : x + 1, y + 1, 9, 9, 4, 0xFFFFFFFF);
	}
}
