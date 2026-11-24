package dev.topclient.client.gui.model;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.ModuleManager;
import dev.topclient.client.clover.screen.HudEditorScreen;
import dev.topclient.client.clover.servers.FavouriteServers;
import dev.topclient.client.clover.servers.FavouriteServersScreen;
import dev.topclient.client.clover.ui.UiSounds;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OptionGroups {
	private OptionGroups() {
	}

	public static GuiModule group(String id, String name, String description, List<GuiSetting> settings) {
		return new GuiModule() {
			@Override
			public String id() {
				return "group:" + id;
			}

			@Override
			public String name() {
				return name;
			}

			@Override
			public String description() {
				return description;
			}

			@Override
			public boolean enabled() {
				return true;
			}

			@Override
			public void setEnabled(boolean on) {
			}

			@Override
			public boolean toggleable() {
				return false;
			}

			@Override
			public List<GuiSetting> settings() {
				return settings;
			}
		};
	}

	public static GuiModule sounds() {
		List<GuiSetting> s = new ArrayList<>();
		s.add(CloverAdapter.header("Sound"));
		s.add(toggle("Menu sounds", "Every sound of the menu on or off",
				UiSounds::enabled, v -> Config.setBool("ui.sounds", v), true));
		s.add(mode("Sound pack", "13 packs: Soft (Clover's original), Crisp, Bubble, Glass, Wood, Retro, Pop, Keys, SciFi, Chime, Drop, Arcade and Minecraft",
				UiSounds.PACKS, UiSounds::pack, i -> {
					Config.setInt("ui.soundPack", i);
					UiSounds.preview();
				}, 0));
		s.add(slider("Volume", 0, 1, 0.05, () -> (double) UiSounds.volume(),
				v -> Config.setFloat("ui.soundVolume", (float) (double) v), 0.8, true));
		s.add(slider("Pitch", 0.5, 2, 0.05, () -> (double) UiSounds.pitch(),
				v -> Config.setFloat("ui.soundPitch", (float) (double) v), 1.0, false));
		s.add(CloverAdapter.action("Play a preview", UiSounds::preview));
		s.add(CloverAdapter.header("Which sounds play"));
		s.add(kind("Switching things on / off", UiSounds.Kind.TOGGLE_ON));
		s.add(kind("Clicking settings", UiSounds.Kind.CLICK));
		s.add(kind("Slider ticks", UiSounds.Kind.TICK));
		s.add(kind("Opening and closing the menu", UiSounds.Kind.OPEN));
		s.add(kind("Hovering cards and buttons", UiSounds.Kind.HOVER));
		s.add(CloverAdapter.header("Notices"));
		s.add(toggle("Toggle notices", "Shows a small notice when a Clover mod is switched with its key",
				() -> Config.getBool("ui.notices", true), v -> Config.setBool("ui.notices", v), true));
		return group("sounds", "Sounds", "Sound pack, volume, pitch and which menu sounds play.", s);
	}

	private static GuiSetting kind(String label, UiSounds.Kind k) {
		boolean def = k != UiSounds.Kind.HOVER;
		return toggle(label, null, () -> UiSounds.kindOn(k), v -> Config.setBool(UiSounds.kindKey(k), v), def);
	}

	public static List<GuiModule> customize() {
		dev.topclient.client.module.modules.client.ClickGUI menu =
				dev.topclient.client.TopClient.INSTANCE.getModuleManager().getModule(dev.topclient.client.module.modules.client.ClickGUI.class);
		List<GuiModule> out = new ArrayList<>();
		out.add(fromSettings(menu, "theme", "Theme", "19 colour themes or your own colour, plus rainbow and breathing for ESP and HUD.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeTheme()));
		out.add(sounds());
		out.add(fromSettings(menu, "background", "Background", "What is behind the menu: Aurora, Fireflies, Starfield, a gradient, your own picture or plain dim.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeBackground()));
		out.add(fromSettings(menu, "window", "Window", "Menu size, opacity, roundness and the open animation.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeWindow()));
		out.add(fromSettings(menu, "cards", "Cards", "Icons, descriptions, card size and how many columns.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeCards()));
		out.add(fromSettings(menu, "text", "Text", "Menu font and the HUD font.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeText()));
		out.add(fromSettings(menu, "icons", "Icons", "Icon theme: outline icons, real Minecraft items or emoji, and the shape behind them.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeIcons()));
		out.add(fromSettings(menu, "falling", "Falling Particles", "Clovers, pumpkins, Christmas, snow, sakura, leaves and more falling behind the menu.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeFalling()));
		out.add(fromSettings(menu, "walker", "Skin Walker", "Your skin walks up next to the menu, strikes a pose and your name floats above it.",
				dev.topclient.client.module.modules.client.ClickGUI.customizeWalker()));
		return out;
	}

public static GuiModule visuals() {
		return new GuiModule() {
			@Override
			public String id() {
				return "group:visuals";
			}

			@Override
			public String name() {
				return "Visuals";
			}

			@Override
			public String description() {
				return "Restyle crystals, totems, swords and more with hundreds of texture styles. Works offline.";
			}

			@Override
			public boolean enabled() {
				return true;
			}

			@Override
			public void setEnabled(boolean on) {
			}

			@Override
			public boolean toggleable() {
				return false;
			}

			@Override
			public Runnable onOpen() {
				return () -> {
					Minecraft mc = Minecraft.getInstance();
					mc.gui.setScreen(new dev.topclient.client.visuals.VisualsScreen(mc.gui.screen()));
				};
			}

			@Override
			public List<GuiSetting> settings() {
				return List.of();
			}
		};
	}

	private static GuiModule fromSettings(dev.topclient.client.module.Module owner, String id, String name, String description,
										  List<dev.topclient.client.module.setting.Setting<?>> list) {
		List<GuiSetting> s = new ArrayList<>();
		for (dev.topclient.client.module.setting.Setting<?> setting : list) {
			GuiSetting g = CoreAdapter.wrapFor(owner, setting);
			if (g != null) {
				s.add(g);
			}
		}
		return group(id, name, description, s);
	}

	public static GuiModule clover() {
		List<GuiSetting> s = new ArrayList<>();
		s.add(CloverAdapter.header("HUD"));
		s.add(CloverAdapter.action("Open the HUD editor", () -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
		}));
		s.add(CloverAdapter.header("Server list"));
		s.add(toggle("Favourite servers tab", "A tab on the right of the server list with your favourite servers",
				FavouriteServers::enabled, FavouriteServers::setEnabled, true));
		s.add(CloverAdapter.action("Pick favourite servers", () -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new FavouriteServersScreen(mc.gui.screen()));
		}));
		s.add(CloverAdapter.header("All Clover mods"));
		s.add(CloverAdapter.action("Enable all Clover mods", () -> ModuleManager.setAll(true)));
		s.add(CloverAdapter.action("Disable all Clover mods", () -> ModuleManager.setAll(false)));
		s.add(CloverAdapter.header("Profiles"));
		for (int i = 1; i <= 3; i++) {
			int slot = i;
			s.add(CloverAdapter.action("Save to profile " + slot, () -> Config.saveProfile(slot)));
			s.add(CloverAdapter.action("Load profile " + slot, () -> Config.loadProfile(slot)));
		}
		return group("clover", "Clover Options", "HUD editor, favourite servers, profiles and switching every Clover mod at once.", s);
	}

	static GuiSetting mode(String label, String desc, String[] modes, Supplier<Integer> get, Consumer<Integer> set, int def) {
		return new GuiSetting(GuiSetting.Kind.MODE, label, desc) {
			@Override
			public String[] modes() {
				return modes;
			}

			@Override
			public int modeIndex() {
				return get.get();
			}

			@Override
			public void setModeIndex(int i) {
				set.accept(Math.floorMod(i, modes.length));
				Config.saveIfNeeded(false);
			}

			@Override
			public void reset() {
				set.accept(def);
			}
		};
	}

	static GuiSetting color(String label, String desc, Supplier<Integer> get, Consumer<Integer> set, int def) {
		return new GuiSetting(GuiSetting.Kind.COLOR, label, desc) {
			@Override
			public int rgb() {
				return get.get() & 0xFFFFFF;
			}

			@Override
			public void setRgb(int rgb) {
				set.accept(rgb & 0xFFFFFF);
				Config.saveIfNeeded(false);
			}

			@Override
			public void reset() {
				set.accept(def);
			}
		};
	}

	static GuiSetting toggle(String label, String desc, Supplier<Boolean> get, Consumer<Boolean> set, boolean def) {
		return new GuiSetting(GuiSetting.Kind.TOGGLE, label, desc) {
			@Override
			public boolean bool() {
				return get.get();
			}

			@Override
			public void setBool(boolean v) {
				if (get.get() != v) {
					set.accept(v);
					UiSounds.toggle(v);
					Config.saveIfNeeded(false);
				}
			}

			@Override
			public void reset() {
				set.accept(def);
			}
		};
	}

	static GuiSetting slider(String label, double min, double max, double step, Supplier<Double> get, Consumer<Double> set, double def) {
		return slider(label, min, max, step, get, set, def, false);
	}

	static GuiSetting slider(String label, double min, double max, double step, Supplier<Double> get, Consumer<Double> set, double def, boolean percent) {
		return new GuiSetting(GuiSetting.Kind.SLIDER, label, null) {
			@Override
			public double min() {
				return min;
			}

			@Override
			public double max() {
				return max;
			}

			@Override
			public double step() {
				return step;
			}

			@Override
			public double value() {
				return get.get();
			}

			@Override
			public void setValue(double v) {
				double before = get.get();
				set.accept(snap(v));
				if (before != get.get()) {
					UiSounds.tick((float) ((get.get() - min) / (max - min)));
				}
			}

			@Override
			public String valueText() {
				return percent ? Math.round(value() * 100) + "%" : super.valueText();
			}

			@Override
			public void reset() {
				set.accept(def);
			}
		};
	}
}
