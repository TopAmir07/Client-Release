package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.ScoreboardHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Minecraft's own sidebar while the TopClient Scoreboard HUD draws it instead. */
@Mixin(Hud.class)
public class ScoreboardMixin {
	@Inject(method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("HEAD"), cancellable = true)
	private void topclient$scoreboard(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
		if (ScoreboardHud.active()) {
			ci.cancel();
		}
	}
}
