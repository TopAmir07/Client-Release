package dev.topclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Icons {
	private static final Map<String, String> BY_NAME = new HashMap<>();
	private static final Map<String, BufferedImage> MASTERS = new HashMap<>();
	private static final Map<String, Identifier> CACHE = new LinkedHashMap<String, Identifier>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Identifier> e) {
			if (size() > 400) {
				try {
					Minecraft.getInstance().getTextureManager().release(e.getValue());
				} catch (RuntimeException ignored) {
				}
				return true;
			}
			return false;
		}
	};
	private static int counter;
	private static boolean broken;

	static {
		Map<String, String> m = BY_NAME;
		m.put("TopClient", "palette-swatch-variant");
		m.put("Friends", "account-heart");
		m.put("Aim Assist", "crosshairs-gps");
		m.put("Auto Clicker", "cursor-default-click");
		m.put("Auto Jump Reset", "arrow-up-bold-circle-outline");
		m.put("Auto WTap", "alpha-w-box-outline");
		m.put("No Miss Delay", "target");
		m.put("Shield Disabler", "shield-off-outline");
		m.put("Trigger Bot", "sword");
		m.put("Anchor Macro", "anchor");
		m.put("Auto Crystal", "diamond-outline");
		m.put("Auto Hit Crystal", "diamond-stone");
		m.put("Crystal Optimizer", "lightning-bolt");
		m.put("Double Anchor", "layers-triple-outline");
		m.put("Auto Double Hand", "hand-back-right-outline");
		m.put("Auto Inventory Totem", "bag-personal-outline");
		m.put("Auto Pot", "bottle-tonic-plus-outline");
		m.put("Auto Pot Refill", "bottle-tonic-outline");
		m.put("Auto XP", "star-four-points-outline");
		m.put("Hover Totem", "cursor-pointer");
		m.put("Totem Offhand", "shield-account-outline");
		m.put("No Jump Delay", "chevron-double-up");
		m.put("Sprint", "run-fast");
		m.put("Fake Lag", "timer-sand");
		m.put("Ping Spoof", "wifi-strength-2");
		m.put("Freecam", "camera-outline");
		m.put("HUD", "monitor-dashboard");
		m.put("No Bounce", "arrow-collapse-vertical");
		m.put("Player ESP", "account-eye-outline");
		m.put("Storage ESP", "treasure-chest");
		m.put("Target HUD", "account-box-outline");
		m.put("Key Pearl", "circle-double");
		m.put("No Break Delay", "pickaxe");
		m.put("Pack Spoof", "package-variant-closed");
		m.put("Prevent", "cancel");
		m.put("Infinite Chat History", "infinity");
		m.put("Chat Timestamps", "message-text-clock-outline");
		m.put("Horse Stats", "horse");
		m.put("Death Coordinates", "skull-outline");
		m.put("Nether Coordinates", "fire");
		m.put("Low Health Warning", "heart-pulse");
		m.put("Pickup Notifier", "package-down");
		m.put("Session Stats", "chart-box-outline");
		m.put("Movement Status", "walk");
		m.put("FPS Graph", "chart-line");
		m.put("Analog Clock", "clock-outline");
		m.put("Day Cycle Bar", "weather-sunset");
		m.put("Reach Display", "ruler");
		m.put("Light Level", "lightbulb-on-outline");
		m.put("Y Level", "arrow-expand-vertical");
		m.put("Chunk Coordinates", "grid");
		m.put("Rotation", "rotate-3d-variant");
		m.put("Held Item Info", "hand-front-right-outline");
		m.put("XP Info", "star-circle-outline");
		m.put("Health Numbers", "heart-outline");
		m.put("Date", "calendar-month-outline");
		m.put("Kills & K/D", "sword-cross");
		m.put("Server TPS", "server-network");
		m.put("Entity Count", "paw");
		m.put("Nearby Players", "account-group-outline");
		m.put("Dimension", "earth");
		m.put("Inventory Space", "view-grid-outline");
		m.put("Total Playtime", "timer-outline");
		m.put("Break Reminder", "coffee-outline");
		m.put("Auto GG", "party-popper");
		m.put("No Hurt Shake", "vibrate-off");
		m.put("Freelook", "eye-refresh-outline");
		m.put("FPS", "speedometer");
		m.put("CPS", "mouse");
		m.put("Coordinates", "map-marker-outline");
		m.put("Direction", "compass-outline");
		m.put("Speed", "speedometer-medium");
		m.put("Ping", "wifi");
		m.put("Clock", "clock-digital");
		m.put("Memory", "memory");
		m.put("Day Counter", "calendar-today-outline");
		m.put("Server", "dns-outline");
		m.put("Item Counter", "counter");
		m.put("Potion Status", "flask-outline");
		m.put("Armor Status", "shield-half-full");
		m.put("Keystrokes", "keyboard-outline");
		m.put("Session Timer", "timer");
		m.put("Saturation", "food-drumstick-outline");
		m.put("Player Count", "account-multiple-outline");
		m.put("Combo Counter", "flash-outline");
		m.put("Totem Counter", "heart-plus-outline");
		m.put("Arrow Counter", "arrow-projectile");
		m.put("Biome", "pine-tree");
		m.put("Stopwatch", "av-timer");
		m.put("Compass Bar", "compass-rose");
		m.put("World Time", "weather-night");
		m.put("Weather", "weather-pouring");
		m.put("Durability Alert", "hammer-wrench");
		m.put("Motion Blur", "blur");
		m.put("Flower Name Tag", "star-four-points-outline");
		m.put("Color Boost", "palette-outline");
		m.put("Item Physics", "package-down");
		m.put("Shulker Box Preview", "package-variant");
		m.put("Spotify Lyrics", "microphone-variant");
		m.put("Spotify HUD", "music-circle-outline");
		m.put("Toggle Sprint", "run");
		m.put("Zoom", "magnify-plus-outline");
		m.put("Time Changer", "theme-light-dark");
		m.put("Crosshair Colour", "crosshairs");
		m.put("Block Outline", "cube-outline");
		m.put("Nametags", "card-account-details-outline");
		m.put("Clover Options", "cog-outline");
		m.put("Sounds", "volume-high");
		m.put("Theme", "palette-outline");
		m.put("Background", "image-outline");
		m.put("Window", "dock-window");
		m.put("Cards", "view-grid-outline");
		m.put("Text", "format-text");
		m.put("Skin Walker", "walk");
		m.put("Totem Particles", "shimmer");
		m.put("Icons", "emoticon-outline");
		m.put("Falling Particles", "weather-snowy");
		m.put("Visuals", "palette-swatch-variant");
		m.put("Auto Safe Anchor", "shield-check-outline");
		m.put("page:Customize", "brush-variant");
		m.put("Menu Look", "palette-outline");
		m.put("page:Combat", "sword");
		m.put("page:Crystal", "diamond-stone");
		m.put("page:Defense", "shield-half-full");
		m.put("page:Movement", "run-fast");
		m.put("page:Utility", "toolbox-outline");
		m.put("page:Network", "lan");
		m.put("page:Render", "eye-outline");
		m.put("page:HUD", "monitor-dashboard");
		m.put("page:Visual", "creation-outline");
		m.put("page:Clover Utility", "puzzle-outline");
		m.put("page:Capes", "flag-variant-outline");
		m.put("page:Client", "cog-outline");
		m.put("ui:search", "magnify");
		m.put("ui:close", "close");
		m.put("ui:chevron", "chevron-right");
		m.put("ui:back", "chevron-left");
		m.put("ui:settings", "tune-variant");
		m.put("ui:move", "cursor-move");
		m.put("ui:key", "keyboard-outline");
		m.put("ui:check", "check-bold");
		m.put("ui:star", "star");
		m.put("ui:star-outline", "star-outline");
		m.put("ui:pencil", "pencil-outline");
		m.put("ui:sort", "sort-variant");
		m.put("ui:list", "view-list");
		m.put("ui:grid", "view-grid-outline");
		m.put("ui:share", "share-variant-outline");
		m.put("ui:plus", "plus");
		m.put("ui:save", "content-save-outline");
		m.put("ui:lock", "lock-outline");
		m.put("ui:emote", "emoticon-happy-outline");
		m.put("ui:profile", "account-circle-outline");
		m.put("ui:cosmetics", "tshirt-crew-outline");
		m.put("ui:waypoint", "map-marker-radius-outline");
		m.put("ui:hud", "cursor-move");
		m.put("ui:info", "information-outline");
		m.put("ui:trash", "trash-can-outline");
		m.put("ui:eye", "eye-outline");
		m.put("ui:eye-off", "eye-off-outline");
		m.put("ui:reset", "restore");
		m.put("ui:image", "image-outline");
		m.put("ui:play", "play");
		m.put("ui:clover", "clover");
		m.put("ui:none", "minus");
		m.put("ui:camera", "camera-outline");
		m.put("ui:crosshair", "crosshairs");
		m.put("ui:puzzle", "puzzle-outline");
		m.put("ui:options", "cog-outline");
		m.put("ui:language", "earth");
		m.put("ui:accounts", "account-box-outline");
		m.put("ui:server", "server-network");
		m.put("ui:palette", "palette-outline");
		m.put("ui:mods", "view-grid-outline");
	}

	private Icons() {
	}

	public static String forModule(String name) {
		return BY_NAME.getOrDefault(name, "puzzle-outline");
	}

	public static String forPage(String title, boolean clover) {
		if (clover && title.equals("Utility")) {
			return BY_NAME.get("page:Clover Utility");
		}
		return BY_NAME.getOrDefault("page:" + title, "puzzle-outline");
	}

	public static String ui(String what) {
		return BY_NAME.getOrDefault("ui:" + what, "minus");
	}

	private static BufferedImage master(String icon) throws Exception {
		BufferedImage have = MASTERS.get(icon);
		if (have != null) {
			return have;
		}
		try (InputStream in = Icons.class.getResourceAsStream("/assets/topclient/icons/" + icon + ".png")) {
			if (in == null) {
				throw new IllegalStateException("missing icon " + icon);
			}
			BufferedImage img = ImageIO.read(in);
			MASTERS.put(icon, img);
			return img;
		}
	}

	private static BufferedImage scale(BufferedImage src, int px) {
		BufferedImage cur = src;
		int w = src.getWidth();
		while (w / 2 >= px * 2) {
			w /= 2;
			cur = step(cur, w);
		}
		return step(cur, px);
	}

	private static BufferedImage step(BufferedImage src, int px) {
		BufferedImage out = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(src, 0, 0, px, px, null);
		g.dispose();
		return out;
	}

	private static Identifier texture(String icon, int px) {
		String key = icon + "@" + px;
		Identifier have = CACHE.get(key);
		if (have != null) {
			return have;
		}
		try {
			BufferedImage img = scale(master(icon), px);
			NativeImage n = new NativeImage(px, px, false);
			for (int y = 0; y < px; y++) {
				for (int x = 0; x < px; x++) {
					int a = (img.getRGB(x, y) >>> 24) & 0xFF;
					n.setPixel(x, y, (a << 24) | 0xFFFFFF);
				}
			}
			Identifier id = Identifier.fromNamespaceAndPath("topclient", "ui_icon/" + (counter++));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, n));
			CACHE.put(key, id);
			return id;
		} catch (Throwable t) {
			return null;
		}
	}

	public static void draw(GuiGraphicsExtractor ctx, String icon, float x, float y, float size, int argb) {
		if (broken || icon == null) {
			return;
		}
		float ui = Draw.ui;
		int px = Math.max(8, Math.min(256, Math.round(size * ui)));
		Identifier id = texture(icon, px);
		if (id == null) {
			return;
		}
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(Math.round(x * ui) / ui, Math.round(y * ui) / ui);
		m.scale(1F / ui, 1F / ui);
		ctx.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0F, 0F, px, px, px, px, px, px, Draw.fade(argb));
		m.popMatrix();
	}

	public static void drawModule(GuiGraphicsExtractor ctx, String name, float cx, float cy, float size, int argb) {
		drawThemed(ctx, name, forModule(name), cx, cy, size, argb);
	}

	public static void drawPage(GuiGraphicsExtractor ctx, String title, boolean clover, float cx, float cy, float size, int argb) {
		String key = clover && title.equals("Utility") ? "page:Clover Utility" : "page:" + title;
		drawThemed(ctx, key, forPage(title, clover), cx, cy, size, argb);
	}

	private static void drawThemed(GuiGraphicsExtractor ctx, String key, String outline, float cx, float cy, float size, int argb) {
		dev.topclient.client.module.modules.client.ClickGUI.IconStyle style =
				dev.topclient.client.module.modules.client.ClickGUI.iconStyle.getMode();
		try {
			if (style == dev.topclient.client.module.modules.client.ClickGUI.IconStyle.Minecraft
					&& ItemIcons.draw(ctx, ItemIcons.forName(key), cx, cy, size * 1.15F)) {
				return;
			}
			if (style == dev.topclient.client.module.modules.client.ClickGUI.IconStyle.Emoji
					&& Pictures.draw(ctx, Emoji.forName(key), cx, cy, size * 1.15F, 0F, (argb >>> 24) & 0xFF)) {
				return;
			}
		} catch (RuntimeException ignored) {
		}
		drawCentered(ctx, outline, cx, cy, size, argb);
	}

	public static boolean fullColour() {
		return dev.topclient.client.module.modules.client.ClickGUI.iconStyle.getMode()
				!= dev.topclient.client.module.modules.client.ClickGUI.IconStyle.Outline;
	}

	public static void drawCentered(GuiGraphicsExtractor ctx, String icon, float cx, float cy, float size, int argb) {
		draw(ctx, icon, cx - size / 2F, cy - size / 2F, size, argb);
	}
}
