package dev.topclient.client.top;

import dev.topclient.client.gui.model.GuiModule;
import dev.topclient.client.gui.model.GuiSetting;

import java.util.List;

/** Mods shown as LOCKED: planned or not realistic in this client. They can't be switched on. */
final class LockedMods {
	private LockedMods() {
	}

	private record Locked(String id, String name, String description) implements GuiModule {
		@Override
		public boolean enabled() {
			return false;
		}

		@Override
		public void setEnabled(boolean on) {
		}

		@Override
		public boolean toggleable() {
			return false;
		}

		@Override
		public List<GuiSetting> settings() {
			return List.of();
		}
	}

	static List<GuiModule> all() {
		return List.of(new Locked("locked:replay", "Replay", "Recording and replaying matches needs a full replay system; not available in TopClient."),
				new Locked("locked:hitcolor", "Hit Colour", "Custom colour when a mob or player is hit. Planned; not in this version."));
	}
}
