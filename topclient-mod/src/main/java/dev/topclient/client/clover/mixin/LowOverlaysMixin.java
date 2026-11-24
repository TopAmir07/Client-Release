package dev.topclient.client.clover.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.topclient.client.clover.module.TopVisuals;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: the burning overlay is drawn lower on the screen. */
@Mixin(ScreenEffectRenderer.class)
public class LowOverlaysMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), require = 0)
	private static void topclient$lowFireStart(PoseStack pose, SubmitNodeCollector c, TextureAtlasSprite s, CallbackInfo ci) {
		pose.pushPose();
		float down = TopVisuals.LowOverlays.fire();
		if (down > 0) {
			pose.translate(0F, -down, 0F);
		}
	}

	@Inject(method = "submitFire", at = @At("RETURN"), require = 0)
	private static void topclient$lowFireEnd(PoseStack pose, SubmitNodeCollector c, TextureAtlasSprite s, CallbackInfo ci) {
		pose.popPose();
	}
}
