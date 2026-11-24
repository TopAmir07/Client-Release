package dev.topclient.client.gui.model;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.HudModule;
import dev.topclient.client.clover.module.Module;
import dev.topclient.client.clover.module.Setting;
import dev.topclient.client.clover.screen.HudEditorScreen;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class CloverAdapter implements GuiModule {
	private final Module module;
	private List<GuiSetting> settings;

	public CloverAdapter(Module module) {
		this.module = module;
	}

	@Override
	public String id() {
		return "clover:" + module.id;
	}

	@Override
	public String name() {
		return module.name;
	}

	@Override
	public String description() {
		return module.description;
	}

	@Override
	public boolean enabled() {
		return module.enabled();
	}

	@Override
	public void setEnabled(boolean on) {
		if (module.enabled() != on) {
			module.setEnabled(on);
			Config.saveIfNeeded(false);
		}
	}

	@Override
	public boolean isNew() {
		return module.isNew;
	}

	@Override
	public String bindName() {
		return Setting.keyName(module.toggleKey.getKey());
	}

	private int builtFor = -1;

	@Override
	public List<GuiSetting> settings() {
		if (module instanceof dev.topclient.client.clover.module.TotemParticles
				&& builtFor != dev.topclient.client.clover.module.TotemParticles.colourVersion) {
			settings = null;
			builtFor = dev.topclient.client.clover.module.TotemParticles.colourVersion;
		}
		if (settings == null) {
			settings = new ArrayList<>();
			settings.add(wrap(module.toggleKey));
			for (Module.Action a : module.actions) {
				settings.add(action(a.label(), () -> {
					Minecraft mc = Minecraft.getInstance();
					mc.gui.setScreen(a.open().apply(mc.gui.screen()));
				}));
			}
			if (module instanceof HudModule h) {
				settings.add(action("Move on screen (HUD editor)", () -> {
					Minecraft mc = Minecraft.getInstance();
					mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
				}));
				settings.add(action("Reset position", h::resetPos));
			}
			List<GuiSetting> look = new ArrayList<>();
			for (Setting s : module.settings) {
				GuiSetting g = wrap(s);
				if (s.look) {
					look.add(g);
				} else {
					settings.add(g);
				}
			}
			if (!look.isEmpty()) {
				settings.add(header("Appearance"));
				settings.addAll(look);
			}
			if (module instanceof dev.topclient.client.clover.module.TotemParticles) {
				settings.addAll(totemColours());
			}
		}
		return settings;
	}

	@Override
	public void resetSettings() {
		module.resetSettings();
		Config.saveIfNeeded(true);
	}

	private static List<GuiSetting> totemColours() {
		List<GuiSetting> out = new ArrayList<>();
		List<Integer> colours = dev.topclient.client.clover.module.TotemParticles.colours();
		out.add(header("Colours (" + colours.size() + " of " + dev.topclient.client.clover.module.TotemParticles.MAX_COLOURS + ")"));
		for (int i = 0; i < colours.size(); i++) {
			final int index = i;
			out.add(new GuiSetting(GuiSetting.Kind.COLOR, "Colour " + (i + 1), null) {
				@Override
				public int rgb() {
					List<Integer> now = dev.topclient.client.clover.module.TotemParticles.colours();
					return index < now.size() ? now.get(index) : 0xFFFFFF;
				}

				@Override
				public void setRgb(int rgb) {
					dev.topclient.client.clover.module.TotemParticles.setColour(index, rgb);
				}
			});
		}
		if (colours.size() < dev.topclient.client.clover.module.TotemParticles.MAX_COLOURS) {
			out.add(action("+ Add a colour", dev.topclient.client.clover.module.TotemParticles::addColour));
		}
		if (colours.size() > 1) {
			out.add(action("- Remove the last colour", () -> dev.topclient.client.clover.module.TotemParticles.removeColour(
					dev.topclient.client.clover.module.TotemParticles.colours().size() - 1)));
		}
		out.add(action("Reset colours", dev.topclient.client.clover.module.TotemParticles::resetColours));
		return out;
	}

	static GuiSetting action(String label, Runnable r) {
		return new GuiSetting(GuiSetting.Kind.ACTION, label, null) {
			@Override
			public void run() {
				dev.topclient.client.clover.ui.UiSounds.click();
				r.run();
			}
		};
	}

	static GuiSetting header(String label) {
		return new GuiSetting(GuiSetting.Kind.HEADER, label, null) {
		};
	}

	static GuiSetting wrap(Setting s) {
		return switch (s.type) {
			case BOOL -> new GuiSetting(GuiSetting.Kind.TOGGLE, s.label, null) {
				@Override
				public boolean bool() {
					return s.getBool();
				}

				@Override
				public void setBool(boolean v) {
					s.setBool(v);
				}

				@Override
				public void reset() {
					s.reset();
				}
			};
			case FLOAT -> new GuiSetting(GuiSetting.Kind.SLIDER, s.label, null) {
				@Override
				public double min() {
					return s.min;
				}

				@Override
				public double max() {
					return s.max;
				}

				@Override
				public double step() {
					return s.step;
				}

				@Override
				public double value() {
					return s.getFloat();
				}

				@Override
				public void setValue(double v) {
					s.setFloat((float) v);
				}

				@Override
				public String valueText() {
					return s.valueText();
				}

				@Override
				public void reset() {
					s.reset();
				}
			};
			case COLOR -> new GuiSetting(GuiSetting.Kind.COLOR, s.label, null) {
				@Override
				public int rgb() {
					return s.getColor();
				}

				@Override
				public void setRgb(int rgb) {
					s.setColor(rgb);
				}

				@Override
				public void reset() {
					s.reset();
				}
			};
			case MODE -> new GuiSetting(GuiSetting.Kind.MODE, s.label, null) {
				@Override
				public String[] modes() {
					return s.modes;
				}

				@Override
				public int modeIndex() {
					return s.getMode();
				}

				@Override
				public void setModeIndex(int i) {
					s.setMode(i);
				}

				@Override
				public void reset() {
					s.reset();
				}
			};
			case KEY -> new GuiSetting(GuiSetting.Kind.KEY, s.label, null) {
				@Override
				public int key() {
					return s.getKey();
				}

				@Override
				public int noneKey() {
					return 0;
				}

				@Override
				public String keyName() {
					return Setting.keyName(s.getKey());
				}

				@Override
				public void setKey(int code) {
					s.setKey(Math.max(0, code));
				}

				@Override
				public void reset() {
					s.reset();
				}
			};
			case HEADER -> header(s.label);
			case TEXT -> new GuiSetting(GuiSetting.Kind.TEXT, s.label, null) {
				@Override
				public String text() {
					return s.getText();
				}

				@Override
				public void setText(String v) {
					s.setText(v);
				}

				@Override
				public void reset() {
					s.reset();
				}
			};
		};
	}
}
