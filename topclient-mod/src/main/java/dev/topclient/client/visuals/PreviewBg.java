package dev.topclient.client.visuals;

/**
 * Removes the dark background that is baked into Visuals preview images, so the
 * tile behind it (whose opacity the "Texture preview opacity" slider controls) shows through.
 * Flood-fills from the image border through pixels that change only gradually
 * (a gradient background); the item itself has hard edges and is left untouched.
 */
public final class PreviewBg {
    private static final int STEP = 12;   // max per-channel change between neighbouring background pixels
    private static final int RANGE = 72;  // max per-channel distance from the border's average colour

    private PreviewBg() {}

    public static void strip(int[] px, String key, int w, int h) {
        try {
            if (px == null || key == null || !key.startsWith("p:") || w < 4 || h < 4 || px.length < w * h) return;

            // reference colour = average of the opaque border pixels
            long r = 0, g = 0, b = 0; int n = 0;
            for (int x = 0; x < w; x++) for (int y : new int[]{0, h - 1}) {
                int c = px[y * w + x];
                if ((c >>> 24) > 200) { r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++; }
            }
            for (int y = 0; y < h; y++) for (int x : new int[]{0, w - 1}) {
                int c = px[y * w + x];
                if ((c >>> 24) > 200) { r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++; }
            }
            if (n < (w + h)) return; // border is mostly transparent already: nothing baked in
            int rr = (int) (r / n), rg = (int) (g / n), rb = (int) (b / n);
            int ref = (rr << 16) | (rg << 8) | rb;

            boolean[] bg = new boolean[w * h];
            int[] queue = new int[w * h];
            int head = 0, tail = 0;
            for (int x = 0; x < w; x++) for (int y : new int[]{0, h - 1}) tail = seed(px, bg, queue, tail, y * w + x, ref);
            for (int y = 0; y < h; y++) for (int x : new int[]{0, w - 1}) tail = seed(px, bg, queue, tail, y * w + x, ref);

            while (head < tail) {
                int i = queue[head++];
                int x = i % w, y = i / w;
                int cur = px[i];
                boolean curClear = (cur >>> 24) < 16;
                for (int d = 0; d < 4; d++) {
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (bg[j]) continue;
                    int c = px[j];
                    int a = c >>> 24;
                    boolean ok;
                    if (a < 16) ok = true;
                    else if (a < 200) ok = false;
                    else ok = dist(c, ref) <= RANGE && (curClear ? dist(c, ref) <= RANGE / 2 : dist(c, cur) <= STEP);
                    if (ok) { bg[j] = true; queue[tail++] = j; }
                }
            }

            int cleared = 0;
            for (int i = 0; i < bg.length; i++) if (bg[i]) cleared++;
            if (cleared > bg.length * 97 / 100) return; // looks like the whole image is "background": leave it

            for (int i = 0; i < bg.length; i++) {
                if (bg[i]) { px[i] &= 0x00FFFFFF; continue; }
                // soften the 1px blend between item and old background
                int x = i % w, y = i / w;
                boolean edge = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w]);
                if (edge && (px[i] >>> 24) > 200 && dist(px[i], ref) <= RANGE / 2) px[i] = (px[i] & 0x00FFFFFF) | (110 << 24);
            }
        } catch (Throwable ignored) {
            // never break preview loading
        }
    }

    private static int seed(int[] px, boolean[] bg, int[] q, int tail, int i, int ref) {
        if (bg[i]) return tail;
        int c = px[i];
        int a = c >>> 24;
        if (a < 16 || (a > 200 && dist(c, ref) <= RANGE)) { bg[i] = true; q[tail++] = i; }
        return tail;
    }

    private static int dist(int a, int b) {
        int dr = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255));
        int dg = Math.abs(((a >> 8) & 255) - ((b >> 8) & 255));
        int db = Math.abs((a & 255) - (b & 255));
        return Math.max(dr, Math.max(dg, db));
    }
}
