package dev.topclient.client.top;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.module.Setting;
import dev.topclient.client.clover.module.UtilityModules.CrosshairColor;
import dev.topclient.client.clover.module.UtilityModules.CrosshairColor.Look;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.ColorPicker;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.TextField;
import dev.topclient.client.top.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

import static dev.topclient.client.top.TopTheme.*;

/**
 * The crosshair editor, Lunar style: presets and drawings on the left, a live preview (or the
 * pixel grid when drawing your own) in the middle, and colour, opacity, size, gap, thickness and
 * outline on the right. Every change shows in game straight away.
 */
public final class CrosshairScreen extends TopScreen {
	private static final int A_PRESET = 1, A_BUILTIN = 2, A_SAVED = 3, A_ENABLE = 4, A_SLIDER = 5, A_TOGGLE = 6, A_SWATCH = 7, A_CUSTOM = 8,
			A_PICKER = 9, A_GRID = 10, A_TOOL = 11, A_NAME = 12, A_SAVE = 13, A_SAVENEW = 14, A_DELETE = 15, A_NEW = 16, A_RESET = 17,
			A_BG = 18, A_LSCROLL = 19, A_RSCROLL = 20, A_EDIT = 21, A_TAB = 22, A_UNDO = 23, A_REDO = 24, A_GRID_SIZE = 25, A_SHARE = 26, A_FAV = 27, A_FRIENDLY_COLOR = 28, A_ENEMY_COLOR = 29, A_SHARE_COPY = 30;

	private static final String[] SLIDERS = {"alpha", "size", "gap", "thick", "scale"};
	private static final String[] SLIDER_LABELS = {"Opacity", "Size", "Gap", "Thickness", "Custom scale"};
	private static final String[] TOGGLES = {"outline", "dot", "dynamic", "vanilla_blending", "dynamic_bow", "dynamic_attack", "spectator", "health_shift", "custom_scale", "friendly", "enemy", "elytra", "third_person"};
	private static final String[] TOGGLE_LABELS = {"Black outline", "Centre dot", "Dynamic movement", "Vanilla blending", "Dynamic bow", "Dynamic attack", "Visible in spectator", "Health color shift", "Use custom scale", "Custom friendly crosshair", "Custom enemy crosshair", "Elytra status crosshair", "Show in third person"};
	private static final int[] SWATCHES = {0xFFFFFF, 0x000000, 0xFF4A4A, 0xFF9A3C, 0xFFE14A, 0x4ADE80, 0x22D3EE, 0x3B82F6, 0xA855F7, 0xF472B6};
	private static final String[] TOOLS = {"Pencil", "Eraser", "Mirror ↔", "Mirror ↕", "Invert", "Clear"};
	private static final String[] BGS = {"Sky", "Grass", "Stone", "Dark"};

	private final Scroll leftScroll = new Scroll(), rightScroll = new Scroll();
	private final Anim hEnable = new Anim();
	private final Anim[] hTiles = new Anim[80], tToggles = new Anim[13], hBtn = new Anim[12];
	private final ColorPicker picker = new ColorPicker();
	private final TextField name = new TextField("Name your crosshair", 24);
	private final TextField share = new TextField("Paste share code here", 120);
	private int activeTab = 0;
	private boolean shareOpen;
	private boolean pickerOpen, eraser, mirrorH = true, mirrorV = true;
	private boolean paintValue = true;
	private String editingId;
	private int bg;
	private final int[] sliderX = new int[5], sliderW = new int[5];
	private int gridX, gridY, cell;
	private String tip;

	public CrosshairScreen(Screen parent) {
		super(parent, "Crosshair", "Presets, your own drawings and every setting, with a live preview");
		for (int i = 0; i < hTiles.length; i++) {
			hTiles[i] = new Anim();
		}
		for (int i = 0; i < tToggles.length; i++) {
			tToggles[i] = new Anim();
		}
		for (int i = 0; i < hBtn.length; i++) {
			hBtn[i] = new Anim();
		}
		name.text = "My crosshair";
		bg = Config.getInt("top.xhair.bg", 0);
	}

	@Override
	protected int prefW() {
		return 1220;
	}

	@Override
	protected int prefH() {
		return 740;
	}

	private static boolean drawing() {
		return CrosshairColor.instance.enabled() && CrosshairColor.current().shape() == CrosshairColor.SHAPE_DRAWING;
	}

	private static float gs() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void drawBody(int x, int y, int w, int h) {
		tip = null;
		int lw = activeTab == 0 ? 0 : 268, rw = 320;
		if (lw > 0) {
			drawLeft(x, y, lw, h);
			Draw.rect(ui.ctx, x + lw, y, 1, h, argb(255, BORDER));
		}
		drawRight(x + w - rw, y, rw, h);
		Draw.rect(ui.ctx, x + w - rw - 1, y, 1, h, argb(255, BORDER));
		drawCentre(x + lw + 1, y, w - lw - rw - 2, h);
	}

	@Override
	protected void drawOverlay() {
		if (tip != null) {
			Widgets.tooltip(ui, tip);
		}
	}

	// ------------------------------------------------------------------ left: presets and drawings

	private void drawLeft(int x, int y, int w, int h) {
		int cols = 3, gap = 8, tile = (w - 28 - gap * (cols - 1)) / cols;
		List<Crosshairs.Drawing> saved = Crosshairs.saved();
		int rowsP = (Crosshairs.PRESETS.size() + cols - 1) / cols;
		int rowsB = (Crosshairs.BUILT_IN.size() + cols - 1) / cols;
		int rowsS = (saved.size() + 1 + cols - 1) / cols;
		int content = 3 * 34 + (rowsP + rowsB + rowsS) * (tile + gap) + 20;
		leftScroll.setMax(content - h);
		leftScroll.update(ui);
		ui.clip(x, y, w, h);
		int cy = y + 16 - Math.round(leftScroll.pos);
		Look cur = CrosshairColor.current();
		int idx = 0;
		cy = section(x, cy, "PRESETS");
		for (int i = 0; i < Crosshairs.PRESETS.size(); i++) {
			Crosshairs.Preset p = Crosshairs.PRESETS.get(i);
			Look l = withColour(p.look(), cur);
			boolean on = sameLook(p.look(), cur);
			tile(x + 14 + (i % cols) * (tile + gap), cy + (i / cols) * (tile + gap), tile, l, p.name(), on, idx++, A_PRESET, i);
		}
		cy += rowsP * (tile + gap) + 10;
		cy = section(x, cy, "SHAPES");
		long[] curBits = Crosshairs.currentBits();
		for (int i = 0; i < Crosshairs.BUILT_IN.size(); i++) {
			Crosshairs.Drawing d = Crosshairs.BUILT_IN.get(i);
			Look l = drawingLook(cur, d.bits());
			boolean on = cur.shape() == CrosshairColor.SHAPE_DRAWING && java.util.Arrays.equals(curBits, d.bits()) && editingId == null;
			tile(x + 14 + (i % cols) * (tile + gap), cy + (i / cols) * (tile + gap), tile, l, d.name(), on, idx++, A_BUILTIN, i);
		}
		cy += rowsB * (tile + gap) + 10;
		cy = section(x, cy, "MY CROSSHAIRS");
		for (int i = 0; i <= saved.size(); i++) {
			int tx = x + 14 + (i % cols) * (tile + gap), ty = cy + (i / cols) * (tile + gap);
			if (i == saved.size()) { // "+ New" tile
				float hv = hTiles[Math.min(idx, hTiles.length - 1)].to(ui, ui.over(tx, ty, tile, tile), 14F);
				Draw.round(ui.ctx, tx, ty, tile, tile, 10, argb(150, mix(PANEL, CARD_HOVER, hv)));
				Draw.outline(ui.ctx, tx, ty, tile, tile, 10, 1, argb(255, mix(BORDER, ACCENT, hv)));
				Draw.textCenter(ui.ctx, "+", tx + tile / 2F, ty + tile / 2F - 8, 26, argb(255, mix(TEXT_MUTE, ACCENT, hv)), true);
				Draw.textCenter(ui.ctx, "New drawing", tx + tile / 2F, ty + tile - 14, 10.5F, argb(255, TEXT_MUTE));
				ui.hit(tx, ty, tile, tile, A_NEW, 0);
				break;
			}
			Crosshairs.Drawing d = saved.get(i);
			boolean on = d.id().equals(editingId) && cur.shape() == CrosshairColor.SHAPE_DRAWING;
			tile(tx, ty, tile, drawingLook(cur, d.bits()), d.name(), on, idx++, A_SAVED, i);
		}
		ui.unclip();
		Widgets.scrollbar(ui, x + w - 8, y + 6, h - 12, leftScroll.pos, leftScroll.max, h, A_LSCROLL, 0);
	}

	private int section(int x, int y, String label) {
		Draw.text(ui.ctx, label, x + 16, y, 11, argb(255, TEXT_MUTE), true);
		return y + 24;
	}

	private void tile(int x, int y, int s, Look l, String label, boolean on, int idx, int action, int arg) {
		if (!ui.visible(y, s)) {
			return;
		}
		boolean over = ui.over(x, y, s, s);
		float hv = hTiles[Math.min(idx, hTiles.length - 1)].to(ui, over || on, 14F);
		Draw.round(ui.ctx, x, y, s, s, 10, argb(255, mix(0x16161C, 0x23232D, hv)));
		Draw.outline(ui.ctx, x, y, s, s, 10, on ? 2 : 1, argb(255, on ? ACCENT : mix(BORDER, BORDER_HOVER, hv)));
		float zoom = 2.2F;
		CrosshairColor.drawLook(ui.ctx, x + s / 2F, y + s / 2F - 4, zoom / ui.scale, gs(), l, 0F);
		Draw.textCenter(ui.ctx, Draw.fit(label, 10F, s - 6), x + s / 2F, y + s - 9, 10F, argb(255, on ? TEXT : TEXT_MUTE));
		ui.hit(x, y, s, s, action, arg);
		if (over) {
			tip = label;
		}
	}

	private static Look withColour(Look p, Look cur) {
		return new Look(p.shape() == 0 ? 1 : p.shape(), p.size(), p.gap(), p.thick(), p.outline(), p.dot(), cur.rgb(), cur.alpha(), null);
	}

	private static Look drawingLook(Look cur, long[] bits) {
		return new Look(CrosshairColor.SHAPE_DRAWING, cur.size(), cur.gap(), Math.max(1, Math.min(2, cur.thick())), cur.outline(), false, cur.rgb(), cur.alpha(), bits);
	}

	private static boolean sameLook(Look a, Look b) {
		return a.shape() == b.shape() && a.size() == b.size() && a.gap() == b.gap() && a.thick() == b.thick() && a.dot() == b.dot();
	}

	// ------------------------------------------------------------------ centre: preview or drawing grid

	private void drawCentre(int x, int y, int w, int h) {
		int pad = 18;
		drawTabs(x + pad, y + 4);
		int px = x + pad, py = y + 42, pw = w - pad * 2;
		boolean draw = activeTab == 2 || drawing();
		int ph = draw ? Math.min(h - pad * 2 - 170, pw) : h - pad * 2 - 90;
		// background of a pretend game view
		int top, bottom;
		switch (bg) {
			case 1 -> {
				top = 0xFF5E9B46;
				bottom = 0xFF2F5A22;
			}
			case 2 -> {
				top = 0xFF8A8A8A;
				bottom = 0xFF5A5A5A;
			}
			case 3 -> {
				top = 0xFF0C0C10;
				bottom = 0xFF050507;
			}
			default -> {
				top = 0xFF5E8FD8;
				bottom = 0xFFB9D4F5;
			}
		}
		Draw.round(ui.ctx, px, py, pw, ph, 12, argb(255, 0x000000));
		Draw.gradientV(ui.ctx, px + 1, py + 1, pw - 2, ph - 2, top, bottom);
		Draw.outline(ui.ctx, px, py, pw, ph, 12, 1, argb(255, BORDER));
		Look cur = CrosshairColor.current();
		if (!CrosshairColor.instance.enabled() || cur.shape() == 0) {
			Draw.textCenter(ui.ctx, CrosshairColor.instance.enabled() ? "Game default crosshair" : "Crosshair mod is off: the game's own crosshair is used",
					px + pw / 2F, py + ph / 2F, 13, argb(220, 0xFFFFFF), true);
		} else if (!draw) {
			// actual size in the middle, a big zoomed copy on the left
			CrosshairColor.drawLook(ui.ctx, px + pw / 2F, py + ph / 2F, 1F / ui.scale, gs(), cur, 0F);
			Draw.textCenter(ui.ctx, "In game size", px + pw / 2F, py + ph / 2F + 46, 11, argb(200, 0xFFFFFF), true);
			CrosshairColor.drawLook(ui.ctx, px + pw / 5F, py + ph / 2F, 4F / ui.scale, gs(), cur, 0F);
			Draw.textCenter(ui.ctx, "Zoomed x4", px + pw / 5F, py + ph - 22, 11, argb(200, 0xFFFFFF), true);
			CrosshairColor.drawLook(ui.ctx, px + pw * 0.8F, py + ph / 2F, 2F / ui.scale, gs(), cur, 0F);
			Draw.textCenter(ui.ctx, "Zoomed x2", px + pw * 0.8F, py + ph - 22, 11, argb(200, 0xFFFFFF), true);
		} else {
			drawGrid(px, py, pw, ph, cur);
		}
		// background chips under the preview
		int cx = px, cy = py + ph + 12;
		Draw.textMid(ui.ctx, "Background", cx, cy + 14, 11.5F, argb(255, TEXT_MUTE), true);
		cx += Draw.width("Background", 11.5F, true) + 12;
		for (int i = 0; i < BGS.length; i++) {
			cx += Widgets.chip(ui, cx, cy + 1, 26, BGS[i], i == bg, ui.over(cx, cy + 1, Widgets.chipWidth(BGS[i]), 26) ? 1F : 0F, A_BG, i) + 6;
		}
		if (draw) {
			drawTools(px, cy + 40, pw);
		}
	}

	private void drawTabs(int x, int y) {
		String[] tabs = {"SIMPLE", "PRESETS", "CUSTOM"};
		int bx = x;
		for (int i = 0; i < tabs.length; i++) {
			int width = Widgets.chipWidth(tabs[i]) + 18;
			Widgets.chip(ui, bx, y, 30, tabs[i], activeTab == i,
					ui.over(bx, y, width, 30) ? 1F : 0F, A_TAB, i);
			bx += width + 8;
		}
	}

	private void drawGrid(int px, int py, int pw, int ph, Look cur) {
		long[] bits = Crosshairs.currentBits();
		int g = Crosshairs.gridSize(), offset = Crosshairs.gridOffset();
		cell = Math.max(8, Math.min((ph - 40) / g, (pw - 260) / g));
		int gw = cell * g;
		gridX = px + 24;
		gridY = py + (ph - gw) / 2;
		Draw.rect(ui.ctx, gridX - 1, gridY - 1, gw + 2, gw + 2, argb(160, 0x000000));
		for (int yy = 0; yy < g; yy++) {
			for (int xx = 0; xx < g; xx++) {
				boolean on = Crosshairs.get(bits, xx + offset, yy + offset);
				boolean centre = xx == g / 2 && yy == g / 2;
				int c = on ? argb(255, cur.rgb()) : ((xx + yy) % 2 == 0 ? argb(110, 0x000000) : argb(80, 0x000000));
				Draw.rect(ui.ctx, gridX + xx * cell, gridY + yy * cell, cell - 1, cell - 1, c);
				if (centre && !on) {
					Draw.rect(ui.ctx, gridX + xx * cell + cell / 2 - 1, gridY + yy * cell + cell / 2 - 1, 2, 2, argb(160, 0xFFFFFF));
				}
			}
		}
		// mirror guides
		if (mirrorH) {
			Draw.rect(ui.ctx, gridX + (g / 2) * cell + cell / 2, gridY, 1, gw, argb(90, ACCENT));
		}
		if (mirrorV) {
			Draw.rect(ui.ctx, gridX, gridY + (g / 2) * cell + cell / 2, gw, 1, argb(90, ACCENT));
		}
		ui.dragHit(gridX, gridY, gw, gw, A_GRID, 0);
		if (ui.over(gridX, gridY, gw, gw)) {
			tip = "Left click / drag: draw   Right click: erase one pixel";
		}
		// live previews on the right of the grid
		int rx = gridX + gw + 30, rw = px + pw - rx - 12;
		int mid = rx + rw / 2;
		CrosshairColor.drawLook(ui.ctx, mid, gridY + gw * 0.3F, 1F / ui.scale, gs(), cur, 0F);
		Draw.textCenter(ui.ctx, "In game size", mid, gridY + gw * 0.3F + 40, 11, argb(210, 0xFFFFFF), true);
		CrosshairColor.drawLook(ui.ctx, mid, gridY + gw * 0.72F, 3F / ui.scale, gs(), cur, 0F);
		Draw.textCenter(ui.ctx, "Zoomed x3", mid, gridY + gw - 6, 11, argb(210, 0xFFFFFF), true);
	}

	private void drawTools(int x, int y, int w) {
		int bx = x;
		for (int i = 0; i < TOOLS.length; i++) {
			boolean on = (i == 0 && !eraser) || (i == 1 && eraser) || (i == 2 && mirrorH) || (i == 3 && mirrorV);
			bx += Widgets.chip(ui, bx, y, 30, TOOLS[i], on, ui.over(bx, y, Widgets.chipWidth(TOOLS[i]), 30) ? 1F : 0F, A_TOOL, i) + 6;
		}
		int ry = y + 42;
		name.draw(ui, x, ry, Math.min(200, w / 3), 36, null, A_NAME, 0);
		int bx2 = x + Math.min(240, w / 3) + 10;
		bx2 += smallButton(bx2, ry, 36, editingId == null ? "Save" : "Save changes", true, hBtn[0], A_SAVE, 0) + 8;
		if (editingId != null) {
			bx2 += smallButton(bx2, ry, 36, "Save as new", false, hBtn[1], A_SAVENEW, 0) + 8;
			smallButton(bx2, ry, 36, "Delete", false, hBtn[2], A_DELETE, 0);
		}
		int ty = ry + 44, tx = x;
		tx += smallButton(tx, ty, 30, "Undo", false, hBtn[3], A_UNDO, 0) + 6;
		tx += smallButton(tx, ty, 30, "Redo", false, hBtn[4], A_REDO, 0) + 6;
		tx += smallButton(tx, ty, 30, "Grid " + Crosshairs.gridSize(), false, hBtn[5], A_GRID_SIZE, 0) + 6;
		smallButton(tx, ty, 30, "Share", false, hBtn[6], A_SHARE, 0);
		if (shareOpen) {
			share.draw(ui, x, ty + 38, w - 10, 34, null, A_SHARE + 100, 1);
			String code = Crosshairs.exportShareCode();
			Draw.text(ui.ctx, "Paste a code above and press ENTER to import", x, ty + 78, 9F, argb(255, TEXT_MUTE));
			smallButton(x, ty + 94, 30, "Copy share code", false, hBtn[7], A_SHARE_COPY, 0);
		}
	}

	// ------------------------------------------------------------------ right: settings

	private void drawRight(int x, int y, int w, int h) {
		int content = 1280 + (pickerOpen ? ColorPicker.HEIGHT + 20 : 0);
		rightScroll.setMax(content - h);
		rightScroll.update(ui);
		ui.clip(x, y, w, h);
		int cx = x + 20, cw = w - 40;
		int cy = y + 18 - Math.round(rightScroll.pos);
		// on / off
		boolean on = CrosshairColor.instance.enabled();
		Draw.textMid(ui.ctx, "Custom crosshair", cx, cy + 11, 13.5F, argb(255, TEXT), true);
		Widgets.toggle(ui, cx + cw - 40, cy, hEnable.to(ui, on, 14F), A_ENABLE, 0);
		cy += 40;
		// colour
		Draw.text(ui.ctx, "COLOUR", cx, cy, 11, argb(255, TEXT_MUTE), true);
		cy += 20;
		int cur = CrosshairColor.current().rgb();
		int sw = (cw - 9 * 6) / 10;
		for (int i = 0; i < SWATCHES.length; i++) {
			int sx = cx + i * (sw + 6);
			boolean sel = (cur & 0xFFFFFF) == SWATCHES[i];
			Draw.round(ui.ctx, sx, cy, sw, sw, 6, argb(255, SWATCHES[i]));
			Draw.outline(ui.ctx, sx, cy, sw, sw, 6, sel ? 2 : 1, argb(255, sel ? ACCENT : BORDER));
			ui.hit(sx, cy, sw, sw, A_SWATCH, i);
		}
		cy += sw + 10;
		int fav = Config.getInt("top.xhair.favorite", -1);
		if (fav >= 0 && fav <= 0xFFFFFF) {
			Draw.round(ui.ctx, cx, cy, 24, 24, 5, argb(255, fav));
			Draw.outline(ui.ctx, cx, cy, 24, 24, 5, 1, argb(255, BORDER));
		}
		Widgets.button(ui, cx + 30, cy, 38, 28, "★", false, ui.over(cx + 30, cy, 38, 28) ? 1F : 0F, A_FAV, 0);
		Widgets.button(ui, cx + 74, cy, cw - 74, 28, pickerOpen ? "Hide colour picker" : "Pick colour / enter HEX…", false, ui.over(cx + 74, cy, cw - 74, 28) ? 1F : 0F, A_CUSTOM, 0);
		cy += 38;
		if (pickerOpen) {
			cy += picker.draw(ui, cx, cy, cw, A_PICKER) + 16;
		}
		// sliders
		for (int i = 0; i < SLIDERS.length; i++) {
			if (i == 4 && !CrosshairColor.setting("custom_scale").getBool()) continue;
			Setting s = CrosshairColor.setting(SLIDERS[i]);
			float v = s.getFloat();
			String label = SLIDER_LABELS[i] + (i == 3 && drawing() ? " (pixel size)" : "");
			Draw.textMid(ui.ctx, label, cx, cy + 8, 12.5F, argb(255, TEXT_DIM), true);
			String val = i == 0 ? Math.round(v * 100) + "%" : (i == 4 ? String.format(java.util.Locale.ROOT, "%.2fx", v) : String.valueOf(Math.round(v)));
			Draw.textRight(ui.ctx, val, cx + cw, cy + 8, 12.5F, argb(255, TEXT));
			sliderX[i] = cx + 8;
			sliderW[i] = cw - 16;
			Widgets.slider(ui, sliderX[i], cy + 32, sliderW[i], (v - s.min) / (s.max - s.min), A_SLIDER, i);
			cy += 56;
		}
		// toggles
		for (int i = 0; i < TOGGLES.length; i++) {
			Setting s = CrosshairColor.setting(TOGGLES[i]);
			Draw.textMid(ui.ctx, TOGGLE_LABELS[i], cx, cy + 11, 12.5F, argb(255, TEXT_DIM), true);
			Widgets.toggle(ui, cx + cw - 40, cy, tToggles[i].to(ui, s.getBool(), 14F), A_TOGGLE, i);
			cy += 38;
		}
		cy += 8;
		Widgets.button(ui, cx, cy, cw, 30, "Friendly colour: " + String.format("#%06X", CrosshairColor.setting("friendly_color").getColor()), false, ui.over(cx, cy, cw, 30) ? 1F : 0F, A_FRIENDLY_COLOR, 0);
		cy += 36;
		Widgets.button(ui, cx, cy, cw, 30, "Enemy colour: " + String.format("#%06X", CrosshairColor.setting("enemy_color").getColor()), false, ui.over(cx, cy, cw, 30) ? 1F : 0F, A_ENEMY_COLOR, 0);
		cy += 38;
		Widgets.button(ui, cx, cy, cw, 34, "Reset to default", false, ui.over(cx, cy, cw, 34) ? 1F : 0F, A_RESET, 0);
		ui.unclip();
		Widgets.scrollbar(ui, x + w - 8, y + 6, h - 12, rightScroll.pos, rightScroll.max, h, A_RSCROLL, 0);
	}

	// ------------------------------------------------------------------ actions

	private void enableCustom() {
		if (!CrosshairColor.instance.enabled()) {
			CrosshairColor.instance.setEnabled(true);
		}
	}

	private void useDrawing(long[] bits) {
		enableCustom();
		Crosshairs.setCurrent(bits.clone());
		CrosshairColor.setting("shape").setMode(CrosshairColor.SHAPE_DRAWING);
		if (CrosshairColor.setting("thick").getFloat() > 3) {
			CrosshairColor.setting("thick").setFloat(2);
		}
	}

	@Override
	protected void onAction(int action, int arg, int button) {
		if (action != A_NAME && action != A_PICKER) {
			name.focused = false;
			picker.hex.focused = false;
		}
		if (action != A_SHARE) share.focused = false;
		switch (action) {
			case A_PRESET -> {
				enableCustom();
				CrosshairColor.apply(Crosshairs.PRESETS.get(arg).look());
				editingId = null;
				UiSounds.click();
			}
			case A_BUILTIN -> {
				Crosshairs.Drawing d = Crosshairs.BUILT_IN.get(arg);
				useDrawing(d.bits());
				editingId = null;
				name.text = d.name();
				UiSounds.click();
			}
			case A_SAVED -> {
				List<Crosshairs.Drawing> saved = Crosshairs.saved();
				if (arg < saved.size()) {
					Crosshairs.Drawing d = saved.get(arg);
					if (button == 1) { // right click deletes
						Crosshairs.delete(d.id());
						if (d.id().equals(editingId)) {
							editingId = null;
						}
					} else {
						useDrawing(d.bits());
						editingId = d.id();
						name.text = d.name();
					}
					UiSounds.click();
				}
			}
			case A_NEW -> {
				useDrawing(Crosshairs.empty());
				editingId = null;
				name.text = "My crosshair";
				UiSounds.click();
			}
			case A_TAB -> {
			activeTab = arg;
			if (activeTab == 2) {
				if (CrosshairColor.current().shape() != CrosshairColor.SHAPE_DRAWING) useDrawing(Crosshairs.currentBits());
			} else if (activeTab == 0 && CrosshairColor.current().shape() == CrosshairColor.SHAPE_DRAWING) {
				CrosshairColor.setting("shape").setMode(1);
			}
			UiSounds.click();
		}
		case A_UNDO -> Crosshairs.undo();
		case A_REDO -> Crosshairs.redo();
		case A_GRID_SIZE -> {
			int[] sizes = {7, 9, 11, 13, 15};
			int current = Crosshairs.gridSize(), next = 7;
			for (int i = 0; i < sizes.length; i++) if (sizes[i] == current) next = sizes[(i + 1) % sizes.length];
			Crosshairs.setGridSize(next);
		}
		case A_SHARE -> {
				shareOpen = !shareOpen;
				if (shareOpen) share.text = Crosshairs.exportShareCode();
				share.focused = false;
			}
			case A_SHARE + 100 -> share.focused = true;
		case A_SHARE_COPY -> Minecraft.getInstance().keyboardHandler.setClipboard(Crosshairs.exportShareCode());
		case A_ENABLE -> CrosshairColor.instance.setEnabled(!CrosshairColor.instance.enabled());
			case A_TOGGLE -> {
				Setting s = CrosshairColor.setting(TOGGLES[arg]);
				s.setBool(!s.getBool());
				UiSounds.click();
			}
			case A_SWATCH -> {
				CrosshairColor.setting("color").setColor(SWATCHES[arg]);
				picker.open(SWATCHES[arg], c -> CrosshairColor.setting("color").setColor(c));
				UiSounds.click();
			}
			case A_FAV -> {
				Config.setInt("top.xhair.favorite", CrosshairColor.current().rgb());
				Config.saveIfNeeded(true);
				UiSounds.click();
			}
			case A_FRIENDLY_COLOR -> {
				pickerOpen = true;
				picker.open(CrosshairColor.setting("friendly_color").getColor(), c -> CrosshairColor.setting("friendly_color").setColor(c));
			}
			case A_ENEMY_COLOR -> {
				pickerOpen = true;
				picker.open(CrosshairColor.setting("enemy_color").getColor(), c -> CrosshairColor.setting("enemy_color").setColor(c));
			}
			case A_CUSTOM -> {
				pickerOpen = !pickerOpen;
				if (pickerOpen) {
					picker.open(CrosshairColor.current().rgb(), c -> CrosshairColor.setting("color").setColor(c));
				}
				UiSounds.click();
			}
			case A_PICKER -> picker.click(arg);
			case A_GRID -> {
				if (button == 1) paint(ui.mx, ui.my, false);
			}
			case A_TOOL -> {
				switch (arg) {
					case 0 -> eraser = false;
					case 1 -> eraser = true;
					case 2 -> mirrorH = !mirrorH;
					case 3 -> mirrorV = !mirrorV;
					case 4 -> {
						long[] b = Crosshairs.currentBits().clone();
						for (int yy = 0; yy < Crosshairs.GRID; yy++) {
							for (int xx = 0; xx < Crosshairs.GRID; xx++) {
								Crosshairs.set(b, xx, yy, !Crosshairs.get(b, xx, yy));
							}
						}
						Crosshairs.setCurrent(b);
					}
					case 5 -> Crosshairs.setCurrent(Crosshairs.empty());
					default -> {
					}
				}
				UiSounds.click();
			}
			case A_NAME -> name.focused = true;
			case A_SAVE -> {
				if (editingId != null) {
					Crosshairs.update(editingId, name.text, Crosshairs.currentBits());
				} else {
					editingId = Crosshairs.saveNew(name.text, Crosshairs.currentBits());
				}
				UiSounds.click();
			}
			case A_SAVENEW -> {
				String id = Crosshairs.saveNew(name.text, Crosshairs.currentBits());
				if (id != null) {
					editingId = id;
				}
				UiSounds.click();
			}
			case A_DELETE -> {
				if (editingId != null) {
					Crosshairs.delete(editingId);
					editingId = null;
				}
				UiSounds.click();
			}
			case A_RESET -> {
				CrosshairColor.instance.resetSettings();
				Config.set("top.xhair.current", null);
				editingId = null;
				pickerOpen = false;
				UiSounds.click();
			}
			case A_BG -> {
				bg = arg;
				Config.setInt("top.xhair.bg", arg);
			}
			default -> {
			}
		}
		Config.saveIfNeeded(false);
	}

	@Override
	public void onDrag(int action, int arg, double mx, double my) {
		switch (action) {
			case A_SLIDER -> {
				Setting s = CrosshairColor.setting(SLIDERS[arg]);
				float f = Widgets.sliderValue(mx, sliderX[arg], sliderW[arg]);
				float v = s.min + f * (s.max - s.min);
				v = Math.round(v / s.step) * s.step;
				s.setFloat(v);
			}
			case A_PICKER -> picker.drag(arg, mx, my);
			case A_GRID -> {
				if (ui.dragAction == A_GRID && !painting) {
					painting = true;
					long[] b = Crosshairs.currentBits();
					int gx = (int) ((mx - gridX) / Math.max(1, cell)), gy = (int) ((my - gridY) / Math.max(1, cell));
					paintValue = !eraser && !(Crosshairs.get(b, gx + Crosshairs.gridOffset(), gy + Crosshairs.gridOffset()) && !eraser && clickToggles);
				}
				paint(mx, my, paintValue);
			}
			case A_LSCROLL -> leftScroll.jump(Widgets.scrollFor(my, wy + HEAD_H + 7, wh - HEAD_H - 13, leftScroll.max, wh - HEAD_H));
			case A_RSCROLL -> rightScroll.jump(Widgets.scrollFor(my, wy + HEAD_H + 7, wh - HEAD_H - 13, rightScroll.max, wh - HEAD_H));
			default -> {
			}
		}
	}

	private boolean painting;
	/** Clicking a pixel that is already on turns it off (only with the pencil, only for the first pixel). */
	private static final boolean clickToggles = true;

	@Override
	public void onRelease(int action, int arg) {
		painting = false;
		Config.saveIfNeeded(true);
	}

	private void paint(double mx, double my, boolean on) {
		int g = Crosshairs.gridSize(), offset = Crosshairs.gridOffset();
		int gx = (int) Math.floor((mx - gridX) / Math.max(1, cell)), gy = (int) Math.floor((my - gridY) / Math.max(1, cell));
		if (gx < 0 || gy < 0 || gx >= g || gy >= g) {
			return;
		}
		long[] b = Crosshairs.currentBits().clone();
		int ox = gx + offset, oy = gy + offset;
		Crosshairs.set(b, ox, oy, on);
		if (mirrorH) Crosshairs.set(b, offset + g - 1 - gx, oy, on);
		if (mirrorV) Crosshairs.set(b, ox, offset + g - 1 - gy, on);
		if (mirrorH && mirrorV) Crosshairs.set(b, offset + g - 1 - gx, offset + g - 1 - gy, on);
		Crosshairs.setCurrent(b);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
		double dx = ui.toDesign(mouseX);
		if (dx < wx + 268) {
			leftScroll.wheel(v);
		} else if (dx > wx + ww - 320) {
			rightScroll.wheel(v);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (name.focused) {
			name.key(input.key(), input.modifiers(), input.isPaste());
			return true;
		}
		if (share.focused) {
			share.key(input.key(), input.modifiers(), input.isPaste());
			if (input.key() == 257) {
				if (Crosshairs.importShareCode(share.text)) { share.text = ""; shareOpen = false; }
				share.focused = false;
			}
			return true;
		}
		if (picker.key(input.key(), input.modifiers(), input.isPaste())) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (!input.isAllowedChatCharacter()) {
			return false;
		}
		if (name.focused) {
			name.type(input.codepointAsString());
			return true;
		}
		if (share.focused) {
			share.type(input.codepointAsString());
			return true;
		}
		return picker.type(input.codepointAsString());
	}
}
