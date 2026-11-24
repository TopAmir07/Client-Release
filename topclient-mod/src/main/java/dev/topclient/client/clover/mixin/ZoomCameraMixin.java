package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.UtilityModules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: divides the camera's field of view while the zoom key is held. Camera only. */
@Mixin(Camera.class)
public class ZoomCameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void topclient$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		double d = UtilityModules.Zoom.divisor();
		if (d > 1.0001) {
			cir.setReturnValue((float) (cir.getReturnValueF() / d));
		}
	}
}
