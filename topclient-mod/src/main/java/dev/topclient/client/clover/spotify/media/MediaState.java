package dev.topclient.client.clover.spotify.media;

import java.awt.image.BufferedImage;

public final class MediaState {
    public static final int DEFAULT_ACCENT = 0x1ED760;

    public static final MediaState NONE = new MediaState(
            false, "", "", "", "", false, 0L, 0L, System.nanoTime(), "", null, null, DEFAULT_ACCENT);

    public final boolean hasMedia;
    public final String title;
    public final String artist;
    public final String album;
    public final String source;
    public final boolean playing;
    public final long durationMs;
    public final long positionMs;
    public final long receivedNanos;
    public final String trackKey;
    public final BufferedImage art;
    public final BufferedImage artFlat;
    public final int accentRgb;

    public MediaState(boolean hasMedia, String title, String artist, String album, String source,
                      boolean playing, long durationMs, long positionMs, long receivedNanos,
                      String trackKey, BufferedImage art, BufferedImage artFlat, int accentRgb) {
        this.hasMedia = hasMedia;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.source = source == null ? "" : source;
        this.playing = playing;
        this.durationMs = Math.max(0L, durationMs);
        this.positionMs = Math.max(0L, positionMs);
        this.receivedNanos = receivedNanos;
        this.trackKey = trackKey == null ? "" : trackKey;
        this.art = art;
        this.artFlat = artFlat;
        this.accentRgb = accentRgb;
    }

    public long elapsedMs(long nowNanos) {
        long pos = positionMs;
        if (playing) {
            pos += Math.max(0L, (nowNanos - receivedNanos) / 1_000_000L);
        }
        if (durationMs > 0L && pos > durationMs) pos = durationMs;
        return Math.max(0L, pos);
    }

    public static MediaState sample() {
        return new MediaState(true, "Voyager", "Daft Punk", "Discovery", "Spotify",
                true, 227_000L, 6_000L, 0L, "sample", null, null, DEFAULT_ACCENT);
    }
}
