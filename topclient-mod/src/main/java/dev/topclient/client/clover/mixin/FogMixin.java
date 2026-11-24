package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopVisuals;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fog mod: only the render-distance fog moves; water/lava/blindness use the environmental values. */
@Mixin(FogRenderer.class)
public class FogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"), require = 0)
	private void topclient$fog(CallbackInfoReturnable<FogData> cir) {
		FogData d = cir.getReturnValue();
		if (d != null && TopVisuals.Fog.on()) {
			float[] r = TopVisuals.Fog.adjust(d.renderDistanceStart, d.renderDistanceEnd);
			d.renderDistanceStart = r[0];
			d.renderDistanceEnd = r[1];
		}
	}
}
