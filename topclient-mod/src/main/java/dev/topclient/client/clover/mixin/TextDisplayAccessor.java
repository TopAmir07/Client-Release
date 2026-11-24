package dev.topclient.client.clover.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Invoker("setText")
	void topclient$setText(Component text);

	@Invoker("setLineWidth")
	void topclient$setLineWidth(int width);

	@Invoker("setBackgroundColor")
	void topclient$setBackground(int argb);

	@Invoker("setTextOpacity")
	void topclient$setTextOpacity(byte opacity);

	@Invoker("setFlags")
	void topclient$setDisplayFlags(byte flags);
}
