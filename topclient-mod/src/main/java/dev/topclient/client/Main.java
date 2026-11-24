package dev.topclient.client;

import net.fabricmc.api.ModInitializer;

import java.io.IOException;

public final class Main implements ModInitializer {
	@Override
	public void onInitialize() {
		try {
			new TopClient();
		} catch (InterruptedException | IOException ignored) {}
	}
}
