package dev.topclient.client.clover.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Microsoft sign-in window, like Dawn: opens Microsoft's own login page (login.live.com) in a small
 * Edge or Chrome window and waits for Microsoft to send the player back with a sign-in code.
 *
 * Safe by design:
 * - the window uses a brand-new, empty browser profile in a temporary folder, deleted afterwards,
 *   so no cookies or saved logins stay behind and your normal browser is never touched;
 * - the game only reads the window's address bar through the browser's local-only debug port
 *   (127.0.0.1), to spot Microsoft's "done" address. The password is typed into Microsoft's page
 *   and never passes through the game.
 */
public final class LiveWindow {
	private static final HttpClient LOCAL = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	private static volatile Process current;

	private LiveWindow() {
	}

	/** Edge first (built into Windows), then Chrome. Chromium-based only: they have the debug port. */
	static File browser() {
		String[][] tries = {
				{"ProgramFiles(x86)", "Microsoft\\Edge\\Application\\msedge.exe"}, {"ProgramFiles", "Microsoft\\Edge\\Application\\msedge.exe"},
				{"ProgramFiles", "Google\\Chrome\\Application\\chrome.exe"}, {"ProgramFiles(x86)", "Google\\Chrome\\Application\\chrome.exe"},
				{"LOCALAPPDATA", "Google\\Chrome\\Application\\chrome.exe"}};
		String want = SignInBrowsers.chosen();
		if ("chrome".equals(want)) { // the player picked Chrome: try it first
			tries = new String[][]{tries[2], tries[3], tries[4], tries[0], tries[1]};
		}
		for (String[] t : tries) {
			String base = System.getenv(t[0]);
			if (base != null) {
				File f = new File(base, t[1]);
				if (f.isFile()) {
					return f;
				}
			}
		}
		return null;
	}

	public static void cancel() {
		Process p = current;
		if (p != null) {
			p.destroy();
		}
	}

	/**
	 * Opens startUrl and blocks until the window reaches an address starting with prefix that holds
	 * a "code=". Returns that address. Throws when the window is closed, times out or is cancelled.
	 */
	public static String run(String startUrl, String prefix) throws IOException {
		File exe = browser();
		if (exe == null) {
			throw new MicrosoftAuth.AuthException("Microsoft Edge or Google Chrome is needed for the sign-in window.");
		}
		Path base = Path.of(System.getProperty("java.io.tmpdir"), "topclient-signin");
		deleteTree(base); // leftovers from an earlier window
		Path dir = base.resolve(Long.toHexString(System.nanoTime()));
		Files.createDirectories(dir);
		Path portFile = dir.resolve("DevToolsActivePort");
		List<String> cmd = new ArrayList<>(List.of(exe.getAbsolutePath(), "--user-data-dir=" + dir, "--remote-debugging-port=0",
				"--remote-debugging-address=127.0.0.1", "--no-first-run", "--no-default-browser-check", "--window-size=520,760",
				"--disable-features=Translate,msEdgeSidebarV2,msHubApps", "--app=" + startUrl));
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		current = p;
		try {
			String port = null;
			long until = System.currentTimeMillis() + 20_000;
			while (port == null) {
				if (Files.isRegularFile(portFile)) {
					String[] lines = Files.readString(portFile, StandardCharsets.UTF_8).replace("\r", "").split("\n");
					if (lines.length >= 1 && !lines[0].isBlank()) {
						port = lines[0].trim();
						break;
					}
				}
				if (!p.isAlive()) {
					throw new MicrosoftAuth.AuthException("The sign-in window was closed.");
				}
				if (System.currentTimeMillis() > until) {
					throw new MicrosoftAuth.AuthException("The sign-in window didn't start.");
				}
				sleep(100);
			}
			long deadline = System.currentTimeMillis() + 15 * 60_000;
			while (true) {
				String found = scan(port, prefix);
				if (found != null) {
					return found;
				}
				if (!p.isAlive()) {
					throw new MicrosoftAuth.AuthException("The sign-in window was closed before finishing.");
				}
				if (System.currentTimeMillis() > deadline) {
					throw new MicrosoftAuth.AuthException("Sign-in timed out.");
				}
				sleep(250);
			}
		} finally {
			current = null;
			p.destroy();
			try {
				p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (p.isAlive()) {
				p.destroyForcibly();
			}
			deleteTree(base); // the temporary profile: cookies, cache, everything
		}
	}

	/** Reads the open pages from the browser's local debug list (127.0.0.1 only). */
	private static String scan(String port, String prefix) {
		try {
			HttpResponse<String> r = LOCAL.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list"))
					.timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
			JsonElement e = JsonParser.parseString(r.body());
			if (!e.isJsonArray()) {
				return null;
			}
			JsonArray a = e.getAsJsonArray();
			for (JsonElement t : a) {
				JsonObject o = t.getAsJsonObject();
				String url = o.has("url") ? o.get("url").getAsString() : "";
				if (url.startsWith(prefix) && (url.contains("code=") || url.contains("error="))) {
					return url;
				}
			}
		} catch (IOException | RuntimeException e) {
			// window still starting or closing
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	private static void sleep(long ms) throws MicrosoftAuth.AuthException {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MicrosoftAuth.AuthException("Sign-in cancelled.");
		}
	}

	private static void deleteTree(Path root) {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> s = Files.walk(root)) {
			s.sorted(Comparator.reverseOrder()).forEach(f -> {
				try {
					Files.deleteIfExists(f);
				} catch (IOException ignored) {
					// a file the browser still holds; removed next time
				}
			});
		} catch (IOException ignored) {
		}
	}
}
