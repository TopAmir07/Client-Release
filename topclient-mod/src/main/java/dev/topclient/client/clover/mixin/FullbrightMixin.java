package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopExtras;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: lights up the lightmap the same way Night Vision does. Screen only. */
@Mixin(LightmapRenderStateExtractor.class)
public class FullbrightMixin {
	@Inject(method = "extract", at = @At("TAIL"), require = 0)
	private void topclient$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		float a = TopExtras.Fullbright.amount();
		if (a > 0F && state.nightVisionEffectIntensity < a) {
			state.nightVisionEffectIntensity = a;
		}
	}
}
