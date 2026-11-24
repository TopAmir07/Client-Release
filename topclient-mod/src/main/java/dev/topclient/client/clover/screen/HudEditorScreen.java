package dev.topclient.client.clover.screen;

import dev.topclient.client.TopClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.HudModule;
import dev.topclient.client.clover.module.ModuleManager;
import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.top.TopProfiles;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static dev.topclient.client.top.TopTheme.*;

/**
 * HUD editor, Lunar style. Drag a mod to move it (it snaps to the centre and the edges), drag the
 * arrow on its corner to resize it. The bar at the top opens the HUD mods list (switch each one
 * on or off), turns snapping on/off, resets the layout, saves it to your profile and goes back.
 * Opened from the main menu it shows a pretend game screen behind the HUD so you still see it.
 */
public class HudEditorScreen extends Screen implements Ui.Handler {
	private static final int A_PANEL = 1, A_SNAP = 2, A_RESET = 3, A_SAVE = 4, A_MENU = 5, A_DONE = 6, A_TOGGLE = 7, A_SELECT = 8,
			A_RESET_ONE = 9, A_PANEL_BG = 10;
	private static final float MIN_SCALE = 0.5F, MAX_SCALE = 2.5F;
	private static final int SNAP = 5;

	private final Screen parent;
	private final Ui ui = new Ui(1F);
	private HudModule dragging, resizing, selected;
	private int grabX, grabY;
	private float startScale;
	private int anchorX, anchorY, startW, startH;
	private int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE;
	private boolean panelOpen;
	private final Anim panelAnim = new Anim(), appear = new Anim();
	private final Anim[] hBar = {new Anim(), new Anim(), new Anim(), new Anim(), new Anim(), new Anim()};
	private final List<Anim> rowAnims = new ArrayList<>(), toggleAnims = new ArrayList<>();
	private final Scroll listScroll = new Scroll();
	private int panelX, panelY, panelW, panelH;
	private String toast;
	private long toastAt;
	/** The Right Shift press that opened this screen is passed on to it too: ignore that one. */
	private final long openedAt = System.currentTimeMillis();
	private boolean keyReleasedSinceOpen;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	private static boolean snapOn() {
		return Config.getBool("top.hudSnap", true);
	}

	private int handle() {
		return Math.max(7, (int) Math.ceil(14 / Minecraft.getInstance().getWindow().getGuiScale()));
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			drawPretendGame(ctx);
		} else {
			ctx.fill(0, 0, width, height, 0x30000000);
		}
		// faint centre lines so you can line things up
		ctx.fill(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
		ctx.fill(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);

		boolean overUi = overUi(mouseX, mouseY);
		HudModule hover = dragging == null && resizing == null && !overUi ? at(mouseX, mouseY) : null;
		int hs = handle();
		for (HudModule h : ModuleManager.huds()) {
			if (!h.enabled()) {
				continue;
			}
			h.render(ctx, mc, true);
			int bw = h.screenW(ctx, mc), bh = h.screenH(ctx, mc);
			int x = h.screenX(width, bw), y = h.screenY(height, bh);
			boolean active = h == hover || h == dragging || h == resizing || h == selected;
			int c = active ? 0xFF000000 | ACCENT : argb(90, ACCENT);
			if (active) {
				ctx.fill(x, y, x + bw, y + bh, argb(28, ACCENT));
			}
			ctx.fill(x - 1, y - 1, x + bw + 1, y, c);
			ctx.fill(x - 1, y + bh, x + bw + 1, y + bh + 1, c);
			ctx.fill(x - 1, y, x, y + bh, c);
			ctx.fill(x + bw, y, x + bw + 1, y + bh, c);
			if (active) {
				drawHandle(ctx, x + bw, y + bh, hs, overHandle(h, mouseX, mouseY) || h == resizing);
				String label = h.name + "  " + Math.round(h.scale.getFloat() * 100) + "%";
				Gfx.shadow(ctx, label, x, y > 14 ? y - 11 : y + bh + 4, 0xFFFFFFFF);
			}
		}
		if (dragging != null && guideX != Integer.MIN_VALUE) {
			ctx.fill(guideX, 0, guideX + 1, height, 0xFF000000 | ACCENT);
		}
		if (dragging != null && guideY != Integer.MIN_VALUE) {
			ctx.fill(0, guideY, width, guideY + 1, 0xFF000000 | ACCENT);
		}

		ui.begin(ctx, mouseX, mouseY);
		try {
			float a = appear.to(ui, true, 8F);
			Draw.alpha = a;
			drawBar(a);
			float p = panelAnim.to(ui, panelOpen, 12F);
			if (p > 0.01F) {
				drawPanel(p);
			}
			if (toast != null && System.currentTimeMillis() - toastAt < 1800) {
				float k = Math.min(1F, (1800 - (System.currentTimeMillis() - toastAt)) / 300F);
				Draw.alpha = k;
				int tw = Draw.width(toast, 13, true) + 36, th = 34, tx = (ui.W - tw) / 2, ty = ui.H - th - 30;
				Draw.shadow(ctx, tx, ty, tw, th, 10, 14, 90);
				Draw.round(ctx, tx, ty, tw, th, 10, argb(245, PANEL2));
				Draw.outline(ctx, tx, ty, tw, th, 10, 1, argb(255, ACCENT));
				Draw.textCenter(ctx, toast, tx + tw / 2F, ty + th / 2F, 13, argb(255, TEXT), true);
			}
		} finally {
			ui.end();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		// no blur: the real game (or the pretend one) stays sharp behind the HUD
	}

	/** A pretend game screen (sky, hills, hotbar) for when there's no world loaded. */
	private void drawPretendGame(GuiGraphicsExtractor ctx) {
		int h = height, w = width;
		ctx.fillGradient(0, 0, w, h * 6 / 10, 0xFF5E8FD8, 0xFFA9C8F0);
		ctx.fillGradient(0, h * 6 / 10, w, h, 0xFF4E8A3A, 0xFF2F5A22);
		for (int i = 0; i < 9; i++) { // blocky hills
			int hx = i * w / 8 - w / 16, hh = 14 + (i * 37 % 23);
			ctx.fill(hx, h * 6 / 10 - hh, hx + w / 8, h * 6 / 10, 0xFF5C9A45);
		}
		int hb = 182, hx = w / 2 - hb / 2, hy = h - 24;
		ctx.fill(hx, hy, hx + hb, hy + 22, 0x88000000);
		for (int i = 0; i < 9; i++) {
			ctx.fill(hx + 1 + i * 20, hy + 1, hx + 21 + i * 20, hy + 2, 0x55FFFFFF);
		}
		ctx.fill(w / 2 - 4, h / 2, w / 2 + 5, h / 2 + 1, 0xDDFFFFFF);
		ctx.fill(w / 2, h / 2 - 4, w / 2 + 1, h / 2 + 5, 0xDDFFFFFF);
	}

	private static final String[] BAR = {"HUD Mods", "Snap", "Reset layout", "Save layout", "Mod menu", "Done"};

	private int[] barRect() {
		int total = 0;
		for (String s : BAR) {
			total += Draw.width(s, 12.5F, true) + 30 + 8;
		}
		total += 8;
		return new int[]{(ui.W - total) / 2, 14, total, 44};
	}

	private void drawBar(float a) {
		int[] r = barRect();
		int y = r[1] + Math.round((1F - a) * -16F);
		Draw.shadow(ui.ctx, r[0], y, r[2], r[3], 14, 16, 110);
		Draw.round(ui.ctx, r[0], y, r[2], r[3], 14, argb(235, BG));
		Draw.outline(ui.ctx, r[0], y, r[2], r[3], 14, 1, argb(255, BORDER));
		int x = r[0] + 8;
		for (int i = 0; i < BAR.length; i++) {
			String label = i == 1 ? (snapOn() ? "Snap: On" : "Snap: Off") : BAR[i];
			int w = Draw.width(BAR[i], 12.5F, true) + 30;
			if (i == 1) {
				w = Draw.width("Snap: Off", 12.5F, true) + 30;
			}
			boolean accent = i == 5 || (i == 0 && panelOpen);
			float hv = hBar[i].to(ui, ui.over(x, y + 6, w, 32), 14F);
			Widgets.button(ui, x, y + 6, w, 32, label, accent, hv, A_PANEL + i, 0);
			x += w + 8;
		}
	}

	private void drawPanel(float p) {
		List<HudModule> mods = ModuleManager.huds();
		while (rowAnims.size() < mods.size()) {
			rowAnims.add(new Anim());
			toggleAnims.add(new Anim());
		}
		panelW = 330;
		panelX = 16 + Math.round((1F - p) * -(panelW + 24));
		panelY = 74;
		panelH = ui.H - panelY - 16;
		Draw.alpha = p;
		Draw.shadow(ui.ctx, panelX, panelY, panelW, panelH, 14, 20, 130);
		Draw.round(ui.ctx, panelX, panelY, panelW, panelH, 14, argb(240, BG));
		Draw.outline(ui.ctx, panelX, panelY, panelW, panelH, 14, 1, argb(255, BORDER));
		ui.hit(panelX, panelY, panelW, panelH, A_PANEL_BG, 0); // clicks on the panel never reach the HUD
		Draw.text(ui.ctx, "HUD MODS", panelX + 18, panelY + 16, 11, argb(255, TEXT_MUTE), true);
		int on = 0;
		for (HudModule h : mods) {
			on += h.enabled() ? 1 : 0;
		}
		Draw.textRight(ui.ctx, on + " of " + mods.size() + " on", panelX + panelW - 18, panelY + 22, 11, argb(255, TEXT_MUTE));
		int listY = panelY + 42, listH = panelH - 42 - 60, rh = 42;
		listScroll.setMax(mods.size() * (rh + 4) - listH);
		listScroll.update(ui);
		ui.clip(panelX + 1, listY, panelW - 2, listH);
		for (int i = 0; i < mods.size(); i++) {
			int ry = listY + i * (rh + 4) - Math.round(listScroll.pos);
			if (!ui.visible(ry, rh)) {
				continue;
			}
			HudModule h = mods.get(i);
			int rx = panelX + 10, rw = panelW - 20;
			boolean sel = h == selected;
			float hv = rowAnims.get(i).to(ui, ui.over(rx, ry, rw, rh), 14F);
			int fill = sel ? mix(PANEL2, ACCENT, 0.2F) : mix(PANEL, CARD_HOVER, hv);
			Draw.round(ui.ctx, rx, ry, rw, rh, 10, argb(230, fill));
			if (sel) {
				Draw.outline(ui.ctx, rx, ry, rw, rh, 10, 1, argb(255, ACCENT));
			}
			Draw.textMid(ui.ctx, h.name, rx + 14, ry + rh / 2F, 13, argb(255, h.enabled() ? TEXT : TEXT_MUTE), true);
			ui.hit(rx, ry, rw - 56, rh, A_SELECT, i);
			Widgets.toggle(ui, rx + rw - 52, ry + (rh - 22) / 2, toggleAnims.get(i).to(ui, h.enabled(), 14F), A_TOGGLE, i);
		}
		ui.unclip();
		int by = panelY + panelH - 48;
		boolean has = selected != null;
		Widgets.button(ui, panelX + 12, by, panelW - 24, 36, has ? "Reset " + selected.name : "Pick a mod to reset it", false,
				has && ui.over(panelX + 12, by, panelW - 24, 36) ? 1F : 0F, has ? A_RESET_ONE : A_PANEL_BG, 0);
		Draw.alpha = 1F;
	}

	/** A square with a diagonal resize arrow, centred on the mod's bottom-right corner. Drawn in real pixels. */
	private void drawHandle(GuiGraphicsExtractor ctx, int cx, int cy, int size, boolean hot) {
		float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
		int px = Math.round(size * gs);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(cx - size / 2F, cy - size / 2F);
		m.scale(1F / gs, 1F / gs);
		int bg = hot ? 0xFF000000 | ACCENT : 0xE0101014;
		int fg = hot ? 0xFFFFFFFF : 0xFF000000 | ACCENT;
		ctx.fill(0, 0, px, px, bg);
		ctx.fill(0, 0, px, 1, 0xFFFFFFFF);
		ctx.fill(0, px - 1, px, px, 0xFFFFFFFF);
		ctx.fill(0, 0, 1, px, 0xFFFFFFFF);
		ctx.fill(px - 1, 0, px, px, 0xFFFFFFFF);
		int in = Math.max(2, px / 5), t = Math.max(1, px / 9);
		for (int i = in; i <= px - in - t; i++) {
			ctx.fill(i, i, i + t + 1, i + t + 1, fg);
		}
		int head = Math.max(3, px / 3);
		ctx.fill(px - in - head, px - in - t, px - in, px - in, fg);
		ctx.fill(px - in - t, px - in - head, px - in, px - in, fg);
		m.popMatrix();
	}

	// ------------------------------------------------------------------ toolbar and panel actions

	@Override
	public void onClick(int action, int arg, int button) {
		List<HudModule> mods = ModuleManager.huds();
		switch (action) {
			case A_PANEL -> {
				panelOpen = !panelOpen;
				UiSounds.click();
			}
			case A_SNAP -> {
				Config.setBool("top.hudSnap", !snapOn());
				UiSounds.click();
			}
			case A_RESET -> {
				for (HudModule h : mods) {
					h.resetPos();
					h.scale.reset();
				}
				Config.saveIfNeeded(true);
				say("Layout reset to the defaults");
			}
			case A_SAVE -> {
				Config.saveIfNeeded(true);
				try {
					TopProfiles.save(TopProfiles.active());
					TopClient.INSTANCE.getProfileManager().saveProfile();
				} catch (RuntimeException ignored) {
				}
				say("Layout saved to profile " + TopProfiles.nameOf(TopProfiles.active()));
			}
			case A_MENU -> {
				UiSounds.click();
				openMenu();
			}
			case A_DONE -> {
				UiSounds.click();
				onClose();
			}
			case A_TOGGLE -> {
				if (arg < mods.size()) {
					HudModule h = mods.get(arg);
					h.setEnabled(!h.enabled());
					Config.saveIfNeeded(false);
				}
			}
			case A_SELECT -> {
				if (arg < mods.size()) {
					selected = mods.get(arg);
					UiSounds.click();
				}
			}
			case A_RESET_ONE -> {
				if (selected != null) {
					selected.resetPos();
					selected.scale.reset();
					Config.saveIfNeeded(true);
					say(selected.name + " reset");
				}
			}
			default -> {
			}
		}
	}

	private void say(String s) {
		toast = s;
		toastAt = System.currentTimeMillis();
		UiSounds.click();
	}

	private boolean overUi(double guiX, double guiY) {
		double dx = ui.toDesign(guiX), dy = ui.toDesign(guiY);
		int[] r = barRect();
		if (dx >= r[0] && dy >= r[1] && dx < r[0] + r[2] && dy < r[1] + r[3]) {
			return true;
		}
		return panelOpen && dx >= panelX && dy >= panelY && dx < panelX + panelW && dy < panelY + panelH;
	}

	// ------------------------------------------------------------------ dragging the HUD

	private boolean overHandle(HudModule h, double mx, double my) {
		Minecraft mc = Minecraft.getInstance();
		int bw = h.screenW(null, mc), bh = h.screenH(null, mc);
		int x = h.screenX(width, bw), y = h.screenY(height, bh);
		int hs = handle() + 2;
		return Gfx.inside(mx, my, x + bw - hs / 2, y + bh - hs / 2, hs, hs);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (ui.click(ui.toDesign(click.x()), ui.toDesign(click.y()), click.button(), this)) {
			return true;
		}
		if (click.button() == 0) {
			Minecraft mc = Minecraft.getInstance();
			var mods = ModuleManager.huds();
			for (int i = mods.size() - 1; i >= 0; i--) { // resize handles first (they stick out)
				HudModule h = mods.get(i);
				if (h.enabled() && overHandle(h, click.x(), click.y())) {
					startResize(h, mc);
					return true;
				}
			}
			HudModule h = at(click.x(), click.y());
			if (h != null) {
				int bw = h.screenW(null, mc), bh = h.screenH(null, mc);
				dragging = h;
				selected = h;
				grabX = (int) click.x() - h.screenX(width, bw);
				grabY = (int) click.y() - h.screenY(height, bh);
				return true;
			}
			selected = null;
		} else if (click.button() == 1) { // right click a mod: switch it off
			HudModule h = at(click.x(), click.y());
			if (h != null) {
				h.setEnabled(false);
				say(h.name + " hidden (turn it back on in HUD Mods)");
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	private void startResize(HudModule h, Minecraft mc) {
		resizing = h;
		selected = h;
		startScale = h.scale.getFloat();
		startW = Math.max(1, h.screenW(null, mc));
		startH = Math.max(1, h.screenH(null, mc));
		anchorX = h.screenX(width, startW);
		anchorY = h.screenY(height, startH);
	}

	private void openMenu() {
		if (parent instanceof dev.topclient.client.gui.ClickGui || parent instanceof dev.topclient.client.top.ModMenuScreen) {
			onClose(); // opened from the menu: go back to it
			return;
		}
		ClickGUI menu = TopClient.INSTANCE.getModuleManager().getModule(ClickGUI.class);
		if (menu != null && !menu.isEnabled()) {
			menu.setEnabled(true);
		} else {
			dev.topclient.client.top.ModMenuScreen.open(this);
		}
	}

	private HudModule at(double mx, double my) {
		Minecraft mc = Minecraft.getInstance();
		var mods = ModuleManager.huds();
		for (int i = mods.size() - 1; i >= 0; i--) {
			HudModule h = mods.get(i);
			if (!h.enabled()) {
				continue;
			}
			int bw = h.screenW(null, mc), bh = h.screenH(null, mc);
			if (Gfx.inside(mx, my, h.screenX(width, bw), h.screenY(height, bh), bw, bh)) {
				return h;
			}
		}
		return null;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (ui.drag(ui.toDesign(click.x()), ui.toDesign(click.y()), this)) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (resizing != null) {
			float fx = (float) (click.x() - anchorX) / startW, fy = (float) (click.y() - anchorY) / startH;
			float f = Math.max(fx, fy);
			float s = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, startScale * f)) * 20F) / 20F;
			if (s != resizing.scale.getFloat()) {
				resizing.scale.setFloat(s);
				int nw = resizing.screenW(null, mc), nh = resizing.screenH(null, mc);
				resizing.setPos((float) anchorX / Math.max(1, width - nw), (float) anchorY / Math.max(1, height - nh));
			}
			return true;
		}
		if (dragging != null) {
			int bw = dragging.screenW(null, mc), bh = dragging.screenH(null, mc);
			int x = (int) Math.round(click.x() - grabX), y = (int) Math.round(click.y() - grabY);
			guideX = guideY = Integer.MIN_VALUE;
			if (snapOn()) {
				int cx = x + bw / 2, cy = y + bh / 2;
				if (Math.abs(cx - width / 2) <= SNAP) {
					x = width / 2 - bw / 2;
					guideX = width / 2;
				} else if (Math.abs(x - 2) <= SNAP) {
					x = 2;
					guideX = 2;
				} else if (Math.abs(x + bw - (width - 2)) <= SNAP) {
					x = width - 2 - bw;
					guideX = width - 2;
				}
				if (Math.abs(cy - height / 2) <= SNAP) {
					y = height / 2 - bh / 2;
					guideY = height / 2;
				} else if (Math.abs(y - 2) <= SNAP) {
					y = 2;
					guideY = 2;
				} else if (Math.abs(y + bh - (height - 2)) <= SNAP) {
					y = height - 2 - bh;
					guideY = height - 2;
				}
			}
			dragging.setPos((float) x / Math.max(1, width - bw), (float) y / Math.max(1, height - bh));
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (ui.release(this)) {
			return true;
		}
		if (dragging != null || resizing != null) {
			dragging = null;
			resizing = null;
			guideX = guideY = Integer.MIN_VALUE;
			Config.saveIfNeeded(true);
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
		if (panelOpen) {
			listScroll.wheel(v);
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent input) {
		keyReleasedSinceOpen = true;
		return super.keyReleased(input);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int key = input.key();
		if (key == GLFW.GLFW_KEY_R) {
			onClick(A_RESET, 0, 0);
			return true;
		}
		if (key == GLFW.GLFW_KEY_H) {
			onClick(A_PANEL, 0, 0);
			return true;
		}
		ClickGUI menu = TopClient.INSTANCE.getModuleManager().getModule(ClickGUI.class);
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || (menu != null && key == menu.getKey() && keyReleasedSinceOpen && System.currentTimeMillis() - openedAt > 250)) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void onClose() {
		Config.saveIfNeeded(true);
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
