package dev.topclient.client.mixin;

import dev.topclient.client.event.EventManager;
import dev.topclient.client.event.events.HudListener;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class InGameHudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void onRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		EventManager.fire(new HudListener.HudEvent(context, tickCounter.getGameTimeDeltaPartialTick(true)));
	}
}
