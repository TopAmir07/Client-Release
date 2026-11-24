package dev.topclient.client.top;

import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.gui.model.GuiModule;
import dev.topclient.client.gui.model.GuiSetting;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.ColorPicker;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.TextField;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.topclient.client.top.TopTheme.*;

/**
 * A mod's OPTIONS page inside the mod menu: back arrow, name, description, enable switch, then
 * every setting with TopClient widgets (switches, sliders, mode pickers, key chips, text fields,
 * colour pickers, buttons) and "Reset to defaults" at the end.
 */
final class OptionsView {
	// action numbers (the screen passes them on)
	static final int A_BACK = 100, A_ENABLE = 101, A_TOGGLE = 102, A_SLIDER = 103, A_LOW = 104, A_HIGH = 105, A_MODE_PREV = 106,
			A_MODE_NEXT = 107, A_KEY = 108, A_TEXT = 109, A_COLOR = 110, A_PICKER = 111, A_ACTION = 112, A_RESET = 113, A_SCROLLBAR = 114,
			A_HUD = 115;

	GuiModule module;
	private List<GuiSetting> settings = List.of();
	private Anim[] anims = new Anim[0];
	private TextField[] fields = new TextField[0];
	private final Anim enableAnim = new Anim(), hBack = new Anim();
	private final Scroll scroll = new Scroll();
	private final ColorPicker picker = new ColorPicker();
	private int pickerFor = -1;
	int listening = -1;
	private int[] slX = new int[0], slW = new int[0];
	private int listTop, listH;
	private String tooltip;

	void open(GuiModule m) {
		module = m;
		settings = new ArrayList<>(m.settings());
		anims = new Anim[settings.size()];
		fields = new TextField[settings.size()];
		slX = new int[settings.size()];
		slW = new int[settings.size()];
		for (int i = 0; i < settings.size(); i++) {
			GuiSetting s = settings.get(i);
			anims[i] = new Anim(s.kind == GuiSetting.Kind.TOGGLE && s.bool() ? 1F : 0F);
			if (s.kind == GuiSetting.Kind.TEXT) {
				fields[i] = new TextField("Type here", 300);
				fields[i].text = s.text();
			}
		}
		enableAnim.v = m.toggleable() && m.enabled() ? 1F : 0F;
		pickerFor = -1;
		listening = -1;
		scroll.reset();
	}

	boolean isTyping() {
		if (listening >= 0 || picker.hex.focused) {
			return true;
		}
		for (TextField f : fields) {
			if (f != null && f.focused) {
				return true;
			}
		}
		return false;
	}

	void draw(Ui ui, int x, int y, int w, int h) {
		tooltip = null;
		// header row: back, icon, name, description, enable switch
		int hx = x + 20, hy = y + 16;
		float hb = hBack.to(ui, ui.over(hx, hy, 32, 32), 14F);
		Widgets.panel(ui, hx, hy, 32, 32, 8, hb);
		Icons.drawCentered(ui.ctx, Icons.ui("back"), hx + 16, hy + 16, 16, argb(255, mix(TEXT_DIM, TEXT, hb)));
		ui.hit(hx, hy, 32, 32, A_BACK, 0);
		Icons.drawModule(ui.ctx, module.name(), hx + 62, hy + 16, 26, argb(255, ACCENT));
		Draw.textMid(ui.ctx, module.name().toUpperCase(Locale.ROOT), hx + 86, hy + 8, 16, argb(255, TEXT), true);
		Draw.textMid(ui.ctx, Draw.fit(module.description(), 12, w - 260), hx + 86, hy + 27, 12, argb(255, TEXT_MUTE));
		if (module.toggleable()) {
			float e = enableAnim.to(ui, module.enabled(), 14F);
			Draw.textRight(ui.ctx, module.enabled() ? "ENABLED" : "DISABLED", x + w - 76, hy + 16, 11.5F, argb(255, module.enabled() ? ENABLED : DISABLED));
			Widgets.toggle(ui, x + w - 64, hy + 5, e, A_ENABLE, 0);
		}
		int sepY = y + 64;
		Draw.rect(ui.ctx, x + 20, sepY, w - 40, 1, argb(255, BORDER));

		// settings list
		listTop = sepY + 1;
		listH = y + h - listTop;
		int cx = x + 28, cw = w - 70;
		scroll.update(ui);
		ui.clip(x, listTop, w, listH);
		int cy = listTop + 12 - Math.round(scroll.pos);
		if (settings.isEmpty()) {
			Draw.textCenter(ui.ctx, module.onOpen() != null ? "This mod opens its own screen:" : "This mod has no options.", x + w / 2F, cy + 30, 13, argb(255, TEXT_MUTE));
			if (module.onOpen() != null) {
				Widgets.button(ui, x + w / 2 - 80, cy + 50, 160, 32, "OPEN", true, 0F, A_HUD, 0);
			}
			cy += 100;
		}
		for (int i = 0; i < settings.size(); i++) {
			cy += row(ui, i, cx, cy, cw);
		}
		if (!settings.isEmpty()) {
			cy += 8;
			Widgets.button(ui, cx, cy, 180, 32, "RESET TO DEFAULTS", false, ui.over(cx, cy, 180, 32) ? 1F : 0F, A_RESET, 0);
			cy += 48;
		}
		ui.unclip();
		int content = cy + Math.round(scroll.pos) - listTop;
		scroll.setMax(content - listH);
		Widgets.scrollbar(ui, x + w - 12, listTop + 6, listH - 12, scroll.pos, scroll.max, listH, A_SCROLLBAR, 0);
		Widgets.tooltip(ui, tooltip);
	}

	private int row(Ui ui, int i, int x, int y, int w) {
		GuiSetting s = settings.get(i);
		if (!ui.visible(y, 90) && s.kind != GuiSetting.Kind.COLOR) {
			return height(s, i);
		}
		boolean hover = ui.over(x, y, w, 40);
		if (hover && s.description != null && !s.description.isBlank()) {
			tooltip = s.description;
		}
		switch (s.kind) {
			case HEADER -> {
				String t = s.label.toUpperCase(Locale.ROOT);
				Draw.text(ui.ctx, t, x, y + 14, 11, argb(255, ACCENT), true);
				int tw = Draw.width(t, 11, true);
				Draw.rect(ui.ctx, x + tw + 10, y + 20, w - tw - 10, 1, argb(255, BORDER));
				return 34;
			}
			case TOGGLE -> {
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - 60), x, y + 20, 13.5F, argb(255, TEXT));
				Widgets.toggle(ui, x + w - 40, y + 9, anims[i].to(ui, s.bool(), 14F), A_TOGGLE, i);
				ui.hit(x, y, w - 50, 40, A_TOGGLE, i);
				return 42;
			}
			case SLIDER -> {
				String val = s.valueText();
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - 90), x, y + 14, 13.5F, argb(255, TEXT));
				Draw.textRight(ui.ctx, val, x + w, y + 14, 12, argb(255, TEXT_DIM));
				double range = Math.max(1e-9, s.max() - s.min());
				float f = anims[i].to(ui, (float) ((s.value() - s.min()) / range), 20F);
				slX[i] = x + 6;
				slW[i] = w - 12;
				Widgets.slider(ui, slX[i], y + 38, slW[i], f, A_SLIDER, i);
				return 58;
			}
			case RANGE -> {
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - 90), x, y + 14, 13.5F, argb(255, TEXT));
				Draw.textRight(ui.ctx, s.valueText(), x + w, y + 14, 12, argb(255, TEXT_DIM));
				double range = Math.max(1e-9, s.max() - s.min());
				Draw.textMid(ui.ctx, "Min", x, y + 38, 11, argb(255, TEXT_MUTE));
				Widgets.slider(ui, x + 40, y + 38, w - 46, (float) ((s.low() - s.min()) / range), A_LOW, i);
				Draw.textMid(ui.ctx, "Max", x, y + 64, 11, argb(255, TEXT_MUTE));
				Widgets.slider(ui, x + 40, y + 64, w - 46, (float) ((s.high() - s.min()) / range), A_HIGH, i);
				slX[i] = x + 40;
				slW[i] = w - 46;
				return 84;
			}
			case MODE -> {
				int bw = Math.min(220, w / 2);
				int bx = x + w - bw;
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - bw - 12), x, y + 20, 13.5F, argb(255, TEXT));
				Draw.round(ui.ctx, bx, y + 5, bw, 30, 8, argb(235, FIELD));
				Draw.outline(ui.ctx, bx, y + 5, bw, 30, 8, 1, argb(255, ui.over(bx, y + 5, bw, 30) ? BORDER_HOVER : BORDER));
				Icons.drawCentered(ui.ctx, Icons.ui("back"), bx + 15, y + 20, 14, argb(255, ui.over(bx, y + 5, 32, 30) ? ACCENT : TEXT_MUTE));
				Icons.drawCentered(ui.ctx, Icons.ui("chevron"), bx + bw - 15, y + 20, 14, argb(255, ui.over(bx + bw - 32, y + 5, 32, 30) ? ACCENT : TEXT_MUTE));
				Draw.textCenter(ui.ctx, Draw.fit(s.valueText(), 12.5F, bw - 70), bx + bw / 2F, y + 20, 12.5F, argb(255, TEXT), true);
				ui.hit(bx, y + 5, bw / 2, 30, A_MODE_PREV, i);
				ui.hit(bx + bw / 2, y + 5, bw - bw / 2, 30, A_MODE_NEXT, i);
				return 42;
			}
			case KEY -> {
				boolean listen = listening == i;
				String k = listen ? "PRESS A KEY..." : s.keyName().toUpperCase(Locale.ROOT);
				int kw = Math.max(96, Draw.width(k, 12, true) + 28);
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - kw - 12), x, y + 20, 13.5F, argb(255, TEXT));
				float pulse = listen ? 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0F;
				Draw.round(ui.ctx, x + w - kw, y + 6, kw, 28, 8, argb(235, listen ? mix(FIELD, ACCENT, 0.15F) : FIELD));
				Draw.outline(ui.ctx, x + w - kw, y + 6, kw, 28, 8, 1, argb(255, listen ? mix(BORDER, ACCENT, pulse) : BORDER));
				Draw.textCenter(ui.ctx, k, x + w - kw / 2F, y + 20, 12, argb(255, listen ? ACCENT : TEXT_DIM), true);
				ui.hit(x + w - kw, y + 6, kw, 28, A_KEY, i);
				return 42;
			}
			case TEXT -> {
				int fw = Math.min(240, w * 3 / 5);
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - fw - 12), x, y + 20, 13.5F, argb(255, TEXT));
				fields[i].draw(ui, x + w - fw, y + 5, fw, 30, null, A_TEXT, i);
				return 42;
			}
			case COLOR -> {
				boolean open = pickerFor == i;
				int rgb = s.rgb() & 0xFFFFFF;
				Draw.textMid(ui.ctx, Draw.fit(s.label, 13.5F, w - 150), x, y + 20, 13.5F, argb(255, TEXT));
				Draw.textRight(ui.ctx, hex(rgb), x + w - 52, y + 20, 12, argb(255, TEXT_MUTE));
				Draw.round(ui.ctx, x + w - 42, y + 7, 42, 26, 7, argb(255, open ? ACCENT : BORDER));
				Draw.round(ui.ctx, x + w - 40, y + 9, 38, 22, 6, argb(255, rgb));
				ui.hit(x, y, w, 40, A_COLOR, i);
				if (!open) {
					return 42;
				}
				int used = picker.draw(ui, x + 8, y + 46, Math.min(w - 16, 320), A_PICKER);
				return 46 + used + 14;
			}
			case ACTION -> {
				Widgets.button(ui, x, y + 4, Math.min(w, 260), 32, s.label.toUpperCase(Locale.ROOT), false, ui.over(x, y + 4, Math.min(w, 260), 32) ? 1F : 0F, A_ACTION, i);
				return 44;
			}
		}
		return 0;
	}

	private int height(GuiSetting s, int i) {
		return switch (s.kind) {
			case HEADER -> 34;
			case SLIDER -> 58;
			case RANGE -> 84;
			case ACTION -> 44;
			default -> 42;
		};
	}

	// ---- input (the screen forwards these) ----
	/** Returns true when the click means "go back to the grid". */
	boolean click(int action, int arg, int button) {
		GuiSetting s = arg >= 0 && arg < settings.size() ? settings.get(arg) : null;
		if (action != A_KEY && listening >= 0) {
			listening = -1;
		}
		if (action != A_TEXT) {
			for (TextField f : fields) {
				if (f != null && f.focused) {
					f.focused = false;
				}
			}
			commitFields();
		}
		if (action != A_PICKER) {
			picker.hex.focused = false;
		}
		switch (action) {
			case A_BACK -> {
				commitFields();
				return true;
			}
			case A_ENABLE -> module.setEnabled(!module.enabled());
			case A_TOGGLE -> s.setBool(!s.bool());
			case A_MODE_PREV -> s.setModeIndex(button == 1 ? s.modeIndex() + 1 : s.modeIndex() - 1);
			case A_MODE_NEXT -> s.setModeIndex(button == 1 ? s.modeIndex() - 1 : s.modeIndex() + 1);
			case A_KEY -> {
				if (button == 1) {
					s.setKey(s.noneKey());
					listening = -1;
				} else {
					listening = listening == arg ? -1 : arg;
				}
			}
			case A_TEXT -> fields[arg].focused = true;
			case A_COLOR -> {
				if (pickerFor == arg) {
					pickerFor = -1;
				} else {
					pickerFor = arg;
					picker.open(s.rgb(), s::setRgb);
				}
			}
			case A_PICKER -> picker.click(arg);
			case A_ACTION -> s.run();
			case A_HUD -> {
				Runnable r = module.onOpen();
				if (r != null) {
					r.run();
				}
			}
			case A_RESET -> {
				module.resetSettings();
				open(module);
			}
			default -> {
				return false;
			}
		}
		UiSounds.click();
		return false;
	}

	void drag(Ui ui, int action, int arg, double mx, double my) {
		GuiSetting s = arg >= 0 && arg < settings.size() ? settings.get(arg) : null;
		switch (action) {
			case A_SLIDER -> s.setValue(s.snap(s.min() + Widgets.sliderValue(mx, slX[arg], slW[arg]) * (s.max() - s.min())));
			case A_LOW -> s.setLow(Math.min(s.high(), s.snap(s.min() + Widgets.sliderValue(mx, slX[arg], slW[arg]) * (s.max() - s.min()))));
			case A_HIGH -> s.setHigh(Math.max(s.low(), s.snap(s.min() + Widgets.sliderValue(mx, slX[arg], slW[arg]) * (s.max() - s.min()))));
			case A_PICKER -> picker.drag(arg, mx, my);
			case A_SCROLLBAR -> scroll.jump(Widgets.scrollFor(my, listTop + 6, listH - 12, scroll.max, listH));
			default -> {
			}
		}
	}

	void wheel(double amount) {
		scroll.wheel(amount);
	}

	boolean key(int key, int mods, boolean paste) {
		if (listening >= 0) {
			GuiSetting s = settings.get(listening);
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				listening = -1;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				s.setKey(s.noneKey());
				listening = -1;
			} else {
				s.setKey(key);
				listening = -1;
			}
			return true;
		}
		if (picker.key(key, mods, paste)) {
			return true;
		}
		for (int i = 0; i < fields.length; i++) {
			TextField f = fields[i];
			if (f != null && f.focused) {
				f.key(key, mods, paste);
				if (!f.focused) {
					settings.get(i).setText(f.text.strip());
				}
				return true;
			}
		}
		return false;
	}

	boolean mouseButtonAsKey(int button) {
		if (listening >= 0 && button >= 2) {
			GuiSetting s = settings.get(listening);
			if (s.mouseKeys()) {
				s.setKey(button);
				listening = -1;
				return true;
			}
		}
		return false;
	}

	boolean type(String s) {
		if (picker.type(s)) {
			return true;
		}
		for (TextField f : fields) {
			if (f != null && f.focused) {
				f.type(s);
				return true;
			}
		}
		return false;
	}

	private void commitFields() {
		for (int i = 0; i < fields.length; i++) {
			if (fields[i] != null && !fields[i].text.strip().equals(settings.get(i).text())) {
				settings.get(i).setText(fields[i].text.strip());
			}
		}
	}
}
