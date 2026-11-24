package dev.topclient.client.clover.module;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static List<Module> all() {
		return MODULES;
	}

	public static List<HudModule> huds() {
		List<HudModule> out = new ArrayList<>();
		for (Module m : MODULES) {
			if (m instanceof HudModule h) {
				out.add(h);
			}
		}
		return out;
	}

	public static Module byId(String id) {
		for (Module m : MODULES) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return null;
	}

	private static void add(Module m) {
		MODULES.add(m);
	}

	public static void register() {
		add(new HudModules.Fps());
		add(new HudModules.Cps());
		add(new HudModules.Coordinates());
		add(new HudModules.Compass());
		add(new HudModules.Speed());
		add(new HudModules.Ping());
		add(new HudModules.Keystrokes());
		add(new HudModules.ArmorStatus());
		add(new HudModules.PotionStatus());
		add(new HudModules.ItemCounter());
		add(new HudModules.Clock());
		add(new HudModules.Memory());
		add(new HudModules.DayCounter());
		add(new HudModules.ServerAddress());
		add(new HudModules.SessionTimer());
		add(new HudModules.Saturation());
		add(new HudModules.PlayerCount());
		add(new SpotifyModule());
		SpotifyModule.setup();
		SpotifyLyrics.setup();
		add(new MoreModules.ComboCounter());
		MoreModules.ComboCounter.hook();
		add(new MoreModules.TotemCounter());
		add(new MoreModules.ArrowCounter());
		add(new MoreModules.BiomeDisplay());
		add(new MoreModules.Stopwatch());
		add(new MoreModules.CompassBar());
		add(new MoreModules.WorldTime());
		add(new MoreModules.Weather());
		add(new MoreModules.DurabilityAlert());
		add(new ChatModules.InfiniteChat());
		add(new MotionBlur());
		add(new ColorBoost());
		add(new ItemPhysics());
		add(new CapePhysics());
		add(new CloverModules13.HorseStats());
		add(new Freelook());
		add(new ShulkerPreview());
		add(new SlotHighlights());
		add(new ChatModules.ChatTimestamps());
		add(new ChatModules.ChatFilter());
		add(new ChatModules.CompactChat());
		add(new TopVisuals.Fog());
		add(new TopVisuals.LowOverlays());
		add(new TopVisuals.WeatherChanger());
		add(new TopExtras.ParticleChanger());
		add(new TopExtras.Fullbright());
		add(new TopExtras.OldAnimations());
		add(new TopWorld.WaypointsMod());
		add(new TopWorld.Minimap());
		add(new TopMore.BossBar());
		add(new TopMore.HitCounter());
		add(new TopMore.HandPosition());
		add(new TopMore.Snippets());
		add(new TopMore.Timer());
		add(new TopMore.Performance());
		add(new CloverModules13.DeathCoords());
		add(new CloverModules13.NetherCoords());
		add(new CloverModules13.LowHealth());
		add(new CloverModules13.PickupNotifier());
		add(new CloverModules13.SessionStats());
		CloverModules13.SessionStats.hook();
		add(new CloverModules13.MovementStatus());
		add(new CloverModules13.FpsGraph());
		add(new CloverModules13.AnalogClock());
		add(new CloverModules13.DayCycle());
		add(new UtilityModules.ToggleSprint());
		add(new TopModules.ToggleSneak());
		add(new TopModules.ToggleDisplay());
		add(new UtilityModules.Zoom());
		add(new UtilityModules.TimeChanger());
		add(new UtilityModules.CrosshairColor());
		add(new UtilityModules.BlockOutlineMod());
		add(new SpotifyLyrics());
		add(new CloverModules16.ReachDisplay());
		add(new CloverModules16.LightLevel());
		add(new CloverModules16.YLevel());
		add(new CloverModules16.ChunkCoords());
		add(new CloverModules16.Facing());
		add(new CloverModules16.HeldItem());
		add(new CloverModules16.XpInfo());
		add(new CloverModules16.HealthDisplay());
		add(new CloverModules16.DateDisplay());
		add(new CloverModules16.KillCounter());
		KillTracker.register();
		add(new CloverModules16.ServerTps());
		add(new CloverModules16.EntityCount());
		add(new CloverModules16.NearbyPlayers());
		add(new CloverModules16.DimensionDisplay());
		add(new CloverModules16.InventorySpace());
		add(new CloverModules16.TotalPlaytime());
		add(new CloverModules16.BreakReminder());
		add(new CloverModules16.AutoGG());
		add(new CloverModules16.NoHurtCam());
		add(new Nametags());
		add(new TotemParticles());
		add(new ScoreboardHud());

		HudModule.clearBackgroundsOnce(MODULES);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			pollToggleKeys(client);
			for (Module m : MODULES) {
				if (m.enabled()) {
					try {
						m.onTick(client);
					} catch (RuntimeException e) {
						CloverClient.LOGGER.warn("Module {} failed on tick: {}", m.id, e.toString());
					}
				}
			}
			Config.saveIfNeeded(false);
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "hud"), (ctx, tickCounter) -> renderHud(ctx));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			for (Module m : MODULES) {
				if (m instanceof UtilityModules.Zoom z) {
					z.restore(client);
				}
			}
			Config.saveIfNeeded(true);
		});
	}

	private static final java.util.Map<String, Boolean> KEY_WAS = new java.util.HashMap<>();
	private static String toast = "";
	private static long toastAt;

	private static void pollToggleKeys(Minecraft mc) {
		if (mc.getWindow() == null) {
			return;
		}
		for (Module m : MODULES) {
			int key = m.toggleKey.getKey();
			if (key <= 0) {
				continue;
			}
			boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), key);
			boolean was = KEY_WAS.getOrDefault(m.id, false);
			if (down && !was && mc.gui.screen() == null && mc.player != null) {
				m.toggle();
				notice(m.name + (m.enabled() ? ": Enabled" : ": Disabled"));
			}
			KEY_WAS.put(m.id, down);
		}
	}

	public static void notice(String text) {
		if (Config.getBool("ui.notices", true)) {
			toast = text;
			toastAt = System.currentTimeMillis();
		}
	}

	private static void drawNotice(GuiGraphicsExtractor ctx, Minecraft mc) {
		long age = System.currentTimeMillis() - toastAt;
		if (toast.isEmpty() || age > 1800) {
			return;
		}
		float a = age < 150 ? age / 150F : age > 1500 ? (1800 - age) / 300F : 1F;
		int alpha = Math.max(8, Math.round(a * 230));
		int w = dev.topclient.client.clover.ui.Gfx.font().width(toast) + 16;
		int x = (ctx.guiWidth() - w) / 2;
		dev.topclient.client.clover.ui.Gfx.rect(ctx, x, 6, w, 16, 4, (alpha << 24) | dev.topclient.client.clover.ui.Theme.panelRgb());
		ctx.fill(x + 3, 20, x + w - 3, 21, (alpha << 24) | dev.topclient.client.clover.ui.Theme.accent());
		ctx.text(dev.topclient.client.clover.ui.Gfx.font(), toast, x + 8, 10, (Math.max(8, alpha) << 24) | 0xE9F7EE, false);
	}

	public static void setAll(boolean on) {
		Module.bulk = true;
		try {
			for (Module m : MODULES) {
				m.setEnabled(on);
			}
		} finally {
			Module.bulk = false;
		}
		dev.topclient.client.clover.ui.UiSounds.toggle(on);
		Config.saveIfNeeded(true);
	}

	private static void renderHud(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		ClickTracker.poll(mc);
		if (mc.gui.hud.isHidden()) {
			return;
		}
		drawNotice(ctx, mc);
		try {
			TopWorld.drawWaypoints(ctx, mc);
		} catch (RuntimeException e) {
		}
		for (Module m : MODULES) {
			if (m instanceof HudModule h && h.enabled()) {
				try {
					h.render(ctx, mc, false);
				} catch (RuntimeException e) {
				}
			}
		}
	}
}
