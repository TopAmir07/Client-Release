package dev.topclient.client.clover.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.topclient.client.clover.module.TopVisuals;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low shield and Hand Position: move the items held in first person. */
@Mixin(ItemInHandRenderer.class)
public class LowShieldMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
	private void topclient$shieldStart(AbstractClientPlayer p, float a, float b, InteractionHand hand, float c, ItemStack stack, float d,
									   PoseStack pose, SubmitNodeCollector col, int light, CallbackInfo ci) {
		pose.pushPose();
		if (dev.topclient.client.clover.module.TopMore.HandPosition.on()) {
			float[] v = dev.topclient.client.clover.module.TopMore.HandPosition.values();
			float side = hand == InteractionHand.MAIN_HAND ? 1F : -1F;
			pose.translate(v[0] * side, v[1], -v[2]);
			pose.scale(v[3], v[3], v[3]);
		}
		float down = TopVisuals.LowOverlays.shield();
		if (down > 0 && stack.is(Items.SHIELD)) {
			pose.translate(0F, -down, 0F);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
	private void topclient$shieldEnd(AbstractClientPlayer p, float a, float b, InteractionHand hand, float c, ItemStack stack, float d,
									 PoseStack pose, SubmitNodeCollector col, int light, CallbackInfo ci) {
		pose.popPose();
	}
}
