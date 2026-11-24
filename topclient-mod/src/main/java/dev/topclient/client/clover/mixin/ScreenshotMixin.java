package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.ui.Screenshots;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Swaps Minecraft's "Saved screenshot as <long path>" for a tidy pop-up and chat line. */
@Mixin(Screenshot.class)
public class ScreenshotMixin {
	@ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private static Consumer<Component> topclient$tidy(Consumer<Component> original) {
		return Screenshots.wrap(original);
	}
}
