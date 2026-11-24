package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class Freelook extends Module {
	private static Freelook instance;
	private final Setting key = Setting.key(this, "look", "Freelook key", GLFW.GLFW_KEY_LEFT_ALT);
	private final Setting toggle = mode("mode", "Key mode", 0, "Hold", "Toggle");
	private final Setting invert = bool("invert", "Invert up / down", false);

	private boolean on;
	private boolean keyWas;
	private float yaw, pitch;
	private CameraType saved;

	public Freelook() {
		super("freelook", "Freelook", "Hold Left Alt to look around yourself in third person while you keep walking straight.", Category.UTILITY);
		isNew = true;
		instance = this;
		settings.add(0, key);
	}

	public static boolean active() {
		Freelook f = instance;
		return f != null && f.on && f.enabled();
	}

	public static float yaw() {
		return instance.yaw;
	}

	public static float pitch() {
		return instance.pitch;
	}

	public static void turn(double dx, double dy) {
		Freelook f = instance;
		f.yaw += (float) dx * 0.15F;
		f.pitch += (float) (f.invert.getBool() ? -dy : dy) * 0.15F;
		f.pitch = Math.max(-90F, Math.min(90F, f.pitch));
	}

	private void start(Minecraft mc) {
		if (on || mc.player == null) {
			return;
		}
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		saved = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		on = true;
	}

	private void stop(Minecraft mc) {
		if (!on) {
			return;
		}
		on = false;
		if (saved != null && mc.options != null) {
			mc.options.setCameraType(saved);
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.getWindow() == null || mc.player == null) {
			stop(mc);
			return;
		}
		boolean down = key.getKey() > 0 && mc.gui.screen() == null && InputConstants.isKeyDown(mc.getWindow(), key.getKey());
		if (toggle.getMode() == 0) {
			if (down) {
				start(mc);
			} else {
				stop(mc);
			}
		} else if (down && !keyWas) {
			if (on) {
				stop(mc);
			} else {
				start(mc);
			}
		}
		keyWas = down;
	}

	@Override
	public void onDisable() {
		stop(Minecraft.getInstance());
	}
}
