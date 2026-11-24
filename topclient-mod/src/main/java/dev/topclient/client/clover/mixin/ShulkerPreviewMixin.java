package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.ShulkerPreview;
import dev.topclient.client.clover.module.SlotHighlights;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ShulkerPreviewMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractTooltip", at = @At("TAIL"), require = 0)
	private void topclient$shulker(GuiGraphicsExtractor ctx, int mouseX, int mouseY, CallbackInfo ci) {
		Slot s = this.hoveredSlot;
		if (s != null && s.hasItem()) {
			ShulkerPreview.draw(ctx, s.getItem(), mouseX, mouseY);
		}
	}

	@Inject(method = "extractSlot", at = @At("HEAD"), require = 0)
	private void topclient$highlight(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (slot.hasItem()) {
			SlotHighlights.draw(ctx, slot.getItem(), slot.x, slot.y);
		}
	}
}
