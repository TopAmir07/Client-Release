package dev.topclient.client.module.modules.render;

import dev.topclient.client.TopClient;
import dev.topclient.client.event.events.HudListener;
import dev.topclient.client.gui.ClickGui;
import dev.topclient.client.module.Category;
import dev.topclient.client.module.Module;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.module.setting.BooleanSetting;
import dev.topclient.client.utils.RenderUtils;
import dev.topclient.client.utils.TextRenderer;
import dev.topclient.client.utils.Utils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.awt.*;
import java.util.List;

public final class HUD extends Module implements HudListener {
	private static final CharSequence topclient = "TopClient |";
	private final BooleanSetting info = new BooleanSetting("Info", true);
	private final BooleanSetting modules = new BooleanSetting("Modules", true)
			.setDescription("Renders module array list");

	public HUD() {
		super("HUD",
				"Renders the client version and enabled modules on the HUD",
				-1,
				Category.RENDER);
		addSettings(info, modules);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.gui.screen() != TopClient.INSTANCE.clickGui) {
			final List<Module> enabledModules = TopClient.INSTANCE.
					getModuleManager().
					getEnabledModules().
					stream().
					sorted((module1, module2) -> {
						CharSequence name1 = module1.getName();
						CharSequence name2 = module2.getName();

						int filteredLength1 = TextRenderer.getWidth(name1);
						int filteredLength2 = TextRenderer.getWidth(name2);

						return Integer.compare(filteredLength2, filteredLength1);
					}).
					toList();

			GuiGraphicsExtractor context = event.context;
			boolean customFont = ClickGUI.customFont.getValue();

			if (!(mc.gui.screen() instanceof ClickGui)) {
				if (info.getValue() && mc.player != null) {
					RenderUtils.unscaledProjection(context);
					int topclientOffset = 10;
					int topclientOffset2 = 10 + TextRenderer.getWidth(topclient);

					String ping = "Ping: ";
					String fps = "FPS: " + mc.getFps() + " |";
					String server = mc.getCurrentServer() == null ? "None" : mc.getCurrentServer().ip;
					if (mc != null && mc.player != null && mc.getConnection() != null) {
						PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
						if (entry != null) {
							ping += entry.getLatency() + " |";
						} else {
							ping += "N/A |";
						}
					} else {
						ping += "N/A |";
					}

				RenderUtils.renderRoundedQuad(context, new Color(35, 35, 35, 255), 5, 6, topclientOffset2 + TextRenderer.getWidth(fps) + TextRenderer.getWidth(ping) + TextRenderer.getWidth(server) + 35, 30, 5, 15);

					TextRenderer.drawString(topclient, context, topclientOffset, 12, Utils.getMainColor(255, 4).getRGB());
					topclientOffset += TextRenderer.getWidth(topclient);

					TextRenderer.drawString(fps, context, topclientOffset + 10, 12, Utils.getMainColor(255, 3).getRGB());
					TextRenderer.drawString(ping, context, (topclientOffset + 10) + TextRenderer.getWidth(fps) + 10, 12, Utils.getMainColor(255, 2).getRGB());
					TextRenderer.drawString(server, context, (topclientOffset + 10) + TextRenderer.getWidth(fps) + TextRenderer.getWidth(ping) + 20, 12, Utils.getMainColor(255, 1).getRGB());

					RenderUtils.scaledProjection(context);
				}
				if (modules.getValue()) {
					int offset = 55;
					for (Module module : enabledModules) {
						RenderUtils.unscaledProjection(context);
						int charOffset = 6 + TextRenderer.getWidth(module.getName());

			RenderUtils.renderRoundedQuad(context, new Color(0, 0, 0, 175), 0, offset - 4, (charOffset + 5), offset + (mc.font.lineHeight * 2) - 1, 0, 0, 0, 5, 10);
						context.fillGradient(0, offset - 4, 2, offset + (mc.font.lineHeight * 2), Utils.getMainColor(255, (enabledModules.indexOf(module))).getRGB(), Utils.getMainColor(255, (enabledModules.indexOf(module)) + 1).getRGB());

						int charOffset2 = customFont ? 5 : 8;

						TextRenderer.drawString(module.getName(), context, charOffset2, offset + (customFont ? 1 : 0), Utils.getMainColor(255, (enabledModules.indexOf(module))).getRGB());

						offset += (mc.font.lineHeight * 2) + 3;
						RenderUtils.scaledProjection(context);
					}
				}
			}
		}
	}
}
