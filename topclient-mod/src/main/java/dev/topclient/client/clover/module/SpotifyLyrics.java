package dev.topclient.client.clover.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.spotify.media.MediaService;
import dev.topclient.client.clover.spotify.media.MediaState;
import dev.topclient.client.clover.ui.CloverFont;
import dev.topclient.client.clover.ui.Gfx;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpotifyLyrics extends Module {
	public static SpotifyLyrics instance;

	private static final String HOST = "lrclib.net";
	private static final Pattern LRC = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");

	private static final Object[][] THEMES = {
			{"Purple Dream", 0xE8E0FF, 0xB18CFF, 0x1E1630},
			{"Clover", 0xE9F7EE, 0x4DFF9A, 0x0F2A1B},
			{"Neon", 0xE0FFFF, 0x00F0FF, 0x140C24},
			{"Sunset", 0xFFF0E2, 0xFF8A3D, 0x2A1A10},
			{"Ocean", 0xE2FBF8, 0x00C2A8, 0x0B2427},
			{"Sakura", 0xFFE9F3, 0xFF7EB6, 0x2A1622},
			{"Gold", 0xFFF8E0, 0xFFD34D, 0x221B0A},
			{"Mono", 0xFFFFFF, 0x9A9A9A, 0x161616},
			{"Custom", 0, 0, 0}};

	final Setting style;
	private final Setting theme;
	private final Setting font;
	private final Setting textColor;
	private final Setting highlightColor;
	private final Setting barColor;
	private final Setting size;
	private final Setting height;
	private final Setting around;
	private final Setting animation;
	private final Setting speed;
	private final Setting karaoke;
	private final Setting shadow;
	private final Setting band;
	private final Setting notes;
	private final Setting offset;
	final Setting hideInMenus;
	private final Setting online;
	final Setting skyDistance;
	final Setting skyElevation;
	final Setting skyFollow;
	final Setting skyFont;
	final Setting skyBold;
	final Setting skyThrough;

	private record Line(long at, String text) {
	}

	private volatile List<Line> lines = List.of();
	volatile boolean synced = true;
	volatile String status = "Play a song in Spotify";
	private String loadedKey = "";
	private int shownIndex = -2;
	private long changedAt;
	private int previousIndex = -1;

	public SpotifyLyrics() {
		super("lyrics", "Spotify Lyrics", "The lyrics of the song playing in Spotify: a pill at the top with the sung word highlighted, "
				+ "and words floating in the sky like the TikTok edits. Change the style, font, colours, sizes and motion. "
				+ "Finds lyrics on lrclib.net, or put .lrc files in config/topclient/lyrics/.", Category.HUD);
		isNew = true;
		instance = this;
		header("Look");
		style = mode("style2", "Style", 1, LyricsLook.STYLES);
		theme = mode("theme", "Theme", 0, names());
		font = mode("font", "Font", 1, CloverFont.names());
		textColor = color("textColor", "Text colour (Custom theme)", 0xFFFFFF);
		highlightColor = color("hlColor", "Highlight colour (Custom theme)", 0x4DFF9A);
		barColor = color("barColor", "Bar colour (Custom theme)", 0x111111);
		size = number("size", "Text size", 1.0F, 4.0F, 0.1F, 2.2F);
		height = number("height", "Height on screen (%)", 8, 75, 1, 34);
		around = number("around", "Lines before / after", 0, 2, 1, 1);
		shadow = bool("shadow", "Text shadow", true);
		band = bool("band", "Soft dark band behind the sky lyrics", false);
		notes = bool("notes", "Little music notes", true);
		header("In the sky");
		skyDistance = number("skyDist", "Distance (blocks)", 20, 90, 1, 45);
		skyElevation = number("skyElev", "Height in the sky (degrees)", 0, 60, 1, 18);
		skyFollow = mode("skyFollow", "Where", 0, "Follows your view slowly", "Always in front of you", "Stays in one direction");
		skyFont = mode("skyFont", "Sky font", 0, "Minecraft", "Smooth (Uniform)");
		skyBold = bool("skyBold", "Bold", true);
		skyThrough = bool("skyThrough", "Show through mountains and blocks", true);
		header("Motion");
		animation = mode("anim", "Line change", 0, "Fade + slide", "Pop", "Typewriter", "None");
		speed = number("speed", "Animation speed", 0.3F, 3.0F, 0.1F, 1.0F);
		karaoke = bool("karaoke", "Karaoke highlight (fills as it is sung)", true);
		offset = number("offset", "Timing offset (seconds, + = earlier)", -3F, 3F, 0.1F, 0F);
		header("Lyrics");
		online = bool("online", "Find lyrics online (lrclib.net, sends only the song name)", true);
		hideInMenus = bool("hideMenus", "Hide while a menu is open", true);
		LyricsLook.addSettings(this);
	}

	private static String[] names() {
		String[] out = new String[THEMES.length];
		for (int i = 0; i < THEMES.length; i++) {
			out[i] = (String) THEMES[i][0];
		}
		return out;
	}

	public static void setup() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "lyrics"), (ctx, tickCounter) -> {
			if (instance != null && instance.enabled()) {
				try {
					instance.render(ctx, Minecraft.getInstance());
				} catch (RuntimeException e) {
				}
			}
		});
	}

	@Override
	public void onEnable() {
		MediaService.start();
	}

	/** The old single-line 3D sky is replaced by VideoSky (one floating text per word). */
	boolean skyOn() {
		return false;
	}

	@Override
	public void onDisable() {
		VideoSky.clear();
		if (SpotifyModule.instance == null || !SpotifyModule.instance.enabled()) {
			MediaService.stop();
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		if (!MediaService.running()) {
			MediaService.start();
		}
		try {
			VideoSky.tick(mc, this);
		} catch (RuntimeException e) {
			VideoSky.clear();
		}
		MediaState s = MediaService.get();
		if (!s.hasMedia || s.title.isEmpty()) {
			if (!loadedKey.isEmpty()) {
				loadedKey = "";
				lines = List.of();
			}
			status = "Play a song in Spotify";
			return;
		}
		String key = s.artist + "\u0000" + s.title;
		if (!key.equals(loadedKey)) {
			loadedKey = key;
			lines = List.of();
			shownIndex = -2;
			status = "Looking for lyrics...";
			String title = s.title, artist = s.artist, album = s.album;
			long dur = s.durationMs;
			Thread t = new Thread(() -> load(key, title, artist, album, dur), "Clover-Lyrics");
			t.setDaemon(true);
			t.start();
		}
	}

	private static Path dir() {
		return Config.dir().resolve("lyrics");
	}

	private static String safe(String s) {
		return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
	}

	private void load(String key, String title, String artist, String album, long durationMs) {
		try {
			Path own = dir().resolve(safe(artist + " - " + title) + ".lrc");
			Path cache = dir().resolve("cache").resolve(Integer.toHexString(key.toLowerCase(Locale.ROOT).hashCode()) + ".lrc");
			String text = null;
			if (Files.isRegularFile(own)) {
				text = Files.readString(own);
			} else if (Files.isRegularFile(cache)) {
				text = Files.readString(cache);
			} else if (online.getBool()) {
				text = fetch(title, artist, album, durationMs);
				if (text == null) {
					String plain = title.replaceAll("\\s*[(\\[].*?[)\\]]", "").replaceAll("\\s+-\\s+.*$", "").trim();
					String mainArtist = artist.split(",|&| feat\\.| ft\\.| x ")[0].trim();
					if (!plain.equals(title) || !mainArtist.equals(artist)) {
						text = fetch(plain, mainArtist, "", 0);
					}
				}
				if (text != null) {
					Files.createDirectories(cache.getParent());
					Files.writeString(cache, text);
				}
			}
			if (!key.equals(loadedKey)) {
				return;
			}
			if (text == null || text.isBlank()) {
				lines = List.of();
				status = online.getBool() ? "No lyrics found for this song" : "No lyrics (online search is off)";
				return;
			}
			parse(text, durationMs);
		} catch (Exception e) {
			if (key.equals(loadedKey)) {
				lines = List.of();
				status = "Could not load lyrics";
			}
		}
	}

	private static HttpClient http;

	private static String get(String url) throws Exception {
		URI uri = URI.create(url);
		if (!HOST.equals(uri.getHost()) || !"https".equals(uri.getScheme())) {
			throw new IllegalStateException("blocked");
		}
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
		}
		HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
				.header("User-Agent", "TopClient (Minecraft mod)").GET().build();
		HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
		return r.statusCode() == 200 ? r.body() : null;
	}

	private static String q(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String fetch(String title, String artist, String album, long durationMs) throws Exception {
		String body = null;
		if (!title.isEmpty() && !artist.isEmpty()) {
			String url = "https://" + HOST + "/api/get?track_name=" + q(title) + "&artist_name=" + q(artist)
					+ (album.isEmpty() ? "" : "&album_name=" + q(album)) + (durationMs > 0 ? "&duration=" + Math.round(durationMs / 1000.0) : "");
			body = get(url);
		}
		JsonObject best = null;
		if (body != null) {
			JsonElement e = JsonParser.parseString(body);
			if (e.isJsonObject()) {
				best = e.getAsJsonObject();
			}
		}
		if (best == null || !has(best, "syncedLyrics")) {
			String found = get("https://" + HOST + "/api/search?track_name=" + q(title) + "&artist_name=" + q(artist));
			if (found != null) {
				JsonElement e = JsonParser.parseString(found);
				if (e.isJsonArray()) {
					JsonArray arr = e.getAsJsonArray();
					for (JsonElement x : arr) {
						if (x.isJsonObject() && has(x.getAsJsonObject(), "syncedLyrics")) {
							best = x.getAsJsonObject();
							break;
						}
					}
					if ((best == null || !has(best, "plainLyrics")) && !arr.isEmpty() && arr.get(0).isJsonObject()) {
						best = arr.get(0).getAsJsonObject();
					}
				}
			}
		}
		if (best == null) {
			return null;
		}
		if (has(best, "syncedLyrics")) {
			return best.get("syncedLyrics").getAsString();
		}
		if (has(best, "plainLyrics")) {
			return "#PLAIN\n" + best.get("plainLyrics").getAsString();
		}
		return null;
	}

	private static boolean has(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() && !o.get(k).getAsString().isBlank();
	}

	private void parse(String text, long durationMs) {
		List<Line> out = new ArrayList<>();
		boolean plain = text.startsWith("#PLAIN");
		if (plain) {
			List<String> raw = new ArrayList<>();
			for (String l : text.substring(6).split("\\R")) {
				if (!l.isBlank()) {
					raw.add(l.trim());
				}
			}
			long len = durationMs > 0 ? durationMs : 200_000L;
			for (int i = 0; i < raw.size(); i++) {
				out.add(new Line(Math.round(len * 0.06 + (len * 0.9) * i / Math.max(1, raw.size())), raw.get(i)));
			}
		} else {
			for (String l : text.split("\\R")) {
				Matcher m = LRC.matcher(l);
				List<Long> times = new ArrayList<>();
				int end = 0;
				while (m.find() && m.start() == end) {
					long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
					if (m.group(3) != null) {
						String f = (m.group(3) + "00").substring(0, 3);
						ms += Long.parseLong(f);
					}
					times.add(ms);
					end = m.end();
				}
				String words = l.substring(end).trim();
				for (long t : times) {
					out.add(new Line(t, words));
				}
			}
			out.sort((a, b) -> Long.compare(a.at, b.at));
		}
		synced = !plain;
		lines = out;
		shownIndex = -2;
		status = out.isEmpty() ? "No lyrics found for this song" : "";
	}

	record Now(String prev, String cur, String next, float progress, boolean pause, float ease) {
	}

	Now now() {
		MediaState s = MediaService.get();
		List<Line> ls = lines;
		if (!s.hasMedia || ls.isEmpty()) {
			return null;
		}
		long now = s.elapsedMs(System.nanoTime()) + Math.round(offset.getFloat() * 1000);
		int idx = -1;
		for (int i = 0; i < ls.size(); i++) {
			if (ls.get(i).at <= now) {
				idx = i;
			} else {
				break;
			}
		}
		if (idx != shownIndex) {
			previousIndex = shownIndex;
			shownIndex = idx;
			changedAt = System.currentTimeMillis();
		}
		float anim = Math.min(1F, (System.currentTimeMillis() - changedAt) / (350F / speed.getFloat()));
		float ease = 1 - (1 - anim) * (1 - anim);
		String cur = idx >= 0 ? ls.get(idx).text : "";
		long start = idx >= 0 ? ls.get(idx).at : 0;
		long end = idx + 1 < ls.size() ? ls.get(idx + 1).at : start + 4000;
		float progress = !synced ? 1F : Math.max(0F, Math.min(1F, (now - start) / (float) Math.max(300, Math.min(end - start, 8000) * 0.85F)));
		boolean pause = cur.isEmpty() || cur.equals("♪");
		int n = Math.round(around.getFloat());
		String prev = n > 0 && idx - 1 >= 0 ? ls.get(idx - 1).text : "";
		String next = n > 0 && idx + 1 < ls.size() ? ls.get(idx + 1).text : "";
		return new Now(prev, cur, next, progress, pause, ease);
	}

	int[] colorsForSky() {
		return colors();
	}

	float sizeSetting() {
		return size.getFloat();
	}

	int animationMode() {
		return animation.getMode();
	}

	boolean karaokeOn() {
		return karaoke.getBool();
	}

	boolean shadowOn() {
		return shadow.getBool();
	}

	boolean bandOn() {
		return band.getBool();
	}

	boolean hideInMenus() {
		return hideInMenus.getBool();
	}

	int[] colors() {
		Object[] t = THEMES[Math.min(theme.getMode(), THEMES.length - 1)];
		if ("Custom".equals(t[0])) {
			return new int[]{textColor.getColor(), highlightColor.getColor(), barColor.getColor()};
		}
		return new int[]{(Integer) t[1], (Integer) t[2], (Integer) t[3]};
	}

	private static int argb(int rgb, float a) {
		return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	int fam() {
		return font.getMode();
	}

	int famFor(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) > 255) {
				return 0;
			}
		}
		return fam();
	}

	private float w(String s, float k) {
		return CloverFont.width(famFor(s), s, k);
	}

	private void draw(GuiGraphicsExtractor ctx, String s, float x, float y, float k, int color) {
		if (((color >>> 24) & 0xFF) < 6 || s.isEmpty()) {
			return;
		}
		CloverFont.draw(ctx, famFor(s), s, x, y, k, color, shadow.getBool());
	}

	private List<String> wrap(String s, float k, float maxW) {
		List<String> rows = new ArrayList<>();
		StringBuilder row = new StringBuilder();
		for (String word : s.split(" ")) {
			String next = row.isEmpty() ? word : row + " " + word;
			if (!row.isEmpty() && w(next, k) > maxW) {
				rows.add(row.toString());
				row = new StringBuilder(word);
			} else {
				row = new StringBuilder(next);
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	private void render(GuiGraphicsExtractor ctx, Minecraft mc) {
		LyricsLook.render(this, ctx, mc); // the pill (styles 0, 2 and 4)
		if (mc.gui.hud.isHidden() || (hideInMenus.getBool() && mc.gui.screen() != null)) {
			return;
		}
		MediaState s = MediaService.get();
		List<Line> ls = lines;
		if (!s.hasMedia || ls.isEmpty()) {
			return;
		}
		long now = s.elapsedMs(System.nanoTime()) + Math.round(offset.getFloat() * 1000);
		int idx = -1;
		for (int i = 0; i < ls.size(); i++) {
			if (ls.get(i).at <= now) {
				idx = i;
			} else {
				break;
			}
		}
		if (idx != shownIndex) {
			previousIndex = shownIndex;
			shownIndex = idx;
			changedAt = System.currentTimeMillis();
		}
		float anim = Math.min(1F, (System.currentTimeMillis() - changedAt) / (350F / speed.getFloat()));
		float ease = 1 - (1 - anim) * (1 - anim);
		String cur = idx >= 0 ? ls.get(idx).text : "";
		long start = idx >= 0 ? ls.get(idx).at : 0;
		long end = idx + 1 < ls.size() ? ls.get(idx + 1).at : start + 4000;
		float progress = !synced ? 1F : Math.max(0F, Math.min(1F, (now - start) / (float) Math.max(300, Math.min(end - start, 8000) * 0.85F)));
		boolean pause = cur.isEmpty() || cur.equals("♪");
		int[] c = colors();
		int sw = ctx.guiWidth(), sh = ctx.guiHeight();
		int st = LyricsLook.legacyMode(this); // old on-screen styles 3-5 map to 2 / 5, everything else to -1
		if (st == 2 || st == 4 || st == 5) {
			sky(ctx, ls, idx, cur, pause, progress, ease, c, sw, sh, st == 5);
		}
		if (st == 1 || st == 3 || st == 4) {
			bar(ctx, pause ? "♪ ♪ ♪" : cur, pause ? 1F : progress, c, sw);
		}
	}

	private void sky(GuiGraphicsExtractor ctx, List<Line> ls, int idx, String cur, boolean pause, float progress, float ease, int[] c,
					 int sw, int sh, boolean oneLine) {
		float k = size.getFloat();
		float cy = sh * height.getFloat() / 100F;
		float maxW = sw * 0.8F;
		int mode = animation.getMode();
		float alpha = mode == 3 ? 1F : ease;
		float slide = mode == 0 ? (1 - ease) * 10 * k : 0;
		float pop = mode == 1 ? 0.75F + 0.25F * ease : 1F;
		float rowH = 11 * k * pop;

		String show = pause ? "♪ ♪ ♪" : cur;
		if (mode == 2 && !pause) {
			show = show.substring(0, Math.max(0, Math.min(show.length(), Math.round(show.length() * ease))));
		}
		List<String> rows = wrap(show, k * pop, maxW);
		if (rows.isEmpty()) {
			rows.add("");
		}
		float blockH = rows.size() * rowH;
		float top = cy - blockH / 2 + slide;

		if (band.getBool()) {
			int bh = Math.round(blockH + 22 * k);
			ctx.fillGradient(0, Math.round(cy - bh / 2F), sw, Math.round(cy), 0x00000000, 0x55000000);
			ctx.fillGradient(0, Math.round(cy), sw, Math.round(cy + bh / 2F), 0x55000000, 0x00000000);
		}

		int n = oneLine ? 0 : Math.round(around.getFloat());
		float small = Math.max(0.8F, k * 0.55F);
		for (int i = 1; i <= n; i++) {
			if (idx - i >= 0) {
				String t = ls.get(idx - i).text;
				float y = top - i * 12 * small - 4;
				float a = (0.55F / i) * alpha;
				draw(ctx, t, sw / 2F - w(t, small) / 2, y, small, argb(c[0], a));
			}
			if (idx + i < ls.size() && idx + i >= 0) {
				String t = ls.get(idx + i).text;
				float y = top + blockH + (i - 1) * 12 * small + 6;
				float a = (0.45F / i) * alpha;
				draw(ctx, t, sw / 2F - w(t, small) / 2, y, small, argb(c[0], a));
			}
		}

		int total = 0;
		for (String r : rows) {
			total += r.length();
		}
		int filled = karaoke.getBool() ? Math.round(total * progress) : total;
		float kk = k * pop;
		for (int i = 0; i < rows.size(); i++) {
			String r = rows.get(i);
			float x = sw / 2F - w(r, kk) / 2;
			float y = top + i * rowH;
			if (pause) {
				float pulse = 0.6F + 0.4F * (float) Math.sin(System.currentTimeMillis() / 250.0);
				draw(ctx, r, x, y, kk, argb(c[1], alpha * pulse));
				continue;
			}
			draw(ctx, r, x, y, kk, argb(karaoke.getBool() ? c[0] : c[1], alpha * (karaoke.getBool() ? 0.6F : 1F)));
			int here = Math.max(0, Math.min(r.length(), filled));
			if (karaoke.getBool() && here > 0) {
				draw(ctx, r.substring(0, here), x, y, kk, argb(c[1], alpha));
			}
			filled -= r.length();
		}
		if (notes.getBool() && !pause) {
			String deco = "♪ ♪ ♪";
			float nk = Math.max(0.8F, k * 0.45F);
			float bob = (float) Math.sin(System.currentTimeMillis() / 300.0) * 2;
			draw(ctx, deco, sw / 2F - w(deco, nk) / 2, top + blockH + (n > 0 && idx + 1 < ls.size() ? n * 12 * small + 8 : 6) + bob, nk, argb(c[1], 0.7F * alpha));
		}
	}

	private void bar(GuiGraphicsExtractor ctx, String text, float progress, int[] c, int sw) {
		float k = 1F;
		String t = text.length() > 70 ? text.substring(0, 67) + "..." : text;
		int tw = Math.round(w(t, k));
		int w = tw + 44, h = 18;
		int x = sw / 2 - w / 2, y = 6;
		Gfx.outline(ctx, x, y, w, h, 8, argb(c[1], 0.55F), argb(c[2], 0.82F));
		ctx.fill(x + 4, y + 3, x + 16, y + 15, argb(c[1], 0.35F));
		draw(ctx, "♪", x + 6.5F, y + 5, 1F, 0xFFFFFFFF);
		float tx = x + 21;
		draw(ctx, t, tx, y + 5, k, argb(c[0], 0.75F));
		int here = karaoke.getBool() ? Math.round(t.length() * progress) : t.length();
		if (here > 0) {
			draw(ctx, t.substring(0, Math.min(t.length(), here)), tx, y + 5, k, argb(c[1], 1F));
		}
		long ms = System.currentTimeMillis();
		for (int i = 0; i < 4; i++) {
			int bh = 3 + Math.round(5 * (0.5F + 0.5F * (float) Math.sin(ms / (120.0 + i * 37) + i)));
			int bx = x + w - 16 + i * 3;
			ctx.fill(bx, y + 13 - bh, bx + 2, y + 13, argb(c[1], 0.9F));
		}
	}
}
