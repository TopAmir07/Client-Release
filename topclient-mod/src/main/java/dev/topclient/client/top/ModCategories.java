package dev.topclient.client.top;

import dev.topclient.client.gui.model.GuiModule;

import java.util.HashMap;
import java.util.Map;

/** Which of the 8 mod-menu categories each module belongs to (by module id). Modules stay unchanged. */
public final class ModCategories {
	private ModCategories() {
	}

	public static final String HUD = "HUD", PVP = "PvP", PERFORMANCE = "Performance", VISUALS = "Visuals", INFORMATION = "Information",
			UTILITY = "Utility", ANIMATIONS = "Animations", MISC = "Miscellaneous";
	public static final String[] ALL = {HUD, PVP, PERFORMANCE, VISUALS, INFORMATION, UTILITY, ANIMATIONS, MISC};

	private static final Map<String, String> MAP = new HashMap<>();
	/** Mods added in TopClient itself (shown under NEW). */
	private static final java.util.Set<String> NEW = new java.util.HashSet<>();

	private static void put(String category, String... ids) {
		for (String id : ids) {
			MAP.put(id, category);
		}
	}

	static {
		put(HUD, "clover:fps", "clover:cps", "clover:ping", "clover:coords", "clover:direction", "clover:compassbar", "clover:clock",
				"clover:analogclock", "clover:date", "clover:memory", "clover:worldtime", "clover:daycycle", "clover:biome", "clover:keystrokes",
				"clover:armor", "clover:itemcount", "clover:helditem", "clover:potions", "clover:saturation", "clover:scoreboard",
				"clover:server", "clover:playtime", "clover:session", "clover:speed", "clover:players", "clover:day", "clover:fpsgraph",
				"clover:spotify", "core:HUD", "clover:togglestatus", "clover:timer", "clover:bossbar", "clover:tablist", "clover:hotbar", "clover:crosshairedit");
		put(PVP, "clover:combo", "clover:reach", "clover:kills", "clover:totems", "clover:arrows",
				"clover:durability", "clover:lowhealth", "clover:health", "clover:hits", "clover:damageindicator");
		put(PERFORMANCE, "clover:performance", "clover:entityculling", "clover:fastchat");
		put(VISUALS, "clover:zoom", "clover:freelook", "clover:blockoutline", "clover:crosshair", "clover:motionblur", "clover:timechanger",
				"clover:colorboost", "clover:nametags", "clover:nohurtcam", "clover:totemparticles", "clover:slothighlights", "group:visuals",
				"clover:weatherchanger", "clover:particles", "clover:fullbright", "clover:fog", "clover:lowoverlays", "clover:hitcolor", "locked:hitcolor", "clover:handpos", "clover:waypoints", "clover:minimap");
		put(INFORMATION, "clover:deathcoords", "clover:nethercoords", "clover:pickups", "clover:movement", "clover:horsestats", "clover:weather",
				"clover:lightlevel", "clover:ylevel", "clover:chunk", "clover:facing", "clover:xp", "clover:dimension", "clover:invspace",
				"clover:entities", "clover:nearby", "clover:tps", "clover:sessionstats");
		put(UTILITY, "clover:togglesprint", "core:Sprint", "clover:stopwatch", "clover:shulker", "clover:chattime", "clover:infinitechat",
				"clover:autogg", "clover:lyrics", "clover:breakreminder", "clover:togglesneak", "clover:snippets", "clover:timer",
				"locked:replay", "clover:chatstyle", "clover:chatfilter", "clover:compactchat");
		put(ANIMATIONS, "clover:itemphysics", "clover:capephysics", "clover:oldanimations", "locked:oldanimations");
		markNew("clover:bossbar", "clover:hits", "clover:handpos", "clover:snippets", "clover:timer", "clover:performance", "clover:waypoints", "clover:minimap", "clover:fog", "clover:lowoverlays", "clover:weatherchanger", "clover:particles", "clover:togglesneak", "clover:togglestatus", "clover:chatfilter", "clover:compactchat", "clover:crosshair", "clover:fullbright", "clover:oldanimations");
	}

	public static void markNew(String... ids) {
		NEW.addAll(java.util.List.of(ids));
	}

	public static boolean isNew(GuiModule m) {
		return NEW.contains(m.id());
	}

	public static String of(GuiModule m) {
		return MAP.getOrDefault(m.id(), MISC);
	}
}
