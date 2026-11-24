package dev.topclient.client.clover;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public final class Config {
	private static final Properties PROPS = new Properties();
	private static boolean loaded;
	private static boolean dirty;
	/** Goes up on every change to the stored values, so cached settings know when to re-read. */
	private static volatile int version;
	private static long lastSave;

	private Config() {
	}

	public static int version() {
		return version;
	}

	public static Path dir() {
		Path p = dev.topclient.client.LegitConfig.dir();
		try {
			Files.createDirectories(p);
		} catch (IOException ignored) {
		}
		return p;
	}

	private static Path file() {
		return dir().resolve("clover.properties");
	}

	private static void ensure() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path f = file();
		if (Files.exists(f)) {
			try (InputStream in = Files.newInputStream(f)) {
				PROPS.load(in);
				version++;
			} catch (IOException e) {
				CloverClient.LOGGER.warn("Could not read settings, using defaults: {}", e.toString());
			}
		}
	}

	public static String get(String key, String def) {
		ensure();
		return PROPS.getProperty(key, def);
	}

	public static boolean getBool(String key, boolean def) {
		String v = get(key, null);
		return v == null ? def : Boolean.parseBoolean(v);
	}

	public static int getInt(String key, int def) {
		String v = get(key, null);
		if (v == null) {
			return def;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static float getFloat(String key, float def) {
		String v = get(key, null);
		if (v == null) {
			return def;
		}
		try {
			return Float.parseFloat(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static void set(String key, String value) {
		ensure();
		if (value == null) {
			if (PROPS.remove(key) != null) {
				dirty = true;
				version++;
			}
		} else if (!value.equals(PROPS.getProperty(key))) {
			PROPS.setProperty(key, value);
			dirty = true;
			version++;
		}
	}

	public static void setBool(String key, boolean v) {
		set(key, Boolean.toString(v));
	}

	public static void setInt(String key, int v) {
		set(key, Integer.toString(v));
	}

	public static void setFloat(String key, float v) {
		set(key, Float.toString(v));
	}

	public static Set<String> keys() {
		ensure();
		return new TreeSet<>(PROPS.stringPropertyNames());
	}

	public static void saveIfNeeded(boolean force) {
		if (!dirty) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!force && now - lastSave < 2000) {
			return;
		}
		lastSave = now;
		dirty = false;
		try (OutputStream out = Files.newOutputStream(file())) {
			PROPS.store(out, "TopClient by TopAmir_ - local settings, never uploaded");
		} catch (IOException e) {
			CloverClient.LOGGER.warn("Could not save settings: {}", e.toString());
		}
	}

	public static void resetAll(String prefix) {
		ensure();
		for (String k : new TreeSet<>(PROPS.stringPropertyNames())) {
			if (prefix == null || k.startsWith(prefix)) {
				PROPS.remove(k);
				dirty = true;
				version++;
			}
		}
		saveIfNeeded(true);
	}

	private static Path profileFile(int slot) {
		Path d = dir().resolve("profiles");
		try {
			Files.createDirectories(d);
		} catch (IOException ignored) {
		}
		return d.resolve("profile" + slot + ".properties");
	}

	public static boolean hasProfile(int slot) {
		return Files.isRegularFile(profileFile(slot));
	}

	public static boolean saveProfile(int slot) {
		ensure();
		Properties copy = new Properties();
		for (String k : PROPS.stringPropertyNames()) {
			if (!k.startsWith("acc.")) {
				copy.setProperty(k, PROPS.getProperty(k));
			}
		}
		try (OutputStream out = Files.newOutputStream(profileFile(slot))) {
			copy.store(out, "Clover Client profile " + slot);
			return true;
		} catch (IOException e) {
			CloverClient.LOGGER.warn("Could not save profile {}: {}", slot, e.toString());
			return false;
		}
	}

	public static boolean loadProfile(int slot) {
		ensure();
		Path f = profileFile(slot);
		if (!Files.isRegularFile(f)) {
			return false;
		}
		Properties in = new Properties();
		try (InputStream s = Files.newInputStream(f)) {
			in.load(s);
		} catch (IOException e) {
			return false;
		}
		for (String k : new TreeSet<>(PROPS.stringPropertyNames())) {
			if (!k.startsWith("acc.")) {
				PROPS.remove(k);
			}
		}
		for (String k : in.stringPropertyNames()) {
			if (!k.startsWith("acc.")) {
				PROPS.setProperty(k, in.getProperty(k));
			}
		}
		dirty = true;
		version++;
		saveIfNeeded(true);
		return true;
	}
}
