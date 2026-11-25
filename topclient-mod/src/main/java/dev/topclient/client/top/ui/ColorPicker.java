package dev.topclient.client.top.ui;

import dev.topclient.client.gui.Draw;

import java.util.function.IntConsumer;

import static dev.topclient.client.top.TopTheme.*;

/**
 * HSV colour picker: saturation/brightness square, hue bar, hex input and R/G/B sliders.
 * Every change calls onChange right away so the result shows live.
 *
 * The screen gives it one action number; the picker uses args 0..5 for its parts and gets
 * clicks and drags back through {@link #click} and {@link #drag}.
 */
public final class ColorPicker {
	private final float[] hsv = new float[3];
	private int rgb, alpha = 255;
	private boolean alphaHex;
	private IntConsumer onChange, onAlphaChange;
	public final TextField hex = new TextField("#AARRGGBB", 9);
	private int sqX, sqY, sqS, hueX, hueY, hueW, slX, slW;
	private final int[] slY = new int[3];

	public static final int HEIGHT = 300;
	private static final String[] NAMES = {"R", "G", "B"};
	private static final String[] NUMBERS = new String[256];

	static {
		for (int i = 0; i < 256; i++) {
			NUMBERS[i] = Integer.toString(i);
		}
	}

	public void open(int rgb, IntConsumer onChange) {
		this.alphaHex = false;
		this.alpha = 255;
		this.onAlphaChange = null;
		this.onChange = onChange;
		set(rgb, true);
	}

	/** Opens an ARGB-aware picker; accepts both #RRGGBB and #AARRGGBB. */
	public void openArgb(int argb, IntConsumer onChange, IntConsumer onAlphaChange) {
		this.alphaHex = true;
		this.alpha = (argb >>> 24) & 0xFF;
		this.onChange = onChange;
		this.onAlphaChange = onAlphaChange;
		set(argb & 0xFFFFFF, true);
	}

	public int rgb() {
		return rgb;
	}

	private void set(int c, boolean fromOutside) {
		rgb = c & 0xFFFFFF;
		toHsv(rgb, hsv);
		hex.text = formatHex();
		if (!fromOutside && onChange != null) {
			onChange.accept(rgb);
		}
	}

	private void fromHsv() {
		rgb = hsv(hsv[0], hsv[1], hsv[2]);
		if (!hex.focused) {
			hex.text = hex(rgb);
		}
		if (onChange != null) {
			onChange.accept(rgb);
		}
	}

	/** Draws the picker into a w-wide column starting at (x, y). Returns the height used. */
	public int draw(Ui ui, int x, int y, int w, int action) {
		sqS = Math.min(200, w - 40);
		sqX = x;
		sqY = y;
		int hueCol = hsv(hsv[0], 1F, 1F);
		// saturation (left to right), then brightness (top to bottom)
		Draw.gradientH(ui.ctx, sqX, sqY, sqS, sqS, argb(255, 0xFFFFFF), argb(255, hueCol));
		Draw.gradientV(ui.ctx, sqX, sqY, sqS, sqS, argb(0, 0x000000), argb(255, 0x000000));
		Draw.outline(ui.ctx, sqX, sqY, sqS, sqS, 2, 1, argb(255, BORDER));
		float px = sqX + hsv[1] * sqS, py = sqY + (1F - hsv[2]) * sqS;
		Draw.circle(ui.ctx, px, py, 7, argb(255, 0xFFFFFF));
		Draw.circle(ui.ctx, px, py, 5, argb(255, rgb));
		ui.dragHit(sqX, sqY, sqS, sqS, action, 0);

		// hue bar (vertical, right of the square)
		hueX = sqX + sqS + 14;
		hueY = sqY;
		hueW = 16;
		int seg = sqS / 6;
		for (int i = 0; i < 6; i++) {
			int top = hsv(i / 6F, 1F, 1F), bottom = hsv((i + 1) / 6F % 1F, 1F, 1F);
			int sh = i == 5 ? sqS - seg * 5 : seg;
			Draw.gradientV(ui.ctx, hueX, hueY + seg * i, hueW, sh, argb(255, top), argb(255, bottom));
		}
		int hy = hueY + Math.round(hsv[0] * sqS);
		Draw.rect(ui.ctx, hueX - 3, hy - 2, hueW + 6, 4, argb(255, 0xFFFFFF));
		ui.dragHit(hueX - 4, hueY, hueW + 8, sqS, action, 1);

		// preview + hex
		int ry = sqY + sqS + 14;
		Draw.round(ui.ctx, x, ry, 34, 30, 7, argb(255, rgb));
		Draw.outline(ui.ctx, x, ry, 34, 30, 7, 1, argb(255, BORDER));
		hex.draw(ui, x + 42, ry, Math.min(130, w - 42), 30, null, action, 2);

		// R / G / B sliders
		slX = x + 18;
		slW = w - 70;
		int sy = ry + 52;
		for (int i = 0; i < 3; i++) {
			int part = (rgb >> (16 - i * 8)) & 0xFF;
			slY[i] = sy + i * 26;
			Draw.textMid(ui.ctx, NAMES[i], x, slY[i], 12, argb(255, TEXT_MUTE), true);
			Widgets.slider(ui, slX, slY[i], slW, part / 255F, action, 3 + i);
			Draw.textRight(ui.ctx, NUMBERS[part], x + w, slY[i], 12, argb(255, TEXT_DIM));
		}
		return (slY[2] + 16) - y;
	}

	public void click(int arg) {
		if (arg == 2) {
			hex.focused = true;
			hex.text = "";
		}
	}

	public void drag(int arg, double mx, double my) {
		switch (arg) {
			case 0 -> {
				hsv[1] = (float) Math.max(0, Math.min(1, (mx - sqX) / sqS));
				hsv[2] = 1F - (float) Math.max(0, Math.min(1, (my - sqY) / sqS));
				fromHsv();
			}
			case 1 -> {
				hsv[0] = (float) Math.max(0, Math.min(0.999, (my - hueY) / sqS));
				fromHsv();
			}
			case 3, 4, 5 -> {
				int v = Math.round(Widgets.sliderValue(mx, slX, slW) * 255);
				int shift = (5 - arg) * 8;
				int c = (rgb & ~(0xFF << shift)) | (v << shift);
				set(c, false);
			}
			default -> {
			}
		}
	}

	/** Keys for the hex field; applies the colour as soon as the text is a valid colour. */
	public boolean key(int key, int mods, boolean paste) {
		if (!hex.focused) {
			return false;
		}
		hex.key(key, mods, paste);
		applyHex();
		if (!hex.focused) {
			hex.text = hex(rgb);
		}
		return true;
	}

	public boolean type(String s) {
		if (!hex.focused) {
			return false;
		}
		hex.type(s);
		applyHex();
		return true;
	}

	private String formatHex() {
		return alphaHex ? String.format(java.util.Locale.ROOT, "#%02X%06X", alpha, rgb) : hex(rgb);
	}

	private void applyHex() {
		String value = hex.text.startsWith("#") ? hex.text.substring(1) : hex.text;
		try {
			if (value.length() == 6) {
				rgb = Integer.parseInt(value, 16);
				toHsv(rgb, hsv);
				if (onChange != null) onChange.accept(rgb);
			} else if (alphaHex && value.length() == 8) {
				alpha = Integer.parseInt(value.substring(0, 2), 16);
				rgb = Integer.parseInt(value.substring(2), 16);
				toHsv(rgb, hsv);
				if (onAlphaChange != null) onAlphaChange.accept(alpha);
				if (onChange != null) onChange.accept(rgb);
			}
		} catch (NumberFormatException ignored) {
			// Keep the last valid colour while the user is typing.
		}
	}
}
