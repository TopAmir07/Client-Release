package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;

/** Visual mods added in TopClient. All of them only change what you see on your own screen. */
public final class TopVisuals {
	private TopVisuals() {
	}

	/** Fog: pushes the distance fog further away (or almost off). Water, lava and blindness fog stay. */
	public static final class Fog extends Module {
		public static Fog instance;
		final Setting distance = number("distance", "Fog distance", 1F, 4F, 0.25F, 2F);
		final Setting remove = bool("remove", "No distance fog at all", false);

		public Fog() {
			super("fog", "Fog", "Moves the distance fog further away, or removes it. Water, lava and blindness fog are unchanged.", Category.VISUAL);
			instance = this;
		}

		public static boolean on() {
			return instance != null && instance.enabled();
		}

		/** New start/end for the render-distance fog. */
		public static float[] adjust(float start, float end) {
			if (instance.remove.getBool()) {
				return new float[]{end * 10F, end * 10F};
			}
			float k = instance.distance.getFloat();
			return new float[]{start * k, end * k};
		}
	}

	/** Lower fire, shield and pumpkin overlays. */
	public static final class LowOverlays extends Module {
		public static LowOverlays instance;
		final Setting fire = number("fire", "Fire lower by", 0F, 0.5F, 0.05F, 0.3F);
		final Setting shield = number("shield", "Shield lower by", 0F, 0.5F, 0.05F, 0.2F);
		final Setting pumpkin = bool("pumpkin", "Hide the carved pumpkin overlay", true);

		public LowOverlays() {
			super("lowoverlays", "Low Fire & Shield", "Moves the burning overlay and the shield in your hand lower so you see more, and can hide the pumpkin overlay.", Category.VISUAL);
			instance = this;
		}

		public static float fire() {
			return instance != null && instance.enabled() ? instance.fire.getFloat() : 0F;
		}

		public static float shield() {
			return instance != null && instance.enabled() ? instance.shield.getFloat() : 0F;
		}

		public static boolean noPumpkin() {
			return instance != null && instance.enabled() && instance.pumpkin.getBool();
		}
	}

	/** Weather changer: clear skies or rain/thunder, only on your screen. */
	public static final class WeatherChanger extends Module {
		public static WeatherChanger instance;
		final Setting mode = mode("mode", "Weather", 0, "Clear", "Rain", "Thunder");

		public WeatherChanger() {
			super("weatherchanger", "Weather Changer", "Shows clear skies, rain or a thunderstorm on your screen only. The real weather doesn't change.", Category.VISUAL);
			instance = this;
		}

		public static boolean on() {
			return instance != null && instance.enabled();
		}

		public static float rain() {
			return instance.mode.getMode() == 0 ? 0F : 1F;
		}

		public static float thunder() {
			return instance.mode.getMode() == 2 ? 1F : 0F;
		}
	}
}
