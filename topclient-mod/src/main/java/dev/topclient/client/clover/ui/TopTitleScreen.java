package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.accounts.Accounts;
import dev.topclient.client.clover.accounts.AccountsScreen;
import dev.topclient.client.clover.screen.HudEditorScreen;
import dev.topclient.client.gui.ClickGui;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.gui.SkinHead;
import dev.topclient.client.gui.SkinModel;
import dev.topclient.client.top.CrosshairScreen;
import dev.topclient.client.top.FabricModsScreen;
import dev.topclient.client.top.ModMenuScreen;
import dev.topclient.client.top.ScreenshotsScreen;
import dev.topclient.client.top.TopTheme;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

import static dev.topclient.client.top.TopTheme.*;

/**
 * The TopClient main menu. A dark, slowly moving background (soft accent glows, drifting
 * particles that follow the mouse a little), the TopClient wordmark, a column of big buttons
 * that slide in one after another, your own skin on the right, and a bottom bar with Integrated
 * Mods in the middle and quick buttons (Screenshots, Crosshair, HUD editor, Cosmetics, Fabric mods,
 * Accounts, Language, Background) around it. Everything uses the TopClient colour theme.
 */
public class TopTitleScreen extends Screen implements Ui.Handler {
	private static final String[] BACKGROUNDS = {"TopClient (moving)", "Minecraft panorama", "Plain dark"};
	private static final int A_SINGLE = 1, A_MULTI = 2, A_MODS = 3, A_OPTIONS = 4, A_QUIT = 5, A_ICON = 6, A_BG_PICK = 7, A_BG_CLOSE = 8,
			A_ACCOUNT = 9, A_NOTHING = 10;

	/** Set for one screen change so "Classic Minecraft menu" can open the normal screen. */
	public static boolean bypassOnce;
	private static Screen allowedVanilla;

	private final Ui ui = new Ui(1F), bgUi = new Ui(1F);
	private final long openedAt = System.nanoTime();
	private final String version;
	private final Anim[] hMain = new Anim[5], hIcons = new Anim[9];
	private final Anim hMods = new Anim(), hAccount = new Anim(), popAnim = new Anim();
	private final Anim[] hPop = new Anim[BACKGROUNDS.length + 1];
	private boolean bgMenu;
	private final SkinModel.Pose pose = new SkinModel.Pose();
	private String tip;
	private double realMx, realMy;

	// background particles: x, y (0..1), depth, phase
	private static final float[][] DOTS = new float[110][4];

	static {
		Random r = new Random(7);
		for (float[] d : DOTS) {
			d[0] = r.nextFloat();
			d[1] = r.nextFloat();
			d[2] = 0.3F + r.nextFloat() * 0.7F;
			d[3] = r.nextFloat() * 6.28F;
		}
	}

	public TopTitleScreen() {
		super(Component.literal("TopClient"));
		String mc = "26.2", mod = "";
		try {
			mc = FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(mc);
			mod = FabricLoader.getInstance().getModContainer(CloverClient.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		} catch (RuntimeException ignored) {
		}
		version = "Minecraft " + mc + (mod.isEmpty() ? "" : "  ·  TopClient " + mod);
		for (int i = 0; i < hMain.length; i++) {
			hMain[i] = new Anim();
		}
		for (int i = 0; i < hIcons.length; i++) {
			hIcons[i] = new Anim();
		}
		for (int i = 0; i < hPop.length; i++) {
			hPop[i] = new Anim();
		}
	}

	// ------------------------------------------------------------------ switching (same as before)

	public static boolean enabled() {
		try {
			return Config.getBool("ui.titleScreen", true);
		} catch (RuntimeException e) {
			return true;
		}
	}

	private static int background() {
		try {
			return Math.floorMod(Config.getInt("ui.titleBg2", 0), BACKGROUNDS.length);
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** Used by the setScreen mixin: swaps Minecraft's title screen for ours. */
	public static Screen swap(Screen screen) {
		if (!(screen instanceof TitleScreen)) {
			return screen;
		}
		if (bypassOnce) {
			bypassOnce = false;
			allowedVanilla = screen;
			return screen;
		}
		if (screen == allowedVanilla || !enabled()) {
			return screen;
		}
		return new TopTitleScreen();
	}

	/** Backup for when another mod changes setScreen first, and a way back from the classic screen. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof TitleScreen)) {
				return;
			}
			if (enabled() && screen != allowedVanilla && !bypassOnce) {
				client.execute(() -> {
					if (client.gui.screen() == screen) {
						client.gui.setScreen(new TopTitleScreen());
					}
				});
				return;
			}
			if (bypassOnce) {
				bypassOnce = false;
				allowedVanilla = screen;
			}
			try {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("TopClient menu"), b -> {
					Config.setBool("ui.titleScreen", true);
					Config.saveIfNeeded(true);
					allowedVanilla = null;
					client.gui.setScreen(new TopTitleScreen());
				}).bounds(6, 30, 120, 20).build());
			} catch (RuntimeException e) {
				CloverClient.LOGGER.warn("TopClient menu button could not be added: {}", e.toString());
			}
		});
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private float since() {
		return (System.nanoTime() - openedAt) / 1e9F;
	}

	private static float clamp01(float k) {
		return Math.max(0F, Math.min(1F, k));
	}

	private static float easeOut(float k) {
		k = clamp01(k);
		return 1F - (1F - k) * (1F - k) * (1F - k);
	}

	// ------------------------------------------------------------------ background

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		int bg = background();
		if (bg == 1) { // the game's own panorama, blurred and darkened
			extractPanorama(ctx, delta);
			if (TopTheme.BLUR > 0) {
				try {
					extractBlurredBackground(ctx);
				} catch (RuntimeException ignored) { // only one blur per frame is allowed
				}
			}
			ctx.fill(0, 0, width, height, 0x66000000);
			return;
		}
		bgUi.begin(ctx, mouseX, mouseY);
		try {
			int w = bgUi.W, h = bgUi.H;
			Draw.gradientV(ctx, 0, 0, w, h, argb(255, mix(BG, 0x000000, 0.25F)), argb(255, mix(BG, ACCENT, 0.06F)));
			if (bg == 0) {
				drawMoving(w, h);
			}
			// vignette top and bottom
			Draw.gradientV(ctx, 0, 0, w, h / 4, argb(150, 0x000000), argb(0, 0x000000));
			Draw.gradientV(ctx, 0, h * 3 / 4, w, h / 4, argb(0, 0x000000), argb(170, 0x000000));
		} finally {
			bgUi.end();
		}
	}

	private void drawMoving(int w, int h) {
		float t = since();
		GuiGraphicsExtractor ctx = bgUi.ctx;
		LoadingScreen.Img glow = LoadingScreen.brand("glow");
		float m = Math.max(w, h);
		float px = (float) (bgUi.mx / Math.max(1, w) - 0.5F), py = (float) (bgUi.my / Math.max(1, h) - 0.5F);
		// the TopClient mark, huge and very faint, behind everything
		Draw.alpha = 0.05F * DOT_FADE(t);
		float ls = h * 0.95F;
		Widgets.logo(bgUi, w * 0.72F - ls / 2F - px * 20, h * 0.48F - ls / 2F - py * 20, ls);
		Draw.alpha = 1F;
		// three soft glows in the theme colours, drifting slowly
		LoadingScreen.image(ctx, glow, w * (0.18F + 0.06F * (float) Math.sin(t * 0.11F)) - px * 30, h * (0.30F + 0.05F * (float) Math.cos(t * 0.09F)) - py * 30,
				m * 1.05F, 0F, argb(Math.round(105 * DOT_FADE(t)), ACCENT));
		LoadingScreen.image(ctx, glow, w * (0.82F + 0.05F * (float) Math.cos(t * 0.08F)) - px * 50, h * (0.72F + 0.06F * (float) Math.sin(t * 0.1F)) - py * 50,
				m * 0.95F, 0F, argb(Math.round(95 * DOT_FADE(t)), ACCENT2));
		LoadingScreen.image(ctx, glow, w * (0.55F + 0.1F * (float) Math.sin(t * 0.05F)), h * (0.05F + 0.04F * (float) Math.sin(t * 0.13F)),
				m * 0.7F, 0F, argb(Math.round(45 * DOT_FADE(t)), 0xFFFFFF));
		// drifting particles; nearer ones move faster and follow the mouse more
		for (float[] d : DOTS) {
			float y = (d[1] - t * 0.012F * d[2]) % 1F;
			if (y < 0) {
				y += 1F;
			}
			float x = d[0] + (float) Math.sin(t * 0.3F + d[3]) * 0.01F;
			float sx = x * w - px * 40 * d[2], sy = y * h - py * 40 * d[2];
			float tw = 0.35F + 0.65F * Math.abs((float) Math.sin(t * 0.8F + d[3]));
			float r = 1F + d[2] * 1.6F;
			Draw.circle(ctx, sx, sy, r, argb(Math.round(150 * tw * d[2] * DOT_FADE(t)), d[3] > 3.1F ? ACCENT : 0xFFFFFF));
		}
	}

	/** Fades the background in over the first second. */
	private static float DOT_FADE(float t) {
		return clamp01(t / 1.0F);
	}

	// ------------------------------------------------------------------ main

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		tip = null;
		ui.begin(ctx, mouseX, mouseY);
		try {
			float t = since();
			int W = ui.W, H = ui.H;
			realMx = ui.mx;
			realMy = ui.my;
			if (bgMenu) {
				ui.mx = ui.my = -9999; // the pop-up is on top: nothing under it reacts
			}
			int left = Math.max(48, Math.round(W * 0.07F));

			// ---- wordmark
			float a0 = easeOut(t / 0.7F);
			Draw.alpha = a0;
			float px = Math.min(46, H * 0.05F);
			int wy = Math.round(H * 0.20F);
			Widgets.wordmark(ui, left - (1F - a0) * 40, wy, px);
			Draw.text(ctx, "Your Minecraft, your way.", left - (1F - a0) * 40 + 2, wy + px * 0.85F, Math.max(12, px * 0.34F), argb(255, TEXT_MUTE));

			// ---- main buttons
			Minecraft mc = Minecraft.getInstance();
			String[] labels = {"Singleplayer", mc.allowsMultiplayer() ? "Multiplayer" : "Multiplayer (not allowed)", "Integrated Mods", "Options", "Quit Game"};
			String[] icons = {"play", "server", "mods", "options", "close"};
			int bw = Math.min(380, Math.max(280, Math.round(W * 0.24F))), bh = Math.max(46, Math.min(56, Math.round(H * 0.055F))), gap = 12;
			int by = Math.round(wy + px * 2.1F);
			by = Math.min(by, H - 5 * (bh + gap) - 120);
			for (int i = 0; i < labels.length; i++) {
				float k = easeOut((t - 0.25F - i * 0.07F) / 0.5F);
				if (k <= 0F) {
					continue;
				}
				Draw.alpha = k;
				int x = Math.round(left - (1F - k) * 60);
				int y = by + i * (bh + gap) + (i == 4 ? 10 : 0);
				mainButton(x, y, bw, bh, labels[i], icons[i], i, i == 0);
			}

			// ---- skin on the right
			Draw.alpha = 1F;
			drawSkin(ctx, t, W, H);

			// ---- bottom bar: icons around a big Integrated Mods button
			float k = easeOut((t - 0.6F) / 0.5F);
			Draw.alpha = k;
			drawBottomBar(W, H, k);

			// ---- corners
			Draw.alpha = clamp01((t - 0.8F) / 0.5F);
			Draw.text(ctx, version, 16, H - 26, 11, argb(170, TEXT_MUTE));
			String copy = "Not affiliated with Mojang or Microsoft";
			Draw.textRight(ctx, copy, W - 16, H - 20, 11, argb(150, TEXT_MUTE));

			Draw.alpha = 1F;
			if (bgMenu) {
				drawBgMenu(W, H);
			}
			if (tip != null && !bgMenu) {
				Widgets.tooltip(ui, tip);
			}
		} finally {
			ui.end();
		}
	}

	private void mainButton(int x, int y, int w, int h, String label, String icon, int i, boolean primary) {
		boolean over = ui.over(x, y, w, h);
		float hv = hMain[i].to(ui, over, 12F);
		GuiGraphicsExtractor ctx = ui.ctx;
		// glass panel that brightens on hover, with a growing accent bar on the left
		Draw.shadow(ctx, x, y, w, h, 12, 12, Math.round(50 + 50 * hv));
		int fill = primary ? mix(mix(PANEL2, ACCENT, 0.28F), ACCENT, hv * 0.25F) : mix(PANEL2, CARD_HOVER, hv);
		Draw.round(ctx, x, y, w, h, 12, argb(Math.round(200 + 40 * hv), fill));
		Draw.outline(ctx, x, y, w, h, 12, 1, argb(255, primary ? mix(ACCENT, 0xFFFFFF, hv * 0.3F) : mix(BORDER, ACCENT, hv)));
		int barH = Math.round((h - 20) * (0.25F + 0.75F * hv));
		Draw.round(ctx, x + 8, y + (h - barH) / 2, 3, Math.max(4, barH), 1, argb(Math.round(120 + 135 * hv), primary ? 0xFFFFFF : ACCENT));
		float shift = hv * 6F;
		Icons.drawCentered(ctx, Icons.ui(icon), x + 34 + shift, y + h / 2F, 20, argb(255, primary ? 0xFFFFFF : mix(TEXT_DIM, ACCENT, hv)));
		Draw.textMid(ctx, label, x + 58 + shift, y + h / 2F, 15, argb(255, primary ? 0xFFFFFF : mix(TEXT_DIM, TEXT, hv)), true);
		if (hv > 0.02F) {
			Icons.drawCentered(ctx, Icons.ui("chevron"), x + w - 24 - (1F - hv) * 8, y + h / 2F, 16, argb(Math.round(255 * hv), primary ? 0xFFFFFF : ACCENT));
		}
		ui.hit(x, y, w, h, A_SINGLE + i, 0);
	}

	private static final String[] BAR_ICONS = {"camera", "crosshair", "hud", "cosmetics", "puzzle", "accounts", "language", "image"};
	private static final String[] BAR_TIPS = {"Screenshots", "Crosshair editor", "HUD editor", "Cosmetics", "Fabric mods", "Accounts", "Language", "Background"};

	private void drawBottomBar(int W, int H, float k) {
		int s = 46, gap = 10, mw = 230;
		int half = BAR_ICONS.length / 2;
		int total = BAR_ICONS.length * (s + gap) + mw + gap;
		int x = (W - total) / 2, y = H - s - 54 + Math.round((1F - k) * 30);
		// soft dock behind the bar
		int dx = x - 14, dw = total + 18;
		Draw.shadow(ui.ctx, dx, y - 12, dw, s + 24, 20, 18, 90);
		Draw.round(ui.ctx, dx, y - 12, dw, s + 24, 20, argb(200, mix(BG, 0x000000, 0.3F)));
		Draw.outline(ui.ctx, dx, y - 12, dw, s + 24, 20, 1, argb(255, BORDER));
		for (int i = 0; i < BAR_ICONS.length; i++) {
			if (i == half) { // the big Integrated Mods button in the middle
				float hv = hMods.to(ui, ui.over(x, y, mw, s), 12F);
				Draw.roundGradient(ui.ctx, x, y - Math.round(hv * 2), mw, s, 12, argb(255, mix(ACCENT, 0xFFFFFF, hv * 0.15F)), argb(255, mix(ACCENT2, ACCENT, hv * 0.3F)));
				Icons.drawCentered(ui.ctx, Icons.ui("mods"), x + 28, y + s / 2F - hv * 2, 18, argb(255, ON_ACCENT));
				Draw.textMid(ui.ctx, "INTEGRATED MODS", x + 48, y + s / 2F - hv * 2, 13, argb(255, ON_ACCENT), true);
				ui.hit(x, y, mw, s, A_MODS, 0);
				x += mw + gap;
			}
			float hv = hIcons[i].to(ui, ui.over(x, y, s, s), 14F);
			int lift = Math.round(hv * 4);
			Widgets.iconButton(ui, x, y - lift, s, Icons.ui(BAR_ICONS[i]), i == 7 && bgMenu, hv, A_ICON, i);
			if (ui.over(x, y - lift, s, s)) {
				tip = BAR_TIPS[i];
			}
			x += s + gap;
		}
	}

	private void drawBgMenu(int W, int H) {
		float p = popAnim.to(ui, bgMenu, 12F);
		Draw.alpha = p;
		Draw.rect(ui.ctx, 0, 0, W, H, argb(Math.round(120 * p), 0x000000));
		ui.hit(0, 0, W, H, A_BG_CLOSE, 0);
		String[] rows = new String[BACKGROUNDS.length + 1];
		System.arraycopy(BACKGROUNDS, 0, rows, 0, BACKGROUNDS.length);
		rows[BACKGROUNDS.length] = "Classic Minecraft menu";
		int pw = 320, rh = 40, ph = 52 + rows.length * (rh + 6) + 10;
		int x = (W - pw) / 2, y = H - 140 - ph + Math.round((1F - p) * 20);
		Draw.shadow(ui.ctx, x, y, pw, ph, 14, 20, 140);
		Draw.round(ui.ctx, x, y, pw, ph, 14, argb(245, BG));
		Draw.outline(ui.ctx, x, y, pw, ph, 14, 1, argb(255, BORDER));
		ui.hit(x, y, pw, ph, A_NOTHING, 0);
		Draw.text(ui.ctx, "BACKGROUND", x + 18, y + 18, 11, argb(255, TEXT_MUTE), true);
		int cur = background();
		double mx = realMx, my = realMy;
		for (int i = 0; i < rows.length; i++) {
			int ry = y + 44 + i * (rh + 6);
			boolean sel = i == cur;
			float hv = hPop[i].to(ui, mx >= x + 10 && my >= ry && mx < x + pw - 10 && my < ry + rh, 14F);
			Draw.round(ui.ctx, x + 10, ry, pw - 20, rh, 10, argb(230, sel ? mix(PANEL2, ACCENT, 0.25F) : mix(PANEL, CARD_HOVER, hv)));
			if (sel) {
				Draw.outline(ui.ctx, x + 10, ry, pw - 20, rh, 10, 1, argb(255, ACCENT));
			}
			Draw.textMid(ui.ctx, rows[i], x + 26, ry + rh / 2F, 13, argb(255, i == rows.length - 1 ? TEXT_MUTE : TEXT), true);
			ui.hit(x + 10, ry, pw - 20, rh, A_BG_PICK, i);
		}
		Draw.alpha = 1F;
	}

	/** Your skin stands on the right, rises in once, floats gently and looks at the mouse. */
	private void drawSkin(GuiGraphicsExtractor ctx, float t, int W, int H) {
		PlayerSkin skin;
		try {
			skin = SkinHead.get();
		} catch (RuntimeException e) {
			return;
		}
		if (skin == null || skin.body() == null || skin.body().texturePath() == null || W < 900) {
			return;
		}
		Identifier tex = skin.body().texturePath();
		boolean slim = skin.model() == PlayerModelType.SLIM;
		float k = t - 0.35F;
		if (k < 0) {
			return;
		}
		float appear = easeOut(k / 0.9F);
		float scale = H * 0.42F / 32F;
		float x = W * 0.74F;
		float feetY = H * 0.72F;
		zero(pose);
		float bob = (float) Math.sin(k * 1.4F);
		float floatY = -(5F + 5F * bob) * appear;
		float bodyY = feetY + floatY + (1F - appear) * 40F;
		pose.yaw = (float) Math.toRadians(-20) + 0.05F * (float) Math.sin(k * 0.5F);
		pose.rightArm[2] = -0.10F - 0.04F * bob;
		pose.leftArm[2] = 0.10F + 0.04F * bob;
		pose.rightLeg[0] = 0.05F * bob;
		pose.leftLeg[0] = -0.05F * bob;
		float headY = bodyY - 28F * scale;
		pose.headYaw = Math.max(-0.7F, Math.min(0.7F, (float) (ui.mx - x) / (W * 0.5F)));
		pose.headPitch = Math.max(-0.4F, Math.min(0.4F, (float) (ui.my - headY) / (H * 0.8F)));
		// glow + shadow
		LoadingScreen.image(ctx, LoadingScreen.brand("glow"), x, feetY - 14F * scale, 34F * scale, 0F, argb(Math.round(70 * appear), ACCENT));
		float sw = 9F * scale * appear;
		Draw.round(ctx, Math.round(x - sw), Math.round(feetY + 4), Math.round(sw * 2), 8, 4, argb(Math.round(70 * appear), 0x000000));
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(x, bodyY);
		m.scale(scale, scale);
		SkinModel.draw(ctx, tex, slim, pose, argb(Math.round(255 * appear), 0xFFFFFF));
		m.popMatrix();
		// name plate + switch account
		String name = Accounts.currentName();
		Draw.alpha = appear;
		int nw = Math.max(Draw.width(name, 15, true), Draw.width("Switch account", 11.5F)) + 44;
		int nx = Math.round(x - nw / 2F), ny = Math.round(feetY + 22);
		float hv = hAccount.to(ui, ui.over(nx, ny, nw, 54), 14F);
		Draw.round(ctx, nx, ny, nw, 54, 12, argb(Math.round(190 + 40 * hv), mix(PANEL2, CARD_HOVER, hv)));
		Draw.outline(ctx, nx, ny, nw, 54, 12, 1, argb(255, mix(BORDER, ACCENT, hv)));
		Draw.textCenter(ctx, name, nx + nw / 2F, ny + 19, 15, argb(255, TEXT), true);
		Draw.textCenter(ctx, "Switch account", nx + nw / 2F, ny + 38, 11.5F, argb(255, mix(TEXT_MUTE, ACCENT, hv)));
		ui.hit(nx, ny, nw, 54, A_ACCOUNT, 0);
		Draw.alpha = 1F;
	}

	private static void zero(SkinModel.Pose p) {
		p.lift = 0;
		p.yaw = 0;
		p.headYaw = p.headPitch = p.headRoll = 0;
		for (int i = 0; i < 3; i++) {
			p.rightArm[i] = p.leftArm[i] = p.rightLeg[i] = p.leftLeg[i] = 0;
		}
	}

	// ------------------------------------------------------------------ actions

	@Override
	public void onClick(int action, int arg, int button) {
		Minecraft mc = Minecraft.getInstance();
		if (action != A_NOTHING) {
			UiSounds.click();
		}
		switch (action) {
			case A_SINGLE -> mc.gui.setScreen(new SelectWorldScreen(this));
			case A_MULTI -> {
				if (mc.allowsMultiplayer()) {
					mc.gui.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
				}
			}
			case A_MODS -> ModMenuScreen.open(this);
			case A_OPTIONS -> mc.gui.setScreen(new OptionsScreen(this, mc.options, false));
			case A_QUIT -> mc.stop();
			case A_ACCOUNT -> mc.gui.setScreen(new AccountsScreen(this));
			case A_ICON -> {
				switch (arg) {
					case 0 -> mc.gui.setScreen(new ScreenshotsScreen(this));
					case 1 -> mc.gui.setScreen(new CrosshairScreen(this));
					case 2 -> mc.gui.setScreen(new HudEditorScreen(this));
					case 3 -> ClickGui.openClassic("clover:capes");
					case 4 -> mc.gui.setScreen(new FabricModsScreen(this));
					case 5 -> mc.gui.setScreen(new AccountsScreen(this));
					case 6 -> mc.gui.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()));
					case 7 -> {
						bgMenu = !bgMenu;
						popAnim.v = 0F;
					}
					default -> {
					}
				}
			}
			case A_BG_CLOSE -> bgMenu = false;
			case A_BG_PICK -> {
				if (arg < BACKGROUNDS.length) {
					Config.setInt("ui.titleBg2", arg);
					Config.saveIfNeeded(true);
					bgMenu = false;
				} else { // classic Minecraft menu
					Config.saveIfNeeded(true);
					bgMenu = false;
					bypassOnce = true;
					mc.gui.setScreen(new TitleScreen());
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (ui.click(ui.toDesign(click.x()), ui.toDesign(click.y()), click.button(), this)) {
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (bgMenu && input.key() == GLFW.GLFW_KEY_ESCAPE) {
			bgMenu = false;
			return true;
		}
		return super.keyPressed(input);
	}
}
