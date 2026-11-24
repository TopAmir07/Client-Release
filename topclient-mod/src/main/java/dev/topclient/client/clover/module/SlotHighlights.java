package dev.topclient.client.clover.module;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Slot Highlights: colours the slots in your inventory and chests by what is in them
 * (totems, enchanted gear, weapons, tools, armour, food, potions, low durability ...).
 * Only draws behind/around the items on your own screen.
 */
public final class SlotHighlights extends Module {
	private static SlotHighlights instance;

	private final Setting style = mode("style", "Style", 0, "Fill", "Border", "Glow corners", "Fill + border");
	private final Setting opacity = number("opacity", "Strength", 10, 100, 5, 45);
	private final Setting rarity = bool("rarity", "Colour by rarity first", false);

	private final Setting hTypes = header("What to highlight");
	private final Setting totems = bool("totems", "Totems", true);
	private final Setting totemCol = color("totemCol", "Totem colour", 0xFFD23F);
	private final Setting mending = bool("mending", "Mending gear", true);
	private final Setting mendingCol = color("mendingCol", "Mending colour", 0x3EE3C8);
	private final Setting enchanted = bool("enchanted", "Other enchanted items", true);
	private final Setting enchantedCol = color("enchantedCol", "Enchanted colour", 0xB15CFF);
	private final Setting weapons = bool("weapons", "Weapons", true);
	private final Setting weaponCol = color("weaponCol", "Weapon colour", 0xFF4F5E);
	private final Setting tools = bool("tools", "Tools", false);
	private final Setting toolCol = color("toolCol", "Tool colour", 0x4FA3FF);
	private final Setting armor = bool("armor", "Armour", false);
	private final Setting armorCol = color("armorCol", "Armour colour", 0x5BE36A);
	private final Setting food = bool("food", "Food", false);
	private final Setting foodCol = color("foodCol", "Food colour", 0xFF9A3C);
	private final Setting potions = bool("potions", "Potions", true);
	private final Setting potionCol = color("potionCol", "Potion colour", 0xFF6AD5);

	private final Setting hDur = header("Durability");
	private final Setting lowDur = bool("lowDur", "Warn when almost broken", true);
	private final Setting lowDurPct = number("lowDurPct", "Warn below (%)", 5, 50, 5, 15);
	private final Setting lowDurCol = color("lowDurCol", "Warning colour", 0xFF2A2A);

	public SlotHighlights() {
		super("slothighlights", "Slot Highlights", "Colours inventory and chest slots by what is in them: totems, enchanted gear, weapons, potions and more. Pick which items and colours.", Category.VISUAL);
		isNew = true;
		instance = this;
	}

	@Override
	protected boolean defaultEnabled() {
		return false;
	}

	/** Called for every slot before its item is drawn. */
	public static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
		SlotHighlights h = instance;
		if (h == null || !h.enabled() || stack == null || stack.isEmpty()) {
			return;
		}
		int rgb = h.colourFor(stack);
		if (rgb == -1) {
			return;
		}
		int a = Math.round(h.opacity.getFloat() / 100F * 255F);
		int fill = (Math.min(255, a) << 24) | (rgb & 0xFFFFFF);
		int line = (Math.min(255, a + 90) << 24) | (rgb & 0xFFFFFF);
		switch (h.style.getMode()) {
			case 1 -> border(ctx, x, y, line);
			case 2 -> corners(ctx, x, y, line);
			case 3 -> {
				ctx.fill(x, y, x + 16, y + 16, fill);
				border(ctx, x, y, line);
			}
			default -> ctx.fill(x, y, x + 16, y + 16, fill);
		}
	}

	private static void border(GuiGraphicsExtractor ctx, int x, int y, int c) {
		ctx.fill(x, y, x + 16, y + 1, c);
		ctx.fill(x, y + 15, x + 16, y + 16, c);
		ctx.fill(x, y + 1, x + 1, y + 15, c);
		ctx.fill(x + 15, y + 1, x + 16, y + 15, c);
	}

	private static void corners(GuiGraphicsExtractor ctx, int x, int y, int c) {
		int l = 5;
		ctx.fill(x, y, x + l, y + 1, c);
		ctx.fill(x, y, x + 1, y + l, c);
		ctx.fill(x + 16 - l, y, x + 16, y + 1, c);
		ctx.fill(x + 15, y, x + 16, y + l, c);
		ctx.fill(x, y + 15, x + l, y + 16, c);
		ctx.fill(x, y + 16 - l, x + 1, y + 16, c);
		ctx.fill(x + 16 - l, y + 15, x + 16, y + 16, c);
		ctx.fill(x + 15, y + 16 - l, x + 16, y + 16, c);
	}

	/** The colour for this item, or -1 for no highlight. The first rule that matches wins. */
	private int colourFor(ItemStack s) {
		if (lowDur.getBool() && s.isDamageableItem() && s.getMaxDamage() > 0) {
			float left = 1F - (float) s.getDamageValue() / s.getMaxDamage();
			if (left * 100F < lowDurPct.getFloat()) {
				return lowDurCol.getColor();
			}
		}
		if (rarity.getBool()) {
			Rarity r = s.getRarity();
			if (r == Rarity.EPIC) {
				return 0xFF55FF;
			}
			if (r == Rarity.RARE) {
				return 0x55FFFF;
			}
			if (r == Rarity.UNCOMMON) {
				return 0xFFFF55;
			}
		}
		if (totems.getBool() && s.has(DataComponents.DEATH_PROTECTION)) {
			return totemCol.getColor();
		}
		ItemEnchantments ench = s.getEnchantments();
		if (mending.getBool() && has(ench, Enchantments.MENDING)) {
			return mendingCol.getColor();
		}
		if (enchanted.getBool() && (s.isEnchanted() || s.has(DataComponents.STORED_ENCHANTMENTS))) {
			return enchantedCol.getColor();
		}
		if (potions.getBool() && s.has(DataComponents.POTION_CONTENTS)) {
			return potionCol.getColor();
		}
		if (weapons.getBool() && (s.has(DataComponents.WEAPON) || s.is(ItemTags.SWORDS) || s.is(ItemTags.SPEARS))) {
			return weaponCol.getColor();
		}
		if (tools.getBool() && s.has(DataComponents.TOOL)) {
			return toolCol.getColor();
		}
		if (armor.getBool() && s.has(DataComponents.EQUIPPABLE) && s.isDamageableItem()) {
			return armorCol.getColor();
		}
		if (food.getBool() && s.has(DataComponents.FOOD)) {
			return foodCol.getColor();
		}
		return -1;
	}

	private static boolean has(ItemEnchantments e, net.minecraft.resources.ResourceKey<Enchantment> key) {
		for (Holder<Enchantment> h : e.keySet()) {
			if (h.is(key)) {
				return true;
			}
		}
		return false;
	}
}
