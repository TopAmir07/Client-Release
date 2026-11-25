package dev.topclient.client.top.ui;

import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import static dev.topclient.client.top.TopTheme.*;

/**
 * Stateless TopClient widgets: Button, IconButton, Chip, Toggle, Slider, Panel and the logo.
 * Each takes a {@link Ui}, a position and a 0..1 "hover" value the caller keeps (see {@link Anim}),
 * draws itself and registers its click area. All colours come from TopTheme.
 */
public final class Widgets {
	private Widgets() {
	}

	public static final int R = 8; // default corner radius

	/** The dark, see-through rounded panel used by menus and buttons. */
	public static void panel(Ui ui, int x, int y, int w, int h, int r, float hover) {
		int fill = mix(PANEL2, CARD_HOVER, hover);
		Draw.round(ui.ctx, x, y, w, h, r, argb(Math.round(170 + 50 * hover), fill));
		Draw.outline(ui.ctx, x, y, w, h, r, 1, argb(255, mix(BORDER, BORDER_HOVER, hover)));
	}

	/** Text button. accent = filled with the accent colour. */
	public static void button(Ui ui, int x, int y, int w, int h, String label, boolean accent, float hover, int action, int arg) {
		if (accent) {
			Draw.roundGradient(ui.ctx, x, y, w, h, R, argb(255, mix(ACCENT, 0xFFFFFF, hover * 0.12F)), argb(255, mix(ACCENT2, ACCENT, hover * 0.3F)));
			Draw.textCenter(ui.ctx, label, x + w / 2F, y + h / 2F, 13, argb(255, ON_ACCENT), true);
		} else {
			panel(ui, x, y, w, h, R, hover);
			Draw.textCenter(ui.ctx, label, x + w / 2F, y + h / 2F, 13, argb(255, mix(TEXT_DIM, TEXT, hover)), true);
		}
		ui.hit(x, y, w, h, action, arg);
	}

	/** Square button with an icon (and an optional accent-coloured state). */
	public static void iconButton(Ui ui, int x, int y, int s, String icon, boolean active, float hover, int action, int arg) {
		panel(ui, x, y, s, s, R, Math.max(hover, active ? 0.6F : 0F));
		if (active) {
			Draw.outline(ui.ctx, x, y, s, s, R, 1, argb(255, ACCENT));
		}
		Icons.drawCentered(ui.ctx, icon, x + s / 2F, y + s / 2F, s * 0.5F, argb(255, active ? ACCENT : mix(TEXT_DIM, TEXT, hover)));
		ui.hit(x, y, s, s, action, arg);
	}

	/** A filter chip. Returns its width so callers can lay out a row. */
	public static int chip(Ui ui, int x, int y, int h, String label, boolean active, float hover, int action, int arg) {
		int w = Draw.width(label, 11.5F, true) + 24;
		int fill = active ? mix(PANEL2, ACCENT, 0.22F) : mix(PANEL2, CARD_HOVER, hover);
		Draw.round(ui.ctx, x, y, w, h, h / 2, argb(235, fill));
		Draw.outline(ui.ctx, x, y, w, h, h / 2, 1, argb(255, active ? ACCENT : mix(BORDER, BORDER_HOVER, hover)));
		Draw.textCenter(ui.ctx, label, x + w / 2F, y + h / 2F, 11.5F, argb(255, active ? TEXT : mix(TEXT_MUTE, TEXT, hover)), true);
		ui.hit(x, y, w, h, action, arg);
		return w;
	}

	public static int chipWidth(String label) {
		return Draw.width(label, 11.5F, true) + 24;
	}

	/** On/off switch, 40x22. on is the animated 0..1 position. */
	public static void toggle(Ui ui, int x, int y, float on, int action, int arg) {
		int w = 40, h = 22;
		int stateColour = mix(DISABLED, ENABLED, on);
		Draw.round(ui.ctx, x, y, w, h, h / 2, argb(255, stateColour));
		Draw.outline(ui.ctx, x, y, w, h, h / 2, 1, argb(255, mix(BORDER, stateColour, on)));
		float kx = x + 11 + on * (w - 22);
		Draw.circle(ui.ctx, kx, y + h / 2F, 8, argb(255, mix(TEXT_MUTE, 0xFFFFFF, on)));
		ui.hit(x - 4, y - 4, w + 8, h + 8, action, arg);
	}

	/**
	 * Horizontal slider. f is the 0..1 fill (animated by the caller). Registers a drag area: the
	 * screen turns the mouse x into a value with {@link #sliderValue}.
	 */
	public static void slider(Ui ui, int x, int y, int w, float f, int action, int arg) {
		f = Math.max(0F, Math.min(1F, f));
		Draw.round(ui.ctx, x, y - 3, w, 6, 3, argb(255, FIELD));
		int fw = Math.round(w * f);
		Draw.roundGradient(ui.ctx, x, y - 3, Math.max(6, fw), 6, 3, argb(255, ACCENT), argb(255, ACCENT2));
		Draw.circle(ui.ctx, x + fw, y + 1, 8.5F, argb(80, 0x000000));
		Draw.circle(ui.ctx, x + fw, y, 8, argb(255, ACCENT));
		Draw.circle(ui.ctx, x + fw, y, 5, argb(255, 0xFFFFFF));
		ui.dragHit(x - 8, y - 12, w + 16, 24, action, arg);
	}

	/** Mouse x to 0..1 along a slider that starts at x and is w wide. */
	public static float sliderValue(double mx, int x, int w) {
		return (float) Math.max(0, Math.min(1, (mx - x) / Math.max(1, w)));
	}

	/** Thin scrollbar on the right edge of a scroll view. */
	public static void scrollbar(Ui ui, int x, int y, int h, float pos, float max, int view, int action, int arg) {
		if (max <= 0) {
			return;
		}
		int thumb = Math.max(30, Math.round(h * view / (float) (view + max)));
		int ty = y + Math.round((h - thumb) * (pos / max));
		Draw.round(ui.ctx, x, y, 4, h, 2, argb(60, BORDER));
		boolean hot = ui.over(x - 6, y, 16, h) || ui.dragAction == action;
		Draw.round(ui.ctx, x, ty, 4, thumb, 2, argb(hot ? 255 : 170, ACCENT));
		ui.dragHit(x - 6, y, 16, h, action, arg);
	}

	/** Scroll position for a mouse y on a scrollbar (centres the thumb on the mouse). */
	public static float scrollFor(double my, int y, int h, float max, int view) {
		int thumb = Math.max(30, Math.round(h * view / (float) (view + max)));
		float f = (float) ((my - y - thumb / 2.0) / Math.max(1, h - thumb));
		return Math.max(0, Math.min(max, f * max));
	}

	/** The TopClient wordmark: logo mark, "TOP" bold and "CLIENT" light. Returns its width. */
	public static int wordmark(Ui ui, float x, float cy, float px) {
		float mark = px * 1.35F;
		logo(ui, x, cy - mark / 2F, mark);
		float tx = x + mark + px * 0.45F;
		Draw.textMid(ui.ctx, "TOP", tx, cy, px, argb(255, TEXT), true);
		float tw = Draw.width("TOP", px, true) + 1;
		Draw.textMid(ui.ctx, "CLIENT", tx + tw, cy, px, argb(255, TEXT_DIM), false);
		return Math.round(tx + tw + Draw.width("CLIENT", px) - x);
	}

	public static int wordmarkWidth(float px) {
		return Math.round(px * 1.35F + px * 0.45F + Draw.width("TOP", px, true) + 1 + Draw.width("CLIENT", px));
	}

	private static final net.minecraft.resources.Identifier MARK =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("topclient", "textures/brand/flower.png");

	/** The TopClient "T" mark, tinted with the accent colour. size = width and height. */
	public static void logo(Ui ui, float x, float y, float size) {
		org.joml.Matrix3x2fStack m = ui.ctx.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(size / 256F, size / 256F);
		ui.ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MARK, 0, 0, 0F, 0F, 256, 256, 256, 256, 256, 256,
				Draw.fade(argb(255, ACCENT)));
		m.popMatrix();
	}

	public static void tooltip(Ui ui, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		java.util.List<String> lines = Draw.wrap(text, 12, 300, 5);
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, Draw.width(l, 12));
		}
		w += 24;
		int h = lines.size() * 17 + 16;
		int x = (int) ui.mx + 16, y = (int) ui.my + 14;
		if (x + w > ui.W - 8) {
			x = (int) ui.mx - w - 12;
		}
		if (y + h > ui.H - 8) {
			y = (int) ui.my - h - 8;
		}
		Draw.shadow(ui.ctx, x, y, w, h, 9, 10, 60);
		Draw.round(ui.ctx, x, y, w, h, 9, argb(250, PANEL2));
		Draw.outline(ui.ctx, x, y, w, h, 9, 1, argb(255, BORDER));
		for (int i = 0; i < lines.size(); i++) {
			Draw.text(ui.ctx, lines.get(i), x + 12, y + 8 + i * 17, 12, argb(255, TEXT_DIM));
		}
	}
}
