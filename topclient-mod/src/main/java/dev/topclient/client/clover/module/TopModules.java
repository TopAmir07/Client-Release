package dev.topclient.client.clover.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;

/** Mods added in TopClient (core group): Toggle Sneak and the toggle status display. */
public final class TopModules {
	private TopModules() {
	}

	/** Press sneak once to keep sneaking, press again to stop. Same as holding the key, nothing more. */
	public static final class ToggleSneak extends Module {
		private boolean toggled, wasDown;
		private static ToggleSneak instance;

		public ToggleSneak() {
			super("togglesneak", "Toggle Sneak", "Press the sneak key once to keep sneaking, press again to stop.", Category.UTILITY);
			instance = this;
		}

		static boolean sneaking() {
			return instance != null && instance.enabled() && instance.toggled;
		}

		@Override
		public void onTick(Minecraft mc) {
			if (mc.player == null || mc.getWindow() == null) {
				toggled = false;
				return;
			}
			InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(mc.options.keyShift);
			boolean down = k.getType() == InputConstants.Type.KEYSYM && k.getValue() > 0
					&& InputConstants.isKeyDown(mc.getWindow(), k.getValue());
			if (down && !wasDown && mc.gui.screen() == null) {
				toggled = !toggled;
			}
			wasDown = down;
			if (mc.player.getAbilities().flying || mc.player.isPassenger()) {
				toggled = false; // would make you fly down or get off
			}
			if (toggled) {
				mc.options.keyShift.setDown(true);
			}
		}

		@Override
		public void onDisable() {
			if (toggled) {
				Minecraft.getInstance().options.keyShift.setDown(false);
			}
			toggled = false;
		}
	}

	/** Shows "[Sprinting (Toggled)]" / "[Sneaking (Toggled)]" like other clients. */
	public static final class ToggleDisplay extends TextHud {
		private static final String[] NONE = new String[0];
		private static final String[] SNEAK = {"Sneaking (Toggled)"};
		private static final String[] SPRINT_T = {"Sprinting (Toggled)"};
		private static final String[] SPRINT_V = {"Sprinting (Vanilla)"};

		public ToggleDisplay() {
			super("togglestatus", "Toggle Status", "Shows when Toggle Sprint or Toggle Sneak is keeping you sprinting or sneaking.", 1, 140);
		}

		@Override
		protected String[] lines(Minecraft mc) {
			if (mc.player == null) {
				return NONE;
			}
			if (ToggleSneak.sneaking()) {
				return SNEAK;
			}
			if (mc.player.isSprinting()) {
				Module ts = ModuleManager.byId("togglesprint");
				return ts != null && ts.enabled() ? SPRINT_T : SPRINT_V;
			}
			return NONE;
		}

		@Override
		public boolean available(Minecraft mc) {
			return mc.player != null && lines(mc).length > 0;
		}
	}
}
