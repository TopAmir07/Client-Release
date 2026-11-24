package dev.topclient.client.clover.spotify.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpotifyUIConfig {
    public enum Theme {
        IPOD("iPod Classic"),
        PHONE("Phone"),
        CARD("Spotify Card"),
        COMPACT("Compact"),
        MINIMAL("Text Only");

        private final String label;
        Theme(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum Accent {
        GREEN("Spotify Green", 0x1ED760),
        WHITE("White", 0xFFFFFF),
        ALBUM("Album Colour", -1),
        BLUE("Sky Blue", 0x4DA3FF),
        AMBER("Amber", 0xFFB020),
        CORAL("Coral", 0xFF6B5E);

        private final String label;
        public final int rgb;
        Accent(String label, int rgb) { this.label = label; this.rgb = rgb; }
        @Override public String toString() { return label; }
    }

    public enum Background {
        SOLID("Solid"),
        ALBUM("Album Art Blur"),
        IMAGE("Custom Image"),
        NONE("None (See-through)");

        private final String label;
        Background(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum BorderColor {
        GRAY("Gray", 0x5A5A5A),
        WHITE("White", 0xFFFFFF),
        BLACK("Black", 0x000000),
        CLEAR("Clear (see-through)", 0xFFFFFF),
        ACCENT("Accent", -1);

        private final String label;
        public final int rgb;
        BorderColor(String label, int rgb) { this.label = label; this.rgb = rgb; }
        @Override public String toString() { return label; }
    }

    public enum TimeMode {
        ELAPSED("Elapsed / Total"),
        REMAINING("Time Remaining");

        private final String label;
        TimeMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum Source {
        AUTO("Spotify, then any player"),
        SPOTIFY("Spotify app only");

        private final String label;
        Source(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public boolean enabled = true;
    public boolean sizeInitialised = false;
    public Source source = Source.AUTO;
    public boolean clickableControls = true;

    public Theme theme = Theme.CARD;
    public float posX = 0.01f;
    public float posY = 0.03f;
    public float scale = 0.8f;

    public float opacity = 1.0f;
    public Accent accent = Accent.GREEN;
    public int cornerRadius = 8;
    public int borderThickness = 1;
    public BorderColor borderColor = BorderColor.GRAY;
    public boolean textShadow = false;
    public boolean showAlbumArt = true;
    public boolean showAlbumName = true;
    public boolean showProgress = true;
    public boolean showControls = true;
    public TimeMode timeMode = TimeMode.ELAPSED;

    public Background background = Background.SOLID;
    public float backgroundOpacity = 0.9f;
    public String backgroundImage = "";
    public float imageDim = 0.45f;

    public boolean scrollLongText = true;
    public boolean hideWhenNothingPlaying = false;
    public boolean hideWhenPaused = false;
    public boolean popupOnly = false;
    public int popupSeconds = 6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SpotifyUIConfig instance = new SpotifyUIConfig();

    public static SpotifyUIConfig get() { return instance; }

    public static Path configDir() {
        return dev.topclient.client.LegitConfig.dir().resolve("spotify");
    }

    public static Path backgroundsDir() {
        return configDir().resolve("backgrounds");
    }

    private static Path file() {
        return dev.topclient.client.LegitConfig.dir().resolve("spotify.json");
    }

    public static void load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                SpotifyUIConfig loaded = GSON.fromJson(r, SpotifyUIConfig.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                System.err.println("[SpotifyUI] Could not read config, using defaults: " + e);
            }
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(configDir());
            Files.createDirectories(backgroundsDir());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            System.err.println("[SpotifyUI] Could not save config: " + e);
        }
    }

    public static void resetToDefaults() {
        instance = new SpotifyUIConfig();
        save();
    }

    public void sanitize() {
        if (theme == null) theme = Theme.CARD;
        if (accent == null) accent = Accent.GREEN;
        if (background == null) background = Background.SOLID;
        if (borderColor == null) borderColor = BorderColor.GRAY;
        if (timeMode == null) timeMode = TimeMode.ELAPSED;
        if (source == null) source = Source.AUTO;
        if (backgroundImage == null) backgroundImage = "";
        posX = clamp(posX, 0f, 1f);
        posY = clamp(posY, 0f, 1f);
        scale = clamp(scale, 0.1f, 3.0f);
        opacity = clamp(opacity, 0.1f, 1f);
        backgroundOpacity = clamp(backgroundOpacity, 0f, 1f);
        imageDim = clamp(imageDim, 0f, 0.9f);
        cornerRadius = Math.max(0, Math.min(16, cornerRadius));
        borderThickness = Math.max(0, Math.min(3, borderThickness));
        popupSeconds = Math.max(2, Math.min(30, popupSeconds));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
