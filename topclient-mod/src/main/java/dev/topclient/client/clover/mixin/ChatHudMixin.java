package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.ChatModules;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
	private static boolean topclient$reentry;

	@Shadow
	private void addMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"},
			constant = @Constant(intValue = 100), require = 0)
	private int topclient$history(int vanilla) {
		return ChatModules.historyLimit(vanilla);
	}

	/** Drops the extra lines of other mods' screenshot messages (our one tidy line replaces them). */
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void topclient$dropShotSpam(Component message, MessageSignature sig, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		if (topclient$reentry) {
			return;
		}
		if (ChatModules.filtered(message)) {
			ci.cancel();
			return;
		}
		Component tidy = dev.topclient.client.clover.ui.Screenshots.tidyChat(message);
		if (tidy == message) {
			return;
		}
		ci.cancel();
		if (tidy != null) {
			topclient$reentry = true;
			try {
				addMessage(tidy, sig, source, tag);
			} finally {
				topclient$reentry = false;
			}
		}
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Component topclient$timestamp(Component message) {
		return ChatModules.stamp(ChatModules.compact(message, this));
	}
}
