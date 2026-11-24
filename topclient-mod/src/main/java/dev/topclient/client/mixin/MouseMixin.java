package dev.topclient.client.mixin;

import dev.topclient.client.event.EventManager;
import dev.topclient.client.event.events.ButtonListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Watches the mouse buttons once per frame so module keybinds can use mouse buttons too. */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
	@Shadow @Final private Minecraft minecraft;

	@Unique private boolean topclient$initialized;
	@Unique private final int[] topclient$buttonStates = new int[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];

	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void onMouseUpdate(CallbackInfo ci) {
		long window = minecraft.getWindow().handle();
		if (!topclient$initialized) {
			topclient$initialized = true;
			for (int button = 0; button < topclient$buttonStates.length; button++) {
				topclient$buttonStates[button] = GLFW.glfwGetMouseButton(window, button);
			}
			return;
		}
		for (int button = 0; button < topclient$buttonStates.length; button++) {
			int state = GLFW.glfwGetMouseButton(window, button);
			if (state != topclient$buttonStates[button]) {
				topclient$buttonStates[button] = state;
				EventManager.fire(new ButtonListener.ButtonEvent(button, window, state));
			}
		}
	}
}
