package dev.topclient.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.Optional;

public final class SkinHead {
	private static volatile PlayerSkin loaded;
	private static volatile boolean failed;
	private static String forName;
	private static boolean asked;
	private static long askedAt;

	private SkinHead() {
	}

	private static GameProfile profile(Minecraft mc) {
		try {
			return mc.getGameProfile();
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static String name() {
		Minecraft mc = Minecraft.getInstance();
		GameProfile p = profile(mc);
		if (p != null && p.name() != null && !p.name().isEmpty()) {
			return p.name();
		}
		try {
			return mc.getUser().getName();
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static PlayerSkin get() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			try {
				PlayerSkin own = mc.player.getSkin();
				if (own != null && own.body() != null && own.body().texturePath() != null) {
					return own;
				}
			} catch (RuntimeException ignored) {
			}
		}
		GameProfile p = profile(mc);
		String name = p != null && p.name() != null ? p.name() : mc.getUser().getName();
		if (!name.equals(forName)) {
			forName = name;
			loaded = null;
			failed = false;
			asked = false;
		}
		PlayerSkin have = loaded;
		if (have != null) {
			return have;
		}
		if (p == null) {
			return null;
		}
		if (asked && failed && System.currentTimeMillis() - askedAt > 15000) {
			asked = false;
			failed = false;
		}
		if (!asked) {
			asked = true;
			askedAt = System.currentTimeMillis();
			final GameProfile known = p;
			// The session's profile carries no skin info outside a world, so ask Mojang's
			// session server for the full profile first (same place the game gets skins).
			Thread th = new Thread(() -> {
				GameProfile full = known;
				try {
					if (known.properties() == null || known.properties().isEmpty()) {
						var r = mc.services().sessionService().fetchProfile(known.id(), false);
						if (r != null && r.profile() != null) {
							full = r.profile();
						}
					}
				} catch (Throwable ignored) {
				}
				final GameProfile use = full;
				mc.execute(() -> {
					try {
						mc.getSkinManager().get(use).whenComplete((v, err) -> {
							Object o = v;
							if (o instanceof Optional<?> opt) {
								o = opt.orElse(null);
							}
							if (o instanceof PlayerSkin st) {
								loaded = st;
							} else {
								failed = true;
							}
						});
					} catch (RuntimeException e) {
						failed = true;
					}
				});
			}, "TopClient skin");
			th.setDaemon(true);
			th.start();
		}
		try {
			return DefaultPlayerSkin.get(p);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static boolean draw(GuiGraphicsExtractor ctx, float x, float y, float size) {
		Identifier tex;
		try {
			PlayerSkin t = get();
			tex = t == null || t.body() == null ? null : t.body().texturePath();
		} catch (RuntimeException e) {
			tex = null;
		}
		if (tex == null) {
			return false;
		}
		float ui = Draw.ui;
		int px = Math.max(8, Math.round(size * ui / 8F) * 8);
		float off = (size * ui - px) / 2F;
		int color = Draw.fade(0xFFFFFFFF);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(Math.round(x * ui + off) / ui, Math.round(y * ui + off) / ui);
		m.scale(1F / ui, 1F / ui);
		ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 8F, 8F, px, px, 8, 8, 64, 64, color);
		ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 40F, 8F, px, px, 8, 8, 64, 64, color);
		m.popMatrix();
		return true;
	}
}
