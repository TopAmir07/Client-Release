package dev.topclient.client.top.ui;

import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.gui.model.GuiModule;

import static dev.topclient.client.top.TopTheme.*;

/**
 * One mod in the grid: favourite star, big icon, name, OPTIONS + gear, and the status bar along
 * the bottom (ENABLED / DISABLED / LOCKED). Built once per module and reused every frame.
 */
public final class ModCard {
	public enum State { ENABLED, DISABLED, LOCKED, OPTIONS_ONLY }

	public final GuiModule module;
	public final String category;
	public final boolean locked;
	private final Anim hover = new Anim(), on = new Anim(), star = new Anim(), opt = new Anim(), gear = new Anim(), bar = new Anim();
	private final String upperName;

	public static final int A_TOGGLE = 0, A_STAR = 1, A_OPTIONS = 2;

	public ModCard(GuiModule module, String category, boolean locked) {
		this.module = module;
		this.category = category;
		this.locked = locked;
		this.upperName = module.name().toUpperCase(java.util.Locale.ROOT);
		on.v = state() == State.ENABLED ? 1F : 0F;
	}

	public State state() {
		if (locked) {
			return State.LOCKED;
		}
		if (!module.toggleable()) {
			return State.OPTIONS_ONLY;
		}
		return module.enabled() ? State.ENABLED : State.DISABLED;
	}

	/**
	 * Grid card. base is the screen's first action number for cards (base + A_TOGGLE etc.), index
	 * this card's position, fav whether it is starred.
	 */
	public void draw(Ui ui, int x, int y, int w, int h, int base, int index, boolean fav) {
		boolean over = ui.over(x, y, w, h);
		float hv = hover.to(ui, over, 14F);
		State st = state();
		float onV = on.to(ui, st == State.ENABLED, 12F);
		int lift = Math.round(hv * 2);
		y -= lift;

		Draw.round(ui.ctx, x, y, w, h, 10, argb(Math.round(225 + 30 * hv), mix(CARD, CARD_HOVER, hv)));
		Draw.outline(ui.ctx, x, y, w, h, 10, 1, argb(255, mix(mix(BORDER, BORDER_HOVER, hv), ACCENT, onV * 0.35F)));

		// favourite star
		float sv = star.to(ui, ui.over(x + 6, y + 6, 26, 26), 16F);
		Icons.drawCentered(ui.ctx, Icons.ui(fav ? "star" : "star-outline"), x + 19, y + 19, 16,
				argb(255, fav ? ACCENT : mix(TEXT_MUTE, TEXT, sv)));
		ui.hit(x + 6, y + 6, 26, 26, base + A_STAR, index);

		// big icon and name
		int barH = 28;
		int iconCy = y + 18 + (h - barH - 72) / 2 + 8;
		Icons.drawModule(ui.ctx, module.name(), x + w / 2F, iconCy, 34 + hv * 3, argb(255, mix(TEXT_DIM, mix(TEXT, ACCENT, onV * 0.5F), Math.max(hv, onV))));
		Draw.textCenter(ui.ctx, Draw.fit(upperName, 12.5F, w - 16, true), x + w / 2F, iconCy + 32, 12.5F, argb(255, TEXT), true);

		// OPTIONS + gear
		int by = y + h - barH - 34;
		int gw = 26, ow = Math.min(110, w - gw - 30);
		int ox = x + (w - ow - gw - 6) / 2;
		boolean hasOptions = !module.settings().isEmpty() || module.onOpen() != null;
		if (hasOptions) {
			float ov = opt.to(ui, ui.over(ox, by, ow, 24), 16F);
			Draw.round(ui.ctx, ox, by, ow, 24, 6, argb(235, mix(PANEL2, CARD_HOVER, ov)));
			Draw.outline(ui.ctx, ox, by, ow, 24, 6, 1, argb(255, mix(BORDER, BORDER_HOVER, ov)));
			Draw.textCenter(ui.ctx, "OPTIONS", ox + ow / 2F, by + 12, 11, argb(255, mix(TEXT_DIM, TEXT, ov)), true);
			ui.hit(ox, by, ow, 24, base + A_OPTIONS, index);
			int gx = ox + ow + 6;
			float gv = gear.to(ui, ui.over(gx, by, gw, 24), 16F);
			Draw.round(ui.ctx, gx, by, gw, 24, 6, argb(235, mix(PANEL2, CARD_HOVER, gv)));
			Draw.outline(ui.ctx, gx, by, gw, 24, 6, 1, argb(255, mix(BORDER, BORDER_HOVER, gv)));
			Icons.drawCentered(ui.ctx, "cog-outline", gx + gw / 2F, by + 12, 14, argb(255, mix(TEXT_MUTE, TEXT, gv)));
			ui.hit(gx, by, gw, 24, base + A_OPTIONS, index);
		}

		// status bar across the bottom
		int bY = y + h - barH;
		int col;
		String label;
		switch (st) {
			case ENABLED -> {
				col = ENABLED;
				label = "ENABLED";
			}
			case DISABLED -> {
				col = DISABLED;
				label = "DISABLED";
			}
			case LOCKED -> {
				col = LOCKED;
				label = "LOCKED";
			}
			default -> {
				col = mix(LOCKED, ACCENT, 0.35F);
				label = "OPTIONS ONLY";
			}
		}
		boolean clickable = st == State.ENABLED || st == State.DISABLED;
		float bv = bar.to(ui, clickable && ui.over(x, bY, w, barH), 16F);
		int fill = mix(col, 0xFFFFFF, bv * 0.12F);
		Draw.roundSideBottom(ui.ctx, x, bY, w, barH, 10, argb(clickable ? 235 : 160, fill));
		if (st == State.LOCKED) {
			Icons.drawCentered(ui.ctx, Icons.ui("lock"), x + w / 2F - Draw.width(label, 11.5F, true) / 2F - 11, bY + barH / 2F, 12, argb(255, TEXT_DIM));
		}
		Draw.textCenter(ui.ctx, label, x + w / 2F, bY + barH / 2F, 11.5F, argb(255, luminance(col) > 0.65F ? 0x0A0A0A : 0xFFFFFF), true);
		if (clickable) {
			ui.hit(x, bY, w, barH, base + A_TOGGLE, index);
		}
	}

	/** List view: one compact row. */
	public void drawRow(Ui ui, int x, int y, int w, int h, int base, int index, boolean fav) {
		boolean over = ui.over(x, y, w, h);
		float hv = hover.to(ui, over, 14F);
		State st = state();
		float onV = on.to(ui, st == State.ENABLED, 12F);
		Draw.round(ui.ctx, x, y, w, h, 8, argb(225, mix(CARD, CARD_HOVER, hv)));
		Draw.outline(ui.ctx, x, y, w, h, 8, 1, argb(255, mix(BORDER, BORDER_HOVER, hv)));
		Icons.drawCentered(ui.ctx, Icons.ui(fav ? "star" : "star-outline"), x + 18, y + h / 2F, 14, argb(255, fav ? ACCENT : TEXT_MUTE));
		ui.hit(x + 6, y + 4, 24, h - 8, base + A_STAR, index);
		Icons.drawModule(ui.ctx, module.name(), x + 48, y + h / 2F, 20, argb(255, mix(TEXT_DIM, ACCENT, onV * 0.7F)));
		Draw.textMid(ui.ctx, module.name(), x + 68, y + h / 2F - 7, 13, argb(255, TEXT), true);
		Draw.textMid(ui.ctx, Draw.fit(module.description(), 11.5F, w - 330), x + 68, y + h / 2F + 9, 11.5F, argb(255, TEXT_MUTE));
		int sw = 96;
		int sx = x + w - sw - 10;
		int col = st == State.ENABLED ? ENABLED : st == State.DISABLED ? DISABLED : LOCKED;
		String label = st == State.ENABLED ? "ENABLED" : st == State.DISABLED ? "DISABLED" : st == State.LOCKED ? "LOCKED" : "OPTIONS";
		Draw.round(ui.ctx, sx, y + (h - 24) / 2, sw, 24, 6, argb(225, col));
		Draw.textCenter(ui.ctx, label, sx + sw / 2F, y + h / 2F, 11, argb(255, luminance(col) > 0.65F ? 0x0A0A0A : 0xFFFFFF), true);
		if (st == State.ENABLED || st == State.DISABLED) {
			ui.hit(sx, y + (h - 24) / 2, sw, 24, base + A_TOGGLE, index);
		}
		if (!module.settings().isEmpty() || module.onOpen() != null) {
			int ox = sx - 96;
			Draw.round(ui.ctx, ox, y + (h - 24) / 2, 86, 24, 6, argb(235, PANEL2));
			Draw.outline(ui.ctx, ox, y + (h - 24) / 2, 86, 24, 6, 1, argb(255, BORDER));
			Draw.textCenter(ui.ctx, "OPTIONS", ox + 43, y + h / 2F, 11, argb(255, TEXT_DIM), true);
			ui.hit(ox, y + (h - 24) / 2, 86, 24, base + A_OPTIONS, index);
		}
	}
}
