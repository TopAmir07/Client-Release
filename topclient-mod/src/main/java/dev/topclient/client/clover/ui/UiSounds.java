package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class UiSounds {
	public static final String[] PACKS = {"Soft", "Crisp", "Bubble", "Glass", "Wood", "Retro",
			"Pop", "Keys", "SciFi", "Chime", "Drop", "Arcade", "Minecraft"};
	private static final int MINECRAFT = 12;

	public enum Kind { TOGGLE_ON, TOGGLE_OFF, CLICK, TICK, OPEN, CLOSE, HOVER }

	private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
	private static long last;
	private static long lastTick;
	private static long lastHover;

	private UiSounds() {
	}

	private static SoundEvent vanilla(String name) {
		return EVENTS.computeIfAbsent("minecraft:" + name, n -> SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(name)));
	}

	private static SoundEvent event(String name) {
		return EVENTS.computeIfAbsent(name, n -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, n)));
	}

	public static boolean enabled() {
		return Config.getBool("ui.sounds", true);
	}

	public static float volume() {
		return Math.max(0F, Math.min(1F, Config.getFloat("ui.soundVolume", 0.8F)));
	}

	public static float pitch() {
		return Math.max(0.5F, Math.min(2F, Config.getFloat("ui.soundPitch", 1F)));
	}

	public static int pack() {
		return Math.max(0, Math.min(PACKS.length - 1, Config.getInt("ui.soundPack", 2) /* Bubble */));
	}

	public static String kindKey(Kind k) {
		return switch (k) {
			case TOGGLE_ON, TOGGLE_OFF -> "ui.sound.toggles";
			case CLICK -> "ui.sound.clicks";
			case TICK -> "ui.sound.ticks";
			case OPEN, CLOSE -> "ui.sound.openClose";
			case HOVER -> "ui.sound.hover";
		};
	}

	public static boolean kindOn(Kind k) {
		return Config.getBool(kindKey(k), k != Kind.HOVER);
	}

	private static SoundEvent soundFor(Kind k) {
		int p = pack();
		if (p == 0) {
			return switch (k) {
				case TOGGLE_ON -> event("ui.toggle_on");
				case TOGGLE_OFF -> event("ui.toggle_off");
				case CLICK -> event("ui.click");
				case TICK, HOVER -> event("ui.tick");
				case OPEN, CLOSE -> event("ui.open");
			};
		}
		if (p == MINECRAFT) {
			return vanilla(switch (k) {
				case TOGGLE_ON, TOGGLE_OFF -> "block.note_block.pling";
				case CLICK -> "ui.button.click";
				case TICK, HOVER -> "block.note_block.hat";
				case OPEN -> "block.chest.open";
				case CLOSE -> "block.chest.close";
			});
		}
		return event("ui." + PACKS[p].toLowerCase(java.util.Locale.ROOT) + "." + k.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static void play(Kind k, float pitch, float vol) {
		if (!enabled() || !kindOn(k)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (k != Kind.HOVER && now - last < 40) {
			return;
		}
		if (k != Kind.HOVER) {
			last = now;
		}
		if (pack() == 0 && k == Kind.CLOSE) {
			pitch *= 0.8F;
		}
		if (pack() == 0 && k == Kind.HOVER) {
			vol *= 0.5F;
		}
		if (pack() == MINECRAFT) {
			if (k == Kind.TOGGLE_ON) {
				pitch *= 1.5F;
			} else if (k == Kind.TOGGLE_OFF) {
				pitch *= 0.9F;
			}
			vol *= k == Kind.CLICK || k == Kind.OPEN || k == Kind.CLOSE ? 0.45F : 0.7F;
		}
		try {
			float p = Math.max(0.5F, Math.min(2F, pitch * pitch()));
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundFor(k), p, volume() * vol));
		} catch (RuntimeException ignored) {
		}
	}

	public static void toggle(boolean on) {
		play(on ? Kind.TOGGLE_ON : Kind.TOGGLE_OFF, 1F, 1F);
	}

	public static void click() {
		play(Kind.CLICK, 0.95F + (float) Math.random() * 0.1F, 0.9F);
	}

	public static void tick(float value) {
		long now = System.currentTimeMillis();
		if (now - lastTick < 55) {
			return;
		}
		lastTick = now;
		last = 0;
		play(Kind.TICK, 0.75F + Math.max(0F, Math.min(1F, value)) * 0.7F, 0.8F);
	}

	public static void open() {
		play(Kind.OPEN, 1F, 0.8F);
	}

	public static void close() {
		play(Kind.CLOSE, 1F, 0.8F);
	}

	public static void hover() {
		long now = System.currentTimeMillis();
		if (now - lastHover < 60) {
			return;
		}
		lastHover = now;
		play(Kind.HOVER, 1F, 0.5F);
	}

	public static void preview() {
		last = 0;
		boolean was = kindOn(Kind.TOGGLE_ON);
		play(Kind.TOGGLE_ON, 1F, 1F);
		if (!was) {
			play(Kind.CLICK, 1F, 1F);
		}
	}
}
