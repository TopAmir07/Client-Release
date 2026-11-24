package dev.topclient.client.clover.mixin;

import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import com.mojang.math.Transformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayEntityAccessor {
	@Invoker("setBillboardConstraints")
	void topclient$setBillboardMode(Display.BillboardConstraints mode);

	@Invoker("setTransformation")
	void topclient$setTransformation(Transformation transformation);

	@Invoker("setBrightnessOverride")
	void topclient$setBrightness(Brightness brightness);

	@Invoker("setViewRange")
	void topclient$setViewRange(float range);
}
