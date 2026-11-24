package dev.topclient.client.visuals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.topclient.client.clover.Config;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class VisualsData {

	public record Style(String id, String category, String name, String by, String sha256, long size, LocalDate added, String preview) {
		public boolean isNew() {
			return added != null && newest != null && !added.isBefore(newest.minusDays(14));
		}
	}

	public record Category(String id, String name, Item icon, String note) {
	}

	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,80}");
	private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

	private static final Category[] KNOWN = {
			new Category("crystal", "Crystals", Items.END_CRYSTAL, null),
			new Category("totem", "Totems", Items.TOTEM_OF_UNDYING, null),
			new Category("pearl", "Pearls", Items.ENDER_PEARL, null),
			new Category("anchor", "Anchors", Items.RESPAWN_ANCHOR, null),
			new Category("glowstone", "Glowstone", Items.GLOWSTONE, null),
			new Category("obsidian", "Obsidian", Items.OBSIDIAN, null),
			new Category("sword", "Swords", Items.DIAMOND_SWORD, null),
			new Category("axe", "Axes", Items.DIAMOND_AXE, null),
			new Category("mace", "Maces", Items.MACE, null),
			new Category("pickaxe", "Pickaxes", Items.DIAMOND_PICKAXE, null),
			new Category("spear", "Spears", Items.TRIDENT, null),
			new Category("trident", "Tridents", Items.TRIDENT, null),
			new Category("shield", "Shields", Items.SHIELD, null),
			new Category("armor", "Armor", Items.NETHERITE_CHESTPLATE, null),
			new Category("gap", "Golden Apples", Items.GOLDEN_APPLE, null),
			new Category("xpbottle", "XP Bottles", Items.EXPERIENCE_BOTTLE, null),
			new Category("cobweb", "Cobwebs", Items.COBWEB, null),
			new Category("fire", "Fire", Items.FLINT_AND_STEEL, null),
			new Category("rails", "Rails", Items.RAIL, null),
			new Category("tntcart", "TNT Minecart", Items.TNT_MINECART, null),
			new Category("hotbar", "Hotbar", Items.ITEM_FRAME, null),
			new Category("inventory", "Inventory", Items.CHEST, null),
			new Category("deepslate", "Deepslate", Items.DEEPSLATE, null),
			new Category("stone", "Stone", Items.STONE, null),
			new Category("sky", "Sky", Items.SUNFLOWER, "Needs a sky mod that reads OptiFine skies (for example FabricSkyBoxes)"),
	};

	private static List<Style> styles = List.of();
	private static List<Category> categories = List.of();
	private static Map<String, Style> byId = Map.of();
	private static String problem;
	private static long loadedStamp = -1;
	private static LocalDate newest;
	private static long lastCheck;

	private VisualsData() {
	}

	public static Path folder() {
		return Config.dir().resolve("visuals");
	}

	public static Path packFile(Style s) {
		return folder().resolve("packs").resolve(s.id() + ".zip");
	}

	public static Path previewFile(Style s) {
		return folder().resolve("previews").resolve(s.id() + ".png");
	}

	public static boolean installed() {
		reloadIfChanged();
		return !styles.isEmpty();
	}

	public static String problem() {
		return problem;
	}

	public static List<Style> styles() {
		reloadIfChanged();
		return styles;
	}

	public static List<Category> categories() {
		reloadIfChanged();
		return categories;
	}

	public static Style style(String id) {
		reloadIfChanged();
		return byId.get(id);
	}

	public static List<Style> in(String category) {
		List<Style> out = new ArrayList<>();
		for (Style s : styles()) {
			if (s.category().equals(category)) {
				out.add(s);
			}
		}
		return out;
	}

	public static Category category(String id) {
		for (Category c : categories()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	public static void forceReload() {
		lastCheck = 0;
		loadedStamp = -1;
		reloadIfChanged();
	}

	public static void reloadIfChanged() {
		long now = System.currentTimeMillis();
		if (now - lastCheck < 1500) {
			return;
		}
		lastCheck = now;
		Path index = folder().resolve("index.json");
		long stamp;
		try {
			stamp = Files.isRegularFile(index) ? Files.getLastModifiedTime(index).toMillis() ^ Files.size(index) : 0;
		} catch (Exception e) {
			stamp = 0;
		}
		if (stamp == loadedStamp) {
			return;
		}
		loadedStamp = stamp;
		load(index);
	}

	private static void load(Path index) {
		styles = List.of();
		categories = List.of();
		byId = Map.of();
		newest = null;
		problem = null;
		if (!Files.isRegularFile(index)) {
			problem = "not-installed";
			return;
		}
		try {
			String json = Files.readString(index, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			JsonArray arr = root.has("styles") ? root.getAsJsonArray("styles") : new JsonArray();
			List<Style> out = new ArrayList<>();
			Map<String, Style> ids = new LinkedHashMap<>();
			for (JsonElement e : arr) {
				try {
					JsonObject o = e.getAsJsonObject();
					String id = o.get("id").getAsString();
					String cat = o.get("category").getAsString();
					String sha = o.has("sha256") ? o.get("sha256").getAsString().toLowerCase(Locale.ROOT) : "";
					if (!ID.matcher(id).matches() || !ID.matcher(cat).matches() || !SHA.matcher(sha).matches()) {
						continue;
					}
					String name = o.has("name") ? o.get("name").getAsString() : id;
					if (name.length() > 48) {
						name = name.substring(0, 48);
					}
					String by = o.has("by") && !o.get("by").isJsonNull() ? o.get("by").getAsString() : null;
					long size = o.has("size") ? o.get("size").getAsLong() : 0;
					LocalDate added = null;
					try {
						added = o.has("added") ? LocalDate.parse(o.get("added").getAsString()) : null;
					} catch (Exception ignored) {
					}
					Style s = new Style(id, cat, name, by, sha, size, added, null);
					if (added != null && (newest == null || added.isAfter(newest))) {
						newest = added;
					}
					if (!ids.containsKey(id)) {
						ids.put(id, s);
						out.add(s);
					}
				} catch (RuntimeException ignored) {
				}
			}
			out.sort((a, b) -> {
				if (a.added() != null && b.added() != null && !a.added().equals(b.added())) {
					return b.added().compareTo(a.added());
				}
				return a.name().compareToIgnoreCase(b.name());
			});
			List<Category> cats = new ArrayList<>();
			for (Category c : KNOWN) {
				for (Style s : out) {
					if (s.category().equals(c.id())) {
						cats.add(c);
						break;
					}
				}
			}
			for (Style s : out) {
				boolean known = false;
				for (Category c : cats) {
					if (c.id().equals(s.category())) {
						known = true;
						break;
					}
				}
				if (!known) {
					String nice = s.category().substring(0, 1).toUpperCase(Locale.ROOT) + s.category().substring(1);
					cats.add(new Category(s.category(), nice, Items.PAPER, null));
				}
			}
			styles = Collections.unmodifiableList(out);
			categories = Collections.unmodifiableList(cats);
			byId = Collections.unmodifiableMap(ids);
			if (out.isEmpty()) {
				problem = "empty";
			}
		} catch (Exception e) {
			problem = "broken";
		}
	}
}
