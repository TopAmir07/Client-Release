package dev.topclient.client.managers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.Set;

import static dev.topclient.client.TopClient.mc;

public final class FriendManager {
    private final Set<String> friends;

    public FriendManager() {
        this.friends = new HashSet<>();
    }

    public void addFriend(Player player) {
        friends.add(player.getName().getString());
    }

    public void removeFriend(Player player) {
        friends.remove(player.getName().getString());
    }

    public boolean isFriend(Player player) {
        return friends.contains(player.getName().getString());
    }

    public boolean isAimingOverFriend() {
        if(mc.hitResult instanceof EntityHitResult hitResult) {
            Entity entity = hitResult.getEntity();

            if(entity instanceof Player player) {
                return isFriend(player);
            }
        }

        return false;
    }
}
