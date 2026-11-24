package dev.topclient.client.clover.mixin;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Instant quit: the moment you press Quit (or the window's X), the game window disappears.
 * Minecraft still saves everything and shuts down properly, it just does it out of sight,
 * so there is no frozen window for a second or two.
 */
@Mixin(Minecraft.class)
public class QuickExitMixin {
	@Inject(method = "stop", at = @At("HEAD"), require = 0)
	private void topclient$hideOnQuit(CallbackInfo ci) {
		try {
			Minecraft mc = (Minecraft) (Object) this;
			if (mc.getWindow() != null) {
				GLFW.glfwHideWindow(mc.getWindow().handle());
			}
		} catch (Throwable ignored) {
		}
	}
}
