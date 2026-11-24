package dev.topclient.client.clover.module;

import dev.topclient.client.clover.ui.Gfx;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MoreModules {
	private MoreModules() {
	}

	static int count(Minecraft mc, Item... items) {
		if (mc.player == null) {
			return 0;
		}
		int total = 0;
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			for (Item it : items) {
				if (!s.isEmpty() && s.is(it)) {
					total += s.getCount();
				}
			}
		}
		if (inv.getContainerSize() < 41) {
			ItemStack off = mc.player.getOffhandItem();
			for (Item it : items) {
				if (!off.isEmpty() && off.is(it)) {
					total += off.getCount();
				}
			}
		}
		return total;
	}

	private static String pretty(String path) {
		StringBuilder b = new StringBuilder();
		for (String w : path.split("_")) {
			if (w.isEmpty()) {
				continue;
			}
			if (b.length() > 0) {
				b.append(' ');
			}
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}

	public static final class ComboCounter extends TextHud {
		private static ComboCounter instance;
		private final Setting timeout = number("timeout", "Reset after (seconds)", 1, 6, 0.5F, 3);
		private final Setting hideZero = bool("hideZero", "Hide when 0", false);
		private int combo;
		private long lastHit;
		private int lastHurt;

		public ComboCounter() {
			super("combo", "Combo Counter", "How many hits in a row you land without getting hit back.", 50, 70);
			isNew = true;
			instance = this;
		}

		static void hook() {
			AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
				Minecraft mc = Minecraft.getInstance();
				ComboCounter c = instance;
				if (c != null && world.isClientSide() && player == mc.player && entity instanceof LivingEntity) {
					c.combo++;
					c.lastHit = System.currentTimeMillis();
				}
				return InteractionResult.PASS;
			});
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null) {
				combo = 0;
				return;
			}
			int hurt = mc.player.hurtTime;
			if (hurt > lastHurt) {
				combo = 0;
			}
			lastHurt = hurt;
			if (combo > 0 && System.currentTimeMillis() - lastHit > timeout.getFloat() * 1000) {
				combo = 0;
			}
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && !(hideZero.getBool() && combo == 0);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return new String[]{"Combo: " + combo};
		}
	}

	public static final class TotemCounter extends TextHud {
		private final Setting hideZero = bool("hideZero", "Hide when you have none", true);

		public TotemCounter() {
			super("totems", "Totem Counter", "How many Totems of Undying you carry.", 90, 57);
			isNew = true;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && !(hideZero.getBool() && count(mc, Items.TOTEM_OF_UNDYING) == 0);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return new String[]{"Totems: " + count(mc, Items.TOTEM_OF_UNDYING)};
		}
	}

	public static final class ArrowCounter extends TextHud {
		private final Setting onlyBow = bool("onlyBow", "Only while holding a bow or crossbow", false);

		public ArrowCounter() {
			super("arrows", "Arrow Counter", "How many arrows (normal, spectral and tipped) you have.", 90, 64);
			isNew = true;
		}

		@Override
		public boolean available(Minecraft mc) {
			if (!super.available(mc)) {
				return false;
			}
			if (onlyBow.getBool()) {
				ItemStack h = mc.player.getMainHandItem();
				ItemStack o = mc.player.getOffhandItem();
				return h.is(Items.BOW) || h.is(Items.CROSSBOW) || o.is(Items.BOW) || o.is(Items.CROSSBOW);
			}
			return true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return new String[]{"Arrows: " + count(mc, Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW)};
		}
	}

	public static final class BiomeDisplay extends TextHud {
		public BiomeDisplay() {
			super("biome", "Biome", "The biome you are standing in.", 1, 56);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.level == null || mc.player == null) {
				return new String[]{"Biome: Plains"};
			}
			String name = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
					.map(k -> pretty(k.identifier().getPath())).orElse("Unknown");
			return new String[]{"Biome: " + name};
		}
	}

	public static final class Stopwatch extends TextHud {
		private final Setting startKey = key("start", "Start / stop key", 0);
		private final Setting resetKey = key("reset", "Reset key", 0);
		private long startedAt;
		private long stored;
		private boolean running;
		private boolean startWas, resetWas;

		public Stopwatch() {
			super("stopwatch", "Stopwatch", "A stopwatch for speedruns and parkour. Set its keys in the settings.", 50, 4);
			isNew = true;
		}

		private Setting key(String k, String label, int def) {
			Setting s = Setting.key(this, k, label, def);
			settings.add(s);
			return s;
		}

		private long elapsed() {
			return stored + (running ? System.currentTimeMillis() - startedAt : 0);
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.getWindow() == null) {
				return;
			}
			boolean s = startKey.getKey() > 0 && InputConstants.isKeyDown(mc.getWindow(), startKey.getKey());
			boolean r = resetKey.getKey() > 0 && InputConstants.isKeyDown(mc.getWindow(), resetKey.getKey());
			if (mc.gui.screen() == null) {
				if (s && !startWas) {
					if (running) {
						stored = elapsed();
						running = false;
					} else {
						startedAt = System.currentTimeMillis();
						running = true;
					}
				}
				if (r && !resetWas) {
					running = false;
					stored = 0;
				}
			}
			startWas = s;
			resetWas = r;
		}

		@Override
		public boolean available(Minecraft mc) {
			return true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			long ms = elapsed();
			String t = String.format("%d:%02d.%02d", ms / 60000, (ms / 1000) % 60, (ms / 10) % 100);
			if (startKey.getKey() <= 0) {
				return new String[]{"Stopwatch " + t + " (set a key)"};
			}
			return new String[]{(running ? "> " : "|| ") + t};
		}
	}

	public static final class CompassBar extends HudModule {
		private final Setting widthSetting = number("width", "Width", 100, 300, 10, 180);
		private final Setting degrees = bool("degrees", "Show degrees", true);

		public CompassBar() {
			super("compassbar", "Compass Bar", "A moving compass strip at the top of the screen, like in Lunar.", 50, 1);
			isNew = true;
		}

		@Override
		public int width(Minecraft mc) {
			return widthSetting.getInt();
		}

		@Override
		public int height(Minecraft mc) {
			return degrees.getBool() ? 22 : 13;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			int w = width(mc);
			float yaw = mc.player == null ? 180F : mc.player.getYRot();
			float heading = ((yaw + 180F) % 360F + 360F) % 360F;
			float pxPerDeg = 1.4F;
			int mid = w / 2;
			for (int d = 0; d < 360; d += 15) {
				float diff = d - heading;
				diff = ((diff + 540F) % 360F) - 180F;
				int x = Math.round(mid + diff * pxPerDeg);
				if (x < 2 || x > w - 2) {
					continue;
				}
				String label = switch (d) {
					case 0 -> "N";
					case 45 -> "NE";
					case 90 -> "E";
					case 135 -> "SE";
					case 180 -> "S";
					case 225 -> "SW";
					case 270 -> "W";
					case 315 -> "NW";
					default -> null;
				};
				int c = colorAt(x);
				if (label != null) {
					int lw = Gfx.font().width(label);
					ctx.text(Gfx.font(), label, x - lw / 2, 1, d % 90 == 0 ? c : (c & 0x00FFFFFF) | 0xB0000000, shadow.getBool());
				} else {
					ctx.fill(x, 4, x + 1, 9, (c & 0x00FFFFFF) | 0x90000000);
				}
			}
			ctx.fill(mid, 10, mid + 1, 13, 0xFFFFFFFF);
			if (degrees.getBool()) {
				String deg = Math.round(heading) % 360 + "°";
				int dw = Gfx.font().width(deg);
				ctx.text(Gfx.font(), deg, mid - dw / 2, 14, colorAt(mid), shadow.getBool());
			}
		}

		@Override
		public boolean available(Minecraft mc) {
			return mc.player != null;
		}
	}

	public static final class WorldTime extends TextHud {
		private final Setting h24 = bool("h24", "24 hour clock", true);

		public WorldTime() {
			super("worldtime", "World Time", "The time of day in your Minecraft world (not your PC clock).", 90, 71);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			long t = mc.level == null ? 6000 : Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
			int hours = (int) ((t / 1000 + 6) % 24);
			int minutes = (int) ((t % 1000) * 60 / 1000);
			String part = t < 12542 ? "Day" : t < 13702 ? "Sunset" : t < 22812 ? "Night" : "Sunrise";
			String clock;
			if (h24.getBool()) {
				clock = String.format("%02d:%02d", hours, minutes);
			} else {
				int h12 = hours % 12 == 0 ? 12 : hours % 12;
				clock = String.format("%d:%02d %s", h12, minutes, hours < 12 ? "AM" : "PM");
			}
			return new String[]{clock + "  " + part};
		}
	}

	public static final class Weather extends TextHud {
		public Weather() {
			super("weather", "Weather", "Shows if it is clear, raining or thundering.", 90, 78);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.level == null) {
				return new String[]{"Weather: Clear"};
			}
			String w = mc.level.isThundering() ? "Thunder" : mc.level.isRaining() ? "Rain" : "Clear";
			return new String[]{"Weather: " + w};
		}
	}

	public static final class DurabilityAlert extends HudModule {
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
				EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		private final Setting threshold = number("threshold", "Warn below (%)", 5, 50, 1, 15);
		private final Setting flash = bool("flash", "Flash", true);

		public DurabilityAlert() {
			super("durability", "Durability Alert", "Warns you when your armour or the tool in your hand is about to break.", 50, 30);
			isNew = true;
		}

		private List<String> warnings(Minecraft mc) {
			List<String> out = new ArrayList<>();
			if (mc.player == null) {
				out.add("Diamond Sword 9%");
				return out;
			}
			for (EquipmentSlot slot : SLOTS) {
				ItemStack st = mc.player.getItemBySlot(slot);
				if (st.isEmpty() || !st.isDamageableItem() || st.getMaxDamage() <= 0) {
					continue;
				}
				int pct = Math.round((st.getMaxDamage() - st.getDamageValue()) * 100F / st.getMaxDamage());
				if (pct <= threshold.getInt()) {
					out.add(st.getHoverName().getString() + " " + pct + "%");
				}
			}
			return out;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && !warnings(mc).isEmpty();
		}

		@Override
		public int width(Minecraft mc) {
			int w = 40;
			for (String s : warnings(mc)) {
				w = Math.max(w, Gfx.font().width("! " + s));
			}
			return w;
		}

		@Override
		public int height(Minecraft mc) {
			return Math.max(1, warnings(mc).size()) * 10 - 2;
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			boolean on = !flash.getBool() || (System.currentTimeMillis() / 400) % 2 == 0;
			int col = on ? 0xFFFF5555 : 0xFFFFAA55;
			List<String> ws = warnings(mc);
			for (int i = 0; i < ws.size(); i++) {
				ctx.text(Gfx.font(), "! " + ws.get(i), 0, i * 10, col, shadow.getBool());
			}
		}
	}

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
