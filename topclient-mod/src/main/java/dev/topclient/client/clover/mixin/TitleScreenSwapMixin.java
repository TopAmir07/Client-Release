package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.ui.TopTitleScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps the argument at the very start of setScreen, so the screen Minecraft stores and the
 * screen it initialises are always the same object (the old PUTFIELD swap could leave an
 * uninitialised vanilla title screen current, which crashed when clicking fast).
 *
 * Shows the Afrika main menu wherever Minecraft would open its own title screen. */
@Mixin(Gui.class)
public abstract class TitleScreenSwapMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen topclient$swapTitle(Screen screen) {
		try {
			return TopTitleScreen.swap(screen);
		} catch (Throwable t) {
			return screen;
		}
	}
}
