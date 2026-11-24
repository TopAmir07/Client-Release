package dev.topclient.client.gui;

import dev.topclient.client.module.modules.client.ClickGUI;

import java.awt.Color;

public final class GuiTheme {
	private GuiTheme() {
	}

	private static final int BASE_WINDOW = 0x131116, BASE_SIDEBAR = 0x0E0C10, BASE_CARD = 0x1B181F, BASE_CARD_HOVER = 0x221E27,
			BASE_FIELD = 0x0F0D12, BASE_BORDER = 0x2E2836, BASE_BORDER_HOVER = 0x433B4D;
	public static int WINDOW = BASE_WINDOW;
	public static int SIDEBAR = BASE_SIDEBAR;
	public static int CARD = BASE_CARD;
	public static int CARD_HOVER = BASE_CARD_HOVER;
	public static int FIELD = BASE_FIELD;
	public static int BORDER = BASE_BORDER;
	public static int BORDER_HOVER = BASE_BORDER_HOVER;
	public static final int TEXT = 0xF5F2F7;
	public static final int TEXT_DIM = 0xB9B1C1;
	public static final int TEXT_MUTE = 0x8C8497;
	public static final int OK = 0x34D399;
	public static final int WARN = 0xFBBF24;
	public static final int DANGER = 0xF87171;
	public static final int CLOVER = 0x34D399;

	private static final int[][] THEMES = {
			{0xFF8A3D, 0xFF4E6A, 0x2A160C},
			{0x8B5CF6, 0xEC4899, 0x1E1432},
			{0xEF4444, 0xF97316, 0x2C1013},
			{0x06B6D4, 0x3B82F6, 0x0C1B2C},
			{0x22C55E, 0xA3E635, 0x0F2318},
			{0xE5E7EB, 0x9CA3AF, 0x18181B},
			{0x6366F1, 0x38BDF8, 0x101530},
			{0xF9A8D4, 0xF472B6, 0x2A1522},
			{0xFB923C, 0xFACC15, 0x2B1A0D},
			{0xFACC15, 0xD97706, 0x231A08},
			{0x22D3EE, 0xA855F7, 0x150C28},
			{0xA5F3FC, 0x93C5FD, 0x0E1A26},
			{0xFF5F1F, 0xDC2626, 0x2C0E07},
			{0x6EE7B7, 0x2DD4BF, 0x0C2420},
			{0xC084FC, 0x7C3AED, 0x1D1032},
			{0xD6A77A, 0xA16207, 0x22170E},
			{0xF0ABFC, 0x22D3EE, 0x1A0F2A},
			{0x34D399, 0x10B981, 0x0B1F14},
			{0xFACC15, 0xEF4444, 0x201406},
	};

	public static int[] swatch(int i) {
		return i >= 0 && i < THEMES.length ? THEMES[i] : null;
	}

	public static int TINT = 0x131116;

	public static void refresh() {
		int i = ClickGUI.themeMode.getModeIndex();
		int a = accent();
		int tint = i >= 0 && i < THEMES.length ? THEMES[i][2] : Draw.mix(0x0A090C, a, 0.16F) & 0xFFFFFF;
		tint = Draw.mix(tint, a, 0.05F) & 0xFFFFFF;
		TINT = tint;
		WINDOW = Draw.mix(BASE_WINDOW, tint, 0.9F) & 0xFFFFFF;
		SIDEBAR = Draw.mix(BASE_SIDEBAR, Draw.mix(tint, 0x000000, 0.28F), 0.9F) & 0xFFFFFF;
		CARD = Draw.mix(BASE_CARD, Draw.mix(tint, 0xFFFFFF, 0.05F), 0.85F) & 0xFFFFFF;
		CARD_HOVER = Draw.mix(BASE_CARD_HOVER, Draw.mix(tint, 0xFFFFFF, 0.09F), 0.85F) & 0xFFFFFF;
		FIELD = Draw.mix(BASE_FIELD, Draw.mix(tint, 0x000000, 0.35F), 0.85F) & 0xFFFFFF;
		BORDER = Draw.mix(BASE_BORDER, Draw.mix(tint, a, 0.22F), 0.75F) & 0xFFFFFF;
		BORDER_HOVER = Draw.mix(BASE_BORDER_HOVER, Draw.mix(tint, a, 0.4F), 0.75F) & 0xFFFFFF;
	}

	public static int accent() {
		int i = ClickGUI.themeMode.getModeIndex();
		if (i >= 0 && i < THEMES.length) {
			return THEMES[i][0];
		}
		return (ClickGUI.red.getValueInt() << 16) | (ClickGUI.green.getValueInt() << 8) | ClickGUI.blue.getValueInt();
	}

	public static int accent2() {
		int i = ClickGUI.themeMode.getModeIndex();
		if (i >= 0 && i < THEMES.length) {
			return THEMES[i][1];
		}
		int c = accent();
		float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
		return Color.HSBtoRGB((hsb[0] + 0.08F) % 1F, hsb[1], Math.min(1F, hsb[2] * 1.05F)) & 0xFFFFFF;
	}

	public static int onAccent() {
		int c = accent();
		double lum = 0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF);
		return lum > 170 ? 0x15121A : 0xFFFFFF;
	}

	public static int windowAlpha() {
		return Math.max(150, Math.min(255, ClickGUI.alphaWindow.getValueInt()));
	}

	public static int radius() {
		return 4 + ClickGUI.roundQuads.getValueInt() * 2;
	}
}
