package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class UtilityModules {
	private UtilityModules() {
	}

	public static final class ToggleSprint extends Module {
		public ToggleSprint() {
			super("togglesprint", "Toggle Sprint", "Keeps you sprinting while you walk forward, no need to hold the sprint key.", Category.UTILITY);
		}

		// Sets sprinting directly instead of pressing the sprint key every tick. Pressing the key each
		// tick flipped vanilla's "Toggle" sprint mode on and off and spammed the sprint message.
		@Override
		public void onTick(Minecraft mc) {
			net.minecraft.client.player.LocalPlayer p = mc.player;
			if (p == null || p.isSprinting() || mc.gui.screen() != null) {
				return;
			}
			boolean food = p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly;
			if (mc.options.keyUp.isDown() && food && !p.isShiftKeyDown() && !p.isUsingItem() && !p.horizontalCollision
					&& !p.isFallFlying() && !p.isPassenger() && !p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)) {
				p.setSprinting(true);
			}
		}
	}

	/**
	 * Zoom: hold the key to zoom like a spyglass, scroll while holding to zoom further in or out.
	 * Changes only the camera's field of view (no FOV-setting tricks), so it works past the FOV slider's limit.
	 */
	public static final class Zoom extends Module {
		private static final int[] KEYS = {GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_G};
		private final Setting key = mode("key", "Hold key", 0, "C", "Z", "V", "B", "X", "G");
		private final Setting factor = number("factor", "Zoom strength", 1.5F, 8F, 0.5F, 4F);
		private final Setting smooth = bool("smooth", "Smooth zoom", true);
		private final Setting scroll = bool("scroll", "Scroll to zoom further", true);
		private final Setting cinematic = bool("cinematic", "Slower mouse while zoomed", true);

		private static Zoom instance;
		private double current = 1, scrollMul = 1;
		private long lastNanos;
		private Integer savedFov; // left over from older versions that changed the FOV setting

		public Zoom() {
			super("zoom", "Zoom", "Hold a key to zoom in like a spyglass; scroll while holding to change how far. Some servers don't allow zoom: check their rules.", Category.UTILITY);
			instance = this;
		}

		private boolean held(Minecraft mc) {
			return enabled() && mc.gui.screen() == null && mc.getWindow() != null
					&& GLFW.glfwGetKey(mc.getWindow().handle(), KEYS[key.getMode()]) == GLFW.GLFW_PRESS;
		}

		/** How much the camera's field of view is divided by right now (1 = no zoom). Called every frame. */
		public static double divisor() {
			Zoom z = instance;
			if (z == null) {
				return 1;
			}
			Minecraft mc = Minecraft.getInstance();
			boolean on = z.held(mc);
			if (!on) {
				z.scrollMul = 1;
			}
			double target = on ? Math.max(1, z.factor.getFloat() * z.scrollMul) : 1;
			long now = System.nanoTime();
			double dt = z.lastNanos == 0 ? 0.016 : Math.min(0.1, (now - z.lastNanos) / 1e9);
			z.lastNanos = now;
			z.current = z.smooth.getBool() ? z.current + (target - z.current) * (1 - Math.exp(-14 * dt)) : target;
			if (Math.abs(z.current - target) < 0.001) {
				z.current = target;
			}
			return z.current;
		}

		/** Mouse wheel while zooming. True = used (the hotbar doesn't scroll). */
		public static boolean onScroll(double amount) {
			Zoom z = instance;
			if (z == null || !z.scroll.getBool() || !z.held(Minecraft.getInstance()) || amount == 0) {
				return false;
			}
			z.scrollMul = Math.max(0.25, Math.min(12.5, z.scrollMul * (amount > 0 ? 1.2 : 1 / 1.2)));
			return true;
		}

		/** Mouse sensitivity multiplier while zoomed (so aiming stays steady). */
		public static double sensitivity() {
			Zoom z = instance;
			if (z == null || !z.cinematic.getBool() || z.current <= 1.01) {
				return 1;
			}
			return 1 / Math.sqrt(z.current);
		}

		@Override
		public void onTick(Minecraft mc) {
			if (savedFov != null) {
				restore(mc);
			}
		}

		public void restore(Minecraft mc) {
			if (savedFov != null && mc.options != null) {
				mc.options.fov().set(savedFov);
			}
			savedFov = null;
		}

		@Override
		public void onDisable() {
			restore(Minecraft.getInstance());
			current = 1;
		}
	}

	public static final class TimeChanger extends Module {
		public static TimeChanger instance;
		private final Setting time = number("time", "Time of day", 0, 24000, 250, 6000);

		public TimeChanger() {
			super("timechanger", "Time Changer", "Pick your own time of day. Only your screen changes, not the server.", Category.WORLD);
			instance = this;
		}

		public static int override() {
			TimeChanger t = instance;
			return t != null && t.enabled() ? t.time.getInt() : -1;
		}
	}

	/**
	 * Crosshair editor: colour, opacity, and (instead of the game's picture) your own shape with size,
	 * gap, thickness, outline, centre dot and a dynamic gap that opens while you move. Looks only.
	 */
	public static final class CrosshairColor extends Module {
		public static CrosshairColor instance;
		final Setting color = color("color", "Colour", dev.topclient.client.clover.ui.Theme.CLOVER_GREEN);
		final Setting alpha = number("alpha", "Opacity", 0.2F, 1F, 0.05F, 1F);
		final Setting shape = mode("shape", "Shape", 0, "Game default", "Cross", "Cross with gap", "Dot", "Circle", "T", "X", "My drawing");
		final Setting size = number("size", "Size", 2, 20, 1, 6);
		final Setting gap = number("gap", "Gap", 0, 10, 1, 3);
		final Setting thick = number("thick", "Thickness", 1, 6, 1, 2);
		final Setting outline = bool("outline", "Black outline", true);
		final Setting dot = bool("dot", "Centre dot", false);
		final Setting dynamic = bool("dynamic", "Dynamic (opens while moving)", false);
		final Setting vanillaBlending = bool("vanilla_blending", "Vanilla blending", false);
		final Setting dynamicBow = bool("dynamic_bow", "Dynamic bow", false);
		final Setting dynamicAttack = bool("dynamic_attack", "Dynamic attack", false);
		final Setting spectator = bool("spectator", "Visible in spectator", true);
		final Setting healthShift = bool("health_shift", "Health color shift", false);
		final Setting customScale = bool("custom_scale", "Use custom scale", false);
		final Setting scale = number("scale", "Custom scale", 0.5F, 2F, 0.05F, 1F);
		private float spread;

		public static final int SHAPE_DRAWING = 7, GRID = 15;

		/** Everything that decides how a crosshair looks; used for the game and for previews. */
		public record Look(int shape, float size, float gap, float thick, boolean outline, boolean dot, int rgb, float alpha, long[] bits) {
			public Look withBits(long[] b) {
				return new Look(shape, size, gap, thick, outline, dot, rgb, alpha, b);
			}
		}

		public CrosshairColor() {
			super("crosshair", "Crosshair", "Crosshair editor like Lunar: presets, your own drawings, colour, size, gap, thickness, outline and opacity, with a live preview.", Category.VISUAL);
			instance = this;
			action("Open crosshair editor", dev.topclient.client.top.CrosshairScreen::new);
		}

		public static boolean active() {
			return instance != null && instance.enabled();
		}

		public static int argb() {
			return (Math.round(instance.alpha.getFloat() * 255) << 24) | instance.color.getColor();
		}

		public static float renderScale() {
			return instance != null && instance.customScale.getBool() ? instance.scale.getFloat() : 1F;
		}

		public static boolean spectatorVisible() {
			return instance != null && instance.spectator.getBool();
		}

		/** True when the editor draws its own shape instead of the game's picture. */
		public static boolean custom() {
			return active() && instance.shape.getMode() != 0;
		}

		public static Look current() {
			CrosshairColor c = instance;
			return new Look(c.shape.getMode(), c.size.getFloat(), c.gap.getFloat(), c.thick.getFloat(), c.outline.getBool(), c.dot.getBool(),
					c.color.getColor(), c.alpha.getFloat(), dev.topclient.client.top.Crosshairs.currentBits());
		}

		/** Puts a look into the settings (used by presets and the editor). */
		public static void apply(Look l) {
			CrosshairColor c = instance;
			c.shape.setMode(l.shape());
			c.size.setFloat(l.size());
			c.gap.setFloat(l.gap());
			c.thick.setFloat(l.thick());
			c.outline.setBool(l.outline());
			c.dot.setBool(l.dot());
		}

		public static Setting setting(String key) {
			for (Setting s : instance.settings) {
				if (key.equals(s.key)) {
					return s;
				}
			}
			return null;
		}

		/** Draws the in-game crosshair centred on (cx, cy) in GUI units, using real pixels for sharp lines. */
		public static void draw(net.minecraft.client.gui.GuiGraphicsExtractor ctx, float cx, float cy) {
			CrosshairColor c = instance;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && (c.dynamic.getBool() || c.dynamicBow.getBool() || c.dynamicAttack.getBool())) {
				var v = mc.player.getDeltaMovement();
				float target = c.dynamic.getBool()
						? (float) Math.min(8, Math.sqrt(v.x * v.x + v.z * v.z) * 30 + (mc.player.onGround() ? 0 : 3)) : 0F;
				if (c.dynamicBow.getBool() && mc.player.isUsingItem()
						&& mc.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem) {
					target += mc.player.getTicksUsingItem() * 0.15F;
				}
				if (c.dynamicAttack.getBool()) target += (1F - mc.player.getAttackStrengthScale(0F)) * 3F;
				c.spread += (Math.min(12F, target) - c.spread) * 0.25F;
			} else {
				c.spread = 0;
			}
			float gs = (float) mc.getWindow().getGuiScale();
			Look look = current();
			if (c.healthShift.getBool() && mc.player != null) {
				float health = Math.max(0F, Math.min(1F, mc.player.getHealth() / Math.max(1F, mc.player.getMaxHealth())));
				int green = Math.round(255 * health);
				look = new Look(look.shape(), look.size(), look.gap(), look.thick(), look.outline(), look.dot(),
						(255 << 16) | (green << 8), look.alpha(), look.bits());
			}
			drawLook(ctx, cx, cy, renderScale() / gs, gs, look, c.spread);
		}

		/**
		 * Draws a look centred on (cx, cy). unit = how big one screen pixel is in the current
		 * drawing units; gs = the game's GUI scale (sizes follow it, like in game).
		 */
		public static void drawLook(net.minecraft.client.gui.GuiGraphicsExtractor ctx, float cx, float cy, float unit, float gs, Look l, float spread) {
			int col = (Math.round(l.alpha() * 255) << 24) | (l.rgb() & 0xFFFFFF), out = (Math.round(l.alpha() * 200) << 24);
			int s = Math.round(l.size() * gs / 2F), g = Math.round((l.gap() + spread) * gs / 2F), t = Math.max(1, Math.round(l.thick() * gs / 2F));
			boolean ol = l.outline();
			var m = ctx.pose();
			m.pushMatrix();
			m.translate(cx, cy);
			m.scale(unit, unit);
			int h0 = -t / 2, h1 = h0 + t;
			switch (l.shape()) {
				case 1 -> { // cross
					bar(ctx, -s, h0, s, h1, col, out, ol);
					bar(ctx, h0, -s, h1, s, col, out, ol);
				}
				case 2, 5 -> { // cross with gap / T
					bar(ctx, -s - g, h0, -g, h1, col, out, ol);
					bar(ctx, g, h0, s + g, h1, col, out, ol);
					bar(ctx, h0, g, h1, s + g, col, out, ol);
					if (l.shape() == 2) {
						bar(ctx, h0, -s - g, h1, -g, col, out, ol);
					}
				}
				case 3 -> bar(ctx, h0 - t / 2, h0 - t / 2, h1 + t / 2, h1 + t / 2, col, out, ol);
				case 4 -> { // circle: small squares around a ring
					int r = s + g;
					int n = Math.max(16, r * 3);
					for (int i = 0; i < n; i++) {
						double a = i * Math.PI * 2 / n;
						int px = (int) Math.round(Math.cos(a) * r), py = (int) Math.round(Math.sin(a) * r);
						if (ol) {
							ctx.fill(px + h0 - 1, py + h0 - 1, px + h1 + 1, py + h1 + 1, out);
						}
					}
					for (int i = 0; i < n; i++) {
						double a = i * Math.PI * 2 / n;
						int px = (int) Math.round(Math.cos(a) * r), py = (int) Math.round(Math.sin(a) * r);
						ctx.fill(px + h0, py + h0, px + h1, py + h1, col);
					}
				}
				case 6 -> { // X: diagonal steps
					for (int i = g; i <= s + g; i++) {
						ctx.fill(i + h0, i + h0, i + h1, i + h1, col);
						ctx.fill(-i + h0, i + h0, -i + h1, i + h1, col);
						ctx.fill(i + h0, -i + h0, i + h1, -i + h1, col);
						ctx.fill(-i + h0, -i + h0, -i + h1, -i + h1, col);
					}
				}
				case SHAPE_DRAWING -> { // your own pixel drawing; Thickness = size of one drawn pixel
					long[] b = l.bits();
					if (b != null) {
						int cell = t, half = GRID / 2;
						for (int pass = ol ? 0 : 1; pass < 2; pass++) {
							for (int gy = 0; gy < GRID; gy++) {
								for (int gx = 0; gx < GRID; gx++) {
									if (!dev.topclient.client.top.Crosshairs.get(b, gx, gy)) {
										continue;
									}
									int x0 = (gx - half) * cell - cell / 2, y0 = (gy - half) * cell - cell / 2;
									if (pass == 0) {
										ctx.fill(x0 - 1, y0 - 1, x0 + cell + 1, y0 + cell + 1, out);
									} else {
										ctx.fill(x0, y0, x0 + cell, y0 + cell, col);
									}
								}
							}
						}
					}
				}
				default -> {
				}
			}
			if (l.dot() && l.shape() != 3 && l.shape() != SHAPE_DRAWING) {
				bar(ctx, h0, h0, h1, h1, col, out, ol);
			}
			m.popMatrix();
		}

		private static void bar(net.minecraft.client.gui.GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int col, int out, boolean outline) {
			if (outline) {
				ctx.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, out);
			}
			ctx.fill(x0, y0, x1, y1, col);
		}
	}

	public static final class BlockOutlineMod extends Module {
		public static BlockOutlineMod instance;
		private final Setting color = color("color", "Colour", dev.topclient.client.clover.ui.Theme.CLOVER_GREEN);
		private final Setting width = number("width", "Thickness", 1, 6, 1, 3);

		public BlockOutlineMod() {
			super("blockoutline", "Block Outline", "Colours and thickens the lines around the block you look at.", Category.VISUAL);
			instance = this;
		}

		public static boolean active() {
			return instance != null && instance.enabled();
		}

		public static int argb() {
			return 0xFF000000 | instance.color.getColor();
		}

		public static float width(Minecraft mc) {
			return mc.getWindow().getAppropriateLineWidth() * instance.width.getInt();
		}
	}
}
