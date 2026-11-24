package dev.topclient.client.clover.spotify.media;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

final class WindowsProvider implements MediaProvider {
    private final Consumer<MediaState> sink;
    private volatile SpotifyUIConfig.Source source;
    private volatile boolean running;
    private volatile Process process;
    private Thread thread;

    private Path scriptFile;
    private Path cmdFile;

    private String lastKey = "";
    private ArtProcessor.Result lastArt;

    WindowsProvider(Consumer<MediaState> sink, SpotifyUIConfig.Source source) {
        this.sink = sink;
        this.source = source;
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::run, "SpotifyUI-Media");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
    }

    @Override
    public void send(MediaCommand command) {
        String line = switch (command) {
            case NEXT -> "next";
            case PREVIOUS -> "prev";
            case TOGGLE -> "toggle";
        };
        writeCommand(line);
    }

    @Override
    public void seek(long positionMs) {
        writeCommand("seek:" + Math.max(0L, positionMs));
    }

    @Override
    public void setSource(SpotifyUIConfig.Source newSource) {
        this.source = newSource;
        writeCommand(newSource == SpotifyUIConfig.Source.SPOTIFY ? "source:spotify" : "source:auto");
    }

    @Override
    public String name() {
        return "Windows media controls";
    }

    private synchronized void writeCommand(String line) {
        if (cmdFile == null) return;
        try {
            Files.writeString(cmdFile, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[SpotifyUI] Could not send command: " + e);
        }
    }

    private void run() {
        try {
            extractScript();
        } catch (IOException e) {
            System.err.println("[SpotifyUI] Could not prepare media script: " + e);
            sink.accept(MediaState.NONE);
            return;
        }

        int failures = 0;
        while (running) {
            long started = System.currentTimeMillis();
            try {
                runOnce();
            } catch (Exception e) {
                System.err.println("[SpotifyUI] Media reader stopped: " + e);
            }
            if (!running) break;
            sink.accept(MediaState.NONE);
            lastKey = "";
            lastArt = null;
            if (System.currentTimeMillis() - started > 15_000L) failures = 0;
            failures++;
            try {
                Thread.sleep(Math.min(10_000L, 1500L * failures));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void extractScript() throws IOException {
        Path dir = SpotifyUIConfig.configDir();
        Files.createDirectories(dir);
        scriptFile = dir.resolve("media_windows.ps1");
        cmdFile = dir.resolve("media.cmd");
        try (InputStream in = WindowsProvider.class.getResourceAsStream("/assets/topclient/spotify/media_windows.ps1")) {
            if (in == null) throw new IOException("media_windows.ps1 is missing from the mod jar");
            Files.copy(in, scriptFile, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(cmdFile);
    }

    private void runOnce() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("powershell.exe");
        cmd.add("-NoLogo");
        cmd.add("-NoProfile");
        cmd.add("-NonInteractive");
        cmd.add("-WindowStyle");
        cmd.add("Hidden");
        // The reader is passed to PowerShell as text, so no execution-policy change is needed.
        cmd.add("-Command");
        cmd.add("& ([scriptblock]::Create([IO.File]::ReadAllText($env:AFK_READER))) -CmdFile $env:AFK_CMDFILE -ParentPid $env:AFK_PARENT -Source $env:AFK_SOURCE");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("AFK_READER", scriptFile.toString());
        pb.environment().put("AFK_CMDFILE", cmdFile.toString());
        pb.environment().put("AFK_PARENT", Long.toString(ProcessHandle.current().pid()));
        pb.environment().put("AFK_SOURCE", source == SpotifyUIConfig.Source.SPOTIFY ? "spotify" : "auto");
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        process = p;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                handle(line);
            }
        } finally {
            p.destroy();
        }
    }

    private void handle(String raw) {
        String line = raw;
        if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
        line = line.trim();
        if (line.isEmpty() || line.charAt(0) != '{') return;

        JsonObject o;
        try {
            o = JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        if (!bool(o, "ok")) {
            lastKey = "";
            lastArt = null;
            MediaService.setArtStatus("Waiting for a song");
            sink.accept(MediaState.NONE);
            return;
        }

        String title = str(o, "title");
        String artist = str(o, "artist");
        String album = str(o, "album");
        String status = str(o, "status");
        String app = prettyApp(str(o, "app"));
        double dur = num(o, "dur");
        double pos = num(o, "pos");

        String key = title + "\u0001" + artist + "\u0001" + album;
        if (!key.equals(lastKey)) {
            lastKey = key;
            lastArt = null;
            MediaService.setArtStatus("Loading...");
        }
        if (o.has("thumb") && !o.get("thumb").isJsonNull()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(o.get("thumb").getAsString());
                ArtProcessor.Result r = ArtProcessor.process(bytes);
                if (r != null) {
                    lastArt = r;
                    MediaService.setArtStatus("Loaded");
                } else {
                    MediaService.setArtStatus("Image format not supported");
                }
            } catch (Throwable e) {
                MediaService.setArtStatus("Could not decode image");
                System.err.println("[SpotifyUI] Could not read album art: " + e);
            }
        } else if (lastArt == null) {
            String err = str(o, "terr");
            if (num(o, "tries") >= 6) {
                MediaService.setArtStatus(err.isEmpty() ? "Player sent no cover image" : "Windows error: " + err);
            }
        }

        boolean hasMedia = !title.isBlank() || !artist.isBlank();
        boolean playing = "Playing".equalsIgnoreCase(status);
        ArtProcessor.Result art = lastArt;

        long posMs = (long) (pos * 1000.0);
        if (MediaService.holdingSeek()) { // just seeked: Windows may still report the old spot
            MediaState cur = MediaService.get();
            if (key.equals(cur.trackKey)) posMs = cur.elapsedMs(System.nanoTime());
        }
        sink.accept(new MediaState(
                hasMedia, title, artist, album, app, playing,
                (long) (dur * 1000.0), posMs, System.nanoTime(), key,
                art == null ? null : art.rounded,
                art == null ? null : art.flat,
                art == null ? MediaState.DEFAULT_ACCENT : art.accent));
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return "";
        try {
            return e.getAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return 0.0;
        try {
            double d = e.getAsDouble();
            return Double.isNaN(d) || Double.isInfinite(d) ? 0.0 : d;
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return false;
        try {
            return e.getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }

    static String prettyApp(String id) {
        if (id == null || id.isBlank()) return "";
        String lower = id.toLowerCase();
        if (lower.contains("spotify")) return "Spotify";
        String s = id;
        int bang = s.indexOf('!');
        if (bang > 0) s = s.substring(0, bang);
        if (s.toLowerCase().endsWith(".exe")) s = s.substring(0, s.length() - 4);
        return s;
    }
}
