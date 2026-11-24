package dev.topclient.client.top.ui;

/** One animated 0..1 value (hover, switch position, ...). Widgets keep these instead of creating objects every frame. */
public final class Anim {
	public float v;

	public Anim() {
	}

	public Anim(float start) {
		v = start;
	}

	public float to(Ui ui, boolean on, float speed) {
		v = ui.approach(v, on ? 1F : 0F, speed);
		return v;
	}

	public float to(Ui ui, float target, float speed) {
		v = ui.approach(v, target, speed);
		return v;
	}
}
