package dev.topclient.client.clover.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class Nametags extends Module {
	public static Nametags instance;

	public final Setting background;
	public final Setting opacity;
	public final Setting colour;
	public final Setting customColour;
	public final Setting bold;
	public final Setting health;
	public final Setting ping;
	public final Setting distance;
	public final Setting self;
	public final Setting selfMark;

	public Nametags() {
		super("nametags", "Nametags", "Clean name tags: no background box, your own colours, health, ping and distance. "
				+ "Only you see the change.", Category.VISUAL);
		instance = this;
		isNew = true;
		header("Background");
		background = mode("bg", "Background", 0, "Clear (name only)", "Vanilla", "Custom opacity");
		opacity = number("bgOpacity", "Custom opacity", 0F, 1F, 0.05F, 0.15F);
		header("Name");
		colour = mode("colour", "Name colour", 0, "Default (team colours)", "White", "Custom", "Rainbow");
		customColour = color("customColour", "Custom colour", 0xFF8A3D);
		bold = bool("bold", "Bold", false);
		header("Extra info");
		health = mode("health", "Health", 1, "Off", "Number", "Hearts", "Percent");
		ping = bool("ping", "Ping", false);
		distance = bool("distance", "Distance", false);
		header("You");
		self = bool("self", "Show my own name tag (F5)", false);
		selfMark = bool("selfMark", "TopClient mark before my name (only you see it)", true);
	}

	@Override
	protected boolean defaultEnabled() {
		return false;
	}

	private static Nametags active() {
		Nametags n = instance;
		return n != null && n.enabled() ? n : null;
	}

	public static float backgroundOpacity() {
		Nametags n = active();
		if (n == null) {
			return -1F;
		}
		return switch (n.background.getMode()) {
			case 0 -> 0F;
			case 2 -> n.opacity.getFloat();
			default -> -1F;
		};
	}

	public static boolean showSelf() {
		Nametags n = active();
		Minecraft mc = Minecraft.getInstance();
		return n != null && n.self.getBool() && !mc.options.getCameraType().isFirstPerson();
	}

	public static Component decorate(LivingEntity player, Component name) {
		Nametags n = active();
		if (n == null || name == null) {
			return name;
		}
		Minecraft mc = Minecraft.getInstance();
		MutableComponent out = Component.empty();

		Component elephant = null;
		if (false) {
			elephant = name.getSiblings().get(0);
			MutableComponent rest = Component.empty();
			for (int i = 1; i < name.getSiblings().size(); i++) {
				rest.append(name.getSiblings().get(i));
			}
			name = rest;
		}
		String plain = name.getString();
		switch (n.colour.getMode()) {
			case 1 -> out.append(Component.literal(plain).setStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(n.bold.getBool())));
			case 2 -> out.append(Component.literal(plain).setStyle(Style.EMPTY.withColor(n.customColour.getColor()).withBold(n.bold.getBool())));
			case 3 -> {
				long now = System.currentTimeMillis();
				for (int i = 0; i < plain.length(); i++) {
					float hue = ((now / 12L + i * 18L) % 360L) / 360F;
					int rgb = java.awt.Color.HSBtoRGB(hue, 0.65F, 1F) & 0xFFFFFF;
					out.append(Component.literal(String.valueOf(plain.charAt(i))).setStyle(Style.EMPTY.withColor(rgb).withBold(n.bold.getBool())));
				}
			}
			default -> out.append(n.bold.getBool() ? name.copy().withStyle(s -> s.withBold(true)) : name);
		}

		float hp = player.getHealth() + player.getAbsorptionAmount();
		float max = Math.max(1F, player.getMaxHealth());
		int hpColour = hp / max > 0.6F ? 0x55FF55 : hp / max > 0.3F ? 0xFFD24D : 0xFF5555;
		String hpText = switch (n.health.getMode()) {
			case 1 -> String.valueOf(Math.round(hp));
			case 2 -> String.format(Locale.ROOT, "%.1f❤", hp / 2F);
			case 3 -> Math.round(hp / max * 100F) + "%";
			default -> null;
		};
		if (hpText != null) {
			out.append(Component.literal(" " + hpText).setStyle(Style.EMPTY.withColor(hpColour)));
		}
		if (n.ping.getBool() && mc.getConnection() != null) {
			PlayerInfo e = mc.getConnection().getPlayerInfo(player.getUUID());
			if (e != null) {
				int ms = e.getLatency();
				int c = ms < 80 ? 0x55FF55 : ms < 160 ? 0xFFD24D : 0xFF5555;
				out.append(Component.literal(" " + ms + "ms").setStyle(Style.EMPTY.withColor(c)));
			}
		}
		if (n.distance.getBool() && mc.player != null && player != mc.player) {
			out.append(Component.literal(" " + Math.round(player.distanceTo(mc.player)) + "m").setStyle(Style.EMPTY.withColor(0xAAAAAA)));
		}
		if (player == mc.player && n.selfMark.getBool()) {
			// drawn on your own screen only: it is never sent to the server or to other players
			int accent = dev.topclient.client.top.TopTheme.ACCENT & 0xFFFFFF;
			out = Component.empty().append(Component.literal("\u25C6 ").setStyle(Style.EMPTY.withColor(accent))).append(out);
		}
		return elephant == null ? out : Component.empty().append(elephant).append(out);
	}
}
