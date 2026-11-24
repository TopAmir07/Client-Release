package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.UtilityModules;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time Changer: only changes the time YOUR game draws (sky, light). Since 26.x the sky follows
 * the world clocks, so we keep the day count and swap just the time of day. Nothing is sent to the server.
 */
@Mixin(ClientClockManager.class)
public class TimeChangerMixin {
	@Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true, require = 0)
	private void topclient$time(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
		int time = UtilityModules.TimeChanger.override();
		if (time >= 0) {
			long total = cir.getReturnValueJ();
			cir.setReturnValue(total - Math.floorMod(total, 24000L) + time);
		}
	}
}
