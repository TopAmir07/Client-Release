package dev.topclient.client.gui;

import dev.topclient.client.TopClient;
import dev.topclient.client.clover.module.ModuleManager;
import dev.topclient.client.gui.model.CoreAdapter;
import dev.topclient.client.gui.model.CloverAdapter;
import dev.topclient.client.gui.model.GuiModule;
import dev.topclient.client.gui.model.OptionGroups;
import dev.topclient.client.module.Category;
import dev.topclient.client.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Page {
	public enum Kind { MODULES, CAPES }

	public final String id;
	public final String title;
	public final String subtitle;
	public final String group;
	public final char glyph;
	public final Kind kind;
	public final boolean clover;
	private final Supplier<List<GuiModule>> source;
	private List<GuiModule> cache;
	/** All Modules / Favorites / Enabled: lists worked out fresh each time, not their own category. */
	public boolean virtual;

	private Page(String id, String title, String subtitle, String group, char glyph, Kind kind, boolean clover, Supplier<List<GuiModule>> source) {
		this.id = id;
		this.title = title;
		this.subtitle = subtitle;
		this.group = group;
		this.glyph = glyph;
		this.kind = kind;
		this.clover = clover;
		this.source = source;
	}

	public List<GuiModule> modules() {
		if (virtual) {
			return source.get();
		}
		if (cache == null) {
			cache = source.get();
		}
		return cache;
	}

	public int enabledCount() {
		int n = 0;
		for (GuiModule m : modules()) {
			if (m.toggleable() && m.enabled()) {
				n++;
			}
		}
		return n;
	}

	public static final String CLOVER_GROUP = "Clover";

	public static List<Page> build() {
		List<Page> pages = new ArrayList<>();
		// the three lists at the top of the sidebar
		Page all = new Page("all", "All Modules", "Every module in TopClient", "Menu", '*', Kind.MODULES, false, () -> allModules(null));
		Page fav = new Page("favorites", "Favorites", "Modules you marked with the heart", "Menu", '*', Kind.MODULES, false, () -> allModules(Favorites::has));
		Page on = new Page("enabled", "Enabled", "Everything that is switched on", "Menu", '*', Kind.MODULES, false,
				() -> allModules(m -> m.toggleable() && m.enabled()));
		all.virtual = fav.virtual = on.virtual = true;
		pages.add(all);
		pages.add(fav);
		pages.add(on);
		// categories (the old Player and Render pages now live inside HUD)
		pages.add(clover(dev.topclient.client.clover.module.Category.HUD, "Info boxes on your screen. Drag them anywhere in the HUD editor", 'H'));
		pages.add(clover(dev.topclient.client.clover.module.Category.VISUAL, "Camera, crosshair, outlines, time and screen effects", 'V'));
		pages.add(clover(dev.topclient.client.clover.module.Category.UTILITY, "Quality-of-life helpers that never touch the server", 'U'));
		pages.add(new Page("clover:capes", "Capes", "Pick a cape for your player. Only you can see it", CLOVER_GROUP, 'C', Kind.CAPES, true, List::of));
		pages.add(new Page("customize", "Customize", "Themes, sounds, background, window, cards and text", "System", 'P', Kind.MODULES, false,
				OptionGroups::customize));
		pages.add(core(Category.CLIENT));
		PAGES = pages;
		return pages;
	}

	private static List<Page> PAGES = List.of();

	/** Every module of every real page, each once (optionally only the ones matching the test). */
	private static List<GuiModule> allModules(java.util.function.Predicate<GuiModule> keep) {
		List<GuiModule> out = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Page p : PAGES) {
			if (p.virtual || p.kind != Kind.MODULES) {
				continue;
			}
			for (GuiModule m : p.modules()) {
				if (seen.add(m.id()) && (keep == null || keep.test(m))) {
					out.add(m);
				}
			}
		}
		return out;
	}

	private static Page core(Category c) {
		return new Page("core:" + c.name(), c.name.toString(), c.subtitle, c.group.title, c.glyph, Kind.MODULES, false, () -> {
			List<GuiModule> out = new ArrayList<>();
			for (Module m : TopClient.INSTANCE.getModuleManager().getModulesInCategory(c)) {
				out.add(new CoreAdapter(m));
			}
			if (c == Category.CLIENT) {
				out.add(OptionGroups.visuals());
			}
			return out;
		});
	}

	private static Page clover(dev.topclient.client.clover.module.Category c, String subtitle, char glyph) {
		return new Page("clover:" + c.name(), c.title, subtitle, CLOVER_GROUP, glyph, Kind.MODULES, true, () -> {
			List<GuiModule> out = new ArrayList<>();
			if (c == dev.topclient.client.clover.module.Category.UTILITY) {
				out.add(OptionGroups.clover());
			}
			if (c == dev.topclient.client.clover.module.Category.HUD) {
				// from the old Player and Render pages
				for (Category old : new Category[]{Category.MOVEMENT, Category.RENDER}) {
					for (Module m : TopClient.INSTANCE.getModuleManager().getModulesInCategory(old)) {
						out.add(new CoreAdapter(m));
					}
				}
			}
			for (dev.topclient.client.clover.module.Module m : ModuleManager.all()) {
				dev.topclient.client.clover.module.Category mc = m.category == dev.topclient.client.clover.module.Category.WORLD
						? dev.topclient.client.clover.module.Category.VISUAL : m.category;
				if (mc == c) {
					out.add(new CloverAdapter(m));
				}
			}
			return out;
		});
	}
}
