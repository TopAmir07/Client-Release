package dev.topclient.client.clover.module;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.spotify.SpotifyHud;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import dev.topclient.client.clover.spotify.media.MediaCommand;
import dev.topclient.client.clover.spotify.media.MediaService;
import dev.topclient.client.clover.spotify.screen.ConfigScreen;
import dev.topclient.client.clover.spotify.screen.PositionScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SpotifyModule extends Module {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "spotify"));

	private static KeyMapping next;
	private static KeyMapping previous;
	private static KeyMapping toggle;
	private static KeyMapping hud;
	private static KeyMapping seekBack;
	private static KeyMapping seekForward;

	public static SpotifyModule instance;

	public SpotifyModule() {
		super("spotify", "Spotify HUD", "Shows the song playing in Spotify with album art, time and skip buttons. "
				+ "Keys: ] next, [ previous, \\ play/pause, , back 10s, . forward 10s, H hide (change them in Options > Controls). "
				+ "With chat open you can drag the time bar to jump in the song.", Category.HUD);
		isNew = true;
		instance = this;
		action("Spotify HUD settings (themes, colours, size...)", ConfigScreen::new);
		action("Move the Spotify HUD", PositionScreen::new);
	}

	public static void setup() {
		SpotifyUIConfig.load();
		next = key("next", GLFW.GLFW_KEY_RIGHT_BRACKET);
		previous = key("previous", GLFW.GLFW_KEY_LEFT_BRACKET);
		toggle = key("toggle", GLFW.GLFW_KEY_BACKSLASH);
		hud = key("hud", GLFW.GLFW_KEY_H);
		seekBack = key("seek_back", GLFW.GLFW_KEY_COMMA);
		seekForward = key("seek_forward", GLFW.GLFW_KEY_PERIOD);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "spotify"), (ctx, tickCounter) -> {
			if (instance != null && instance.enabled()) {
				SpotifyHud.render(ctx);
			}
		});
		if (instance != null && instance.enabled()) {
			MediaService.start();
		}
	}

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.topclient.spotify." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}

	@Override
	public void onEnable() {
		SpotifyUIConfig.get().enabled = true;
		SpotifyUIConfig.save();
		MediaService.start();
	}

	@Override
	public void onDisable() {
		if (SpotifyLyrics.instance == null || !SpotifyLyrics.instance.enabled()) {
			MediaService.stop();
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		if (next == null) {
			return;
		}
		if (!MediaService.running()) {
			MediaService.start();
		}
		while (next.consumeClick()) {
			MediaService.send(MediaCommand.NEXT);
		}
		while (previous.consumeClick()) {
			MediaService.send(MediaCommand.PREVIOUS);
		}
		while (toggle.consumeClick()) {
			MediaService.send(MediaCommand.TOGGLE);
		}
		while (seekBack.consumeClick()) {
			MediaService.seekBy(-10_000L);
		}
		while (seekForward.consumeClick()) {
			MediaService.seekBy(10_000L);
		}
		while (hud.consumeClick()) {
			SpotifyHud.hiddenByKey = !SpotifyHud.hiddenByKey;
		}
	}
}
