package dev.topclient.client.clover.module;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cape Physics: capes are drawn as 16 thin strips that swing, trail and wave like cloth instead of
 * one stiff board. TopClient's own simulation: the top strip follows the game's own cape swing,
 * and every strip below follows the one above it a little later, so movement rolls down the cape
 * as a wave. Purely visual and only for you, so it is safe on every server.
 * The drawing hook is clover/mixin/CapePhysicsMixin.
 */
public final class CapePhysics extends Module {
	public static final int SEGMENTS = 16;

	public final Setting strength = number("strength", "Wave strength", 0F, 2F, 0.1F, 1F);
	public final Setting stiffness = number("stiffness", "Stiffness", 0.1F, 1F, 0.05F, 0.45F);
	public final Setting wind = bool("wind", "Wind (moves a little when standing)", true);

	private static CapePhysics instance;
	private static final Map<Integer, Chain> CHAINS = new HashMap<>();
	private static long lastSweep;

	public CapePhysics() {
		super("capephysics", "Cape Physics", "Capes swing and wave like real cloth when you move (yours and everyone's).", Category.VISUAL);
		isNew = true;
		instance = this;
	}

	@Override
	protected boolean defaultEnabled() {
		return true;
	}

	public static CapePhysics active() {
		CapePhysics p = instance;
		return p != null && p.enabled() ? p : null;
	}

	/** One player's cape: the angle of each strip from straight down (degrees, + = away from the back). */
	private static final class Chain {
		final float[] a = new float[SEGMENTS];
		long last = System.nanoTime();
		boolean fresh = true;
	}

	/**
	 * Steps the cape of one player and returns the quads to draw, in model pixels relative to the
	 * point where the cape hangs from the shoulders. Each quad: 4 x (x, y, z, u, v) then the normal (nx, ny, nz).
	 *
	 * @param flap  the game's swing (how much the player moves), lean: up/down speed, side: sideways
	 */
	public static float[][] step(int id, float flap, float lean, float side) {
		CapePhysics p = instance;
		long now = System.nanoTime();
		Chain c = CHAINS.computeIfAbsent(id, k -> new Chain());
		float dt = Math.min(0.1F, Math.max(0F, (now - c.last) / 1e9F));
		c.last = now;
		if (now - lastSweep > 5_000_000_000L) { // forget players that left
			lastSweep = now;
			for (Iterator<Map.Entry<Integer, Chain>> it = CHAINS.entrySet().iterator(); it.hasNext(); ) {
				if (now - it.next().getValue().last > 10_000_000_000L) {
					it.remove();
				}
			}
		}
		float target = 6F + lean / 2F + flap;
		if (c.fresh) {
			java.util.Arrays.fill(c.a, target);
			c.fresh = false;
		}
		float stiff = p == null ? 0.45F : p.stiffness.getFloat();
		float str = p == null ? 1F : p.strength.getFloat();
		boolean windy = p == null || p.wind.getBool();
		c.a[0] += (target - c.a[0]) * (1F - (float) Math.exp(-dt * 16F));
		float follow = 1F - (float) Math.exp(-dt * (5F + 22F * stiff));
		for (int i = 1; i < SEGMENTS; i++) {
			c.a[i] += (c.a[i - 1] - c.a[i]) * follow;
		}
		// the wave on top: stronger and faster the faster you move; a light flutter when standing
		float move = Math.min(1.2F, Math.max(0F, flap / 35F));
		double t = (now % 3_600_000_000_000L) / 1e9;
		float amp = str * ((windy ? 2.2F : 0F) + 9F * move);
		double speed = Math.PI * 2 * (0.7 + 1.6 * move);
		float[] d = new float[SEGMENTS];
		for (int i = 0; i < SEGMENTS; i++) {
			float k = i / (float) (SEGMENTS - 1);
			float wave = amp * k * (float) Math.sin(t * speed - i * 0.55);
			d[i] = Math.max(2F, Math.min(110F, c.a[i] + wave)); // never into the back, never over the head
		}
		return quads(d, side);
	}

	/**
	 * Builds the strips (cape is 10 x 16 pixels, 1 thick; texture laid out like a normal 64x32 cape).
	 * The game turns its cape model half a turn, so the picture's left edge (u = 1) sits at x = +5.
	 */
	private static float[][] quads(float[] deg, float side) {
		int n = SEGMENTS;
		float seg = 16F / n;
		float[] py = new float[n + 1], pz = new float[n + 1], ny = new float[n + 1], nz = new float[n + 1];
		for (int i = 0; i < n; i++) {
			double r = Math.toRadians(deg[i]);
			py[i + 1] = py[i] + seg * (float) Math.cos(r);
			pz[i + 1] = pz[i] + seg * (float) Math.sin(r);
		}
		for (int i = 0; i <= n; i++) { // outward normal at each joint: average of the strips around it
			double r = Math.toRadians(i == 0 ? deg[0] : i == n ? deg[n - 1] : (deg[i - 1] + deg[i]) / 2F);
			ny[i] = -(float) Math.sin(r);
			nz[i] = (float) Math.cos(r);
		}
		float[][] out = new float[n * 4 + 2][];
		int q = 0;
		for (int i = 0; i < n; i++) {
			float v0 = 1F + 16F * i / n, v1 = 1F + 16F * (i + 1) / n;
			float oy0 = py[i] + ny[i], oz0 = pz[i] + nz[i], oy1 = py[i + 1] + ny[i + 1], oz1 = pz[i + 1] + nz[i + 1];
			float sn = -(float) Math.sin(Math.toRadians(deg[i])), cs = (float) Math.cos(Math.toRadians(deg[i]));
			// outside (the picture side, seen from behind)
			out[q++] = quad(-5, oy0, oz0, 11, v0, 5, oy0, oz0, 1, v0, 5, oy1, oz1, 1, v1, -5, oy1, oz1, 11, v1, 0, sn, cs);
			// inside (against the back)
			out[q++] = quad(5, py[i], pz[i], 22, v0, -5, py[i], pz[i], 12, v0, -5, py[i + 1], pz[i + 1], 12, v1, 5, py[i + 1], pz[i + 1], 22, v1, 0, -sn, -cs);
			// the two thin side edges
			out[q++] = quad(-5, py[i], pz[i], 12, v0, -5, oy0, oz0, 11, v0, -5, oy1, oz1, 11, v1, -5, py[i + 1], pz[i + 1], 12, v1, -1, 0, 0);
			out[q++] = quad(5, oy0, oz0, 1, v0, 5, py[i], pz[i], 0, v0, 5, py[i + 1], pz[i + 1], 0, v1, 5, oy1, oz1, 1, v1, 1, 0, 0);
		}
		out[q++] = quad(-5, py[0], pz[0], 11, 0, 5, py[0], pz[0], 1, 0, 5, ny[0], nz[0], 1, 1, -5, ny[0], nz[0], 11, 1, 0, -1, 0);
		out[q] = quad(-5, py[n] + ny[n], pz[n] + nz[n], 21, 0, 5, py[n] + ny[n], pz[n] + nz[n], 11, 0, 5, py[n], pz[n], 11, 1, -5, py[n], pz[n], 21, 1, 0, 1, 0);
		// sideways lean: tilt around the hanging point (as the game does, half the lean)
		if (side != 0F) {
			double r = Math.toRadians(side / 2F);
			float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
			for (float[] f : out) {
				for (int k = 0; k < 4; k++) {
					float x = f[k * 5], y = f[k * 5 + 1];
					f[k * 5] = x * cs - y * sn;
					f[k * 5 + 1] = x * sn + y * cs;
				}
				float x = f[20], y = f[21];
				f[20] = x * cs - y * sn;
				f[21] = x * sn + y * cs;
			}
		}
		return out;
	}

	private static float[] quad(float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
			float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3, float nx, float ny, float nz) {
		return new float[]{x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, nx, ny, nz};
	}
}
