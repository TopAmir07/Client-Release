package dev.topclient.client.clover.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("setPostEffect")
	void topclient$setPostProcessor(Identifier id);

	@Invoker("clearPostEffect")
	void topclient$clearPostProcessor();

	@Invoker("currentPostEffect")
	Identifier topclient$getPostProcessorId();
}
