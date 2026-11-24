package dev.topclient.client.clover.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.topclient.client.clover.module.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class FreelookCameraMixin {
	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), require = 0)
	private void topclient$rotation(Camera self, float yaw, float pitch, Operation<Void> original) {
		if (Freelook.active()) {
			original.call(self, Freelook.yaw(), Freelook.pitch());
		} else {
			original.call(self, yaw, pitch);
		}
	}
}
