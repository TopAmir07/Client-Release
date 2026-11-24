package dev.topclient.client.clover.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.topclient.client.clover.module.UtilityModules;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: the wheel changes the zoom while zooming, and the mouse slows down a little while zoomed in. */
@Mixin(MouseHandler.class)
public class ZoomMouseMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (UtilityModules.Zoom.onScroll(yOffset)) {
			ci.cancel();
		}
	}

	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0), require = 0)
	private Object topclient$zoomSensitivity(Object sensitivity) {
		double k = UtilityModules.Zoom.sensitivity();
		return k != 1 && sensitivity instanceof Double d ? Double.valueOf(d * k) : sensitivity;
	}
}
