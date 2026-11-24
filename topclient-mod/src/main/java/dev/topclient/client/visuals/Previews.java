package dev.topclient.client.visuals;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Previews {

	public record Tex(Identifier id, int w, int h) {
	}

	private record Decoded(String key, int w, int h, int[] argb) {
	}

	private static final int SIZE = 192;
	private static final int MAX_LOADED = 220;
	private static final int UPLOADS_PER_FRAME = 6;

	private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "TopClient visuals preview");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private static final Map<String, Tex> loaded = new LinkedHashMap<>(64, 0.75F, true);
	private static final Set<String> pending = ConcurrentHashMap.newKeySet();
	private static final Set<String> failed = ConcurrentHashMap.newKeySet();
	private static final ConcurrentLinkedQueue<Decoded> ready = new ConcurrentLinkedQueue<>();
	private static int counter;

	private Previews() {
	}

	public static void tick() {
		for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
			Decoded d = ready.poll();
			if (d == null) {
				break;
			}
			pending.remove(d.key());
			try {
				NativeImage n = new NativeImage(d.w(), d.h(), false);
				for (int y = 0; y < d.h(); y++) {
					for (int x = 0; x < d.w(); x++) {
						n.setPixel(x, y, d.argb()[y * d.w() + x]);
					}
				}
				Identifier id = Identifier.fromNamespaceAndPath("topclient", "visuals/p" + (counter++));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, n));
				Tex old = loaded.put(d.key(), new Tex(id, d.w(), d.h()));
				if (old != null) {
					destroy(old.id());
				}
			} catch (Throwable t) {
				failed.add(d.key());
			}
		}
		while (loaded.size() > MAX_LOADED) {
			Iterator<Map.Entry<String, Tex>> it = loaded.entrySet().iterator();
			Map.Entry<String, Tex> eldest = it.next();
			it.remove();
			destroy(eldest.getValue().id());
		}
	}

	public static Tex get(VisualsData.Style s) {
		return request("p:" + s.id(), VisualsData.previewFile(s), SIZE);
	}

	public static Tex picture(Path file, int max) {
		if (file == null) {
			return null;
		}
		return request("b:" + max + ":" + file.getFileName(), file, max);
	}

	private static Tex request(String key, Path file, int max) {
		Tex t = loaded.get(key);
		if (t != null) {
			return t;
		}
		if (failed.contains(key) || !pending.add(key)) {
			return null;
		}
		POOL.execute(() -> {
			try {
				if (!Files.isRegularFile(file) || Files.size(file) > VisualsLook.MAX_PICTURE_BYTES) {
					throw new IllegalStateException();
				}
				BufferedImage img = ImageIO.read(file.toFile());
				if (img == null) {
					throw new IllegalStateException();
				}
				float k = Math.min(1F, max / (float) Math.max(img.getWidth(), img.getHeight()));
				int w = Math.max(1, Math.round(img.getWidth() * k));
				int h = Math.max(1, Math.round(img.getHeight() * k));
				BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = out.createGraphics();
				boolean pixel = img.getWidth() <= 64 && w >= img.getWidth();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, pixel
						? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g.drawImage(img, 0, 0, w, h, null);
				g.dispose();
				int[] px = out.getRGB(0, 0, w, h, null, 0, w);
				PreviewBg.strip(px, key, w, h); // remove the dark square baked into style previews
				ready.add(new Decoded(key, w, h, px));
			} catch (Throwable e) {
				failed.add(key);
				pending.remove(key);
			}
		});
		return null;
	}

	public static void forget(Path file) {
		String tail = ":" + file.getFileName();
		loaded.entrySet().removeIf(e -> {
			if (e.getKey().startsWith("b:") && e.getKey().endsWith(tail)) {
				destroy(e.getValue().id());
				return true;
			}
			return false;
		});
	}

	public static void clearFailures() {
		failed.clear();
	}

	private static void destroy(Identifier id) {
		try {
			Minecraft.getInstance().getTextureManager().release(id);
		} catch (RuntimeException ignored) {
		}
	}
}
