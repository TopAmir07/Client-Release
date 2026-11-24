package dev.topclient.client;

import dev.topclient.client.event.EventManager;
import dev.topclient.client.gui.ClickGui;
import dev.topclient.client.managers.FriendManager;
import dev.topclient.client.module.ModuleManager;
import dev.topclient.client.managers.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;

@SuppressWarnings("all")
public final class TopClient {
	public ProfileManager profileManager;
	public ModuleManager moduleManager;
	public EventManager eventManager;
	public FriendManager friendManager;
	public static Minecraft mc;
	public String version = "2.0";
	public static TopClient INSTANCE;
	public boolean guiInitialized;
	public ClickGui clickGui;
	public Screen previousScreen = null;

	public TopClient() throws InterruptedException, IOException {
		INSTANCE = this;
		this.eventManager = new EventManager();
		this.moduleManager = new ModuleManager();
		this.clickGui = new ClickGui();
		this.profileManager = new ProfileManager();
		this.friendManager = new FriendManager();

		this.getProfileManager().loadProfile();

		this.guiInitialized = false;
		mc = Minecraft.getInstance();
	}

	public ProfileManager getProfileManager() {
		return profileManager;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	public FriendManager getFriendManager() {
		return friendManager;
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public ClickGui getClickGui() {
		return clickGui;
	}

	public String getVersion() {
		return version;
	}
}
