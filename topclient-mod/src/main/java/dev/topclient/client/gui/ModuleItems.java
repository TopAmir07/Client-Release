package dev.topclient.client.gui;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A Minecraft item for every module and page, drawn as its icon in the menu (like the cards in
 * the picture: crosshair -> target block, armour -> chestplate...). Matched by words in the name.
 */
public final class ModuleItems {
	private static final Object[][] RULES = {
			{"nametag", Items.NAME_TAG}, {"name tag", Items.NAME_TAG},
			{"cape physics", Items.ELYTRA}, {"cape", Items.ELYTRA},
			{"crosshair", Items.TARGET}, {"keystroke", Items.STONE_BUTTON}, {"cps", Items.LEVER},
			{"armor", Items.DIAMOND_CHESTPLATE}, {"armour", Items.DIAMOND_CHESTPLATE},
			{"nether coord", Items.NETHERRACK}, {"death coord", Items.SKELETON_SKULL}, {"chunk", Items.GRASS_BLOCK},
			{"coordinate", Items.COMPASS}, {"y level", Items.LADDER}, {"compass", Items.COMPASS}, {"direction", Items.COMPASS},
			{"rotation", Items.RECOVERY_COMPASS}, {"fps graph", Items.COMPARATOR}, {"fps", Items.REDSTONE},
			{"tps", Items.REPEATER}, {"ping", Items.ENDER_EYE}, {"memory", Items.REPEATER},
			{"analog clock", Items.CLOCK}, {"world time", Items.CLOCK}, {"time changer", Items.DAYLIGHT_DETECTOR},
			{"day cycle", Items.DAYLIGHT_DETECTOR}, {"day counter", Items.SUNFLOWER}, {"clock", Items.CLOCK}, {"date", Items.PAPER},
			{"stopwatch", Items.CLOCK}, {"timer", Items.CLOCK}, {"playtime", Items.CLOCK}, {"break reminder", Items.BELL},
			{"biome", Items.OAK_SAPLING}, {"dimension", Items.OBSIDIAN}, {"weather", Items.WATER_BUCKET},
			{"light level", Items.TORCH}, {"speed", Items.SUGAR}, {"movement", Items.LEATHER_BOOTS}, {"sprint", Items.SUGAR},
			{"no bounce", Items.SLIME_BALL}, {"zoom", Items.SPYGLASS}, {"freelook", Items.ENDER_EYE},
			{"item physics", Items.APPLE}, {"pickup", Items.HOPPER}, {"item counter", Items.CHEST}, {"inventory", Items.CHEST},
			{"held item", Items.IRON_SWORD}, {"shulker", Items.SHULKER_BOX}, {"totem particle", Items.GLOWSTONE_DUST},
			{"totem", Items.TOTEM_OF_UNDYING}, {"potion", Items.POTION}, {"low health", Items.FERMENTED_SPIDER_EYE}, {"health", Items.GLISTERING_MELON_SLICE},
			{"saturation", Items.COOKED_BEEF}, {"xp", Items.EXPERIENCE_BOTTLE}, {"arrow", Items.ARROW}, {"durability", Items.ANVIL},
			{"target", Items.PLAYER_HEAD}, {"kill", Items.DIAMOND_SWORD}, {"combo", Items.BLAZE_POWDER}, {"reach", Items.STICK},
			{"session", Items.WRITABLE_BOOK}, {"auto gg", Items.OAK_SIGN}, {"chat", Items.WRITABLE_BOOK}, {"scoreboard", Items.ITEM_FRAME},
			{"player count", Items.PLAYER_HEAD}, {"nearby", Items.PLAYER_HEAD}, {"friends", Items.PLAYER_HEAD}, {"server", Items.COMMAND_BLOCK},
			{"entity", Items.SPAWNER}, {"motion blur", Items.FEATHER}, {"color boost", Items.PRISMARINE_CRYSTALS}, {"colour", Items.GLOW_INK_SAC},
			{"block outline", Items.GLASS}, {"slot highlight", Items.GLOW_ITEM_FRAME}, {"spotify lyrics", Items.JUKEBOX},
			{"spotify", Items.MUSIC_DISC_CAT}, {"horse", Items.SADDLE}, {"no hurt", Items.SHIELD}, {"hurt", Items.SHIELD},
			{"notification", Items.BELL}, {"watermark", Items.PAPER}, {"theme", Items.PAINTING}, {"menu", Items.PAINTING},
			{"customize", Items.PAINTING}, {"sound", Items.NOTE_BLOCK}, {"profile", Items.NAME_TAG}, {"key", Items.TRIPWIRE_HOOK},
			{"hud", Items.MAP}, {"visual", Items.ENDER_EYE}, {"utility", Items.IRON_PICKAXE}, {"topclient", Items.BOOK},
			{"client", Items.BOOK}, {"all modules", Items.NETHER_STAR}, {"favorites", Items.POPPY}, {"enabled", Items.EMERALD},
			{"settings", Items.COMPARATOR}, {"clover", Items.LILY_PAD}};
	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private ModuleItems() {
	}

	public static ItemStack of(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		ItemStack have = CACHE.get(n);
		if (have != null) {
			return have;
		}
		ItemStack s = new ItemStack(Items.BOOK);
		for (Object[] r : RULES) {
			if (n.contains((String) r[0])) {
				s = new ItemStack((Item) r[1]);
				break;
			}
		}
		CACHE.put(n, s);
		return s;
	}
}
