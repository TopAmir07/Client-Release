package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopVisuals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the carved-pumpkin blur when Low Fire & Shield says so. */
@Mixin(Hud.class)
public class PumpkinOverlayMixin {
	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$pumpkin(GuiGraphicsExtractor ctx, Identifier texture, float alpha, CallbackInfo ci) {
		if (TopVisuals.LowOverlays.noPumpkin() && texture.getPath().contains("pumpkin")) {
			ci.cancel();
		}
	}
}
