package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public final class ClickTracker {
	private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
	private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
	private static boolean leftDown;
	private static boolean rightDown;

	private ClickTracker() {
	}

	public static void poll(Minecraft mc) {
		long now = System.currentTimeMillis();
		long window = mc.getWindow().handle();
		boolean l = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean r = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		if (mc.gui.screen() == null) {
			if (l && !leftDown) {
				LEFT.addLast(now);
			}
			if (r && !rightDown) {
				RIGHT.addLast(now);
			}
		}
		leftDown = l;
		rightDown = r;
		trim(LEFT, now);
		trim(RIGHT, now);
	}

	private static void trim(ArrayDeque<Long> q, long now) {
		while (!q.isEmpty() && now - q.peekFirst() > 1000) {
			q.pollFirst();
		}
	}

	public static int left() {
		return LEFT.size();
	}

	public static int right() {
		return RIGHT.size();
	}

	public static boolean leftHeld() {
		return leftDown;
	}

	public static boolean rightHeld() {
		return rightDown;
	}
}
