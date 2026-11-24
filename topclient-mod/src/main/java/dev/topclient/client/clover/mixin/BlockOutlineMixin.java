package dev.topclient.client.clover.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.topclient.client.clover.module.UtilityModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class BlockOutlineMixin {
	private static final float HIGH_CONTRAST_WIDTH = 7.0F;

	@WrapOperation(method = "submitBlockOutline", require = 0,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V"))
	private void topclient$outline(LevelRenderer self, PoseStack matrices, SubmitNodeCollector queue, RenderType type,
									  BlockOutlineRenderState state, int color, float width, boolean translucent,
									  Operation<Void> original) {
		if (!UtilityModules.BlockOutlineMod.active()) {
			original.call(self, matrices, queue, type, state, color, width, translucent);
		} else if (width != HIGH_CONTRAST_WIDTH) {
			original.call(self, matrices, queue, type, state, UtilityModules.BlockOutlineMod.argb(),
					UtilityModules.BlockOutlineMod.width(Minecraft.getInstance()), translucent);
		}
	}
}
