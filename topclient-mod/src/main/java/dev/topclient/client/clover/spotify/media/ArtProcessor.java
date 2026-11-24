package dev.topclient.client.clover.spotify.media;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ArtProcessor {
    private static final int ART_SIZE = 128;
    private static final int ART_RADIUS = 14;

    private ArtProcessor() {}

    public static final class Result {
        public final BufferedImage rounded;
        public final BufferedImage flat;
        public final int accent;

        Result(BufferedImage rounded, BufferedImage flat, int accent) {
            this.rounded = rounded;
            this.flat = flat;
            this.accent = accent;
        }
    }

    public static Result process(byte[] data) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) return null;
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage square = toArgb(src).getSubimage(
                (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        BufferedImage flat = scale(square, ART_SIZE, ART_SIZE);
        int accent = accent(flat);
        BufferedImage rounded = bake(flat, false, ART_SIZE, ART_SIZE, ART_RADIUS, 0f);
        return new Result(rounded, flat, accent);
    }

    public static BufferedImage bake(BufferedImage src, boolean blur, int w, int h, int radius, float dim) {
        BufferedImage source = toArgb(src);
        if (blur) {
            source = scale(source, 8, 8);
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            if (radius > 0) {
                g.fill(new RoundRectangle2D.Float(0, 0, w, h, radius * 2f, radius * 2f));
            } else {
                g.fillRect(0, 0, w, h);
            }
            g.setComposite(AlphaComposite.SrcIn);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            double sx = (double) w / source.getWidth();
            double sy = (double) h / source.getHeight();
            double s = Math.max(sx, sy);
            int dw = (int) Math.ceil(source.getWidth() * s);
            int dh = (int) Math.ceil(source.getHeight() * s);
            int dx = (w - dw) / 2;
            int dy = (h - dh) / 2;
            g.drawImage(source, dx, dy, dw, dh, null);

            if (dim > 0f) {
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(new Color(0, 0, 0, Math.min(255, Math.round(dim * 255f))));
                g.fillRect(0, 0, w, h);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    public static byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage cur = toArgb(src);
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
            cur = resizeOnce(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return resizeOnce(cur, w, h);
    }

    private static BufferedImage resizeOnce(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static int accent(BufferedImage img) {
        float[] hsb = new float[3];
        double weight = 0, sr = 0, sg = 0, sb = 0, satSum = 0;
        int count = 0;
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                Color.RGBtoHSB(r, g, b, hsb);
                double w = hsb[1] * hsb[2];
                w = w * w + 0.0005;
                weight += w;
                sr += r * w;
                sg += g * w;
                sb += b * w;
                satSum += hsb[1];
                count++;
            }
        }
        if (count == 0 || weight <= 0) return MediaState.DEFAULT_ACCENT;
        if (satSum / count < 0.10) return 0xE6E6E6;
        int r = (int) Math.round(sr / weight);
        int g = (int) Math.round(sg / weight);
        int b = (int) Math.round(sb / weight);
        Color.RGBtoHSB(r, g, b, hsb);
        float s = Math.max(0.45f, hsb[1]);
        float v = Math.max(0.78f, hsb[2]);
        return Color.HSBtoRGB(hsb[0], Math.min(1f, s), Math.min(1f, v)) & 0xFFFFFF;
    }
}
