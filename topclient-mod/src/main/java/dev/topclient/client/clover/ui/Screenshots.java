package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Tidy screenshots: instead of Minecraft's chat line with the long file name, you get a
 * camera flash, a pink pop-up with a preview of the picture, and a short chat line with
 * [Open] and [Folder]. Turn it off with ui.screenshotToast=false.
 */
public final class Screenshots {
	private static final Identifier TEX = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "screenshot/last");
	private static DynamicTexture texture;
	private static int imgW, imgH;
	private static long shownAt = -1;
	private static long flashAt = -1;

	private Screenshots() {
	}

	public static boolean enabled() {
		try {
			return Config.getBool("ui.screenshotToast", true);
		} catch (RuntimeException e) {
			return true;
		}
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "screenshot_toast"), (ctx, tick) -> draw(ctx));
	}

	/** Wraps the message Minecraft sends after saving a screenshot. */
	public static Consumer<Component> wrap(Consumer<Component> original) {
		if (original == null || !enabled()) {
			return original;
		}
		return text -> {
			try {
				File file = savedFile(text);
				if (file != null) {
					original.accept(message(file));
					loadPreview(file);
					return;
				}
			} catch (Throwable t) {
				CloverClient.LOGGER.warn("Screenshot pop-up failed: {}", t.toString());
			}
			original.accept(text);
		};
	}

	private static File savedFile(Component text) {
		if (!(text.getContents() instanceof TranslatableContents tr) || !"screenshot.success".equals(tr.getKey())) {
			return null;
		}
		for (Object arg : tr.getArgs()) {
			if (arg instanceof Component t) {
				File f = findFile(t);
				if (f != null) {
					return f;
				}
			}
		}
		return null;
	}

	private static File findFile(Component t) {
		ClickEvent ev = t.getStyle().getClickEvent();
		if (ev instanceof ClickEvent.OpenFile of) {
			return of.file();
		}
		for (Component s : t.getSiblings()) {
			File f = findFile(s);
			if (f != null) {
				return f;
			}
		}
		return null;
	}

	static final String NS = "topclient";

	private static Style button(int color, String hover, ClickEvent click) {
		return Style.EMPTY.withColor(color).withBold(true).withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
	}

	/** One tidy line: name + four coloured buttons. */
	private static Component message(File file) {
		String name = file.getName();
		ClickEvent copy = new ClickEvent.Custom(net.minecraft.resources.Identifier.fromNamespaceAndPath(NS, "shot_copy"), java.util.Optional.of(net.minecraft.nbt.StringTag.valueOf(file.getAbsolutePath())));
		ClickEvent rename = new ClickEvent.Custom(net.minecraft.resources.Identifier.fromNamespaceAndPath(NS, "shot_rename"), java.util.Optional.of(net.minecraft.nbt.StringTag.valueOf(file.getAbsolutePath())));
		MutableComponent line = Component.literal("✦ ").setStyle(Style.EMPTY.withColor(0xFF4F9A))
				.append(Component.literal("Screenshot ").setStyle(Style.EMPTY.withColor(0xFFE6F3)))
				.append(Component.literal(name.replace(".png", "")).setStyle(Style.EMPTY.withColor(0x9E8FB2)))
				.append(Component.literal("   "));
		line.append(Component.literal("[Copy]").setStyle(button(0x4ADE80, "Copy the picture (paste it in Discord etc.)", copy))).append(Component.literal(" "));
		line.append(Component.literal("[View]").setStyle(button(0x60A5FA, "Open the picture", new ClickEvent.OpenFile(file)))).append(Component.literal(" "));
		line.append(Component.literal("[File path]").setStyle(button(0xC084FC, file.getAbsolutePath() + "\n\nClick to copy the path", new ClickEvent.CopyToClipboard(file.getAbsolutePath())))).append(Component.literal(" "));
		line.append(Component.literal("[Rename]").setStyle(button(0xFBBF24, "Give it a name", rename)));
		return line;
	}

	private static final java.util.regex.Pattern SHOT_NAME = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(?:_\\d+)?\\.png)");

	private static long foreignAt = 0;
	private static boolean foreignDone = false;

	/**
	 * Other mods' screenshot messages ("Screenshot captured / file / path / [Copy path] [Open] ...") become our
	 * one tidy line. Returns the text to show, or null to drop the line. Works whether that mod sends
	 * everything as one message or as several lines.
	 */
	public static Component tidyChat(Component text) {
		if (text == null || !enabled()) {
			return text;
		}
		try {
			String str = text.getString();
			long now = System.currentTimeMillis();
			boolean header = str.contains("Screenshot captured") || (text.getContents() instanceof TranslatableContents tr && "screenshot.success".equals(tr.getKey()));
			boolean follow = now - foreignAt < 2500 && (str.contains("[Copy path]") || str.contains("Search screenshots") || str.contains("[Open]")
					|| str.contains("[Rename]") || str.contains("KiB") || str.contains("MiB") || str.contains(File.separator + "screenshots") || SHOT_NAME.matcher(str).find());
			if (!header && !follow) {
				return text;
			}
			if (header) {
				foreignAt = now;
				foreignDone = false;
			}
			File file = findFile(text);
			if (file == null) {
				java.util.regex.Matcher m = SHOT_NAME.matcher(str);
				if (m.find()) {
					File f = new File(new File(Minecraft.getInstance().gameDirectory, "screenshots"), m.group(1));
					if (f.isFile()) {
						file = f;
					}
				}
			}
			if (file != null && !foreignDone) {
				foreignDone = true;
				loadPreview(file);
				return message(file);
			}
			return null; // the rest of that mod's lines
		} catch (Throwable t) {
			return text;
		}
	}

	/** Our own chat buttons. Returns true when it handled the click. */
	public static boolean onClick(ClickEvent ev) {
		if (!(ev instanceof ClickEvent.Custom c) || !NS.equals(c.id().getNamespace())) {
			return false;
		}
		String path = c.payload().flatMap(n -> n.asString()).orElse("");
		File file = new File(path);
		File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
		try {
			// only files inside this game's screenshots folder
			if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator) || !file.isFile()) {
				return true;
			}
		} catch (Exception e) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		switch (c.id().getPath()) {
			case "shot_copy" -> copyImage(file);
			case "shot_rename" -> mc.execute(() -> mc.gui.setScreen(new RenameShotScreen(mc.gui.screen(), file)));
			default -> {
			}
		}
		return true;
	}

	private static void say(String msg, int color) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.gui != null) {
				mc.gui.hud.setOverlayMessage(Component.literal(msg).setStyle(Style.EMPTY.withColor(color)), false);
			}
		});
	}

	/** Puts the picture itself on the clipboard (Windows), or its path elsewhere. */
	private static void copyImage(File file) {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		if (!os.contains("win")) {
			Minecraft.getInstance().keyboardHandler.setClipboard(file.getAbsolutePath());
			say("Path copied", 0xC084FC);
			return;
		}
		Thread th = new Thread(() -> {
			try {
				// The path goes in through an environment variable, never into the command text.
				ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden", "-Command",
						"Add-Type -AssemblyName System.Windows.Forms,System.Drawing; $i=[System.Drawing.Image]::FromFile($env:TOPCLIENT_SHOT); [System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()");
				pb.environment().put("TOPCLIENT_SHOT", file.getAbsolutePath());
				pb.redirectErrorStream(true);
				Process p = pb.start();
				p.getInputStream().readAllBytes();
				boolean ok = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
				say(ok ? "Screenshot copied — paste it anywhere" : "Couldn't copy the picture", ok ? 0x4ADE80 : 0xFF7D97);
			} catch (Exception e) {
				say("Couldn't copy the picture", 0xFF7D97);
			}
		}, "TopClient copy screenshot");
		th.setDaemon(true);
		th.start();
	}

	private static void loadPreview(File file) {
		Minecraft mc = Minecraft.getInstance();
		flashAt = System.currentTimeMillis();
		Thread th = new Thread(() -> {
			try {
				NativeImage img;
				try (InputStream in = Files.newInputStream(file.toPath())) {
					img = NativeImage.read(in);
				}
				mc.execute(() -> {
					try {
						if (texture != null) {
							mc.getTextureManager().release(TEX);
						}
						texture = new DynamicTexture(TEX::toString, img);
						mc.getTextureManager().register(TEX, texture);
						imgW = img.getWidth();
						imgH = img.getHeight();
						shownAt = System.currentTimeMillis();
					} catch (Throwable t) {
						img.close();
					}
				});
			} catch (Throwable t) {
				CloverClient.LOGGER.warn("Screenshot preview failed: {}", t.toString());
			}
		}, "TopClient screenshot preview");
		th.setDaemon(true);
		th.start();
	}

	private static void draw(GuiGraphicsExtractor ctx) {
		long now = System.currentTimeMillis();
		int w = ctx.guiWidth(), h = ctx.guiHeight();
		if (flashAt > 0 && now - flashAt < 260) {
			float a = 1F - (now - flashAt) / 260F;
			ctx.fill(0, 0, w, h, Bloom.argb(0xFFFFFF, a * 0.55F));
		}
		if (shownAt < 0 || texture == null || imgW <= 0) {
			return;
		}
		float t = (now - shownAt) / 1000F;
		if (t > 4.5F) {
			return;
		}
		float in = Math.min(1F, t / 0.3F), out = Math.min(1F, Math.max(0F, (4.5F - t) / 0.4F));
		float slide = 1F - (1F - in) * (1F - in);
		float a = Math.min(in, out);
		int pw = 150, th = Math.round(138F * imgH / imgW);
		int ph = th + 30;
		int x = Math.round(w - 8 - pw * slide), y = 8;
		Gfx.rect(ctx, x - 1, y - 1, pw + 2, ph + 2, 7, Bloom.argb(Bloom.PINK, 0.8F * a));
		Gfx.rect(ctx, x, y, pw, ph, 6, Bloom.argb(Bloom.PANEL, 0.94F * a));
		ctx.blit(RenderPipelines.GUI_TEXTURED, TEX, x + 6, y + 6, 0F, 0F, 138, th, imgW, imgH, imgW, imgH, Bloom.argb(0xFFFFFF, a));
		Gfx.text(ctx, "Screenshot saved", x + 7, y + th + 10, Bloom.argb(0xFFF3F8, a));
		Gfx.text(ctx, "Open it from chat", x + 7, y + th + 20, Bloom.argb(0xD9A9C4, a));
	}
}
