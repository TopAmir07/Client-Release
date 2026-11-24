package dev.topclient.client.gui;

import dev.topclient.client.TopClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.cosmetic.Cosmetics;
import dev.topclient.client.clover.screen.HudEditorScreen;
import dev.topclient.client.clover.ui.LoadingScreen;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.model.GuiModule;
import dev.topclient.client.gui.model.GuiSetting;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.utils.RenderUtils;
import dev.topclient.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import static dev.topclient.client.gui.GuiTheme.*;

public final class ClickGui extends Screen {
	private static final int SIDEBAR_W = 330;
	private static final int PAD = 26;
	private static final int GAP = 14;
	private static final int CARD_H = 124;
	private static final int CARD_H_COMPACT = 76;
	private static final int FOOTER_H = 36;
	private static final int INSPECTOR_W = 404;
	private static final int[] PALETTE = {0xFFFFFF, 0xF87171, 0xFB923C, 0xFBBF24, 0xA3E635, 0x34D399,
			0x22D3EE, 0x60A5FA, 0xA78BFA, 0xF472B6, 0x9CA3AF, 0x111111};

	private List<Page> pages;
	private Page page;
	private String search = "";
	private int filter;

	private GuiModule inspected;
	private GuiModule inspectedShown;
	private GuiSetting listening;
	private GuiSetting colorOpen;
	private GuiSetting modeOpen;

	private enum Focus { NONE, SEARCH, FIELD }

	private Focus focus = Focus.NONE;
	private String fieldId = "";
	private String fieldText = "";
	private Consumer<String> fieldCommit;

	private DoubleConsumer drag;

	private float scroll, scrollTarget, maxScroll;
	private float sideScroll, sideScrollTarget, sideMaxScroll;
	private float inspScroll, inspScrollTarget, inspMaxScroll;

	private long openedAt = -1;
	private final MenuWalker walker = new MenuWalker();
	private final FallingParticles falling = new FallingParticles();
	private int openKey = -1;
	private String hoverId = "";
	private long lastFrame;
	private float dt;
	private final Map<String, Float> anims = new HashMap<>();

	private float ui = 1F;
	private float lastUi = -1F;
	private double mx, my;
	private int wx, wy, ww, wh;
	private int contentX, contentW, gridTop, gridBottom;
	private int[] sideRect = new int[4], gridRect = new int[4], inspRect = new int[4];
	private final List<Hit> hits = new ArrayList<>();
	private String tooltip;

	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right, int[] clip, String tag) {
		boolean contains(double px, double py) {
			if (px < x || py < y || px >= x + w || py >= y + h) {
				return false;
			}
			return clip == null || (px >= clip[0] && py >= clip[1] && px < clip[2] && py < clip[3]);
		}
	}

	public ClickGui() {
		super(Component.empty());
	}

	@Override
	protected void init() {
		if (pages == null) {
			pages = Page.build();
			page = pages.get(0);
		}
		if (openedAt < 0) {
			openedAt = System.currentTimeMillis();
			walker.reset();
			try {
				ClickGUI menu = TopClient.INSTANCE.getModuleManager().getModule(ClickGUI.class);
				openKey = menu != null ? menu.getKey() : -1;
			} catch (RuntimeException e) {
				openKey = -1;
			}
			UiSounds.open();
		}
	}

	@Override
	protected void setInitialFocus() {
		if (minecraft == null) {
			return;
		}
		super.setInitialFocus();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ClickGUI module = TopClient.INSTANCE.getModuleManager().getModule(ClickGUI.class);
		if (module != null && module.isEnabled()) {
			module.setEnabled(false);
		} else {
			onGuiClose();
		}
	}

	public void onGuiClose() {
		if (openedAt >= 0) {
			UiSounds.close();
		}
		commitField();
		Cosmetics.tryOn(Cosmetics.Slot.CAPE, null);
		listening = null;
		drag = null;
		openedAt = -1;
		Config.saveIfNeeded(true);
		TopClient.INSTANCE.getProfileManager().saveProfile();
		Minecraft.getInstance().setScreenAndShow(TopClient.INSTANCE.previousScreen);
	}

	private boolean animationsOff() {
		return ClickGUI.animationMode.isMode(ClickGUI.AnimationMode.Off);
	}

	private float anim(String key, float target, float speed) {
		if (animationsOff()) {
			anims.put(key, target);
			return target;
		}
		float v = anims.getOrDefault(key, target);
		v += (target - v) * (1F - (float) Math.exp(-speed * dt));
		if (Math.abs(target - v) < 0.002F) {
			v = target;
		}
		anims.put(key, v);
		return v;
	}

	private static float ease(float t) {
		t = Math.max(0F, Math.min(1F, t));
		return 1F - (float) Math.pow(1F - t, 3);
	}

	private boolean over(int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private boolean over(int x, int y, int w, int h, int[] clip) {
		return over(x, y, w, h) && mx >= clip[0] && my >= clip[1] && mx < clip[2] && my < clip[3];
	}

	private void hit(int x, int y, int w, int h, Runnable left) {
		hits.add(new Hit(x, y, w, h, left, null, null, null));
	}

	private void hit(int x, int y, int w, int h, Runnable left, Runnable right, int[] clip) {
		hits.add(new Hit(x, y, w, h, left, right, clip, null));
	}

	private void hitTagged(String tag, int x, int y, int w, int h, Runnable left, Runnable right, int[] clip) {
		hits.add(new Hit(x, y, w, h, left, right, clip, tag));
	}

	private double toDesignX(double guiX) {
		return guiX * Minecraft.getInstance().getWindow().getGuiScale() / ui;
	}

	private double toDesignY(double guiY) {
		return guiY * Minecraft.getInstance().getWindow().getGuiScale() / ui;
	}

	private void focusField(String id, String text, Consumer<String> commit) {
		commitField();
		focus = Focus.FIELD;
		fieldId = id;
		fieldText = text == null ? "" : text;
		fieldCommit = commit;
	}

	private void commitField() {
		if (focus == Focus.FIELD && fieldCommit != null) {
			fieldCommit.accept(fieldText.strip());
		}
		if (focus == Focus.FIELD) {
			focus = Focus.NONE;
		}
		fieldCommit = null;
		fieldId = "";
	}

	private void openInspector(GuiModule m) {
		commitField();
		inspected = m;
		inspectedShown = m;
		inspScroll = inspScrollTarget = 0;
		colorOpen = null;
		modeOpen = null;
		listening = null;
		if (m.id().equals("group:theme")) {
			for (GuiSetting s : m.settings()) {
				if (s.kind == GuiSetting.Kind.MODE) {
					modeOpen = s;
					break;
				}
			}
		}
		UiSounds.click();
	}

	private void closeInspector() {
		commitField();
		inspected = null;
		listening = null;
		colorOpen = null;
		modeOpen = null;
	}

	private void selectPage(Page p) {
		if (p == page && search.isEmpty()) {
			return;
		}
		commitField();
		page = p;
		search = "";
		if (focus == Focus.SEARCH) {
			focus = Focus.NONE;
		}
		scroll = scrollTarget = 0;
		filter = 0;
		closeInspector();
		Cosmetics.tryOn(Cosmetics.Slot.CAPE, null);
		UiSounds.click();
	}

	private List<GuiModule> visibleModules() {
		List<GuiModule> source = new ArrayList<>();
		if (!search.isEmpty()) {
			String q = search.toLowerCase(Locale.ROOT);
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (Page p : pages) {
				if (p.virtual) {
					continue; // "All", "Favorites" and "Enabled" repeat modules from the real pages
				}
				for (GuiModule m : p.modules()) {
					if ((m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q)) && seen.add(m.id())) {
						source.add(m);
					}
				}
			}
		} else {
			source.addAll(page.modules());
		}
		if (filter == 0) {
			return source;
		}
		List<GuiModule> out = new ArrayList<>();
		for (GuiModule m : source) {
			boolean on = m.toggleable() && m.enabled();
			if ((filter == 1) == on) {
				out.add(m);
			}
		}
		return out;
	}

	private Page pageOf(GuiModule m) {
		for (Page p : pages) {
			if (p.modules().contains(m)) {
				return p;
			}
		}
		return null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != this) {
			return;
		}
		if (pages == null) {
			init();
		}

		GuiTheme.refresh();
		long now = System.nanoTime();
		dt = lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
		lastFrame = now;

		if (TopClient.INSTANCE.previousScreen != null && TopClient.INSTANCE.previousScreen != this) {
			TopClient.INSTANCE.previousScreen.extractRenderState(ctx, 0, 0, delta);
		}

		ClickGUI.OpenAnimation openMode = ClickGUI.openAnimation.getMode();
		float openT = openMode == ClickGUI.OpenAnimation.None ? 1F : ease((System.currentTimeMillis() - openedAt) / 260F);
		Draw.alpha = openT;

		double sf = mc.getWindow().getGuiScale();
		int fbW = mc.getWindow().getWidth();
		int fbH = mc.getWindow().getHeight();
		float wantUi = (float) (Math.max(0.6, Math.min(1.8, fbH / 1000.0)) * ClickGUI.guiScale.getValue());
		if (drag == null || lastUi <= 0) {
			lastUi = wantUi;
		}
		ui = lastUi;
		Draw.ui = ui;
		mx = mouseX * sf / ui;
		my = mouseY * sf / ui;
		int W = Math.round(fbW / ui);
		int H = Math.round(fbH / ui);

		hits.clear();
		tooltip = null;

		RenderUtils.unscaledProjection(ctx);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.scale(ui, ui);
		Backdrop.draw(ctx, W, H);
		boolean fallInFront = ClickGUI.fallLayer.isMode(ClickGUI.FallLayer.InFront);
		if (!fallInFront) {
			drawFalling(ctx, W, H);
		}
		if (openMode == ClickGUI.OpenAnimation.Slide) {
			m.translate(0F, (1F - openT) * 16F);
		} else if (openMode == ClickGUI.OpenAnimation.Zoom) {
			float z = 0.94F + 0.06F * openT;
			m.translate(W / 2F, H / 2F);
			m.scale(z, z);
			m.translate(-W / 2F, -H / 2F);
		}
		String hoverBefore = hoverId;
		hoverId = "";

		ww = Math.min(W - 24, Math.max(880, Math.min(1200, W - 120)));
		wh = Math.min(H - 24, Math.max(540, Math.min(740, H - 110)));
		wx = (W - ww) / 2;
		wy = (H - wh) / 2;

		try {
			drawWalker(ctx, W);
			drawWindow(ctx);
			drawSidebar(ctx);
			if (page.kind == Page.Kind.CAPES && search.isEmpty()) {
				drawHeader(ctx);
				drawCapes(ctx);
			} else {
				drawHeader(ctx);
				drawGrid(ctx);
			}
			drawFooter(ctx);
			drawInspector(ctx);
			if (fallInFront) {
				drawFalling(ctx, W, H);
			}
			drawTooltip(ctx);
			if (!hoverId.isEmpty() && !hoverId.equals(hoverBefore)) {
				UiSounds.hover();
			}
		} finally {
			m.popMatrix();
			RenderUtils.scaledProjection(ctx);
			Draw.alpha = 1F;
		}
	}

	private void drawFalling(GuiGraphicsExtractor ctx, int W, int H) {
		try {
			falling.render(ctx, W, H, dt);
		} catch (RuntimeException e) {
		}
	}

	private void drawWalker(GuiGraphicsExtractor ctx, int W) {
		float since = (System.currentTimeMillis() - openedAt) / 1000F;
		try {
			walker.render(ctx, W, wx, wy, ww, wh, since);
		} catch (RuntimeException e) {
			return;
		}
		int[] b = walker.box();
		if (b[2] > 0) {
			hit(b[0], b[1], b[2], b[3], () -> {
				walker.poke((System.currentTimeMillis() - openedAt) / 1000F);
				UiSounds.click();
			});
		}
	}

	// ---- Look (v3.14): a framed window with a top bar, a boxed sidebar and outlined module cards ----
	private static final int HEAD_H = 96;
	private static final int LINE = 0xD6D6DE;
	private static final java.time.format.DateTimeFormatter CLOCK_FMT = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd   h:mm a", Locale.ENGLISH);

	private void drawWindow(GuiGraphicsExtractor ctx) {
		int r = 16;
		Draw.shadow(ctx, wx, wy, ww, wh, r, 30, 140);
		Backdrop.windowPicture(ctx, wx, wy, ww, wh, r);
		Draw.round(ctx, wx, wy, ww, wh, r, Draw.argb(Math.min(232, Backdrop.windowAlpha()), Draw.mix(0x121218, WINDOW, 0.35F)));
		Draw.outline(ctx, wx, wy, ww, wh, r, 2, Draw.argb(150, LINE));
		Draw.rect(ctx, wx + 2, wy + HEAD_H, ww - 4, 2, Draw.argb(110, LINE));
		drawTopBar(ctx);
	}

	/** Logo and name, the date and time, and the quick buttons (HUD editor, Customize, Capes, About, Close). */
	private void drawTopBar(GuiGraphicsExtractor ctx) {
		int cy = wy + HEAD_H / 2;
		LoadingScreen.image(ctx, LoadingScreen.brand("elephant"), wx + 54, cy, 52, 0F, Draw.argb(255, 0xFFFFFF));
		Draw.text(ctx, "TOPCLIENT", wx + 94, cy - 23, 21, Draw.argb(255, 0xFFFFFF), true);
		Draw.text(ctx, "by TopAmir_", wx + 94, cy + 3, 17, Draw.argb(255, 0xE4E4EA));

		int bs = 54, gap = 12;
		int bx = wx + ww - 28 - bs;
		int by = cy - bs / 2;
		topButton(ctx, "close", bx, by, bs, null, "close", false, "Close", this::onClose);
		bx -= bs + gap;
		topButton(ctx, "about", bx, by, bs, ModuleItems.of("client"), null, isPage("core:CLIENT"), "TopClient", () -> openPage("core:CLIENT"));
		bx -= bs + gap;
		topButton(ctx, "capes", bx, by, bs, ModuleItems.of("cape"), null, isPage("clover:capes"), "Capes", () -> openPage("clover:capes"));
		bx -= bs + gap;
		topButton(ctx, "customize", bx, by, bs, ModuleItems.of("customize"), null, isPage("customize"), "Customize", () -> openPage("customize"));
		bx -= bs + gap;
		topButton(ctx, "hudedit", bx, by, bs, ModuleItems.of("hud"), null, false, "HUD Editor", () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this)));
		int left = wx + 94 + Math.max(Draw.width("TOPCLIENT", 21, true), Draw.width("by TopAmir_", 17)) + 24;
		int right = bx - 24;
		int dw = Math.min(320, right - left);
		if (dw > 150) {
			int dx = Math.max(left, Math.min(right - dw, wx + ww / 2 - dw / 2 - 40));
			Draw.round(ctx, dx, cy - 25, dw, 50, 10, Draw.argb(70, 0x000000));
			Draw.outline(ctx, dx, cy - 25, dw, 50, 10, 2, Draw.argb(150, LINE));
			Draw.textCenter(ctx, java.time.LocalDateTime.now().format(CLOCK_FMT), dx + dw / 2F, cy, 19, Draw.argb(255, 0xF2F2F6));
		}
	}

	private boolean isPage(String id) {
		return page != null && page.id.equals(id) && search.isEmpty();
	}

	/** Opens the classic menu straight on one page (e.g. "clover:capes"). */
	public static void openClassic(String pageId) {
		Minecraft mc = Minecraft.getInstance();
		TopClient.INSTANCE.previousScreen = mc.gui.screen();
		ClickGui g = TopClient.INSTANCE.clickGui;
		mc.setScreenAndShow(g);
		if (pageId != null) {
			if (g.pages == null) {
				g.pages = Page.build();
				g.page = g.pages.get(0);
			}
			g.openPage(pageId);
		}
	}

	private void openPage(String id) {
		for (Page p : pages) {
			if (p.id.equals(id)) {
				selectPage(p);
				return;
			}
		}
	}

	private void topButton(GuiGraphicsExtractor ctx, String id, int x, int y, int s, net.minecraft.world.item.ItemStack item, String icon, boolean active, String tip, Runnable action) {
		boolean hov = over(x, y, s, s) && inspected == null;
		float hv = anim("top." + id, hov ? 1F : 0F, 18F);
		Draw.round(ctx, x, y, s, s, 10, Draw.argb(Math.round(60 + 40 * hv), 0x000000));
		Draw.outline(ctx, x, y, s, s, 10, 2, Draw.argb(active ? 255 : Math.round(130 + 110 * hv), active ? 0xFFFFFF : LINE));
		if (item != null) {
			drawItem(ctx, item, x + s / 2F, y + s / 2F, s * 0.62F);
		} else if (icon != null) {
			Icons.drawCentered(ctx, icon, x + s / 2F, y + s / 2F, s * 0.55F, Draw.argb(255, 0xFFFFFF));
		}
		if (hov) {
			hoverId = "top:" + id;
			tooltip = tip;
		}
		hit(x, y, s, s, () -> {
			UiSounds.click();
			action.run();
		});
	}

	/** A Minecraft item as an icon, centred at (cx, cy), size in design pixels. */
	private static void drawItem(GuiGraphicsExtractor ctx, net.minecraft.world.item.ItemStack stack, float cx, float cy, float size) {
		try {
			org.joml.Matrix3x2fStack m = ctx.pose();
			m.pushMatrix();
			m.translate(cx - size / 2F, cy - size / 2F);
			m.scale(size / 16F, size / 16F);
			ctx.item(stack, 0, 0);
			m.popMatrix();
		} catch (RuntimeException ignored) {
		}
	}

	private void drawSidebar(GuiGraphicsExtractor ctx) {
		int sx = wx + 22, sy = wy + HEAD_H + 22, sw = SIDEBAR_W - 34, sh = wh - HEAD_H - 44;
		Draw.round(ctx, sx, sy, sw, sh, 12, Draw.argb(70, 0x000000));
		Draw.outline(ctx, sx, sy, sw, sh, 12, 2, Draw.argb(130, LINE));
		int top = sy + 10;
		int bottom = sy + sh - 10;
		sideRect = new int[]{sx, top, sx + sw, bottom};
		sideScroll += (sideScrollTarget - sideScroll) * Math.min(1F, dt * 14F);

		ctx.enableScissor(sx, top, sx + sw, bottom);
		int iy = top - Math.round(sideScroll);
		boolean divided = false;
		for (Page p : pages) {
			if (!p.virtual && !divided) {
				divided = true;
				iy += 6;
				Draw.rect(ctx, sx + 24, iy, sw - 48, 2, Draw.argb(110, LINE));
				iy += 12;
			}
			drawSideItem(ctx, p, sx + 10, iy, sw - 20);
			iy += 54;
		}
		ctx.disableScissor();
		int contentH = iy + Math.round(sideScroll) - top + 6;
		sideMaxScroll = Math.max(0, contentH - (bottom - top));
		sideScrollTarget = Math.max(0, Math.min(sideMaxScroll, sideScrollTarget));
	}

	private static String where(Minecraft mc) {
		if (mc.getCurrentServer() != null) {
			return mc.getCurrentServer().ip;
		}
		if (mc.isLocalServer()) {
			return "Singleplayer";
		}
		return mc.level != null ? "In game" : "Main menu";
	}

	private void drawSideItem(GuiGraphicsExtractor ctx, Page p, int x, int y, int w) {
		int h = 48;
		boolean active = p == page && search.isEmpty();
		boolean hov = over(x, y, w, h, sideRect);
		float a = anim("side.a." + p.id, active ? 1F : 0F, 14F);
		float hv = anim("side.h." + p.id, hov ? 1F : 0F, 18F);
		if (hv > 0.01F && a < 0.99F) {
			Draw.round(ctx, x, y, w, h, 10, Draw.argb(Math.round(26 * hv), 0xFFFFFF));
		}
		if (a > 0.01F) {
			Draw.round(ctx, x, y, w, h, 10, Draw.argb(Math.round(245 * a), 0xF4F4F7));
		}
		drawItem(ctx, ModuleItems.of(p.title), x + 26, y + h / 2F, 26);
		int tc = Draw.mix(0xF2F2F6, 0x141418, a);
		Draw.textMid(ctx, Draw.fit(p.title, 17, w - 110), x + 54, y + h / 2F, 17, Draw.argb(255, tc), a > 0.5F);
		if (p.kind == Page.Kind.MODULES) {
			int n = p.modules().size();
			Draw.textRight(ctx, Integer.toString(n), x + w - 16, y + h / 2F, 17, Draw.argb(255, Draw.mix(0xC8C8D0, 0x3A3A44, a)));
		}
		if (hov) {
			hoverId = "side:" + p.id;
		}
		hit(x, y, w, h, () -> selectPage(p), null, sideRect);
	}

	private void drawHeader(GuiGraphicsExtractor ctx) {
		contentX = wx + SIDEBAR_W + 8;
		contentW = ww - SIDEBAR_W - 8 - 30;
		int top = wy + HEAD_H + 26;
		int right = contentX + contentW;
		boolean searching = !search.isEmpty();
		String title = searching ? "Search" : page.title;
		List<GuiModule> all = searching ? visibleModulesUnfiltered() : page.modules();
		Draw.text(ctx, title, contentX, top + 6, 21, Draw.argb(255, 0xFFFFFF), true);
		if (page.kind == Page.Kind.MODULES || searching) {
			String count = searching ? all.size() + (all.size() == 1 ? " result" : " results") : all.size() + (all.size() == 1 ? " module" : " modules");
			Draw.text(ctx, count, contentX + Draw.width(title, 21, true) + 18, top + 8, 18, Draw.argb(255, 0xD0D0D8));
		}
		int sw = Math.min(440, Math.max(220, contentW * 2 / 5));
		drawSearch(ctx, right - sw, top - 4, sw, 46);
		filter = 0;
		gridTop = top + 56;
		gridBottom = wy + wh - 22;
	}

	private List<GuiModule> visibleModulesUnfiltered() {
		int f = filter;
		filter = 0;
		List<GuiModule> l = visibleModules();
		filter = f;
		return l;
	}

	private void drawSearch(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		boolean focused = focus == Focus.SEARCH;
		float f = anim("search.focus", focused ? 1F : 0F, 16F);
		Draw.round(ctx, x, y, w, h, 10, Draw.argb(70, 0x000000));
		Draw.outline(ctx, x, y, w, h, 10, 2, Draw.argb(Math.round(130 + 125 * f), Draw.mix(LINE, 0xFFFFFF, f)));
		// the field first, so the clear (X) button added below sits on top and gets its clicks
		hitTagged("search", x, y, w, h, () -> {
			commitField();
			focus = Focus.SEARCH;
		}, null, null);
		int tx = x + 20;
		int maxW = w - 20 - 34;
		if (search.isEmpty() && !focused) {
			Draw.textMid(ctx, "Search...", tx, y + h / 2F, 18, Draw.argb(255, 0xC8C8D0));
			Icons.drawCentered(ctx, Icons.ui("search"), x + w - 22, y + h / 2F, 18, Draw.argb(160, 0xFFFFFF));
		} else {
			String shown = search;
			while (Draw.width(shown, 18) > maxW && shown.length() > 1) {
				shown = shown.substring(1);
			}
			Draw.textMid(ctx, shown, tx, y + h / 2F, 18, Draw.argb(255, 0xFFFFFF));
			if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
				Draw.rect(ctx, tx + Draw.width(shown, 18) + 1, y + 12, 2, h - 24, Draw.argb(255, 0xFFFFFF));
			}
			if (!search.isEmpty()) {
				int bx = x + w - 34;
				boolean hov = over(bx, y + 10, 24, h - 20);
				Draw.round(ctx, bx, y + 11, 24, 24, 12, Draw.argb(hov ? 60 : 30, 0xFFFFFF));
				Icons.drawCentered(ctx, Icons.ui("close"), bx + 12, y + 23, 15, Draw.argb(255, 0xE0E0E6));
				hitTagged("search", bx, y + 10, 24, h - 20, () -> {
					search = "";
					scrollTarget = 0;
					UiSounds.click();
				}, null, null);
			}
		}

	}

	private void button(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String label, boolean accentStyle, Runnable action) {
		button(ctx, id, x, y, w, h, label, accentStyle, action, null);
	}

	private void button(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String label, boolean accentStyle, Runnable action, int[] clip) {
		boolean hov = clip == null ? over(x, y, w, h) : over(x, y, w, h, clip);
		float hv = anim("btn." + id, hov ? 1F : 0F, 18F);
		if (hov) {
			hoverId = "btn:" + id;
		}
		if (accentStyle) {
			Draw.round(ctx, x, y, w, h, 10, Draw.argb(Math.round(40 + 40 * hv), accent()));
			Draw.outline(ctx, x, y, w, h, 10, 2, Draw.argb(Math.round(140 + 115 * hv), accent()));
			Draw.textCenter(ctx, label, x + w / 2F, y + h / 2F, 14, Draw.argb(255, 0xFFFFFF));
		} else {
			Draw.round(ctx, x, y, w, h, 10, Draw.argb(Math.round(60 + 40 * hv), 0x000000));
			Draw.outline(ctx, x, y, w, h, 10, 2, Draw.argb(Math.round(130 + 110 * hv), LINE));
			Draw.textCenter(ctx, label, x + w / 2F, y + h / 2F, 14, Draw.argb(255, Draw.mix(0xD8D8DE, 0xFFFFFF, hv)));
		}
		hit(x, y, w, h, action, null, clip);
	}

	private void drawGrid(GuiGraphicsExtractor ctx) {
		List<GuiModule> mods = visibleModules();
		int cols = switch (ClickGUI.columns.getMode()) {
			case Two -> 2;
			case Three -> 3;
			case Four -> 4;
			default -> contentW >= 1180 ? 4 : contentW >= 620 ? 3 : 2;
		};
		int cardH = cardHeight();
		int gap = 22;
		int scrollbar = 12;
		int cw = (contentW - scrollbar - gap * (cols - 1)) / cols;
		int rows = (mods.size() + cols - 1) / cols;
		int contentH = rows * (cardH + gap);
		int viewH = gridBottom - gridTop;
		maxScroll = Math.max(0, contentH - viewH + 6);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll += (scrollTarget - scroll) * Math.min(1F, dt * 14F);
		gridRect = new int[]{contentX - 8, gridTop, contentX + contentW + 8, gridBottom};

		if (mods.isEmpty()) {
			String msg = !search.isEmpty() ? "No module matches \"" + search + "\""
					: page.id.equals("favorites") ? "No favorites yet" : page.id.equals("enabled") ? "Nothing is switched on yet" : "Nothing to show";
			String hint = page.id.equals("favorites") ? "Click the heart on any module to add it here" : "Try another category";
			Draw.textCenter(ctx, msg, contentX + contentW / 2F, gridTop + viewH / 2F - 12, 18, Draw.argb(255, 0xE0E0E6));
			Draw.textCenter(ctx, hint, contentX + contentW / 2F, gridTop + viewH / 2F + 16, 14, Draw.argb(255, 0xA8A8B2));
			return;
		}

		ctx.enableScissor(gridRect[0], gridRect[1], gridRect[2], gridRect[3]);
		for (int i = 0; i < mods.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int x = contentX + col * (cw + gap);
			int y = gridTop + 4 + row * (cardH + gap) - Math.round(scroll);
			if (y + cardH < gridTop - 4 || y > gridBottom + 4) {
				continue;
			}
			drawCard(ctx, mods.get(i), x, y, cw, cardH);
		}
		ctx.disableScissor();
		drawScrollbar(ctx, contentX + contentW + 2, gridTop + 4, viewH - 8, scroll, maxScroll, viewH);
	}

	private void drawScrollbar(GuiGraphicsExtractor ctx, int x, int y, int h, float pos, float max, int view) {
		if (max <= 0) {
			return;
		}
		float total = max + view;
		int bh = Math.max(30, Math.round(h * view / total));
		int by = y + Math.round((h - bh) * (pos / max));
		Draw.round(ctx, x, y, 5, h, 2, Draw.argb(30, 0xFFFFFF));
		Draw.round(ctx, x, by, 5, bh, 2, Draw.argb(170, 0xFFFFFF));
	}

	private int cardHeight() {
		return ClickGUI.cardSize.isMode(ClickGUI.CardSize.Compact) ? 160 : 200;
	}

	/** A module card: name and heart on top, its item in the middle, settings and on/off buttons below. */
	private void drawCard(GuiGraphicsExtractor ctx, GuiModule mod, int x, int y, int w, int h) {
		boolean toggleable = mod.toggleable();
		boolean on = toggleable && mod.enabled();
		boolean hov = over(x, y, w, h, gridRect) && inspected == null;
		float hv = anim("card.h." + mod.id(), hov ? 1F : 0F, 16F);
		float en = anim("card.e." + mod.id(), on ? 1F : 0F, 12F);
		int r = 12;
		if (hov) {
			hoverId = "card:" + mod.id();
		}
		Draw.round(ctx, x, y, w, h, r, Draw.argb(Math.round(Backdrop.cardAlpha() * 0.28F + 40 * hv), 0x000000));
		Draw.outline(ctx, x, y, w, h, r, 2, Draw.argb(Math.round(120 + 100 * hv), Draw.mix(LINE, accent(), 0.6F * en)));

		// card body: left click = settings (or on/off when there are none), right click = settings
		hit(x, y, w, h, () -> {
			if (!mod.settings().isEmpty() || !toggleable) {
				if (mod.onOpen() != null) {
					UiSounds.click();
					mod.onOpen().run();
				} else {
					openInspector(mod);
				}
			} else {
				mod.setEnabled(!mod.enabled());
			}
		}, !mod.settings().isEmpty() ? () -> openInspector(mod) : null, gridRect);

		String name = Draw.fit(mod.name(), 17, w - 70, false);
		Draw.text(ctx, name, x + 20, y + 18, 17, Draw.argb(255, 0xFFFFFF));
		if (mod.isNew()) {
			int nx = x + 20 + Draw.width(name, 17) + 8;
			if (nx + 36 < x + w - 44) {
				Draw.round(ctx, nx, y + 19, 36, 16, 8, Draw.argb(60, CLOVER));
				Draw.textCenter(ctx, "NEW", nx + 18, y + 27, 10, Draw.argb(255, CLOVER), true);
			}
		}

		// favourite heart
		boolean fav = Favorites.has(mod);
		int hx = x + w - 38, hy = y + 12;
		boolean hh = over(hx, hy, 28, 28, gridRect) && inspected == null;
		Icons.drawCentered(ctx, fav ? "heart" : "heart-outline", hx + 14, hy + 14, 22,
				Draw.argb(fav ? 255 : (hh ? 230 : 150), fav ? 0xFFFFFF : 0xD8D8DE));

		// the module's item
		float isz = Math.min(64F, h * 0.3F);
		drawItem(ctx, ModuleItems.of(mod.name()), x + w / 2F, y + 44 + (h - 44 - 62) / 2F, isz);

		// buttons: settings gear + on/off (or Settings)
		int byy = y + h - 58;
		int gw = 46;
		boolean hasSettings = !mod.settings().isEmpty() || mod.onOpen() != null;
		boolean gh = over(x + 16, byy, gw, 42, gridRect) && inspected == null;
		Draw.round(ctx, x + 16, byy, gw, 42, 8, Draw.argb(gh ? 90 : 60, 0x000000));
		Draw.outline(ctx, x + 16, byy, gw, 42, 8, 2, Draw.argb(hasSettings ? (gh ? 240 : 140) : 60, LINE));
		Icons.drawCentered(ctx, Icons.ui("settings"), x + 16 + gw / 2F, byy + 21, 20, Draw.argb(hasSettings ? 235 : 90, 0xFFFFFF));
		int mbx = x + 16 + gw + 10, mbw = w - 32 - gw - 10;
		boolean mh = over(mbx, byy, mbw, 42, gridRect) && inspected == null;
		float mhv = anim("card.b." + mod.id(), mh ? 1F : 0F, 18F);
		String label = toggleable ? (on ? "Enabled" : "Disabled") : "Settings";
		if (on) {
			Draw.round(ctx, mbx, byy, mbw, 42, 8, Draw.argb(Math.round(70 + 50 * mhv), accent()));
			Draw.outline(ctx, mbx, byy, mbw, 42, 8, 2, Draw.argb(255, accent()));
		} else {
			Draw.round(ctx, mbx, byy, mbw, 42, 8, Draw.argb(Math.round(60 + 30 * mhv), 0x000000));
			Draw.outline(ctx, mbx, byy, mbw, 42, 8, 2, Draw.argb(Math.round(140 + 100 * mhv), LINE));
		}
		Draw.textCenter(ctx, label, mbx + mbw / 2F, byy + 21, 18, Draw.argb(255, on ? 0xFFFFFF : Draw.mix(0xE4E4EA, 0xFFFFFF, mhv)));
		if (hov && !mod.description().isBlank() && tooltip == null && !mh && !gh && !hh) {
			tooltip = mod.description();
		}

		// these sit on top of the card body, so they get the click first
		if (hasSettings) {
			hit(x + 16, byy, gw, 42, () -> {
				if (mod.onOpen() != null) {
					UiSounds.click();
					mod.onOpen().run();
				} else {
					openInspector(mod);
				}
			}, null, gridRect);
			if (gh) {
				tooltip = "Settings";
			}
		}
		hit(mbx, byy, mbw, 42, () -> {
			if (toggleable) {
				mod.setEnabled(!mod.enabled());
			} else if (mod.onOpen() != null) {
				UiSounds.click();
				mod.onOpen().run();
			} else {
				openInspector(mod);
			}
		}, null, gridRect);
		hit(hx, hy, 28, 28, () -> {
			Favorites.toggle(mod);
			UiSounds.click();
		}, null, gridRect);
		if (hh) {
			tooltip = fav ? "Remove from favorites" : "Add to favorites";
		}
	}

	private void drawSwitch(GuiGraphicsExtractor ctx, String id, int x, int y, boolean on) {
		float t = anim("sw." + id, on ? 1F : 0F, 16F);
		int w = 46, h = 26;
		if (t > 0.01F) {
			Draw.roundGradient(ctx, x, y, w, h, 13, Draw.argb(255, Draw.mix(FIELD, accent(), t)), Draw.argb(255, Draw.mix(FIELD, accent2(), t)));
		} else {
			Draw.round(ctx, x, y, w, h, 13, Draw.argb(255, FIELD));
		}
		Draw.outline(ctx, x, y, w, h, 13, 1, Draw.argb(Math.round(255 * (1 - t)), BORDER));
		float kx = x + 13 + (w - 26) * t;
		Draw.circle(ctx, kx, y + h / 2F + 1, 9.5F, Draw.argb(60, 0x000000));
		Draw.circle(ctx, kx, y + h / 2F, 9, Draw.argb(255, Draw.mix(0x8F8798, 0xFFFFFF, t)));
	}

	private void drawCapes(GuiGraphicsExtractor ctx) {
		int top = gridTop;
		int ty = top;
		boolean elytra = Cosmetics.capeOnElytra();
		drawSwitch(ctx, "cape.elytra", contentX, ty + 5, elytra);
		Draw.textMid(ctx, "Also on the elytra", contentX + 56, ty + 18, 13, Draw.argb(255, TEXT_DIM));
		hit(contentX, ty, 56 + Draw.width("Also on the elytra", 13), 36, () -> {
			Cosmetics.setCapeOnElytra(!Cosmetics.capeOnElytra());
			UiSounds.toggle(Cosmetics.capeOnElytra());
			Config.saveIfNeeded(false);
		});
		// Other TopClient players' capes, hats, wings, buddies and auras (read from their skins)
		int ox = contentX + 56 + Draw.width("Also on the elytra", 13) + 24;
		boolean others = Cosmetics.showOthers();
		drawSwitch(ctx, "cape.others", ox, ty + 5, others);
		Draw.textMid(ctx, "Others' cosmetics", ox + 56, ty + 18, 13, Draw.argb(255, TEXT_DIM));
		hit(ox, ty, 56 + Draw.width("Others' cosmetics", 13), 36, () -> {
			Config.setBool("cos.showOthers", !Cosmetics.showOthers());
			UiSounds.toggle(Cosmetics.showOthers());
			Config.saveIfNeeded(false);
		});

		int fw = Math.max(140, Math.min(380, contentW - 520));
		int fx = contentX + contentW - fw - 90;
		textField(ctx, "cape.path", fx, ty, fw, 36, Cosmetics.customCapePath(), "Your own cape: path to a .png", s -> {
			Cosmetics.setCustomCapePath(s);
			Config.saveIfNeeded(false);
		});
		button(ctx, "cape.apply", fx + fw + 8, ty, 82, 36, "Use it", true, () -> {
			commitField();
			if (Cosmetics.cape("custom") != null) {
				Cosmetics.save(Cosmetics.Slot.CAPE, "custom");
			}
			UiSounds.click();
		});

		int gTop = top + 52;
		int viewH = gridBottom - gTop;
		int th = 158;
		int cols = Math.max(1, (contentW - 10 + GAP) / (112 + GAP));
		int tw = (contentW - 10 - GAP * (cols - 1)) / cols;
		String[][] capes = Cosmetics.of(Cosmetics.Slot.CAPE);
		int count = capes.length + 1;
		int rows = (count + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (th + GAP) - viewH + 6);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll += (scrollTarget - scroll) * Math.min(1F, dt * 14F);
		gridRect = new int[]{contentX - 8, gTop, contentX + contentW, gridBottom};
		String saved = Cosmetics.saved(Cosmetics.Slot.CAPE);
		String hovered = null;

		ctx.enableScissor(gridRect[0], gridRect[1], gridRect[2], gridRect[3]);
		for (int i = 0; i < count; i++) {
			String key = i == 0 ? "none" : capes[i - 1][0];
			String label = i == 0 ? "No cape" : capes[i - 1][1];
			int x = contentX + (i % cols) * (tw + GAP);
			int y = gTop + 4 + (i / cols) * (th + GAP) - Math.round(scroll);
			if (y + th < gTop - 4 || y > gridBottom + 4) {
				continue;
			}
			boolean sel = key.equals(saved);
			boolean hov = over(x, y, tw, th, gridRect) && inspected == null;
			if (hov) {
				hovered = key;
			}
			float hv = anim("cape.h." + key, hov ? 1F : 0F, 16F);
			float sv = anim("cape.s." + key, sel ? 1F : 0F, 14F);
			int r = Math.max(8, radius() - 2);
			Draw.round(ctx, x, y - Math.round(hv * 2), tw, th, r, Draw.argb(255, Draw.mix(CARD, CARD_HOVER, hv)));
			Draw.outline(ctx, x, y - Math.round(hv * 2), tw, th, r, sv > 0.5F ? 2 : 1, Draw.argb(255, Draw.mix(BORDER, accent(), Math.max(sv, hv * 0.4F))));
			int yy = y - Math.round(hv * 2);

			if (i == 0) {
				Draw.textCenter(ctx, "-", x + tw / 2F, yy + 58, 26, Draw.argb(255, TEXT_MUTE));
			} else {
				Cosmetics.CapeTex t = Cosmetics.capeTile(key, hv > 0.05F || sv > 0.5F);
				if (t != null) {
					int aw = 50, ah = 80;
					int ax = x + (tw - aw) / 2, ay = yy + 16;
					Draw.round(ctx, ax - 4, ay - 4, aw + 8, ah + 8, 6, Draw.argb(255, FIELD));
					ctx.blit(RenderPipelines.GUI_TEXTURED, t.id(), ax, ay, t.w() / 64F, t.h() / 32F, aw, ah,
							t.w() * 10 / 64, t.h() / 2, t.w(), t.h(), Draw.fade(0xFFFFFFFF));
				} else {
					Draw.textCenter(ctx, "+ PNG", x + tw / 2F, yy + 58, 14, Draw.argb(255, TEXT_MUTE));
				}
			}
			Draw.textCenter(ctx, Draw.fit(label, 12, tw - 12), x + tw / 2F, yy + th - 30, 12, Draw.argb(255, sel ? TEXT : TEXT_DIM));
			if (sel) {
				Draw.round(ctx, x + tw / 2 - 26, yy + th - 18, 52, 14, 7, Draw.argb(46, accent()));
				Draw.textCenter(ctx, "WEARING", x + tw / 2F, yy + th - 10.5F, 9, Draw.argb(255, accent()));
			}
			hit(x, y, tw, th, () -> {
				if ("custom".equals(key) && Cosmetics.cape("custom") == null) {
					focusField("cape.path", Cosmetics.customCapePath(), s -> {
						Cosmetics.setCustomCapePath(s);
						Config.saveIfNeeded(false);
					});
					return;
				}
				Cosmetics.save(Cosmetics.Slot.CAPE, key);
				Config.saveIfNeeded(false);
				UiSounds.toggle(!"none".equals(key));
			}, null, gridRect);
		}
		ctx.disableScissor();
		Cosmetics.tryOn(Cosmetics.Slot.CAPE, hovered);
		drawScrollbar(ctx, contentX + contentW - 4, gTop + 4, viewH - 8, scroll, maxScroll, viewH);
	}

	private void textField(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String value, String placeholder, Consumer<String> commit) {
		textField(ctx, id, x, y, w, h, value, placeholder, commit, null);
	}

	private void textField(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String value, String placeholder, Consumer<String> commit, int[] clip) {
		boolean focused = focus == Focus.FIELD && fieldId.equals(id);
		float f = anim("field." + id, focused ? 1F : 0F, 16F);
		Draw.round(ctx, x, y, w, h, 10, Draw.argb(255, FIELD));
		Draw.outline(ctx, x, y, w, h, 10, 1, Draw.argb(255, Draw.mix(BORDER, accent(), f)));
		String text = focused ? fieldText : value;
		int maxW = w - 24;
		if (text == null || text.isEmpty()) {
			if (!focused) {
				Draw.textMid(ctx, Draw.fit(placeholder, 12.5F, maxW), x + 12, y + h / 2F, 12.5F, Draw.argb(255, TEXT_MUTE));
			}
			text = "";
		}
		String shown = text;
		while (Draw.width(shown, 12.5F) > maxW && shown.length() > 1) {
			shown = shown.substring(1);
		}
		if (!shown.isEmpty()) {
			Draw.textMid(ctx, shown, x + 12, y + h / 2F, 12.5F, Draw.argb(255, TEXT));
		}
		if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
			Draw.rect(ctx, x + 12 + Draw.width(shown, 12.5F) + 1, y + 9, 1, h - 18, Draw.argb(255, accent()));
		}
		hitTagged("field:" + id, x, y, w, h, () -> {
			if (!focused) {
				focusField(id, value, commit);
			}
		}, null, clip);
	}

	private void drawFooter(GuiGraphicsExtractor ctx) {
		if (!ClickGUI.showStats.getValue()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Draw.text(ctx, mc.getFps() + " FPS", 14, 10, 15, Draw.argb(200, 0xFFFFFF));
		if (mc.player != null && mc.getConnection() != null) {
			String ping = "Ping: " + Utils.getPing(mc.player) + " ms";
			Draw.text(ctx, ping, Math.round(wx + ww) - Draw.width(ping, 15), 10, 15, Draw.argb(200, 0xFFFFFF));
		}
	}

	private void drawInspector(GuiGraphicsExtractor ctx) {
		float raw = anim("insp", inspected != null ? 1F : 0F, 12F);
		if (raw <= 0.01F && inspected == null) {
			anims.put("insp", 0F);
			inspectedShown = null;
			inspRect = new int[4];
			return;
		}
		float t = ease(raw);
		GuiModule mod = inspectedShown;
		if (mod == null) {
			return;
		}
		ctx.enableScissor(wx, wy, wx + ww, wy + wh);
		try {
			drawInspectorPanel(ctx, mod, t);
		} finally {
			ctx.disableScissor();
		}
	}

	private void drawInspectorPanel(GuiGraphicsExtractor ctx, GuiModule mod, float t) {
		int r = radius();
		int left = wx + SIDEBAR_W + 1;
		Draw.roundSide(ctx, left, wy, ww - SIDEBAR_W - 1, wh, r, Draw.argb(Math.round(130 * t), 0x050407), false);
		hit(left, wy, ww - SIDEBAR_W - 1, wh, this::closeInspector);

		int pw = INSPECTOR_W;
		int px = wx + ww - Math.round(pw * t);
		int py = wy;
		int ph = wh;
		inspRect = new int[]{px, py, px + pw, py + ph};
		Draw.gradientH(ctx, px - 26, py + 2, 26, ph - 4, Draw.argb(0, 0x000000), Draw.argb(Math.round(90 * t), 0x000000));
		Draw.roundSide(ctx, px, py, pw, ph, r, Draw.argb(255, 0x17141B), false);
		Draw.rect(ctx, px, py, 1, ph, Draw.argb(255, BORDER));
		Draw.gradientV(ctx, px + 1, py, pw - r, 90, Draw.argb(26, accent()), Draw.argb(0, accent()));
		hit(px, py, pw, ph, () -> {
		});

		int x = px + 24;
		int w = pw - 48;
		int y = py + 24;

		int cbx = px + pw - 24 - 30;
		boolean ch = over(cbx, y - 2, 30, 30);
		Draw.round(ctx, cbx, y - 2, 30, 30, 9, Draw.argb(ch ? 255 : 0, CARD_HOVER));
		Icons.drawCentered(ctx, Icons.ui("close"), cbx + 15, y + 13, 18, Draw.argb(255, ch ? TEXT : TEXT_DIM));
		hit(cbx, y - 2, 30, 30, this::closeInspector);

		Draw.roundGradient(ctx, x, y - 4, 42, 42, 12, Draw.argb(255, accent()), Draw.argb(255, accent2()));
		Icons.drawModule(ctx, mod.name(), x + 21, y + 17, 23, Draw.argb(255, onAccent()));
		Draw.text(ctx, Draw.fit(mod.name(), 20, w - 100, true), x + 56, y - 1, 20, Draw.argb(255, TEXT), true);
		Page owner = pageOf(mod);
		if (owner != null) {
			Draw.text(ctx, (owner.clover ? "Clover  ·  " : "") + owner.title, x + 56, y + 23, 12, Draw.argb(255, TEXT_MUTE));
		}
		y += 52;
		List<String> desc = Draw.wrap(mod.description(), 12.5F, w, 3);
		for (String line : desc) {
			Draw.text(ctx, line, x, y, 12.5F, Draw.argb(255, TEXT_DIM));
			y += 18;
		}
		y += 10;

		if (mod.toggleable()) {
			boolean on = mod.enabled();
			Draw.round(ctx, x, y, w, 46, 12, Draw.argb(255, on ? Draw.mix(CARD, accent(), 0.10F) : CARD));
			Draw.outline(ctx, x, y, w, 46, 12, 1, Draw.argb(255, on ? Draw.mix(BORDER, accent(), 0.6F) : BORDER));
			Draw.textMid(ctx, on ? "Enabled" : "Disabled", x + 16, y + 23, 15, Draw.argb(255, TEXT), true);
			drawSwitch(ctx, "insp." + mod.id(), x + w - 16 - 46, y + 10, on);
			hit(x, y, w, 46, () -> mod.setEnabled(!mod.enabled()));
			y += 58;
		}

		int listTop = y;
		int listBottom = py + ph - 66;
		int[] clip = new int[]{px, listTop, px + pw, listBottom};
		inspScroll += (inspScrollTarget - inspScroll) * Math.min(1F, dt * 14F);
		ctx.enableScissor(px, listTop, px + pw, listBottom);
		int sy = listTop - Math.round(inspScroll);
		List<GuiSetting> settings = mod.settings();
		if (settings.isEmpty()) {
			Draw.text(ctx, "No settings", x, sy + 8, 13, Draw.argb(255, TEXT_MUTE));
		}
		for (GuiSetting s : settings) {
			sy += drawSetting(ctx, mod, s, x, sy, w, clip);
		}
		ctx.disableScissor();
		int listH = sy + Math.round(inspScroll) - listTop;
		inspMaxScroll = Math.max(0, listH - (listBottom - listTop) + 8);
		inspScrollTarget = Math.max(0, Math.min(inspMaxScroll, inspScrollTarget));
		if (inspMaxScroll > 0) {
			Draw.gradientV(ctx, px + 1, listBottom - 16, pw - r, 16, Draw.argb(0, 0x17141B), Draw.argb(255, 0x17141B));
			drawScrollbar(ctx, px + pw - 10, listTop + 4, listBottom - listTop - 8, inspScroll, inspMaxScroll, listBottom - listTop);
		}

		int by = py + ph - 52;
		Draw.rect(ctx, px + 1, by - 12, pw - r, 1, Draw.argb(255, BORDER));
		boolean rh = over(x, by, w, 36);
		Draw.round(ctx, x, by, w, 36, 10, Draw.argb(rh ? 40 : 18, DANGER));
		Draw.outline(ctx, x, by, w, 36, 10, 1, Draw.argb(rh ? 200 : 90, DANGER));
		Icons.drawCentered(ctx, Icons.ui("reset"), x + w / 2F - Draw.width("Reset to defaults", 13, true) / 2F - 4, by + 18, 16, Draw.argb(255, DANGER));
		Draw.textCenter(ctx, "Reset to defaults", x + w / 2F + 9, by + 18, 13, Draw.argb(255, DANGER), true);
		hit(x, by, w, 36, () -> {
			mod.resetSettings();
			UiSounds.toggle(false);
		});
	}

	private int drawSetting(GuiGraphicsExtractor ctx, GuiModule mod, GuiSetting s, int x, int y, int w, int[] clip) {
		String id = mod.id() + "/" + s.label;
		boolean rowHover = over(x, y, w, 40, clip);
		if (rowHover && s.description != null && !s.description.isBlank()) {
			tooltip = s.description;
		}
		switch (s.kind) {
			case HEADER -> {
				Draw.text(ctx, s.label.toUpperCase(Locale.ROOT), x, y + 16, 11, Draw.argb(255, accent()), true);
				int lw = Draw.width(s.label.toUpperCase(Locale.ROOT), 11, true);
				Draw.rect(ctx, x + lw + 10, y + 22, w - lw - 10, 1, Draw.argb(255, BORDER));
				return 36;
			}
			case TOGGLE -> {
				Draw.textMid(ctx, Draw.fit(s.label, 14, w - 70), x, y + 22, 14, Draw.argb(255, TEXT));
				drawSwitch(ctx, id, x + w - 46, y + 9, s.bool());
				hit(x, y, w, 44, () -> s.setBool(!s.bool()), null, clip);
				return 44;
			}
			case SLIDER, RANGE -> {
				String val = s.valueText();
				int vw = Draw.width(val, 12) + 16;
				Draw.textMid(ctx, Draw.fit(s.label, 14, w - vw - 12), x, y + 16, 14, Draw.argb(255, TEXT));
				Draw.round(ctx, x + w - vw, y + 5, vw, 22, 7, Draw.argb(255, FIELD));
				Draw.textCenter(ctx, val, x + w - vw / 2F, y + 16.5F, 12, Draw.argb(255, TEXT_DIM));
				int tx = x + 8, tw = w - 16, ty = y + 40;
				double range = Math.max(1e-9, s.max() - s.min());
				Draw.round(ctx, tx, ty - 3, tw, 6, 3, Draw.argb(255, FIELD));
				if (s.kind == GuiSetting.Kind.SLIDER) {
					float f = anim("sl." + id, (float) ((s.value() - s.min()) / range), 20F);
					int fw = Math.round(tw * f);
					Draw.roundGradient(ctx, tx, ty - 3, Math.max(6, fw), 6, 3, Draw.argb(255, accent()), Draw.argb(255, accent2()));
					knob(ctx, tx + fw, ty);
					hit(x, y + 28, w, 26, () -> {
						drag = px -> s.setValue(s.snap(s.min() + Math.max(0, Math.min(1, (px - tx) / tw)) * range));
						drag.accept(mx);
					}, null, clip);
				} else {
					float lo = anim("lo." + id, (float) ((s.low() - s.min()) / range), 20F);
					float hi = anim("hi." + id, (float) ((s.high() - s.min()) / range), 20F);
					int lx = tx + Math.round(tw * lo), hx = tx + Math.round(tw * hi);
					Draw.round(ctx, lx, ty - 3, Math.max(6, hx - lx), 6, 3, Draw.argb(255, accent()));
					knob(ctx, lx, ty);
					knob(ctx, hx, ty);
					hit(x, y + 28, w, 26, () -> {
						double clickV = s.min() + Math.max(0, Math.min(1, (mx - tx) / tw)) * range;
						boolean lowKnob = Math.abs(clickV - s.low()) <= Math.abs(clickV - s.high());
						drag = px -> {
							double v = s.snap(s.min() + Math.max(0, Math.min(1, (px - tx) / tw)) * range);
							if (lowKnob) {
								s.setLow(v);
							} else {
								s.setHigh(v);
							}
						};
						drag.accept(mx);
					}, null, clip);
				}
				return 62;
			}
			case MODE -> {
				String[] all = s.modes();
				boolean picker = all.length > 4;
				boolean open = picker && modeOpen == s;
				boolean themes = s.label.equals("Theme") && GuiTheme.swatch(0) != null && all.length > 10;
				int bw = Math.min(200, w / 2);
				int bx = x + w - bw;
				Draw.textMid(ctx, Draw.fit(s.label, 14, w - bw - 12), x, y + 22, 14, Draw.argb(255, TEXT));
				int third = bw / 4;
				boolean hl = over(bx, y + 6, third, 32, clip);
				boolean hr = over(bx + bw - third, y + 6, third, 32, clip);
				boolean hm = picker && over(bx + third, y + 6, bw - third * 2, 32, clip);
				Draw.round(ctx, bx, y + 6, bw, 32, 9, Draw.argb(255, FIELD));
				Draw.outline(ctx, bx, y + 6, bw, 32, 9, 1, Draw.argb(255, open || hl || hr || hm ? Draw.mix(BORDER, accent(), 0.6F) : BORDER));
				Icons.drawCentered(ctx, Icons.ui("back"), bx + 14, y + 22, 16, Draw.argb(255, hl ? accent() : TEXT_MUTE));
				Icons.drawCentered(ctx, Icons.ui("chevron"), bx + bw - 14, y + 22, 16, Draw.argb(255, hr ? accent() : TEXT_MUTE));
				String value = s.valueText();
				int[] sw = themes ? GuiTheme.swatch(s.modeIndex()) : null;
				int vw = Draw.width(Draw.fit(value, 13, bw - 70), 13) + (themes ? 18 : 0);
				float vx = bx + bw / 2F - vw / 2F;
				if (themes) {
					drawSwatch(ctx, vx, y + 15, sw);
					vx += 18;
				}
				Draw.text(ctx, Draw.fit(value, 13, bw - 70), vx, y + 15, 13, Draw.argb(255, hm ? accent() : TEXT), picker);
				hit(bx, y + 6, third, 32, () -> s.setModeIndex(s.modeIndex() - 1), () -> s.setModeIndex(s.modeIndex() + 1), clip);
				hit(bx + bw - third, y + 6, third, 32, () -> s.setModeIndex(s.modeIndex() + 1), () -> s.setModeIndex(s.modeIndex() - 1), clip);
				hit(bx + third, y + 6, bw - third * 2, 32, () -> {
					if (picker) {
						modeOpen = open ? null : s;
						UiSounds.click();
					} else {
						s.setModeIndex(s.modeIndex() + 1);
					}
				}, () -> s.setModeIndex(s.modeIndex() - 1), clip);
				if (!open) {
					return 46;
				}
				int cx = x, cy = y + 48;
				int chipH = 30;
				for (int i = 0; i < all.length; i++) {
					int idx = i;
					boolean sel = i == s.modeIndex();
					int[] c = themes ? GuiTheme.swatch(i) : null;
					int cw = Draw.width(all[i], 12.5F, sel) + 22 + (themes ? 18 : 0);
					if (cx + cw > x + w) {
						cx = x;
						cy += chipH + 8;
					}
					boolean ch = over(cx, cy, cw, chipH, clip);
					Draw.round(ctx, cx, cy, cw, chipH, 9, Draw.argb(255, sel ? Draw.mix(FIELD, accent(), 0.18F) : ch ? CARD_HOVER : FIELD));
					Draw.outline(ctx, cx, cy, cw, chipH, 9, 1, Draw.argb(255, sel ? accent() : ch ? BORDER_HOVER : BORDER));
					int tx = cx + 11;
					if (themes) {
						drawSwatch(ctx, tx, cy + 8, c);
						tx += 18;
					}
					Draw.textMid(ctx, all[i], tx, cy + chipH / 2F, 12.5F, Draw.argb(255, sel ? TEXT : TEXT_DIM), sel);
					hit(cx, cy, cw, chipH, () -> {
						s.setModeIndex(idx);
						UiSounds.click();
					}, null, clip);
					cx += cw + 8;
				}
				return cy + chipH + 12 - y;
			}
			case KEY -> {
				boolean listen = listening == s;
				String k = listen ? "Press a key..." : s.keyName().toUpperCase(Locale.ROOT);
				int kw = Math.max(90, Draw.width(k, 12) + 26);
				int kx = x + w - kw;
				Draw.textMid(ctx, Draw.fit(s.label, 14, w - kw - 12), x, y + 22, 14, Draw.argb(255, TEXT));
				float pulse = listen ? 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0F;
				Draw.round(ctx, kx, y + 7, kw, 30, 9, Draw.argb(255, listen ? Draw.mix(FIELD, accent(), 0.15F) : FIELD));
				Draw.outline(ctx, kx, y + 7, kw, 30, 9, 1, Draw.argb(255, listen ? Draw.mix(BORDER, accent(), pulse) : BORDER));
				Draw.textCenter(ctx, k, kx + kw / 2F, y + 22.5F, 12, Draw.argb(255, listen ? accent() : TEXT_DIM));
				hitTagged("key:" + id, kx, y + 7, kw, 30, () -> {
					listening = listen ? null : s;
					UiSounds.click();
				}, () -> {
					s.setKey(s.noneKey());
					listening = null;
				}, clip);
				return 46;
			}
			case TEXT -> {
				int fw = Math.min(220, w * 3 / 5);
				Draw.textMid(ctx, Draw.fit(s.label, 14, w - fw - 12), x, y + 22, 14, Draw.argb(255, TEXT));
				textField(ctx, id, x + w - fw, y + 6, fw, 32, s.text(), "Type here", s::setText, clip);
				return 46;
			}
			case COLOR -> {
				boolean open = colorOpen == s;
				int rgb = s.rgb();
				String hex = String.format("#%06X", rgb & 0xFFFFFF);
				Draw.textMid(ctx, Draw.fit(s.label, 14, w - 140), x, y + 22, 14, Draw.argb(255, TEXT));
				Draw.textRight(ctx, hex, x + w - 52, y + 22, 12, Draw.argb(255, TEXT_MUTE));
				Draw.round(ctx, x + w - 42, y + 9, 42, 26, 8, Draw.argb(255, BORDER));
				Draw.round(ctx, x + w - 40, y + 11, 38, 22, 7, Draw.argb(255, rgb));
				hit(x, y, w, 44, () -> {
					colorOpen = open ? null : s;
					UiSounds.click();
				}, null, clip);
				if (!open) {
					return 44;
				}
				int sz = 24, gap = Math.max(4, (w - PALETTE.length * sz) / (PALETTE.length - 1));
				for (int i = 0; i < PALETTE.length; i++) {
					int c = PALETTE[i];
					int cx = x + i * (sz + gap);
					int cy = y + 46;
					boolean sel = (c & 0xFFFFFF) == (rgb & 0xFFFFFF);
					Draw.round(ctx, cx - 2, cy - 2, sz + 4, sz + 4, 8, Draw.argb(sel ? 255 : 0, accent()));
					Draw.round(ctx, cx, cy, sz, sz, 7, Draw.argb(255, c));
					hit(cx, cy, sz, sz, () -> s.setRgb(c), null, clip);
				}
				return 82;
			}
			case ACTION -> {
				button(ctx, "act." + id, x, y + 5, w, 34, s.label, false, s::run, clip);
				return 46;
			}
		}
		return 0;
	}

	private void drawSwatch(GuiGraphicsExtractor ctx, float x, float y, int[] c) {
		int a = c == null ? accent() : c[0];
		int b = c == null ? accent2() : c[1];
		Draw.splitCircle(ctx, x + 7, y + 7, 7F, Draw.argb(255, a), Draw.argb(255, b));
	}

	private void knob(GuiGraphicsExtractor ctx, int x, int y) {
		Draw.circle(ctx, x, y + 1, 9.5F, Draw.argb(70, 0x000000));
		Draw.circle(ctx, x, y, 9, Draw.argb(255, accent()));
		Draw.circle(ctx, x, y, 6, Draw.argb(255, 0xFFFFFF));
	}

	private void drawTooltip(GuiGraphicsExtractor ctx) {
		if (tooltip == null || drag != null) {
			return;
		}
		List<String> lines = Draw.wrap(tooltip, 12, 300, 4);
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, Draw.width(l, 12));
		}
		w += 24;
		int h = lines.size() * 17 + 16;
		int x = (int) mx + 16;
		int y = (int) my + 14;
		if (x + w > wx + ww - 8) {
			x = (int) mx - w - 12;
		}
		Draw.shadow(ctx, x, y, w, h, 9, 10, 60);
		Draw.round(ctx, x, y, w, h, 9, Draw.argb(250, 0x221E27));
		Draw.outline(ctx, x, y, w, h, 9, 1, Draw.argb(255, BORDER));
		for (int i = 0; i < lines.size(); i++) {
			Draw.text(ctx, lines.get(i), x + 12, y + 8 + i * 17, 12, Draw.argb(255, TEXT_DIM));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double px = toDesignX(click.x());
		double py = toDesignY(click.y());
		mx = px;
		my = py;
		int button = click.button();

		Hit target = null;
		for (int i = hits.size() - 1; i >= 0; i--) {
			if (hits.get(i).contains(px, py)) {
				target = hits.get(i);
				break;
			}
		}
		String tag = target == null || target.tag() == null ? "" : target.tag();

		if (listening != null) {
			if (listening.mouseKeys() && button >= 2) {
				listening.setKey(button);
				listening = null;
				return true;
			}
			boolean sameChip = tag.startsWith("key:");
			listening = null;
			if (sameChip && button == 0) {
				return true;
			}
		}

		if (focus == Focus.FIELD && !tag.equals("field:" + fieldId)) {
			commitField();
		}
		if (focus == Focus.SEARCH && !tag.equals("search")) {
			focus = Focus.NONE;
		}

		if (target != null) {
			Runnable r = button == 0 ? target.left() : button == 1 ? target.right() : null;
			if (r != null) {
				r.run();
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (drag != null) {
			mx = toDesignX(click.x());
			drag.accept(mx);
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (drag != null) {
			drag = null;
			Config.saveIfNeeded(false);
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double px = toDesignX(mouseX);
		double py = toDesignY(mouseY);
		float amount = (float) (verticalAmount * 56);
		if (inspected != null && px >= inspRect[0] && px < inspRect[2] && py >= inspRect[1] && py < inspRect[3]) {
			inspScrollTarget = Math.max(0, Math.min(inspMaxScroll, inspScrollTarget - amount));
		} else if (inspected == null && px >= sideRect[0] && px < sideRect[2] && py >= sideRect[1] && py < sideRect[3]) {
			sideScrollTarget = Math.max(0, Math.min(sideMaxScroll, sideScrollTarget - amount));
		} else if (inspected == null) {
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - amount));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int key = input.key();

		if (listening != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				listening = null;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				listening.setKey(listening.noneKey());
				listening = null;
			} else {
				listening.setKey(key);
				listening = null;
			}
			return true;
		}

		if (focus == Focus.SEARCH || focus == Focus.FIELD) {
			boolean isSearch = focus == Focus.SEARCH;
			String text = isSearch ? search : fieldText;
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				if (isSearch && !search.isEmpty()) {
					search = "";
					scrollTarget = 0;
				} else if (isSearch) {
					focus = Focus.NONE;
				} else {
					commitField();
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				if (isSearch) {
					focus = Focus.NONE;
				} else {
					commitField();
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!text.isEmpty()) {
					text = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : text.substring(0, text.length() - 1);
				}
			} else if (input.isPaste()) {
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
				if (clip != null) {
					text += clip.replace("\r", "").replace("\n", "");
				}
			} else {
				return true;
			}
			if (isSearch) {
				search = text.length() > 40 ? text.substring(0, 40) : text;
				scrollTarget = 0;
			} else {
				fieldText = text.length() > 300 ? text.substring(0, 300) : text;
			}
			return true;
		}

		if (key == GLFW.GLFW_KEY_F && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			focus = Focus.SEARCH;
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && inspected != null) {
			closeInspector();
			return true;
		}
		return super.keyPressed(input);
	}

	private boolean swallowOpenKey() {
		if (openKey < 0) {
			return false;
		}
		if (System.currentTimeMillis() - openedAt < 250) {
			return true;
		}
		boolean held;
		try {
			held = openKey >= GLFW.GLFW_KEY_SPACE && GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), openKey) == GLFW.GLFW_PRESS;
		} catch (Throwable t) {
			held = false;
		}
		if (!held) {
			openKey = -1;
		}
		return held;
	}

	public boolean isTyping() {
		return focus != Focus.NONE || listening != null;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (listening != null || !input.isAllowedChatCharacter()) {
			return false;
		}
		if (swallowOpenKey()) {
			return true;
		}
		String typed = input.codepointAsString();
		if (focus == Focus.FIELD) {
			if (fieldText.length() < 300) {
				fieldText += typed;
			}
			return true;
		}
		if (inspected != null) {
			return false;
		}
		if (focus != Focus.SEARCH && typed.isBlank()) {
			return false;
		}
		focus = Focus.SEARCH;
		if (search.length() < 40) {
			search += typed;
			scrollTarget = 0;
		}
		return true;
	}
}
