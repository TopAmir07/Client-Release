package dev.topclient.client.top.ui;

import dev.topclient.client.gui.Draw;
import dev.topclient.client.top.TopTheme;
import dev.topclient.client.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawing + input context shared by every TopClient screen.
 *
 * Screens draw in "design pixels": real screen pixels divided by a size factor that follows the
 * window height, so the menus look the same at every GUI scale and resolution and text stays sharp.
 *
 * Clickable areas are kept in plain int arrays that are reused every frame (no objects created
 * per frame). Each area carries an action number and an argument; the screen's {@link Handler}
 * decides what they mean.
 */
public final class Ui {
	public interface Handler {
		/** button 0 = left, 1 = right. */
		void onClick(int action, int arg, int button);

		/** Called while the mouse is held after a click on an area marked as draggable. */
		default void onDrag(int action, int arg, double mx, double my) {
		}

		default void onRelease(int action, int arg) {
		}
	}

	public GuiGraphicsExtractor ctx;
	public float scale = 1F;
	/** Design size of the whole screen. */
	public int W, H;
	public double mx, my;
	/** Seconds since the last frame (capped), for animations. */
	public float dt = 0.016F;
	private long lastFrame;
	private final float userScale;

	// ---- hit areas (reused arrays) ----
	private static final int MAX = 768;
	private final int[] hx = new int[MAX], hy = new int[MAX], hw = new int[MAX], hh = new int[MAX];
	private final int[] hAction = new int[MAX], hArg = new int[MAX], hFlags = new int[MAX];
	private final int[] cx0 = new int[MAX], cy0 = new int[MAX], cx1 = new int[MAX], cy1 = new int[MAX];
	private int count;
	private static final int DRAG = 1, CLIPPED = 2;

	// clip rectangle applied to new hit areas (set by scroll views)
	private boolean clipOn;
	private int clipX0, clipY0, clipX1, clipY1;

	// drag in progress
	public int dragAction = -1, dragArg;

	public Ui(float userScale) {
		this.userScale = userScale;
	}

	/** Starts a frame: picks the size factor, switches to real-pixel drawing and clears hit areas. */
	public void begin(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		this.ctx = ctx;
		long now = System.nanoTime();
		dt = lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
		lastFrame = now;
		Minecraft mc = Minecraft.getInstance();
		double sf = mc.getWindow().getGuiScale();
		int fbW = mc.getWindow().getWidth(), fbH = mc.getWindow().getHeight();
		if (dragAction < 0) {
			scale = (float) (Math.max(0.6, Math.min(1.8, fbH / 1000.0)) * userScale);
		}
		Draw.ui = scale;
		mx = mouseX * sf / scale;
		my = mouseY * sf / scale;
		W = Math.round(fbW / scale);
		H = Math.round(fbH / scale);
		count = 0;
		clipOn = false;
		TopTheme.tick();
		RenderUtils.unscaledProjection(ctx);
		ctx.pose().pushMatrix();
		ctx.pose().scale(scale, scale);
	}

	public void end() {
		ctx.pose().popMatrix();
		RenderUtils.scaledProjection(ctx);
		Draw.alpha = 1F;
	}

	/** Converts a mouse position the game reports (GUI units) to design pixels. */
	public double toDesign(double gui) {
		return gui * Minecraft.getInstance().getWindow().getGuiScale() / scale;
	}

	// ---- clipping for scroll views ----
	public void clip(int x, int y, int w, int h) {
		clipOn = true;
		clipX0 = x;
		clipY0 = y;
		clipX1 = x + w;
		clipY1 = y + h;
		ctx.enableScissor(x, y, x + w, y + h); // the scissor follows the current (design-pixel) transform
	}

	public void unclip() {
		clipOn = false;
		ctx.disableScissor();
	}

	public boolean visible(int y, int h) {
		return !clipOn || (y + h > clipY0 && y < clipY1);
	}

	// ---- hover + hit areas ----
	public boolean over(int x, int y, int w, int h) {
		if (mx < x || my < y || mx >= x + w || my >= y + h) {
			return false;
		}
		return !clipOn || (mx >= clipX0 && my >= clipY0 && mx < clipX1 && my < clipY1);
	}

	public void hit(int x, int y, int w, int h, int action, int arg) {
		add(x, y, w, h, action, arg, 0);
	}

	/** Like hit, and keeps reporting {@link Handler#onDrag} while the button is held. */
	public void dragHit(int x, int y, int w, int h, int action, int arg) {
		add(x, y, w, h, action, arg, DRAG);
	}

	private void add(int x, int y, int w, int h, int action, int arg, int flags) {
		if (count >= MAX) {
			return;
		}
		int i = count++;
		hx[i] = x;
		hy[i] = y;
		hw[i] = w;
		hh[i] = h;
		hAction[i] = action;
		hArg[i] = arg;
		hFlags[i] = flags | (clipOn ? CLIPPED : 0);
		cx0[i] = clipX0;
		cy0[i] = clipY0;
		cx1[i] = clipX1;
		cy1[i] = clipY1;
	}

	/** Finds the top-most area under the point and runs it. True when something was hit. */
	public boolean click(double px, double py, int button, Handler h) {
		mx = px;
		my = py;
		for (int i = count - 1; i >= 0; i--) {
			if (px < hx[i] || py < hy[i] || px >= hx[i] + hw[i] || py >= hy[i] + hh[i]) {
				continue;
			}
			if ((hFlags[i] & CLIPPED) != 0 && (px < cx0[i] || py < cy0[i] || px >= cx1[i] || py >= cy1[i])) {
				continue;
			}
			if ((hFlags[i] & DRAG) != 0 && button == 0) {
				dragAction = hAction[i];
				dragArg = hArg[i];
				h.onClick(hAction[i], hArg[i], button);
				h.onDrag(dragAction, dragArg, px, py);
				return true;
			}
			h.onClick(hAction[i], hArg[i], button);
			return true;
		}
		return false;
	}

	public boolean drag(double px, double py, Handler h) {
		if (dragAction < 0) {
			return false;
		}
		mx = px;
		my = py;
		h.onDrag(dragAction, dragArg, px, py);
		return true;
	}

	public boolean release(Handler h) {
		if (dragAction < 0) {
			return false;
		}
		int a = dragAction, g = dragArg;
		dragAction = -1;
		h.onRelease(a, g);
		return true;
	}

	/** Smoothly moves value toward target (frame-rate independent). */
	public float approach(float value, float target, float speed) {
		float v = value + (target - value) * (1F - (float) Math.exp(-speed * dt));
		return Math.abs(target - v) < 0.002F ? target : v;
	}
}
