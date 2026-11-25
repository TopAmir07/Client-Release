package dev.topclient.client.clover.module;

import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class HudModules {
	private HudModules() {
	}

	public static final class Fps extends TextHud {
		private final Setting frameTime = bool("frametime", "Show frame time (ms)", false);
		private final Setting minMaxAvg = bool("minmaxavg", "Show min / avg / max FPS (last 5 s)", false);
		private final Setting updateMs = number("updatems", "Text update interval (ms)", 50, 1000, 50, 250);

		public Fps() {
			super("fps", "FPS", "Shows your frames per second. Optional frame time and min / avg / max measured from real frames.", 1, 5);
		}

		@Override
		protected boolean defaultEnabled() {
			return false; // everything starts off: switch it on with Right Shift
		}

		@Override
		protected long cacheMillis() {
			return updateMs.getInt();
		}

		@Override
		protected String[] lines(Minecraft mc) {
			boolean ft = frameTime.getBool() && PerfStats.ready();
			boolean mm = minMaxAvg.getBool() && PerfStats.ready();
			if (!ft && !mm) {
				return new String[]{mc.getFps() + " FPS"};
			}
			java.util.ArrayList<String> out = new java.util.ArrayList<>(3);
			out.add(mc.getFps() + " FPS");
			if (ft) {
				out.add(String.format(java.util.Locale.ROOT, "%.2f ms", PerfStats.frameMs()));
			}
			if (mm) {
				out.add("min " + PerfStats.min() + "  avg " + PerfStats.avg() + "  max " + PerfStats.max());
			}
			return out.toArray(new String[0]);
		}

		@Override
		protected void draw(net.minecraft.client.gui.GuiGraphicsExtractor ctx, Minecraft mc) {
			PerfStats.frame(); // one real frame sample per drawn frame
			super.draw(ctx, mc);
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}
	}

	public static final class Cps extends TextHud {
		private final Setting both = bool("both", "Show left | right", true);

		public Cps() {
			super("cps", "CPS", "Clicks per second of your mouse buttons.", 1, 12);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return new String[]{both.getBool() ? ClickTracker.left() + " | " + ClickTracker.right() + " CPS" : ClickTracker.left() + " CPS"};
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}
	}

	public static final class Coordinates extends TextHud {
		private final Setting decimals = bool("decimals", "Show decimals", false);
		private final Setting facing = bool("facing", "Show direction", true);

		public Coordinates() {
			super("coords", "Coordinates", "Your X, Y and Z position and the way you face.", 1, 19);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return new String[]{"X: 0  Y: 0  Z: 0"};
			}
			String x, y, z;
			if (decimals.getBool()) {
				x = String.format("%.1f", mc.player.getX());
				y = String.format("%.1f", mc.player.getY());
				z = String.format("%.1f", mc.player.getZ());
			} else {
				x = Integer.toString((int) Math.floor(mc.player.getX()));
				y = Integer.toString((int) Math.floor(mc.player.getY()));
				z = Integer.toString((int) Math.floor(mc.player.getZ()));
			}
			if (facing.getBool()) {
				return new String[]{"X: " + x + "  Y: " + y + "  Z: " + z, "Facing: " + Direction.of(mc.player.getYRot())};
			}
			return new String[]{"X: " + x + "  Y: " + y + "  Z: " + z};
		}
	}

	public static final class Direction {
		private static final String[] NAMES = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};

		public static String of(float yaw) {
			float a = ((yaw % 360) + 360) % 360;
			return NAMES[Math.round(a / 45F) % 8];
		}
	}

	public static final class Compass extends TextHud {
		public Compass() {
			super("direction", "Direction", "A compass heading, like North-East 45 degrees.", 1, 29);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return new String[]{"North 0"};
			}
			float a = ((mc.player.getYRot() % 360) + 360) % 360;
			int heading = Math.round((a + 180) % 360);
			return new String[]{Direction.of(mc.player.getYRot()) + " " + heading + "\u00B0"};
		}
	}

	public static final class Speed extends TextHud {
		private double lastX, lastZ;
		private boolean have;
		private double bps;

		public Speed() {
			super("speed", "Speed", "How fast you move, in blocks per second.", 1, 36);
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null) {
				have = false;
				return;
			}
			double x = mc.player.getX(), z = mc.player.getZ();
			if (have) {
				double d = Math.hypot(x - lastX, z - lastZ) * 20.0;
				bps = bps * 0.7 + d * 0.3;
			}
			lastX = x;
			lastZ = z;
			have = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return new String[]{String.format("%.1f blocks/s", bps)};
		}
	}

	public static final class Ping extends TextHud {
		private final Setting minMaxAvg = bool("minmaxavg", "Show min / avg / max ping (last samples)", false);
		private final Setting updateMs = number("updatems", "Text update interval (ms)", 100, 2000, 100, 500);
		private final int[] ring = new int[60];
		private int ringN;
		private int ringPos;
		private Object lastConnection;

		public Ping() {
			super("ping", "Ping", "The latency the server reports for you (the same value as the tab list). Optional min / avg / max.", 1, 43);
		}

		@Override
		protected long cacheMillis() {
			return updateMs.getInt();
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.isLocalServer() || mc.getConnection() == null || mc.player == null) {
				ringN = 0;
				lastConnection = null;
				return new String[]{"Ping: -"};
			}
			if (mc.getConnection() != lastConnection) {
				lastConnection = mc.getConnection();
				ringN = 0;
				ringPos = 0;
			}
			PlayerInfo e = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			int ms = e == null ? 0 : e.getLatency();
			if (ms <= 0) {
				return new String[]{"Ping: -"};
			}
			ring[ringPos] = ms;
			ringPos = (ringPos + 1) % ring.length;
			ringN = Math.min(ringN + 1, ring.length);
			if (!minMaxAvg.getBool() || ringN < 2) {
				return new String[]{"Ping: " + ms + " ms"};
			}
			int mn = Integer.MAX_VALUE, mx = 0;
			long sum = 0;
			for (int i = 0; i < ringN; i++) {
				mn = Math.min(mn, ring[i]);
				mx = Math.max(mx, ring[i]);
				sum += ring[i];
			}
			return new String[]{"Ping: " + ms + " ms", "min " + mn + "  avg " + (sum / ringN) + "  max " + mx};
		}
	}

	public static final class Clock extends TextHud {
		private final Setting seconds = bool("seconds", "Show seconds", false);
		private final Setting h24 = bool("h24", "24 hour clock", true);

		private String formatPattern;
		private DateTimeFormatter format;

		public Clock() {
			super("clock", "Clock", "Your computer's local time.", 90, 1);
		}

		@Override
		protected long cacheMillis() {
			return 200;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			String pattern = (h24.getBool() ? "HH:mm" : "hh:mm") + (seconds.getBool() ? ":ss" : "") + (h24.getBool() ? "" : " a");
			if (!pattern.equals(formatPattern)) {
				formatPattern = pattern;
				format = DateTimeFormatter.ofPattern(pattern);
			}
			return new String[]{LocalTime.now().format(format)};
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}
	}

	public static final class Memory extends TextHud {
		public Memory() {
			super("memory", "Memory", "How much Java memory the game uses.", 90, 8);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			Runtime r = Runtime.getRuntime();
			long used = (r.totalMemory() - r.freeMemory()) / 1048576L;
			long max = r.maxMemory() / 1048576L;
			return new String[]{"RAM: " + used + " / " + max + " MB"};
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}
	}

	public static final class DayCounter extends TextHud {
		public DayCounter() {
			super("day", "Day Counter", "Which Minecraft day you are on.", 90, 15);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			long day = mc.level == null ? 0 : mc.level.getGameTime() / 24000L + 1;
			return new String[]{"Day " + day};
		}
	}

	public static final class ServerAddress extends TextHud {
		public ServerAddress() {
			super("server", "Server", "The address of the server you are on.", 90, 22);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			var info = mc.getCurrentServer();
			return new String[]{info == null ? (mc.isLocalServer() ? "Singleplayer" : "Not connected") : info.ip};
		}
	}

	public static final class ItemCounter extends TextHud {
		public ItemCounter() {
			super("itemcount", "Item Counter", "How many of the item in your hand you carry in total.", 90, 29);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return new String[]{"Items: 0"};
			}
			ItemStack held = mc.player.getMainHandItem();
			if (held.isEmpty()) {
				return new String[]{"Hand: empty"};
			}
			int total = 0;
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, held)) {
					total += s.getCount();
				}
			}
			return new String[]{held.getHoverName().getString() + ": " + total};
		}
	}

	public static final class PotionStatus extends HudModule {
		public PotionStatus() {
			super("potions", "Potion Status", "Your active effects with the time left.", 1, 49);
		}

		private List<String> rows(Minecraft mc) {
			List<String> out = new ArrayList<>();
			if (mc.player == null) {
				out.add("Speed II  1:30");
				return out;
			}
			for (MobEffectInstance e : mc.player.getActiveEffects()) {
				String n = e.getEffect().value().getDisplayName().getString();
				int lvl = e.getAmplifier() + 1;
				String time;
				if (e.isInfiniteDuration()) {
					time = "inf";
				} else {
					int secs = e.getDuration() / 20;
					time = (secs / 60) + ":" + String.format("%02d", secs % 60);
				}
				out.add(n + (lvl > 1 ? " " + lvl : "") + "  " + time);
			}
			return out;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && !rows(mc).isEmpty();
		}

		@Override
		public int width(Minecraft mc) {
			int w = 10;
			for (String r : rows(mc)) {
				w = Math.max(w, Gfx.font().width(r));
			}
			return w;
		}

		@Override
		public int height(Minecraft mc) {
			return Math.max(1, rows(mc).size()) * 10 - 2;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			List<String> r = rows(mc);
			for (int i = 0; i < r.size(); i++) {
				line(ctx, r.get(i), 0, i * 10);
			}
		}
	}

	public static final class ArmorStatus extends HudModule {
		private final Setting showHand = bool("hand", "Include item in hand", true);
		private final Setting percent = bool("percent", "Show durability %", true);
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

		public ArmorStatus() {
			super("armor", "Armor Status", "Your armor pieces and their durability.", 1, 64);
		}

		private List<ItemStack> stacks(Minecraft mc) {
			List<ItemStack> out = new ArrayList<>();
			if (mc.player == null) {
				return out;
			}
			for (EquipmentSlot s : SLOTS) {
				ItemStack st = mc.player.getItemBySlot(s);
				if (!st.isEmpty()) {
					out.add(st);
				}
			}
			if (showHand.getBool()) {
				ItemStack h = mc.player.getMainHandItem();
				if (!h.isEmpty()) {
					out.add(h);
				}
			}
			return out;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && !stacks(mc).isEmpty();
		}

		@Override
		public int width(Minecraft mc) {
			return 18 + (percent.getBool() ? 30 : 0);
		}

		@Override
		public int height(Minecraft mc) {
			return Math.max(1, stacks(mc).size()) * 18 - 2;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			List<ItemStack> list = stacks(mc);
			for (int i = 0; i < list.size(); i++) {
				ItemStack st = list.get(i);
				int y = i * 18;
				ctx.item(st, 0, y);
				if (percent.getBool() && st.isDamageableItem()) {
					int max = st.getMaxDamage();
					int left = max - st.getDamageValue();
					int pct = Math.round(left * 100F / max);
					int col = pct > 60 ? 0x55FF55 : pct > 25 ? 0xFFFF55 : 0xFF5555;
					ctx.text(Gfx.font(), pct + "%", 19, y + 4, 0xFF000000 | col, shadow.getBool());
				} else if (percent.getBool() && st.getCount() > 1) {
					ctx.text(Gfx.font(), "x" + st.getCount(), 19, y + 4, 0xFF000000 | textColor.getColor(), shadow.getBool());
				}
			}
		}
	}

	public static final class Keystrokes extends HudModule {
		private final Setting mouse = bool("mouse", "Show mouse buttons", true);
		private final Setting space = bool("space", "Show space bar", true);
		private final Setting cpsInKeys = bool("cpsInKeys", "CPS inside the mouse keys", true);
		private final Setting pressedColor = color("pressed", "Pressed colour", Theme.CLOVER_GREEN);
		private final Setting keyStyle = mode("keyStyle", "Key style", 0, "Clear (thin outline)", "Filled boxes", "Letters only");

		private static final int K = 18, GAP = 2;

		public Keystrokes() {
			super("keystrokes", "Keystrokes", "Shows the movement keys and mouse buttons you press.", 1, 76);
		}

		@Override
		public boolean available(Minecraft mc) {
			return mc.player != null;
		}

		@Override
		public int width(Minecraft mc) {
			return K * 3 + GAP * 2;
		}

		@Override
		public int height(Minecraft mc) {
			int h = K * 2 + GAP;
			if (mouse.getBool()) {
				h += GAP + K;
			}
			if (space.getBool()) {
				h += GAP + 10;
			}
			return h;
		}

		private void key(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean down) {
			int style = keyStyle.getMode();
			int tc;
			if (style == 1) { // the old filled boxes
				Gfx.rect(ctx, x, y, w, h, 2, down ? (0xCC000000 | pressedColor.getColor()) : 0x66000000);
				tc = down ? 0xFF000000 : textA() | textColor.getColor();
			} else if (style == 0) { // clear: thin outline, soft fill only while pressed
				if (down) {
					Gfx.rect(ctx, x, y, w, h, 2, 0x8C000000 | pressedColor.getColor());
				}
				Gfx.frame(ctx, x, y, w, h, 2, down ? 0xFF000000 | pressedColor.getColor() : 0x59FFFFFF);
				tc = down ? 0xFF000000 : textA() | textColor.getColor();
			} else { // letters only
				tc = down ? 0xFF000000 | pressedColor.getColor() : textA() | textColor.getColor();
			}
			int tx = x + (w - Gfx.font().width(label)) / 2, ty = y + (h - 8) / 2;
			if (style != 1) {
				outlineText(ctx, label, tx, ty);
			}
			ctx.text(Gfx.font(), label, tx, ty, tc, style != 1 && shadow.getBool() && !outline.getBool() && !(down && style == 0));
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			var o = mc.options;
			key(ctx, K + GAP, 0, K, K, "W", o.keyUp.isDown());
			key(ctx, 0, K + GAP, K, K, "A", o.keyLeft.isDown());
			key(ctx, K + GAP, K + GAP, K, K, "S", o.keyDown.isDown());
			key(ctx, (K + GAP) * 2, K + GAP, K, K, "D", o.keyRight.isDown());
			int y = K * 2 + GAP * 2;
			if (mouse.getBool()) {
				int w = (K * 3 + GAP * 2 - GAP) / 2;
				boolean cps = cpsInKeys.getBool();
				key(ctx, 0, y, w, K, cps ? "LMB " + ClickTracker.left() : "LMB", ClickTracker.leftHeld());
				key(ctx, w + GAP, y, w, K, cps ? "RMB " + ClickTracker.right() : "RMB", ClickTracker.rightHeld());
				y += K + GAP;
			}
			if (space.getBool()) {
				key(ctx, 0, y, K * 3 + GAP * 2, 10, "-----", o.keyJump.isDown());
			}
		}
	}

	public static final class SessionTimer extends TextHud {
		private static final long START = System.currentTimeMillis();
		private final Setting sinceJoin = bool("sinceJoin", "Count from joining the world", true);
		private long joined;
		private boolean inWorld;

		public SessionTimer() {
			super("session", "Session Timer", "How long you have been playing.", 90, 36);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			boolean now = mc.level != null;
			if (now && !inWorld) {
				joined = System.currentTimeMillis();
			}
			inWorld = now;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			long from = sinceJoin.getBool() && joined > 0 ? joined : START;
			long secs = (System.currentTimeMillis() - from) / 1000;
			String t = secs >= 3600 ? String.format("%d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60)
					: String.format("%d:%02d", secs / 60, secs % 60);
			return new String[]{"Session: " + t};
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}
	}

	public static final class Saturation extends TextHud {
		private final Setting showExhaustion = bool("exhaustion", "Show exhaustion", true);

		public Saturation() {
			super("saturation", "Food Info", "AppleSkin-style food HUD: hunger, saturation and optional exhaustion.", 90, 43);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return new String[]{"Food: 20  Sat: 5.0"};
			}
			var food = mc.player.getFoodData();
			String main = String.format("Food: %d  Sat: %.1f", food.getFoodLevel(), food.getSaturationLevel());
			if (!showExhaustion.getBool()) {
				return new String[]{main};
			}
			return new String[]{main, String.format("Exhaustion: %.2f / 4.00", food.getExhaustionLevel())};
		}
	}

	public static final class PlayerCount extends TextHud {
		public PlayerCount() {
			super("players", "Player Count", "How many players are on the server (tab list).", 90, 50);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			int n = mc.getConnection() == null ? 1 : mc.getConnection().getOnlinePlayers().size();
			return new String[]{"Players: " + n};
		}
	}
}
