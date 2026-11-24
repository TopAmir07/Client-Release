package dev.topclient.client.top;

import com.mojang.blaze3d.platform.NativeImage;
import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.TextField;
import dev.topclient.client.top.ui.Widgets;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.topclient.client.top.TopTheme.*;

/**
 * Every Fabric mod that is really loaded, with its icon, description and version. Mods that ship
 * inside another mod (Fabric API's parts) are counted under that mod, so nothing shows twice.
 * TopClient and the game itself are marked as built in. Mods are loaded when the game starts, so
 * this list shows them as loaded; to turn one off you remove it from the mods folder.
 */
public final class FabricModsScreen extends TopScreen {
	private static final int A_ROW = 1, A_CONFIG = 2, A_FILTER = 3, A_SEARCH = 4, A_FOLDER = 5, A_SITE = 6, A_SCROLL = 7;
	private static final String[] FILTERS = {"All", "Your mods", "Built in"};

	private record Entry(ModContainer mod, String id, String name, String version, String desc, String authors, String license,
						 String site, int children, boolean builtIn) {
	}

	private final List<Entry> all = new ArrayList<>();
	private final Map<String, Identifier> icons = new HashMap<>();
	private final Map<String, Boolean> hasConfig = new HashMap<>();
	private final List<Anim> hovers = new ArrayList<>(), cfgHovers = new ArrayList<>();
	private final Anim[] hBtn = {new Anim(), new Anim(), new Anim()};
	private final TextField search = new TextField("Search mods", 40);
	private int filter;
	private String open;
	private List<Entry> shown = List.of();

	public FabricModsScreen(Screen parent) {
		super(parent, "Fabric Mods", "Every mod loaded right now. Built-in TopClient parts are marked.");
		load();
	}

	private void load() {
		Map<String, Integer> children = new HashMap<>();
		for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
			c.getContainingMod().ifPresent(p -> children.merge(p.getMetadata().getId(), 1, Integer::sum));
		}
		for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
			if (c.getContainingMod().isPresent()) {
				continue; // shown as part of its parent mod
			}
			ModMetadata m = c.getMetadata();
			String id = m.getId();
			boolean builtIn = id.equals(CloverClient.MOD_ID) || id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")
					|| id.equals("mixinextras");
			String authors = m.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
			String site = m.getContact().get("homepage").or(() -> m.getContact().get("sources")).orElse("");
			String license = String.join(", ", m.getLicense());
			all.add(new Entry(c, id, m.getName(), m.getVersion().getFriendlyString(), m.getDescription(), authors, license, site,
					children.getOrDefault(id, 0), builtIn));
		}
		all.sort((a, b) -> {
			if (a.builtIn() != b.builtIn()) {
				return a.builtIn() ? 1 : -1;
			}
			return a.name().compareToIgnoreCase(b.name());
		});
		loadIcons();
	}

	private void loadIcons() {
		List<Entry> copy = new ArrayList<>(all);
		Thread t = new Thread(() -> {
			for (Entry e : copy) {
				try {
					Optional<String> p = e.mod().getMetadata().getIconPath(64);
					if (p.isEmpty()) {
						continue;
					}
					Optional<Path> file = e.mod().findPath(p.get());
					if (file.isEmpty()) {
						continue;
					}
					NativeImage img;
					try (InputStream in = Files.newInputStream(file.get())) {
						img = NativeImage.read(in);
					}
					Minecraft.getInstance().execute(() -> {
						Identifier id = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "modicon/" + e.id().replaceAll("[^a-z0-9_.-]", "_"));
						try {
							Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, img));
							icons.put(e.id(), id);
						} catch (RuntimeException ex) {
							img.close();
						}
					});
				} catch (Throwable ignored) {
					// no icon: a letter is shown instead
				}
			}
		}, "TopClient mod icons");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void removed() {
		super.removed();
		for (Identifier id : icons.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		icons.clear();
	}

	@Override
	public void init() {
		super.init();
		if (icons.isEmpty() && !all.isEmpty()) {
			loadIcons();
		}
	}

	@Override
	protected int prefW() {
		return 1100;
	}

	@Override
	protected int prefH() {
		return 760;
	}

	@Override
	protected void drawHeaderExtras(int x, int right, int cy) {
		int bx = right - smallButtonWidth("Open mods folder");
		smallButton(bx, cy - 17, 34, "Open mods folder", false, hBtn[0], A_FOLDER, 0);
	}

	// ------------------------------------------------------------------ list

	@Override
	protected void drawBody(int x, int y, int w, int h) {
		int pad = 22;
		// search + filters
		search.draw(ui, x + pad, y + 16, 300, 36, Icons.ui("search"), A_SEARCH, 0);
		int fx = x + pad + 312;
		for (int i = 0; i < FILTERS.length; i++) {
			fx += Widgets.chip(ui, fx, y + 21, 26, FILTERS[i], filter == i, ui.over(fx, y + 21, Widgets.chipWidth(FILTERS[i]), 26) ? 1F : 0F, A_FILTER, i) + 6;
		}
		String q = search.text.toLowerCase(Locale.ROOT).trim();
		shown = all.stream().filter(e -> filter == 0 || (filter == 1) != e.builtIn())
				.filter(e -> q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q) || e.id().contains(q) || e.desc().toLowerCase(Locale.ROOT).contains(q))
				.toList();
		long external = all.stream().filter(e -> !e.builtIn()).count();
		Draw.textRight(ui.ctx, external + " of your mods  ·  " + all.size() + " total", x + w - pad, y + 34, 11.5F, argb(255, TEXT_MUTE));
		int listY = y + 66, listH = h - 66 - 40;
		while (hovers.size() < all.size()) {
			hovers.add(new Anim());
			cfgHovers.add(new Anim());
		}
		int rh = 66, openH = 92;
		int total = 0;
		for (Entry e : shown) {
			total += rh + 8 + (e.id().equals(open) ? openH : 0);
		}
		scroll.setMax(total - listH + 8);
		scroll.update(ui);
		ui.clip(x, listY, w, listH);
		int ry = listY - Math.round(scroll.pos);
		for (int i = 0; i < shown.size(); i++) {
			Entry e = shown.get(i);
			boolean isOpen = e.id().equals(open);
			int fullH = rh + (isOpen ? openH : 0);
			if (ui.visible(ry, fullH)) {
				drawRow(e, i, x + pad, ry, w - pad * 2 - 10, rh, isOpen, openH);
			}
			ry += fullH + 8;
		}
		ui.unclip();
		if (shown.isEmpty()) {
			Draw.textCenter(ui.ctx, "No mods match your search", x + w / 2F, listY + 60, 13, argb(255, TEXT_MUTE), true);
		}
		Widgets.scrollbar(ui, x + w - 10, listY, listH, scroll.pos, scroll.max, listH, A_SCROLL, 0);
		Draw.textCenter(ui.ctx, "Fabric loads mods when the game starts. To turn one off, remove it from the mods folder (or switch it off in the launcher) and restart.",
				x + w / 2F, y + h - 20, 11, argb(255, TEXT_MUTE));
	}

	private void drawRow(Entry e, int i, int x, int y, int w, int rh, boolean isOpen, int openH) {
		boolean over = ui.over(x, y, w, rh);
		float hv = hovers.get(Math.min(i, hovers.size() - 1)).to(ui, over || isOpen, 14F);
		int h = rh + (isOpen ? openH : 0);
		Draw.round(ui.ctx, x, y, w, h, 12, argb(235, mix(PANEL2, CARD_HOVER, hv)));
		Draw.outline(ui.ctx, x, y, w, h, 12, 1, argb(255, isOpen ? ACCENT : mix(BORDER, BORDER_HOVER, hv)));
		// icon
		int is = 44, ix = x + 12, iy = y + (rh - is) / 2;
		Identifier icon = icons.get(e.id());
		if (icon != null) {
			Draw.round(ui.ctx, ix, iy, is, is, 9, argb(255, 0x101014));
			ui.ctx.blit(RenderPipelines.GUI_TEXTURED, icon, ix + 2, iy + 2, 0F, 0F, is - 4, is - 4, 64, 64, 64, 64, Draw.fade(0xFFFFFFFF));
		} else {
			Draw.roundGradient(ui.ctx, ix, iy, is, is, 9, argb(255, mix(ACCENT, 0x000000, 0.35F)), argb(255, mix(ACCENT2, 0x000000, 0.55F)));
			String letter = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
			Draw.textCenter(ui.ctx, letter, ix + is / 2F, iy + is / 2F, 20, argb(255, 0xFFFFFF), true);
		}
		int tx = ix + is + 14;
		Draw.text(ui.ctx, Draw.fit(e.name(), 14, w - 360), tx, y + 14, 14, argb(255, TEXT), true);
		int nw = Math.min(Draw.width(e.name(), 14, true), w - 360);
		Draw.text(ui.ctx, e.version(), tx + nw + 10, y + 17, 11, argb(255, TEXT_MUTE));
		String desc = e.desc().isBlank() ? "No description" : e.desc().replace('\n', ' ');
		Draw.text(ui.ctx, Draw.fit(desc, 11.5F, w - tx + x - 230), tx, y + 38, 11.5F, argb(255, TEXT_DIM));
		ui.hit(x, y, w, rh, A_ROW, i);
		// right side: tag + configure
		int rx = x + w - 14;
		boolean cfg = canConfigure(e);
		if (cfg) {
			int bw = smallButtonWidth("Configure");
			rx -= bw;
			smallButton(rx, y + (rh - 32) / 2, 32, "Configure", e.id().equals(CloverClient.MOD_ID), cfgHovers.get(Math.min(i, cfgHovers.size() - 1)), A_CONFIG, i);
			rx -= 10;
		}
		String tag = e.builtIn() ? (e.id().equals(CloverClient.MOD_ID) ? "TopClient" : "Game") : "Loaded";
		int tw = Draw.width(tag, 10.5F, true) + 18;
		rx -= tw;
		int tagCol = e.builtIn() ? ACCENT : ENABLED;
		Draw.round(ui.ctx, rx, y + (rh - 22) / 2, tw, 22, 11, argb(60, tagCol));
		Draw.textCenter(ui.ctx, tag, rx + tw / 2F, y + rh / 2F, 10.5F, argb(255, mix(tagCol, 0xFFFFFF, 0.3F)), true);
		if (isOpen) {
			int dy = y + rh;
			Draw.rect(ui.ctx, x + 12, dy, w - 24, 1, argb(255, BORDER));
			col(x + 14, dy + 14, "ID", e.id());
			col(x + 14 + (w - 28) / 4, dy + 14, "Authors", e.authors().isBlank() ? "Unknown" : e.authors());
			col(x + 14 + (w - 28) / 2, dy + 14, "Licence", e.license().isBlank() ? "Not given" : e.license());
			col(x + 14 + (w - 28) * 3 / 4, dy + 14, "Includes", e.children() == 0 ? "Nothing else" : e.children() + " built-in parts");
			if (e.site().startsWith("https://") || e.site().startsWith("http://")) {
				int bw = smallButtonWidth("Website");
				smallButton(x + w - 14 - bw, dy + 46, 32, "Website", false, hBtn[0], A_SITE, i);
			}
			Draw.text(ui.ctx, Draw.fit(desc, 11.5F, w - 160), x + 14, dy + 58, 11.5F, argb(255, TEXT_MUTE));
		}
	}

	private void col(int x, int y, String label, String value) {
		Draw.text(ui.ctx, label.toUpperCase(Locale.ROOT), x, y, 10, argb(255, TEXT_MUTE), true);
		Draw.text(ui.ctx, Draw.fit(value, 12, 230), x, y + 15, 12, argb(255, TEXT));
	}

	// ------------------------------------------------------------------ Mod Menu bridge (only if Mod Menu is installed)

	private boolean canConfigure(Entry e) {
		if (e.id().equals(CloverClient.MOD_ID)) {
			return true;
		}
		if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
			return false;
		}
		return hasConfig.computeIfAbsent(e.id(), id -> configScreen(id) != null);
	}

	private Screen configScreen(String id) {
		try {
			Class<?> c = Class.forName("com.terraformersmc.modmenu.ModMenu");
			Object s = c.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, id, this);
			return s instanceof Screen sc ? sc : null;
		} catch (Throwable t) {
			return null;
		}
	}

	// ------------------------------------------------------------------ actions

	@Override
	protected void onAction(int action, int arg, int button) {
		if (action != A_SEARCH) {
			search.focused = false;
		}
		switch (action) {
			case A_ROW -> {
				if (arg < shown.size()) {
					String id = shown.get(arg).id();
					open = id.equals(open) ? null : id;
					UiSounds.click();
				}
			}
			case A_CONFIG -> {
				if (arg < shown.size()) {
					Entry e = shown.get(arg);
					UiSounds.click();
					if (e.id().equals(CloverClient.MOD_ID)) {
						ModMenuScreen.open(this);
					} else {
						Screen s = configScreen(e.id());
						if (s != null) {
							Minecraft.getInstance().gui.setScreen(s);
						}
					}
				}
			}
			case A_FILTER -> filter = arg;
			case A_SEARCH -> search.focused = true;
			case A_FOLDER -> {
				Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
				Util.getPlatform().openPath(mods);
			}
			case A_SITE -> {
				if (arg < shown.size()) {
					String site = shown.get(arg).site();
					if (site.startsWith("https://") || site.startsWith("http://")) {
						Util.getPlatform().openUri(site);
					}
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void onDrag(int action, int arg, double mx, double my) {
		if (action == A_SCROLL) {
			scroll.jump(Widgets.scrollFor(my, wy + HEAD_H + 67, wh - HEAD_H - 107, scroll.max, wh - HEAD_H - 106));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (search.focused) {
			search.key(input.key(), input.modifiers(), input.isPaste());
			scroll.reset();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (!input.isAllowedChatCharacter()) {
			return false;
		}
		String t = input.codepointAsString();
		if (!search.focused && t.isBlank()) {
			return false;
		}
		search.focused = true;
		search.type(t);
		scroll.reset();
		return true;
	}
}
