package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopExtras;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks which block particles are being made, so the Particle Changer can tell break from hit. */
@Mixin(ClientLevel.class)
public class BlockParticlesMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), require = 0)
	private void topclient$breakStart(BlockPos pos, BlockState state, CallbackInfo ci) {
		TopExtras.ParticleChanger.context = TopExtras.ParticleChanger.BLOCK_BREAK;
	}

	@Inject(method = "addDestroyBlockEffect", at = @At("RETURN"), require = 0)
	private void topclient$breakEnd(BlockPos pos, BlockState state, CallbackInfo ci) {
		TopExtras.ParticleChanger.context = -1;
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), require = 0)
	private void topclient$hitStart(BlockPos pos, Direction side, CallbackInfo ci) {
		TopExtras.ParticleChanger.context = TopExtras.ParticleChanger.BLOCK_HIT;
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("RETURN"), require = 0)
	private void topclient$hitEnd(BlockPos pos, Direction side, CallbackInfo ci) {
		TopExtras.ParticleChanger.context = -1;
	}
}
