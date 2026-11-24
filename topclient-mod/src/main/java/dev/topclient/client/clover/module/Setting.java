package dev.topclient.client.clover.module;

import dev.topclient.client.clover.Config;

public final class Setting {
	public enum Type { BOOL, FLOAT, COLOR, MODE, KEY, HEADER, TEXT }

	public final Type type;
	public final String key;
	public final String label;
	public final float min;
	public final float max;
	public final float step;
	public final String[] modes;
	private final String path;
	private final float defFloat;
	private final int defInt;
	private final boolean defBool;
	public boolean look;
	private String defText = "";

	// Parsed values, re-read only when Config.version() changes (every setter goes through Config).
	private int textVer = -1;
	private String textVal;
	private int keyVer = -1;
	private int keyVal;
	private int boolVer = -1;
	private boolean boolVal;
	private int floatVer = -1;
	private float floatVal;
	private int colorVer = -1;
	private int colorVal;

	private Setting(Module owner, Type type, String key, String label, float min, float max, float step,
					String[] modes, float defFloat, int defInt, boolean defBool) {
		this.type = type;
		this.key = key;
		this.label = label;
		this.min = min;
		this.max = max;
		this.step = step;
		this.modes = modes;
		this.path = "mod." + owner.id + "." + key;
		this.defFloat = defFloat;
		this.defInt = defInt;
		this.defBool = defBool;
	}

	public static Setting bool(Module owner, String key, String label, boolean def) {
		return new Setting(owner, Type.BOOL, key, label, 0, 1, 1, null, 0, 0, def);
	}

	public static Setting number(Module owner, String key, String label, float min, float max, float step, float def) {
		return new Setting(owner, Type.FLOAT, key, label, min, max, step, null, def, 0, false);
	}

	public static Setting color(Module owner, String key, String label, int rgb) {
		return new Setting(owner, Type.COLOR, key, label, 0, 0, 0, null, 0, rgb & 0xFFFFFF, false);
	}

	public static Setting mode(Module owner, String key, String label, int defIndex, String... modes) {
		return new Setting(owner, Type.MODE, key, label, 0, modes.length - 1, 1, modes, 0, defIndex, false);
	}

	public static Setting key(Module owner, String key, String label, int defKey) {
		return new Setting(owner, Type.KEY, key, label, 0, 0, 0, null, 0, defKey, false);
	}

	public static Setting text(Module owner, String key, String label, String def) {
		Setting s = new Setting(owner, Type.TEXT, key, label, 0, 0, 0, null, 0, 0, false);
		s.defText = def == null ? "" : def;
		return s;
	}

	public String getText() {
		if (textVer != Config.version() || textVal == null) {
			textVal = Config.get(path, defText);
			textVer = Config.version();
		}
		return textVal;
	}

	public void setText(String v) {
		Config.set(path, v == null ? null : v.length() > 300 ? v.substring(0, 300) : v);
	}

	public static Setting header(Module owner, String label) {
		return new Setting(owner, Type.HEADER, "_" + label, label, 0, 0, 0, null, 0, 0, false);
	}

	public int getKey() {
		if (keyVer != Config.version()) {
			keyVal = Config.getInt(path, defInt);
			keyVer = Config.version();
		}
		return keyVal;
	}

	public void setKey(int code) {
		Config.setInt(path, code);
		dev.topclient.client.clover.ui.UiSounds.click();
	}

	public boolean getBool() {
		if (boolVer != Config.version()) {
			boolVal = Config.getBool(path, defBool);
			boolVer = Config.version();
		}
		return boolVal;
	}

	public void setBool(boolean v) {
		boolean was = getBool();
		Config.setBool(path, v);
		if (was != v) {
			dev.topclient.client.clover.ui.UiSounds.toggle(v);
		}
	}

	public float getFloat() {
		if (floatVer != Config.version()) {
			floatVal = Math.max(min, Math.min(max, Config.getFloat(path, defFloat)));
			floatVer = Config.version();
		}
		return floatVal;
	}

	public void setFloat(float v) {
		float snapped = step > 0 ? Math.round((v - min) / step) * step + min : v;
		float before = getFloat();
		float now = Math.max(min, Math.min(max, snapped));
		Config.setFloat(path, now);
		if (now != before && max > min) {
			dev.topclient.client.clover.ui.UiSounds.tick((now - min) / (max - min));
		}
	}

	public int getInt() {
		return Math.round(getFloat());
	}

	public int getColor() {
		if (colorVer != Config.version()) {
			colorVal = Config.getInt(path, defInt) & 0xFFFFFF;
			colorVer = Config.version();
		}
		return colorVal;
	}

	public void setColor(int rgb) {
		int before = getColor();
		Config.setInt(path, rgb & 0xFFFFFF);
		if (before != (rgb & 0xFFFFFF)) {
			dev.topclient.client.clover.ui.UiSounds.tick(((rgb >> 8) & 0xFF) / 255F);
		}
	}

	public int getMode() {
		return Math.max(0, Math.min(modes.length - 1, Config.getInt(path, defInt)));
	}

	public void setMode(int i) {
		Config.setInt(path, Math.floorMod(i, modes.length));
		dev.topclient.client.clover.ui.UiSounds.click();
	}

	public String modeName() {
		return modes[getMode()];
	}

	public void reset() {
		if (type != Type.HEADER) {
			Config.set(path, null);
		}
	}

	public String valueText() {
		return switch (type) {
			case BOOL -> getBool() ? "On" : "Off";
			case FLOAT -> step >= 1 ? Integer.toString(getInt()) : String.format("%.2f", getFloat());
			case COLOR -> String.format("#%06X", getColor());
			case MODE -> modeName();
			case KEY -> keyName(getKey());
			case HEADER -> "";
			case TEXT -> getText();
		};
	}

	public static String keyName(int code) {
		if (code <= 0) {
			return "None";
		}
		return com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
	}
}
