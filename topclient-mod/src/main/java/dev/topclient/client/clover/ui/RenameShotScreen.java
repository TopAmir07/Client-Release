package dev.topclient.client.clover.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.File;

/** Small window to give a screenshot a name (stays in the same screenshots folder). */
public class RenameShotScreen extends Screen {
	private final Screen parent;
	private final File file;
	private EditBox field;
	private String error = "";

	public RenameShotScreen(Screen parent, File file) {
		super(Component.literal("Rename screenshot"));
		this.parent = parent;
		this.file = file;
	}

	@Override
	protected void init() {
		int w = 220, x = (width - w) / 2, y = height / 2 - 20;
		field = new EditBox(font, x, y, w, 20, Component.literal("Name"));
		field.setMaxLength(60);
		field.setValue(file.getName().replaceFirst("\\.png$", ""));
		addRenderableWidget(field);
		setInitialFocus(field);
		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> apply()).bounds(x, y + 28, 108, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 112, y + 28, 108, 20).build());
	}

	private void apply() {
		String name = field.getValue().trim().replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\.png$", "");
		if (name.isEmpty() || name.startsWith(".")) {
			error = "Pick a name";
			return;
		}
		File to = new File(file.getParentFile(), name + ".png");
		if (to.exists()) {
			error = "A screenshot with that name already exists";
			return;
		}
		if (!file.renameTo(to)) {
			error = "Couldn't rename it";
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui != null) {
			mc.gui.hud.setOverlayMessage(Component.literal("Renamed to " + to.getName()).withColor(0xFBBF24), false);
		}
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.centeredText(font, Component.literal("Rename screenshot"), width / 2, height / 2 - 44, 0xFFFFE6F3);
		if (!error.isEmpty()) {
			ctx.centeredText(font, Component.literal(error), width / 2, height / 2 + 34, 0xFFFF7D97);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
