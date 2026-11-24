package dev.topclient.client.clover.servers;

import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FavouriteServersScreen extends Screen {
	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private final Screen parent;
	private final ServerList servers;
	private final List<Hit> hits = new ArrayList<>();
	private int scroll, contentH;
	private int mx, my;

	public FavouriteServersScreen(Screen parent) {
		super(Component.literal("Favourite servers"));
		this.parent = parent;
		this.servers = new ServerList(Minecraft.getInstance());
		this.servers.load();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean on, Runnable action) {
		boolean over = Gfx.inside(mx, my, x, y, w, h);
		Gfx.outline(ctx, x, y, w, h, 3, on || over ? Theme.accentArgb() : Theme.border(), on ? Theme.accentDark() : over ? Theme.cardHover() : Theme.card());
		Gfx.centered(ctx, label, x + w / 2, y + (h - 8) / 2, Theme.text());
		hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		hits.clear();
		mx = mouseX;
		my = mouseY;
		ctx.fillGradient(0, 0, width, height, Theme.bgTop(), Theme.bgBottom());
		int pw = Math.min(width - 16, 360), ph = Math.min(height - 16, 300);
		int px = (width - pw) / 2, py = (height - ph) / 2;
		Gfx.rect(ctx, px, py, pw, ph, 6, Theme.border());
		Gfx.rect(ctx, px + 1, py + 1, pw - 2, ph - 2, 5, Theme.panel());
		Gfx.scaled(ctx, "FAVOURITE SERVERS", px + 12, py + 10, 1.1F, Theme.text(), true);
		Gfx.text(ctx, "Click a server to join it. Add it to pin it to the tab on the right of the server list.", px + 12, py + 24, Theme.textDim());
		button(ctx, px + pw - 58, py + 8, 48, 16, "Done", false, this::onClose);

		int top = py + 38, bottom = py + ph - 8;
		ctx.enableScissor(px + 4, top, px + pw - 4, bottom);
		int y = top - scroll;
		int x = px + 10, w = pw - 20;
		if (servers.size() == 0) {
			Gfx.centered(ctx, "No servers yet - add some in the server list first.", px + pw / 2, top + 20, Theme.textDim());
		}
		List<String> favs = FavouriteServers.list();
		int firstRowHit = hits.size();
		for (int i = 0; i < servers.size(); i++) {
			ServerData s = servers.get(i);
			if (s == null || s.ip == null) {
				continue;
			}
			boolean fav = FavouriteServers.isFavourite(s.ip);
			boolean visible = y + 30 > top && y < bottom;
			if (visible) {
				Gfx.outline(ctx, x, y, w, 30, 4, fav ? Theme.accentArgb() : Theme.border(), Theme.card());
				FavouriteServers.drawTile(ctx, s, x + 4, y + 4, 22, false);
				Gfx.text(ctx, Gfx.fit(s.name, w - 150), x + 32, y + 6, Theme.text());
				Gfx.text(ctx, Gfx.fit(s.ip, w - 150), x + 32, y + 17, Theme.textDim());
				String addr = s.ip;
				if (fav) {
					int pos = favs.indexOf(addr.trim().toLowerCase(java.util.Locale.ROOT));
					if (pos > 0) {
						button(ctx, x + w - 112, y + 7, 16, 16, "^", false, () -> FavouriteServers.move(addr, -1));
					}
					if (pos >= 0 && pos < favs.size() - 1) {
						button(ctx, x + w - 94, y + 7, 16, 16, "v", false, () -> FavouriteServers.move(addr, 1));
					}
				}
				button(ctx, x + w - 74, y + 7, 68, 16, fav ? "* Favourite" : "Add", fav, () -> FavouriteServers.toggle(addr));
				ServerData join = s;
				hits.add(firstRowHit, new Hit(x, y, w - 120, 30, () -> FavouriteServers.join(this, join)));
			}
			y += 34;
		}
		ctx.disableScissor();
		contentH = y + scroll - top;
		scroll = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top)), scroll));
		for (int i = hits.size() - 1; i >= firstRowHit; i--) {
			Hit h = hits.get(i);
			if (h.y() + h.h() <= top || h.y() >= bottom) {
				hits.remove(i);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (Gfx.inside(click.x(), click.y(), h.x(), h.y(), h.w(), h.h())) {
					h.action().run();
					return true;
				}
			}
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, scroll - (int) Math.round(verticalAmount * 16));
		return true;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
