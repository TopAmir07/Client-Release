package dev.topclient.client.clover.module;

import dev.topclient.client.clover.Config;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CloverModules16 {
	private CloverModules16() {
	}

	private static String[] one(String s) {
		return new String[]{s};
	}

	public static final class ReachDisplay extends TextHud {
		private static double last = -1;
		private static long at;

		public ReachDisplay() {
			super("reach", "Reach Display", "How far away you were when you last hit something.", 1, 40);
			isNew = true;
			AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
				Minecraft mc = Minecraft.getInstance();
				if (world.isClientSide() && player == mc.player) {
					last = Math.sqrt(player.distanceToSqr(entity));
					at = System.currentTimeMillis();
				}
				return InteractionResult.PASS;
			});
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (last < 0 || System.currentTimeMillis() - at > 4000) {
				return one("Reach: -");
			}
			return one(String.format(Locale.ROOT, "Reach: %.2f blocks", last));
		}
	}

	public static final class LightLevel extends TextHud {
		public LightLevel() {
			super("lightlevel", "Light Level", "Block and sky light where you stand. Mobs spawn at block light 0.", 1, 46);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null || mc.level == null) {
				return one("Light: 12 (sky 15)");
			}
			BlockPos p = mc.player.blockPosition();
			int block = mc.level.getBrightness(LightLayer.BLOCK, p);
			int sky = mc.level.getBrightness(LightLayer.SKY, p);
			return one("Light: " + block + " (sky " + sky + ")" + (block == 0 ? "  mobs can spawn" : ""));
		}
	}

	public static final class YLevel extends TextHud {
		public YLevel() {
			super("ylevel", "Y Level", "Your height, with a hint of which ores are common there.", 1, 52);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			int y = mc.player == null ? -58 : mc.player.blockPosition().getY();
			String hint;
			if (y <= -50) {
				hint = "diamonds, redstone";
			} else if (y <= 0) {
				hint = "diamonds, lapis, gold";
			} else if (y <= 16) {
				hint = "iron, lapis, gold";
			} else if (y <= 48) {
				hint = "copper, iron";
			} else if (y <= 96) {
				hint = "coal, copper";
			} else {
				hint = "coal, emeralds (mountains)";
			}
			return one("Y " + y + "  - " + hint);
		}
	}

	public static final class ChunkCoords extends TextHud {
		public ChunkCoords() {
			super("chunk", "Chunk Coordinates", "Which chunk you are in, and where in that chunk.", 1, 58);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return one("Chunk: 3 / -7  (5, 12)");
			}
			BlockPos p = mc.player.blockPosition();
			return one("Chunk: " + (p.getX() >> 4) + " / " + (p.getZ() >> 4) + "  (" + (p.getX() & 15) + ", " + (p.getZ() & 15) + ")");
		}
	}

	public static final class Facing extends TextHud {
		public Facing() {
			super("facing", "Rotation", "Exact yaw and pitch (where you are looking), for builders and bridging.", 1, 64);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return one("Yaw 45.0  Pitch 12.5");
			}
			float yaw = ((mc.player.getYRot() % 360) + 540) % 360 - 180;
			String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
			String dir = dirs[Math.floorMod(Math.round((yaw + 360) / 45F), 8)];
			return one(String.format(Locale.ROOT, "%s  Yaw %.1f  Pitch %.1f", dir, yaw, mc.player.getXRot()));
		}
	}

	public static final class HeldItem extends TextHud {
		public HeldItem() {
			super("helditem", "Held Item Info", "Name, count and durability of the item in your hand.", 50, 78);
			isNew = true;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && mc.player != null && !mc.player.getMainHandItem().isEmpty();
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
				return one("Diamond Sword  (1520 / 1561)");
			}
			ItemStack s = mc.player.getMainHandItem();
			String t = s.getHoverName().getString();
			if (s.isDamageableItem()) {
				t += "  (" + (s.getMaxDamage() - s.getDamageValue()) + " / " + s.getMaxDamage() + ")";
			} else if (s.getCount() > 1) {
				t += "  x" + s.getCount();
			}
			return one(t);
		}
	}

	public static final class XpInfo extends TextHud {
		public XpInfo() {
			super("xp", "XP Info", "Your level and how far you are to the next one.", 50, 72);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return one("Level 30  (45%)");
			}
			return one("Level " + mc.player.experienceLevel + "  (" + Math.round(mc.player.experienceProgress * 100) + "%)");
		}
	}

	public static final class HealthDisplay extends TextHud {
		public HealthDisplay() {
			super("health", "Health Numbers", "Your exact health and absorption as numbers.", 34, 86);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return one("Health 18.0 / 20  +4");
			}
			float abs = mc.player.getAbsorptionAmount();
			return one(String.format(Locale.ROOT, "Health %.1f / %.0f", mc.player.getHealth(), mc.player.getMaxHealth())
					+ (abs > 0 ? String.format(Locale.ROOT, "  +%.0f", abs) : ""));
		}
	}

	public static final class DateDisplay extends TextHud {
		private final Setting style = mode("style", "Format", 0, "Sun 1 Nov 2026", "01/11/2026", "2026-11-01");

		public DateDisplay() {
			super("date", "Date", "Today's date from your PC.", 99, 8);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			String f = switch (style.getMode()) {
				case 1 -> "dd/MM/yyyy";
				case 2 -> "yyyy-MM-dd";
				default -> "EEE d MMM yyyy";
			};
			return one(LocalDate.now().format(DateTimeFormatter.ofPattern(f, Locale.ENGLISH)));
		}
	}

	public static final class KillCounter extends TextHud {
		private int deaths;
		private boolean wasDead;

		public KillCounter() {
			super("kills", "Kills & K/D", "Kills, deaths and K/D ratio this session (kills of players and mobs you hit).", 1, 70);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null) {
				return;
			}
			boolean dead = mc.player.isDeadOrDying();
			if (dead && !wasDead) {
				deaths++;
			}
			wasDead = dead;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			int kills = KillTracker.kills();
			String kd = deaths == 0 ? String.valueOf(kills) : String.format(Locale.ROOT, "%.2f", kills / (float) deaths);
			return one("Kills " + kills + "  Deaths " + deaths + "  K/D " + kd);
		}
	}

	public static final class ServerTps extends TextHud {
		private long lastWorldTime = -1;
		private long lastReal;
		private double tps = 20;

		public ServerTps() {
			super("tps", "Server TPS", "Roughly how fast the server is running (20 = perfect, lower = lag).", 1, 76);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.level == null) {
				lastWorldTime = -1;
				return;
			}
			long now = System.currentTimeMillis();
			long wt = mc.level.getGameTime();
			if (lastWorldTime < 0) {
				lastWorldTime = wt;
				lastReal = now;
				return;
			}
			if (now - lastReal >= 2000) {
				double measured = (wt - lastWorldTime) / ((now - lastReal) / 1000.0);
				if (measured >= 0 && measured < 40) {
					tps = tps * 0.5 + Math.min(20, measured) * 0.5;
				}
				lastWorldTime = wt;
				lastReal = now;
			}
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return one(String.format(Locale.ROOT, "TPS %.1f", tps));
		}
	}

	public static final class EntityCount extends TextHud {
		private int count;
		private int tick;

		public EntityCount() {
			super("entities", "Entity Count", "How many mobs, items and players your game has loaded.", 1, 82);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.level == null || ++tick % 20 != 0) {
				return;
			}
			int n = 0;
			for (Entity ignored : mc.level.entitiesForRendering()) {
				n++;
			}
			count = n;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return one("Entities: " + count);
		}
	}

	public static final class NearbyPlayers extends TextHud {
		private final Setting max = number("max", "Players to show", 1, 8, 1, 4);

		public NearbyPlayers() {
			super("nearby", "Nearby Players", "The closest players around you and how far away they are.", 99, 40);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null || mc.level == null) {
				return new String[]{"Steve  12m", "Alex  30m"};
			}
			List<AbstractClientPlayer> list = new ArrayList<>();
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p != mc.player) {
					list.add(p);
				}
			}
			list.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
			int n = Math.min(list.size(), Math.round(max.getFloat()));
			if (n == 0) {
				return one("No players nearby");
			}
			String[] out = new String[n];
			for (int i = 0; i < n; i++) {
				AbstractClientPlayer p = list.get(i);
				out[i] = p.getName().getString() + "  " + Math.round(Math.sqrt(p.distanceToSqr(mc.player))) + "m";
			}
			return out;
		}
	}

	public static final class DimensionDisplay extends TextHud {
		public DimensionDisplay() {
			super("dimension", "Dimension", "Which world you are in: Overworld, Nether or The End.", 99, 46);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.level == null) {
				return one("Overworld");
			}
			String p = mc.level.dimension().identifier().getPath();
			return one(switch (p) {
				case "overworld" -> "Overworld";
				case "the_nether" -> "The Nether";
				case "the_end" -> "The End";
				default -> p.replace('_', ' ');
			});
		}
	}

	public static final class InventorySpace extends TextHud {
		public InventorySpace() {
			super("invspace", "Inventory Space", "How many free slots you have left - never lose loot again.", 99, 52);
			isNew = true;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return one("Free slots: 12 / 36");
			}
			var inv = mc.player.getInventory();
			int free = 0;
			for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).isEmpty()) {
					free++;
				}
			}
			return one("Free slots: " + free + " / 36" + (free == 0 ? "  FULL" : ""));
		}
	}

	public static final class TotalPlaytime extends TextHud {
		private long lastTick;
		private long pending;

		public TotalPlaytime() {
			super("playtime", "Total Playtime", "All the time you have played with Clover, saved between sessions.", 99, 58);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			long now = System.currentTimeMillis();
			if (mc.player != null && lastTick > 0) {
				long d = now - lastTick;
				if (d > 0 && d < 5000) {
					pending += d;
				}
			}
			lastTick = now;
			if (pending >= 30000) {
				Config.set("playtime.ms", String.valueOf(saved() + pending));
				pending = 0;
			}
		}

		private static long saved() {
			try {
				return Long.parseLong(Config.get("playtime.ms", "0"));
			} catch (NumberFormatException e) {
				return 0;
			}
		}

		private long total() {
			return saved() + pending;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			long m = total() / 60000;
			return one("Played: " + (m / 60) + "h " + (m % 60) + "m");
		}
	}

	public static final class BreakReminder extends TextHud {
		private final Setting minutes = number("minutes", "Remind me every (minutes)", 15, 120, 5, 60);
		private long playedMs;
		private long lastTick;
		private long showUntil;

		public BreakReminder() {
			super("breakreminder", "Break Reminder", "Reminds you to take a short break and drink some water.", 50, 20);
			isNew = true;
		}

		@Override
		public void onTick(Minecraft mc) {
			long now = System.currentTimeMillis();
			if (mc.player != null && lastTick > 0 && now - lastTick < 5000) {
				playedMs += now - lastTick;
				if (playedMs >= minutes.getFloat() * 60000L) {
					playedMs = 0;
					showUntil = now + 10000;
				}
			}
			lastTick = now;
		}

		@Override
		public boolean available(Minecraft mc) {
			return super.available(mc) && System.currentTimeMillis() < showUntil;
		}

		@Override
		protected String[] lines(Minecraft mc) {
			return new String[]{"Time for a short break!", "Stretch, rest your eyes and drink some water."};
		}
	}

	public static final class AutoGG extends Module {
		private final Setting message = mode("message", "Message", 0, "gg", "GG", "gg wp", "good game");
		private long lastSent;

		public AutoGG() {
			super("autogg", "Auto GG", "Says gg in chat when a game ends (messages like \"won the game\" or \"Victory\").", Category.UTILITY);
			isNew = true;
			ClientReceiveMessageEvents.GAME.register((text, overlay) -> {
				if (!enabled() || overlay) {
					return;
				}
				String s = text.getString().toLowerCase(Locale.ROOT);
				boolean end = s.contains("won the game") || s.contains("victory!") || s.contains("winner:") || s.contains("1st killer")
						|| s.contains("game over") || s.contains("you won") || s.contains("has won");
				long now = System.currentTimeMillis();
				Minecraft mc = Minecraft.getInstance();
				if (end && now - lastSent > 10000 && mc.getConnection() != null) {
					lastSent = now;
					mc.getConnection().sendChat(message.modeName());
				}
			});
		}
	}

	public static final class NoHurtCam extends Module {
		public static NoHurtCam INSTANCE;

		public NoHurtCam() {
			super("nohurtcam", "No Hurt Shake", "Stops the screen from shaking when you get hit - keeps your aim steady.", Category.UTILITY);
			isNew = true;
			INSTANCE = this;
		}

		public static boolean active() {
			return INSTANCE != null && INSTANCE.enabled();
		}
	}
}
