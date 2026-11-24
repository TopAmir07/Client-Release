package dev.topclient.client.top;

import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.TextField;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;

import java.util.List;

import static dev.topclient.client.top.TopTheme.*;

/** WAYPOINTS tab: add a waypoint where you stand, rename, recolour, hide or delete them. */
final class WaypointsView {
	static final int A_ADD = 600, A_COLOR = 601, A_RENAME = 602, A_EYE = 603, A_DELETE = 604, A_FIELD = 605, A_SCROLL = 606, A_LAST = 606;

	private final Scroll scroll = new Scroll();
	final TextField name = new TextField("Waypoint name", 32);
	private Waypoints.Waypoint renaming;
	private List<Waypoints.Waypoint> shown = List.of();
	private int top, height;

	boolean isTyping() {
		return renaming != null;
	}

	void draw(Ui ui, int x, int y, int w, int h) {
		top = y;
		height = h;
		int cx = x + 28, cw = w - 70;
		String world = Waypoints.worldKey();
		Draw.text(ui.ctx, "WAYPOINTS", cx, y + 22, 11, argb(255, ACCENT), true);
		if (world == null) {
			Icons.drawCentered(ui.ctx, Icons.ui("waypoint"), x + w / 2F, y + h / 2F - 30, 40, argb(255, ACCENT));
			Draw.textCenter(ui.ctx, "Join a world to add waypoints.", x + w / 2F, y + h / 2F + 10, 14, argb(255, TEXT_MUTE), true);
			return;
		}
		Draw.textMid(ui.ctx, Draw.fit(world.startsWith("sp:") ? world.substring(3) + " (singleplayer)" : world, 12, cw - 260), cx + 100, y + 28, 12, argb(255, TEXT_MUTE));
		Widgets.button(ui, cx + cw - 230, y + 14, 230, 34, "ADD WAYPOINT HERE", true, ui.over(cx + cw - 230, y + 14, 230, 34) ? 1F : 0F, A_ADD, 0);
		int listTop = y + 62, listH = h - 62;
		shown = Waypoints.list();
		scroll.update(ui);
		ui.clip(x, listTop, w, listH);
		int ry = listTop + 4 - Math.round(scroll.pos);
		if (shown.isEmpty()) {
			Draw.textCenter(ui.ctx, "No waypoints here yet. Walk somewhere and press ADD WAYPOINT HERE.", x + w / 2F, listTop + 60, 13, argb(255, TEXT_MUTE));
		}
		String dim = Waypoints.dimension();
		for (int i = 0; i < shown.size(); i++) {
			Waypoints.Waypoint p = shown.get(i);
			int rh = 52;
			if (ry + rh > listTop && ry < listTop + listH) {
				boolean over = ui.over(cx, ry, cw, rh);
				Draw.round(ui.ctx, cx, ry, cw, rh, 9, argb(225, over ? CARD_HOVER : CARD));
				Draw.outline(ui.ctx, cx, ry, cw, rh, 9, 1, argb(255, over ? BORDER_HOVER : BORDER));
				// colour (click to change)
				Draw.round(ui.ctx, cx + 12, ry + 14, 24, 24, 7, argb(255, p.visible ? p.color : mix(p.color, 0x000000, 0.6F)));
				ui.hit(cx + 12, ry + 14, 24, 24, A_COLOR, i);
				if (p == renaming) {
					name.focused = true;
					name.draw(ui, cx + 48, ry + 10, Math.min(300, cw - 260), 32, null, A_FIELD, i);
				} else {
					Draw.textMid(ui.ctx, Draw.fit(p.name, 13.5F, cw - 300, true), cx + 48, ry + 18, 13.5F, argb(255, p.visible ? TEXT : TEXT_MUTE), true);
					String where = p.x + ", " + p.y + ", " + p.z + "   " + niceDim(p.dim) + (dim.equals(p.dim) ? "   " + Math.round(Waypoints.distance(p)) + " m away" : "");
					Draw.textMid(ui.ctx, where, cx + 48, ry + 36, 11.5F, argb(255, TEXT_MUTE));
				}
				int bx = cx + cw - 118;
				icon(ui, bx, ry + 12, "pencil", A_RENAME, i, TEXT_DIM);
				icon(ui, bx + 38, ry + 12, p.visible ? "eye" : "eye-off", A_EYE, i, p.visible ? ACCENT : TEXT_MUTE);
				icon(ui, bx + 76, ry + 12, "trash", A_DELETE, i, DISABLED);
			}
			ry += rh + 8;
		}
		ui.unclip();
		scroll.setMax(ry + Math.round(scroll.pos) - listTop - listH + 8);
		Widgets.scrollbar(ui, x + w - 12, listTop + 6, listH - 12, scroll.pos, scroll.max, listH, A_SCROLL, 0);
	}

	private static String niceDim(String d) {
		if (d == null) {
			return "";
		}
		return switch (d) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> d;
		};
	}

	private void icon(Ui ui, int x, int y, String icon, int action, int arg, int color) {
		boolean over = ui.over(x, y, 30, 28);
		Draw.round(ui.ctx, x, y, 30, 28, 7, argb(over ? 220 : 0, PANEL2));
		Icons.drawCentered(ui.ctx, Icons.ui(icon), x + 15, y + 14, 15, argb(255, over ? mix(color, 0xFFFFFF, 0.25F) : color));
		ui.hit(x, y, 30, 28, action, arg);
	}

	void click(int action, int arg) {
		Waypoints.Waypoint p = arg >= 0 && arg < shown.size() ? shown.get(arg) : null;
		if (action != A_FIELD) {
			finishRename();
		}
		switch (action) {
			case A_ADD -> {
				Waypoints.Waypoint w = Waypoints.addHere(null);
				if (w != null) {
					renaming = w;
					name.text = w.name;
				}
			}
			case A_COLOR -> {
				if (p != null) {
					int i = 0;
					for (int k = 0; k < Waypoints.COLORS.length; k++) {
						if (Waypoints.COLORS[k] == p.color) {
							i = k + 1;
						}
					}
					p.color = Waypoints.COLORS[i % Waypoints.COLORS.length];
					Waypoints.save();
				}
			}
			case A_RENAME -> {
				if (p != null) {
					renaming = p;
					name.text = p.name;
				}
			}
			case A_EYE -> {
				if (p != null) {
					p.visible = !p.visible;
					Waypoints.save();
				}
			}
			case A_DELETE -> {
				if (p != null) {
					Waypoints.remove(p);
				}
			}
			default -> {
				return;
			}
		}
		UiSounds.click();
	}

	void finishRename() {
		if (renaming != null) {
			String n = name.text.strip();
			if (!n.isEmpty()) {
				renaming.name = n;
				Waypoints.save();
			}
			renaming = null;
			name.focused = false;
		}
	}

	boolean key(int key, int mods, boolean paste) {
		if (renaming == null) {
			return false;
		}
		name.key(key, mods, paste);
		if (!name.focused) {
			finishRename();
		}
		return true;
	}

	boolean type(String s) {
		if (renaming == null) {
			return false;
		}
		name.type(s);
		return true;
	}

	void drag(double my) {
		scroll.jump(Widgets.scrollFor(my, top + 68, height - 74, scroll.max, height - 62));
	}

	void wheel(double v) {
		scroll.wheel(v);
	}
}
