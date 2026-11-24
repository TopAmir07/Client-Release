package dev.topclient.client.clover.spotify.render;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import dev.topclient.client.clover.spotify.media.ArtProcessor;
import dev.topclient.client.clover.spotify.media.MediaState;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class Textures {
    private static int counter = 0;

    private static BufferedImage artSource;
    private static Identifier artId;

    private static String bgKey = "";
    private static Identifier bgId;

    private static String customName = "";
    private static long customStamp = -1L;
    private static BufferedImage customImage;

    private Textures() {}

    public static Identifier art(MediaState state) {
        BufferedImage img = state.art;
        if (img == artSource) return artId;
        release(artId);
        artSource = img;
        artId = img == null ? null : upload(img, "art");
        return artId;
    }

    public static Identifier background(String key, Supplier<BufferedImage> baker) {
        if (key.equals(bgKey)) return bgId;
        release(bgId);
        bgKey = key;
        bgId = null;
        try {
            BufferedImage baked = baker.get();
            if (baked != null) bgId = upload(baked, "bg");
        } catch (Exception e) {
            System.err.println("[SpotifyUI] Could not build background: " + e);
        }
        return bgId;
    }

    public static String customKey(String name) {
        loadCustom(name);
        return name + "@" + customStamp;
    }

    public static BufferedImage customImage(String name) {
        loadCustom(name);
        return customImage;
    }

    public static List<String> listBackgrounds() {
        List<String> out = new ArrayList<>();
        Path dir = SpotifyUIConfig.backgroundsDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String l = n.toLowerCase(Locale.ROOT);
                        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg");
                    })
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    private static void loadCustom(String name) {
        if (name == null || name.isBlank()) {
            customName = "";
            customStamp = -1L;
            customImage = null;
            return;
        }
        try {
            Path dir = SpotifyUIConfig.backgroundsDir().toAbsolutePath().normalize();
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                customName = name;
                customStamp = -1L;
                customImage = null;
                return;
            }
            long stamp = Files.getLastModifiedTime(file).toMillis();
            if (name.equals(customName) && stamp == customStamp) return;
            customName = name;
            customStamp = stamp;
            customImage = ImageIO.read(file.toFile());
        } catch (Exception e) {
            customName = name;
            customStamp = -1L;
            customImage = null;
        }
    }

    private static Identifier upload(BufferedImage img, String prefix) {
        try {
            byte[] png = ArtProcessor.toPng(img);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            DynamicTexture texture = new DynamicTexture(() -> "topclient_spotify_" + prefix, image);
            texture.upload();
            Identifier id = Identifier.fromNamespaceAndPath("topclient", "spotify/" + prefix + "_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        } catch (Exception e) {
            System.err.println("[SpotifyUI] Could not upload texture: " + e);
            return null;
        }
    }

    private static void release(Identifier id) {
        if (id == null) return;
        try {
            Minecraft.getInstance().getTextureManager().release(id);
        } catch (Exception ignored) {
        }
    }
}
