package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;

/** The rest of the TopClient list: boss bar, hit counter, hand position, chat snippets, timer, performance. */
public final class TopMore {
	private TopMore() {
	}

	/** Boss Bar: hide it or make it smaller. */
	public static final class BossBar extends Module {
		public static BossBar instance;
		final Setting hide = bool("hide", "Hide the boss bar", false);
		final Setting scale = number("scale", "Size", 0.5F, 1F, 0.05F, 1F);

		public BossBar() {
			super("bossbar", "Boss Bar", "Hides the boss bar or makes it smaller.", Category.HUD);
			instance = this;
		}

		public static boolean hidden() {
			return instance != null && instance.enabled() && instance.hide.getBool();
		}

		public static float scale() {
			return instance != null && instance.enabled() ? instance.scale.getFloat() : 1F;
		}
	}

	/** Hit Counter: how many hits you landed in a row and this session (display only). */
	public static final class HitCounter extends TextHud {
		static HitCounter instance;
		private final Setting session = bool("session", "Show total this session", true);
		private int total, streak;
		private long last;

		public HitCounter() {
			super("hits", "Hit Counter", "Counts the hits you land: in a row (resets after 3 seconds) and this session.", 1, 160);
			instance = this;
		}

		public static void onAttack() {
			HitCounter h = instance;
			if (h == null || !h.enabled()) {
				return;
			}
			long now = System.currentTimeMillis();
			h.streak = now - h.last > 3000 ? 1 : h.streak + 1;
			h.total++;
			h.last = now;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			int s = System.currentTimeMillis() - last > 3000 ? 0 : streak;
			return session.getBool() ? new String[]{"Hits: " + s + "  (" + total + ")"} : new String[]{"Hits: " + s};
		}
	}

	/** Hand Position: moves and resizes the items in your hands (first person). Looks only. */
	public static final class HandPosition extends Module {
		public static HandPosition instance;
		final Setting x = number("x", "Left / right", -0.5F, 0.5F, 0.05F, 0F);
		final Setting y = number("y", "Up / down", -0.5F, 0.5F, 0.05F, 0F);
		final Setting z = number("z", "Closer / further", -0.5F, 0.5F, 0.05F, 0F);
		final Setting size = number("size", "Size", 0.5F, 1.5F, 0.05F, 1F);

		public HandPosition() {
			super("handpos", "Hand Position", "Moves and resizes the items in your hands in first person. Only changes how it looks.", Category.VISUAL);
			instance = this;
		}

		public static boolean on() {
			return instance != null && instance.enabled();
		}

		public static float[] values() {
			return new float[]{instance.x.getFloat(), instance.y.getFloat(), instance.z.getFloat(), instance.size.getFloat()};
		}
	}

	/** Chat Snippets: a key opens chat with your text already typed. You still press Enter yourself. */
	public static final class Snippets extends Module {
		private final Setting[] text = new Setting[3];
		private final Setting[] key = new Setting[3];
		private final boolean[] was = new boolean[3];

		public Snippets() {
			super("snippets", "Chat Snippets", "Press a key to open chat with a saved message typed in. Nothing is sent until you press Enter.", Category.UTILITY);
			for (int i = 0; i < 3; i++) {
				header("Snippet " + (i + 1));
				text[i] = text("text" + i, "Text", i == 0 ? "gg" : "");
				key[i] = Setting.key(this, "key" + i, "Key", 0);
				settings.add(key[i]);
			}
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.getWindow() == null) {
				return;
			}
			for (int i = 0; i < 3; i++) {
				int k = key[i].getKey();
				boolean down = k > 0 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), k);
				if (down && !was[i] && mc.gui.screen() == null && mc.player != null && !text[i].getText().isBlank()) {
					mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen(text[i].getText(), false));
				}
				was[i] = down;
			}
		}
	}

	/** Timer: counts down from the time you set and shows it on screen. Start/stop with its key. */
	public static final class Timer extends TextHud {
		private final Setting minutes = number("minutes", "Minutes", 1, 60, 1, 5);
		private final Setting startKey = Setting.key(this, "start", "Start / stop key", 0);
		private long endsAt;
		private boolean was;

		public Timer() {
			super("timer", "Timer", "A countdown on your screen. Set the minutes and a key to start or stop it.", 1, 175);
			settings.add(startKey);
		}

		@Override
		public void onTick(Minecraft mc) {
			int k = startKey.getKey();
			boolean down = k > 0 && mc.getWindow() != null && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), k);
			if (down && !was && mc.gui.screen() == null) {
				endsAt = endsAt > System.currentTimeMillis() ? 0 : System.currentTimeMillis() + minutes.getInt() * 60_000L;
			}
			was = down;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			long left = Math.max(0, endsAt - System.currentTimeMillis()) / 1000;
			if (endsAt == 0) {
				return new String[]{"Timer: " + minutes.getInt() + ":00"};
			}
			return new String[]{left == 0 ? "Timer: done!" : String.format("Timer: %d:%02d", left / 60, left % 60)};
		}
	}

	/** Performance: hides far-away entities and offers render-distance presets. */
	public static final class Performance extends Module {
		public static Performance instance;
		final Setting entityDist = number("entityDist", "Hide entities further than (blocks, 0 = off)", 0, 128, 8, 64);
		final Setting keepPlayers = bool("keepPlayers", "Always show players", true);
		final Setting preset = mode("preset", "Render distance preset", 0, "Leave as is", "Low (6)", "Medium (10)", "High (16)");
		private int applied = -1;

		public Performance() {
			super("performance", "Performance", "More FPS: hides entities far away (players stay visible) and quick render-distance presets.", Category.UTILITY);
			instance = this;
		}

		@Override
		public void onTick(Minecraft mc) {
			int p = preset.getMode();
			if (p != applied && mc.options != null) {
				applied = p;
				int rd = switch (p) {
					case 1 -> 6;
					case 2 -> 10;
					case 3 -> 16;
					default -> -1;
				};
				if (rd > 0) {
					mc.options.renderDistance().set(rd);
				}
			}
		}

		@Override
		public void onDisable() {
			applied = -1;
		}

		/** True when an entity at this distance should be skipped. */
		public static boolean cull(net.minecraft.world.entity.Entity e, double camX, double camY, double camZ) {
			Performance p = instance;
			if (p == null || !p.enabled()) {
				return false;
			}
			float d = p.entityDist.getFloat();
			if (d <= 0 || (p.keepPlayers.getBool() && e instanceof net.minecraft.world.entity.player.Player)) {
				return false;
			}
			return e.distanceToSqr(camX, camY, camZ) > d * d;
		}
	}
}
