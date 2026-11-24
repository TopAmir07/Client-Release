package dev.topclient.client.clover.spotify.media;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

abstract class PollProvider implements MediaProvider {
    protected record Raw(String status, String title, String artist, String album,
                         double posSec, double durSec, String artUrl, String app) {}

    private final Consumer<MediaState> sink;
    private ScheduledExecutorService executor;
    private String lastKey = "";
    private ArtProcessor.Result lastArt;

    protected PollProvider(Consumer<MediaState> sink) {
        this.sink = sink;
    }

    protected abstract Raw poll() throws Exception;

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SpotifyUI-Media");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, 0L, 1000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (executor != null) executor.shutdownNow();
    }

    @Override
    public void setSource(SpotifyUIConfig.Source source) {
    }

    private void tick() {
        try {
            Raw r = poll();
            if (r == null || (r.title().isBlank() && r.artist().isBlank())) {
                lastKey = "";
                lastArt = null;
                sink.accept(MediaState.NONE);
                return;
            }
            String key = r.title() + "\u0001" + r.artist() + "\u0001" + r.album();
            if (!key.equals(lastKey)) {
                lastKey = key;
                lastArt = null;
                if (r.artUrl() != null && !r.artUrl().isBlank()) {
                    try {
                        byte[] bytes = fetch(r.artUrl());
                        if (bytes != null) lastArt = ArtProcessor.process(bytes);
                        MediaService.setArtStatus(lastArt != null ? "Loaded" : "No cover image");
                    } catch (Exception e) {
                        System.err.println("[SpotifyUI] Could not load album art: " + e);
                    }
                }
            }
            ArtProcessor.Result art = lastArt;
            sink.accept(new MediaState(
                    true, r.title(), r.artist(), r.album(), r.app(),
                    "playing".equalsIgnoreCase(r.status()),
                    (long) (r.durSec() * 1000.0), (long) (r.posSec() * 1000.0),
                    System.nanoTime(), key,
                    art == null ? null : art.rounded,
                    art == null ? null : art.flat,
                    art == null ? MediaState.DEFAULT_ACCENT : art.accent));
        } catch (Exception e) {
            sink.accept(MediaState.NONE);
        }
    }

    private byte[] fetch(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"file".equalsIgnoreCase(uri.getScheme())) return null;
        return Files.readAllBytes(Path.of(uri));
    }

    protected static String run(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return null;
        }
        if (p.exitValue() != 0) return null;
        return out.toString(StandardCharsets.UTF_8).trim();
    }

    protected static void fire(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.start();
        } catch (IOException ignored) {
        }
    }

    protected static double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0.0;
        }
    }
}

final class LinuxProvider extends PollProvider {
    private static final String SEP = "\u001f";

    LinuxProvider(Consumer<MediaState> sink) {
        super(sink);
    }

    @Override
    protected Raw poll() throws Exception {
        String fmt = "{{status}}" + SEP + "{{xesam:title}}" + SEP + "{{xesam:artist}}" + SEP
                + "{{xesam:album}}" + SEP + "{{position}}" + SEP + "{{mpris:length}}" + SEP
                + "{{mpris:artUrl}}" + SEP + "{{playerName}}";
        String out = run(List.of("playerctl", "--player=spotify,%any", "metadata", "--format", fmt));
        if (out == null || out.isBlank()) return null;
        String[] p = out.split(SEP, -1);
        if (p.length < 8) return null;
        return new Raw(p[0], p[1], p[2], p[3], parse(p[4]) / 1_000_000.0, parse(p[5]) / 1_000_000.0, p[6], p[7]);
    }

    @Override
    public void send(MediaCommand command) {
        String c = switch (command) {
            case NEXT -> "next";
            case PREVIOUS -> "previous";
            case TOGGLE -> "play-pause";
        };
        fire(List.of("playerctl", "--player=spotify,%any", c));
    }

    @Override
    public String name() {
        return "playerctl (Linux)";
    }
}

final class MacProvider extends PollProvider {
    private static final String SCRIPT = String.join("\n",
            "tell application \"System Events\" to set isRunning to (name of processes) contains \"Spotify\"",
            "if isRunning then",
            "  tell application \"Spotify\"",
            "    set sep to character id 31",
            "    return (player state as string) & sep & (name of current track) & sep & (artist of current track) & sep & (album of current track) & sep & (player position as string) & sep & ((duration of current track) as string) & sep & (artwork url of current track)",
            "  end tell",
            "else",
            "  return \"\"",
            "end if");

    MacProvider(Consumer<MediaState> sink) {
        super(sink);
    }

    @Override
    protected Raw poll() throws Exception {
        String out = run(List.of("osascript", "-e", SCRIPT));
        if (out == null || out.isBlank()) return null;
        String[] p = out.split("\u001f", -1);
        if (p.length < 7) return null;
        return new Raw(p[0], p[1], p[2], p[3], parse(p[4]), parse(p[5]) / 1000.0, p[6], "Spotify");
    }

    @Override
    public void send(MediaCommand command) {
        String c = switch (command) {
            case NEXT -> "next track";
            case PREVIOUS -> "previous track";
            case TOGGLE -> "playpause";
        };
        fire(List.of("osascript", "-e", "tell application \"Spotify\" to " + c));
    }

    @Override
    public String name() {
        return "Spotify (macOS)";
    }
}
