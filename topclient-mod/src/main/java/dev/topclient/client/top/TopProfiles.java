package dev.topclient.client.top;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.topclient.client.TopClient;
import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.Module;
import dev.topclient.client.clover.module.ModuleManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Named profiles (Default, UHC, Skyblock, ...). A profile is every mod's on/off state, settings and
 * HUD position: all "mod.*" settings plus the older modules' JSON. Menu looks, accounts and
 * favourites stay the same for every profile.
 *
 * Files: config/topclient/profiles/<id>.properties and <id>.json. Nothing leaves the PC; sharing
 * puts a text code on the clipboard that the player pastes themselves.
 */
public final class TopProfiles {
	private TopProfiles() {
	}

	private static final String PREFIX = "mod.";
	private static final Gson GSON = new Gson();
	public static final int MAX_NAME = 24;

	public record Profile(String id, String name) {
	}

	private static Path dir() {
		Path d = Config.dir().resolve("profiles");
		try {
			Files.createDirectories(d);
		} catch (IOException ignored) {
		}
		return d;
	}

	// ---- list ----
	public static List<Profile> list() {
		List<Profile> out = new ArrayList<>();
		for (String id : Config.get("top.profiles", "").split(",")) {
			if (!id.isBlank()) {
				out.add(new Profile(id, Config.get("top.profile." + id + ".name", id)));
			}
		}
		if (out.isEmpty()) { // first start: today's settings become "Default"
			Config.set("top.profiles", "default");
			Config.set("top.profile.default.name", "Default");
			Config.set("top.profile.active", "default");
			save("default");
			out.add(new Profile("default", "Default"));
		}
		return out;
	}

	public static String active() {
		return Config.get("top.profile.active", "default");
	}

	public static String nameOf(String id) {
		return Config.get("top.profile." + id + ".name", id);
	}

	private static void setList(List<String> ids) {
		Config.set("top.profiles", String.join(",", ids));
	}

	private static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Profile p : list()) {
			out.add(p.id());
		}
		return out;
	}

	private static String newId(String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		if (base.isEmpty()) {
			base = "profile";
		}
		List<String> ids = ids();
		String id = base;
		for (int n = 2; ids.contains(id); n++) {
			id = base + "-" + n;
		}
		return id;
	}

	public static String clean(String name) {
		name = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").strip();
		if (name.length() > MAX_NAME) {
			name = name.substring(0, MAX_NAME);
		}
		return name;
	}

	// ---- save / load ----
	/** Writes the current mod settings into the profile's files. */
	public static void save(String id) {
		Map<String, String> mods = currentMods();
		java.util.Properties p = new java.util.Properties();
		p.putAll(mods);
		try (var out = Files.newOutputStream(dir().resolve(id + ".properties"))) {
			p.store(out, "TopClient profile " + nameOf(id));
			Files.writeString(dir().resolve(id + ".json"), TopClient.INSTANCE.getProfileManager().toJson());
		} catch (IOException e) {
			CloverClient.LOGGER.warn("Could not save profile {}: {}", id, e.toString());
		}
	}

	private static Map<String, String> currentMods() {
		Map<String, String> out = new LinkedHashMap<>();
		for (String k : Config.keys()) {
			if (k.startsWith(PREFIX)) {
				out.put(k, Config.get(k, ""));
			}
		}
		return out;
	}

	/** Saves the active profile, then switches every mod to the chosen one. */
	public static void switchTo(String id) {
		if (id.equals(active())) {
			return;
		}
		save(active());
		java.util.Properties p = new java.util.Properties();
		try (var in = Files.newInputStream(dir().resolve(id + ".properties"))) {
			p.load(in);
		} catch (IOException e) {
			// a profile that was never saved starts from the current settings
			save(id);
			Config.set("top.profile.active", id);
			Config.saveIfNeeded(true);
			return;
		}
		String json = null;
		try {
			json = Files.readString(dir().resolve(id + ".json"));
		} catch (IOException ignored) {
		}
		Map<String, String> mods = new LinkedHashMap<>();
		for (String k : p.stringPropertyNames()) {
			if (k.startsWith(PREFIX)) {
				mods.put(k, p.getProperty(k));
			}
		}
		apply(mods, json);
		Config.set("top.profile.active", id);
		Config.saveIfNeeded(true);
	}

	/** Replaces every "mod.*" setting, running each mod's switch-on/off code where its state changes. */
	private static void apply(Map<String, String> mods, String coreJson) {
		Map<String, Boolean> was = new LinkedHashMap<>();
		for (Module m : ModuleManager.all()) {
			was.put(m.id, m.enabled());
		}
		for (String k : Config.keys()) {
			if (k.startsWith(PREFIX)) {
				Config.set(k, null);
			}
		}
		for (Map.Entry<String, String> e : mods.entrySet()) {
			Config.set(e.getKey(), e.getValue());
		}
		Module.bulk = true;
		try {
			for (Module m : ModuleManager.all()) {
				boolean now = m.enabled(), before = was.getOrDefault(m.id, false);
				if (now != before) {
					Config.setBool(PREFIX + m.id + ".enabled", before);
					m.setEnabled(now);
				}
			}
		} finally {
			Module.bulk = false;
		}
		if (coreJson != null) {
			TopClient.INSTANCE.getProfileManager().applyJson(coreJson);
		}
	}

	// ---- create / rename / delete ----
	/** Copies the current settings into a new profile and makes it active. */
	public static String saveAsNew(String name) {
		name = clean(name);
		if (name.isEmpty()) {
			name = "Profile " + (ids().size() + 1);
		}
		save(active());
		String id = newId(name);
		List<String> ids = ids();
		ids.add(id);
		setList(ids);
		Config.set("top.profile." + id + ".name", name);
		Config.set("top.profile.active", id);
		save(id);
		Config.saveIfNeeded(true);
		return id;
	}

	public static void rename(String id, String name) {
		name = clean(name);
		if (!name.isEmpty()) {
			Config.set("top.profile." + id + ".name", name);
			Config.saveIfNeeded(false);
		}
	}

	/** Deletes a profile that isn't active (the active one can't be deleted). */
	public static boolean delete(String id) {
		List<String> ids = ids();
		if (id.equals(active()) || ids.size() <= 1 || !ids.remove(id)) {
			return false;
		}
		setList(ids);
		Config.set("top.profile." + id + ".name", null);
		try {
			Files.deleteIfExists(dir().resolve(id + ".properties"));
			Files.deleteIfExists(dir().resolve(id + ".json"));
		} catch (IOException ignored) {
		}
		Config.saveIfNeeded(true);
		return true;
	}

	// ---- sharing (a text code; nothing is uploaded) ----
	public static String shareCode(String id) {
		if (id.equals(active())) {
			save(id);
		}
		JsonObject o = new JsonObject();
		o.addProperty("name", nameOf(id));
		JsonObject mods = new JsonObject();
		java.util.Properties p = new java.util.Properties();
		try (var in = Files.newInputStream(dir().resolve(id + ".properties"))) {
			p.load(in);
		} catch (IOException ignored) {
		}
		for (String k : p.stringPropertyNames()) {
			if (k.startsWith(PREFIX)) {
				mods.addProperty(k, p.getProperty(k));
			}
		}
		o.add("mods", mods);
		try {
			o.addProperty("core", Files.readString(dir().resolve(id + ".json")));
		} catch (IOException ignored) {
		}
		return "TOPCLIENT-PROFILE:" + Base64.getEncoder().encodeToString(GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
	}

	/** Adds a profile from a share code. Returns its name, or null when the code isn't valid. */
	public static String importCode(String code) {
		if (code == null) {
			return null;
		}
		code = code.strip();
		if (!code.startsWith("TOPCLIENT-PROFILE:") || code.length() > 400_000) {
			return null;
		}
		JsonObject o;
		try {
			o = GSON.fromJson(new String(Base64.getDecoder().decode(code.substring(18)), StandardCharsets.UTF_8), JsonObject.class);
		} catch (RuntimeException e) {
			return null;
		}
		if (o == null || !o.has("mods") || !o.get("mods").isJsonObject()) {
			return null;
		}
		String name = clean(o.has("name") ? o.get("name").getAsString() : "Shared");
		if (name.isEmpty()) {
			name = "Shared";
		}
		String id = newId(name);
		java.util.Properties p = new java.util.Properties();
		for (var e : o.getAsJsonObject("mods").entrySet()) {
			// only mod settings: a code can't touch accounts, looks or anything else
			if (e.getKey().startsWith(PREFIX) && e.getValue().isJsonPrimitive() && e.getKey().length() < 120) {
				p.setProperty(e.getKey(), e.getValue().getAsString());
			}
		}
		try (var out = Files.newOutputStream(dir().resolve(id + ".properties"))) {
			p.store(out, "TopClient profile " + name);
			if (o.has("core") && o.get("core").isJsonPrimitive()) {
				Files.writeString(dir().resolve(id + ".json"), o.get("core").getAsString());
			}
		} catch (IOException e) {
			return null;
		}
		List<String> ids = ids();
		ids.add(id);
		setList(ids);
		Config.set("top.profile." + id + ".name", name);
		Config.saveIfNeeded(true);
		return name;
	}
}
