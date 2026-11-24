package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: what the client world reports as rain/thunder (only on this PC). */
@Mixin(Level.class)
public class WeatherMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true, require = 0)
	private void topclient$rain(float partial, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && TopVisuals.WeatherChanger.on()) {
			cir.setReturnValue(TopVisuals.WeatherChanger.rain());
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true, require = 0)
	private void topclient$thunder(float partial, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && TopVisuals.WeatherChanger.on()) {
			cir.setReturnValue(TopVisuals.WeatherChanger.thunder());
		}
	}
}
