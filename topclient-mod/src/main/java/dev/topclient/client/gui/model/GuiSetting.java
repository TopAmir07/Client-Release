package dev.topclient.client.gui.model;

public abstract class GuiSetting {
	public enum Kind { TOGGLE, SLIDER, RANGE, MODE, KEY, TEXT, COLOR, HEADER, ACTION }

	public final Kind kind;
	public final String label;
	public final String description;

	protected GuiSetting(Kind kind, String label, String description) {
		this.kind = kind;
		this.label = label;
		this.description = description;
	}

	public boolean bool() {
		return false;
	}

	public void setBool(boolean v) {
	}

	public double min() {
		return 0;
	}

	public double max() {
		return 1;
	}

	public double step() {
		return 0;
	}

	public double value() {
		return 0;
	}

	public void setValue(double v) {
	}

	public double low() {
		return 0;
	}

	public double high() {
		return 0;
	}

	public void setLow(double v) {
	}

	public void setHigh(double v) {
	}

	public String[] modes() {
		return new String[0];
	}

	public int modeIndex() {
		return 0;
	}

	public void setModeIndex(int i) {
	}

	public int key() {
		return noneKey();
	}

	public void setKey(int code) {
	}

	public int noneKey() {
		return -1;
	}

	public boolean mouseKeys() {
		return false;
	}

	public String keyName() {
		return "None";
	}

	public String text() {
		return "";
	}

	public void setText(String s) {
	}

	public int rgb() {
		return 0xFFFFFF;
	}

	public void setRgb(int rgb) {
	}

	public void run() {
	}

	public void reset() {
	}

	public String valueText() {
		return switch (kind) {
			case TOGGLE -> bool() ? "On" : "Off";
			case SLIDER -> format(value());
			case RANGE -> format(low()) + " - " + format(high());
			case MODE -> modes().length == 0 ? "" : modes()[Math.max(0, Math.min(modes().length - 1, modeIndex()))];
			case KEY -> keyName();
			case TEXT -> text();
			case COLOR -> String.format("#%06X", rgb() & 0xFFFFFF);
			default -> "";
		};
	}

	protected String format(double v) {
		double s = step();
		if (s >= 1 || (s <= 0 && Math.abs(v - Math.rint(v)) < 1e-6)) {
			return Long.toString(Math.round(v));
		}
		if (s >= 0.1) {
			return String.format(java.util.Locale.ROOT, "%.1f", v);
		}
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}

	public double snap(double v) {
		double s = step();
		double out = Math.max(min(), Math.min(max(), v));
		if (s > 0) {
			out = Math.round((out - min()) / s) * s + min();
		}
		return Math.max(min(), Math.min(max(), out));
	}
}
