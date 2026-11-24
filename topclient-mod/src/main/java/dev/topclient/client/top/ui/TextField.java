package dev.topclient.client.top.ui;

import dev.topclient.client.gui.Draw;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static dev.topclient.client.top.TopTheme.*;

/** Single-line text field (search box, hex input, rename). The screen forwards keys while it's focused. */
public final class TextField {
	public String text = "";
	public boolean focused;
	public final int max;
	public final String placeholder;
	private final Anim hover = new Anim();

	public TextField(String placeholder, int max) {
		this.placeholder = placeholder;
		this.max = max;
	}

	public void draw(Ui ui, int x, int y, int w, int h, String icon, int action, int arg) {
		float hv = hover.to(ui, ui.over(x, y, w, h) || focused, 14F);
		Draw.round(ui.ctx, x, y, w, h, 8, argb(235, FIELD));
		Draw.outline(ui.ctx, x, y, w, h, 8, 1, argb(255, focused ? ACCENT : mix(BORDER, BORDER_HOVER, hv)));
		int tx = x + 10;
		if (icon != null) {
			dev.topclient.client.gui.Icons.drawCentered(ui.ctx, icon, x + 16, y + h / 2F, 14, argb(255, focused ? ACCENT : TEXT_MUTE));
			tx = x + 30;
		}
		int room = w - (tx - x) - 10;
		if (text.isEmpty() && !focused) {
			Draw.textMid(ui.ctx, Draw.fit(placeholder, 12.5F, room), tx, y + h / 2F, 12.5F, argb(255, TEXT_MUTE));
		} else {
			String shown = text;
			while (shown.length() > 0 && Draw.width(shown, 12.5F) > room - 6) {
				shown = shown.substring(1);
			}
			Draw.textMid(ui.ctx, shown, tx, y + h / 2F, 12.5F, argb(255, TEXT));
			if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
				int cx = tx + Draw.width(shown, 12.5F) + 1;
				Draw.rect(ui.ctx, cx, y + h / 2 - 7, 1, 14, argb(255, ACCENT));
			}
		}
		ui.hit(x, y, w, h, action, arg);
	}

	/** Returns true when the key was used. Enter/Escape unfocus. */
	public boolean key(int key, int mods, boolean paste) {
		if (!focused) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			focused = false;
			return true;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (!text.isEmpty()) {
				text = (mods & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : text.substring(0, text.length() - 1);
			}
			return true;
		}
		if (paste) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) {
				type(clip.replace("\r", "").replace("\n", ""));
			}
			return true;
		}
		return true;
	}

	public void type(String s) {
		if (!focused) {
			return;
		}
		text = (text + s);
		if (text.length() > max) {
			text = text.substring(0, max);
		}
	}
}
