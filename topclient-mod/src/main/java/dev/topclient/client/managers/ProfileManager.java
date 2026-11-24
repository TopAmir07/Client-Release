package dev.topclient.client.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.topclient.client.TopClient;
import dev.topclient.client.module.Module;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.module.setting.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class ProfileManager {
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private static Path dir() {
		return dev.topclient.client.LegitConfig.dir();
	}

	private static Path file() {
		return dir().resolve("profile.json");
	}

	public void loadProfile() {
		try {
			if (Files.isRegularFile(file())) {
				JsonObject root = gson.fromJson(Files.readString(file()), JsonObject.class);
				if (root == null)
					return;
				for (Module module : modules()) {
					JsonElement e = root.get(module.getName().toString());
					if (e != null && e.isJsonObject())
						apply(module, e.getAsJsonObject(), false);
				}
			}
		} catch (Exception ignored) {
		}
	}

	public void saveProfile() {
		try {
			Files.createDirectories(dir());
			Files.writeString(file(), toJson());
		} catch (Exception ignored) {
		}
	}

	/** Every older module's on/off state and settings as JSON (used for TopClient profiles). */
	public String toJson() {
		JsonObject root = new JsonObject();
		for (Module module : modules()) {
			JsonObject m = new JsonObject();
			m.addProperty("enabled", module.isEnabled() && !(module instanceof ClickGUI));
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				JsonElement v = write(setting);
				if (v != null)
					settings.add(setting.getName().toString(), v);
			}
			m.add("settings", settings);
			root.add(module.getName().toString(), m);
		}
		return gson.toJson(root);
	}

	/** Applies JSON from {@link #toJson()}: modules not switched on there are switched off. */
	public void applyJson(String json) {
		JsonObject root;
		try {
			root = gson.fromJson(json, JsonObject.class);
		} catch (Exception e) {
			return;
		}
		if (root == null)
			return;
		for (Module module : modules()) {
			if (module instanceof ClickGUI)
				continue;
			JsonElement e = root.get(module.getName().toString());
			if (module.isEnabled() && (e == null || !e.isJsonObject() || !e.getAsJsonObject().has("enabled")
					|| !e.getAsJsonObject().get("enabled").getAsBoolean()))
				module.setEnabled(false);
			if (e != null && e.isJsonObject())
				apply(module, e.getAsJsonObject(), false);
		}
		saveProfile();
	}

	private static List<Module> modules() {
		return TopClient.INSTANCE.getModuleManager().getModules();
	}

	private void apply(Module module, JsonObject config, boolean legacy) {
		JsonElement enabled = config.get("enabled");
		JsonObject settings = config.has("settings") && config.get("settings").isJsonObject() ? config.getAsJsonObject("settings") : null;

		List<Setting<?>> list = module.getSettings();
		for (int i = 0; i < list.size(); i++) {
			Setting<?> setting = list.get(i);
			JsonElement v = legacy ? config.get(String.valueOf(i)) : settings == null ? null : settings.get(setting.getName().toString());
			if (v == null)
				continue;
			try {
				read(module, setting, v);
			} catch (Exception ignored) {
			}
		}

		if (!(module instanceof ClickGUI) && enabled != null && enabled.isJsonPrimitive() && enabled.getAsBoolean())
			module.setEnabled(true);
	}

	private static void read(Module module, Setting<?> setting, JsonElement v) {
		if (setting instanceof BooleanSetting b) {
			b.setValue(v.getAsBoolean());
		} else if (setting instanceof ModeSetting<?> m) {
			String raw = v.getAsString();
			int found = -1;
			for (int i = 0; i < m.getModes().size(); i++) {
				if (m.getModes().get(i).name().equalsIgnoreCase(raw)) {
					found = i;
				}
			}
			if (found >= 0) {
				m.setModeIndex(found);
			} else if (raw.matches("\\d+")) {
				m.setModeIndex(Integer.parseInt(raw));
			}
		} else if (setting instanceof NumberSetting n) {
			n.setValue(v.getAsDouble());
		} else if (setting instanceof KeybindSetting k) {
			k.setKey(v.getAsInt());
			if (k.isModuleKey())
				module.setKey(v.getAsInt());
		} else if (setting instanceof StringSetting s) {
			s.setValue(v.getAsString());
		} else if (setting instanceof MinMaxSetting r && v.isJsonObject()) {
			JsonObject o = v.getAsJsonObject();
			r.setMinValue(o.get("1").getAsDouble());
			r.setMaxValue(o.get("2").getAsDouble());
		}
	}

	private static JsonElement write(Setting<?> setting) {
		JsonObject holder = new JsonObject();
		if (setting instanceof BooleanSetting b) {
			holder.addProperty("v", b.getValue());
		} else if (setting instanceof ModeSetting<?> m) {
			holder.addProperty("v", m.getMode().name());
		} else if (setting instanceof NumberSetting n) {
			holder.addProperty("v", n.getValue());
		} else if (setting instanceof KeybindSetting k) {
			holder.addProperty("v", k.getKey());
		} else if (setting instanceof StringSetting s) {
			holder.addProperty("v", s.getValue());
		} else if (setting instanceof MinMaxSetting r) {
			JsonObject o = new JsonObject();
			o.addProperty("1", r.getMinValue());
			o.addProperty("2", r.getMaxValue());
			return o;
		} else {
			return null;
		}
		return holder.get("v");
	}
}
