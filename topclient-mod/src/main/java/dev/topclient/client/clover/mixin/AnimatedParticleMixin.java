package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TotemLook;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SimpleAnimatedParticle.class, priority = 2000)
public class AnimatedParticleMixin {
	@Inject(method = "tick()V", at = @At("TAIL"), require = 0)
	private void topclient$totemTick(CallbackInfo ci) {
		if ((Object) this instanceof TotemLook look) {
			try {
				look.topclient$apply();
			} catch (RuntimeException ignored) {
			}
		}
	}

	@Inject(method = "getLightCoords(F)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$totemGlow(float tint, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof TotemLook look && look.topclient$glows()) {
			cir.setReturnValue(0xF000F0);
		}
	}
}
