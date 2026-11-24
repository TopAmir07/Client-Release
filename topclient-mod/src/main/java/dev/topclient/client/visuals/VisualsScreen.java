package dev.topclient.client.visuals;

import dev.topclient.client.TopClient;
import dev.topclient.client.clover.ui.UiSounds;
import dev.topclient.client.gui.Backdrop;
import dev.topclient.client.gui.Draw;
import dev.topclient.client.gui.Icons;
import dev.topclient.client.gui.ItemIcons;
import dev.topclient.client.gui.Pictures;
import dev.topclient.client.gui.UiFont;
import dev.topclient.client.module.modules.client.ClickGUI;
import dev.topclient.client.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public final class VisualsScreen extends Screen {

	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
		boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + w && py < y + h;
		}
	}

	private static final class Flake {
		float x, y, vy, vx, rot, vr, size;
		String key;
	}

	private enum Tone { INFO, OK, ERROR }

	private enum Sort { NEW, AZ }

	private static final String EQUIPPED = "@equipped";
	private static final String PRESETS = "@presets";
	private static final String CUSTOM = "@custom";
	private static final int HEADER = 66;
	private static final int RAIL = 232;

	private static String lastPage = EQUIPPED;

	private final Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private final Map<String, Float> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, String> picks = new LinkedHashMap<>();
	private final Map<String, List<VisualsData.Style>> lists = new HashMap<>();
	private final List<Flake> flakes = new ArrayList<>();
	private final Random random = new Random();

	private VisualsLook.Palette c;
	private float ui = 1F;
	private double mx, my;
	private long lastFrame;
	private float dt;
	private String hoverId = "";
	private Object dataSeen;
	private String page = lastPage;
	private long pageAt = System.currentTimeMillis();
	private Sort sort = Sort.NEW;

	private String query = "";
	private String pathText = "";
	private String hexText = "";
	private String focus;
	private List<VisualsData.Style> searchList = List.of();
	private String searchFor = "";

	private float scroll, scrollTarget, railScroll, railTarget;
	private int contentMax, railMax;
	private int viewX, viewY, viewW, viewH, railY, railH;

	private VisualsData.Style modal;
	private List<VisualsData.Style> modalList = List.of();
	private long modalAt;

	private volatile boolean busy;
	private volatile String toast;
	private volatile Tone toastTone = Tone.INFO;
	private volatile long toastAt;

	private Consumer<Float> dragSet;
	private int dragX, dragW;
	private String decoFor;

	public VisualsScreen(Screen parent) {
		super(Component.literal("TopClient Visuals"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		VisualsLook.load();
		if (picks.isEmpty()) {
			picks.putAll(VisualsPack.applied());
		}
		hexText = VisualsLook.hex(VisualsLook.accent).substring(1);
		VisualsData.forceReload();
		UiSounds.open();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		UiSounds.close();
		lastPage = page;
		VisualsLook.save();
		TopClient.INSTANCE.getProfileManager().saveProfile();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	private void say(String text, Tone t) {
		toast = text;
		toastTone = t;
		toastAt = System.currentTimeMillis();
	}

	private float anim(String key, float target, float speed) {
		float v = anims.getOrDefault(key, target);
		v += (target - v) * (1F - (float) Math.exp(-speed * dt));
		if (Math.abs(target - v) < 0.002F) {
			v = target;
		}
		anims.put(key, v);
		return v;
	}

	private float animFrom(String key, float start, float target, float speed) {
		if (!anims.containsKey(key)) {
			anims.put(key, start);
		}
		return anim(key, target, speed);
	}

	private boolean over(int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private boolean overView(int x, int y, int w, int h) {
		return over(x, y, w, h) && mx >= viewX && my >= viewY && mx < viewX + viewW && my < viewY + viewH;
	}

	private static float ease(float t) {
		t = Math.max(0F, Math.min(1F, t));
		return 1F - (float) Math.pow(1F - t, 3);
	}

	private static float back(float t) {
		t = Math.max(0F, Math.min(1F, t));
		float k = 1.6F;
		float q = t - 1F;
		return 1F + (k + 1F) * q * q * q + k * q * q;
	}

	private void hover(String id) {
		hoverId = id;
	}

	private void go(String to) {
		if (!to.equals(page)) {
			page = to;
			pageAt = System.currentTimeMillis();
			scroll = scrollTarget = 0;
			UiSounds.click();
		}
		query = "";
		if ("search".equals(focus)) {
			focus = null;
		}
	}

	private void refreshData() {
		Object now = VisualsData.styles();
		if (now != dataSeen) {
			dataSeen = now;
			lists.clear();
			searchFor = "\u0000";
			if (!VisualsData.styles().isEmpty()) {
				picks.entrySet().removeIf(e -> VisualsData.style(e.getValue()) == null);
			}
			if (!page.startsWith("@") && VisualsData.category(page) == null) {
				page = EQUIPPED;
			}
		}
	}

	private List<VisualsData.Style> list(String category) {
		String key = category + "|" + sort;
		List<VisualsData.Style> have = lists.get(key);
		if (have == null) {
			have = new ArrayList<>(VisualsData.in(category));
			if (sort == Sort.AZ) {
				have.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
			}
			lists.put(key, have);
		}
		return have;
	}

	private List<VisualsData.Style> search() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (!q.equals(searchFor)) {
			searchFor = q;
			List<VisualsData.Style> out = new ArrayList<>();
			for (VisualsData.Style s : VisualsData.styles()) {
				VisualsData.Category cat = VisualsData.category(s.category());
				String hay = (s.name() + " " + s.id() + " " + (s.by() == null ? "" : s.by()) + " " + (cat == null ? "" : cat.name())).toLowerCase(Locale.ROOT);
				if (hay.contains(q)) {
					out.add(s);
				}
			}
			searchList = out;
		}
		return searchList;
	}

	private int changes() {
		Map<String, String> applied = VisualsPack.applied();
		int n = 0;
		for (Map.Entry<String, String> e : picks.entrySet()) {
			if (!e.getValue().equals(applied.get(e.getKey()))) {
				n++;
			}
		}
		for (String k : applied.keySet()) {
			if (!picks.containsKey(k)) {
				n++;
			}
		}
		return n;
	}

	private void toggle(VisualsData.Style s) {
		if (s.id().equals(picks.get(s.category()))) {
			picks.remove(s.category());
			UiSounds.toggle(false);
		} else {
			picks.put(s.category(), s.id());
			UiSounds.toggle(true);
		}
	}

	private void apply() {
		if (busy) {
			return;
		}
		busy = true;
		Map<String, String> want = new LinkedHashMap<>(picks);
		say("Building your pack...", Tone.INFO);
		UiSounds.click();
		Thread t = new Thread(() -> {
			try {
				VisualsPack.Result r = VisualsPack.build(want);
				Minecraft.getInstance().execute(() -> {
					try {
						String problem = VisualsPack.activate(want);
						if (problem != null) {
							say(problem, Tone.ERROR);
							UiSounds.toggle(false);
							return;
						}
						if (r.skipped() > 0) {
							say(r.skipped() + " style(s) failed the safety check and were skipped. Run the installer again.", Tone.ERROR);
						} else if (want.isEmpty()) {
							say("All styles removed", Tone.OK);
						} else {
							say("Applied " + r.applied() + " style" + (r.applied() == 1 ? "" : "s") + ". Reloading textures...", Tone.OK);
						}
						UiSounds.toggle(true);
					} catch (Exception e) {
						say("Could not turn the pack on", Tone.ERROR);
					} finally {
						busy = false;
					}
				});
			} catch (Exception e) {
				Minecraft.getInstance().execute(() -> {
					say("Could not build the pack", Tone.ERROR);
					UiSounds.toggle(false);
					busy = false;
				});
			}
		}, "TopClient visuals apply");
		t.setDaemon(true);
		t.start();
	}

	private void undo() {
		picks.clear();
		picks.putAll(VisualsPack.applied());
		say("Back to what is applied", Tone.INFO);
		UiSounds.click();
	}

	private void shuffle(String category) {
		List<VisualsData.Style> l = VisualsData.in(category);
		if (!l.isEmpty()) {
			picks.put(category, l.get(random.nextInt(l.size())).id());
		}
	}

	private void openFolder(Path p) {
		try {
			Files.createDirectories(p);
			Util.getPlatform().openFile(p.toFile());
		} catch (Exception e) {
			say("Could not open the folder", Tone.ERROR);
		}
	}

	private void addPicture(Path p) {
		try {
			String err = VisualsLook.addPicture(p);
			if (err != null) {
				say(err, Tone.ERROR);
				UiSounds.toggle(false);
			} else {
				say("Picture added", Tone.OK);
				UiSounds.toggle(true);
			}
		} catch (Exception e) {
			say("Could not copy that picture", Tone.ERROR);
		}
	}

	private void pickPicture() {
		Thread t = new Thread(() -> {
			String path;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(3);
				filters.put(stack.UTF8("*.png"));
				filters.put(stack.UTF8("*.jpg"));
				filters.put(stack.UTF8("*.jpeg"));
				filters.flip();
				path = TinyFileDialogs.tinyfd_openFileDialog("Choose a picture", "", filters, "Pictures (*.png, *.jpg)", false);
			} catch (Throwable e) {
				path = null;
			}
			if (path != null) {
				Path p = Path.of(path);
				Minecraft.getInstance().execute(() -> addPicture(p));
			}
		}, "TopClient visuals picture");
		t.setDaemon(true);
		t.start();
	}

	private void addTypedPath() {
		String s = pathText.trim();
		if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
			s = s.substring(1, s.length() - 1);
		}
		if (s.isEmpty()) {
			say("Paste the path of a PNG or JPG first", Tone.ERROR);
			return;
		}
		try {
			addPicture(Path.of(s));
			pathText = "";
		} catch (Exception e) {
			say("That path is not valid", Tone.ERROR);
		}
		focus = null;
	}

	private void useHex() {
		int v = VisualsLook.parseHex(hexText, -1);
		if (v < 0) {
			say("Type a colour like 75D4FF", Tone.ERROR);
			UiSounds.toggle(false);
			return;
		}
		VisualsLook.accent = v;
		VisualsLook.save();
		hexText = VisualsLook.hex(v).substring(1);
		focus = null;
		UiSounds.toggle(true);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		int added = 0;
		for (Path p : paths) {
			String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
			if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
				addPicture(p);
				added++;
			}
		}
		if (added == 0) {
			say("Drop a PNG or JPG picture", Tone.ERROR);
		}
	}

	private int a(int alpha, int rgb) {
		return Draw.argb(alpha, rgb);
	}

	private int surfaceAlpha() {
		return VisualsLook.picturePath() != null || c.glass ? 200 : 255;
	}

	private void box(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int argb) {
		if (r <= 0) {
			Draw.rect(ctx, x, y, w, h, argb);
		} else {
			Draw.round(ctx, x, y, w, h, r, argb);
		}
	}

	private void edge(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int argb) {
		if (c.flat) {
			return;
		}
		if (c.minecraft) {
			Draw.rect(ctx, x, y, w, 1, a(255, 0x6E6E6E));
			Draw.rect(ctx, x, y, 1, h, a(255, 0x6E6E6E));
			Draw.rect(ctx, x, y + h - 1, w, 1, a(255, 0x111111));
			Draw.rect(ctx, x + w - 1, y, 1, h, a(255, 0x111111));
			return;
		}
		Draw.outline(ctx, x, y, w, h, r, 1, argb);
	}

	private void fill2(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int top, int bottom) {
		if (r <= 0 || !c.gradient) {
			box(ctx, x, y, w, h, r, top);
		} else {
			Draw.roundGradient(ctx, x, y, w, h, r, top, bottom);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		dt = lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
		lastFrame = now;
		Previews.tick();
		VisualsData.reloadIfChanged();
		refreshData();
		c = VisualsLook.palette();

		double sf = mc.getWindow().getGuiScale();
		int fbW = mc.getWindow().getWidth();
		int fbH = mc.getWindow().getHeight();
		ui = (float) (Math.max(0.6, Math.min(1.8, fbH / 1000.0)) * ClickGUI.guiScale.getValue() * VisualsLook.size.scale);
		Draw.ui = ui;
		Draw.forceFamily = c.minecraft ? UiFont.Family.MINECRAFT : null;
		mx = mouseX * sf / ui;
		my = mouseY * sf / ui;
		int W = Math.round(fbW / ui);
		int H = Math.round(fbH / ui);
		hits.clear();
		String hoverBefore = hoverId;
		hoverId = "";
		scroll += (scrollTarget - scroll) * (1F - (float) Math.exp(-20F * dt));
		railScroll += (railTarget - railScroll) * (1F - (float) Math.exp(-20F * dt));

		float open = ease((System.currentTimeMillis() - openedAt) / 300F);
		Draw.alpha = open;
		RenderUtils.unscaledProjection(ctx);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.scale(ui, ui);
		try {
			Path pic = VisualsLook.picturePath();
			Draw.rect(ctx, 0, 0, W, H, a(150, c.backdrop));
			if (pic == null && !c.flat && !c.minecraft) {
				float t = (System.currentTimeMillis() - openedAt) / 1000F;
				Backdrop.blob(ctx, W * (0.22F + 0.05F * (float) Math.sin(t * 0.3F)), H * 0.3F, H * 0.45F, a(55, c.accent));
				Backdrop.blob(ctx, W * (0.8F + 0.04F * (float) Math.cos(t * 0.25F)), H * 0.75F, H * 0.5F, a(45, c.accent2));
			}

			int ww = Math.min(W - 40, 1280);
			int wh = Math.min(H - 40, 790);
			int wx = (W - ww) / 2;
			int wy = (H - wh) / 2;
			float z = 0.95F + 0.05F * back(Math.min(1F, (System.currentTimeMillis() - openedAt) / 400F));
			m.pushMatrix();
			m.translate(W / 2F, H / 2F + (1F - open) * 16F);
			m.scale(z, z);
			m.translate(-W / 2F, -H / 2F);
			int r = c.radius;
			if (!c.flat) {
				Draw.shadow(ctx, wx, wy, ww, wh, Math.max(r, 2), 34, 140);
			}
			VisualsPreviewBg.draw(ctx, this, pic, wx, wy, ww, wh, r);
			edge(ctx, wx, wy, ww, wh, r, a(255, c.border));
			if (c.gradient) {
				Draw.gradientH(ctx, wx + r, wy, ww - r * 2, 2, a(220, c.accent), a(0, c.accent2));
			}
			decorations(ctx, wx, wy, ww, wh);
			header(ctx, wx, wy, ww);
			rail(ctx, wx, wy + HEADER, RAIL, wh - HEADER);
			viewX = wx + RAIL;
			viewY = wy + HEADER;
			viewW = ww - RAIL;
			viewH = wh - HEADER;
			content(ctx, viewX, viewY, viewW, viewH);
			applyBar(ctx, viewX, viewY, viewW, viewH);
			toast(ctx, viewX, viewY, viewW, viewH);
			m.popMatrix();
			if (modal != null) {
				modal(ctx, W, H);
			}
		} finally {
			m.popMatrix();
			RenderUtils.scaledProjection(ctx);
			Draw.alpha = 1F;
			Draw.forceFamily = null;
		}
		if (!hoverId.isEmpty() && !hoverId.equals(hoverBefore)) {
			UiSounds.hover();
		}
	}

	static final class VisualsPreviewBg {
		static void draw(GuiGraphicsExtractor ctx, VisualsScreen s, Path pic, int x, int y, int w, int h, int r) {
			VisualsLook.Palette c = s.c;
			Previews.Tex tex = pic == null ? null : Previews.picture(pic, 2048);
			if (tex != null) {
				ctx.enableScissor(x, y, x + w, y + h);
				s.cover(ctx, tex, x, y, w, h, 0xFFFFFFFF);
				Draw.rect(ctx, x, y, w, h, Draw.argb(Math.round(VisualsLook.darken * 2.55F), 0x000000));
				ctx.disableScissor();
				return;
			}
			int top = Draw.argb(c.alpha, c.gradient ? Draw.mix(c.window, c.accent, 0.08F) : c.window);
			int bot = Draw.argb(c.alpha, c.window);
			s.fill2(ctx, x, y, w, h, r, top, bot);
		}
	}

	void cover(GuiGraphicsExtractor ctx, Previews.Tex tex, int x, int y, int w, int h, int color) {
		float k = Math.max(w / (float) tex.w(), h / (float) tex.h());
		int rw = Math.max(1, Math.round(w / k));
		int rh = Math.max(1, Math.round(h / k));
		int u = (tex.w() - rw) / 2;
		int v = (tex.h() - rh) / 2;
		ctx.blit(RenderPipelines.GUI_TEXTURED, tex.id(), x, y, u, v, w, h, rw, rh, tex.w(), tex.h(), Draw.fade(color));
	}

	private void contain(GuiGraphicsExtractor ctx, Previews.Tex tex, int x, int y, int w, int h, int color) {
		float k = Math.min(w / (float) tex.w(), h / (float) tex.h());
		int dw = Math.round(tex.w() * k);
		int dh = Math.round(tex.h() * k);
		ctx.blit(RenderPipelines.GUI_TEXTURED, tex.id(), x + (w - dw) / 2, y + (h - dh) / 2, 0F, 0F, dw, dh, tex.w(), tex.h(), tex.w(), tex.h(), Draw.fade(color));
	}

	private String[] decoKeys(String deco) {
		return switch (deco) {
			case "halloween" -> new String[]{"1f383", "1f987", "1f47b"};
			case "snow" -> new String[]{"2744"};
			case "sakura" -> new String[]{"1f338"};
			case "stars" -> new String[]{"2728", "2b50"};
			case "bubbles" -> new String[]{"1fae7"};
			case "embers" -> new String[]{"1f525"};
			default -> new String[0];
		};
	}

	private void decorations(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		String deco = VisualsLook.theme().deco();
		if (!VisualsLook.decorations || deco == null) {
			flakes.clear();
			decoFor = null;
			return;
		}
		if (!deco.equals(decoFor)) {
			flakes.clear();
			decoFor = deco;
		}
		String[] keys = decoKeys(deco);
		if (keys.length == 0) {
			return;
		}
		boolean rise = deco.equals("bubbles") || deco.equals("embers");
		while (flakes.size() < 16) {
			Flake f = new Flake();
			f.key = keys[random.nextInt(keys.length)];
			f.x = random.nextFloat();
			f.y = flakes.size() < 8 && anims.get("decoInit") == null ? random.nextFloat() : (rise ? 1.05F : -0.05F);
			f.vy = (0.025F + random.nextFloat() * 0.035F) * (rise ? -1 : 1);
			f.vx = (random.nextFloat() - 0.5F) * 0.01F;
			f.rot = random.nextFloat() * 6.28F;
			f.vr = (random.nextFloat() - 0.5F) * 1.2F;
			f.size = 14 + random.nextFloat() * 14;
			flakes.add(f);
		}
		anims.put("decoInit", 1F);
		ctx.enableScissor(x, y, x + w, y + h);
		for (int i = flakes.size() - 1; i >= 0; i--) {
			Flake f = flakes.get(i);
			f.y += f.vy * dt;
			f.x += f.vx * dt + (float) Math.sin((float) ((System.currentTimeMillis() % 3_600_000L) / 1000.0) + i) * 0.0004F;
			f.rot += f.vr * dt;
			if (f.y > 1.1F || f.y < -0.1F) {
				flakes.remove(i);
				continue;
			}
			float edgeFade = Math.min(1F, Math.min(f.y + 0.05F, 1.05F - f.y) * 8F);
			Pictures.draw(ctx, f.key, x + f.x * w, y + f.y * h, f.size, f.rot, Math.round(90 * Math.max(0F, edgeFade)));
		}
		ctx.disableScissor();
	}

	private void header(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int sa = VisualsLook.picturePath() != null ? 120 : (c.glass ? 90 : 255);
		if (!c.flat) {
			Draw.rect(ctx, x + Math.max(c.radius, 1), y + HEADER - 1, w - Math.max(c.radius, 1) * 2, 1, a(255, c.border));
		}
		if (c.minecraft || c.flat || c.alpha < 255 || sa < 255) {
			box(ctx, x + 1, y + 1, w - 2, HEADER - 2, Math.max(0, c.radius - 1), a(Math.min(sa, c.alpha) / 2, c.header));
		}
		VisualsLook.Theme th = VisualsLook.theme();
		float bob = (float) Math.sin((System.currentTimeMillis() - openedAt) / 600F) * 1.5F;
		box(ctx, x + 18, y + 13, 40, 40, c.small + 2, a(255, Draw.mix(c.surface, c.accent, 0.25F)));
		if (!Pictures.draw(ctx, th.icon(), x + 38, y + 33 + bob, 26, 0F, 255)) {
			ItemIcons.draw(ctx, Items.PAINTING, x + 38, y + 33, 24);
		}
		Draw.text(ctx, "TopClient Visuals", x + 70, y + 14, 21, a(255, c.text), true);
		int total = VisualsData.styles().size();
		String sub = total == 0 ? "Not installed yet" : total + " styles  \u00B7  " + picks.size() + " equipped  \u00B7  works offline";
		Draw.text(ctx, sub, x + 70, y + 39, 12, a(255, c.textDim));

		int fw = Math.min(360, w - RAIL - 360);
		int fx = x + w - fw - 70;
		if (fw > 120) {
			field(ctx, "search", fx, y + 14, fw, 38, query, "Search every style", true);
		}
		int cx = x + w - 52;
		boolean ch = over(cx, y + 17, 32, 32);
		float chv = anim("close", ch ? 1F : 0F, 18F);
		box(ctx, cx, y + 17, 32, 32, c.small, a(Math.round(255 * chv), c.surface2));
		Icons.drawCentered(ctx, Icons.ui("close"), cx + 16, y + 33, 18, a(255, Draw.mix(c.textDim, c.text, chv)));
		if (ch) {
			hover("close");
		}
		hits.add(new Hit(cx, y + 17, 32, 32, this::onClose, null));
	}

	private void railItem(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String label, String count, Runnable icon, boolean dot) {
		boolean sel = page.equals(id) && query.isBlank();
		boolean hov = over(x, y, w, h) && my >= railY && my < railY + railH;
		float hv = anim("rail.h." + id, hov ? 1F : 0F, 18F);
		float sv = anim("rail.s." + id, sel ? 1F : 0F, 14F);
		if (sv > 0.01F || hv > 0.01F) {
			int fillc = Draw.mix(c.surface, Draw.mix(c.surface2, c.accent, 0.18F), sv);
			box(ctx, x, y, w, h, c.small, a(Math.round(255 * Math.max(sv, hv * 0.7F)), fillc));
		}
		if (sv > 0.01F) {
			int bh = Math.round((h - 16) * sv);
			box(ctx, x, y + (h - bh) / 2, 3, bh, c.minecraft ? 0 : 2, a(255, c.accent));
		}
		ctx.pose().pushMatrix();
		ctx.pose().translate(hv * 3F, 0F);
		icon.run();
		Draw.textMid(ctx, Draw.fit(label, 13.5F, w - 90, sel), x + 44, y + h / 2F, 13.5F, a(255, Draw.mix(c.textSoft, c.text, Math.max(sv, hv))), sel);
		ctx.pose().popMatrix();
		if (count != null) {
			Draw.textRight(ctx, count, x + w - 12, y + h / 2F, 11.5F, a(255, sel ? c.accent : c.textDim));
		}
		if (dot) {
			Draw.circle(ctx, x + w - 12 - (count == null ? 0 : Draw.width(count, 11.5F) + 10), y + h / 2F, 3.2F, a(255, c.accent));
		}
		if (hov) {
			hover("rail." + id);
		}
		hits.add(new Hit(x, Math.max(y, railY), w, Math.min(y + h, railY + railH) - Math.max(y, railY), () -> go(id), null));
	}

	private void rail(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int sa = surfaceAlpha();
		if (!c.flat || VisualsLook.picturePath() != null) {
			Draw.rect(ctx, x + 1, y, w - 1, h - Math.max(c.radius, 1), a(Math.min(sa, c.alpha) * 2 / 3, c.rail));
		} else {
			Draw.rect(ctx, x + 1, y, w - 1, h - Math.max(c.radius, 1), a(c.alpha, c.rail));
		}
		if (!c.flat) {
			Draw.rect(ctx, x + w - 1, y, 1, h, a(255, c.border));
		}
		int bottomH = 62;
		railY = y + 10;
		railH = h - bottomH - 10;
		int ix = x + 12;
		int iw = w - 24;
		ctx.enableScissor(x, railY, x + w, railY + railH);
		int yy = railY - Math.round(railScroll);
		int start = yy;
		final int ry0 = yy;
		railItem(ctx, EQUIPPED, ix, yy, iw, 38, "Equipped", String.valueOf(picks.size()),
				() -> Icons.drawCentered(ctx, "check-bold", ix + 22, ry0 + 19, 17, a(255, c.accent)), false);
		yy += 42;
		final int ry1 = yy;
		railItem(ctx, PRESETS, ix, yy, iw, 38, "Presets", String.valueOf(VisualsPack.presets().size()),
				() -> Icons.drawCentered(ctx, "star-circle-outline", ix + 22, ry1 + 19, 17, a(255, c.accent)), false);
		yy += 50;
		if (!VisualsData.categories().isEmpty()) {
			Draw.text(ctx, "STYLES", ix + 10, yy, 10.5F, a(255, c.textDim), true);
		}
		yy += 20;
		for (VisualsData.Category cat : VisualsData.categories()) {
			final int ry = yy;
			if (yy + 38 >= railY && yy <= railY + railH) {
				railItem(ctx, cat.id(), ix, yy, iw, 36, cat.name(), String.valueOf(list(cat.id()).size()),
						() -> ItemIcons.draw(ctx, cat.icon(), ix + 22, ry + 18, 19), picks.containsKey(cat.id()));
			}
			yy += 38;
		}
		ctx.disableScissor();
		railMax = Math.max(0, yy - start - railH + 8);
		railTarget = Math.max(0, Math.min(railTarget, railMax));
		if (railMax > 0) {
			float frac = railH / (float) (railH + railMax);
			int bh = Math.max(30, Math.round(railH * frac));
			int by = railY + Math.round((railH - bh) * (railScroll / railMax));
			box(ctx, x + w - 6, by, 3, bh, c.minecraft ? 0 : 2, a(140, c.border));
		}
		int by = y + h - bottomH + 8;
		Draw.rect(ctx, x + 12, by - 8, w - 24, 1, a(255, c.border));
		final int cy = by;
		railY = by;
		railH = 46;
		railItem(ctx, CUSTOM, ix, by, iw, 42, "Customization", null,
				() -> Icons.drawCentered(ctx, "palette-outline", ix + 22, cy + 21, 18, a(255, c.accent)), false);
		railY = y + 10;
		railH = h - bottomH - 10;
	}

	private void content(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		ctx.enableScissor(x, y + 1, x + w, y + h - 1);
		clipping = true;
		int firstHit = hits.size();
		try {
			float in = ease((System.currentTimeMillis() - pageAt) / 260F);
			Draw.alpha = Math.min(Draw.alpha, 0.25F + 0.75F * in);
			ctx.pose().pushMatrix();
			ctx.pose().translate(0F, (1F - in) * 10F);
			int pad = 26;
			int cx = x + pad;
			int cw = w - pad * 2;
			int top = y + 22 - Math.round(scroll);
			int used;
			if (page.equals(CUSTOM) && query.isBlank()) {
				used = customizePage(ctx, cx, top, cw);
			} else if (!VisualsData.installed()) {
				used = emptyState(ctx, cx, top, cw, h);
			} else if (!query.isBlank()) {
				List<VisualsData.Style> res = search();
				used = title(ctx, cx, top, cw, "Results for \"" + query.trim() + "\"", res.size() + " match" + (res.size() == 1 ? "" : "es") + "  \u00B7  Left-click to equip, right-click to look closer", null);
				used += grid(ctx, res, cx, top + used, cw, true);
			} else if (page.equals(EQUIPPED)) {
				used = equippedPage(ctx, cx, top, cw);
			} else if (page.equals(PRESETS)) {
				used = presetsPage(ctx, cx, top, cw);
			} else if (page.equals(CUSTOM)) {
				used = customizePage(ctx, cx, top, cw);
			} else {
				used = categoryPage(ctx, cx, top, cw);
			}
			ctx.pose().popMatrix();
			Draw.alpha = ease((System.currentTimeMillis() - openedAt) / 300F);
			contentMax = Math.max(0, used + 22 + 90 - h);
			scrollTarget = Math.max(0, Math.min(scrollTarget, contentMax));
		} finally {
			clipping = false;
			ctx.disableScissor();
			clipHits(firstHit, x, y, w, h);
		}
		if (contentMax > 0) {
			float frac = h / (float) (h + contentMax);
			int bh = Math.max(40, Math.round((h - 20) * frac));
			int by = y + 10 + Math.round((h - 20 - bh) * (scroll / contentMax));
			box(ctx, x + w - 8, by, 4, bh, c.minecraft ? 0 : 2, a(150, c.border));
		}
	}

	private void clipHits(int from, int x, int y, int w, int h) {
		for (int i = hits.size() - 1; i >= from; i--) {
			Hit t = hits.get(i);
			int x1 = Math.max(t.x(), x);
			int y1 = Math.max(t.y(), y);
			int x2 = Math.min(t.x() + t.w(), x + w);
			int y2 = Math.min(t.y() + t.h(), y + h);
			if (x2 <= x1 || y2 <= y1) {
				hits.remove(i);
			} else if (x1 != t.x() || y1 != t.y() || x2 != t.x() + t.w() || y2 != t.y() + t.h()) {
				hits.set(i, new Hit(x1, y1, x2 - x1, y2 - y1, t.left(), t.right()));
			}
		}
	}

	private int title(GuiGraphicsExtractor ctx, int x, int y, int w, String title, String sub, String note) {
		Draw.text(ctx, Draw.fit(title, 25, w - 380, true), x, y, 25, a(255, c.text), true);
		Draw.text(ctx, Draw.fit(sub, 12.5F, w), x, y + 34, 12.5F, a(255, c.textDim));
		int used = 60;
		if (note != null) {
			box(ctx, x, y + 58, w, 32, c.small, a(200, Draw.mix(c.surface, 0xFBBF24, 0.18F)));
			Draw.textMid(ctx, Draw.fit(note, 12.5F, w - 24), x + 14, y + 74, 12.5F, a(255, 0xFDE68A));
			used += 42;
		}
		return used + 8;
	}

	private int categoryPage(GuiGraphicsExtractor ctx, int x, int y, int w) {
		VisualsData.Category cat = VisualsData.category(page);
		if (cat == null) {
			return 0;
		}
		List<VisualsData.Style> l = list(cat.id());
		String chosen = picks.get(cat.id());
		VisualsData.Style cs = chosen == null ? null : VisualsData.style(chosen);
		String sub = l.size() + " styles  \u00B7  " + (cs == null ? "nothing equipped" : "equipped: " + cs.name()) + "  \u00B7  right-click for a big preview";
		int used = title(ctx, x, y, w, cat.name(), sub, cat.note());
		int bx = x + w;
		bx -= 92;
		button(ctx, "clear", bx, y + 2, 92, 34, "Clear", false, cs != null, () -> {
			picks.remove(cat.id());
			UiSounds.toggle(false);
		});
		bx -= 104;
		button(ctx, "random", bx, y + 2, 96, 34, "Random", false, true, () -> {
			shuffle(cat.id());
			UiSounds.toggle(true);
		});
		bx -= 158;
		segmented(ctx, "sort", bx, y + 2, 150, 34, new String[]{"Newest", "A - Z"}, sort.ordinal(), i -> {
			sort = Sort.values()[i];
		});
		return used + grid(ctx, l, x, y + used, w, false);
	}

	private int grid(GuiGraphicsExtractor ctx, List<VisualsData.Style> l, int x, int y, int w, boolean showCat) {
		if (l.isEmpty()) {
			Draw.textCenter(ctx, "Nothing here", x + w / 2F, y + 60, 14, a(255, c.textDim));
			return 120;
		}
		int gap = 14;
		int cols = Math.max(2, (w + gap) / (168 + gap));
		int cw = (w - gap * (cols - 1)) / cols;
		int chh = cw + 50;
		long since = System.currentTimeMillis() - pageAt;
		for (int i = 0; i < l.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int cx = x + col * (cw + gap);
			int cy = y + row * (chh + gap);
			if (cy + chh < viewY - 10 || cy > viewY + viewH + 10) {
				continue;
			}
			float pop = back(Math.min(1F, (since - Math.min(i, 24) * 18L) / 320F));
			styleCard(ctx, l.get(i), l, cx, cy, cw, chh, showCat, pop);
		}
		int rows = (l.size() + cols - 1) / cols;
		return rows * (chh + gap);
	}

	private void styleCard(GuiGraphicsExtractor ctx, VisualsData.Style s, List<VisualsData.Style> from, int x, int y, int w, int h, boolean showCat, float pop) {
		String id = "card." + s.id();
		boolean on = s.id().equals(picks.get(s.category()));
		boolean hov = overView(x, y, w, h) && modal == null;
		float hv = anim(id, hov ? 1F : 0F, 18F);
		float ov = anim(id + ".on", on ? 1F : 0F, 14F);
		int lift = Math.round(hv * 3);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		float sc = 0.9F + 0.1F * pop;
		m.translate(x + w / 2F, y + h / 2F);
		m.scale(sc, sc);
		m.translate(-(x + w / 2F), -(y + h / 2F));
		float keep = Draw.alpha;
		Draw.alpha = keep * Math.max(0F, Math.min(1F, pop));
		int yy = y - lift;
		if (!c.flat && hv > 0.02F) {
			Draw.shadow(ctx, x, yy, w, h, Math.max(1, c.small), 16, Math.round(110 * hv));
		}
		int fillc = Draw.mix(Draw.mix(c.surface, c.surface2, hv), c.accent, ov * 0.12F);
		box(ctx, x, yy, w, h, c.small, a(surfaceAlpha(), fillc));
		int bc = Draw.mix(Draw.mix(c.border, c.borderHi, hv), c.accent, ov);
		if (ov > 0.02F && !c.minecraft) {
			Draw.outline(ctx, x, yy, w, h, c.small, 2, a(Math.round(255 * Math.max(ov, 0.0F)), c.accent));
		} else {
			edge(ctx, x, yy, w, h, c.small, a(255, bc));
		}
		if (c.minecraft && ov > 0.02F) {
			Draw.outline(ctx, x, yy, w, h, 0, 2, a(255, c.accent));
		}
		int pad = 8;
		int pw = w - pad * 2;
		box(ctx, x + pad, yy + pad, pw, pw, Math.max(0, c.small - 3), Backdrop.previewColor(a(255, c.field)));
		Previews.Tex tex = Previews.get(s);
		if (tex != null) {
			float zoom = 1F + hv * 0.04F;
			int zw = Math.round(pw * zoom);
			int off = (zw - pw) / 2;
			clip(ctx, x + pad, yy + pad, x + pad + pw, yy + pad + pw);
			contain(ctx, tex, x + pad - off, yy + pad - off, zw, zw, 0xFFFFFFFF);
			ctx.disableScissor();
		} else {
			VisualsData.Category cat = VisualsData.category(s.category());
			if (cat != null) {
				ItemIcons.draw(ctx, cat.icon(), x + pad + pw / 2F, yy + pad + pw / 2F, 34);
			}
			shimmer(ctx, x + pad, yy + pad, pw, pw);
		}
		if (s.isNew()) {
			int nw = Draw.width("NEW", 10, true) + 14;
			box(ctx, x + pad + 6, yy + pad + 6, nw, 18, c.minecraft ? 0 : 9, a(255, c.accent));
			Draw.textCenter(ctx, "NEW", x + pad + 6 + nw / 2F, yy + pad + 15, 10, a(255, c.onAccent), true);
		}
		if (ov > 0.02F) {
			float k = back(ov);
			float ccx = x + w - pad - 14;
			float ccy = yy + pad + 14;
			Draw.circle(ctx, ccx, ccy, 11F * k, a(255, c.accent));
			Icons.drawCentered(ctx, "check-bold", ccx, ccy, 14F * k, a(255, c.onAccent));
		}
		Draw.text(ctx, Draw.fit(s.name(), 13.5F, w - 20, true), x + 10, yy + pad + pw + 9, 13.5F, a(255, c.text), true);
		String sub;
		if (showCat) {
			VisualsData.Category cat = VisualsData.category(s.category());
			sub = (cat == null ? s.category() : cat.name()) + (s.by() != null ? "  \u00B7  by " + s.by() : "");
		} else {
			sub = s.by() != null ? "by " + s.by() : (s.added() != null ? "Added " + s.added() : "");
		}
		Draw.text(ctx, Draw.fit(sub, 11.5F, w - 20), x + 10, yy + pad + pw + 27, 11.5F, a(255, c.textDim));
		Draw.alpha = keep;
		m.popMatrix();
		if (hov) {
			hover(id);
		}
		if (hov || (x + w >= viewX && y + h >= viewY && x <= viewX + viewW && y <= viewY + viewH)) {
			int hy = Math.max(y, viewY);
			int hh = Math.min(y + h, viewY + viewH) - hy;
			if (hh > 0) {
				hits.add(new Hit(x, hy, w, hh, () -> toggle(s), () -> openModal(s, from)));
			}
		}
	}

	private boolean clipping;

	private void clip(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
		if (clipping) {
			x1 = Math.max(x1, viewX);
			y1 = Math.max(y1, viewY + 1);
			x2 = Math.min(x2, viewX + viewW);
			y2 = Math.min(y2, viewY + viewH - 1);
			if (y2 <= y1 || x2 <= x1) {
				x2 = x1;
				y2 = y1;
			}
		}
		ctx.enableScissor(x1, y1, x2, y2);
	}

	private void shimmer(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		float t = ((System.currentTimeMillis() % 1400L) / 1400F) * (w + 120) - 60;
		clip(ctx, x, y, x + w, y + h);
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(x + t, y + h / 2F);
		m.scale(0.4F, 1.6F);
		Backdrop.blob(ctx, 0, 0, w * 0.4F, a(28, 0xFFFFFF));
		m.popMatrix();
		ctx.disableScissor();
	}

	private void openModal(VisualsData.Style s, List<VisualsData.Style> from) {
		modal = s;
		modalList = from;
		modalAt = System.currentTimeMillis();
		UiSounds.preview();
	}

	private int equippedPage(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int used = title(ctx, x, y, w, "Equipped", "Your look: one style for each item. Click a tile to change it.", null);
		int bx = x + w - 104;
		button(ctx, "clearAll", bx, y + 2, 104, 34, "Clear all", false, !picks.isEmpty(), () -> {
			picks.clear();
			UiSounds.toggle(false);
		});
		bx -= 140;
		button(ctx, "savePreset", bx, y + 2, 132, 34, "Save preset", false, !picks.isEmpty(), this::savePreset);
		bx -= 132;
		button(ctx, "shuffleAll", bx, y + 2, 124, 34, "Shuffle all", true, true, () -> {
			for (VisualsData.Category cat : VisualsData.categories()) {
				if (!"sky".equals(cat.id())) {
					shuffle(cat.id());
				}
			}
			UiSounds.toggle(true);
			say("Shuffled. Press Apply to try it", Tone.INFO);
		});
		List<VisualsData.Category> cats = VisualsData.categories();
		int gap = 14;
		int cols = Math.max(2, (w + gap) / (200 + gap));
		int cw = (w - gap * (cols - 1)) / cols;
		int ch = 96;
		int yy = y + used;
		long since = System.currentTimeMillis() - pageAt;
		for (int i = 0; i < cats.size(); i++) {
			VisualsData.Category cat = cats.get(i);
			int cx = x + (i % cols) * (cw + gap);
			int cy = yy + (i / cols) * (ch + gap);
			if (cy + ch < viewY || cy > viewY + viewH) {
				continue;
			}
			float pop = back(Math.min(1F, (since - Math.min(i, 24) * 16L) / 300F));
			String sid = picks.get(cat.id());
			VisualsData.Style s = sid == null ? null : VisualsData.style(sid);
			String id = "eq." + cat.id();
			boolean hov = overView(cx, cy, cw, ch) && modal == null;
			float hv = anim(id, hov ? 1F : 0F, 18F);
			float keep = Draw.alpha;
			Draw.alpha = keep * Math.max(0F, Math.min(1F, pop));
			int lift = Math.round(hv * 3);
			int top = cy - lift;
			if (!c.flat && hv > 0.02F) {
				Draw.shadow(ctx, cx, top, cw, ch, Math.max(1, c.small), 14, Math.round(100 * hv));
			}
			box(ctx, cx, top, cw, ch, c.small, a(surfaceAlpha(), Draw.mix(c.surface, c.surface2, hv)));
			edge(ctx, cx, top, cw, ch, c.small, a(255, s != null ? Draw.mix(c.border, c.accent, 0.6F + 0.4F * hv) : Draw.mix(c.border, c.borderHi, hv)));
			int ps = ch - 20;
			box(ctx, cx + 10, top + 10, ps, ps, Math.max(0, c.small - 3), Backdrop.previewColor(a(255, c.field)));
			Previews.Tex tex = s == null ? null : Previews.get(s);
			if (tex != null) {
				contain(ctx, tex, cx + 10, top + 10, ps, ps, 0xFFFFFFFF);
			} else {
				ItemIcons.draw(ctx, cat.icon(), cx + 10 + ps / 2F, top + 10 + ps / 2F, 30);
			}
			int tx = cx + ps + 22;
			int tw = cw - ps - 32;
			Draw.text(ctx, Draw.fit(cat.name().toUpperCase(Locale.ROOT), 10.5F, tw, true), tx, top + 18, 10.5F, a(255, c.textDim), true);
			Draw.text(ctx, Draw.fit(s == null ? "Default" : s.name(), 14.5F, tw, true), tx, top + 36, 14.5F, a(255, s == null ? c.textDim : c.text), true);
			Draw.text(ctx, Draw.fit(s == null ? "Click to pick one" : (s.by() != null ? "by " + s.by() : "Click to change"), 11.5F, tw), tx, top + 60, 11.5F, a(255, s == null ? c.accent : c.textDim));
			Draw.alpha = keep;
			if (hov) {
				hover(id);
			}
			final VisualsData.Style st = s;
			int hy = Math.max(cy, viewY);
			int hh = Math.min(cy + ch, viewY + viewH) - hy;
			if (hh > 0) {
				hits.add(new Hit(cx, hy, cw, hh, () -> go(cat.id()), st == null ? null : () -> openModal(st, VisualsData.in(cat.id()))));
			}
		}
		return used + ((cats.size() + cols - 1) / cols) * (ch + gap);
	}

	private void savePreset() {
		int n = VisualsPack.presets().size() + 1;
		String name = "Look " + n;
		while (true) {
			boolean taken = false;
			for (VisualsPack.Preset p : VisualsPack.presets()) {
				if (p.name().equalsIgnoreCase(name)) {
					taken = true;
					break;
				}
			}
			if (!taken) {
				break;
			}
			name = "Look " + (++n);
		}
		VisualsPack.savePreset(name, picks);
		say("Saved as " + name, Tone.OK);
		UiSounds.toggle(true);
	}

	private int presetsPage(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int used = title(ctx, x, y, w, "Presets", "Save your whole look and switch back to it in one click. Stored only on this PC.", null);
		button(ctx, "savePreset2", x + w - 190, y + 2, 190, 34, "Save current look", true, !picks.isEmpty(), this::savePreset);
		List<VisualsPack.Preset> list = VisualsPack.presets();
		int yy = y + used;
		if (list.isEmpty()) {
			box(ctx, x, yy, w, 120, c.small, a(surfaceAlpha(), c.surface));
			edge(ctx, x, yy, w, 120, c.small, a(255, c.border));
			Pictures.draw(ctx, "2b50", x + w / 2F, yy + 40, 30, 0F, 255);
			Draw.textCenter(ctx, "No presets yet", x + w / 2F, yy + 72, 15, a(255, c.text), true);
			Draw.textCenter(ctx, "Equip some styles, then press Save current look", x + w / 2F, yy + 94, 12, a(255, c.textDim));
			return used + 130;
		}
		for (int i = 0; i < list.size(); i++) {
			VisualsPack.Preset p = list.get(i);
			int rh = 84;
			int ry = yy + i * (rh + 12);
			if (ry + rh < viewY || ry > viewY + viewH) {
				continue;
			}
			String id = "preset." + i;
			boolean hov = overView(x, ry, w, rh);
			float hv = anim(id, hov ? 1F : 0F, 18F);
			box(ctx, x, ry, w, rh, c.small, a(surfaceAlpha(), Draw.mix(c.surface, c.surface2, hv)));
			edge(ctx, x, ry, w, rh, c.small, a(255, Draw.mix(c.border, c.borderHi, hv)));
			Draw.text(ctx, Draw.fit(p.name(), 16, 220, true), x + 18, ry + 20, 16, a(255, c.text), true);
			Draw.text(ctx, p.picks().size() + " styles", x + 18, ry + 46, 12, a(255, c.textDim));
			int px = x + 250;
			int ps = 56;
			int room = Math.max(0, (w - 250 - 220) / (ps + 8));
			int k = 0;
			for (String sid : p.picks().values()) {
				if (k >= room) {
					break;
				}
				VisualsData.Style s = VisualsData.style(sid);
				if (s == null) {
					continue;
				}
				box(ctx, px + k * (ps + 8), ry + 14, ps, ps, Math.max(0, c.small - 3), Backdrop.previewColor(a(255, c.field)));
				Previews.Tex tex = Previews.get(s);
				if (tex != null) {
					contain(ctx, tex, px + k * (ps + 8) + 3, ry + 17, ps - 6, ps - 6, 0xFFFFFFFF);
				}
				k++;
			}
			final VisualsPack.Preset pick = p;
			button(ctx, id + ".del", x + w - 96, ry + 25, 80, 34, "Delete", false, true, () -> {
				VisualsPack.deletePreset(pick.name());
				UiSounds.toggle(false);
			});
			button(ctx, id + ".load", x + w - 196, ry + 25, 92, 34, "Load", true, true, () -> {
				picks.clear();
				for (Map.Entry<String, String> e : pick.picks().entrySet()) {
					if (VisualsData.style(e.getValue()) != null) {
						picks.put(e.getKey(), e.getValue());
					}
				}
				say("Loaded " + pick.name() + ". Press Apply to wear it", Tone.OK);
				UiSounds.toggle(true);
			});
		}
		return used + list.size() * 96;
	}

	private int emptyState(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int bw = Math.min(w, 640);
		int bx = x + (w - bw) / 2;
		int by = y + 40;
		int bh = 330;
		box(ctx, bx, by, bw, bh, c.radius, a(surfaceAlpha(), c.surface));
		edge(ctx, bx, by, bw, bh, c.radius, a(255, c.border));
		float bob = (float) Math.sin(System.currentTimeMillis() / 500.0) * 4F;
		ItemIcons.draw(ctx, Items.PAINTING, bx + bw / 2F, by + 56 + bob, 46);
		String p = VisualsData.problem();
		String head = "broken".equals(p) ? "The style list is damaged" : "Install the style packs";
		Draw.textCenter(ctx, head, bx + bw / 2F, by + 110, 21, a(255, c.text), true);
		String[] lines = {
				"1.  Close Minecraft.",
				"2.  Run  install-visuals.bat  (it is in the tools folder of the download).",
				"3.  Start the game again and open this card.",
				"The installer downloads the packs once from GitHub. This menu never goes online."
		};
		for (int i = 0; i < lines.length; i++) {
			Draw.text(ctx, Draw.fit(lines[i], 13, bw - 60), bx + 30, by + 146 + i * 26, 13, a(255, i == 3 ? c.textDim : c.textSoft));
		}
		button(ctx, "openFolder", bx + 30, by + bh - 62, 170, 38, "Open folder", false, true, () -> openFolder(VisualsData.folder()));
		button(ctx, "recheck", bx + 210, by + bh - 62, 130, 38, "Check again", true, true, () -> {
			VisualsData.forceReload();
			Previews.clearFailures();
			say(VisualsData.installed() ? "Found " + VisualsData.styles().size() + " styles" : "Still not installed", VisualsData.installed() ? Tone.OK : Tone.ERROR);
		});
		return by - y + bh + 20;
	}

	private int card(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String title) {
		box(ctx, x, y, w, h, c.small, a(surfaceAlpha(), c.surface));
		edge(ctx, x, y, w, h, c.small, a(255, c.border));
		Draw.text(ctx, title, x + 16, y + 14, 11, a(255, c.textDim), true);
		Draw.rect(ctx, x + 16, y + 34, w - 32, 1, a(255, c.border));
		return y + 48;
	}

	private int customizePage(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int used = title(ctx, x, y, w, "Customization", "Make this menu yours. Everything is saved on this PC only.", null);
		int top = y + used;
		boolean two = w >= 760;
		int gap = 18;
		int colW = two ? (w - gap) / 2 : w;
		int leftEnd = backgroundCard(ctx, x, top, colW);
		int end;
		if (two) {
			leftEnd = themesCard(ctx, x, leftEnd + gap, colW);
			int rightEnd = lookColumn(ctx, x + colW + gap, top, colW);
			end = Math.max(leftEnd, rightEnd);
		} else {
			end = lookColumn(ctx, x, leftEnd + gap, colW);
			end = themesCard(ctx, x, end + gap, w);
		}
		button(ctx, "resetLook", x, end + gap, w, 40, "Reset the look", false, true, () -> {
			VisualsLook.reset();
			hexText = VisualsLook.hex(VisualsLook.accent).substring(1);
			say("Look reset", Tone.OK);
			UiSounds.toggle(false);
		});
		return end + gap + 40 - y;
	}

	private int backgroundCard(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int inner = w - 32;
		int per = Math.max(3, (inner + 10) / (112 + 10));
		int tw = (inner - 10 * (per - 1)) / per;
		int th = Math.round(tw * 0.66F);
		int tiles = 1 + VisualsLook.pictures.size() + (VisualsLook.pictures.size() < VisualsLook.MAX_PICTURES ? 1 : 0);
		int rows = (tiles + per - 1) / per;
		int h = 48 + rows * (th + 10) + 46 + 52 + 58;
		int cy = card(ctx, x, y, w, h, "MENU BACKGROUND");
		int x0 = x + 16;
		for (int i = 0; i < tiles; i++) {
			int tx = x0 + (i % per) * (tw + 10);
			int ty = cy + (i / per) * (th + 10);
			int pi = i - 1;
			boolean isGame = i == 0;
			boolean isAdd = pi >= VisualsLook.pictures.size();
			boolean sel = isGame ? VisualsLook.picture < 0 : !isAdd && VisualsLook.picture == pi;
			String id = "tile." + i;
			boolean hov = overView(tx, ty, tw, th);
			float hv = anim(id, hov ? 1F : 0F, 18F);
			float sv = anim(id + ".s", sel ? 1F : 0F, 14F);
			final int tileIdx = pi;
			hits.add(new Hit(tx, ty, tw, th, () -> {
				if (isGame) {
					VisualsLook.picture = -1;
					VisualsLook.save();
					UiSounds.click();
				} else if (isAdd) {
					pickPicture();
				} else {
					VisualsLook.picture = tileIdx;
					VisualsLook.save();
					UiSounds.click();
				}
			}, null));
			box(ctx, tx, ty, tw, th, Math.max(0, c.small - 2), a(255, Draw.mix(c.field, c.surface2, hv * 0.5F)));
			if (isGame) {
				clip(ctx, tx + 3, ty + 3, tx + tw - 3, ty + th - 3);
				Draw.gradientV(ctx, tx + 3, ty + 3, tw - 6, th - 6, a(255, 0x7EB6F0), a(255, 0xBFE0FF));
				Draw.rect(ctx, tx + 3, ty + th - 22, tw - 6, 6, a(255, 0x5DA03C));
				Draw.rect(ctx, tx + 3, ty + th - 16, tw - 6, 13, a(255, 0x8A5A35));
				Draw.circle(ctx, tx + tw - 22, ty + 18, 7, a(255, 0xFFF3A0));
				ctx.disableScissor();
				Draw.rect(ctx, tx + 3, ty + th - 21, tw - 6, 18, a(150, 0x000000));
				Draw.textCenter(ctx, "Game", tx + tw / 2F, ty + th - 12, 11.5F, a(255, 0xFFFFFF), true);
			} else if (isAdd) {
				Draw.outline(ctx, tx + 2, ty + 2, tw - 4, th - 4, Math.max(1, c.small - 3), 1, a(255, Draw.mix(c.border, c.accent, hv)));
				Draw.textCenter(ctx, "+", tx + tw / 2F, ty + th / 2F - 9, 24, a(255, Draw.mix(c.textDim, c.accent, hv)), true);
				Draw.textCenter(ctx, "Add image", tx + tw / 2F, ty + th / 2F + 14, 11.5F, a(255, c.textDim));
			} else {
				Previews.Tex tex = Previews.picture(VisualsLook.pictureDir().resolve(VisualsLook.pictures.get(pi)), 320);
				clip(ctx, tx + 3, ty + 3, tx + tw - 3, ty + th - 3);
				if (tex != null) {
					cover(ctx, tex, tx + 3, ty + 3, tw - 6, th - 6, 0xFFFFFFFF);
				} else {
					shimmer(ctx, tx + 3, ty + 3, tw - 6, th - 6);
				}
				ctx.disableScissor();
				if (hv > 0.02F) {
					int rx = tx + tw - 24;
					int ry = ty + 4;
					boolean xh = overView(rx, ry, 20, 20);
					Draw.circle(ctx, rx + 10, ry + 10, 10, a(Math.round(230 * hv), xh ? c.danger : 0x000000));
					Icons.drawCentered(ctx, Icons.ui("close"), rx + 10, ry + 10, 12, a(Math.round(255 * hv), 0xFFFFFF));
					final int idx = pi;
					hits.add(new Hit(rx, ry, 20, 20, () -> {
						Path gone = VisualsLook.pictureDir().resolve(VisualsLook.pictures.get(idx));
						Previews.forget(gone);
						VisualsLook.removePicture(idx);
						UiSounds.toggle(false);
					}, null));
				}
			}
			if (sv > 0.02F) {
				Draw.outline(ctx, tx, ty, tw, th, Math.max(1, c.small - 2), 2, a(Math.round(255 * sv), c.accent));
			} else {
				edge(ctx, tx, ty, tw, th, Math.max(0, c.small - 2), a(255, Draw.mix(c.border, c.borderHi, hv)));
			}
			if (hov) {
				hover(id);
			}
		}
		cy += rows * (th + 10) + 4;
		Draw.text(ctx, Draw.fit("PNG or JPG: drop it onto this window, or paste its path.", 12, inner), x0, cy, 12, a(255, c.textDim));
		Draw.text(ctx, Draw.fit("The mod keeps its own copy. Nothing leaves this PC.", 12, inner), x0, cy + 18, 12, a(255, c.textDim));
		cy += 44;
		int addW = 64;
		int dirW = 80;
		int fieldW = inner - addW - dirW - 16;
		field(ctx, "path", x0, cy, fieldW, 36, pathText, "C:\\Pictures\\wallpaper.png", false);
		button(ctx, "addPath", x0 + fieldW + 8, cy, addW, 36, "ADD", true, true, this::addTypedPath);
		button(ctx, "folder", x0 + fieldW + addW + 16, cy, dirW, 36, "Folder", false, true, () -> openFolder(VisualsLook.pictureDir()));
		cy += 52;
		slider(ctx, "darken", x0, cy, inner, "Darken picture", VisualsLook.darken + "%", VisualsLook.darken / 90F, f -> {
			VisualsLook.darken = Math.round(f * 90);
			VisualsLook.save();
		});
		return y + h;
	}

	private int lookColumn(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int inner = w - 32;
		int x0 = x + 16;
		int prevH = 190;
		int cy = card(ctx, x, y, w, prevH, "LIVE PREVIEW");
		livePreview(ctx, x0, cy, inner, prevH - 62);
		cy = y + prevH + 18;
		int winH = 48 + 26 + 40 + 16 + 26 + 40 + 18 + 48 + 54;
		int wy = card(ctx, x, cy, w, winH, "WINDOW");
		Draw.text(ctx, "Look", x0, wy, 12.5F, a(255, c.textSoft));
		wy += 22;
		VisualsLook.Look[] looks = VisualsLook.Look.values();
		String[] ll = new String[looks.length];
		for (int i = 0; i < looks.length; i++) {
			ll[i] = looks[i].label;
		}
		segmented(ctx, "look", x0, wy, inner, 36, ll, VisualsLook.look.ordinal(), i -> {
			VisualsLook.look = looks[i];
			VisualsLook.save();
		});
		wy += 52;
		Draw.text(ctx, "Size", x0, wy, 12.5F, a(255, c.textSoft));
		wy += 22;
		VisualsLook.Size[] sizes = VisualsLook.Size.values();
		segmented(ctx, "size", x0, wy, inner, 36, new String[]{sizes[0].label, sizes[1].label, sizes[2].label}, VisualsLook.size.ordinal(), i -> {
			VisualsLook.size = sizes[i];
			VisualsLook.save();
		});
		wy += 54;
		slider(ctx, "opacity", x0, wy, inner, "Window opacity", VisualsLook.opacity + "%", (VisualsLook.opacity - 30) / 70F, f -> {
			VisualsLook.opacity = 30 + Math.round(f * 70);
			VisualsLook.save();
		});
		wy += 54;
		// background behind the item in each texture preview (the baked-in dark square is stripped by PreviewBg)
		slider(ctx, "previewOpacity", x0, wy, inner, "Texture preview opacity", ClickGUI.previewOpacity.getValueInt() + "%",
				ClickGUI.previewOpacity.getValueFloat() / 100F, f -> ClickGUI.previewOpacity.setValue(Math.round(f * 100)));
		cy += winH + 18;
		int accH = 48 + 40 + 12 + 36 + 18;
		int ay = card(ctx, x, cy, w, accH, "ACCENT COLOUR");
		int n = VisualsLook.SWATCHES.length;
		int sw = Math.min(40, (inner - (n - 1) * 8) / n);
		for (int i = 0; i < n; i++) {
			int col = VisualsLook.SWATCHES[i];
			int sx = x0 + i * (sw + 8);
			boolean hov = overView(sx, ay, sw, sw);
			boolean sel = VisualsLook.accent == col;
			float hv = anim("sw." + i, hov ? 1F : 0F, 18F);
			float sv = anim("sw.s." + i, sel ? 1F : 0F, 14F);
			float rad = sw / 2F - 3 + hv * 2;
			if (sv > 0.02F) {
				Draw.circle(ctx, sx + sw / 2F, ay + sw / 2F, sw / 2F + 1, a(Math.round(255 * sv), c.text));
				Draw.circle(ctx, sx + sw / 2F, ay + sw / 2F, sw / 2F - 1, a(255, c.surface));
			}
			if (c.minecraft) {
				Draw.rect(ctx, Math.round(sx + sw / 2F - rad), Math.round(ay + sw / 2F - rad), Math.round(rad * 2), Math.round(rad * 2), a(255, col));
			} else {
				Draw.circle(ctx, sx + sw / 2F, ay + sw / 2F, rad, a(255, col));
			}
			if (hov) {
				hover("sw." + i);
			}
			hits.add(new Hit(sx, ay, sw, sw, () -> {
				VisualsLook.accent = col;
				VisualsLook.save();
				hexText = VisualsLook.hex(col).substring(1);
				UiSounds.click();
			}, null));
		}
		ay += sw + 12;
		int useW = 70;
		Draw.circle(ctx, x0 + 18, ay + 18, 12, a(255, VisualsLook.parseHex(hexText, VisualsLook.accent)));
		field(ctx, "hex", x0 + 40, ay, inner - useW - 48, 36, "#" + hexText, "#75D4FF", false);
		button(ctx, "useHex", x0 + inner - useW, ay, useW, 36, "Use", true, true, this::useHex);
		return cy + accH;
	}

	private int themesCard(GuiGraphicsExtractor ctx, int x, int y, int w) {
		int inner = w - 32;
		int x0 = x + 16;
		int gap = 10;
		int per = Math.max(2, (inner + gap) / (134 + gap));
		int tw = (inner - gap * (per - 1)) / per;
		int th = 52;
		int rows = (VisualsLook.THEMES.size() + per - 1) / per;
		int h = 48 + rows * (th + gap) + 44;
		int cy = card(ctx, x, y, w, h, "THEMES");
		for (int i = 0; i < VisualsLook.THEMES.size(); i++) {
			VisualsLook.Theme t = VisualsLook.THEMES.get(i);
			int tx = x0 + (i % per) * (tw + gap);
			int ty = cy + (i / per) * (th + gap);
			String id = "theme." + t.id();
			boolean sel = VisualsLook.theme.equals(t.id());
			boolean hov = overView(tx, ty, tw, th);
			float hv = anim(id, hov ? 1F : 0F, 18F);
			float sv = anim(id + ".s", sel ? 1F : 0F, 14F);
			int lift = Math.round(hv * 2);
			int base = Draw.mix(t.base(), t.accent(), 0.12F + hv * 0.08F);
			if (c.gradient && c.small > 0) {
				Draw.roundGradient(ctx, tx, ty - lift, tw, th, c.small, a(255, Draw.mix(base, t.accent(), 0.18F)), a(255, base));
			} else {
				box(ctx, tx, ty - lift, tw, th, c.small, a(255, base));
			}
			if (sv > 0.02F) {
				Draw.outline(ctx, tx, ty - lift, tw, th, Math.max(1, c.small), 2, a(Math.round(255 * sv), t.accent()));
			} else {
				edge(ctx, tx, ty - lift, tw, th, c.small, a(255, Draw.mix(Draw.mix(t.base(), 0xFFFFFF, 0.12F), t.accent(), hv)));
			}
			Pictures.draw(ctx, t.icon(), tx + 24, ty - lift + th / 2F, 22 + hv * 3, hv * 0.15F, 255);
			Draw.textMid(ctx, Draw.fit(t.name(), 13, tw - 80, true), tx + 44, ty - lift + th / 2F, 13, a(255, 0xF6F4F8), true);
			Draw.circle(ctx, tx + tw - 30, ty - lift + th / 2F, 6, a(255, t.accent()));
			Draw.circle(ctx, tx + tw - 16, ty - lift + th / 2F, 6, a(255, t.accent2()));
			if (hov) {
				hover(id);
			}
			hits.add(new Hit(tx, ty, tw, th, () -> {
				VisualsLook.useTheme(t);
				hexText = VisualsLook.hex(VisualsLook.accent).substring(1);
				UiSounds.toggle(true);
			}, null));
		}
		int ty = cy + rows * (th + gap) + 2;
		toggleRow(ctx, "deco", x0, ty, inner, "Theme decorations (pumpkins, snow, petals...)", VisualsLook.decorations, () -> {
			VisualsLook.decorations = !VisualsLook.decorations;
			VisualsLook.save();
			UiSounds.toggle(VisualsLook.decorations);
		});
		return y + h;
	}

	private void toggleRow(GuiGraphicsExtractor ctx, String id, int x, int y, int w, String label, boolean on, Runnable action) {
		Draw.textMid(ctx, Draw.fit(label, 12.5F, w - 70), x, y + 13, 12.5F, a(255, c.textSoft));
		float o = anim("tg." + id, on ? 1F : 0F, 14F);
		int sw = 44, sh = 24;
		int sx = x + w - sw;
		box(ctx, sx, y + 1, sw, sh, c.minecraft ? 0 : sh / 2, a(255, Draw.mix(c.field, c.accent, o)));
		edge(ctx, sx, y + 1, sw, sh, c.minecraft ? 0 : sh / 2, a(255, c.border));
		float kx = sx + 12 + o * (sw - 24);
		if (c.minecraft) {
			Draw.rect(ctx, Math.round(kx - 8), y + 5, 16, 16, a(255, 0xDDDDDD));
		} else {
			Draw.circle(ctx, kx, y + 13, 8, a(255, 0xFFFFFF));
		}
		if (overView(x, y, w, 26)) {
			hover("tg." + id);
		}
		hits.add(new Hit(x, y, w, 26, action, null));
	}

	private void livePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int r = Math.max(0, c.small - 2);
		Path pic = VisualsLook.picturePath();
		Previews.Tex tex = pic == null ? null : Previews.picture(pic, 320);
		if (tex != null) {
			clip(ctx, x, y, x + w, y + h);
			cover(ctx, tex, x, y, w, h, 0xFFFFFFFF);
			Draw.rect(ctx, x, y, w, h, a(Math.round(VisualsLook.darken * 2.55F), 0x000000));
			ctx.disableScissor();
		} else {
			fill2(ctx, x, y, w, h, r, a(c.alpha, Draw.mix(c.window, c.accent, c.gradient ? 0.08F : 0F)), a(c.alpha, c.window));
		}
		edge(ctx, x, y, w, h, r, a(255, c.border));
		Draw.rect(ctx, x + 1, y + 1, w - 2, 16, a(160, c.header));
		box(ctx, x + 8, y + 5, 7, 7, c.minecraft ? 0 : 3, a(255, c.accent));
		Draw.rect(ctx, x + 22, y + 7, w / 4, 3, a(255, c.textDim));
		int railW = w / 4;
		Draw.rect(ctx, x + 1, y + 17, railW, h - 18, a(150, c.rail));
		for (int i = 0; i < 6; i++) {
			int iy = y + 24 + i * 15;
			if (iy + 10 > y + h) {
				break;
			}
			box(ctx, x + 6, iy, railW - 10, 10, c.minecraft ? 0 : 3, a(255, i == 1 ? Draw.mix(c.surface2, c.accent, 0.2F) : c.surface));
			if (i == 1) {
				Draw.rect(ctx, x + 6, iy, 2, 10, a(255, c.accent));
			}
		}
		int gx = x + railW + 10;
		int gw = w - railW - 18;
		int cols = 4;
		int tw = (gw - (cols - 1) * 6) / cols;
		int th = Math.min(tw, (h - 34) / 2 - 4);
		for (int i = 0; i < 8; i++) {
			int tx = gx + (i % cols) * (tw + 6);
			int ty = y + 24 + (i / cols) * (th + 6);
			box(ctx, tx, ty, tw, th, c.minecraft ? 0 : Math.min(c.small, 5), a(surfaceAlpha(), i == 2 ? Draw.mix(c.surface, c.accent, 0.2F) : c.surface));
			if (i == 2) {
				Draw.outline(ctx, tx, ty, tw, th, Math.max(1, Math.min(c.small, 5)), 1, a(255, c.accent));
			}
		}
		box(ctx, gx + gw - 60, y + h - 16, 60, 10, c.minecraft ? 0 : 5, a(255, c.accent));
	}

	private void field(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String text, String placeholder, boolean searchIcon) {
		boolean on = id.equals(focus);
		float f = anim("field." + id, on ? 1F : 0F, 16F);
		box(ctx, x, y, w, h, c.small, a(255, c.field));
		if (c.minecraft) {
			Draw.outline(ctx, x, y, w, h, 0, 1, a(255, on ? 0xFFFFFF : 0xA0A0A0));
		} else {
			Draw.outline(ctx, x, y, w, h, Math.max(1, c.small), 1, a(255, Draw.mix(c.border, c.accent, f)));
		}
		int tx = x + 12;
		if (searchIcon) {
			Icons.drawCentered(ctx, Icons.ui("search"), x + 20, y + h / 2F, 17, a(255, Draw.mix(c.textDim, c.accent, f)));
			tx = x + 38;
		}
		int room = w - (tx - x) - 12;
		boolean empty = id.equals("hex") ? hexText.isEmpty() : text.isEmpty();
		if (empty && !on) {
			Draw.textMid(ctx, Draw.fit(placeholder, 13, room), tx, y + h / 2F, 13, a(160, c.textDim));
		} else {
			String shown = text;
			while (Draw.width(shown, 13) > room && shown.length() > 1) {
				shown = shown.substring(1);
			}
			Draw.textMid(ctx, shown, tx, y + h / 2F, 13, a(255, c.text));
			if (on && (System.currentTimeMillis() / 530) % 2 == 0) {
				Draw.rect(ctx, tx + Draw.width(shown, 13) + 1, y + 10, 1, h - 20, a(255, c.accent));
			}
		}
		hits.add(new Hit(x, y, w, h, () -> focus = id, null));
		if (on && id.equals("search") && !query.isEmpty()) {
			int bx = x + w - 28;
			Icons.drawCentered(ctx, Icons.ui("close"), bx + 10, y + h / 2F, 14, a(255, c.textDim));
			hits.add(new Hit(bx, y, 24, h, () -> {
				query = "";
				focus = "search";
			}, null));
		}
	}

	private void button(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String label, boolean primary, boolean enabled, Runnable action) {
		boolean hov = enabled && over(x, y, w, h) && (y < viewY || overView(x, y, w, h) || x < viewX);
		float hv = anim("btn." + id, hov ? 1F : 0F, 18F);
		int lift = c.minecraft ? 0 : Math.round(hv * 2);
		int yy = y - lift;
		if (c.minecraft) {
			int fillc = !enabled ? 0x2A2A2A : primary ? Draw.mix(0x6B6B6B, c.accent, 0.35F + hv * 0.2F) : Draw.mix(0x6B6B6B, 0x8B8BCB, hv * 0.6F);
			Draw.rect(ctx, x, yy, w, h, a(255, 0x000000));
			Draw.rect(ctx, x + 1, yy + 1, w - 2, h - 2, a(255, fillc));
			Draw.rect(ctx, x + 1, yy + 1, w - 2, 1, a(110, 0xFFFFFF));
			Draw.rect(ctx, x + 1, yy + h - 3, w - 2, 2, a(110, 0x000000));
			if (hov) {
				Draw.outline(ctx, x, yy, w, h, 0, 1, a(255, 0xFFFFFF));
			}
		} else if (primary && enabled) {
			int top = Draw.mix(c.accent, 0xFFFFFF, hv * 0.12F);
			int bot = Draw.mix(c.accent2, 0xFFFFFF, hv * 0.12F);
			if (!c.flat && hv > 0.02F) {
				Draw.shadow(ctx, x, yy, w, h, Math.max(1, c.small), 12, Math.round(90 * hv));
			}
			if (c.gradient) {
				Draw.roundGradient(ctx, x, yy, w, h, Math.max(1, c.small), a(255, top), a(255, bot));
			} else {
				box(ctx, x, yy, w, h, c.small, a(255, top));
			}
		} else {
			int fillc = enabled ? Draw.mix(c.surface2, Draw.mix(c.surface2, 0xFFFFFF, 0.08F), hv) : c.field;
			box(ctx, x, yy, w, h, c.small, a(enabled ? 255 : 160, fillc));
			edge(ctx, x, yy, w, h, c.small, a(255, Draw.mix(c.border, c.borderHi, hv)));
		}
		int tc = !enabled ? c.textDim : primary && !c.minecraft ? c.onAccent : Draw.mix(c.textSoft, c.text, hv);
		Draw.textCenter(ctx, label, x + w / 2F, yy + h / 2F, 13, a(enabled ? 255 : 120, c.minecraft && enabled ? 0xFFFFFF : tc), true);
		if (hov) {
			hover("btn." + id);
		}
		if (enabled) {
			hits.add(new Hit(x, y, w, h, action, null));
		}
	}

	private void segmented(GuiGraphicsExtractor ctx, String id, int x, int y, int w, int h, String[] labels, int selected, java.util.function.IntConsumer pick) {
		box(ctx, x, y, w, h, c.small, a(255, c.field));
		edge(ctx, x, y, w, h, c.small, a(255, c.border));
		int n = labels.length;
		float seg = (w - 6) / (float) n;
		float pos = anim("seg." + id, selected, 16F);
		int px = Math.round(x + 3 + pos * seg);
		if (c.minecraft) {
			Draw.rect(ctx, px, y + 3, Math.round(seg), h - 6, a(255, Draw.mix(0x6B6B6B, c.accent, 0.45F)));
		} else if (c.gradient) {
			Draw.roundGradient(ctx, px, y + 3, Math.round(seg), h - 6, Math.max(1, c.small - 2), a(255, c.accent), a(255, c.accent2));
		} else {
			box(ctx, px, y + 3, Math.round(seg), h - 6, Math.max(0, c.small - 2), a(255, c.accent));
		}
		for (int i = 0; i < n; i++) {
			int sx = Math.round(x + 3 + i * seg);
			int sw = Math.round(seg);
			float on = Math.max(0F, 1F - Math.abs(pos - i));
			boolean hov = over(sx, y, sw, h) && (y < viewY || overView(sx, y, sw, h));
			int tc = Draw.mix(hov ? c.text : c.textSoft, c.minecraft ? 0xFFFFFF : c.onAccent, on);
			float fs = seg < 80 ? 11.5F : 12.5F;
			Draw.textCenter(ctx, Draw.fit(labels[i], fs, sw - 8, true), sx + sw / 2F, y + h / 2F, fs, a(255, tc), true);
			if (hov) {
				hover("seg." + id + "." + i);
			}
			final int k = i;
			hits.add(new Hit(sx, y, sw, h, () -> {
				if (k != selected) {
					pick.accept(k);
					UiSounds.click();
				}
			}, null));
		}
	}

	private void slider(GuiGraphicsExtractor ctx, String id, int x, int y, int w, String label, String value, float frac, Consumer<Float> set) {
		frac = Math.max(0F, Math.min(1F, frac));
		Draw.text(ctx, label, x, y, 12.5F, a(255, c.textSoft));
		Draw.textRight(ctx, value, x + w, y + 7, 12.5F, a(255, c.text));
		int ty = y + 26;
		float shown = anim("sl." + id, frac, 22F);
		boolean hov = overView(x, ty - 8, w, 22) || (dragSet != null && dragX == x && dragW == w);
		float hv = anim("sl.h." + id, hov ? 1F : 0F, 18F);
		box(ctx, x, ty, w, 6, c.minecraft ? 0 : 3, a(255, c.field));
		int fw = Math.round(w * shown);
		if (fw > 0) {
			if (c.gradient) {
				Draw.gradientH(ctx, x, ty, fw, 6, a(255, c.accent2), a(255, c.accent));
			} else {
				box(ctx, x, ty, fw, 6, c.minecraft ? 0 : 3, a(255, c.accent));
			}
		}
		float kx = x + w * shown;
		if (c.minecraft) {
			Draw.rect(ctx, Math.round(kx - 4), ty - 6, 8, 18, a(255, 0xC6C6C6));
			Draw.outline(ctx, Math.round(kx - 4), ty - 6, 8, 18, 0, 1, a(255, 0x000000));
		} else {
			Draw.circle(ctx, kx, ty + 3, 8 + hv * 2, a(255, 0xFFFFFF));
			Draw.circle(ctx, kx, ty + 3, 4 + hv, a(255, c.accent));
		}
		if (hov) {
			hover("sl." + id);
		}
		hits.add(new Hit(x - 6, ty - 10, w + 12, 26, () -> {
			dragSet = set;
			dragX = x;
			dragW = w;
			dragTo(mx);
			UiSounds.click();
		}, null));
	}

	private int lastTick = -1;

	private void dragTo(double px) {
		if (dragSet == null) {
			return;
		}
		float f = (float) Math.max(0, Math.min(1, (px - dragX) / dragW));
		dragSet.accept(f);
		int step = Math.round(f * 20);
		if (step != lastTick) {
			lastTick = step;
			UiSounds.tick(f);
		}
	}

	private void applyBar(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int n = changes();
		boolean show = (n > 0 || busy) && VisualsData.installed();
		float s = anim("bar", show ? 1F : 0F, 12F);
		if (s < 0.01F) {
			return;
		}
		int bw = Math.min(w - 40, 520);
		int bh = 58;
		int bx = x + (w - bw) / 2;
		int by = Math.round(y + h - 18 - bh * s - (1F - s) * 10F);
		float keep = Draw.alpha;
		Draw.alpha = keep * s;
		if (!c.flat) {
			Draw.shadow(ctx, bx, by, bw, bh, Math.max(1, c.radius), 26, 170);
		}
		box(ctx, bx, by, bw, bh, c.radius, a(245, Draw.mix(c.surface2, c.accent, 0.08F)));
		edge(ctx, bx, by, bw, bh, c.radius, a(255, Draw.mix(c.border, c.accent, 0.5F)));
		String msg = busy ? "Applying..." : n + " change" + (n == 1 ? "" : "s") + " not applied yet";
		Draw.circle(ctx, bx + 24, by + bh / 2F, 5 + (float) Math.sin(System.currentTimeMillis() / 220.0) * 1.2F, a(255, c.accent));
		Draw.textMid(ctx, Draw.fit(msg, 14, bw - 290, true), bx + 40, by + bh / 2F, 14, a(255, c.text), true);
		hits.add(new Hit(bx, by, bw, bh, () -> {
		}, null));
		button(ctx, "undo", bx + bw - 236, by + 11, 96, 36, "Undo", false, !busy, this::undo);
		button(ctx, "apply", bx + bw - 130, by + 11, 116, 36, busy ? "" : "Apply", true, !busy, this::apply);
		if (busy) {
			spinner(ctx, bx + bw - 72, by + bh / 2F, 8, c.textSoft);
		}
		Draw.alpha = keep;
	}

	private void spinner(GuiGraphicsExtractor ctx, float cx, float cy, float r, int rgb) {
		float t = (System.currentTimeMillis() % 1000L) / 1000F;
		for (int i = 0; i < 8; i++) {
			double ang = (i / 8.0 + t) * Math.PI * 2;
			Draw.circle(ctx, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, 1.8F, a(Math.round(40 + 215 * (i / 7F)), rgb));
		}
	}

	private void toast(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		String t = toast;
		if (t == null) {
			return;
		}
		long age = System.currentTimeMillis() - toastAt;
		if (age > 3600) {
			toast = null;
			return;
		}
		float in = ease(age / 220F) * (age > 3200 ? 1F - (age - 3200) / 400F : 1F);
		int tw = Math.min(w - 40, Draw.width(t, 13, true) + 60);
		int th = 42;
		int tx = x + w - tw - 22;
		int ty = Math.round(y + 16 + (1F - in) * -12F);
		float keep = Draw.alpha;
		Draw.alpha = keep * in;
		int col = switch (toastTone) {
			case OK -> c.ok;
			case ERROR -> c.danger;
			default -> c.accent;
		};
		if (!c.flat) {
			Draw.shadow(ctx, tx, ty, tw, th, Math.max(1, c.small), 18, 140);
		}
		box(ctx, tx, ty, tw, th, c.small, a(250, Draw.mix(c.surface2, col, 0.12F)));
		edge(ctx, tx, ty, tw, th, c.small, a(255, Draw.mix(c.border, col, 0.6F)));
		Draw.circle(ctx, tx + 20, ty + th / 2F, 5, a(255, col));
		Draw.textMid(ctx, Draw.fit(t, 13, tw - 46, true), tx + 34, ty + th / 2F, 13, a(255, c.text), true);
		Draw.alpha = keep;
	}

	private void modal(GuiGraphicsExtractor ctx, int W, int H) {
		VisualsData.Style s = modal;
		float in = ease((System.currentTimeMillis() - modalAt) / 220F);
		Draw.rect(ctx, 0, 0, W, H, a(Math.round(170 * in), 0x000000));
		int mw = Math.min(W - 80, 640);
		int img = Math.min(mw - 48, H - 80 - 170);
		int mh = img + 170;
		int mxp = (W - mw) / 2;
		int myp = (H - mh) / 2;
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		float z = 0.92F + 0.08F * back(Math.min(1F, (System.currentTimeMillis() - modalAt) / 300F));
		m.translate(W / 2F, H / 2F);
		m.scale(z, z);
		m.translate(-W / 2F, -H / 2F);
		float keep = Draw.alpha;
		Draw.alpha = keep * in;
		hits.add(new Hit(0, 0, W, H, () -> modal = null, () -> modal = null));
		if (!c.flat) {
			Draw.shadow(ctx, mxp, myp, mw, mh, Math.max(1, c.radius), 40, 200);
		}
		fill2(ctx, mxp, myp, mw, mh, c.radius, a(255, Draw.mix(c.window, c.accent, 0.08F)), a(255, c.window));
		edge(ctx, mxp, myp, mw, mh, c.radius, a(255, c.border));
		hits.add(new Hit(mxp, myp, mw, mh, () -> {
		}, () -> {
		}));
		int ix = mxp + 24;
		int iy = myp + 24;
		int iw = mw - 48;
		box(ctx, ix, iy, iw, img, c.small, Backdrop.previewColor(a(255, c.field)));
		Previews.Tex tex = Previews.get(s);
		if (tex != null) {
			contain(ctx, tex, ix + 10, iy + 10, iw - 20, img - 20, 0xFFFFFFFF);
		} else {
			shimmer(ctx, ix, iy, iw, img);
		}
		int idx = modalList.indexOf(s);
		if (modalList.size() > 1 && idx >= 0) {
			arrow(ctx, "prev", ix + 8, iy + img / 2 - 22, "chevron-left", () -> step(-1));
			arrow(ctx, "next", ix + iw - 52, iy + img / 2 - 22, "chevron-right", () -> step(1));
			Draw.textRight(ctx, (idx + 1) + " / " + modalList.size(), ix + iw - 12, iy + img - 16, 11.5F, a(200, c.textDim));
		}
		int ty = iy + img + 18;
		VisualsData.Category cat = VisualsData.category(s.category());
		Draw.text(ctx, Draw.fit(s.name(), 21, iw - 200, true), ix, ty, 21, a(255, c.text), true);
		StringBuilder sb = new StringBuilder(cat == null ? s.category() : cat.name());
		if (s.by() != null) {
			sb.append("  \u00B7  by ").append(s.by());
		}
		if (s.added() != null) {
			sb.append("  \u00B7  added ").append(s.added());
		}
		if (s.size() > 0) {
			sb.append("  \u00B7  ").append(s.size() >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", s.size() / 1048576.0) : Math.max(1, s.size() / 1024) + " KB");
		}
		Draw.text(ctx, Draw.fit(sb.toString(), 12, iw - 200), ix, ty + 30, 12, a(255, c.textDim));
		boolean on = s.id().equals(picks.get(s.category()));
		int by = myp + mh - 64;
		button(ctx, "m.close", ix + iw - 290, by, 110, 40, "Close", false, true, () -> modal = null);
		button(ctx, "m.equip", ix + iw - 170, by, 170, 40, on ? "Unequip" : "Equip", !on, true, () -> toggle(s));
		Draw.alpha = keep;
		m.popMatrix();
	}

	private void arrow(GuiGraphicsExtractor ctx, String id, int x, int y, String icon, Runnable action) {
		boolean hov = over(x, y, 44, 44);
		float hv = anim("arrow." + id, hov ? 1F : 0F, 18F);
		Draw.circle(ctx, x + 22, y + 22, 20, a(Math.round(120 + 100 * hv), 0x000000));
		Icons.drawCentered(ctx, icon, x + 22, y + 22, 22, a(255, 0xFFFFFF));
		if (hov) {
			hover("arrow." + id);
		}
		hits.add(new Hit(x, y, 44, 44, action, null));
	}

	private void step(int d) {
		if (modal == null || modalList.isEmpty()) {
			return;
		}
		int i = modalList.indexOf(modal);
		if (i < 0) {
			return;
		}
		i = (i + d + modalList.size()) % modalList.size();
		modal = modalList.get(i);
		UiSounds.tick(i / (float) Math.max(1, modalList.size() - 1));
	}

	private double toDesign(double gui) {
		return gui * Minecraft.getInstance().getWindow().getGuiScale() / ui;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		mx = toDesign(click.x());
		my = toDesign(click.y());
		int button = click.button();
		if (button != 0 && button != 1) {
			return true;
		}
		String before = focus;
		focus = null;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.contains(mx, my)) {
				Runnable r = button == 0 ? h.left() : h.right();
				if (r != null) {
					r.run();
				} else if (button == 1) {
					focus = before;
				}
				if (focus != null && !focus.equals(before)) {
					UiSounds.click();
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (dragSet != null) {
			dragTo(toDesign(click.x()));
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		dragSet = null;
		lastTick = -1;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (modal != null) {
			step(verticalAmount > 0 ? -1 : 1);
			return true;
		}
		double dx = toDesign(mouseX);
		double dy = toDesign(mouseY);
		if (dx < viewX && dy >= railY && dy < railY + railH) {
			railTarget = (float) Math.max(0, Math.min(railMax, railTarget - verticalAmount * 60));
		} else {
			scrollTarget = (float) Math.max(0, Math.min(contentMax, scrollTarget - verticalAmount * 80));
		}
		return true;
	}

	private boolean ctrl(KeyEvent input) {
		return (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int key = input.key();
		if (modal != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				modal = null;
			} else if (key == GLFW.GLFW_KEY_LEFT) {
				step(-1);
			} else if (key == GLFW.GLFW_KEY_RIGHT) {
				step(1);
			} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) {
				toggle(modal);
			}
			return true;
		}
		if (focus != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				focus = null;
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				if (focus.equals("path")) {
					addTypedPath();
				} else if (focus.equals("hex")) {
					useHex();
				} else {
					focus = null;
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				edit(s -> s.isEmpty() ? s : ctrl(input) ? "" : s.substring(0, s.length() - 1));
				return true;
			}
			if (input.isPaste()) {
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
				if (clip != null) {
					String one = clip.replace("\r", "").replace("\n", "");
					edit(s -> s + one);
				}
				return true;
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_F && ctrl(input)) {
			focus = "search";
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (!query.isEmpty()) {
				query = "";
				return true;
			}
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	private void edit(java.util.function.UnaryOperator<String> op) {
		switch (focus) {
			case "search" -> {
				query = cap(op.apply(query), 40);
				scrollTarget = 0;
			}
			case "path" -> pathText = cap(op.apply(pathText), 400);
			case "hex" -> {
				String v = op.apply(hexText).replace("#", "").replaceAll("[^0-9a-fA-F]", "");
				hexText = cap(v, 6).toUpperCase(Locale.ROOT);
			}
			default -> {
			}
		}
	}

	private static String cap(String s, int n) {
		return s.length() > n ? s.substring(0, n) : s;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (!input.isAllowedChatCharacter() || modal != null) {
			return false;
		}
		String s = input.codepointAsString();
		if (focus == null) {
			if (VisualsData.installed() && !page.equals(CUSTOM) && s.matches("[A-Za-z0-9]")) {
				focus = "search";
				query = "";
			} else {
				return false;
			}
		}
		edit(v -> v + s);
		return true;
	}
}
