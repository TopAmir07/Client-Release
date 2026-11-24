package dev.topclient.client.module;

import dev.topclient.client.TopClient;
import dev.topclient.client.event.events.ButtonListener;
import dev.topclient.client.module.modules.client.*;
import dev.topclient.client.module.modules.movement.*;
import dev.topclient.client.module.modules.render.*;
import dev.topclient.client.module.setting.KeybindSetting;

import dev.topclient.client.gui.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager implements ButtonListener {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		addModules();
		addKeybinds();
	}

	public void addModules() {

		add(new Sprint());

		add(new HUD());

		add(new ClickGUI());
		add(new Friends());
	}

	public List<Module> getEnabledModules() {
		return modules.stream()
				.filter(Module::isEnabled)
				.toList();
	}

	public List<Module> getModules() {
		return modules;
	}

	public void addKeybinds() {
		TopClient.INSTANCE.getEventManager().add(ButtonListener.class, this);

		for (Module module : modules)
			module.addSetting(new KeybindSetting("Keybind", module.getKey(), true).setDescription("Key to enabled the module"));
	}

	public List<Module> getModulesInCategory(Category category) {
		return modules.stream()
				.filter(module -> module.getCategory() == category)
				.toList();
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> moduleClass) {
		return (T) modules.stream()
				.filter(moduleClass::isInstance)
				.findFirst()
				.orElse(null);
	}

	public void add(Module module) {
		modules.add(module);
	}

	@Override
	public void onButtonPress(ButtonEvent event) {
		if (event.action != GLFW.GLFW_PRESS || event.button == GLFW.GLFW_KEY_UNKNOWN)
			return;

		Screen screen = Minecraft.getInstance().gui.screen();
		for (Module module : modules) {
			if (module.getKey() != event.button)
				continue;

			boolean menuKey = module instanceof ClickGUI;
			if (menuKey && screen instanceof ClickGui gui && gui.isTyping())
				continue;
			if (menuKey && screen instanceof dev.topclient.client.top.ModMenuScreen mm && mm.isTyping())
				continue;
			if (screen == null || (menuKey && (screen instanceof ClickGui || screen instanceof dev.topclient.client.top.ModMenuScreen
					|| screen instanceof InventoryScreen)))
				module.toggle();
		}
	}
}
