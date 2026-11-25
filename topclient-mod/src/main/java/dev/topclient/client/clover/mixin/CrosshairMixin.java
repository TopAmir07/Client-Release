package dev.topclient.client.clover.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.topclient.client.clover.module.UtilityModules;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class CrosshairMixin {
	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void topclient$crosshair(GuiGraphicsExtractor ctx, RenderPipeline pipeline, Identifier texture,
										int x, int y, int w, int h, Operation<Void> original) {
		if (UtilityModules.CrosshairColor.custom()) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.player != null && mc.player.isSpectator() && !UtilityModules.CrosshairColor.spectatorVisible()) {
				original.call(ctx, pipeline, texture, x, y, w, h);
				return;
			}
			if (UtilityModules.CrosshairColor.vanillaBlending()) {
				original.call(ctx, pipeline, texture, x, y, w, h);
			}
			UtilityModules.CrosshairColor.draw(ctx, x + w / 2F, y + h / 2F);
		} else if (UtilityModules.CrosshairColor.active()) {
			ctx.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, w, h, UtilityModules.CrosshairColor.argb());
		} else {
			original.call(ctx, pipeline, texture, x, y, w, h);
		}
	}
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void topclient$thirdPersonCrosshair(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (UtilityModules.CrosshairColor.custom()
				&& UtilityModules.CrosshairColor.thirdPersonEnabled()
				&& mc.player != null
				&& !mc.options.getCameraType().isFirstPerson()) {
			if (mc.player.isSpectator() && !UtilityModules.CrosshairColor.spectatorVisible()) return;
			int cx = mc.getWindow().getGuiScaledWidth() / 2;
			int cy = mc.getWindow().getGuiScaledHeight() / 2;
			UtilityModules.CrosshairColor.draw(ctx, cx, cy);
		}
	}

}
