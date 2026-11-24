package dev.topclient.client.module.modules.movement;

import dev.topclient.client.event.events.TickListener;
import dev.topclient.client.module.Category;
import dev.topclient.client.module.Module;

public final class Sprint extends Module implements TickListener {
    public Sprint() {
        super("Sprint", "Keeps you sprinting at all times", -1, Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        mc.player.setSprinting(mc.player.input.hasForwardImpulse());
    }
}
