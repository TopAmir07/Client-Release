package dev.topclient.client.clover.module;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Picks the one post-processing effect that matches Motion Blur + Color Boost together,
 * so both can be on at the same time. Never replaces an effect the game itself is using
 * (for example the creeper / spider spectator views).
 */
public final class PostFx {
	private static int failures;

	private PostFx() {
	}

	static boolean ours(Identifier id) {
		if (id == null || !CloverClient.MOD_ID.equals(id.getNamespace())) {
			return false;
		}
		String p = id.getPath();
		return p.startsWith("motion_blur") || p.startsWith("fx_m");
	}

	private static Identifier wanted() {
		Module mb = ModuleManager.byId("motionblur");
		Module cb = ModuleManager.byId("colorboost");
		int blur = mb instanceof MotionBlur m && m.enabled() ? m.level() : 0;
		if (cb instanceof ColorBoost c && c.enabled() && c.ensureParams(Minecraft.getInstance())) {
			return Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "fx_m" + blur);
		}
		if (blur > 0) {
			return Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "motion_blur_" + blur);
		}
		return null;
	}

	public static void update(Minecraft mc) {
		if (mc == null || mc.gameRenderer == null || failures > 3) {
			return;
		}
		try {
			GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
			Identifier now = gr.topclient$getPostProcessorId();
			Identifier want = wanted();
			if (want == null) {
				if (ours(now)) {
					gr.topclient$clearPostProcessor();
				}
				return;
			}
			if (mc.level == null) {
				return;
			}
			if (now == null || (ours(now) && !now.equals(want))) {
				gr.topclient$setPostProcessor(want);
			}
		} catch (RuntimeException e) {
			failures++;
			CloverClient.LOGGER.warn("Screen effects could not start: {}", e.toString());
		}
	}
}
