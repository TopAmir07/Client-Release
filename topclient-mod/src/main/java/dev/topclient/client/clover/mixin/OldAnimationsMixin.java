package dev.topclient.client.clover.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.topclient.client.clover.module.TopExtras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7 Animations: block-hit, sword block pose and no item dip. First-person hand only. */
@Mixin(ItemInHandRenderer.class)
public abstract class OldAnimationsMixin {
	@Shadow
	private float mainHandHeight;
	@Shadow
	private ItemStack mainHandItem;

	@Shadow
	protected abstract void applyItemArmAttackTransform(PoseStack pose, HumanoidArm arm, float attack);

	/** Swing while eating or drinking (the old "block-hit"). */
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyEatTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V"),
			require = 0)
	private void topclient$blockHit(ItemInHandRenderer self, PoseStack pose, float partialTick, HumanoidArm arm, ItemStack stack, Player player,
									Operation<Void> original) {
		original.call(self, pose, partialTick, arm, stack, player);
		if (TopExtras.OldAnimations.blockHit()) {
			float attack = player.getAttackAnim(partialTick);
			if (attack > 0F) {
				applyItemArmAttackTransform(pose, arm, attack);
			}
		}
	}

	/** Sword block pose while right click is held with a sword in the main hand. */
	@Inject(method = "applyItemArmTransform", at = @At("TAIL"), require = 0)
	private void topclient$swordBlock(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
		if (!TopExtras.OldAnimations.swordBlock()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || arm != p.getMainArm() || p.isUsingItem() || !mc.options.keyUse.isDown()
				|| !p.getMainHandItem().is(ItemTags.SWORDS)) {
			return;
		}
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		pose.translate(side * -0.14142136F, 0.08F, 0.14142136F);
		pose.mulPose(Axis.XP.rotationDegrees(-102.25F));
		pose.mulPose(Axis.YP.rotationDegrees(side * 13.365F));
		pose.mulPose(Axis.ZP.rotationDegrees(side * 78.05F));
	}

	/** No dip after each hit: the item stays up while it's the same item. */
	@Inject(method = "tick", at = @At("TAIL"), require = 0)
	private void topclient$noDip(CallbackInfo ci) {
		if (!TopExtras.OldAnimations.noDip()) {
			return;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null && ItemStack.matches(mainHandItem, p.getMainHandItem())) {
			mainHandHeight = 1F;
		}
	}
}
