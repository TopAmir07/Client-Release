package dev.topclient.client.module.modules.client;

import dev.topclient.client.TopClient;
import dev.topclient.client.event.events.ButtonListener;
import dev.topclient.client.event.events.HudListener;
import dev.topclient.client.managers.FriendManager;
import dev.topclient.client.module.Category;
import dev.topclient.client.module.Module;
import dev.topclient.client.module.setting.BooleanSetting;
import dev.topclient.client.module.setting.KeybindSetting;
import dev.topclient.client.utils.RenderUtils;
import dev.topclient.client.utils.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class Friends extends Module implements ButtonListener, HudListener {
    private final KeybindSetting addFriendKey = new KeybindSetting("Friend Key", GLFW.GLFW_MOUSE_BUTTON_MIDDLE, false)
            .setDescription("Key to add/remove friends");
    public final BooleanSetting friendStatus = new BooleanSetting("Friend Status", false)
            .setDescription("Tells you if you're aiming at a friend or not");

    private FriendManager manager;

    public Friends() {
        super("Friends", "This module makes it so you can't do certain stuff if you have a player friended!", -1, Category.CLIENT);
        addSettings(addFriendKey, friendStatus);
        setKey(-1);
    }

    @Override
    public void onEnable() {
        manager = TopClient.INSTANCE.getFriendManager();

        eventManager.add(ButtonListener.class, this);
        eventManager.add(HudListener.class, this);

        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(ButtonListener.class, this);
        eventManager.remove(HudListener.class, this);

        super.onDisable();
    }

    @Override
    public void onButtonPress(ButtonEvent event) {
        if(mc.player == null)
            return;

        if(mc.gui.screen() != null)
            return;

        if(mc.hitResult instanceof EntityHitResult hitResult) {
            Entity entity = hitResult.getEntity();

            if(entity instanceof Player player) {
                if (event.button == addFriendKey.getKey() && event.action == GLFW.GLFW_PRESS) {
                    if(!manager.isFriend(player))
                        manager.addFriend(player);
                    else manager.removeFriend(player);
                }
            }
        }
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if(!friendStatus.getValue())
            return;

        GuiGraphicsExtractor context = event.context;
        RenderUtils.unscaledProjection(context);
        if(mc.hitResult instanceof EntityHitResult hitResult) {
            Entity entity = hitResult.getEntity();

            if(entity instanceof Player player) {
                if(manager.isFriend(player)) {
                    TextRenderer.drawCenteredString("Player is friend", context, (mc.getWindow().getWidth() / 2), (mc.getWindow().getHeight() / 2) + 25, Color.GREEN.getRGB());
                }
            }
        }
        RenderUtils.scaledProjection(context);
    }
}
