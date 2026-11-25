package dev.topclient.client.clover;

import dev.topclient.client.clover.cosmetic.Cosmetics;
import dev.topclient.client.clover.module.ModuleManager;
import dev.topclient.client.clover.servers.FavouriteServers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloverClient implements ClientModInitializer {
	public static final String MOD_ID = "topclient";
	public static final String AUTHOR = "TopAmir_";
	public static final Logger LOGGER = LoggerFactory.getLogger("TopClient/Clover");

	static {
		System.setProperty("java.awt.headless", "true");
	}

	@Override
	public void onInitializeClient() {
		ModuleManager.register();
		Cosmetics.register();
		dev.topclient.client.clover.cosmetic.Hats.register();
		FavouriteServers.register();
		dev.topclient.client.clover.accounts.AccountsScreen.register();
		dev.topclient.client.clover.ui.TopTitleScreen.register();
		dev.topclient.client.top.MenuOverlay.register();
		dev.topclient.client.clover.ui.Screenshots.register();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.saveIfNeeded(true));
		LOGGER.info("Clover mods loaded ({} mods). Fully offline.", ModuleManager.all().size());
	}
}
