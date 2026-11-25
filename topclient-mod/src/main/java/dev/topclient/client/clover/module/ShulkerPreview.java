package dev.topclient.client.clover.module;

import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ShulkerPreview extends Module {
	private static ShulkerPreview instance;
	private final Setting onlyFilled = bool("onlyFilled", "Only when it has items", true);

	public ShulkerPreview() {
		super("shulker", "Shulker Preview", "Preview the contents of a shulker box when hovering over it.", Category.VISUAL);
		isNew = true;
		instance = this;
	}

	@Override
	protected boolean defaultEnabled() {
		return false; // everything starts off: switch it on with Right Shift
	}

	public static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int mouseX, int mouseY) {
		ShulkerPreview p = instance;
		if (p == null || !p.enabled() || stack == null || stack.isEmpty()) {
			return;
		}
		ItemContainerContents c = stack.get(DataComponents.CONTAINER);
		if (c == null) {
			return;
		}
		List<ItemStack> items = new ArrayList<>(c.allItemsCopyStream().toList());
		boolean any = items.stream().anyMatch(s -> !s.isEmpty());
		if (!any && p.onlyFilled.getBool()) {
			return;
		}
		int slots = Math.max(27, (items.size() + 8) / 9 * 9);
		int rows = slots / 9;
		int w = 9 * 18 + 8, h = rows * 18 + 8;
		int sw = ctx.guiWidth(), sh = ctx.guiHeight();
		int x = Math.max(2, Math.min(sw - w - 2, mouseX + 10));
		int y = mouseY - h - 14;
		if (y < 2) {
			y = Math.min(sh - h - 2, mouseY + 24);
		}
		Gfx.rect(ctx, x - 1, y - 1, w + 2, h + 2, 4, Theme.accentArgb());
		Gfx.rect(ctx, x, y, w, h, 3, 0xF0101010);
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < slots; i++) {
			int sx = x + 4 + (i % 9) * 18, sy = y + 4 + (i / 9) * 18;
			ctx.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF);
			if (i < items.size()) {
				ItemStack s = items.get(i);
				if (!s.isEmpty()) {
					ctx.item(s, sx, sy);
					ctx.itemDecorations(mc.font, s, sx, sy);
				}
			}
		}
	}
}
