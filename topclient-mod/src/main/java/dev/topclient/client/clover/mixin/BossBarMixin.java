package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopMore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Boss Bar mod: hide it, or draw it smaller (centred at the top). */
@Mixin(Hud.class)
public class BossBarMixin {
	@Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$bossStart(GuiGraphicsExtractor ctx, DeltaTracker t, CallbackInfo ci) {
		if (TopMore.BossBar.hidden()) {
			ci.cancel();
			return;
		}
		float s = TopMore.BossBar.scale();
		ctx.pose().pushMatrix();
		if (s < 1F) {
			float cx = ctx.guiWidth() / 2F;
			ctx.pose().translate(cx, 0);
			ctx.pose().scale(s, s);
			ctx.pose().translate(-cx, 0);
		}
	}

	@Inject(method = "extractBossOverlay", at = @At("RETURN"), require = 0)
	private void topclient$bossEnd(GuiGraphicsExtractor ctx, DeltaTracker t, CallbackInfo ci) {
		ctx.pose().popMatrix();
	}
}
