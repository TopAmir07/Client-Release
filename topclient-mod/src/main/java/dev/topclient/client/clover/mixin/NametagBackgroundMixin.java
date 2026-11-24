package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.Nametags;
import net.minecraft.client.renderer.state.OptionsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Name tag background box. In 26.2 name tags (and text displays, same as 1.21.11) read the opacity from
 * OptionsRenderState.getBackgroundOpacity; Options.getBackgroundOpacity now only feeds chat/HUD boxes,
 * so hooking that (the old target) did nothing for name tags and changed the chat background instead.
 */
@Mixin(OptionsRenderState.class)
public class NametagBackgroundMixin {
	@Inject(method = "getBackgroundOpacity(F)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void topclient$nametagBackground(float fallback, CallbackInfoReturnable<Float> cir) {
		float o = Nametags.backgroundOpacity();
		if (o >= 0F) {
			cir.setReturnValue(o);
		}
	}
}
