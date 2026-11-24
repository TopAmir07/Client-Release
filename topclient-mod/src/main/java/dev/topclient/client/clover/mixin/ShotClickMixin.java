package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.ui.Screenshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles TopClient's own chat buttons (screenshot Copy / Rename) on this computer, never sent to a server. */
@Mixin(Screen.class)
public class ShotClickMixin {
	@Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true, require = 0)
	private static void topclient$click(ClickEvent ev, Minecraft client, Screen screen, CallbackInfo ci) {
		if (Screenshots.onClick(ev)) {
			ci.cancel();
		}
	}

	@Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true, require = 0)
	private static void topclient$basic(ClickEvent ev, Minecraft client, Screen screen, CallbackInfo ci) {
		if (Screenshots.onClick(ev)) {
			ci.cancel();
		}
	}
}
