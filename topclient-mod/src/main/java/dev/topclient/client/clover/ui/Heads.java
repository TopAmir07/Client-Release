package dev.topclient.client.clover.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Draws a player's face (with hat layer). The skin is looked up once per account from
 * Mojang's own session server (the same place the game gets skins from) and cached.
 */
public final class Heads {
	private static final Map<UUID, Supplier<PlayerSkin>> SKINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> ASKED = new ConcurrentHashMap<>();

	private Heads() {
	}

	public static void draw(GuiGraphicsExtractor ctx, UUID id, int x, int y, int size) {
		PlayerSkin tex;
		try {
			tex = id == null ? DefaultPlayerSkin.getDefaultSkin() : skin(id);
			PlayerFaceExtractor.extractRenderState(ctx, tex, x, y, size);
		} catch (Throwable t) {
			ctx.fill(x, y, x + size, y + size, 0xFF6A3359);
		}
	}

	private static PlayerSkin skin(UUID id) {
		Supplier<PlayerSkin> s = SKINS.get(id);
		if (s != null) {
			return s.get();
		}
		if (ASKED.putIfAbsent(id, Boolean.TRUE) == null) {
			Minecraft mc = Minecraft.getInstance();
			Thread t = new Thread(() -> {
				try {
					var result = mc.services().sessionService().fetchProfile(id, false);
					if (result != null && result.profile() != null) {
						mc.execute(() -> SKINS.put(id, mc.getSkinManager().createLookup(result.profile(), false)));
					}
				} catch (Throwable ignored) {
					// no internet or Mojang busy: keep the default skin
				}
			}, "TopClient skin lookup");
			t.setDaemon(true);
			t.start();
		}
		return DefaultPlayerSkin.get(id);
	}
}
