package dev.topclient.client.top;

import com.mojang.blaze3d.platform.NativeImage;
import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.ui.RenameShotScreen;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.top.ui.Anim;
import dev.topclient.client.top.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.topclient.client.top.TopTheme.*;

/**
 * Your screenshots, newest first and grouped by day. Click one to see it big, then copy, open,
 * rename or delete it, or step through them with the arrow keys. Only reads the game's own
 * screenshots folder.
 */
public final class ScreenshotsScreen extends TopScreen {
	private static final int A_SHOT = 1, A_FOLDER = 2, A_REFRESH = 3, A_VIEW_CLOSE = 4, A_PREV = 5, A_NEXT = 6, A_OPEN = 7, A_RENAME = 8,
			A_DELETE = 9, A_CONFIRM = 10, A_CANCEL = 11, A_COPY_PATH = 12, A_SCROLL = 13, A_NOTHING = 14;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final int THUMB_W = 320;

	private record Shot(File file, long time, long size) {
	}

	private record Tex(Identifier id, int w, int h) {
	}

	private final List<Shot> shots = new ArrayList<>();
	private final Map<File, Tex> thumbs = new HashMap<>();
	private final java.util.Set<File> loading = new java.util.HashSet<>();
	private final List<Anim> hovers = new ArrayList<>();
	private final Anim[] hBtn = new Anim[10];
	private final Anim viewAnim = new Anim();
	private ExecutorService pool = newPool();

	private static ExecutorService newPool() {
		return Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "TopClient screenshot thumbnails");
			t.setDaemon(true);
			return t;
		});
	}
	private int viewing = -1;
	private Tex big;
	private File bigFile;
	private boolean confirmDelete;
	private int texCounter;

	public ScreenshotsScreen(Screen parent) {
		super(parent, "Screenshots", "Press F2 in game to take one. Click a picture to open it.");
		for (int i = 0; i < hBtn.length; i++) {
			hBtn[i] = new Anim();
		}
		reload();
	}

	public static File dir() {
		return new File(Minecraft.getInstance().gameDirectory, "screenshots");
	}

	private void reload() {
		shots.clear();
		File[] files = dir().listFiles((d, n) -> {
			String l = n.toLowerCase(Locale.ROOT);
			return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg");
		});
		if (files != null) {
			for (File f : files) {
				if (f.isFile()) {
					shots.add(new Shot(f, f.lastModified(), f.length()));
				}
			}
		}
		shots.sort((a, b) -> Long.compare(b.time(), a.time()));
	}

	@Override
	protected int prefW() {
		return 1240;
	}

	@Override
	protected int prefH() {
		return 760;
	}

	@Override
	protected void drawHeaderExtras(int x, int right, int cy) {
		int bx = right;
		String count = shots.size() + (shots.size() == 1 ? " screenshot" : " screenshots");
		bx -= smallButtonWidth("Open folder");
		smallButton(bx, cy - 17, 34, "Open folder", false, hBtn[0], A_FOLDER, 0);
		bx -= smallButtonWidth("Refresh") + 8;
		smallButton(bx, cy - 17, 34, "Refresh", false, hBtn[1], A_REFRESH, 0);
		Draw.textRight(ui.ctx, count, bx - 14, cy, 12, argb(255, TEXT_MUTE));
	}

	// ------------------------------------------------------------------ grid

	@Override
	protected void drawBody(int x, int y, int w, int h) {
		if (shots.isEmpty()) {
			Icons.drawCentered(ui.ctx, Icons.ui("camera"), x + w / 2F, y + h / 2F - 40, 44, argb(255, TEXT_MUTE));
			Draw.textCenter(ui.ctx, "No screenshots yet", x + w / 2F, y + h / 2F + 10, 16, argb(255, TEXT), true);
			Draw.textCenter(ui.ctx, "Press F2 in game and they show up here.", x + w / 2F, y + h / 2F + 34, 12.5F, argb(255, TEXT_MUTE));
			return;
		}
		while (hovers.size() < shots.size()) {
			hovers.add(new Anim());
		}
		int pad = 22, gap = 14, cols = Math.max(2, (w - pad * 2 + gap) / (250 + gap));
		int cw = (w - pad * 2 - gap * (cols - 1)) / cols, ch = Math.round(cw * 9F / 16F) + 46;
		// lay out: a day header, then that day's pictures
		int cy = 0;
		LocalDate lastDay = null;
		int col = 0;
		int[] posY = new int[shots.size()], posX = new int[shots.size()];
		List<int[]> headers = new ArrayList<>();
		List<String> headerText = new ArrayList<>();
		LocalDate today = LocalDate.now();
		for (int i = 0; i < shots.size(); i++) {
			LocalDate d = Instant.ofEpochMilli(shots.get(i).time()).atZone(ZoneId.systemDefault()).toLocalDate();
			if (!d.equals(lastDay)) {
				if (lastDay != null) {
					cy += ch + gap + 6;
				}
				col = 0;
				headers.add(new int[]{cy});
				headerText.add(d.equals(today) ? "Today" : d.equals(today.minusDays(1)) ? "Yesterday" : DAY.format(d));
				cy += 34;
				lastDay = d;
			} else if (col == cols) {
				col = 0;
				cy += ch + gap;
			}
			posX[i] = col * (cw + gap);
			posY[i] = cy;
			col++;
		}
		int total = cy + ch + pad;
		scroll.setMax(total - h + pad);
		scroll.update(ui);
		int top = y + pad - Math.round(scroll.pos);
		ui.clip(x, y, w, h);
		for (int k = 0; k < headers.size(); k++) {
			int hy = top + headers.get(k)[0];
			if (ui.visible(hy, 24)) {
				Draw.text(ui.ctx, headerText.get(k), x + pad, hy + 4, 13, argb(255, TEXT), true);
			}
		}
		for (int i = 0; i < shots.size(); i++) {
			int sx = x + pad + posX[i], sy = top + posY[i];
			if (!ui.visible(sy, ch)) {
				continue;
			}
			Shot s = shots.get(i);
			boolean over = ui.over(sx, sy, cw, ch) && viewing < 0;
			float hv = hovers.get(i).to(ui, over, 14F);
			int lift = Math.round(hv * 3);
			Draw.shadow(ui.ctx, sx, sy - lift, cw, ch, 10, 10, Math.round(40 + 60 * hv));
			Draw.round(ui.ctx, sx, sy - lift, cw, ch, 10, argb(255, mix(PANEL2, CARD_HOVER, hv)));
			Draw.outline(ui.ctx, sx, sy - lift, cw, ch, 10, 1, argb(255, mix(BORDER, ACCENT, hv)));
			int iw = cw - 12, ih = ch - 46;
			Tex t = thumb(s.file());
			if (t != null) {
				fit(t, sx + 6, sy - lift + 6, iw, ih);
			} else {
				Draw.rect(ui.ctx, sx + 6, sy - lift + 6, iw, ih, argb(255, FIELD));
				Draw.textCenter(ui.ctx, "Loading…", sx + cw / 2F, sy - lift + 6 + ih / 2F, 11, argb(255, TEXT_MUTE));
			}
			String n = s.file().getName().replaceFirst("\\.(png|jpe?g)$", "");
			Draw.text(ui.ctx, Draw.fit(n, 12, cw - 70), sx + 10, sy - lift + ih + 14, 12, argb(255, TEXT), true);
			String meta = TIME.format(Instant.ofEpochMilli(s.time()).atZone(ZoneId.systemDefault())) + "  ·  " + size(s.size());
			Draw.textRight(ui.ctx, meta, sx + cw - 10, sy - lift + ih + 21, 10.5F, argb(255, TEXT_MUTE));
			ui.hit(sx, sy, cw, ch, A_SHOT, i);
		}
		ui.unclip();
		Widgets.scrollbar(ui, x + w - 8, y + 6, h - 12, scroll.pos, scroll.max, h, A_SCROLL, 0);
	}

	private void fit(Tex t, int x, int y, int w, int h) {
		float k = Math.min(w / (float) t.w(), h / (float) t.h());
		int dw = Math.round(t.w() * k), dh = Math.round(t.h() * k);
		int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
		Draw.rect(ui.ctx, x, y, w, h, argb(255, 0x050507));
		ui.ctx.blit(RenderPipelines.GUI_TEXTURED, t.id(), dx, dy, 0F, 0F, dw, dh, t.w(), t.h(), t.w(), t.h(), Draw.fade(0xFFFFFFFF));
	}

	private static String size(long b) {
		return b > 1 << 20 ? String.format(Locale.ROOT, "%.1f MB", b / 1048576.0) : (b / 1024) + " KB";
	}

	// ------------------------------------------------------------------ viewer

	@Override
	protected void drawOverlay() {
		float v = viewAnim.to(ui, viewing >= 0, 12F);
		if (v < 0.01F || viewing >= shots.size() && viewing >= 0) {
			return;
		}
		if (viewing < 0) {
			return;
		}
		Draw.alpha = v;
		Draw.rect(ui.ctx, 0, 0, ui.W, ui.H, argb(215, 0x000000));
		ui.hit(0, 0, ui.W, ui.H, A_VIEW_CLOSE, 0);
		Shot s = shots.get(viewing);
		int bw = Math.round(ui.W * 0.82F), bh = Math.round(ui.H * 0.74F);
		int bx = (ui.W - bw) / 2, by = 40 + Math.round((1F - v) * 20);
		Tex t = bigFile != null && bigFile.equals(s.file()) && big != null ? big : thumb(s.file());
		if (t != null) {
			fit(t, bx, by, bw, bh);
		}
		ui.hit(bx, by, bw, bh, A_NOTHING, 0);
		// arrows
		if (viewing > 0) {
			arrow(bx - 56, by + bh / 2 - 22, "<", A_PREV, hBtn[2]);
		}
		if (viewing < shots.size() - 1) {
			arrow(bx + bw + 12, by + bh / 2 - 22, ">", A_NEXT, hBtn[3]);
		}
		// name + buttons
		int ry = by + bh + 16;
		Draw.text(ui.ctx, s.file().getName(), bx, ry + 6, 14, argb(255, TEXT), true);
		Draw.text(ui.ctx, (viewing + 1) + " of " + shots.size() + "  ·  " + size(s.size()), bx, ry + 26, 11, argb(255, TEXT_MUTE));
		int x = bx + bw;
		if (confirmDelete) {
			x -= smallButtonWidth("Yes, delete it");
			smallButton(x, ry, 36, "Yes, delete it", true, hBtn[4], A_CONFIRM, 0);
			x -= smallButtonWidth("Keep it") + 8;
			smallButton(x, ry, 36, "Keep it", false, hBtn[5], A_CANCEL, 0);
			Draw.textRight(ui.ctx, "Delete this screenshot for good?", x - 12, ry + 18, 12.5F, argb(255, DISABLED));
		} else {
			String[] labels = {"Delete", "Rename", "Copy path", "Open"};
			int[] acts = {A_DELETE, A_RENAME, A_COPY_PATH, A_OPEN};
			for (int i = 0; i < labels.length; i++) {
				x -= smallButtonWidth(labels[i]);
				smallButton(x, ry, 36, labels[i], i == 3, hBtn[4 + i], acts[i], 0);
				x -= 8;
			}
		}
		Draw.alpha = 1F;
	}

	private void arrow(int x, int y, String label, int action, Anim a) {
		float hv = a.to(ui, ui.over(x, y, 44, 44), 14F);
		Draw.circle(ui.ctx, x + 22, y + 22, 22, argb(Math.round(150 + 90 * hv), mix(PANEL2, ACCENT, hv * 0.6F)));
		Draw.textCenter(ui.ctx, label, x + 22, y + 22, 18, argb(255, TEXT), true);
		ui.hit(x, y, 44, 44, action, 0);
	}

	private void open(int i) {
		if (i < 0 || i >= shots.size()) {
			return;
		}
		viewing = i;
		confirmDelete = false;
		loadBig(shots.get(i).file());
	}

	// ------------------------------------------------------------------ actions

	@Override
	protected void onAction(int action, int arg, int button) {
		switch (action) {
			case A_SHOT -> {
				UiSounds.click();
				open(arg);
			}
			case A_FOLDER -> {
				dir().mkdirs();
				Util.getPlatform().openFile(dir());
			}
			case A_REFRESH -> {
				reload();
				UiSounds.click();
			}
			case A_VIEW_CLOSE -> {
				viewing = -1;
				confirmDelete = false;
			}
			case A_PREV -> open(viewing - 1);
			case A_NEXT -> open(viewing + 1);
			case A_OPEN -> {
				if (viewing >= 0) {
					Util.getPlatform().openFile(shots.get(viewing).file());
				}
			}
			case A_COPY_PATH -> {
				if (viewing >= 0) {
					Minecraft.getInstance().keyboardHandler.setClipboard(shots.get(viewing).file().getAbsolutePath());
					UiSounds.click();
				}
			}
			case A_RENAME -> {
				if (viewing >= 0) {
					Minecraft.getInstance().gui.setScreen(new RenameShotScreen(this, shots.get(viewing).file()));
					viewing = -1;
				}
			}
			case A_DELETE -> confirmDelete = true;
			case A_CANCEL -> confirmDelete = false;
			case A_CONFIRM -> {
				if (viewing >= 0) {
					File f = shots.get(viewing).file();
					if (inside(f) && f.delete()) {
						forget(f);
						shots.remove(viewing);
						hovers.clear();
						if (shots.isEmpty()) {
							viewing = -1;
						} else {
							open(Math.min(viewing, shots.size() - 1));
						}
					}
				}
				confirmDelete = false;
			}
			default -> {
			}
		}
	}

	@Override
	public void onDrag(int action, int arg, double mx, double my) {
		if (action == A_SCROLL) {
			scroll.jump(Widgets.scrollFor(my, wy + HEAD_H + 7, wh - HEAD_H - 13, scroll.max, wh - HEAD_H));
		}
	}

	/** Only files directly in the game's screenshots folder are touched. */
	private static boolean inside(File f) {
		try {
			return f.getCanonicalFile().getParentFile().equals(dir().getCanonicalFile());
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void init() {
		super.init();
		if (pool.isShutdown()) {
			pool = newPool();
			loading.clear();
			bigFile = null;
		}
		reload(); // back from renaming: names may have changed
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (viewing >= 0) {
			switch (input.key()) {
				case GLFW.GLFW_KEY_ESCAPE -> {
					viewing = -1;
					return true;
				}
				case GLFW.GLFW_KEY_LEFT -> {
					open(viewing - 1);
					return true;
				}
				case GLFW.GLFW_KEY_RIGHT -> {
					open(viewing + 1);
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
		if (viewing >= 0) {
			open(v > 0 ? viewing - 1 : viewing + 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, h, v);
	}

	@Override
	public void removed() {
		super.removed();
		Minecraft mc = Minecraft.getInstance();
		for (Tex t : thumbs.values()) {
			mc.getTextureManager().release(t.id());
		}
		thumbs.clear();
		if (big != null) {
			mc.getTextureManager().release(big.id());
			big = null;
		}
		pool.shutdownNow();
	}

	// ------------------------------------------------------------------ loading pictures (off the game thread)

	private Tex thumb(File f) {
		Tex t = thumbs.get(f);
		if (t != null || loading.contains(f) || pool.isShutdown()) {
			return t;
		}
		loading.add(f);
		pool.submit(() -> load(f, true));
		return null;
	}

	private void loadBig(File f) {
		if (f.equals(bigFile)) {
			return;
		}
		bigFile = f;
		if (!pool.isShutdown()) {
			pool.submit(() -> load(f, false));
		}
	}

	private void load(File f, boolean small) {
		Minecraft mc = Minecraft.getInstance();
		try {
			NativeImage img;
			try (InputStream in = Files.newInputStream(f.toPath())) {
				img = NativeImage.read(in);
			}
			NativeImage use = img;
			if (small && img.getWidth() > THUMB_W) {
				int tw = THUMB_W, th = Math.max(1, Math.round(img.getHeight() * (THUMB_W / (float) img.getWidth())));
				use = new NativeImage(tw, th, false);
				img.resizeSubRectTo(0, 0, img.getWidth(), img.getHeight(), use);
				img.close();
			}
			NativeImage done = use;
			mc.execute(() -> {
				if (pool.isShutdown()) {
					done.close();
					return;
				}
				Identifier id = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "shots/" + (small ? "t" : "b") + (texCounter++));
				try {
					DynamicTexture tex = new DynamicTexture(id::toString, done);
					mc.getTextureManager().register(id, tex);
				} catch (RuntimeException e) {
					done.close();
					return;
				}
				Tex t = new Tex(id, done.getWidth(), done.getHeight());
				if (small) {
					thumbs.put(f, t);
					loading.remove(f);
				} else if (f.equals(bigFile)) {
					if (big != null) {
						mc.getTextureManager().release(big.id());
					}
					big = t;
				} else {
					mc.getTextureManager().release(id);
				}
			});
		} catch (Throwable e) {
			CloverClient.LOGGER.warn("Couldn't load screenshot {}: {}", f.getName(), e.toString());
		}
	}

	private void forget(File f) {
		Tex t = thumbs.remove(f);
		if (t != null) {
			Minecraft.getInstance().getTextureManager().release(t.id());
		}
		if (f.equals(bigFile) && big != null) {
			Minecraft.getInstance().getTextureManager().release(big.id());
			big = null;
			bigFile = null;
		}
	}

}
