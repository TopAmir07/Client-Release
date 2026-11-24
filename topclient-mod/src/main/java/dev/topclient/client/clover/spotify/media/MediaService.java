package dev.topclient.client.clover.spotify.media;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class MediaService {
    private static final AtomicReference<MediaState> STATE = new AtomicReference<>(MediaState.NONE);
    private static MediaProvider provider;
    private static volatile String artStatus = "Waiting for a song";

    public static String artStatus() {
        return artStatus;
    }

    static void setArtStatus(String status) {
        artStatus = status == null ? "" : status;
    }

    private MediaService() {}

    public static synchronized void start() {
        if (provider != null) return;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            provider = new WindowsProvider(STATE::set, SpotifyUIConfig.get().source);
        } else if (os.contains("mac")) {
            provider = new MacProvider(STATE::set);
        } else {
            provider = new LinuxProvider(STATE::set);
        }
        provider.start();
        if (!hookAdded) {
            hookAdded = true;
            Runtime.getRuntime().addShutdownHook(new Thread(MediaService::stop, "Clover-Spotify-Shutdown"));
        }
    }

    private static boolean hookAdded;

    public static synchronized void stop() {
        if (provider != null) provider.stop();
        provider = null;
        STATE.set(MediaState.NONE);
    }

    public static synchronized boolean running() {
        return provider != null;
    }

    public static MediaState get() {
        return STATE.get();
    }

    public static void send(MediaCommand command) {
        MediaProvider p = provider;
        if (p != null) p.send(command);
    }

    /** Jump to a point in the current song. The bar moves at once; Windows follows a moment later. */
    public static void seek(long positionMs) {
        MediaProvider p = provider;
        MediaState s = STATE.get();
        if (p == null || !s.hasMedia || s.durationMs <= 0) return;
        long ms = Math.max(0L, Math.min(s.durationMs - 500L, positionMs));
        p.seek(ms);
        STATE.set(new MediaState(s.hasMedia, s.title, s.artist, s.album, s.source, s.playing, s.durationMs, ms,
                System.nanoTime(), s.trackKey, s.art, s.artFlat, s.accentRgb));
        seekHoldUntil = System.nanoTime() + 1_500_000_000L;
    }

    /** Seek by a number of seconds (negative = back). */
    public static void seekBy(long deltaMs) {
        MediaState s = STATE.get();
        if (!s.hasMedia) return;
        seek(s.elapsedMs(System.nanoTime()) + deltaMs);
    }

    // For a moment after seeking, ignore older positions still on their way from Windows.
    private static volatile long seekHoldUntil;

    static boolean holdingSeek() {
        return System.nanoTime() < seekHoldUntil;
    }

    public static void applySource() {
        MediaProvider p = provider;
        if (p != null) p.setSource(SpotifyUIConfig.get().source);
    }

    public static String providerName() {
        MediaProvider p = provider;
        return p == null ? "Not running" : p.name();
    }
}
