package dev.topclient.client.gui;

import dev.topclient.client.module.modules.client.ClickGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public final class Draw {
	public static float alpha = 1F;

	private Draw() {
	}

	public static int argb(int a, int rgb) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	public static int fade(int argb) {
		int a = (argb >>> 24) & 0xFF;
		return ((Math.round(a * alpha) & 0xFF) << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int c1, int c2, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
		int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
		int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
		int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int withAlpha(int argb, int a) {
		return argb(a, argb);
	}

	public static void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		ctx.fill(x, y, x + w, y + h, fade(argb));
	}

	public static void gradientV(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		ctx.fillGradient(x, y, x + w, y + h, fade(top), fade(bottom));
	}

	public static void gradientH(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int left, int right) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int strip = Math.max(2, w / 24);
		for (int i = 0; i < w; i += strip) {
			int sw = Math.min(strip, w - i);
			int c = mix(left, right, (i + sw / 2F) / w);
			ctx.fill(x + i, y, x + i + sw, y + h, fade(c));
		}
	}

	private static final java.util.Map<String, Identifier> SHAPES = new java.util.LinkedHashMap<String, Identifier>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, Identifier> e) {
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
	private static int shapeCounter;
	private static boolean shapesBroken;

	private interface Coverage {
		double at(double x, double y);
	}

	private static Identifier shape(String key, int w, int h, Coverage c, int samples) {
		Identifier have = SHAPES.get(key);
		if (have != null || shapesBroken) {
			return have;
		}
		try {
			NativeImage n = new NativeImage(w, h, false);
			double step = 1.0 / samples;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					double sum = 0;
					for (int sy = 0; sy < samples; sy++) {
						for (int sx = 0; sx < samples; sx++) {
							sum += c.at(x + (sx + 0.5) * step, y + (sy + 0.5) * step);
						}
					}
					int a = (int) Math.round(Math.max(0, Math.min(1, sum / (samples * samples))) * 255);
					n.setPixel(x, y, (a << 24) | 0xFFFFFF);
				}
			}
			Identifier id = Identifier.fromNamespaceAndPath("topclient", "ui_shape/" + (shapeCounter++));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, n));
			SHAPES.put(key, id);
			return id;
		} catch (Throwable t) {
			shapesBroken = true;
			return null;
		}
	}

	private static Identifier circleTex(int d) {
		double r = d / 2.0;
		return shape("c" + d, d, d, (x, y) -> (x - r) * (x - r) + (y - r) * (y - r) <= r * r ? 1 : 0, 4);
	}

	private static Identifier ringTex(int d, int t) {
		double r = d / 2.0, ri = Math.max(0, r - t);
		return shape("r" + d + "/" + t, d, d, (x, y) -> {
			double q = (x - r) * (x - r) + (y - r) * (y - r);
			return q <= r * r && q >= ri * ri ? 1 : 0;
		}, 4);
	}

	private static Identifier shadowTex(int r, int s) {
		int c = r + 2 * s;
		int size = 2 * c;
		return shape("s" + r + "/" + s, size, size, (x, y) -> {
			double cx = Math.max(Math.abs(x - c) - (c - s - r), 0);
			double cy = Math.max(Math.abs(y - c) - (c - s - r), 0);
			double d = Math.sqrt(cx * cx + cy * cy) - r;
			double t = Math.max(0, Math.min(1, 0.5 - d / (2.0 * s)));
			return t * t * (3 - 2 * t);
		}, 1);
	}

	private static int px(float v) {
		return Math.round(v * ui);
	}

	private static void quad(GuiGraphicsExtractor ctx, Identifier tex, int x, int y, int w, int h, int u, int v, int rw, int rh, int texW, int texH, int color) {
		if (w > 0 && h > 0) {
			ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, rw, rh, texW, texH, color);
		}
	}

	private static void fillPx(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		if (x2 > x1 && y2 > y1) {
			ctx.fill(x1, y1, x2, y2, color);
		}
	}

	private static void begin(GuiGraphicsExtractor ctx) {
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.scale(1F / ui, 1F / ui);
	}

	private static void end(GuiGraphicsExtractor ctx) {
		ctx.pose().popMatrix();
	}

	public static void round(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int argb) {
		roundCorners(ctx, x, y, w, h, r, argb, argb, true, true);
	}

	public static void roundSide(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int argb, boolean left) {
		roundCorners(ctx, x, y, w, h, r, argb, argb, left, !left);
	}

	/** Rounded only at the two bottom corners (status bars along the bottom of a card). */
	public static void roundSideBottom(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int c = fade(argb);
		if ((c >>> 24) == 0) {
			return;
		}
		begin(ctx);
		try {
			int X = px(x), Y = px(y), X2 = px(x + w), Y2 = px(y + h);
			int R = Math.max(0, Math.min(px(r), Math.min(X2 - X, Y2 - Y)));
			Identifier tex = R >= 1 ? circleTex(2 * R) : null;
			if (tex == null) {
				fillPx(ctx, X, Y, X2, Y2, c);
				return;
			}
			fillPx(ctx, X, Y, X2, Y2 - R, c);
			fillPx(ctx, X + R, Y2 - R, X2 - R, Y2, c);
			int D = 2 * R;
			quad(ctx, tex, X, Y2 - R, R, R, 0, R, R, R, D, D, c);
			quad(ctx, tex, X2 - R, Y2 - R, R, R, R, R, R, R, D, D, c);
		} finally {
			end(ctx);
		}
	}

	public static void roundGradient(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int top, int bottom) {
		roundCorners(ctx, x, y, w, h, r, top, bottom, true, true);
	}

	private static void roundCorners(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int top, int bottom, boolean leftRound, boolean rightRound) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int ct = fade(top), cb = fade(bottom);
		if (((ct >>> 24) | (cb >>> 24)) == 0) {
			return;
		}
		begin(ctx);
		try {
			int X = px(x), Y = px(y), X2 = px(x + w), Y2 = px(y + h);
			int W = X2 - X, H = Y2 - Y;
			int R = Math.max(0, Math.min(px(r), Math.min(W, H) / 2));
			Identifier tex = R >= 1 ? circleTex(2 * R) : null;
			if (tex == null) {
				if (ct == cb) {
					fillPx(ctx, X, Y, X2, Y2, ct);
				} else {
					ctx.fillGradient(X, Y, X2, Y2, ct, cb);
				}
				return;
			}
			float k = R / (float) H;
			int midTop = ct == cb ? ct : mix(ct, cb, k);
			int midBot = ct == cb ? cb : mix(ct, cb, 1 - k);
			int lx = leftRound ? X + R : X;
			int rx = rightRound ? X2 - R : X2;
			if (ct == cb) {
				fillPx(ctx, X, Y + R, X2, Y2 - R, ct);
				fillPx(ctx, lx, Y, rx, Y + R, ct);
				fillPx(ctx, lx, Y2 - R, rx, Y2, cb);
			} else {
				if (Y2 - R > Y + R) {
					ctx.fillGradient(X, Y + R, X2, Y2 - R, midTop, midBot);
				}
				if (rx > lx) {
					ctx.fillGradient(lx, Y, rx, Y + R, ct, midTop);
					ctx.fillGradient(lx, Y2 - R, rx, Y2, midBot, cb);
				}
			}
			int D = 2 * R;
			int cornerTop = ct == cb ? ct : mix(ct, cb, k * 0.55F);
			int cornerBot = ct == cb ? cb : mix(ct, cb, 1 - k * 0.55F);
			if (leftRound) {
				quad(ctx, tex, X, Y, R, R, 0, 0, R, R, D, D, cornerTop);
				quad(ctx, tex, X, Y2 - R, R, R, 0, R, R, R, D, D, cornerBot);
			}
			if (rightRound) {
				quad(ctx, tex, X2 - R, Y, R, R, R, 0, R, R, D, D, cornerTop);
				quad(ctx, tex, X2 - R, Y2 - R, R, R, R, R, R, R, D, D, cornerBot);
			}
		} finally {
			end(ctx);
		}
	}

	public static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int t, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int c = fade(argb);
		if ((c >>> 24) == 0) {
			return;
		}
		begin(ctx);
		try {
			int X = px(x), Y = px(y), X2 = px(x + w), Y2 = px(y + h);
			int W = X2 - X, H = Y2 - Y;
			int T = Math.max(1, Math.round(t * ui));
			int R = Math.max(T, Math.min(px(r), Math.min(W, H) / 2));
			Identifier tex = ringTex(2 * R, T);
			fillPx(ctx, X + R, Y, X2 - R, Y + T, c);
			fillPx(ctx, X + R, Y2 - T, X2 - R, Y2, c);
			fillPx(ctx, X, Y + R, X + T, Y2 - R, c);
			fillPx(ctx, X2 - T, Y + R, X2, Y2 - R, c);
			if (tex != null) {
				int D = 2 * R;
				quad(ctx, tex, X, Y, R, R, 0, 0, R, R, D, D, c);
				quad(ctx, tex, X2 - R, Y, R, R, R, 0, R, R, D, D, c);
				quad(ctx, tex, X, Y2 - R, R, R, 0, R, R, R, D, D, c);
				quad(ctx, tex, X2 - R, Y2 - R, R, R, R, R, R, R, D, D, c);
			}
		} finally {
			end(ctx);
		}
	}

	public static void shadow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int spread, int maxAlpha) {
		int col = fade(argb(Math.min(255, Math.round(maxAlpha * 0.75F)), 0x000000));
		if ((col >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		begin(ctx);
		try {
			int S = Math.max(2, px(spread) / 2);
			int R = Math.max(1, px(r));
			int C = R + 2 * S;
			int off = Math.round(S / 3F);
			int X = px(x) - S, Y = px(y) - S + off, X2 = px(x + w) + S, Y2 = px(y + h) + S + off;
			if (X2 - X < 2 * C || Y2 - Y < 2 * C) {
				return;
			}
			Identifier tex = shadowTex(R, S);
			if (tex == null) {
				return;
			}
			int D = 2 * C;
			quad(ctx, tex, X, Y, C, C, 0, 0, C, C, D, D, col);
			quad(ctx, tex, X2 - C, Y, C, C, C, 0, C, C, D, D, col);
			quad(ctx, tex, X, Y2 - C, C, C, 0, C, C, C, D, D, col);
			quad(ctx, tex, X2 - C, Y2 - C, C, C, C, C, C, C, D, D, col);
			quad(ctx, tex, X + C, Y, X2 - X - 2 * C, C, C - 1, 0, 1, C, D, D, col);
			quad(ctx, tex, X + C, Y2 - C, X2 - X - 2 * C, C, C - 1, C, 1, C, D, D, col);
			quad(ctx, tex, X, Y + C, C, Y2 - Y - 2 * C, 0, C - 1, C, 1, D, D, col);
			quad(ctx, tex, X2 - C, Y + C, C, Y2 - Y - 2 * C, C, C - 1, C, 1, D, D, col);
		} finally {
			end(ctx);
		}
	}

	public static void splitCircle(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int left, int right) {
		int l = fade(left), rr = fade(right);
		begin(ctx);
		try {
			int D = Math.max(2, Math.round(radius * 2 * ui) / 2 * 2);
			int X = Math.round(cx * ui - D / 2F), Y = Math.round(cy * ui - D / 2F);
			Identifier tex = circleTex(D);
			if (tex == null) {
				return;
			}
			quad(ctx, tex, X, Y, D / 2, D, 0, 0, D / 2, D, D, D, l);
			quad(ctx, tex, X + D / 2, Y, D / 2, D, D / 2, 0, D / 2, D, D, D, rr);
		} finally {
			end(ctx);
		}
	}

	public static void circle(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int argb) {
		int c = fade(argb);
		if ((c >>> 24) == 0) {
			return;
		}
		begin(ctx);
		try {
			int D = Math.max(2, Math.round(radius * 2 * ui));
			int X = Math.round(cx * ui - D / 2F), Y = Math.round(cy * ui - D / 2F);
			Identifier tex = circleTex(D);
			if (tex == null) {
				fillPx(ctx, X, Y, X + D, Y + D, c);
				return;
			}
			quad(ctx, tex, X, Y, D, D, 0, 0, D, D, D, D, c);
		} finally {
			end(ctx);
		}
	}

	public static float ui = 1F;

	public static UiFont.Family forceFamily;

	private static UiFont.Family family() {
		if (forceFamily != null) {
			return forceFamily;
		}
		int i = ClickGUI.menuFont.getModeIndex();
		UiFont.Family[] all = UiFont.Family.values();
		return i >= 0 && i < all.length ? all[i] : UiFont.Family.INTER;
	}

	private static float vanillaWidth(String s, float px) {
		return Minecraft.getInstance().font.width(s) * px / 9F;
	}

	public static int width(String s, float px) {
		return width(s, px, false);
	}

	public static int width(String s, float px, boolean bold) {
		if (s == null || s.isEmpty()) {
			return 0;
		}
		UiFont.Family f = family();
		if (UiFont.usable(f)) {
			float w = UiFont.width(f, bold, s, px, ui);
			if (w >= 0) {
				return Math.round(w);
			}
		}
		return Math.round(vanillaWidth(s, px));
	}

	public static void text(GuiGraphicsExtractor ctx, String s, float x, float y, float px, int argb) {
		text(ctx, s, x, y, px, argb, false);
	}

	public static void text(GuiGraphicsExtractor ctx, String s, float x, float y, float px, int argb, boolean bold) {
		if (s == null || s.isEmpty()) {
			return;
		}
		int c = fade(argb);
		if (((c >>> 24) & 0xFF) < 5) {
			return;
		}
		UiFont.Family f = family();
		if (UiFont.usable(f) && UiFont.draw(ctx, f, bold, s, x, y, px, ui, c)) {
			return;
		}
		float k = px / 9F;
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(x, y + px * 0.08F);
		m.scale(k, k);
		ctx.text(Minecraft.getInstance().font, s, 0, 0, c, false);
		m.popMatrix();
	}

	public static void textMid(GuiGraphicsExtractor ctx, String s, float x, float cy, float px, int argb) {
		text(ctx, s, x, cy - px / 2F, px, argb, false);
	}

	public static void textMid(GuiGraphicsExtractor ctx, String s, float x, float cy, float px, int argb, boolean bold) {
		text(ctx, s, x, cy - px / 2F, px, argb, bold);
	}

	public static void textCenter(GuiGraphicsExtractor ctx, String s, float cx, float cy, float px, int argb) {
		textCenter(ctx, s, cx, cy, px, argb, false);
	}

	public static void textCenter(GuiGraphicsExtractor ctx, String s, float cx, float cy, float px, int argb, boolean bold) {
		text(ctx, s, cx - width(s, px, bold) / 2F, cy - px / 2F, px, argb, bold);
	}

	public static void textRight(GuiGraphicsExtractor ctx, String s, float right, float cy, float px, int argb) {
		text(ctx, s, right - width(s, px), cy - px / 2F, px, argb, false);
	}

	private static final java.util.Map<String, String> FIT_CACHE = new java.util.HashMap<>();
	private static final java.util.Map<String, java.util.List<String>> WRAP_CACHE = new java.util.HashMap<>();

	private static String layoutKey(String s, float px, int maxW, int lines) {
		return family().ordinal() + "|" + ui + "|" + px + "|" + maxW + "|" + lines + "|" + s;
	}

	public static String fit(String s, float px, int maxW) {
		return fit(s, px, maxW, false);
	}

	public static String fit(String s, float px, int maxW, boolean bold) {
		if (s == null) {
			return "";
		}
		String key = layoutKey(s, px, maxW, bold ? -1 : 0);
		String have = FIT_CACHE.get(key);
		if (have == null) {
			if (FIT_CACHE.size() > 4000) {
				FIT_CACHE.clear();
			}
			have = fitNow(s, px, maxW, bold);
			FIT_CACHE.put(key, have);
		}
		return have;
	}

	private static String fitNow(String s, float px, int maxW, boolean bold) {
		if (s == null) {
			return "";
		}
		if (width(s, px, bold) <= maxW) {
			return s;
		}
		String dots = "...";
		int lo = 0, hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (width(s.substring(0, mid) + dots, px, bold) <= maxW) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo <= 0 ? "" : s.substring(0, lo).trim() + dots;
	}

	public static java.util.List<String> wrap(String s, float px, int maxW, int maxLines) {
		String key = layoutKey(s == null ? "" : s, px, maxW, maxLines);
		java.util.List<String> have = WRAP_CACHE.get(key);
		if (have == null) {
			if (WRAP_CACHE.size() > 2000) {
				WRAP_CACHE.clear();
			}
			have = java.util.List.copyOf(wrapNow(s, px, maxW, maxLines));
			WRAP_CACHE.put(key, have);
		}
		return have;
	}

	private static java.util.List<String> wrapNow(String s, float px, int maxW, int maxLines) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (s == null || s.isBlank()) {
			return out;
		}
		String[] words = s.trim().split("\\s+");
		StringBuilder line = new StringBuilder();
		int i = 0;
		while (i < words.length) {
			String next = line.isEmpty() ? words[i] : line + " " + words[i];
			if (width(next, px) <= maxW || line.isEmpty()) {
				line = new StringBuilder(next);
				i++;
				continue;
			}
			out.add(line.toString());
			line = new StringBuilder();
			if (out.size() == maxLines) {
				break;
			}
		}
		if (!line.isEmpty() && out.size() < maxLines) {
			out.add(line.toString());
		}
		if (i < words.length && !out.isEmpty()) {
			int last = out.size() - 1;
			String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
			out.set(last, fit(out.get(last) + " " + rest, px, maxW));
		}
		for (int j = 0; j < out.size(); j++) {
			out.set(j, fit(out.get(j), px, maxW));
		}
		return out;
	}
}
