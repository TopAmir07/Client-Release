package dev.topclient.client.clover.module;

import java.util.HashMap;
import java.util.Map;

/**
 * Afrika Item Physics: dropped items tip over smoothly and lie flat as soon as they are thrown,
 * spin gently while falling, then stop exactly where they land and rest right on the ground
 * (no floating, no bob). Only changes how items are drawn for you, so it is safe on every server.
 * The drawing happens in clover/mixin/ItemPhysicsMixin; this class works out the pose.
 */
public final class ItemPhysics extends Module {
	public final Setting flat = bool("flat", "Lie flat on the ground", true);
	public final Setting randomTurn = bool("random", "Random direction", true);
	public final Setting airSpin = bool("airspin", "Spin while falling", true);
	public final Setting smooth = bool("smooth", "Smooth tip-over", true);

	/** Items resting on the ground: the direction they landed in (so they never jump on landing). */
	private static final Map<Integer, Float> LANDED = new HashMap<>();
	private static ItemPhysics instance;

	public ItemPhysics() {
		super("itemphysics", "Item Physics", "Dropped items tip over, land and lie flat on the ground instead of floating and spinning.", Category.VISUAL);
		isNew = true;
		instance = this;
	}

	@Override
	protected boolean defaultEnabled() {
		return false; // everything starts off: switch it on with Right Shift
	}

	public static ItemPhysics active() {
		ItemPhysics p = instance;
		return p != null && p.enabled() ? p : null;
	}

	/**
	 * The pose of one dropped item, or null to draw it the normal way (module off, or in water).
	 *
	 * @param spin the turn the game itself would use now (radians), so landing never jumps
	 * @return {turn around Y (radians), tilt from 0 = upright to 1 = flat}
	 */
	public static float[] pose(int id, float age, float offset, boolean onGround, boolean inWater, float spin) {
		ItemPhysics p = active();
		if (p == null || inWater) {
			LANDED.remove(id);
			return null;
		}
		float base = p.randomTurn.getBool() ? offset * 7.31F : 0F;
		float air = p.airSpin.getBool() ? spin : base;
		float yaw;
		if (onGround) {
			if (LANDED.size() > 4096) {
				LANDED.clear();
			}
			yaw = LANDED.computeIfAbsent(id, k -> air);
		} else {
			LANDED.remove(id);
			yaw = air;
		}
		float tilt = 0F;
		if (p.flat.getBool()) {
			if (p.smooth.getBool()) {
				float t = Math.min(1F, Math.max(0F, age / 6F)); // tips over during its first 6 ticks
				tilt = 1F - (1F - t) * (1F - t) * (1F - t);
			} else {
				tilt = 1F;
			}
		}
		return new float[]{yaw, tilt};
	}

	/**
	 * How high to lift the item so the lowest point of its tilted model touches the ground.
	 * minY/maxZ: the item model's box. Tilting by a around X maps a point (y, z) to y*cos(a) - z*sin(a).
	 */
	public static float lift(double minY, double maxZ, float tilt) {
		double a = tilt * Math.PI / 2;
		double bottom = minY * Math.cos(a) - maxZ * Math.sin(a);
		double gap = 0.0625 * (1 - tilt) + 0.004 * tilt; // vanilla floats 1px; flat items sit on the floor
		return (float) (-bottom + gap);
	}

	/** The item model's box, made safe (some models report nothing). */
	public static double safe(double v, double fallback) {
		return Double.isFinite(v) && Math.abs(v) < 4 ? v : fallback;
	}
}
