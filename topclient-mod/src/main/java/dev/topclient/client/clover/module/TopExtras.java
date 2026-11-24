package dev.topclient.client.clover.module;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Fullbright, Particle Changer and 1.7 Animations. All of them only change what you see on your own screen. */
public final class TopExtras {
	private TopExtras() {
	}

	/** Fullbright: lights up dark places the way Night Vision does. */
	public static final class Fullbright extends Module {
		public static Fullbright instance;
		final Setting strength = number("strength", "Strength %", 10F, 100F, 5F, 100F);

		public Fullbright() {
			super("fullbright", "Fullbright", "See in caves and at night as if everything were lit, like Night Vision. Only changes how light looks on your screen.", Category.VISUAL);
			instance = this;
		}

		/** 0..1, how strongly dark areas are lit up (0 when off). */
		public static float amount() {
			Fullbright f = instance;
			return f != null && f.enabled() ? Math.max(0F, Math.min(1F, f.strength.getFloat() / 100F)) : 0F;
		}
	}

	/**
	 * Particle Changer: every kind of particle has its own switch and amount, so turning off
	 * fireworks never touches critical hits.
	 */
	public static final class ParticleChanger extends Module {
		public static ParticleChanger instance;

		/** key, label, particle type ids (minecraft:...) */
		static final String[][] KINDS = {
				{"firework", "Fireworks", "firework", "flash"},
				{"crit", "Critical hits", "crit"},
				{"enchantcrit", "Enchantment criticals", "enchanted_hit"},
				{"sweep", "Sword sweep", "sweep_attack"},
				{"damage", "Damage hearts", "damage_indicator"},
				{"explosion", "Explosions", "explosion", "explosion_emitter", "gust", "gust_emitter_large", "gust_emitter_small", "small_gust"},
				{"totem", "Totem of Undying", "totem_of_undying"},
				{"potion", "Potion effects", "effect", "instant_effect", "entity_effect", "witch"},
				{"blockbreak", "Block break (and walking dust)", "block"},
				{"blockhit", "Block hit"},
				{"smoke", "Smoke", "smoke", "large_smoke", "campfire_cosy_smoke", "campfire_signal_smoke", "white_smoke", "ash", "white_ash"},
				{"flame", "Flames", "flame", "small_flame", "soul_fire_flame", "copper_fire_flame"},
				{"water", "Water and bubbles", "bubble", "bubble_pop", "bubble_column_up", "current_down", "splash", "rain", "fishing", "underwater",
						"dripping_water", "falling_water", "dripping_dripstone_water", "falling_dripstone_water", "dolphin"},
				{"lava", "Lava", "lava", "dripping_lava", "falling_lava", "landing_lava", "dripping_dripstone_lava", "falling_dripstone_lava"},
				{"portal", "Portal", "portal", "reverse_portal"},
				{"redstone", "Redstone dust", "dust", "dust_color_transition", "dust_plume"},
				{"hearts", "Hearts and villager sparkles", "heart", "angry_villager", "happy_villager"},
				{"other", "Everything else"}};
		public static final int BLOCK_BREAK = 8, BLOCK_HIT = 9, OTHER = KINDS.length - 1;

		private final Setting[] on = new Setting[KINDS.length];
		private final Setting[] amount = new Setting[KINDS.length];
		private final int[] counter = new int[KINDS.length];
		private static final Map<Identifier, Integer> BY_TYPE = new HashMap<>();
		/** Set while the game spawns block break or block hit particles. */
		public static int context = -1;

		public ParticleChanger() {
			// keeps the old "particles" id so your on/off state carries over
			super("particles", "Particle Changer", "Turn each kind of particle on or off and set how many you see (0%, 25%, 50%, 75% or 100%). Fireworks, crits, explosions, totems, potions, blocks, smoke and more are all separate.", Category.VISUAL);
			instance = this;
			for (int i = 0; i < KINDS.length; i++) {
				header(KINDS[i][1]);
				on[i] = bool(KINDS[i][0], "Show " + KINDS[i][1].toLowerCase(java.util.Locale.ROOT), true);
				amount[i] = number(KINDS[i][0] + "Amt", "Amount %", 0F, 100F, 25F, 100F);
				for (int k = 2; k < KINDS[i].length; k++) {
					BY_TYPE.put(Identifier.withDefaultNamespace(KINDS[i][k]), i);
				}
			}
		}

		public static int kindOf(ParticleType<?> type) {
			Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
			return id == null ? OTHER : BY_TYPE.getOrDefault(id, OTHER);
		}

		/** True when a particle of this kind should not be shown. Keeps an even share (no random numbers). */
		public static boolean drop(int kind) {
			ParticleChanger p = instance;
			if (p == null || kind < 0 || !p.enabled()) {
				return false;
			}
			if (!p.on[kind].getBool()) {
				return true;
			}
			int keep = Math.round(p.amount[kind].getFloat());
			if (keep >= 100) {
				return false;
			}
			if (keep <= 0) {
				return true;
			}
			p.counter[kind] += keep;
			if (p.counter[kind] >= 100) {
				p.counter[kind] -= 100;
				return false;
			}
			return true;
		}
	}

	/** 1.7 Animations: the old way your hand and items move, each part on its own switch. */
	public static final class OldAnimations extends Module {
		public static OldAnimations instance;
		final Setting blockHit = bool("blockhit", "Swing while eating, drinking or using items (block-hit)", true);
		final Setting swordBlock = bool("swordblock", "Sword block pose when holding right click", true);
		final Setting noDip = bool("nodip", "No item dip after each hit (old attack look)", true);

		public OldAnimations() {
			super("oldanimations", "1.7 Animations", "Old-school hand animations: swing while eating or using items, the sword block pose on right click, and no item dip after hits. Only changes how your hand looks.", Category.VISUAL);
			instance = this;
		}

		private static boolean on() {
			return instance != null && instance.enabled();
		}

		public static boolean blockHit() {
			return on() && instance.blockHit.getBool();
		}

		public static boolean swordBlock() {
			return on() && instance.swordBlock.getBool();
		}

		public static boolean noDip() {
			return on() && instance.noDip.getBool();
		}
	}
}
