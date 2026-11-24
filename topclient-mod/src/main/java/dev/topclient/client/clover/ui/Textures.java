package dev.topclient.client.clover.ui;

import dev.topclient.client.clover.CloverClient;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Textures {
	public record Tex(Identifier id, int w, int h) {
	}

	private static final long MAX_BYTES = 40L * 1024 * 1024;
	private static final int MAX_SIDE = 2048;
	private static final Map<String, Tex> CACHE = new HashMap<>();
	private static final Map<String, Long> STAMPS = new HashMap<>();
	private static final Map<String, Long> CHECKED = new HashMap<>();
	private static final Map<String, Tex> BY_TEXT = new HashMap<>();
	private static int counter;

	private Textures() {
	}

	public static boolean isImageFile(Path p) {
		String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
		return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif");
	}

	public static Tex load(String pathText) {
		if (pathText == null || pathText.isBlank()) {
			return null;
		}
		long now = System.currentTimeMillis();
		Long checked = CHECKED.get(pathText);
		if (checked != null && now - checked < 1000) {
			return BY_TEXT.get(pathText);
		}
		CHECKED.put(pathText, now);
		Tex result = loadNow(pathText);
		if (result == null) {
			BY_TEXT.remove(pathText);
		} else {
			BY_TEXT.put(pathText, result);
		}
		return result;
	}

	private static Tex loadNow(String pathText) {
		try {
			Path p = Path.of(pathText.trim().replace("\"", ""));
			if (!Files.isRegularFile(p) || !isImageFile(p) || Files.size(p) > MAX_BYTES) {
				return null;
			}
			long stamp = Files.getLastModifiedTime(p).toMillis();
			String key = p.toAbsolutePath().toString();
			Tex have = CACHE.get(key);
			if (have != null && STAMPS.getOrDefault(key, 0L) == stamp) {
				return have;
			}
			if (have != null) {
				Minecraft.getInstance().getTextureManager().release(have.id());
			}
			NativeImage image = read(p);
			if (image == null) {
				return null;
			}
			Identifier id = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "user/pic" + (counter++));
			Minecraft.getInstance().getTextureManager().register(id,
					new DynamicTexture(id::toString, image));
			Tex t = new Tex(id, image.getWidth(), image.getHeight());
			CACHE.put(key, t);
			STAMPS.put(key, stamp);
			return t;
		} catch (Exception e) {
			return null;
		}
	}

	public static NativeImage read(Path p) throws IOException {
		BufferedImage img = ImageIO.read(p.toFile());
		return fromBuffered(img);
	}

	public static NativeImage fromBuffered(BufferedImage img) {
		if (img == null) {
			return null;
		}
		int w = img.getWidth(), h = img.getHeight();
		int tw = w, th = h;
		if (Math.max(w, h) > MAX_SIDE) {
			double k = MAX_SIDE / (double) Math.max(w, h);
			tw = Math.max(1, (int) Math.round(w * k));
			th = Math.max(1, (int) Math.round(h * k));
		}
		NativeImage out = new NativeImage(tw, th, false);
		for (int y = 0; y < th; y++) {
			for (int x = 0; x < tw; x++) {
				int argb = img.getRGB(x * w / tw, y * h / th);
				out.setPixel(x, y, argb);
			}
		}
		return out;
	}
}
