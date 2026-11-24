package dev.topclient.client.clover.spotify;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import dev.topclient.client.clover.spotify.media.MediaCommand;
import dev.topclient.client.clover.spotify.media.MediaService;
import dev.topclient.client.clover.spotify.media.MediaState;
import dev.topclient.client.clover.spotify.render.HudRenderer;
import dev.topclient.client.clover.spotify.screen.ConfigScreen;
import dev.topclient.client.clover.spotify.screen.PositionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

public final class SpotifyHud {
    public static boolean hiddenByKey = false;

    private static String lastKey = "";
    private static long changeNanos = System.nanoTime();
    private static boolean wasDown = false;
    private static boolean seeking = false;

    private SpotifyHud() {}

    public static long changeNanos() {
        return changeNanos;
    }

    private static void trackChange(MediaState s) {
        String key = s.hasMedia ? s.trackKey : "";
        if (!key.equals(lastKey)) {
            lastKey = key;
            changeNanos = System.nanoTime();
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        SpotifyUIConfig cfg = SpotifyUIConfig.get();
        MediaState s = MediaService.get();
        trackChange(s);

        if (!cfg.enabled || hiddenByKey) return;
        if (mc.gui.screen() instanceof ConfigScreen || mc.gui.screen() instanceof PositionScreen) return;
        if (!s.hasMedia && cfg.hideWhenNothingPlaying) return;
        if (s.hasMedia && !s.playing && cfg.hideWhenPaused) return;

        long now = System.nanoTime();
        float master = 1f;
        if (cfg.popupOnly && mc.gui.screen() == null) {
            if (!s.hasMedia) return;
            float shownFor = (now - changeNanos) / 1_000_000_000f;
            float total = cfg.popupSeconds;
            if (shownFor > total) return;
            master = Math.min(1f, (total - shownFor) / 0.5f);
        }

        int gw = ctx.guiWidth();
        int gh = ctx.guiHeight();

        if (!cfg.sizeInitialised) {
            cfg.scale = HudRenderer.autoScale(cfg.theme, gw, gh);
            cfg.sizeInitialised = true;
            SpotifyUIConfig.save();
        }

        float w = HudRenderer.width(cfg.theme) * cfg.scale;
        float h = HudRenderer.height(cfg.theme) * cfg.scale;
        float x = cfg.posX * Math.max(0f, gw - w);
        float y = cfg.posY * Math.max(0f, gh - h);

        boolean screenOpen = mc.gui.screen() != null;
        int mouseX = -10_000;
        int mouseY = -10_000;
        if (screenOpen) {
            mouseX = (int) (mc.mouseHandler.xpos() * gw / (double) mc.getWindow().getWidth());
            mouseY = (int) (mc.mouseHandler.ypos() * gh / (double) mc.getWindow().getHeight());
        }

        HudRenderer.draw(ctx, mc, s, x, y, cfg.scale, master, true, mouseX, mouseY, changeNanos);

        boolean down = screenOpen && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && cfg.clickableControls) {
            for (HudRenderer.HitBox box : HudRenderer.HITS) {
                if (box.contains(mouseX, mouseY)) {
                    switch (box.action()) {
                        case PREVIOUS -> MediaService.send(MediaCommand.PREVIOUS);
                        case NEXT -> MediaService.send(MediaCommand.NEXT);
                        case TOGGLE -> MediaService.send(MediaCommand.TOGGLE);
                        case SEEK -> seeking = true; // drag the time bar, let go to jump there
                    }
                    break;
                }
            }
        }
        if (seeking) {
            if (down) {
                HudRenderer.seekPreview = HudRenderer.seekFraction(mouseX);
            } else {
                if (s.hasMedia && s.durationMs > 0 && HudRenderer.seekPreview >= 0f) {
                    MediaService.seek((long) (HudRenderer.seekPreview * s.durationMs));
                }
                seeking = false;
                HudRenderer.seekPreview = -1f;
            }
        } else {
            HudRenderer.seekPreview = -1f;
        }
        wasDown = down;
    }
}
