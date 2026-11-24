package dev.topclient.client.gui;

import dev.topclient.client.clover.Config;
import dev.topclient.client.gui.model.GuiModule;

import java.util.LinkedHashSet;
import java.util.Set;

/** Modules marked with the heart in the menu (saved in the mod's own settings file). */
public final class Favorites {
	private static Set<String> ids;

	private Favorites() {
	}

	private static Set<String> load() {
		if (ids == null) {
			ids = new LinkedHashSet<>();
			for (String s : Config.get("ui.favorites", "").split(",")) {
				if (!s.isBlank()) {
					ids.add(s.trim());
				}
			}
		}
		return ids;
	}

	public static boolean has(GuiModule m) {
		return load().contains(m.id());
	}

	public static void toggle(GuiModule m) {
		Set<String> s = load();
		if (!s.remove(m.id())) {
			s.add(m.id());
		}
		Config.set("ui.favorites", String.join(",", s));
		Config.saveIfNeeded(false);
	}

	public static int count() {
		return load().size();
	}
}
