package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TopMore;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit Counter: counts your own attacks (only reads them, never makes any). */
@Mixin(MultiPlayerGameMode.class)
public class HitCounterMixin {
	@Inject(method = "attack", at = @At("HEAD"), require = 0)
	private void topclient$hit(Player player, Entity target, CallbackInfo ci) {
		TopMore.HitCounter.onAttack();
	}
}
