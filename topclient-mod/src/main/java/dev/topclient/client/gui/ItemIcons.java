package dev.topclient.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

public final class ItemIcons {
	private static final Map<String, Item> BY_NAME = new HashMap<>();
	private static final Map<Item, ItemStack> STACKS = new HashMap<>();

	static {
		Map<String, Item> m = BY_NAME;
		m.put("TopClient", Items.PAINTING);
		m.put("Friends", Items.POPPY);
		m.put("Aim Assist", Items.TARGET);
		m.put("Auto Clicker", Items.STONE_BUTTON);
		m.put("Auto Jump Reset", Items.SLIME_BALL);
		m.put("Auto WTap", Items.LEATHER_BOOTS);
		m.put("No Miss Delay", Items.IRON_SWORD);
		m.put("Shield Disabler", Items.NETHERITE_AXE);
		m.put("Trigger Bot", Items.DIAMOND_SWORD);
		m.put("Anchor Macro", Items.RESPAWN_ANCHOR);
		m.put("Auto Crystal", Items.END_CRYSTAL);
		m.put("Auto Hit Crystal", Items.OBSIDIAN);
		m.put("Crystal Optimizer", Items.GLOWSTONE_DUST);
		m.put("Double Anchor", Items.GLOWSTONE);
		m.put("Auto Double Hand", Items.TOTEM_OF_UNDYING);
		m.put("Auto Inventory Totem", Items.CHEST);
		m.put("Auto Pot", Items.SPLASH_POTION);
		m.put("Auto Pot Refill", Items.GLASS_BOTTLE);
		m.put("Auto XP", Items.EXPERIENCE_BOTTLE);
		m.put("Hover Totem", Items.TOTEM_OF_UNDYING);
		m.put("Totem Offhand", Items.SHIELD);
		m.put("No Jump Delay", Items.RABBIT_FOOT);
		m.put("Sprint", Items.SUGAR);
		m.put("Fake Lag", Items.CLOCK);
		m.put("Ping Spoof", Items.REPEATER);
		m.put("Freecam", Items.ENDER_EYE);
		m.put("HUD", Items.ITEM_FRAME);
		m.put("No Bounce", Items.SLIME_BLOCK);
		m.put("Player ESP", Items.SPYGLASS);
		m.put("Storage ESP", Items.ENDER_CHEST);
		m.put("Target HUD", Items.PLAYER_HEAD);
		m.put("Key Pearl", Items.ENDER_PEARL);
		m.put("No Break Delay", Items.NETHERITE_PICKAXE);
		m.put("Pack Spoof", Items.BUNDLE);
		m.put("Prevent", Items.BARRIER);
		m.put("Infinite Chat History", Items.WRITABLE_BOOK);
		m.put("Chat Timestamps", Items.CLOCK);
		m.put("Horse Stats", Items.SADDLE);
		m.put("Death Coordinates", Items.SKELETON_SKULL);
		m.put("Nether Coordinates", Items.OBSIDIAN);
		m.put("Low Health Warning", Items.REDSTONE);
		m.put("Pickup Notifier", Items.HOPPER);
		m.put("Session Stats", Items.WRITTEN_BOOK);
		m.put("Movement Status", Items.LEATHER_BOOTS);
		m.put("FPS Graph", Items.COMPARATOR);
		m.put("Analog Clock", Items.CLOCK);
		m.put("Day Cycle Bar", Items.SUNFLOWER);
		m.put("Reach Display", Items.STICK);
		m.put("Light Level", Items.TORCH);
		m.put("Y Level", Items.DIAMOND_ORE);
		m.put("Chunk Coordinates", Items.GRASS_BLOCK);
		m.put("Rotation", Items.ARROW);
		m.put("Held Item Info", Items.DIAMOND_SWORD);
		m.put("XP Info", Items.EXPERIENCE_BOTTLE);
		m.put("Health Numbers", Items.GOLDEN_APPLE);
		m.put("Date", Items.PAPER);
		m.put("Kills & K/D", Items.NETHERITE_SWORD);
		m.put("Server TPS", Items.REPEATER);
		m.put("Entity Count", Items.ZOMBIE_HEAD);
		m.put("Nearby Players", Items.PLAYER_HEAD);
		m.put("Dimension", Items.ENDER_EYE);
		m.put("Inventory Space", Items.BUNDLE);
		m.put("Total Playtime", Items.CLOCK);
		m.put("Break Reminder", Items.WATER_BUCKET);
		m.put("Auto GG", Items.FIREWORK_ROCKET);
		m.put("No Hurt Shake", Items.SHIELD);
		m.put("Freelook", Items.ENDER_PEARL);
		m.put("FPS", Items.REDSTONE_TORCH);
		m.put("CPS", Items.IRON_SWORD);
		m.put("Coordinates", Items.COMPASS);
		m.put("Direction", Items.RECOVERY_COMPASS);
		m.put("Speed", Items.SUGAR);
		m.put("Ping", Items.ECHO_SHARD);
		m.put("Clock", Items.CLOCK);
		m.put("Memory", Items.REPEATER);
		m.put("Day Counter", Items.DAYLIGHT_DETECTOR);
		m.put("Server", Items.FILLED_MAP);
		m.put("Item Counter", Items.CHEST);
		m.put("Potion Status", Items.POTION);
		m.put("Armor Status", Items.IRON_CHESTPLATE);
		m.put("Keystrokes", Items.TRIPWIRE_HOOK);
		m.put("Session Timer", Items.CLOCK);
		m.put("Saturation", Items.GOLDEN_CARROT);
		m.put("Player Count", Items.PLAYER_HEAD);
		m.put("Combo Counter", Items.DIAMOND_SWORD);
		m.put("Totem Counter", Items.TOTEM_OF_UNDYING);
		m.put("Arrow Counter", Items.ARROW);
		m.put("Biome", Items.OAK_SAPLING);
		m.put("Stopwatch", Items.CLOCK);
		m.put("Compass Bar", Items.COMPASS);
		m.put("World Time", Items.DAYLIGHT_DETECTOR);
		m.put("Weather", Items.WATER_BUCKET);
		m.put("Durability Alert", Items.ANVIL);
		m.put("Motion Blur", Items.ENDER_EYE);
		m.put("Flower Name Tag", Items.PINK_TULIP);
		m.put("Color Boost", Items.DYE.pink());
		m.put("Item Physics", Items.APPLE);
		m.put("Shulker Box Preview", Items.SHULKER_BOX);
		m.put("Spotify Lyrics", Items.NOTE_BLOCK);
		m.put("Spotify HUD", Items.MUSIC_DISC_CAT);
		m.put("Toggle Sprint", Items.LEATHER_BOOTS);
		m.put("Zoom", Items.SPYGLASS);
		m.put("Time Changer", Items.CLOCK);
		m.put("Crosshair Colour", Items.TARGET);
		m.put("Block Outline", Items.GLASS);
		m.put("Nametags", Items.NAME_TAG);
		m.put("Totem Particles", Items.TOTEM_OF_UNDYING);
		m.put("Clover Options", Items.LILY_PAD);
		m.put("Sounds", Items.NOTE_BLOCK);
		m.put("Theme", Items.PAINTING);
		m.put("Background", Items.ITEM_FRAME);
		m.put("Window", Items.GLASS_PANE);
		m.put("Cards", Items.MAP);
		m.put("Text", Items.WRITABLE_BOOK);
		m.put("Skin Walker", Items.ARMOR_STAND);
		m.put("Menu Look", Items.PAINTING);
		m.put("Icons", Items.ITEM_FRAME);
		m.put("Falling Particles", Items.SNOWBALL);
		m.put("Visuals", Items.PAINTING);
		m.put("Auto Safe Anchor", Items.GLOWSTONE);
		m.put("page:Combat", Items.DIAMOND_SWORD);
		m.put("page:Crystal", Items.END_CRYSTAL);
		m.put("page:Defense", Items.TOTEM_OF_UNDYING);
		m.put("page:Movement", Items.LEATHER_BOOTS);
		m.put("page:Utility", Items.CRAFTING_TABLE);
		m.put("page:Network", Items.REPEATER);
		m.put("page:Render", Items.SPYGLASS);
		m.put("page:HUD", Items.ITEM_FRAME);
		m.put("page:Visual", Items.GLOW_INK_SAC);
		m.put("page:Clover Utility", Items.LILY_PAD);
		m.put("page:Capes", Items.ELYTRA);
		m.put("page:Customize", Items.PAINTING);
		m.put("page:Client", Items.COMMAND_BLOCK);
	}

	private ItemIcons() {
	}

	public static Item forName(String name) {
		return BY_NAME.get(name);
	}

	public static boolean draw(GuiGraphicsExtractor ctx, Item item, float cx, float cy, float size) {
		if (item == null) {
			return false;
		}
		ItemStack stack = STACKS.computeIfAbsent(item, ItemStack::new);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(cx - size / 2F, cy - size / 2F);
		m.scale(size / 16F, size / 16F);
		ctx.item(stack, 0, 0);
		m.popMatrix();
		return true;
	}
}
