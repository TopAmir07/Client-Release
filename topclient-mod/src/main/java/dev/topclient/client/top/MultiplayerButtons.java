package dev.topclient.client.top;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.gui.ClickGui;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

/**
 * Four buttons along the top of the Multiplayer screen: Fabric Mods and Cosmetics on the left,
 * Screenshots and TopClient Mods on the right (around the title).
 */
public final class MultiplayerButtons {
	private MultiplayerButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof JoinMultiplayerScreen)) {
				return;
			}
			try {
				int w = 76, h = 20, y = 6, gap = 4;
				var list = Screens.getWidgets(screen);
				list.add(button("Fabric Mods", "Every mod you have installed", 6, y, w, h, () -> client.gui.setScreen(new FabricModsScreen(screen))));
				list.add(button("Cosmetics", "Your capes and other cosmetics", 6 + w + gap, y, w, h, () -> ClickGui.openClassic("clover:capes")));
				list.add(button("Screenshots", "See, copy, rename and delete your screenshots", width - 6 - w * 2 - gap, y, w, h,
						() -> client.gui.setScreen(new ScreenshotsScreen(screen))));
				list.add(button("TopClient Mods", "The mods built into TopClient", width - 6 - w, y, w, h, () -> ModMenuScreen.open(screen)));
			} catch (RuntimeException e) {
				CloverClient.LOGGER.warn("TopClient multiplayer buttons could not be added: {}", e.toString());
			}
		});
	}

	private static Button button(String label, String tip, int x, int y, int w, int h, Runnable action) {
		return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).tooltip(Tooltip.create(Component.literal(tip))).build();
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
