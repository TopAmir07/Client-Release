package dev.topclient.client.clover.spotify.screen;

import dev.topclient.client.clover.spotify.SpotifyHud;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.Accent;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.Background;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.BorderColor;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.Source;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.Theme;
import dev.topclient.client.clover.spotify.config.SpotifyUIConfig.TimeMode;
import dev.topclient.client.clover.spotify.media.MediaService;
import dev.topclient.client.clover.spotify.media.MediaState;
import dev.topclient.client.clover.spotify.render.Gfx;
import dev.topclient.client.clover.spotify.render.HudRenderer;
import dev.topclient.client.clover.spotify.render.Textures;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConfigScreen extends Screen {
    private static final int PANEL = 0xFF141414;
    private static final int PANEL_EDGE = 0xFF2A2A2A;
    private static final int ROW_HOVER = 0xFF1B1B1B;
    private static final int CONTROL = 0xFF242424;
    private static final int CONTROL_EDGE = 0xFF353535;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xFFA0A0A0;
    private static final int DIMTEXT = 0xFF6E6E6E;
    private static final int GREEN = 0xFF1ED760;

    private static final String[] TAB_NAMES = {"General", "Layout", "Appearance", "Background", "Behavior"};

    private final Screen parent;
    private final List<List<Row>> pages = new ArrayList<>();
    private int tab = 0;
    private float scroll = 0f;
    private Row active = null;

    private int px, py, pw, ph, sideW, cx, cy, cw, ch, prevX, prevW;
    private boolean showPreview;

    private long resetArmedAt = 0L;

    public ConfigScreen(Screen parent) {
        super(Component.literal("SpotifyUI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pages.clear();
        buildPages();
    }

    @Override
    public void onClose() {
        SpotifyUIConfig.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        SpotifyUIConfig.save();
    }

    private void buildPages() {
        SpotifyUIConfig c = SpotifyUIConfig.get();

        List<Row> general = new ArrayList<>();
        general.add(new Header("Status"));
        general.add(new Info("Now playing", () -> {
            MediaState s = MediaService.get();
            if (!s.hasMedia) return "Nothing playing";
            String name = s.title + (s.artist.isBlank() ? "" : " - " + s.artist);
            return name;
        }));
        general.add(new Info("Source", () -> {
            MediaState s = MediaService.get();
            return s.hasMedia && !s.source.isBlank() ? s.source : MediaService.providerName();
        }));
        general.add(new Info("Album art", MediaService::artStatus));
        general.add(new Header("Display"));
        general.add(new Toggle("Show HUD", "Turns the whole overlay on or off", () -> c.enabled, v -> c.enabled = v));
        general.add(new Cycle<Source>("Music source", "Which player to read from",
                () -> Arrays.asList(Source.values()), () -> c.source,
                v -> {
                    c.source = v;
                    MediaService.applySource();
                }, Source::toString));
        general.add(new Toggle("Clickable controls", "Click skip and play when chat or inventory is open",
                () -> c.clickableControls, v -> c.clickableControls = v));
        general.add(new Header("Keys"));
        general.add(new Info("Skip / pause", () -> "Options > Controls > SpotifyUI"));
        general.add(new Header("Reset"));
        general.add(new Button("Reset all settings", "Restores every option to its default",
                () -> System.currentTimeMillis() - resetArmedAt < 3000L ? "Click to confirm" : "Reset",
                () -> {
                    if (System.currentTimeMillis() - resetArmedAt < 3000L) {
                        SpotifyUIConfig.resetToDefaults();
                        resetArmedAt = 0L;
                        if (minecraft != null) minecraft.gui.setScreen(new ConfigScreen(parent));
                    } else {
                        resetArmedAt = System.currentTimeMillis();
                    }
                }));
        pages.add(general);

        List<Row> layout = new ArrayList<>();
        layout.add(new Header("Design"));
        layout.add(new Cycle<Theme>("Theme", "Resets the size to fit your screen",
                () -> Arrays.asList(Theme.values()), () -> c.theme,
                v -> {
                    c.theme = v;
                    c.scale = HudRenderer.autoScale(v, width, height);
                }, Theme::toString));
        layout.add(new Header("Size and position"));
        layout.add(new Slider("Size", "Make the HUD bigger or smaller", 0.1f, 3.0f, 0.05f,
                () -> c.scale, v -> c.scale = v, v -> String.format(Locale.ROOT, "%.2fx", v)));
        layout.add(new Button("Auto-fit size", "Picks a size that suits your screen", () -> "Fit",
                () -> c.scale = HudRenderer.autoScale(c.theme, width, height)));
        layout.add(new Slider("Horizontal", "Left to right", 0f, 1f, 0.01f,
                () -> c.posX, v -> c.posX = v, ConfigScreen::percent));
        layout.add(new Slider("Vertical", "Top to bottom", 0f, 1f, 0.01f,
                () -> c.posY, v -> c.posY = v, ConfigScreen::percent));
        layout.add(new Button("Move on screen", "Drag the HUD where you want it", () -> "Open",
                () -> {
                    SpotifyUIConfig.save();
                    if (minecraft != null) minecraft.gui.setScreen(new PositionScreen(this));
                }));
        pages.add(layout);

        List<Row> look = new ArrayList<>();
        look.add(new Header("Colour"));
        look.add(new Slider("Transparency", "Lower is more see-through", 0.1f, 1f, 0.05f,
                () -> c.opacity, v -> c.opacity = v, ConfigScreen::percent));
        look.add(new Cycle<Accent>("Accent colour", "Progress bar and highlights",
                () -> Arrays.asList(Accent.values()), () -> c.accent, v -> c.accent = v, Accent::toString));
        look.add(new Header("Shape"));
        look.add(new Slider("Corner roundness", "Card, Compact and Text themes", 0, 16, 1,
                () -> (float) c.cornerRadius, v -> c.cornerRadius = Math.round(v),
                v -> Math.round(v) + " px"));
        look.add(new Slider("Border thickness", "0 removes the border", 0, 3, 1,
                () -> (float) c.borderThickness, v -> c.borderThickness = Math.round(v),
                v -> Math.round(v) == 0 ? "Off" : Math.round(v) + " px"));
        look.add(new Cycle<BorderColor>("Border colour", "",
                () -> Arrays.asList(BorderColor.values()), () -> c.borderColor, v -> c.borderColor = v,
                BorderColor::toString));
        look.add(new Header("Content"));
        look.add(new Toggle("Album art", "", () -> c.showAlbumArt, v -> c.showAlbumArt = v));
        look.add(new Toggle("Album name", "iPod theme", () -> c.showAlbumName, v -> c.showAlbumName = v));
        look.add(new Toggle("Progress bar", "", () -> c.showProgress, v -> c.showProgress = v));
        look.add(new Toggle("Control buttons", "iPod, Phone and Card themes", () -> c.showControls, v -> c.showControls = v));
        look.add(new Cycle<TimeMode>("Time display", "",
                () -> Arrays.asList(TimeMode.values()), () -> c.timeMode, v -> c.timeMode = v, TimeMode::toString));
        look.add(new Toggle("Text shadow", "Helps on see-through backgrounds", () -> c.textShadow, v -> c.textShadow = v));
        pages.add(look);

        List<Row> bg = new ArrayList<>();
        bg.add(new Header("Background"));
        bg.add(new Cycle<Background>("Style", "None removes the background completely",
                () -> Arrays.asList(Background.values()), () -> c.background, v -> c.background = v,
                Background::toString));
        bg.add(new Slider("Opacity", "Lower is more see-through", 0f, 1f, 0.05f,
                () -> c.backgroundOpacity, v -> c.backgroundOpacity = v, ConfigScreen::percent));
        bg.add(new Header("Custom image"));
        bg.add(new Cycle<String>("Image", "Pick a file from the folder below",
                () -> {
                    List<String> l = new ArrayList<>();
                    l.add("None");
                    l.addAll(Textures.listBackgrounds());
                    return l;
                },
                () -> c.backgroundImage.isBlank() ? "None" : c.backgroundImage,
                v -> {
                    if (v.equals("None")) {
                        c.backgroundImage = "";
                    } else {
                        c.backgroundImage = v;
                        c.background = Background.IMAGE;
                    }
                }, s -> s));
        bg.add(new Slider("Image darkness", "Keeps the text readable", 0f, 0.9f, 0.05f,
                () -> c.imageDim, v -> c.imageDim = v, ConfigScreen::percent));
        bg.add(new Button("Backgrounds folder", "Drop .png or .jpg files in here", () -> "Open", this::openFolder));
        pages.add(bg);

        List<Row> behave = new ArrayList<>();
        behave.add(new Header("Text"));
        behave.add(new Toggle("Scroll long titles", "Otherwise they are shortened with ...",
                () -> c.scrollLongText, v -> c.scrollLongText = v));
        behave.add(new Header("Visibility"));
        behave.add(new Toggle("Hide when nothing plays", "", () -> c.hideWhenNothingPlaying, v -> c.hideWhenNothingPlaying = v));
        behave.add(new Toggle("Hide when paused", "", () -> c.hideWhenPaused, v -> c.hideWhenPaused = v));
        behave.add(new Toggle("Pop-up mode", "Only appears when the song changes",
                () -> c.popupOnly, v -> c.popupOnly = v));
        behave.add(new Slider("Pop-up length", "How long it stays on screen", 2, 30, 1,
                () -> (float) c.popupSeconds, v -> c.popupSeconds = Math.round(v),
                v -> Math.round(v) + " s"));
        pages.add(behave);
    }

    private static String percent(float v) {
        return Math.round(v * 100f) + "%";
    }

    private void openFolder() {
        try {
            Path dir = SpotifyUIConfig.backgroundsDir();
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String opener = os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open";
            new ProcessBuilder(opener, dir.toAbsolutePath().toString()).start();
        } catch (IOException e) {
            System.err.println("[SpotifyUI] Could not open folder: " + e);
        }
    }

    private void layout() {
        pw = Math.min(width - 20, 560);
        ph = Math.min(height - 20, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sideW = 88;
        showPreview = pw >= 500;
        prevW = showPreview ? 150 : 0;
        cx = px + sideW + 14;
        cy = py + 40;
        cw = pw - sideW - 14 - 14 - (showPreview ? prevW + 14 : 0);
        ch = ph - 40 - 12;
        prevX = px + pw - 14 - prevW;
    }

    private List<Row> rows() {
        return pages.get(tab);
    }

    private int totalHeight() {
        int t = 0;
        for (Row r : rows()) t += r.height();
        return t;
    }

    private float maxScroll() {
        return Math.max(0, totalHeight() - ch);
    }

    private void ui(GuiGraphicsExtractor ctx) {
        Gfx.begin(ctx, font, 1f, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        scroll = Math.max(0f, Math.min(scroll, maxScroll()));
        ui(ctx);

        ctx.fill(0, 0, width, height, 0xC0000000);
        Gfx.roundRect(px, py, pw, ph, 8, PANEL);
        Gfx.ring(px, py, pw, ph, 8, 1, PANEL_EDGE);

        Gfx.equalizer(px + 20, py + 19, 12, GREEN, MediaService.get().playing);
        Gfx.text("SpotifyUI", px + 34, py + 13, TEXT, 1.4f);
        Gfx.textRight("by TopAmir_", px + pw - 16, py + 16, DIMTEXT, 0.8f, 1f);
        Gfx.rect(px + 1, py + 34, pw - 2, 1, PANEL_EDGE);

        Gfx.rect(px + sideW, py + 35, 1, ph - 36, PANEL_EDGE);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int ty = py + 44 + i * 24;
            boolean sel = i == tab;
            boolean hov = inside(mouseX, mouseY, px + 8, ty, sideW - 16, 20);
            if (sel) {
                Gfx.roundRect(px + 8, ty, sideW - 16, 20, 4, 0xFF222222);
                Gfx.roundRect(px + 8, ty + 4, 2, 12, 1, GREEN);
            } else if (hov) {
                Gfx.roundRect(px + 8, ty, sideW - 16, 20, 4, 0xFF1A1A1A);
            }
            Gfx.text(TAB_NAMES[i], px + 18, ty + (20 - 7f) / 2f, sel ? TEXT : SUBTEXT, 1f);
        }
        int bx = px + 8, by = py + ph - 30, bw = sideW - 16;
        boolean doneHover = inside(mouseX, mouseY, bx, by, bw, 20);
        Gfx.roundRect(bx, by, bw, 20, 10, doneHover ? 0xFF3BE477 : GREEN);
        Gfx.textCentered("Done", bx + bw / 2f, by + (20 - 7f) / 2f, 0xFF0A0A0A, 1f, 1f);

        ctx.enableScissor(cx - 6, cy, cx + cw + 6, cy + ch);
        int y = cy - Math.round(scroll);
        for (Row r : rows()) {
            int h = r.height();
            if (y + h > cy - 2 && y < cy + ch + 2) {
                boolean over = inside(mouseX, mouseY, cx - 4, y, cw + 8, h)
                        && inside(mouseX, mouseY, cx, cy, cw, ch);
                if (over && r.interactive()) Gfx.roundRect(cx - 4, y, cw + 8, h - 2, 5, ROW_HOVER);
                r.draw(this, cx, y, cw, mouseX, mouseY);
            }
            y += h;
        }
        ctx.disableScissor();
        ui(ctx);

        if (maxScroll() > 0) {
            float ratio = ch / (float) totalHeight();
            int barH = Math.max(16, Math.round(ch * ratio));
            int barY = cy + Math.round((ch - barH) * (scroll / maxScroll()));
            Gfx.roundRect(cx + cw + 6, barY, 2, barH, 1, 0xFF444444);
        }

        if (showPreview) drawPreview(ctx);
        ui(ctx);
    }

    private void drawPreview(GuiGraphicsExtractor ctx) {
        SpotifyUIConfig c = SpotifyUIConfig.get();
        ui(ctx);
        Gfx.roundRect(prevX, cy, prevW, ch, 6, 0xFF0C0C0C);
        Gfx.ring(prevX, cy, prevW, ch, 6, 1, PANEL_EDGE);
        Gfx.textCentered("PREVIEW", prevX + prevW / 2f, cy + 8, DIMTEXT, 0.75f, 1f);

        MediaState real = MediaService.get();
        MediaState state = real.hasMedia ? real : MediaState.sample();
        float w = HudRenderer.width(c.theme);
        float h = HudRenderer.height(c.theme);
        float availW = prevW - 16;
        float availH = ch - 34;
        float s = Math.min(Math.min(availW / w, availH / h), 1.6f);
        float x = prevX + (prevW - w * s) / 2f;
        float y = cy + 22 + (availH - h * s) / 2f;
        HudRenderer.draw(ctx, minecraft, state, x, y, s, 1f, false, -1, -1, SpotifyHud.changeNanos());
        ui(ctx);
        if (!real.hasMedia) {
            Gfx.textCentered("Sample data", prevX + prevW / 2f, cy + ch - 12, DIMTEXT, 0.75f, 1f);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int mx = (int) click.x();
        int my = (int) click.y();

        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (inside(mx, my, px + 8, py + 44 + i * 24, sideW - 16, 20)) {
                tab = i;
                scroll = 0f;
                active = null;
                return true;
            }
        }
        if (inside(mx, my, px + 8, py + ph - 30, sideW - 16, 20)) {
            onClose();
            return true;
        }
        if (inside(mx, my, cx, cy, cw, ch)) {
            int y = cy - Math.round(scroll);
            for (Row r : rows()) {
                int h = r.height();
                if (my >= y && my < y + h) {
                    if (r.click(this, cx, y, cw, mx, my)) {
                        active = r;
                        SpotifyUIConfig.save();
                    }
                    return true;
                }
                y += h;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (active != null) {
            int y = cy - Math.round(scroll);
            for (Row r : rows()) {
                if (r == active) {
                    r.drag(this, cx, y, cw, (int) click.x());
                    return true;
                }
                y += r.height();
            }
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (active != null) SpotifyUIConfig.save();
        active = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inside((int) mouseX, (int) mouseY, px + sideW, cy - 4, pw - sideW, ch + 8)) {
            scroll = Math.max(0f, Math.min(maxScroll(), scroll - (float) vertical * 18f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private abstract static class Row {
        abstract int height();
        abstract void draw(ConfigScreen s, int x, int y, int w, int mx, int my);
        boolean interactive() { return true; }
        boolean click(ConfigScreen s, int x, int y, int w, int mx, int my) { return false; }
        void drag(ConfigScreen s, int x, int y, int w, int mx) {}
    }

    private abstract static class Labeled extends Row {
        static final int CONTROL_W = 112;
        final String label;
        final String desc;

        Labeled(String label, String desc) {
            this.label = label;
            this.desc = desc == null ? "" : desc;
        }

        @Override int height() { return desc.isEmpty() ? 26 : 32; }

        int controlX(int x, int w) { return x + w - CONTROL_W; }

        int controlY(int y) { return y + (height() - 2 - 16) / 2; }

        void drawLabel(int x, int y, int w) {
            float maxW = w - CONTROL_W - 12;
            int rowH = height() - 2;
            if (desc.isEmpty()) {
                Gfx.text(Gfx.scrollFit(label, maxW, 1f), x, y + (rowH - 7f) / 2f, TEXT, 1f);
            } else {
                Gfx.text(Gfx.scrollFit(label, maxW, 1f), x, y + 5, TEXT, 1f);
                Gfx.text(Gfx.scrollFit(desc, maxW, 0.75f), x, y + 17, DIMTEXT, 0.75f);
            }
        }
    }

    private static final class Header extends Row {
        final String text;
        Header(String text) { this.text = text; }
        @Override int height() { return 24; }
        @Override boolean interactive() { return false; }
        @Override void draw(ConfigScreen s, int x, int y, int w, int mx, int my) {
            String t = text.toUpperCase(Locale.ROOT);
            Gfx.text(t, x, y + 12, GREEN, 0.8f);
            float tw = Gfx.width(t, 0.8f);
            Gfx.rect(x + tw + 6, y + 15, w - tw - 6, 1, 0xFF262626);
        }
    }

    private static final class Info extends Row {
        final String label;
        final Supplier<String> value;
        Info(String label, Supplier<String> value) { this.label = label; this.value = value; }
        @Override int height() { return 24; }
        @Override boolean interactive() { return false; }
        @Override void draw(ConfigScreen s, int x, int y, int w, int mx, int my) {
            Gfx.text(label, x, y + (22 - 7f) / 2f, SUBTEXT, 1f);
            String full = value.get();
            float maxW = w - 100;
            float ty = y + (22 - 7f) / 2f;
            if (Gfx.width(full, 1f) <= maxW) {
                Gfx.textRight(full, x + w, ty, TEXT, 1f, 1f);
            } else {
                Gfx.text(Gfx.scrollFit(full, maxW, 1f), x + w - maxW, ty, TEXT, 1f);
            }
        }
    }

    private static final class Toggle extends Labeled {
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;
        float anim = -1f;

        Toggle(String label, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {
            super(label, desc);
            this.get = get;
            this.set = set;
        }

        @Override void draw(ConfigScreen s, int x, int y, int w, int mx, int my) {
            drawLabel(x, y, w);
            boolean on = get.get();
            float target = on ? 1f : 0f;
            if (anim < 0f) anim = target;
            anim += (target - anim) * 0.35f;
            int pillW = 30, pillH = 14;
            int px = x + w - pillW;
            int py = controlY(y) + 1;
            Gfx.roundRect(px, py, pillW, pillH, 7, Gfx.mix(0xFF3A3A3A, GREEN, anim));
            float kx = px + 7 + anim * (pillW - 14);
            Gfx.circle(kx, py + pillH / 2f, 5, 0xFFFFFFFF);
        }

        @Override boolean click(ConfigScreen s, int x, int y, int w, int mx, int my) {
            set.accept(!get.get());
            return false;
        }
    }

    private static final class Slider extends Labeled {
        final float min, max, step;
        final Supplier<Float> get;
        final Consumer<Float> set;
        final Function<Float, String> fmt;

        Slider(String label, String desc, float min, float max, float step,
               Supplier<Float> get, Consumer<Float> set, Function<Float, String> fmt) {
            super(label, desc);
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
        }

        private int trackX(int x, int w) { return controlX(x, w) + 2; }
        private int trackW() { return CONTROL_W - 46; }

        @Override void draw(ConfigScreen s, int x, int y, int w, int mx, int my) {
            drawLabel(x, y, w);
            float v = get.get();
            float t = (v - min) / (max - min);
            t = Math.max(0f, Math.min(1f, t));
            int tx = trackX(x, w);
            int ty = controlY(y) + 8;
            Gfx.roundRect(tx, ty - 1, trackW(), 3, 1, 0xFF3A3A3A);
            Gfx.roundRect(tx, ty - 1, Math.max(2, Math.round(trackW() * t)), 3, 1, GREEN);
            Gfx.circle(tx + trackW() * t, ty + 0.5f, 4.5f, 0xFFFFFFFF);
            Gfx.textRight(fmt.apply(v), x + w, ty - 3.5f, SUBTEXT, 1f, 1f);
        }

        @Override boolean click(ConfigScreen s, int x, int y, int w, int mx, int my) {
            if (mx < trackX(x, w) - 6) return false;
            update(x, w, mx);
            return true;
        }

        @Override void drag(ConfigScreen s, int x, int y, int w, int mx) {
            update(x, w, mx);
        }

        private void update(int x, int w, int mx) {
            float t = (mx - trackX(x, w)) / (float) trackW();
            t = Math.max(0f, Math.min(1f, t));
            float v = min + (max - min) * t;
            v = Math.round(v / step) * step;
            v = Math.max(min, Math.min(max, v));
            set.accept(v);
        }
    }

    private static final class Cycle<T> extends Labeled {
        final Supplier<List<T>> options;
        final Supplier<T> get;
        final Consumer<T> set;
        final Function<T, String> name;

        Cycle(String label, String desc, Supplier<List<T>> options, Supplier<T> get,
              Consumer<T> set, Function<T, String> name) {
            super(label, desc);
            this.options = options;
            this.get = get;
            this.set = set;
            this.name = name;
        }

        @Override void draw(ConfigScreen s, int x, int y, int w, int mx, int my) {
            drawLabel(x, y, w);
            int bx = controlX(x, w);
            int by = controlY(y);
            boolean hov = inside(mx, my, bx, by, CONTROL_W, 16)
                    && inside(mx, my, s.cx, s.cy, s.cw, s.ch);
            Gfx.roundRect(bx, by, CONTROL_W, 16, 8, CONTROL);
            Gfx.ring(bx, by, CONTROL_W, 16, 8, 1, hov ? 0xFF4A4A4A : CONTROL_EDGE);
            float ty = by + (16 - 7f) / 2f;
            Gfx.text("<", bx + 8, ty, hov ? TEXT : DIMTEXT, 1f);
            Gfx.textRight(">", bx + CONTROL_W - 8, ty, hov ? TEXT : DIMTEXT, 1f, 1f);
            String full = name.apply(get.get());
            float maxW = CONTROL_W - 36;
            if (Gfx.width(full, 1f) <= maxW) {
                Gfx.textCentered(full, bx + CONTROL_W / 2f, ty, TEXT, 1f, 1f);
            } else {
                Gfx.text(Gfx.scrollFit(full, maxW, 1f), bx + 18, ty, TEXT, 1f);
            }
        }

        @Override boolean click(ConfigScreen s, int x, int y, int w, int mx, int my) {
            int bx = controlX(x, w);
            if (mx < bx || mx >= bx + CONTROL_W) return false;
            List<T> list = options.get();
            if (list.isEmpty()) return false;
            int i = list.indexOf(get.get());
            if (i < 0) i = 0;
            boolean back = mx < bx + CONTROL_W / 3;
            i = back ? (i - 1 + list.size()) % list.size() : (i + 1) % list.size();
            set.accept(list.get(i));
            return false;
        }
    }

    private static final class Button extends Labeled {
        final Supplier<String> text;
        final Runnable action;

        Button(String label, String desc, Supplier<String> text, Runnable action) {
            super(label, desc);
            this.text = text;
            this.action = action;
        }

        @Override void draw(ConfigScreen s, int x, int y, int w, int mx, int my) {
            drawLabel(x, y, w);
            int bx = controlX(x, w);
            int by = controlY(y);
            boolean hov = inside(mx, my, bx, by, CONTROL_W, 16)
                    && inside(mx, my, s.cx, s.cy, s.cw, s.ch);
            Gfx.roundRect(bx, by, CONTROL_W, 16, 8, hov ? 0xFF2E2E2E : CONTROL);
            Gfx.ring(bx, by, CONTROL_W, 16, 8, 1, hov ? 0xFF4A4A4A : CONTROL_EDGE);
            String full = text.get();
            float maxW = CONTROL_W - 16;
            float ty = by + (16 - 7f) / 2f;
            if (Gfx.width(full, 1f) <= maxW) {
                Gfx.textCentered(full, bx + CONTROL_W / 2f, ty, TEXT, 1f, 1f);
            } else {
                Gfx.text(Gfx.scrollFit(full, maxW, 1f), bx + 8, ty, TEXT, 1f);
            }
        }

        @Override boolean click(ConfigScreen s, int x, int y, int w, int mx, int my) {
            int bx = controlX(x, w);
            if (mx >= bx && mx < bx + CONTROL_W) action.run();
            return false;
        }
    }
}
