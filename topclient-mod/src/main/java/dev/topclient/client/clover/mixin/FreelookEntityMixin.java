package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class FreelookEntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$freelook(double dx, double dy, CallbackInfo ci) {
		if (Freelook.active() && (Object) this == Minecraft.getInstance().player) {
			Freelook.turn(dx, dy);
			ci.cancel();
		}
	}
}
