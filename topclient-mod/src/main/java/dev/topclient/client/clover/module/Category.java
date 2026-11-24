package dev.topclient.client.clover.module;

public enum Category {
	HUD("HUD"),
	VISUAL("Visual"),
	UTILITY("Utility"),
	WORLD("World");

	public final String title;

	Category(String title) {
		this.title = title;
	}
}
