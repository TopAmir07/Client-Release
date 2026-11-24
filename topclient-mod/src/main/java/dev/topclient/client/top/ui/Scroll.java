package dev.topclient.client.top.ui;

/** Smooth scrolling for one scroll view: wheel sets a target, the view glides to it. */
public final class Scroll {
	public float pos, target, max;

	public void wheel(double amount) {
		target = Math.max(0, Math.min(max, target - (float) amount * 60F));
	}

	public void setMax(float m) {
		max = Math.max(0, m);
		target = Math.min(target, max);
		pos = Math.min(pos, max);
	}

	public void update(Ui ui) {
		pos = ui.approach(pos, target, 18F);
	}

	public void jump(float to) {
		target = pos = Math.max(0, Math.min(max, to));
	}

	public void reset() {
		pos = target = 0;
	}
}
