package dev.topclient.client.top;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.accounts.AccountsScreen;
import dev.topclient.client.clover.mixin.ScreenChildrenAccessor;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.ClickGui;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static dev.topclient.client.top.TopTheme.*;

/**
 * The bar at the top of the title screen and the pause menu: TopClient wordmark, then
 * [cosmetics] [ MODS ] [profile]. Darkens the background a little; the game's own blur stays.
 */
public final class MenuOverlay implements Ui.Handler {
	private static final int A_COSMETICS = 1, A_MODS = 2, A_PROFILE = 3, A_HUD = 4, A_SHOTS = 5;
	/** Design-pixel height the overlay takes at the top of the screen. */
	public static final int HEIGHT = 112;

	private final Ui ui = new Ui(1F);
	private final Anim hLeft = new Anim(), hMods = new Anim(), hRight = new Anim(), appear = new Anim(), hHud = new Anim(), hShots = new Anim();
	private Screen owner;

	public static boolean enabled() {
		return Config.getBool("top.menuOverlay", true);
	}

	/** Darkens the background (call before the screen's buttons are drawn). GUI units. */
	public static void dimBackground(GuiGraphicsExtractor ctx, int width, int height) {
		if (DIM_ALPHA > 0) {
			ctx.fill(0, 0, width, height, argb(Math.round(DIM_ALPHA * 0.6F), 0x000000));
		}
	}

	/** Draws the overlay on top of a screen. */
	public void draw(Screen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		owner = screen;
		ui.begin(ctx, mouseX, mouseY);
		try {
			float a = appear.to(ui, true, 6F);
			Draw.alpha = a;
			int cx = ui.W / 2;
			// wordmark
			float px = 22;
			int ww = Widgets.wordmarkWidth(px);
			Widgets.wordmark(ui, cx - ww / 2F, 34 - (1F - a) * 10, px);
			// buttons: [cosmetics] [hud] [ MODS ] [screenshots] [profile]
			int s = 40, mw = 200, gap = 10, y = 60;
			int x0 = cx - (s * 4 + gap * 4 + mw) / 2;
			Widgets.iconButton(ui, x0, y, s, Icons.ui("cosmetics"), false, hLeft.to(ui, ui.over(x0, y, s, s), 14F), A_COSMETICS, 0);
			int hx = x0 + s + gap;
			Widgets.iconButton(ui, hx, y, s, Icons.ui("hud"), false, hHud.to(ui, ui.over(hx, y, s, s), 14F), A_HUD, 0);
			int mx = hx + s + gap;
			float hm = hMods.to(ui, ui.over(mx, y, mw, s), 14F);
			Draw.roundGradient(ctx, mx, y, mw, s, Widgets.R, argb(255, mix(ACCENT, 0xFFFFFF, hm * 0.15F)), argb(255, mix(ACCENT2, ACCENT, hm * 0.3F)));
			Draw.textCenter(ctx, "MODS", mx + mw / 2F, y + s / 2F, 14, argb(255, ON_ACCENT), true);
			ui.hit(mx, y, mw, s, A_MODS, 0);
			int sx = mx + mw + gap;
			Widgets.iconButton(ui, sx, y, s, Icons.ui("camera"), false, hShots.to(ui, ui.over(sx, y, s, s), 14F), A_SHOTS, 0);
			int rx = sx + s + gap;
			Widgets.iconButton(ui, rx, y, s, Icons.ui("profile"), false, hRight.to(ui, ui.over(rx, y, s, s), 14F), A_PROFILE, 0);
			if (ui.over(x0, y, s, s)) {
				Widgets.tooltip(ui, "Cosmetics");
			} else if (ui.over(hx, y, s, s)) {
				Widgets.tooltip(ui, "HUD editor");
			} else if (ui.over(sx, y, s, s)) {
				Widgets.tooltip(ui, "Screenshots");
			} else if (ui.over(rx, y, s, s)) {
				Widgets.tooltip(ui, "Accounts and profile");
			}
		} finally {
			ui.end();
		}
	}

	/** Mouse position comes in GUI units; true when the overlay used the click. */
	public boolean click(double guiX, double guiY, int button) {
		if (button != 0) {
			return false;
		}
		return ui.click(ui.toDesign(guiX), ui.toDesign(guiY), button, this);
	}

	@Override
	public void onClick(int action, int arg, int button) {
		Minecraft mc = Minecraft.getInstance();
		UiSounds.click();
		switch (action) {
			case A_MODS -> ModMenuScreen.open(owner);
			case A_COSMETICS -> ClickGui.openClassic("clover:capes");
			case A_PROFILE -> mc.gui.setScreen(new AccountsScreen(owner));
			case A_HUD -> mc.gui.setScreen(new dev.topclient.client.clover.screen.HudEditorScreen(owner));
			case A_SHOTS -> mc.gui.setScreen(new ScreenshotsScreen(owner));
			default -> {
			}
		}
	}

	/** GUI-unit y below which the overlay is drawn (so vanilla buttons can be moved under it). */
	public static int bottomGui() {
		Minecraft mc = Minecraft.getInstance();
		double sf = mc.getWindow().getGuiScale();
		float scale = (float) Math.max(0.6, Math.min(1.8, mc.getWindow().getHeight() / 1000.0));
		return (int) Math.ceil(HEIGHT * scale / sf);
	}

	// ---- pause menu: attach to every PauseScreen that shows its buttons ----
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu() || !enabled()) {
				return;
			}
			MenuOverlay overlay = new MenuOverlay();
			try {
				((ScreenChildrenAccessor) screen).topclient$setTitle(Component.empty());
			} catch (RuntimeException ignored) {
			}
			// move the game's buttons down so they start under the overlay
			int top = Integer.MAX_VALUE;
			for (var w : Screens.getWidgets(screen)) {
				if (w instanceof AbstractWidget aw) {
					top = Math.min(top, aw.getY());
				}
			}
			int need = bottomGui() + 6;
			if (top != Integer.MAX_VALUE && top < need) {
				int shift = need - top;
				for (var w : Screens.getWidgets(screen)) {
					if (w instanceof AbstractWidget aw) {
						aw.setY(aw.getY() + shift);
					}
				}
			}
			ScreenEvents.afterBackground(screen).register((s, ctx, mx, my, delta) -> dimBackground(ctx, s.width, s.height));
			ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> overlay.draw(s, ctx, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !overlay.click(click.x(), click.y(), click.button()));
		});
	}
}
