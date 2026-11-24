package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopExtras;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particle Changer: each kind of particle is kept or dropped on its own. */
@Mixin(ParticleEngine.class)
public class ParticlesMixin {
	/** Every particle made from a type (fireworks, crits, smoke, potions...). */
	@Inject(method = "makeParticle", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$byType(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		if (TopExtras.ParticleChanger.drop(TopExtras.ParticleChanger.kindOf(options.getType()))) {
			cir.setReturnValue(null);
		}
	}

	/** Block break and block hit particles are added directly while the game is breaking blocks. */
	@Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$blocks(Particle p, CallbackInfo ci) {
		int kind = TopExtras.ParticleChanger.context;
		if (kind >= 0 && TopExtras.ParticleChanger.drop(kind)) {
			ci.cancel();
		}
	}
}
