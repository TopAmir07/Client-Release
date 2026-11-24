package dev.topclient.client.clover.module;

import dev.topclient.client.clover.CloverClient;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Afrika Color Boost: makes the whole game more colourful with a full-screen shader
 * (assets/topclient/shaders/post/color_boost.fsh). Every option is a slider and changes
 * apply live: the values are written into a tiny 2x1 texture the shader reads. Fully offline.
 */
public final class ColorBoost extends Module {
	/** Shader looks this up as topclient:textures/effect/fx_params.png */
	static final Identifier PARAMS = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "textures/effect/fx_params.png");

	private final Setting saturation = number("saturation", "Saturation %", 0, 200, 5, 135);
	private final Setting vibrance = number("vibrance", "Vibrance", 0, 100, 5, 40);
	private final Setting contrast = number("contrast", "Contrast", -50, 50, 1, 8);
	private final Setting brightness = number("brightness", "Brightness", -50, 50, 1, 0);
	private final Setting warmth = number("warmth", "Warmth", -50, 50, 1, 0);
	private final Setting tint = number("tint", "Tint (green / pink)", -50, 50, 1, 0);
	private final Setting strength = number("strength", "Strength %", 0, 100, 5, 100);

	private static DynamicTexture texture;
	private static int lastHash = Integer.MIN_VALUE;

	public ColorBoost() {
		super("colorboost", "Color Boost", "Makes your whole game more colourful. Drag the sliders: Saturation and Vibrance for colour, Contrast and Brightness for punch, Warmth and Tint for mood. Works together with Motion Blur.", Category.VISUAL);
		isNew = true;
	}

	@Override
	protected boolean defaultEnabled() {
		return false; // everything starts off: switch it on with Right Shift
	}

	private static int enc(float v, float lo, float hi) {
		return Math.max(0, Math.min(255, Math.round((v - lo) / (hi - lo) * 255F)));
	}

	private static int argb(int r, int g, int b, int a) {
		return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
	}

	/**
	 * Makes sure our parameter texture exists and holds the current slider values.
	 * Must run before the effect is first loaded, so the effect binds this texture.
	 */
	boolean ensureParams(Minecraft mc) {
		try {
			int p0 = argb(enc(saturation.getFloat(), 0, 200), enc(vibrance.getFloat(), 0, 100), enc(contrast.getFloat(), -50, 50), enc(brightness.getFloat(), -50, 50));
			int p1 = argb(enc(warmth.getFloat(), -50, 50), enc(strength.getFloat(), 0, 100), enc(tint.getFloat(), -50, 50), 255);
			if (texture == null) {
				NativeImage img = new NativeImage(2, 1, false);
				img.setPixel(0, 0, p0);
				img.setPixel(1, 0, p1);
				texture = new DynamicTexture(PARAMS::toString, img);
				mc.getTextureManager().register(PARAMS, texture);
				lastHash = p0 * 31 + p1;
				return true;
			}
			int hash = p0 * 31 + p1;
			if (hash != lastHash) {
				NativeImage img = texture.getPixels();
				if (img != null) {
					img.setPixel(0, 0, p0);
					img.setPixel(1, 0, p1);
					texture.upload();
				}
				lastHash = hash;
			}
			return true;
		} catch (Throwable t) {
			CloverClient.LOGGER.warn("Color Boost could not update: {}", t.toString());
			return false;
		}
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
