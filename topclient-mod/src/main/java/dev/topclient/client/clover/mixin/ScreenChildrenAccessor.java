package dev.topclient.client.clover.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Screen.class)
public interface ScreenChildrenAccessor {
	@Accessor("children")
	List<GuiEventListener> topclient$children();

	/** Used by the pause-menu overlay to hide the "Game Menu" heading (the TopClient logo sits there). */
	@Mutable
	@Accessor("title")
	void topclient$setTitle(Component title);
}
