package dev.topclient.client.top;

import dev.topclient.client.TopClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.gui.Favorites;
import dev.topclient.client.gui.model.CloverAdapter;
import dev.topclient.client.gui.model.CoreAdapter;
import dev.topclient.client.gui.model.GuiModule;
import dev.topclient.client.gui.model.OptionGroups;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.ModCard;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static dev.topclient.client.top.TopTheme.*;

/**
 * The TopClient mod menu (Right Shift): a centred window about 60% of the screen wide with a
 * header (wordmark, MODS / SETTINGS / WAYPOINTS tabs, close), a profile sidebar and the main area.
 */
public final class ModMenuScreen extends Screen implements Ui.Handler {
	// ---- action numbers for click areas ----
	static final int A_CLOSE = 1, A_TAB = 2, A_CARD = 200, A_GRID_SCROLL = 210,
			A_PROFILE = 300, A_PROFILE_EDIT = 301, A_PROFILE_DEL = 302, A_SHARE = 310, A_SAVE_NEW = 311, A_HUD_EDIT = 312,
			A_SHARE_COPY = 313, A_SHARE_PASTE = 314, A_RENAME_FIELD = 315, A_TOOL = 316,
			A_CHIP = 400, A_SEARCH = 401, A_SORT = 402, A_VIEW = 403;
	static final int SIDE_W = 230;

	static final String[] TABS = {"MODS", "SETTINGS", "WAYPOINTS"};
	private static ModMenuScreen instance;

	final Ui ui = new Ui(1F);
	private Screen parent;
	int tab;
	private final Anim openAnim = new Anim(), hClose = new Anim();
	private final Anim[] hTab = {new Anim(), new Anim(), new Anim()};
	private final Anim tabLine = new Anim();

	// mods
	private final java.util.List<ModCard> cards = new java.util.ArrayList<>();
	final java.util.List<ModCard> visible = new java.util.ArrayList<>();
	private boolean visibleDirty = true;
	private final Scroll gridScroll = new Scroll();
	private int gridTop, gridH;
	final OptionsView options = new OptionsView();
	final SettingsView settingsView = new SettingsView();
	final WaypointsView waypointsView = new WaypointsView();
	boolean inOptions;

	// filter bar
	private int filter = -2; // -2 all, -1 new, 0.. category index
	private final dev.topclient.client.top.ui.TextField search = new dev.topclient.client.top.ui.TextField("Search mods...", 40);
	private String lastSearch = "";
	private final Anim[] hChip = new Anim[ModCategories.ALL.length + 2];
	private final Anim hSort = new Anim(), hView = new Anim();
	static final String[] SORTS = {"A-Z", "FAVORITES FIRST", "ENABLED FIRST"};

	{
		for (int i = 0; i < hChip.length; i++) {
			hChip[i] = new Anim();
		}
	}

	private int sortMode() {
		return Math.floorMod(Config.getInt("top.menu.sort", 1), SORTS.length);
	}

	private boolean listView() {
		return Config.getBool("top.menu.list", false);
	}

	// profiles sidebar
	private java.util.List<TopProfiles.Profile> profiles = java.util.List.of();
	private final java.util.List<Anim> hProfile = new java.util.ArrayList<>();
	private final Anim hShare = new Anim(), hSaveNew = new Anim(), hHud = new Anim();
	private String toolTip;
	private final Anim[] hTools = {new Anim(), new Anim(), new Anim()};
	private static final String[] TOOL_ICONS = {"crosshair", "camera", "puzzle"}, TOOL_TIPS = {"Crosshair editor", "Screenshots", "Fabric mods"};
	private final dev.topclient.client.top.ui.TextField rename = new dev.topclient.client.top.ui.TextField("Profile name", TopProfiles.MAX_NAME);
	private String renaming;
	private boolean shareOpen;
	private String notice;
	private long noticeAt;

	// window geometry (design pixels), worked out every frame
	int wx, wy, ww, wh;
	static final int HEAD_H = 64;

	private ModMenuScreen() {
		super(Component.literal("TopClient"));
	}

	/** Opens the menu over the given screen (null = the game). */
	public static void open(Screen parent) {
		if (instance == null) {
			instance = new ModMenuScreen();
		}
		instance.parent = parent instanceof ModMenuScreen ? instance.parent : parent;
		instance.openAnim.v = 0F;
		UiSounds.open();
		Minecraft.getInstance().setScreenAndShow(instance);
	}

	public static boolean isOpen() {
		return Minecraft.getInstance().gui.screen() instanceof ModMenuScreen;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ClickGUI module = TopClient.INSTANCE.getModuleManager().getModule(ClickGUI.class);
		if (module != null && module.isEnabled()) {
			module.setEnabled(false); // its onDisable calls closeNow()
		} else {
			closeNow();
		}
	}

	/** Leaves the menu and goes back to where it was opened from. */
	public void closeNow() {
		UiSounds.close();
		Config.saveIfNeeded(true);
		TopClient.INSTANCE.getProfileManager().saveProfile();
		Minecraft.getInstance().setScreenAndShow(parent);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ui.begin(ctx, mouseX, mouseY);
		try {
			float open = openAnim.to(ui, true, 9F);
			Draw.alpha = open;
			// darken the game behind the window
			Draw.rect(ctx, 0, 0, ui.W, ui.H, argb(Math.round(DIM_ALPHA * open), 0x000000));

			ww = Math.max(Math.min(ui.W - 40, 960), Math.round(ui.W * 0.6F));
			ww = Math.min(ww, ui.W - 24);
			wh = Math.min(ui.H - 40, Math.max(560, Math.round(ui.H * 0.72F)));
			wx = (ui.W - ww) / 2;
			wy = (ui.H - wh) / 2 + Math.round((1F - open) * 14F);

			Draw.shadow(ctx, wx, wy, ww, wh, 14, 26, 150);
			Draw.round(ctx, wx, wy, ww, wh, 14, argb(WINDOW_ALPHA, BG));
			Draw.outline(ctx, wx, wy, ww, wh, 14, 1, argb(255, BORDER));
			toolTip = null;
			drawHeader();
			drawBody();
			if (toolTip != null) {
				Widgets.tooltip(ui, toolTip);
			}
		} finally {
			ui.end();
		}
	}

	private void drawHeader() {
		int cy = wy + HEAD_H / 2;
		Widgets.wordmark(ui, wx + 22, cy, 17);
		// tabs in the middle
		int tw = 0;
		for (String t : TABS) {
			tw += Draw.width(t, 13, true) + 36;
		}
		int tx = wx + (ww - tw) / 2;
		float lineTo = 0;
		int lineW = 0;
		for (int i = 0; i < TABS.length; i++) {
			int w = Draw.width(TABS[i], 13, true) + 36;
			boolean active = i == tab;
			float h = hTab[i].to(ui, ui.over(tx, wy + 12, w, HEAD_H - 24), 14F);
			Draw.textCenter(ui.ctx, TABS[i], tx + w / 2F, cy, 13, argb(255, active ? TEXT : mix(TEXT_MUTE, TEXT_DIM, h)), true);
			if (active) {
				lineTo = tx + 12;
				lineW = w - 24;
			}
			ui.hit(tx, wy + 12, w, HEAD_H - 24, A_TAB, i);
			tx += w;
		}
		if (tabLine.v == 0) {
			tabLine.v = lineTo;
		}
		float lx = tabLine.to(ui, lineTo, 16F);
		Draw.round(ui.ctx, Math.round(lx), wy + HEAD_H - 14, lineW, 3, 1, argb(255, ACCENT));
		// close
		int s = 30, cx = wx + ww - s - 16;
		float hc = hClose.to(ui, ui.over(cx, cy - s / 2, s, s), 14F);
		Draw.round(ui.ctx, cx, cy - s / 2, s, s, 8, argb(Math.round(200 * hc), mix(CARD_HOVER, DISABLED, 0.35F)));
		Icons.drawCentered(ui.ctx, Icons.ui("close"), cx + s / 2F, cy, 16, argb(255, mix(TEXT_MUTE, TEXT, hc)));
		ui.hit(cx, cy - s / 2, s, s, A_CLOSE, 0);
		Draw.rect(ui.ctx, wx + 1, wy + HEAD_H, ww - 2, 1, argb(255, BORDER));
	}

	private void drawBody() {
		int x = wx, y = wy + HEAD_H + 1, w = ww, h = wh - HEAD_H - 1;
		if (tab == 0) {
			drawSidebar(x, y, SIDE_W, h);
			Draw.rect(ui.ctx, x + SIDE_W, y, 1, h, argb(255, BORDER));
			if (inOptions) {
				options.draw(ui, x + SIDE_W + 1, y, w - SIDE_W - 1, h);
			} else {
				drawGrid(x + SIDE_W + 1, y, w - SIDE_W - 1, h);
			}
		} else if (tab == 1) {
			settingsView.draw(ui, x, y, w, h);
		} else {
			waypointsView.draw(ui, x, y, w, h);
		}
	}

	// ------------------------------------------------------------------ profiles sidebar

	private void drawSidebar(int x, int y, int w, int h) {
		profiles = TopProfiles.list();
		while (hProfile.size() < profiles.size()) {
			hProfile.add(new Anim());
		}
		String active = TopProfiles.active();
		Draw.text(ui.ctx, "PROFILES", x + 18, y + 14, 11, argb(255, TEXT_MUTE), true);
		int ry = y + 36, rh = 40;
		int bottomButtons = 3 * 38 + 16 + 46 + (shareOpen ? 76 : 0);
		int maxRows = Math.max(1, (h - 36 - bottomButtons - 8) / (rh + 4));
		for (int i = 0; i < profiles.size() && i < maxRows; i++) {
			TopProfiles.Profile p = profiles.get(i);
			boolean on = p.id().equals(active);
			int rx = x + 10, rw = w - 20;
			boolean over = ui.over(rx, ry, rw, rh);
			float hv = hProfile.get(i).to(ui, over, 14F);
			int fill = on ? mix(PANEL2, ACCENT, 0.2F) : mix(PANEL, CARD_HOVER, hv);
			Draw.round(ui.ctx, rx, ry, rw, rh, 8, argb(on ? 255 : Math.round(140 + 100 * hv), fill));
			if (on) {
				Draw.outline(ui.ctx, rx, ry, rw, rh, 8, 1, argb(255, ACCENT));
				Draw.round(ui.ctx, rx, ry + 9, 3, rh - 18, 1, argb(255, ACCENT));
			}
			// icon: first letter in a tile
			Draw.round(ui.ctx, rx + 8, ry + 8, 24, 24, 6, argb(255, on ? ACCENT : CARD_HOVER));
			String letter = p.name().isEmpty() ? "?" : p.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
			Draw.textCenter(ui.ctx, letter, rx + 20, ry + 20, 12, argb(255, on ? ON_ACCENT : TEXT_DIM), true);
			if (p.id().equals(renaming)) {
				rename.focused = true;
				rename.draw(ui, rx + 38, ry + 6, rw - 44, 28, null, A_RENAME_FIELD, i);
			} else {
				Draw.textMid(ui.ctx, Draw.fit(p.name(), 13, rw - 96, on), rx + 40, ry + rh / 2F, 13, argb(255, on ? TEXT : TEXT_DIM), on);
				ui.hit(rx, ry, rw, rh, A_PROFILE, i);
				if (over || on) {
					int px = rx + rw - 28;
					Icons.drawCentered(ui.ctx, Icons.ui("pencil"), px + 10, ry + rh / 2F, 14, argb(255, ui.over(px, ry + 8, 22, 24) ? ACCENT : TEXT_MUTE));
					ui.hit(px, ry + 8, 22, 24, A_PROFILE_EDIT, i);
					if (!on && over) {
						int dx = px - 24;
						Icons.drawCentered(ui.ctx, Icons.ui("trash"), dx + 10, ry + rh / 2F, 14, argb(255, ui.over(dx, ry + 8, 22, 24) ? DISABLED : TEXT_MUTE));
						ui.hit(dx, ry + 8, 22, 24, A_PROFILE_DEL, i);
					}
				}
			}
			ry += rh + 4;
		}
		// bottom buttons
		int bx = x + 12, bw = w - 24, by = y + h - bottomButtons;
		// quick tools: crosshair editor, screenshots, fabric mods
		int ts = 36, tg = (bw - ts * 3) / 2;
		for (int i = 0; i < 3; i++) {
			int tx = bx + i * (ts + tg);
			Widgets.iconButton(ui, tx, by, ts, Icons.ui(TOOL_ICONS[i]), false, hTools[i].to(ui, ui.over(tx, by, ts, ts), 14F), A_TOOL, i);
			if (ui.over(tx, by, ts, ts)) {
				toolTip = TOOL_TIPS[i];
			}
		}
		by += 46;
		if (shareOpen) {
			Widgets.button(ui, bx, by, bw, 32, "COPY PROFILE CODE", false, ui.over(bx, by, bw, 32) ? 1F : 0F, A_SHARE_COPY, 0);
			Widgets.button(ui, bx, by + 38, bw, 32, "PASTE CODE AS NEW", false, ui.over(bx, by + 38, bw, 32) ? 1F : 0F, A_SHARE_PASTE, 0);
			by += 76;
		}
		Widgets.button(ui, bx, by, bw, 32, "PROFILE SHARING", shareOpen, hShare.to(ui, ui.over(bx, by, bw, 32), 14F), A_SHARE, 0);
		Widgets.button(ui, bx, by + 38, bw, 32, "SAVE AS NEW PROFILE", false, hSaveNew.to(ui, ui.over(bx, by + 38, bw, 32), 14F), A_SAVE_NEW, 0);
		Widgets.button(ui, bx, by + 76, bw, 32, "EDIT HUD LAYOUT", true, hHud.to(ui, ui.over(bx, by + 76, bw, 32), 14F), A_HUD_EDIT, 0);
		if (notice != null && System.currentTimeMillis() - noticeAt < 3500) {
			Draw.textCenter(ui.ctx, Draw.fit(notice, 11.5F, w - 16), x + w / 2F, by - 12, 11.5F, argb(255, ACCENT));
		}
	}

	private void say(String text) {
		notice = text;
		noticeAt = System.currentTimeMillis();
	}

	private void finishRename() {
		if (renaming != null) {
			TopProfiles.rename(renaming, rename.text);
			renaming = null;
			rename.focused = false;
		}
	}

	private void profileAction(int action, int arg) {
		if (action != A_RENAME_FIELD) {
			finishRename();
		}
		TopProfiles.Profile p = arg >= 0 && arg < profiles.size() ? profiles.get(arg) : null;
		Minecraft mc = Minecraft.getInstance();
		switch (action) {
			case A_PROFILE -> {
				if (p != null && !p.id().equals(TopProfiles.active())) {
					TopProfiles.switchTo(p.id());
					inOptions = false;
					say("Switched to " + p.name());
				}
			}
			case A_PROFILE_EDIT -> {
				if (p != null) {
					renaming = p.id();
					rename.text = p.name();
					rename.focused = true;
				}
			}
			case A_PROFILE_DEL -> {
				if (p != null && TopProfiles.delete(p.id())) {
					say("Deleted " + p.name());
				}
			}
			case A_SHARE -> shareOpen = !shareOpen;
			case A_SHARE_COPY -> {
				mc.keyboardHandler.setClipboard(TopProfiles.shareCode(TopProfiles.active()));
				say("Code copied: send it to a friend");
			}
			case A_SHARE_PASTE -> {
				String name = TopProfiles.importCode(mc.keyboardHandler.getClipboard());
				say(name == null ? "Copy a TopClient profile code first" : "Added profile " + name);
			}
			case A_SAVE_NEW -> {
				String id = TopProfiles.saveAsNew("");
				renaming = id;
				rename.text = TopProfiles.nameOf(id);
				rename.focused = true;
			}
			case A_HUD_EDIT -> mc.setScreenAndShow(new dev.topclient.client.clover.screen.HudEditorScreen(this));
			case A_TOOL -> {
				UiSounds.click();
				switch (arg) {
					case 0 -> mc.setScreenAndShow(new CrosshairScreen(this));
					case 1 -> mc.setScreenAndShow(new ScreenshotsScreen(this));
					default -> mc.setScreenAndShow(new FabricModsScreen(this));
				}
			}
			default -> {
			}
		}
		UiSounds.click();
	}

	// ------------------------------------------------------------------ mods grid

	/** Every mod the menu shows, built once. Older modules come through the same adapters as the classic menu. */
	private void buildCards() {
		if (!cards.isEmpty()) {
			return;
		}
		for (dev.topclient.client.clover.module.Module m : dev.topclient.client.clover.module.ModuleManager.all()) {
			GuiModule g = new CloverAdapter(m);
			cards.add(new ModCard(g, ModCategories.of(g), false));
		}
		for (dev.topclient.client.module.Module m : TopClient.INSTANCE.getModuleManager().getModules()) {
			if (m instanceof ClickGUI || m instanceof dev.topclient.client.module.modules.client.Friends) {
				continue; // menu options live in SETTINGS; the friend list isn't a mod
			}
			GuiModule g = new CoreAdapter(m);
			cards.add(new ModCard(g, ModCategories.of(g), false));
		}
		GuiModule visuals = OptionGroups.visuals();
		cards.add(new ModCard(visuals, ModCategories.of(visuals), false));
		for (GuiModule locked : LockedMods.all()) {
			cards.add(new ModCard(locked, ModCategories.of(locked), true));
		}
		cards.sort((a, b) -> a.module.name().compareToIgnoreCase(b.module.name()));
	}

	private void refreshVisible() {
		buildCards();
		if (!visibleDirty) {
			return;
		}
		visibleDirty = false;
		visible.clear();
		String q = search.text.strip().toLowerCase(java.util.Locale.ROOT);
		for (ModCard c : cards) {
			if (filter == -1 && !(c.module.isNew() || ModCategories.isNew(c.module))) {
				continue;
			}
			if (filter >= 0 && !c.category.equals(ModCategories.ALL[filter])) {
				continue;
			}
			if (!q.isEmpty() && !c.module.name().toLowerCase(java.util.Locale.ROOT).contains(q)
					&& !c.module.description().toLowerCase(java.util.Locale.ROOT).contains(q)
					&& !c.category.toLowerCase(java.util.Locale.ROOT).contains(q)) {
				continue;
			}
			visible.add(c);
		}
		switch (sortMode()) {
			case 1 -> visible.sort(FAV_FIRST);
			case 2 -> visible.sort(ENABLED_FIRST);
			default -> {
			}
		}
	}

	private static final java.util.Comparator<ModCard> FAV_FIRST = (a, b) -> {
		boolean fa = Favorites.has(a.module), fb = Favorites.has(b.module);
		return fa == fb ? a.module.name().compareToIgnoreCase(b.module.name()) : fa ? -1 : 1;
	};
	private static final java.util.Comparator<ModCard> ENABLED_FIRST = (a, b) -> {
		boolean ea = a.state() == ModCard.State.ENABLED, eb = b.state() == ModCard.State.ENABLED;
		return ea == eb ? a.module.name().compareToIgnoreCase(b.module.name()) : ea ? -1 : 1;
	};

	private void markDirty() {
		visibleDirty = true;
	}

	/** Search, sort and view on the first row, category chips on the second. Returns the height used. */
	private int drawFilterBar(int x, int y, int w) {
		if (!search.text.equals(lastSearch)) {
			lastSearch = search.text;
			markDirty();
			gridScroll.reset();
		}
		int pad = 18;
		int sx = x + pad, sw = Math.min(320, w - pad * 2 - 90);
		search.draw(ui, sx, y + 12, sw, 32, Icons.ui("search"), A_SEARCH, 0);
		int ix = x + w - pad - 32;
		Widgets.iconButton(ui, ix, y + 12, 32, Icons.ui(listView() ? "grid" : "list"), false, hView.to(ui, ui.over(ix, y + 12, 32, 32), 14F), A_VIEW, 0);
		int sortW = Draw.width(SORTS[sortMode()], 11, true) + 46;
		int ox = ix - 8 - sortW;
		float hs = hSort.to(ui, ui.over(ox, y + 12, sortW, 32), 14F);
		Widgets.panel(ui, ox, y + 12, sortW, 32, Widgets.R, hs);
		Icons.drawCentered(ui.ctx, Icons.ui("sort"), ox + 16, y + 28, 14, argb(255, ACCENT));
		Draw.textMid(ui.ctx, SORTS[sortMode()], ox + 30, y + 28, 11, argb(255, mix(TEXT_DIM, TEXT, hs)), true);
		ui.hit(ox, y + 12, sortW, 32, A_SORT, 0);
		// chips
		int cx = x + pad, cy = y + 54, ch = 26;
		for (int i = -2; i < ModCategories.ALL.length; i++) {
			String label = i == -2 ? "ALL" : i == -1 ? "NEW" : ModCategories.ALL[i].toUpperCase(java.util.Locale.ROOT);
			int cw = Widgets.chipWidth(label);
			if (cx + cw > x + w - pad) {
				cx = x + pad;
				cy += ch + 8;
			}
			Widgets.chip(ui, cx, cy, ch, label, filter == i, hChip[i + 2].to(ui, ui.over(cx, cy, cw, ch), 14F), A_CHIP, i);
			cx += cw + 8;
		}
		return cy + ch + 8 - y;
	}

	private void drawGrid(int x, int y, int w, int h) {
		int bar = drawFilterBar(x, y, w);
		refreshVisible();
		boolean list = listView();
		int pad = 18, gap = list ? 6 : 12, cols = list ? 1 : 3;
		gridTop = y + bar + 4;
		gridH = h - bar - 4;
		int gx = x + pad, gw = w - pad * 2 - 10;
		int cw = (gw - gap * (cols - 1)) / cols, ch = list ? 52 : 168;
		gridScroll.update(ui);
		ui.clip(x, gridTop, w, gridH);
		int top = gridTop + 4 - Math.round(gridScroll.pos);
		for (int i = 0; i < visible.size(); i++) {
			int cx = gx + (i % cols) * (cw + gap);
			int cy = top + (i / cols) * (ch + gap);
			if (cy + ch < gridTop || cy > gridTop + gridH) {
				continue;
			}
			ModCard c = visible.get(i);
			if (list) {
				c.drawRow(ui, cx, cy, cw, ch, A_CARD, i, Favorites.has(c.module));
			} else {
				c.draw(ui, cx, cy, cw, ch, A_CARD, i, Favorites.has(c.module));
			}
		}
		ui.unclip();
		int rows = (visible.size() + cols - 1) / cols;
		int content = rows * (ch + gap) + 8;
		gridScroll.setMax(content - gridH);
		Widgets.scrollbar(ui, x + w - 12, gridTop + 4, gridH - 12, gridScroll.pos, gridScroll.max, gridH, A_GRID_SCROLL, 0);
		if (visible.isEmpty()) {
			Draw.textCenter(ui.ctx, "No mods match.", x + w / 2F, y + h / 2F, 14, argb(255, TEXT_MUTE), true);
		}
	}

	private void cardAction(int which, int index, int button) {
		if (index < 0 || index >= visible.size()) {
			return;
		}
		ModCard c = visible.get(index);
		switch (which) {
			case ModCard.A_TOGGLE -> {
				if (c.state() == ModCard.State.ENABLED || c.state() == ModCard.State.DISABLED) {
					c.module.setEnabled(!c.module.enabled());
					if (sortMode() == 2) {
						markDirty();
					}
				}
			}
			case ModCard.A_STAR -> {
				Favorites.toggle(c.module);
				markDirty();
				UiSounds.click();
			}
			case ModCard.A_OPTIONS -> {
				if (c.module.settings().isEmpty() && c.module.onOpen() != null) {
					c.module.onOpen().run();
				} else {
					options.open(c.module);
					inOptions = true;
				}
				UiSounds.click();
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public void onClick(int action, int arg, int button) {
		if (action >= OptionsView.A_BACK && action <= OptionsView.A_HUD) {
			if (options.click(action, arg, button)) {
				inOptions = false;
			}
			return;
		}
		if (action >= WaypointsView.A_ADD && action <= WaypointsView.A_LAST) {
			waypointsView.click(action, arg);
			return;
		}
		waypointsView.finishRename();
		if (action >= SettingsView.A_PRESET && action <= SettingsView.A_LAST) {
			settingsView.click(action, arg, button);
			return;
		}
		if (action >= A_PROFILE && action <= A_RENAME_FIELD) {
			profileAction(action, arg);
			return;
		}
		finishRename();
		if (action != A_SEARCH) {
			search.focused = false;
		}
		switch (action) {
			case A_CHIP -> {
				filter = arg;
				markDirty();
				gridScroll.reset();
				UiSounds.click();
				return;
			}
			case A_SEARCH -> {
				search.focused = true;
				return;
			}
			case A_SORT -> {
				Config.setInt("top.menu.sort", (sortMode() + (button == 1 ? SORTS.length - 1 : 1)) % SORTS.length);
				markDirty();
				UiSounds.click();
				return;
			}
			case A_VIEW -> {
				Config.setBool("top.menu.list", !listView());
				gridScroll.reset();
				UiSounds.click();
				return;
			}
			default -> {
			}
		}
		if (action >= A_CARD && action < A_CARD + 3) {
			cardAction(action - A_CARD, arg, button);
			return;
		}
		switch (action) {
			case A_CLOSE -> onClose();
			case A_TAB -> {
				if (tab != arg) {
					tab = arg;
					search.focused = false;
					UiSounds.click();
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void onDrag(int action, int arg, double mx, double my) {
		if (action == A_GRID_SCROLL) {
			gridScroll.jump(Widgets.scrollFor(my, gridTop + 4, gridH - 12, gridScroll.max, gridH));
		} else if (action >= OptionsView.A_BACK && action <= OptionsView.A_HUD) {
			options.drag(ui, action, arg, mx, my);
		} else if (action >= SettingsView.A_PRESET && action <= SettingsView.A_LAST) {
			settingsView.drag(action, arg, mx, my);
		} else if (action == WaypointsView.A_SCROLL) {
			waypointsView.drag(my);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (inOptions && options.mouseButtonAsKey(click.button())) {
			return true;
		}
		ui.click(ui.toDesign(click.x()), ui.toDesign(click.y()), click.button(), this);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
		if (tab == 2) {
			waypointsView.wheel(v);
		} else if (tab == 1) {
			settingsView.wheel(v);
		} else if (tab == 0 && inOptions) {
			options.wheel(v);
		} else if (tab == 0) {
			gridScroll.wheel(v);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		return ui.drag(ui.toDesign(click.x()), ui.toDesign(click.y()), this) || super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (ui.release(this)) {
			Config.saveIfNeeded(false);
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (renaming != null) {
			rename.key(input.key(), input.modifiers(), input.isPaste());
			if (!rename.focused) {
				if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
					renaming = null;
				} else {
					finishRename();
				}
			}
			return true;
		}
		if (tab == 2 && waypointsView.key(input.key(), input.modifiers(), input.isPaste())) {
			return true;
		}
		if (tab == 1 && settingsView.key(input.key(), input.modifiers(), input.isPaste())) {
			return true;
		}
		if (inOptions && options.key(input.key(), input.modifiers(), input.isPaste())) {
			return true;
		}
		if (!inOptions && tab == 0 && search.focused) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE && !search.text.isEmpty()) {
				search.text = "";
				return true;
			}
			search.key(input.key(), input.modifiers(), input.isPaste());
			return true;
		}
		if (!inOptions && tab == 0 && input.key() == GLFW.GLFW_KEY_F && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			search.focused = true;
			return true;
		}
		if (input.key() == GLFW.GLFW_KEY_ESCAPE && inOptions) {
			inOptions = false;
			return true;
		}
		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (!input.isAllowedChatCharacter()) {
			return false;
		}
		if (renaming != null) {
			rename.type(input.codepointAsString());
			return true;
		}
		if (tab == 1) {
			return settingsView.type(input.codepointAsString());
		}
		if (tab == 2) {
			return waypointsView.type(input.codepointAsString());
		}
		if (inOptions) {
			return options.type(input.codepointAsString());
		}
		if (tab == 0) {
			String t = input.codepointAsString();
			if (!search.focused && t.isBlank()) {
				return false;
			}
			search.focused = true; // just start typing to search
			search.type(t);
			return true;
		}
		return false;
	}

	/** True while a text field has the keyboard (so Right Shift types instead of closing). */
	public boolean isTyping() {
		return renaming != null || search.focused || (inOptions && options.isTyping()) || (tab == 1 && settingsView.isTyping()) || (tab == 2 && waypointsView.isTyping());
	}
}
