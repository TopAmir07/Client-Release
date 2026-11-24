package dev.topclient.client.top;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.Scroll;
import dev.topclient.client.top.ui.Ui;
import dev.topclient.client.top.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static dev.topclient.client.top.TopTheme.*;

/**
 * Base for the TopClient windows (Crosshair, Screenshots, Fabric Mods...): a centred window that
 * fades and rises in, a header with the title and a close button, and the shared mouse/keyboard
 * plumbing. Subclasses draw their body and handle their own action numbers.
 */
public abstract class TopScreen extends Screen implements Ui.Handler {
	public static final int A_CLOSE = 9000;
	protected static final int HEAD_H = 64;

	protected final Ui ui = new Ui(1F);
	protected final Screen parent;
	protected final Anim openAnim = new Anim(), hClose = new Anim();
	protected final Scroll scroll = new Scroll();
	/** Window rectangle in design pixels, set every frame. */
	protected int wx, wy, ww, wh;
	private final String heading, sub;

	protected TopScreen(Screen parent, String heading, String sub) {
		super(Component.literal(heading));
		this.parent = parent;
		this.heading = heading;
		this.sub = sub;
	}

	/** Preferred window size (design pixels); it's clamped to the screen. */
	protected int prefW() {
		return 1040;
	}

	protected int prefH() {
		return 680;
	}

	protected abstract void drawBody(int x, int y, int w, int h);

	/** Extra things in the header, right of the title (x = where they may start, right = close button). */
	protected void drawHeaderExtras(int x, int right, int cy) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ui.begin(ctx, mouseX, mouseY);
		try {
			float open = openAnim.to(ui, true, 9F);
			Draw.alpha = open;
			Draw.rect(ctx, 0, 0, ui.W, ui.H, argb(Math.round(Math.max(DIM_ALPHA, 150) * open), 0x000000));
			ww = Math.min(prefW(), ui.W - 40);
			wh = Math.min(prefH(), ui.H - 40);
			wx = (ui.W - ww) / 2;
			float rise = 1F - open;
			wy = (ui.H - wh) / 2 + Math.round(rise * rise * 26F);
			Draw.shadow(ctx, wx, wy, ww, wh, 16, 28, 160);
			Draw.round(ctx, wx, wy, ww, wh, 16, argb(Math.max(WINDOW_ALPHA, 235), BG));
			Draw.outline(ctx, wx, wy, ww, wh, 16, 1, argb(255, BORDER));
			// accent line along the top edge
			Draw.roundGradient(ctx, wx + 24, wy, ww - 48, 2, 1, argb(200, ACCENT), argb(200, ACCENT2));
			int cy = wy + HEAD_H / 2;
			Draw.textMid(ctx, heading, wx + 26, cy - 8, 20, argb(255, TEXT), true);
			Draw.textMid(ctx, sub, wx + 26, cy + 13, 11.5F, argb(255, TEXT_MUTE));
			int s = 32, cx = wx + ww - s - 18;
			drawHeaderExtras(wx + 40 + Math.max(Draw.width(heading, 20, true), Draw.width(sub, 11.5F)), cx - 10, cy);
			float hc = hClose.to(ui, ui.over(cx, cy - s / 2, s, s), 14F);
			Draw.round(ctx, cx, cy - s / 2, s, s, 9, argb(Math.round(60 + 160 * hc), mix(CARD_HOVER, DISABLED, 0.35F * hc)));
			Icons.drawCentered(ctx, Icons.ui("close"), cx + s / 2F, cy, 16, argb(255, mix(TEXT_MUTE, TEXT, hc)));
			ui.hit(cx, cy - s / 2, s, s, A_CLOSE, 0);
			Draw.rect(ctx, wx + 1, wy + HEAD_H, ww - 2, 1, argb(255, BORDER));
			drawBody(wx, wy + HEAD_H + 1, ww, wh - HEAD_H - 1);
			drawOverlay();
		} finally {
			ui.end();
		}
	}

	/** Drawn last, above everything (tooltips, pop-ups). */
	protected void drawOverlay() {
	}

	protected void onAction(int action, int arg, int button) {
	}

	@Override
	public final void onClick(int action, int arg, int button) {
		if (action == A_CLOSE) {
			UiSounds.click();
			onClose();
			return;
		}
		onAction(action, arg, button);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		ui.click(ui.toDesign(click.x()), ui.toDesign(click.y()), click.button(), this);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		return ui.drag(ui.toDesign(click.x()), ui.toDesign(click.y()), this) || super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (ui.release(this)) {
			Config.saveIfNeeded(false);
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
		scroll.wheel(v);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Config.saveIfNeeded(true);
		Minecraft.getInstance().gui.setScreen(parent);
	}

	/** A small round-cornered text button with its own hover animation; returns its width. */
	protected int smallButton(int x, int y, int h, String label, boolean accent, Anim hov, int action, int arg) {
		int w = Draw.width(label, 12.5F, true) + 30;
		float hv = hov.to(ui, ui.over(x, y, w, h), 14F);
		Widgets.button(ui, x, y, w, h, label, accent, hv, action, arg);
		return w;
	}

	protected static int smallButtonWidth(String label) {
		return Draw.width(label, 12.5F, true) + 30;
	}
}
