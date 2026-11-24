package dev.topclient.client.top;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.topclient.client.clover.Config;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints, kept per world (server address or singleplayer world name) in
 * config/topclient/waypoints.json. Nothing is shared or uploaded.
 */
public final class Waypoints {
	private Waypoints() {
	}

	public static final class Waypoint {
		public String name;
		public int x, y, z;
		public String dim;
		public int color;
		public boolean visible = true;

		public Waypoint() {
		}

		public Waypoint(String name, int x, int y, int z, String dim, int color) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dim = dim;
			this.color = color;
		}
	}

	public static final int[] COLORS = {0x7C3AED, 0x3B82F6, 0x22C55E, 0xEF4444, 0xF97316, 0xFACC15, 0xEC4899, 0x22D3EE, 0xFFFFFF};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Map<String, List<Waypoint>> all;
	private static final List<Waypoint> EMPTY = new ArrayList<>();

	private static Path file() {
		return Config.dir().resolve("waypoints.json");
	}

	private static Map<String, List<Waypoint>> load() {
		if (all == null) {
			all = new LinkedHashMap<>();
			try {
				if (Files.isRegularFile(file())) {
					Map<String, List<Waypoint>> m = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, List<Waypoint>>>() {
					}.getType());
					if (m != null) {
						all.putAll(m);
					}
				}
			} catch (Exception ignored) {
			}
		}
		return all;
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(load()));
		} catch (Exception ignored) {
		}
	}

	/** Which world we're in: the server address, or "sp:" + the singleplayer world's name. Null on the menu. */
	public static String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (mc.getCurrentServer() != null) {
			return mc.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
		}
		if (mc.getSingleplayerServer() != null) {
			return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "unknown";
	}

	public static String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	/** This world's waypoints (all dimensions). */
	public static List<Waypoint> list() {
		String k = worldKey();
		if (k == null) {
			return EMPTY;
		}
		return load().computeIfAbsent(k, x -> new ArrayList<>());
	}

	/** Adds a waypoint where the player stands. */
	public static Waypoint addHere(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		List<Waypoint> l = list();
		Waypoint w = new Waypoint(name == null || name.isBlank() ? "Waypoint " + (l.size() + 1) : name,
				mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dimension(), COLORS[l.size() % COLORS.length]);
		l.add(w);
		save();
		return w;
	}

	public static void remove(Waypoint w) {
		list().remove(w);
		save();
	}

	public static double distance(Waypoint w) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0;
		}
		double dx = w.x + 0.5 - mc.player.getX(), dy = w.y - mc.player.getY(), dz = w.z + 0.5 - mc.player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
