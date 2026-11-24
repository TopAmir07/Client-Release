package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.Nametags;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AvatarRenderer.class, priority = 2000)
public class NametagTextMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void topclient$nametag(Avatar player, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
		try {
			if (state.nameTag != null) {
				state.nameTag = Nametags.decorate(player, state.nameTag);
			}
		} catch (RuntimeException e) {
		}
	}
}
