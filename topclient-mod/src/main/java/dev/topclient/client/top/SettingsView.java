package dev.topclient.client.top;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.ClickGui;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.ColorPicker;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;

import static dev.topclient.client.top.TopTheme.*;

/**
 * SETTINGS tab. Appearance: accent colour (HSV picker, hex, R/G/B), presets, gradient and rainbow,
 * window opacity, background darkening and blur, the three status colours, reset. General: the
 * title/pause overlay and which menu Right Shift opens. Every change shows immediately.
 */
final class SettingsView {
	static final int A_PRESET = 500, A_PICK = 501, A_PICKER = 502, A_OPACITY = 503, A_DIM = 504, A_BLUR = 505, A_RAINBOW = 506,
			A_SPEED = 507, A_GRADIENT = 508, A_STATUS = 509, A_RESET = 510, A_OVERLAY = 511, A_CLASSIC = 512, A_OPEN_CLASSIC = 513,
			A_SCROLL = 514, A_LAST = 514;

	private final Scroll scroll = new Scroll();
	private final ColorPicker picker = new ColorPicker();
	/** What the picker edits: 0 accent, 1 accent 2, 10..12 status colours, -1 closed. */
	private int picking = -1;
	private final Anim rainbowA = new Anim(), gradientA = new Anim(), overlayA = new Anim(), classicA = new Anim();
	private final Anim[] hPreset = new Anim[PRESETS.length];
	private int top, height;
	private final int[] sliderX = new int[4], sliderW = new int[4];
	private static final String[] STATUS = {"enabled", "disabled", "locked"};
	private static final String[] STATUS_LABEL = {"Enabled", "Disabled", "Locked"};
	private static final int[] STATUS_DEF = {DEF_ENABLED, DEF_DISABLED, DEF_LOCKED};

	SettingsView() {
		for (int i = 0; i < hPreset.length; i++) {
			hPreset[i] = new Anim();
		}
	}

	boolean isTyping() {
		return picker.hex.focused;
	}

	void draw(Ui ui, int x, int y, int w, int h) {
		top = y;
		height = h;
		scroll.update(ui);
		ui.clip(x, y, w, h);
		int cx = x + 28, cw = Math.min(w - 70, 760);
		int cy = y + 18 - Math.round(scroll.pos);

		cy = section(ui, "APPEARANCE", cx, cy, cw);
		// presets
		Draw.textMid(ui.ctx, "Theme presets", cx, cy + 12, 13.5F, argb(255, TEXT));
		cy += 30;
		int px = cx;
		String cur = preset().id();
		boolean custom = Config.get("top.theme.accent", null) != null;
		for (int i = 0; i < PRESETS.length; i++) {
			Preset p = PRESETS[i];
			int pw = Draw.width(p.name(), 12, true) + 44;
			if (px + pw > cx + cw) {
				px = cx;
				cy += 40;
			}
			boolean on = !custom && p.id().equals(cur);
			float hv = hPreset[i].to(ui, ui.over(px, cy, pw, 32), 14F);
			Draw.round(ui.ctx, px, cy, pw, 32, 8, argb(235, on ? mix(PANEL2, p.accent(), 0.2F) : mix(PANEL2, CARD_HOVER, hv)));
			Draw.outline(ui.ctx, px, cy, pw, 32, 8, 1, argb(255, on ? p.accent() : mix(BORDER, BORDER_HOVER, hv)));
			Draw.splitCircle(ui.ctx, px + 16, cy + 16, 7, argb(255, p.accent()), argb(255, p.accent2()));
			Draw.textMid(ui.ctx, p.name(), px + 30, cy + 16, 12, argb(255, on ? TEXT : TEXT_DIM), true);
			ui.hit(px, cy, pw, 32, A_PRESET, i);
			px += pw + 8;
		}
		cy += 48;
		// accent colours
		cy = colorRow(ui, "Accent colour", baseAccent(), 0, cx, cy, cw);
		cy = colorRow(ui, "Second accent (gradients)", baseAccent2(), 1, cx, cy, cw);
		cy = toggleRow(ui, "Gradient", "Accent fades into the second accent on bars, buttons and the logo.", gradientA.to(ui, GRADIENT, 14F), A_GRADIENT, cx, cy, cw);
		cy = toggleRow(ui, "Rainbow mode", "The accent colour slowly cycles through the rainbow.", rainbowA.to(ui, RAINBOW, 14F), A_RAINBOW, cx, cy, cw);
		if (RAINBOW) {
			cy = sliderRow(ui, "Rainbow speed", String.format("%.1fx", rainbowSpeed()), (rainbowSpeed() - 0.1F) / 2.9F, 3, A_SPEED, cx, cy, cw);
		}
		cy = sliderRow(ui, "Window opacity", windowOpacity() + "%", (windowOpacity() - 40) / 60F, 0, A_OPACITY, cx, cy, cw);
		cy = sliderRow(ui, "Background darkening", dim() + "%", dim() / 90F, 1, A_DIM, cx, cy, cw);
		cy = sliderRow(ui, "Title screen blur", blur() == 0 ? "Off" : Integer.toString(blur()), blur() / 10F, 2, A_BLUR, cx, cy, cw);

		cy = section(ui, "STATUS COLOURS", cx, cy + 8, cw);
		for (int i = 0; i < 3; i++) {
			cy = colorRow(ui, STATUS_LABEL[i], status(STATUS[i], STATUS_DEF[i]), 10 + i, cx, cy, cw);
		}
		cy += 6;
		Widgets.button(ui, cx, cy, 220, 34, "RESET APPEARANCE", false, ui.over(cx, cy, 220, 34) ? 1F : 0F, A_RESET, 0);
		cy += 54;

		cy = section(ui, "GENERAL", cx, cy, cw);
		cy = toggleRow(ui, "Title and pause menu bar", "TopClient logo with Cosmetics, MODS and Profile at the top of the main and pause menus.",
				overlayA.to(ui, MenuOverlay.enabled(), 14F), A_OVERLAY, cx, cy, cw);
		cy = toggleRow(ui, "Right Shift opens the classic menu", "The older menu with capes, sounds and more customisation.",
				classicA.to(ui, ClickGUI.classicMenu.getValue(), 14F), A_CLASSIC, cx, cy, cw);
		Widgets.button(ui, cx, cy + 4, 220, 34, "OPEN CLASSIC MENU", false, ui.over(cx, cy + 4, 220, 34) ? 1F : 0F, A_OPEN_CLASSIC, 0);
		cy += 60;
		ui.unclip();
		scroll.setMax(cy + Math.round(scroll.pos) - y - h);
		Widgets.scrollbar(ui, x + w - 12, y + 6, h - 12, scroll.pos, scroll.max, h, A_SCROLL, 0);
	}

	private int section(Ui ui, String title, int x, int y, int w) {
		Draw.text(ui.ctx, title, x, y + 6, 11, argb(255, ACCENT), true);
		int tw = Draw.width(title, 11, true);
		Draw.rect(ui.ctx, x + tw + 10, y + 12, w - tw - 10, 1, argb(255, BORDER));
		return y + 30;
	}

	private int colorRow(Ui ui, String label, int rgb, int which, int x, int y, int w) {
		boolean open = picking == which;
		Draw.textMid(ui.ctx, label, x, y + 18, 13.5F, argb(255, TEXT));
		Draw.textRight(ui.ctx, hex(rgb), x + w - 54, y + 18, 12, argb(255, TEXT_MUTE));
		Draw.round(ui.ctx, x + w - 44, y + 5, 44, 26, 7, argb(255, open ? ACCENT : BORDER));
		Draw.round(ui.ctx, x + w - 42, y + 7, 40, 22, 6, argb(255, rgb));
		ui.hit(x, y, w, 36, A_PICK, which);
		if (!open) {
			return y + 40;
		}
		int used = picker.draw(ui, x + 8, y + 42, Math.min(w - 16, 340), A_PICKER);
		return y + 42 + used + 16;
	}

	private int toggleRow(Ui ui, String label, String help, float on, int action, int x, int y, int w) {
		Draw.textMid(ui.ctx, label, x, y + 12, 13.5F, argb(255, TEXT));
		Draw.textMid(ui.ctx, Draw.fit(help, 11.5F, w - 60), x, y + 30, 11.5F, argb(255, TEXT_MUTE));
		Widgets.toggle(ui, x + w - 40, y + 10, on, action, 0);
		ui.hit(x, y, w - 50, 40, action, 0);
		return y + 48;
	}

	private int sliderRow(Ui ui, String label, String value, float f, int slot, int action, int x, int y, int w) {
		Draw.textMid(ui.ctx, label, x, y + 12, 13.5F, argb(255, TEXT));
		Draw.textRight(ui.ctx, value, x + w, y + 12, 12, argb(255, TEXT_DIM));
		sliderX[slot] = x + 6;
		sliderW[slot] = w - 12;
		Widgets.slider(ui, x + 6, y + 36, w - 12, f, action, slot);
		return y + 56;
	}

	// ---- input ----
	void click(int action, int arg, int button) {
		if (action != A_PICKER) {
			picker.hex.focused = false;
		}
		switch (action) {
			case A_PRESET -> applyPreset(PRESETS[arg]);
			case A_PICK -> {
				if (picking == arg) {
					picking = -1;
				} else {
					picking = arg;
					if (arg == 0) {
						picker.open(baseAccent(), TopTheme::setAccent);
					} else if (arg == 1) {
						picker.open(baseAccent2(), TopTheme::setAccent2);
					} else {
						String which = STATUS[arg - 10];
						picker.open(status(which, STATUS_DEF[arg - 10]), c -> setStatus(which, c));
					}
				}
			}
			case A_PICKER -> picker.click(arg);
			case A_RAINBOW -> setRainbow(!RAINBOW);
			case A_GRADIENT -> setGradient(!GRADIENT);
			case A_RESET -> {
				reset();
				picking = -1;
			}
			case A_OVERLAY -> Config.setBool("top.menuOverlay", !MenuOverlay.enabled());
			case A_CLASSIC -> ClickGUI.classicMenu.setValue(!ClickGUI.classicMenu.getValue());
			case A_OPEN_CLASSIC -> ClickGui.openClassic(null);
			default -> {
				return;
			}
		}
		Config.saveIfNeeded(false);
		UiSounds.click();
	}

	void drag(int action, int arg, double mx, double my) {
		switch (action) {
			case A_PICKER -> picker.drag(arg, mx, my);
			case A_OPACITY -> setWindowOpacity(40 + Math.round(Widgets.sliderValue(mx, sliderX[0], sliderW[0]) * 60));
			case A_DIM -> setDim(Math.round(Widgets.sliderValue(mx, sliderX[1], sliderW[1]) * 90));
			case A_BLUR -> setBlur(Math.round(Widgets.sliderValue(mx, sliderX[2], sliderW[2]) * 10));
			case A_SPEED -> setRainbowSpeed(0.1F + Widgets.sliderValue(mx, sliderX[3], sliderW[3]) * 2.9F);
			case A_SCROLL -> scroll.jump(Widgets.scrollFor(my, top + 6, height - 12, scroll.max, height));
			default -> {
			}
		}
	}

	void wheel(double v) {
		scroll.wheel(v);
	}

	boolean key(int key, int mods, boolean paste) {
		return picker.key(key, mods, paste);
	}

	boolean type(String s) {
		return picker.type(s);
	}
}
