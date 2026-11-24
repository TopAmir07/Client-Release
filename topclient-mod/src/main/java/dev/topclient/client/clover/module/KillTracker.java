package dev.topclient.client.clover.module;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class KillTracker {
	private static final Map<Integer, Long> HITS = new HashMap<>();
	private static final Map<Integer, Long> COUNTED = new HashMap<>();
	private static int killCount;
	private static boolean registered;

	private KillTracker() {
	}

	public static int kills() {
		return killCount;
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() && player == Minecraft.getInstance().player && entity instanceof LivingEntity) {
				HITS.put(entity.getId(), System.currentTimeMillis());
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.player == null) {
				HITS.clear();
				COUNTED.clear();
				return;
			}
			try {
				watchHits(mc);
			} catch (RuntimeException ignored) {
			}
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message.getString());
			}
		});
	}

	private static void watchHits(Minecraft mc) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Integer, Long>> it = HITS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Long> e = it.next();
			if (now - e.getValue() > 6000) {
				it.remove();
				continue;
			}
			Entity entity = mc.level.getEntity(e.getKey());
			if (entity instanceof LivingEntity living && (living.isDeadOrDying() || living.deathTime > 0)) {
				it.remove();
				killed(living);
			}
		}
		COUNTED.values().removeIf(t -> now - t > 4000);
	}

	private static void onMessage(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || text == null) {
			return;
		}
		String me = mc.player.getName().getString();
		if (me.length() < 3 || !text.contains(me)) {
			return;
		}
		String lower = text.toLowerCase();
		if (!(lower.contains("kill") || lower.contains("slain") || lower.contains("shot") || lower.contains(" by ")
				|| lower.contains("eliminat") || lower.contains("died"))) {
			return;
		}
		for (Player p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			String name = p.getName().getString();
			if (name.length() >= 3 && text.contains(name) && p.distanceToSqr(mc.player) < 64 * 64) {
				killed(p);
			}
		}
	}

	private static void killed(LivingEntity victim) {
		Minecraft mc = Minecraft.getInstance();
		if (victim == mc.player) {
			return;
		}
		Long seen = COUNTED.get(victim.getId());
		if (seen == null || System.currentTimeMillis() - seen > 3000) {
			killCount++;
		}
		COUNTED.put(victim.getId(), System.currentTimeMillis());
	}
}
