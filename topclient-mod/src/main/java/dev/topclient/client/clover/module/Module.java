package dev.topclient.client.clover.module;

import dev.topclient.client.clover.Config;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting> settings = new ArrayList<>();
	public boolean isNew;
	public record Action(String label, java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> open) {
	}

	public final List<Action> actions = new ArrayList<>();

	protected void action(String label, java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> open) {
		actions.add(new Action(label, open));
	}

	public final Setting toggleKey;
	private final String enabledKey;
	private int enabledVer = -1;
	private boolean enabledVal;

	protected Module(String id, String name, String description, Category category) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabledKey = "mod." + id + ".enabled";
		this.toggleKey = Setting.key(this, "key", "Toggle key", 0);
	}

	/** Cached: re-read from the config only when something in it changed (this is called very often). */
	public boolean enabled() {
		if (enabledVer != Config.version()) {
			enabledVal = Config.getBool(enabledKey, defaultEnabled());
			enabledVer = Config.version();
		}
		return enabledVal;
	}

	protected boolean defaultEnabled() {
		return false;
	}

	public void setEnabled(boolean on) {
		boolean was = enabled();
		Config.setBool(enabledKey, on);
		if (on != was && !bulk) {
			dev.topclient.client.clover.ui.UiSounds.toggle(on);
		}
		if (on && !was) {
			onEnable();
		} else if (!on && was) {
			onDisable();
		}
	}

	public static boolean bulk;

	public void toggle() {
		setEnabled(!enabled());
	}

	public boolean favorite() {
		return Config.getBool("mod." + id + ".fav", false);
	}

	public void setFavorite(boolean f) {
		Config.setBool("mod." + id + ".fav", f);
	}

	protected Setting bool(String key, String label, boolean def) {
		Setting s = Setting.bool(this, key, label, def);
		settings.add(s);
		return s;
	}

	protected Setting number(String key, String label, float min, float max, float step, float def) {
		Setting s = Setting.number(this, key, label, min, max, step, def);
		settings.add(s);
		return s;
	}

	protected Setting color(String key, String label, int rgb) {
		Setting s = Setting.color(this, key, label, rgb);
		settings.add(s);
		return s;
	}

	protected Setting text(String key, String label, String def) {
		Setting s = Setting.text(this, key, label, def);
		settings.add(s);
		return s;
	}

	protected Setting header(String label) {
		Setting s = Setting.header(this, label);
		settings.add(s);
		return s;
	}

	protected Setting mode(String key, String label, int def, String... modes) {
		Setting s = Setting.mode(this, key, label, def, modes);
		settings.add(s);
		return s;
	}

	public void resetSettings() {
		for (Setting s : settings) {
			s.reset();
		}
		toggleKey.reset();
	}

	public void onTick(Minecraft mc) {
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	public boolean hasSettings() {
		return true;
	}
}
