package dev.topclient.client.gui.model;

import java.util.List;

public interface GuiModule {
	String name();

	String description();

	boolean enabled();

	void setEnabled(boolean on);

	default Runnable onOpen() {
		return null;
	}

	default boolean toggleable() {
		return true;
	}

	List<GuiSetting> settings();

	default String bindName() {
		return "None";
	}

	default boolean isNew() {
		return false;
	}

	String id();

	default void resetSettings() {
		for (GuiSetting s : settings()) {
			s.reset();
		}
	}
}
