package dev.topclient.client.clover.servers;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Textures;
import dev.topclient.client.clover.ui.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import dev.topclient.client.clover.mixin.ScreenChildrenAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FavouriteServers {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "textures/gui/logo.png");
	private static final int TILE = 18;
	private static final int GAP = 4;

	private record Icon(Identifier id, int hash) {
	}

	private static final Map<String, Icon> ICONS = new HashMap<>();
	private static int iconCounter;

	private FavouriteServers() {
	}

	public static boolean enabled() {
		return Config.getBool("fav.tab", true);
	}

	public static void setEnabled(boolean on) {
		Config.setBool("fav.tab", on);
	}

	public static List<String> list() {
		String raw = Config.get("fav.servers", "");
		List<String> out = new ArrayList<>();
		for (String s : raw.split("\\|")) {
			String a = s.trim();
			if (!a.isEmpty() && !out.contains(a)) {
				out.add(a);
			}
		}
		return out;
	}

	private static void store(List<String> list) {
		Config.set("fav.servers", String.join("|", list));
		Config.saveIfNeeded(true);
	}

	public static boolean isFavourite(String address) {
		return address != null && list().contains(key(address));
	}

	public static void toggle(String address) {
		List<String> l = list();
		String k = key(address);
		if (!l.remove(k)) {
			l.add(k);
		}
		store(l);
	}

	public static void move(String address, int dir) {
		List<String> l = list();
		int i = l.indexOf(key(address));
		int j = i + dir;
		if (i < 0 || j < 0 || j >= l.size()) {
			return;
		}
		l.set(i, l.get(j));
		l.set(j, key(address));
		store(l);
	}

	private static String key(String address) {
		return address.trim().toLowerCase(Locale.ROOT).replace("|", "");
	}

	static List<ServerData> favouritesIn(ServerList servers) {
		List<ServerData> out = new ArrayList<>();
		for (String fav : list()) {
			for (int i = 0; i < servers.size(); i++) {
				ServerData s = servers.get(i);
				if (s != null && s.ip != null && key(s.ip).equals(fav)) {
					out.add(s);
					break;
				}
			}
		}
		return out;
	}

	static Identifier icon(ServerData s) {
		byte[] png = s.getIconBytes();
		if (png == null || png.length == 0) {
			return null;
		}
		int hash = Arrays.hashCode(png);
		Icon have = ICONS.get(s.ip);
		if (have != null && have.hash() == hash) {
			return have.id();
		}
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
			NativeImage n = Textures.fromBuffered(img);
			if (n == null) {
				return null;
			}
			if (have != null) {
				Minecraft.getInstance().getTextureManager().release(have.id());
			}
			Identifier id = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "server/icon" + (iconCounter++));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, n));
			ICONS.put(s.ip, new Icon(id, hash));
			return id;
		} catch (Exception e) {
			return null;
		}
	}

	static int statusColor(ServerData s) {
		ServerData.State st = s.state();
		if (st == ServerData.State.SUCCESSFUL) {
			return 0xFF3DDC5A;
		}
		if (st == ServerData.State.UNREACHABLE || st == ServerData.State.INCOMPATIBLE) {
			return 0xFFE5484D;
		}
		return 0xFF8A8F98;
	}

	static void drawTile(GuiGraphicsExtractor ctx, ServerData s, int x, int y, int size, boolean hover) {
		Gfx.rect(ctx, x, y, size, size, 3, hover ? Theme.accentArgb() : Theme.border());
		Gfx.rect(ctx, x + 1, y + 1, size - 2, size - 2, 2, Theme.argb(Theme.panelRgb(), 255));
		Identifier icon = icon(s);
		if (icon != null) {
			Gfx.image(ctx, icon, x + 1, y + 1, size - 2, size - 2, 64, 64);
		} else {
			Gfx.image(ctx, LOGO, x + 2, y + 2, size - 4, size - 4, 256, 256);
		}
		int dot = statusColor(s);
		int d = size >= 22 ? 7 : 5;
		Gfx.rect(ctx, x + size - d, y - 2, d + 2, d + 2, d / 2 + 1, 0xFF0A0A0A);
		Gfx.rect(ctx, x + size - d + 1, y - 1, d, d, d / 2, dot);
	}

	public static void join(Screen parent, ServerData s) {
		Minecraft mc = Minecraft.getInstance();
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(s.ip), s, false, null);
	}

	private static Screen tabScreen;
	private static final List<ServerData> TILES = new ArrayList<>();
	private static final List<int[]> RECTS = new ArrayList<>();
	private static int[] addRect;

	public static boolean click(Screen screen, double mx, double my) {
		if (screen != tabScreen || !enabled()) {
			return false;
		}
		for (int i = 0; i < RECTS.size(); i++) {
			int[] r = RECTS.get(i);
			if (Gfx.inside(mx, my, r[0], r[1], TILE, TILE)) {
				join(screen, TILES.get(i));
				return true;
			}
		}
		if (addRect != null && Gfx.inside(mx, my, addRect[0], addRect[1], TILE, TILE)) {
			Minecraft.getInstance().gui.setScreen(new FavouriteServersScreen(screen));
			return true;
		}
		return false;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof JoinMultiplayerScreen mp) || !enabled()) {
				return;
			}
			ServerList servers = ((dev.topclient.client.clover.mixin.JoinMultiplayerScreenAccessor) mp).topclient$servers();
			if (servers == null) {
				return;
			}
			List<ServerData> favs = favouritesIn(servers);
			int top = 28;
			int maxH = Math.max(TILE + 10, height - top - 64);
			int fit = Math.max(0, (maxH - 8 - TILE) / (TILE + GAP));
			List<ServerData> shown = new ArrayList<>(favs.subList(0, Math.min(fit, favs.size())));
			int panelW = TILE + 8;
			int panelX = width - panelW;
			int panelH = 6 + (shown.size() + 1) * (TILE + GAP) + 2;
			int x = panelX + 4;

			tabScreen = screen;
			TILES.clear();
			RECTS.clear();
			for (int i = 0; i < shown.size(); i++) {
				TILES.add(shown.get(i));
				RECTS.add(new int[]{x, top + 6 + i * (TILE + GAP)});
			}
			int addY = top + 6 + shown.size() * (TILE + GAP);
			addRect = new int[]{x, addY};

			try {
				List<GuiEventListener> children = ((ScreenChildrenAccessor) screen).topclient$children();
				for (int i = 0; i < RECTS.size(); i++) {
					ServerData info = TILES.get(i);
					int[] r = RECTS.get(i);
					children.add(0, Button.builder(Component.literal(""), b -> join(screen, info)).bounds(r[0], r[1], TILE, TILE).build());
				}
				children.add(0, Button.builder(Component.literal(""), b -> client.gui.setScreen(new FavouriteServersScreen(screen)))
						.bounds(x, addY, TILE, TILE).build());
			} catch (RuntimeException e) {
			}

			ScreenEvents.afterExtract(screen).register((s, ctx, mouseX, mouseY, delta) -> {
				Gfx.rect(ctx, panelX, top, panelW + 6, panelH, 5, Theme.border());
				Gfx.rect(ctx, panelX + 1, top + 1, panelW + 6, panelH - 2, 4, Theme.argb(Theme.panelRgb(), 235));
				String hoverName = null;
				int hoverY = 0;
				for (int i = 0; i < shown.size(); i++) {
					int[] r = RECTS.size() > i ? RECTS.get(i) : new int[]{x, 0};
					boolean over = Gfx.inside(mouseX, mouseY, r[0], r[1], TILE, TILE);
					drawTile(ctx, shown.get(i), r[0], r[1], TILE, over);
					if (over) {
						hoverName = shown.get(i).name + "  -  click to join";
						hoverY = r[1];
					}
				}
				boolean addOver = Gfx.inside(mouseX, mouseY, x, addY, TILE, TILE);
				Gfx.rect(ctx, x, addY, TILE, TILE, 3, addOver ? Theme.accentArgb() : Theme.border());
				Gfx.rect(ctx, x + 1, addY + 1, TILE - 2, TILE - 2, 2, Theme.argb(Theme.panelRgb(), 255));
				int c = addOver ? Theme.accentArgb() : Theme.text();
				ctx.fill(x + TILE / 2 - 1, addY + 5, x + TILE / 2 + 1, addY + TILE - 5, c);
				ctx.fill(x + 5, addY + TILE / 2 - 1, x + TILE - 5, addY + TILE / 2 + 1, c);
				if (addOver) {
					hoverName = shown.isEmpty() ? "Add favourite servers" : "Edit favourites";
					hoverY = addY;
				}
				if (hoverName != null) {
					int w = Gfx.width(hoverName) + 10;
					Gfx.outline(ctx, panelX - w - 4, hoverY + 2, w, 15, 3, Theme.accentArgb(), Theme.argb(Theme.panelRgb(), 245));
					Gfx.text(ctx, hoverName, panelX - w + 1, hoverY + 6, Theme.text());
				}
			});
		});
	}
}
