package dev.topclient.client.utils;

import dev.topclient.client.module.modules.client.ClickGUI;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;

import java.awt.*;

import static dev.topclient.client.TopClient.mc;

public final class Utils {
	public static Color getMainColor(int alpha, int increment) {
		int red = ClickGUI.red.getValueInt();
		int green = ClickGUI.green.getValueInt();
		int blue = ClickGUI.blue.getValueInt();

		if (ClickGUI.rainbow.getValue()) {
			return ColorUtils.getBreathingRGBColor(increment, alpha);
		} else {
			if (ClickGUI.breathing.getValue()) {
				return ColorUtils.getMainColor(new Color(red, green, blue, alpha), increment, 20);
			} else {
				return new Color(red, green, blue, alpha);
			}
		}
	}

	public static int getPing(Entity player) {
		if (mc.getConnection().getConnection() == null) return 0;

		PlayerInfo playerListEntry = mc.getConnection().getPlayerInfo((player.getUUID()));
		if (playerListEntry == null) return 0;
		return playerListEntry.getLatency();
	}
}
