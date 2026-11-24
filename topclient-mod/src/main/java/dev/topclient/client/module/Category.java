package dev.topclient.client.module;

public enum Category {
	COMBAT("Combat", "Sword fights, aim and knockback", Group.COMBAT, 'S'),
	CRYSTAL("Crystal", "End crystals and respawn anchors", Group.COMBAT, 'C'),
	DEFENSE("Defense", "Totems, potions and staying alive", Group.COMBAT, 'D'),
	MOVEMENT("Movement", "How you move", Group.PLAYER, 'M'),
	UTILITY("Utility", "Handy automation and tweaks", Group.PLAYER, 'U'),
	NETWORK("Network", "Connection and packet tools", Group.PLAYER, 'N'),
	RENDER("Render", "ESP, HUD and camera", Group.VISUAL, 'R'),
	CLIENT("Client", "Menu, theme and client options", Group.SYSTEM, 'A');

	public enum Group {
		COMBAT("Combat"), PLAYER("Player"), VISUAL("Visual"), SYSTEM("System");

		public final String title;

		Group(String title) {
			this.title = title;
		}
	}

	public final CharSequence name;
	public final String subtitle;
	public final Group group;
	public final char glyph;

	Category(CharSequence name, String subtitle, Group group, char glyph) {
		this.name = name;
		this.subtitle = subtitle;
		this.group = group;
		this.glyph = glyph;
	}
}
