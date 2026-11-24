package dev.topclient.client.clover.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.topclient.client.clover.module.ItemPhysics;
import dev.topclient.client.clover.module.ItemPose;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics, done gently: vanilla still draws the item; we only change where it sits and how
 * it is turned. Every hook is optional (require = 0), so if Minecraft changes, items just look
 * vanilla again instead of the game crashing.
 *
 * 1. When the game prepares a dropped item, ItemPhysics.pose() works out its turn and tilt.
 * 2. Vanilla then moves the item up (with its bob) and turns it: we replace that one move with a
 *    height that puts the tilted item right on the ground, and that one turn with ours.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemPhysicsMixin {
	@Unique
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
	@Unique
	private static ItemEntityRenderState topclient$cur;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void topclient$pose(ItemEntity item, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
		try {
			((ItemPose) state).topclient$setPose(ItemPhysics.pose(item.getId(), state.ageInTicks, state.bobOffset,
					item.onGround(), item.isInWater() || item.isInLava(), ItemEntity.getSpin(state.ageInTicks, state.bobOffset)));
		} catch (RuntimeException e) {
			((ItemPose) state).topclient$setPose(null);
		}
	}

	@Inject(method = SUBMIT, at = @At("HEAD"), require = 0)
	private void topclient$begin(ItemEntityRenderState state, PoseStack m, net.minecraft.client.renderer.SubmitNodeCollector q,
			net.minecraft.client.renderer.state.level.CameraRenderState c, CallbackInfo ci) {
		topclient$cur = state;
	}

	@Unique
	private static float[] topclient$now() {
		ItemEntityRenderState s = topclient$cur;
		return s == null ? null : ((ItemPose) s).topclient$pose();
	}

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), require = 0)
	private void topclient$place(PoseStack m, float x, float y, float z) {
		float[] p = topclient$now();
		if (p == null) {
			m.translate(x, y, z);
			return;
		}
		AABB b = topclient$cur.item.getModelBoundingBox();
		double minY = b == null ? -0.125 : ItemPhysics.safe(b.minY, -0.125), maxZ = b == null ? 0.03 : ItemPhysics.safe(b.maxZ, 0.03);
		m.translate(x, ItemPhysics.lift(minY, maxZ, p[1]), z);
	}

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0), require = 0)
	private void topclient$turn(PoseStack m, Quaternionfc q) {
		float[] p = topclient$now();
		if (p == null) {
			m.mulPose(q);
			return;
		}
		m.mulPose(Axis.YP.rotation(p[0]));
		if (p[1] > 0F) {
			m.mulPose(Axis.XP.rotation((float) (p[1] * Math.PI / 2)));
		}
	}
}
