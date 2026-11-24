package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;

public final class MotionBlur extends Module {
	private final Setting strength = mode("strength", "Strength", 1, "Light", "Medium", "Strong", "Extreme");

	public MotionBlur() {
		super("motionblur", "Motion Blur", "Smooth blur when you turn your camera, like in Lunar and Badlion. Works together with Color Boost.", Category.VISUAL);
		isNew = true;
	}

	/** 1..4 */
	int level() {
		return strength.getMode() + 1;
	}

	@Override
	public void onTick(Minecraft mc) {
		PostFx.update(mc);
	}

	@Override
	public void onDisable() {
		PostFx.update(Minecraft.getInstance());
	}
}
