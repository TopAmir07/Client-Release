package dev.topclient.client.mixin;

import dev.topclient.client.event.EventManager;
import dev.topclient.client.event.events.ButtonListener;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void onPress(long window, int action, KeyEvent input, CallbackInfo ci) {
		EventManager.fire(new ButtonListener.ButtonEvent(input.key(), window, action));
	}
}
