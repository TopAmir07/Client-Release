package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopMore;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance: skips drawing entities far away (players can stay visible). */
@Mixin(EntityRenderDispatcher.class)
public class EntityCullMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private <E extends Entity> void topclient$cull(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (TopMore.Performance.cull(entity, x, y, z)) {
			cir.setReturnValue(false);
		}
	}
}
