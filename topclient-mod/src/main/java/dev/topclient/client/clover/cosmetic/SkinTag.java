package dev.topclient.client.clover.cosmetic;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Reads the small "TopClient tag" that the launcher writes into the unused corners of a player's
 * skin (pixels Minecraft never draws), so TopClient players can see each other's cosmetics with
 * no server at all: the game already downloads every skin from Mojang.
 *
 * Layout (shared with the launcher's skintag.go): three 8x8 blocks at (0,0), (24,0) and (56,0),
 * row by row; each pixel gives 3 bytes (red, green, blue). Bytes: "AFK1", length (1 byte), the text
 * ("p=1;c=cape;h=hat;b=band;w=wings;y=buddy;a=aura", p=1 marks an Afrika Launcher player), then a CRC32 of the text (4 bytes, big-endian).
 * Only short ids made of a-z, 0-9 and _ are accepted; anything else is ignored.
 */
public final class SkinTag {
	public static final int[][] BLOCKS = {{0, 0}, {24, 0}, {56, 0}};
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");
	private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

	private SkinTag() {
	}

	/**
	 * @param key   a stable key for the skin (its texture id: skins never change behind the same id)
	 * @param w     skin width, h skin height
	 * @param pixel returns the ARGB colour at (x, y)
	 */
	public static Map<String, String> read(String key, int w, int h, IntBinaryOperator pixel) {
		Map<String, String> hit = CACHE.get(key);
		if (hit != null) {
			return hit;
		}
		Map<String, String> out = Collections.emptyMap();
		try {
			if (w == 64 && h == 64) {
				out = decode(pixel);
			}
		} catch (RuntimeException ignored) {
			// not a tagged skin
		}
		if (CACHE.size() > 512) {
			CACHE.clear();
		}
		CACHE.put(key, out);
		return out;
	}

	public static Map<String, String> cached(String key) {
		return CACHE.get(key);
	}

	static Map<String, String> decode(IntBinaryOperator pixel) {
		byte[] b = new byte[BLOCKS.length * 64 * 3];
		int i = 0;
		for (int[] blk : BLOCKS) {
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					int c = pixel.applyAsInt(blk[0] + x, blk[1] + y);
					b[i++] = (byte) (c >> 16);
					b[i++] = (byte) (c >> 8);
					b[i++] = (byte) c;
				}
			}
		}
		if (b[0] != 'A' || b[1] != 'F' || b[2] != 'K' || b[3] != '1') {
			return Collections.emptyMap();
		}
		int n = b[4] & 0xFF;
		if (5 + n + 4 > b.length) {
			return Collections.emptyMap();
		}
		CRC32 crc = new CRC32();
		crc.update(b, 5, n);
		long want = ((b[5 + n] & 0xFFL) << 24) | ((b[6 + n] & 0xFFL) << 16) | ((b[7 + n] & 0xFFL) << 8) | (b[8 + n] & 0xFFL);
		if (crc.getValue() != want) {
			return Collections.emptyMap();
		}
		Map<String, String> out = new HashMap<>();
		for (String kv : new String(b, 5, n, StandardCharsets.US_ASCII).split(";")) {
			int e = kv.indexOf('=');
			if (e == 1 && "chbwyap".indexOf(kv.charAt(0)) >= 0) {
				String v = kv.substring(2);
				if (ID.matcher(v).matches() && !v.equals("none") && !v.equals("custom")) {
					out.put(kv.substring(0, 1), v);
				}
			}
		}
		return Collections.unmodifiableMap(out);
	}
}
