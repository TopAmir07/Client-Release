package dev.topclient.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Where TopClient keeps its settings: config/topclient. Nothing is copied from other clients. */
public final class LegitConfig {
	private LegitConfig() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("topclient");
	}
}
