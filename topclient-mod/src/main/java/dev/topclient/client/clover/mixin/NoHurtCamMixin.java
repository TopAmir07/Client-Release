package dev.topclient.client.clover.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.topclient.client.clover.module.CloverModules16;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class NoHurtCamMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$noHurtCam(CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
		if (CloverModules16.NoHurtCam.active()) {
			ci.cancel();
		}
	}
}
