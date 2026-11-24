package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.ItemPose;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives each dropped item's render state a place for its Item Physics pose. */
@Mixin(ItemEntityRenderState.class)
public class ItemPoseStateMixin implements ItemPose {
	@Unique
	private float[] topclient$pose;

	@Override
	public float[] topclient$pose() {
		return topclient$pose;
	}

	@Override
	public void topclient$setPose(float[] pose) {
		topclient$pose = pose;
	}
}
