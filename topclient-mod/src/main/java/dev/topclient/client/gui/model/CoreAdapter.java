package dev.topclient.client.gui.model;

import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.module.Module;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.module.setting.*;
import dev.topclient.client.utils.KeyUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class CoreAdapter implements GuiModule {
	private final Module module;
	private List<GuiSetting> settings;

	public CoreAdapter(Module module) {
		this.module = module;
	}

	public Module module() {
		return module;
	}

	@Override
	public String id() {
		return "core:" + module.getName();
	}

	@Override
	public String name() {
		return module.getName().toString();
	}

	@Override
	public String description() {
		return module.getDescription() == null ? "" : module.getDescription().toString();
	}

	@Override
	public boolean enabled() {
		return !(module instanceof ClickGUI) && module.isEnabled();
	}

	@Override
	public boolean toggleable() {
		return !(module instanceof ClickGUI);
	}

	@Override
	public void setEnabled(boolean on) {
		if (!toggleable() || module.isEnabled() == on) {
			return;
		}
		module.setEnabled(on);
		UiSounds.toggle(on);
	}

	@Override
	public String bindName() {
		int k = module.getKey();
		return k == GLFW.GLFW_KEY_UNKNOWN || k == 0 ? "None" : KeyUtils.getKey(k).toString();
	}

	@Override
	public List<GuiSetting> settings() {
		if (settings == null) {
			settings = new ArrayList<>();
			for (Setting<?> s : module.getSettings()) {
				if (module instanceof ClickGUI menu && menu.onCustomizePage(s)) {
					continue;
				}
				GuiSetting g = wrap(s);
				if (g != null) {
					settings.add(g);
				}
			}
		}
		return settings;
	}

	private static String desc(Setting<?> s) {
		return s.getDescription() == null ? null : s.getDescription().toString();
	}

	public static GuiSetting wrapFor(Module module, Setting<?> setting) {
		return new CoreAdapter(module).wrap(setting);
	}

	private GuiSetting wrap(Setting<?> setting) {
		String label = setting.getName().toString();
		if (setting instanceof SectionSetting) {
			return new GuiSetting(GuiSetting.Kind.HEADER, label, null) {
			};
		}
		if (setting instanceof BooleanSetting b) {
			return new GuiSetting(GuiSetting.Kind.TOGGLE, label, desc(b)) {
				@Override
				public boolean bool() {
					return b.getValue();
				}

				@Override
				public void setBool(boolean v) {
					if (b.getValue() != v) {
						b.setValue(v);
						UiSounds.toggle(v);
					}
				}

				@Override
				public void reset() {
					b.setValue(b.getOriginalValue());
				}
			};
		}
		if (setting instanceof NumberSetting n) {
			return new GuiSetting(GuiSetting.Kind.SLIDER, label, desc(n)) {
				@Override
				public double min() {
					return n.getMin();
				}

				@Override
				public double max() {
					return n.getMax();
				}

				@Override
				public double step() {
					return n.getIncrement();
				}

				@Override
				public double value() {
					return n.getValue();
				}

				@Override
				public void setValue(double v) {
					double before = n.getValue();
					n.setValue(v);
					if (n.getValue() != before && max() > min()) {
						UiSounds.tick((float) ((n.getValue() - min()) / (max() - min())));
					}
				}

				@Override
				public void reset() {
					n.setValue(n.getOriginalValue());
				}
			};
		}
		if (setting instanceof MinMaxSetting r) {
			return new GuiSetting(GuiSetting.Kind.RANGE, label, desc(r)) {
				@Override
				public double min() {
					return r.getMin();
				}

				@Override
				public double max() {
					return r.getMax();
				}

				@Override
				public double step() {
					return r.getIncrement();
				}

				@Override
				public double low() {
					return r.getMinValue();
				}

				@Override
				public double high() {
					return r.getMaxValue();
				}

				@Override
				public void setLow(double v) {
					double before = r.getMinValue();
					r.setMinValue(Math.min(v, r.getMaxValue()));
					if (before != r.getMinValue() && max() > min()) {
						UiSounds.tick((float) ((r.getMinValue() - min()) / (max() - min())));
					}
				}

				@Override
				public void setHigh(double v) {
					double before = r.getMaxValue();
					r.setMaxValue(Math.max(v, r.getMinValue()));
					if (before != r.getMaxValue() && max() > min()) {
						UiSounds.tick((float) ((r.getMaxValue() - min()) / (max() - min())));
					}
				}

				@Override
				public void reset() {
					r.setMinValue(r.getOriginalMinValue());
					r.setMaxValue(r.getOriginalMaxValue());
				}
			};
		}
		if (setting instanceof ModeSetting<?> m) {
			String[] names = new String[m.getModes().size()];
			for (int i = 0; i < names.length; i++) {
				names[i] = m.getModes().get(i).name();
			}
			return new GuiSetting(GuiSetting.Kind.MODE, label, desc(m)) {
				@Override
				public String[] modes() {
					return names;
				}

				@Override
				public int modeIndex() {
					return m.getModeIndex();
				}

				@Override
				public void setModeIndex(int i) {
					m.setModeIndex(Math.floorMod(i, names.length));
					UiSounds.click();
				}

				@Override
				public void reset() {
					m.setModeIndex(m.getOriginalValue());
				}
			};
		}
		if (setting instanceof KeybindSetting k) {
			return new GuiSetting(GuiSetting.Kind.KEY, label, desc(k)) {
				@Override
				public int key() {
					return k.getKey();
				}

				@Override
				public int noneKey() {
					return GLFW.GLFW_KEY_UNKNOWN;
				}

				@Override
				public boolean mouseKeys() {
					return true;
				}

				@Override
				public String keyName() {
					int code = k.getKey();
					return code == GLFW.GLFW_KEY_UNKNOWN ? "None" : KeyUtils.getKey(code).toString();
				}

				@Override
				public void setKey(int code) {
					k.setKey(code);
					if (k.isModuleKey()) {
						module.setKey(code);
					}
					UiSounds.click();
				}

				@Override
				public void reset() {
					k.setKey(k.getOriginalKey());
					if (k.isModuleKey()) {
						module.setKey(k.getOriginalKey());
					}
				}
			};
		}
		if (setting instanceof StringSetting t) {
			String def = t.getValue();
			return new GuiSetting(GuiSetting.Kind.TEXT, label, desc(t)) {
				@Override
				public String text() {
					return t.getValue();
				}

				@Override
				public void setText(String s) {
					t.setValue(s);
					UiSounds.click();
				}

				@Override
				public void reset() {
					t.setValue(def);
				}
			};
		}
		return null;
	}
}
