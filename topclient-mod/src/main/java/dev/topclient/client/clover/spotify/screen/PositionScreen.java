package dev.topclient.client.clover.spotify.screen;

import dev.topclient.client.clover.spotify.SpotifyHud;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import dev.topclient.client.clover.spotify.media.MediaService;
import dev.topclient.client.clover.spotify.media.MediaState;
import dev.topclient.client.clover.spotify.render.Gfx;
import dev.topclient.client.clover.spotify.render.HudRenderer;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class PositionScreen extends Screen {
    private static final int GREEN = 0xFF1ED760;

    private static final String[] PRESET_NAMES = {"Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center"};
    private static final float[][] PRESETS = {{0.01f, 0.02f}, {0.99f, 0.02f}, {0.01f, 0.98f}, {0.99f, 0.98f}, {0.5f, 0.5f}};

    private final Screen parent;
    private boolean dragging = false;
    private float grabX, grabY;
    private boolean snapX, snapY;

    public PositionScreen(Screen parent) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        SpotifyUIConfig.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private float hudW() { return HudRenderer.width(SpotifyUIConfig.get().theme) * SpotifyUIConfig.get().scale; }
    private float hudH() { return HudRenderer.height(SpotifyUIConfig.get().theme) * SpotifyUIConfig.get().scale; }
    private float hudX() { return SpotifyUIConfig.get().posX * Math.max(0f, width - hudW()); }
    private float hudY() { return SpotifyUIConfig.get().posY * Math.max(0f, height - hudH()); }

    private int presetX(int i) {
        int bw = 74, gap = 6;
        int total = PRESET_NAMES.length * bw + (PRESET_NAMES.length - 1) * gap;
        return (width - total) / 2 + i * (bw + gap);
    }

    private int presetY() { return height - 30; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        SpotifyUIConfig c = SpotifyUIConfig.get();
        ctx.fill(0, 0, width, height, 0x88000000);

        MediaState real = MediaService.get();
        MediaState state = real.hasMedia ? real : MediaState.sample();
        float x = Math.round(hudX());
        float y = Math.round(hudY());
        float w = hudW();
        float h = hudH();

        Gfx.begin(ctx, font, 1f, false);
        if (dragging) {
            if (snapX) Gfx.rect(width / 2f, 0, 1, height, 0x661ED760);
            if (snapY) Gfx.rect(0, height / 2f, width, 1, 0x661ED760);
        }

        HudRenderer.draw(ctx, minecraft, state, x, y, c.scale, 1f, false, -1, -1, SpotifyHud.changeNanos());

        Gfx.begin(ctx, font, 1f, false);
        Gfx.ring(Math.round(x) - 3, Math.round(y) - 3, Math.round(w) + 6, Math.round(h) + 6, 4, 1, GREEN);

        Gfx.textCentered("Drag the HUD to move it", width / 2f, 16, 0xFFFFFFFF, 1.3f, 1f);
        Gfx.textCentered("Scroll to resize   |   Arrow keys to nudge   |   R to reset   |   Esc or Enter when done",
                width / 2f, 34, 0xFFB3B3B3, 0.85f, 1f);

        for (int i = 0; i < PRESET_NAMES.length; i++) {
            int bx = presetX(i), by = presetY();
            boolean hov = mouseX >= bx && mouseX < bx + 74 && mouseY >= by && mouseY < by + 18;
            Gfx.roundRect(bx, by, 74, 18, 9, hov ? 0xFF2E2E2E : 0xFF1C1C1C);
            Gfx.ring(bx, by, 74, 18, 9, 1, hov ? 0xFF555555 : 0xFF333333);
            Gfx.textCentered(PRESET_NAMES[i], bx + 37, by + (18 - 7f) / 2f, 0xFFFFFFFF, 0.9f, 1f);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x();
            int my = (int) click.y();
            for (int i = 0; i < PRESET_NAMES.length; i++) {
                int bx = presetX(i), by = presetY();
                if (mx >= bx && mx < bx + 74 && my >= by && my < by + 18) {
                    SpotifyUIConfig c = SpotifyUIConfig.get();
                    c.posX = PRESETS[i][0];
                    c.posY = PRESETS[i][1];
                    return true;
                }
            }
            if (mx >= hudX() && mx < hudX() + hudW() && my >= hudY() && my < hudY() + hudH()) {
                dragging = true;
                grabX = (float) (mx - hudX());
                grabY = (float) (my - hudY());
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (dragging) {
            SpotifyUIConfig c = SpotifyUIConfig.get();
            float freeX = Math.max(1f, width - hudW());
            float freeY = Math.max(1f, height - hudH());
            float nx = (float) click.x() - grabX;
            float ny = (float) click.y() - grabY;
            snapX = Math.abs(nx + hudW() / 2f - width / 2f) < 4f;
            snapY = Math.abs(ny + hudH() / 2f - height / 2f) < 4f;
            if (snapX) nx = width / 2f - hudW() / 2f;
            if (snapY) ny = height / 2f - hudH() / 2f;
            if (nx < 3f) nx = 0f;
            if (ny < 3f) ny = 0f;
            if (nx > freeX - 3f) nx = freeX;
            if (ny > freeY - 3f) ny = freeY;
            c.posX = Math.max(0f, Math.min(1f, nx / freeX));
            c.posY = Math.max(0f, Math.min(1f, ny / freeY));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        snapX = false;
        snapY = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        SpotifyUIConfig c = SpotifyUIConfig.get();
        c.scale = Math.max(0.1f, Math.min(3.0f, Math.round((c.scale + (float) vertical * 0.05f) * 20f) / 20f));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        SpotifyUIConfig c = SpotifyUIConfig.get();
        float freeX = Math.max(1f, width - hudW());
        float freeY = Math.max(1f, height - hudH());
        int key = input.key();
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> c.posX = Math.max(0f, c.posX - 1f / freeX);
            case GLFW.GLFW_KEY_RIGHT -> c.posX = Math.min(1f, c.posX + 1f / freeX);
            case GLFW.GLFW_KEY_UP -> c.posY = Math.max(0f, c.posY - 1f / freeY);
            case GLFW.GLFW_KEY_DOWN -> c.posY = Math.min(1f, c.posY + 1f / freeY);
            case GLFW.GLFW_KEY_R -> {
                c.posX = 0.01f;
                c.posY = 0.03f;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onClose();
            default -> {
                return super.keyPressed(input);
            }
        }
        return true;
    }
}
