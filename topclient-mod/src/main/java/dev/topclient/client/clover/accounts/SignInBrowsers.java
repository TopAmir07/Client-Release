package dev.topclient.client.clover.accounts;

import dev.topclient.client.clover.Config;
import net.minecraft.util.Util;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Which browser opens the Microsoft sign-in page. Every choice opens a private window,
 * so the browser can't sign straight into an account it already remembers. The launcher
 * writes the player's choice to ui.signinBrowser; it can also be changed in the switcher.
 */
public final class SignInBrowsers {
	public record Browser(String id, String name, String[] paths, String[] args) {
	}

	private static final String PF = "ProgramFiles", PF86 = "ProgramFiles(x86)", LAD = "LOCALAPPDATA";

	public static final List<Browser> ALL = List.of(
			new Browser("chrome", "Chrome", new String[]{PF + "|Google\\Chrome\\Application\\chrome.exe", PF86 + "|Google\\Chrome\\Application\\chrome.exe", LAD + "|Google\\Chrome\\Application\\chrome.exe"}, new String[]{"--incognito", "--new-window"}),
			new Browser("edge", "Edge", new String[]{PF86 + "|Microsoft\\Edge\\Application\\msedge.exe", PF + "|Microsoft\\Edge\\Application\\msedge.exe"}, new String[]{"--inprivate"}),
			new Browser("firefox", "Firefox", new String[]{PF + "|Mozilla Firefox\\firefox.exe", PF86 + "|Mozilla Firefox\\firefox.exe"}, new String[]{"-private-window"}),
			new Browser("brave", "Brave", new String[]{PF + "|BraveSoftware\\Brave-Browser\\Application\\brave.exe", PF86 + "|BraveSoftware\\Brave-Browser\\Application\\brave.exe", LAD + "|BraveSoftware\\Brave-Browser\\Application\\brave.exe"}, new String[]{"--incognito"}),
			new Browser("operagx", "Opera GX", new String[]{LAD + "|Programs\\Opera GX\\opera.exe", LAD + "|Programs\\Opera GX\\launcher.exe"}, new String[]{"--private"}),
			new Browser("opera", "Opera", new String[]{LAD + "|Programs\\Opera\\opera.exe", LAD + "|Programs\\Opera\\launcher.exe"}, new String[]{"--private"}),
			new Browser("vivaldi", "Vivaldi", new String[]{LAD + "|Vivaldi\\Application\\vivaldi.exe", PF + "|Vivaldi\\Application\\vivaldi.exe"}, new String[]{"--incognito"})
	);

	private SignInBrowsers() {
	}

	static File exe(Browser b) {
		for (String p : b.paths()) {
			String[] parts = p.split("\\|", 2);
			String base = System.getenv(parts[0]);
			if (base != null) {
				File f = new File(base, parts[1]);
				if (f.isFile()) {
					return f;
				}
			}
		}
		return null;
	}

	/** Installed browsers plus "default" (the normal Windows browser, not private). */
	public static List<String> choices() {
		List<String> out = new ArrayList<>();
		out.add("auto");
		for (Browser b : ALL) {
			if (exe(b) != null) {
				out.add(b.id());
			}
		}
		out.add("default");
		return out;
	}

	public static String chosen() {
		try {
			return Config.get("ui.signinBrowser", "auto");
		} catch (RuntimeException e) {
			return "auto";
		}
	}

	public static void choose(String id) {
		Config.set("ui.signinBrowser", id);
		Config.saveIfNeeded(true);
	}

	public static String label(String id) {
		if ("auto".equals(id)) {
			return "Automatic (private)";
		}
		if ("default".equals(id)) {
			return "Windows default";
		}
		for (Browser b : ALL) {
			if (b.id().equals(id)) {
				return b.name() + " (private)";
			}
		}
		return id;
	}

	/** Opens a Microsoft sign-in link; nothing else is ever opened this way. */
	public static void open(String url) {
		if (url == null || !(url.startsWith("https://www.microsoft.com/") || url.startsWith("https://microsoft.com/") || url.startsWith("https://login.live.com/"))) {
			return;
		}
		String want = chosen();
		if (!"default".equals(want)) {
			try {
				for (Browser b : ALL) {
					if (!"auto".equals(want) && !b.id().equals(want)) {
						continue;
					}
					File f = exe(b);
					if (f != null) {
						List<String> cmd = new ArrayList<>();
						cmd.add(f.getAbsolutePath());
						cmd.addAll(List.of(b.args()));
						cmd.add(url);
						new ProcessBuilder(cmd).start();
						return;
					}
				}
			} catch (Exception ignored) {
			}
		}
		try {
			Util.getPlatform().openUri(URI.create(url));
		} catch (RuntimeException ignored) {
		}
	}
}
