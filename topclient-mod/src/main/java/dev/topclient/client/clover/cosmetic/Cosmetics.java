package dev.topclient.client.clover.cosmetic;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.ui.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class Cosmetics {
	public enum Slot {
		CAPE("Capes");

		public final String title;

		Slot(String title) {
			this.title = title;
		}
	}

	public static final String[][] CAPES = {
			{"anniversary", "Anniversary"}, {"aurora", "Aurora"}, {"cherry_blossom", "Cherry Blossom"}, {"cobalt", "Cobalt"},
			{"common", "Common"}, {"copper", "Copper"}, {"crafter", "Crafter"}, {"creeper", "Creeper"}, {"crown", "Crown"},
			{"minecon_enderman", "Enderman"}, {"green_event", "Green"}, {"hero", "Hero"}, {"home", "Home"},
			{"iron_golem", "Iron Golem"}, {"java_edition", "Java Edition"}, {"mc_experience", "MC Experience"},
			{"menace", "Menace"}, {"migrator", "Migrator"}, {"minecon_2011", "MineCon 2011"}, {"minecon_2013", "MineCon 2013"},
			{"minecraft_movie", "Minecraft Movie"}, {"mojang_office", "Mojang Office"}, {"moonlight", "Moonlight"},
			{"minecon_pickaxe", "Pickaxe"}, {"realms_mapmaker", "Realms Mapmaker"}, {"twitch", "Twitch"},
			{"vanilla", "Vanilla"}, {"warden", "Warden"},
			{"custom", "Your Own Cape"}};
	public static final Identifier WHITE = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "textures/cosmetics/white.png");

	private static final Map<Slot, String> TRY_ON = new HashMap<>();

	private Cosmetics() {
	}

	/** Petal Shop capes: only listed (and only worn) once bought in the launcher's Shop. */
	public static final String[][] SHOP_CAPES = {
			{"petal_bloom", "Petal Bloom"}, {"petal_sunset", "Top Sunset"}, {"petal_ocean", "Deep Ocean"},
			{"petal_neon", "Neon Grid"}, {"petal_nebula", "Nebula"}, {"petal_royal", "Royal Petal"},
			{"petal_pass_cape", "Kente Bloom"}};

	public static String[][] of(Slot slot) {
		java.util.List<String[]> out = new java.util.ArrayList<>(java.util.Arrays.asList(CAPES));
		for (String[] c : SHOP_CAPES) {
			if (owned(c[0])) {
				out.add(out.size() - 1, c); // before "Your Own Cape"
			}
		}
		for (String[] c : AnimCapes.LIST) { // animated Petal Shop capes, once bought
			if (owned(c[0])) {
				out.add(out.size() - 1, new String[]{c[0], c[1]});
			}
		}
		return out.toArray(new String[0][]);
	}

	/** Shop items (ids starting "petal_") need to be in cos.owned, which the launcher writes. */
	private static String ownedRaw = null;
	private static java.util.Set<String> ownedSet = java.util.Set.of();

	public static boolean owned(String id) {
		if (id == null || !id.startsWith("petal_")) {
			return true;
		}
		String raw = Config.get("cos.owned", "");
		if (!raw.equals(ownedRaw)) { // only re-read when the list changed, not every frame
			java.util.Set<String> set = new java.util.HashSet<>();
			for (String o : raw.split(",")) {
				if (!o.isBlank()) {
					set.add(o.trim());
				}
			}
			ownedSet = set;
			ownedRaw = raw;
		}
		return ownedSet.contains(id);
	}

	public static void register() {
	}

	private static int savedVer = -1;
	private static String[] savedVals;

	/** The equipped item for a slot. Cached per config version: this is asked for on every player render. */
	public static String saved(Slot slot) {
		if (savedVals == null || savedVer != Config.version()) {
			savedVals = new String[Slot.values().length];
			savedVer = Config.version();
		}
		String v = savedVals[slot.ordinal()];
		if (v == null) {
			String raw = Config.get("cos." + slot.name().toLowerCase(), "none");
			v = valid(slot, raw) ? raw : "none";
			savedVals[slot.ordinal()] = v;
		}
		return v;
	}

	public static void save(Slot slot, String key) {
		Config.set("cos." + slot.name().toLowerCase(), valid(slot, key) ? key : "none");
	}

	public static boolean valid(Slot slot, String key) {
		if ("none".equals(key)) {
			return true;
		}
		for (String[] c : of(slot)) {
			if (c[0].equals(key)) {
				return true;
			}
		}
		return false;
	}

	public static String label(Slot slot, String key) {
		for (String[] c : of(slot)) {
			if (c[0].equals(key)) {
				return c[1];
			}
		}
		return "None";
	}

	public static void tryOn(Slot slot, String key) {
		if (key == null) {
			TRY_ON.remove(slot);
		} else {
			TRY_ON.put(slot, key);
		}
	}

	public static String current(Slot slot) {
		String t = TRY_ON.get(slot);
		return t != null ? t : saved(slot);
	}

	public static boolean capeOnElytra() {
		return Config.getBool("cos.capeElytra", true);
	}

	public static void setCapeOnElytra(boolean v) {
		Config.setBool("cos.capeElytra", v);
	}

	public record CapeTex(Identifier id, int w, int h) {
	}

	public static String customCapePath() {
		return Config.get("cos.customCape", "");
	}

	public static void setCustomCapePath(String p) {
		Config.set("cos.customCape", p == null ? "" : p.trim());
	}

	public static Identifier capeTexture(String key) {
		CapeTex t = cape(key);
		return t == null ? null : t.id();
	}

	public static CapeTex cape(String key) {
		if (key == null || "none".equals(key)) {
			return null;
		}
		if (!owned(key)) {
			return null;
		}
		if (AnimCapes.is(key)) {
			Identifier f = AnimCapes.frame(key, true);
			return f == null ? null : new CapeTex(f, 512, 256);
		}
		if ("custom".equals(key)) {
			Textures.Tex t = Textures.load(customCapePath());
			return t == null ? null : new CapeTex(t.id(), t.w(), t.h());
		}
		return new CapeTex(Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "textures/cosmetics/capes/" + key + ".png"), 512, 256);
	}

	/** Menu tiles: animated capes only move while hovered or selected (saves memory). */
	public static CapeTex capeTile(String key, boolean animate) {
		if (AnimCapes.is(key) && owned(key)) {
			Identifier f = AnimCapes.frame(key, animate);
			return f == null ? null : new CapeTex(f, 512, 256);
		}
		return cape(key);
	}

	public static Identifier currentCape() {
		return capeTexture(current(Slot.CAPE));
	}

	public static Identifier capeFor(Avatar player) {
		return capeFor(player, null);
	}

	/** Your cape for you; for other players, the cape from their TopClient skin tag (if they have one). */
	public static Identifier capeFor(Avatar player, PlayerSkin skin) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		if (player == mc.player) {
			return currentCape();
		}
		String key = tagOf(skin).get("c");
		return key == null ? null : sharedCape(key);
	}

	/** Turns other TopClient players' cosmetics on/off (the TopClient menu, Cosmetics). */
	public static boolean showOthers() {
		return Config.getBool("cos.showOthers", true);
	}

	/** The cosmetics another TopClient player wears, read from the invisible tag in their skin. */
	public static java.util.Map<String, String> tagOf(PlayerSkin skin) {
		return showOthers() ? readTag(skin) : java.util.Map.of();
	}

	/** The TopClient tag in a skin, whatever the cosmetics switch says (the Petal uses it too). */
	public static java.util.Map<String, String> readTag(PlayerSkin skin) {
		if (skin == null || skin.body() == null) {
			return java.util.Map.of();
		}
		Identifier id = skin.body().texturePath();
		if (id == null || !id.getPath().startsWith("skins/")) { // only downloaded skins, never built-in ones
			return java.util.Map.of();
		}
		String key = id.toString();
		java.util.Map<String, String> hit = SkinTag.cached(key);
		if (hit != null) {
			return hit;
		}
		net.minecraft.client.renderer.texture.AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
		if (!(t instanceof net.minecraft.client.renderer.texture.DynamicTexture nt) || nt.getPixels() == null) {
			return java.util.Map.of();
		}
		com.mojang.blaze3d.platform.NativeImage img = nt.getPixels();
		return SkinTag.read(key, img.getWidth(), img.getHeight(), img::getPixel);
	}

	/** A cape another player picked: only capes that ship with the mod (never a custom picture). */
	private static Identifier sharedCape(String key) {
		if (AnimCapes.is(key)) {
			return AnimCapes.frame(key, true);
		}
		for (String[][] list : new String[][][]{CAPES, SHOP_CAPES}) {
			for (String[] c : list) {
				if (c[0].equals(key) && !"custom".equals(key)) {
					return Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "textures/cosmetics/capes/" + key + ".png");
				}
			}
		}
		return null;
	}

	public static PlayerSkin withCape(Avatar player, PlayerSkin old) {
		Identifier texture = old == null ? null : capeFor(player, old);
		if (texture == null) {
			return old;
		}
		boolean elytra = capeOnElytra();
		if (old.cape() != null && texture.equals(old.cape().texturePath()) && (!elytra || old.elytra() == old.cape())) {
			return old;
		}
		ClientAsset.Texture cape = new ClientAsset.ResourceTexture(texture, texture);
		return new PlayerSkin(old.body(), cape, elytra ? cape : old.elytra(), old.model(), old.secure());
	}
}
