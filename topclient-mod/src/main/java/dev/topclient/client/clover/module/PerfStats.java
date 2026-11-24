package dev.topclient.client.clover.module;

/**
 * Real frame-time statistics, measured once per drawn frame (not taken from the game's once-a-second FPS number).
 * Call {@link #frame()} exactly once per frame; the values cover roughly the last five seconds.
 */
public final class PerfStats {
	private static final int WINDOWS = 10;
	private static final long WINDOW_NS = 500_000_000L;
	private static final long GAP_NS = 1_000_000_000L; // longer pauses (menus, F1) are not frames we measured

	private static final float[] wMin = new float[WINDOWS];
	private static final float[] wMax = new float[WINDOWS];
	private static final float[] wAvg = new float[WINDOWS];
	private static final float[] wMs = new float[WINDOWS];
	private static int filled;
	private static int idx;

	private static long last;
	private static long winStart;
	private static int count;
	private static long sum;
	private static long worst;
	private static long best = Long.MAX_VALUE;

	private static volatile float minFps;
	private static volatile float maxFps;
	private static volatile float avgFps;
	private static volatile float frameMs;

	private PerfStats() {
	}

	public static void frame() {
		long now = System.nanoTime();
		if (last != 0) {
			long dt = now - last;
			if (dt > GAP_NS) {
				resetWindow();
				filled = 0;
				idx = 0;
				winStart = now;
			} else if (dt > 0) {
				count++;
				sum += dt;
				worst = Math.max(worst, dt);
				best = Math.min(best, dt);
			}
		}
		last = now;
		if (winStart == 0) {
			winStart = now;
		}
		if (now - winStart >= WINDOW_NS && count > 0) {
			publish(now);
		}
	}

	private static void publish(long now) {
		double elapsed = sum;
		wAvg[idx] = (float) (count * 1_000_000_000.0 / elapsed);
		wMin[idx] = 1_000_000_000F / worst;
		wMax[idx] = 1_000_000_000F / best;
		wMs[idx] = (float) (elapsed / count / 1_000_000.0);
		idx = (idx + 1) % WINDOWS;
		filled = Math.min(filled + 1, WINDOWS);
		float mn = Float.MAX_VALUE, mx = 0, av = 0, ms = 0;
		for (int i = 0; i < filled; i++) {
			mn = Math.min(mn, wMin[i]);
			mx = Math.max(mx, wMax[i]);
			av += wAvg[i];
			ms += wMs[i];
		}
		minFps = mn;
		maxFps = mx;
		avgFps = av / filled;
		frameMs = ms / filled;
		resetWindow();
		winStart = now;
	}

	private static void resetWindow() {
		count = 0;
		sum = 0;
		worst = 0;
		best = Long.MAX_VALUE;
	}

	/** True once at least one half-second window has been measured. */
	public static boolean ready() {
		return avgFps > 0;
	}

	public static int min() {
		return Math.round(minFps);
	}

	public static int max() {
		return Math.round(maxFps);
	}

	public static int avg() {
		return Math.round(avgFps);
	}

	public static float frameMs() {
		return frameMs;
	}
}
