package dev.topclient.client.clover.module;

import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Theme;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CloverModules13 {
	private CloverModules13() {
	}

	public static final class HorseStats extends TextHud {
		public HorseStats() {
			super("horsestats", "Horse Stats", "Speed, jump height and health of the horse you look at or ride.", 50, 60);
			isNew = true;
		}

		private static AbstractHorse horse(Minecraft mc) {
			if (mc.player == null) {
				return null;
			}
			if (mc.player.getVehicle() instanceof AbstractHorse h) {
				return h;
			}
			return mc.crosshairPickEntity instanceof AbstractHorse h ? h : null;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && horse(mc) != null;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			AbstractHorse h = horse(mc);
			if (h == null) {
				return new String[]{"Speed: 13.2 blocks/s", "Jump: 3.9 blocks", "Health: 13 hearts"};
			}
			double speed = h.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 42.16;
			double j = h.getAttributeBaseValue(Attributes.JUMP_STRENGTH);
			double jump = -0.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - 0.343930367;
			float hearts = h.getMaxHealth() / 2F;
			return new String[]{
					String.format("Speed: %.1f blocks/s", speed),
					String.format("Jump: %.1f blocks", Math.max(0, jump)),
					String.format("Health: %s hearts", hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.format("%.1f", hearts))};
		}
	}

	public static final class DeathCoords extends TextHud {
		private final Setting distance = bool("distance", "Show distance", true);
		private boolean wasDead;
		private int[] spot;
		private String where = "";

		public DeathCoords() {
			super("deathcoords", "Death Coordinates", "Remembers where you last died so you can get your items back.", 1, 84);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null || mc.level == null) {
				return;
			}
			boolean dead = mc.player.isDeadOrDying();
			if (dead && !wasDead) {
				spot = new int[]{(int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ())};
				where = mc.level.dimension().identifier().getPath();
			}
			wasDead = dead;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && spot != null;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (spot == null) {
				return new String[]{"Last death: 120 64 -340"};
			}
			String line = "Last death: " + spot[0] + " " + spot[1] + " " + spot[2];
			if (distance.getBool() && mc.player != null && mc.level != null && mc.level.dimension().identifier().getPath().equals(where)) {
				line += "  (" + Math.round(Math.hypot(mc.player.getX() - spot[0], mc.player.getZ() - spot[2])) + "m)";
			} else if (!where.isEmpty()) {
				line += "  (" + where.replace("the_", "") + ")";
			}
			return new String[]{line};
		}
	}

	public static final class NetherCoords extends TextHud {
		public NetherCoords() {
			super("nethercoords", "Nether Coordinates", "Shows where you would be in the Nether (or the Overworld) - great for portals.", 1, 26);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null || mc.level == null) {
				return new String[]{"Nether: 15 / -42"};
			}
			boolean nether = mc.level.dimension() == Level.NETHER;
			double x = mc.player.getX(), z = mc.player.getZ();
			return nether
					? new String[]{"Overworld: " + (int) Math.floor(x * 8) + " / " + (int) Math.floor(z * 8)}
					: new String[]{"Nether: " + (int) Math.floor(x / 8) + " / " + (int) Math.floor(z / 8)};
		}
	}

	public static final class LowHealth extends HudModule {
		private final Setting below = number("below", "Warn at or below (hearts)", 1, 10, 0.5F, 4);
		private final Setting strength = number("strength", "Strength", 0.2F, 1F, 0.05F, 0.6F);

		public LowHealth() {
			super("lowhealth", "Low Health Warning", "The screen edges glow red when your health is low.", 50, 50);
			isNew = true;
		}

		@Override
		public int width(Minecraft mc) {
			return Gfx.font().width("Low Health Warning");
		}

		@Override
		public int height(Minecraft mc) {
			return 8;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			ctx.text(Gfx.font(), "Low Health Warning", 0, 0, 0xFFFF5555, true);
		}

		@Override
		public void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
			if (preview) {
				super.render(ctx, mc, true);
				return;
			}
			if (mc.player == null || mc.player.isDeadOrDying()) {
				return;
			}
			float hearts = mc.player.getHealth() / 2F;
			if (hearts > below.getFloat()) {
				return;
			}
			float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 180.0);
			int a = Math.round(200 * strength.getFloat() * pulse);
			int edge = (a << 24) | 0xC00000;
			int w = ctx.guiWidth(), h = ctx.guiHeight();
			int s = Math.max(30, Math.min(w, h) / 5);
			ctx.fillGradient(0, 0, w, s, edge, 0);
			ctx.fillGradient(0, h - s, w, h, 0, edge);
			for (int i = 0; i < s; i += 2) {
				int c = (Math.round(a * (1F - i / (float) s)) << 24) | 0xC00000;
				ctx.fill(i, 0, i + 2, h, c);
				ctx.fill(w - i - 2, 0, w - i, h, c);
			}
		}
	}

	public static final class PickupNotifier extends HudModule {
		private record Pickup(String name, int amount, long at) {
		}

		private final Setting seconds = number("seconds", "Show for (seconds)", 1, 8, 0.5F, 3);
		private final ArrayDeque<Pickup> recent = new ArrayDeque<>();
		private Map<String, Integer> last;
		private int settle;

		public PickupNotifier() {
			super("pickups", "Pickup Notifier", "Shows +3 Diamond and so on when you pick items up.", 75, 70);
			isNew = true;
		}

		private static Map<String, Integer> snapshot(Minecraft mc) {
			Map<String, Integer> m = new HashMap<>();
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty()) {
					m.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
				}
			}
			return m;
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null) {
				last = null;
				settle = 0;
				return;
			}
			Map<String, Integer> now = snapshot(mc);
			if (last != null && settle > 20 && mc.gui.screen() == null) {
				for (Map.Entry<String, Integer> e : now.entrySet()) {
					int diff = e.getValue() - last.getOrDefault(e.getKey(), 0);
					if (diff > 0) {
						recent.addFirst(new Pickup(e.getKey(), diff, System.currentTimeMillis()));
					}
				}
				while (recent.size() > 5) {
					recent.removeLast();
				}
			}
			if (mc.gui.screen() == null) {
				last = now;
			}
			settle++;
			long cut = System.currentTimeMillis() - Math.round(seconds.getFloat() * 1000);
			recent.removeIf(p -> p.at() < cut);
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && !recent.isEmpty();
		}

		private List<String> rows() {
			List<String> out = new ArrayList<>();
			if (recent.isEmpty()) {
				out.add("+3 Diamond");
				return out;
			}
			for (Pickup p : recent) {
				out.add("+" + p.amount() + " " + p.name());
			}
			return out;
		}

		@Override
		public int width(Minecraft mc) {
			int w = 30;
			for (String r : rows()) {
				w = Math.max(w, Gfx.font().width(r));
			}
			return w;
		}

		@Override
		public int height(Minecraft mc) {
			return rows().size() * 10 - 2;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			List<String> r = rows();
			for (int i = 0; i < r.size(); i++) {
				ctx.text(Gfx.font(), r.get(i), 0, i * 10, 0xFF000000 | (i == 0 ? 0x7CFF8A : textColor.getColor()), shadow.getBool());
			}
		}
	}

	public static final class SessionStats extends TextHud {
		private static int hits;
		private double walked;
		private int jumps;
		private double lx, lz;
		private boolean have, wasGround = true;

		public SessionStats() {
			super("sessionstats", "Session Stats", "Distance walked, jumps and hits since you started playing.", 90, 85);
			isNew = true;
		}

		static void hook() {
			AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
				if (world.isClientSide() && player == Minecraft.getInstance().player && entity instanceof LivingEntity) {
					hits++;
				}
				return InteractionResult.PASS;
			});
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null) {
				have = false;
				return;
			}
			double x = mc.player.getX(), z = mc.player.getZ();
			if (have) {
				double d = Math.hypot(x - lx, z - lz);
				if (d < 10) {
					walked += d;
				}
			}
			boolean ground = mc.player.onGround();
			if (wasGround && !ground && mc.player.getDeltaMovement().y > 0.2) {
				jumps++;
			}
			wasGround = ground;
			lx = x;
			lz = z;
			have = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			String dist = walked >= 1000 ? String.format("%.2f km", walked / 1000) : Math.round(walked) + " m";
			return new String[]{"Walked: " + dist, "Jumps: " + jumps + "   Hits: " + hits};
		}
	}

	public static final class MovementStatus extends TextHud {
		public MovementStatus() {
			super("movement", "Movement Status", "Shows if you are sprinting, sneaking, flying, swimming or riding.", 1, 91);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return new String[]{"[Sprinting]"};
			}
			Entity v = mc.player.getVehicle();
			String s = v != null ? "Riding" : mc.player.getAbilities().flying ? "Flying" : mc.player.isSwimming() ? "Swimming"
					: mc.player.isShiftKeyDown() ? "Sneaking" : mc.player.isSprinting() ? "Sprinting" : "Walking";
			return new String[]{"[" + s + "]"};
		}
	}

	public static final class FpsGraph extends HudModule {
		private final int[] history = new int[90];
		private int pos;

		public FpsGraph() {
			super("fpsgraph", "FPS Graph", "A live graph of your FPS for the last few seconds.", 1, 98);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			history[pos] = mc.getFps();
			pos = (pos + 1) % history.length;
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}

		@Override
		public int width(Minecraft mc) {
			return history.length;
		}

		@Override
		public int height(Minecraft mc) {
			return 40;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			int max = 30, min = Integer.MAX_VALUE, sum = 0, n = 0;
			for (int v : history) {
				if (v > 0) {
					max = Math.max(max, v);
					min = Math.min(min, v);
					sum += v;
					n++;
				}
			}
			int accent = colorAt(0);
			for (int i = 0; i < history.length; i++) {
				int v = history[(pos + i) % history.length];
				int bh = Math.round(28F * v / max);
				ctx.fill(i, 38 - bh, i + 1, 38, (accent & 0x00FFFFFF) | 0xB0000000);
			}
			String label = n == 0 ? "FPS" : "FPS " + (sum / n) + "  min " + min + "  max " + max;
			ctx.text(Gfx.font(), label, 0, 0, 0xFF000000 | textColor.getColor(), shadow.getBool());
		}
	}

	public static final class AnalogClock extends HudModule {
		private final Setting size = number("size", "Size", 30, 90, 2, 46);
		private final Setting secondsHand = bool("seconds", "Seconds hand", true);

		public AnalogClock() {
			super("analogclock", "Analog Clock", "A real ticking clock face with your PC time.", 95, 40);
			isNew = true;
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}

		@Override
		public int width(Minecraft mc) {
			return size.getInt();
		}

		@Override
		public int height(Minecraft mc) {
			return size.getInt();
		}

		private static void line(GuiGraphicsExtractor ctx, float cx, float cy, double angle, float len, int color, int thick) {
			int steps = Math.max(2, Math.round(len));
			for (int i = 0; i <= steps; i++) {
				float t = i / (float) steps * len;
				int x = Math.round(cx + (float) Math.sin(angle) * t), y = Math.round(cy - (float) Math.cos(angle) * t);
				ctx.fill(x - thick / 2, y - thick / 2, x - thick / 2 + thick, y - thick / 2 + thick, color);
			}
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			int s = size.getInt();
			float r = s / 2F, c = r;
			int rim = 0xFF000000 | Theme.accent();
			for (int i = 0; i < 60; i++) {
				double a = Math.PI * 2 * i / 60;
				boolean hour = i % 5 == 0;
				int x = Math.round(c + (float) Math.sin(a) * (r - 2)), y = Math.round(c - (float) Math.cos(a) * (r - 2));
				ctx.fill(x - (hour ? 1 : 0), y - (hour ? 1 : 0), x + 1, y + 1, hour ? rim : 0x99FFFFFF);
			}
			LocalTime t = LocalTime.now();
			double sec = t.getSecond() + t.getNano() / 1e9;
			double min = t.getMinute() + sec / 60;
			double hr = (t.getHour() % 12) + min / 60;
			line(ctx, c, c, Math.PI * 2 * hr / 12, r * 0.5F, 0xFFFFFFFF, 2);
			line(ctx, c, c, Math.PI * 2 * min / 60, r * 0.75F, 0xFFE0E0E0, 1);
			if (secondsHand.getBool()) {
				line(ctx, c, c, Math.PI * 2 * sec / 60, r * 0.82F, rim, 1);
			}
			Gfx.rect(ctx, Math.round(c) - 2, Math.round(c) - 2, 4, 4, 2, rim);
		}
	}

	public static final class DayCycle extends HudModule {
		public DayCycle() {
			super("daycycle", "Day Cycle Bar", "A bar showing where the sun is: day, sunset, night and sunrise.", 50, 10);
			isNew = true;
		}

		@Override
		public int width(Minecraft mc) {
			return 120;
		}

		@Override
		public int height(Minecraft mc) {
			return 18;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			long time = mc.level == null ? 6000 : Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
			int w = 120;
			int[][] seg = {{0, 12000, 0xFF79C7FF}, {12000, 13800, 0xFFFF9A4D}, {13800, 22200, 0xFF1B2A55}, {22200, 24000, 0xFFFFB37A}};
			for (int[] sgm : seg) {
				int x0 = Math.round(w * sgm[0] / 24000F), x1 = Math.round(w * sgm[1] / 24000F);
				ctx.fill(x0, 11, x1, 17, sgm[2]);
			}
			int mx = Math.round(w * time / 24000F);
			boolean night = time >= 13000 && time < 23000;
			Gfx.rect(ctx, Math.max(0, mx - 3), 9, 7, 10, 3, night ? 0xFFE6E6FF : 0xFFFFE066);
			long day = mc.level == null ? 1 : mc.level.getGameTime() / 24000L + 1;
			int hours = (int) ((time / 1000 + 6) % 24), minutes = (int) ((time % 1000) * 60 / 1000);
			ctx.text(Gfx.font(), String.format("Day %d  %02d:%02d  %s", day, hours, minutes, night ? "Night" : "Day"), 0, 0,
					0xFF000000 | textColor.getColor(), shadow.getBool());
		}
	}
}
