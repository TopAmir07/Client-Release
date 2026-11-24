package dev.topclient.client.visuals;

import dev.topclient.client.clover.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.Pack;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class VisualsPack {

	public static final String PACK_FOLDER = "TopClientVisuals";
	public static final String PACK_ID = "file/" + PACK_FOLDER;
	private static final long MAX_ENTRY = 24L * 1024 * 1024;
	private static final long MAX_PACK = 160L * 1024 * 1024;

	public record Preset(String name, Map<String, String> picks) {
	}

	private static final Map<String, String> applied = new LinkedHashMap<>();
	private static final List<Preset> presets = new ArrayList<>();
	private static final Map<String, Boolean> verified = new ConcurrentHashMap<>();
	private static boolean loaded;

	private VisualsPack() {
	}

	private static Path selectionFile() {
		return Config.dir().resolve("visuals-selection.properties");
	}

	private static Path presetsFile() {
		return Config.dir().resolve("visuals-presets.properties");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(selectionFile())) {
			p.load(in);
		} catch (Exception ignored) {
		}
		for (String k : p.stringPropertyNames()) {
			applied.put(k, p.getProperty(k));
		}
		Properties q = new Properties();
		try (InputStream in = Files.newInputStream(presetsFile())) {
			q.load(in);
		} catch (Exception ignored) {
		}
		for (int i = 0; i < 20; i++) {
			String name = q.getProperty("preset." + i + ".name");
			String data = q.getProperty("preset." + i + ".picks");
			if (name == null || data == null) {
				continue;
			}
			presets.add(new Preset(name, decode(data)));
		}
	}

	private static Map<String, String> decode(String data) {
		Map<String, String> m = new LinkedHashMap<>();
		for (String part : data.split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) {
				m.put(part.substring(0, eq), part.substring(eq + 1));
			}
		}
		return m;
	}

	private static String encode(Map<String, String> m) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : m.entrySet()) {
			if (sb.length() > 0) {
				sb.append(';');
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		return sb.toString();
	}

	public static Map<String, String> applied() {
		load();
		return new LinkedHashMap<>(applied);
	}

	public static List<Preset> presets() {
		load();
		return List.copyOf(presets);
	}

	public static void savePreset(String name, Map<String, String> picks) {
		load();
		presets.removeIf(p -> p.name().equalsIgnoreCase(name));
		presets.add(0, new Preset(name, new LinkedHashMap<>(picks)));
		while (presets.size() > 20) {
			presets.remove(presets.size() - 1);
		}
		savePresets();
	}

	public static void deletePreset(String name) {
		load();
		presets.removeIf(p -> p.name().equalsIgnoreCase(name));
		savePresets();
	}

	private static void savePresets() {
		Properties q = new Properties();
		for (int i = 0; i < presets.size(); i++) {
			q.setProperty("preset." + i + ".name", presets.get(i).name());
			q.setProperty("preset." + i + ".picks", encode(presets.get(i).picks()));
		}
		try {
			Files.createDirectories(Config.dir());
			try (OutputStream out = Files.newOutputStream(presetsFile())) {
				q.store(out, "TopClient Visuals presets (stays on this PC)");
			}
		} catch (Exception ignored) {
		}
	}

	private static void saveSelection() {
		Properties p = new Properties();
		applied.forEach(p::setProperty);
		try {
			Files.createDirectories(Config.dir());
			try (OutputStream out = Files.newOutputStream(selectionFile())) {
				p.store(out, "TopClient Visuals (stays on this PC)");
			}
		} catch (Exception ignored) {
		}
	}

	public static boolean verify(VisualsData.Style s) {
		Path f = VisualsData.packFile(s);
		try {
			String key = f + "|" + Files.size(f) + "|" + Files.getLastModifiedTime(f).toMillis();
			Boolean have = verified.get(key);
			if (have != null) {
				return have;
			}
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(f)) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) > 0) {
					md.update(buf, 0, n);
				}
			}
			boolean ok = HexFormat.of().formatHex(md.digest()).equals(s.sha256());
			verified.put(key, ok);
			return ok;
		} catch (Exception e) {
			return false;
		}
	}

	static String clean(String raw) {
		String p = raw.replace('\\', '/');
		while (p.startsWith("./")) {
			p = p.substring(2);
		}
		if (p.startsWith("/") || p.contains("..") || p.contains(":") || !p.startsWith("assets/")) {
			return null;
		}
		String lower = p.toLowerCase();
		boolean png = lower.endsWith(".png");
		boolean meta = lower.endsWith(".png.mcmeta");
		boolean sky = lower.startsWith("assets/minecraft/optifine/sky/") && lower.endsWith(".properties");
		boolean model = lower.endsWith(".json") && (lower.contains("/models/") || lower.contains("/items/"));
		if (!(png || meta || sky || model)) {
			return null;
		}
		if (!p.matches("[A-Za-z0-9_./-]+")) {
			return null;
		}
		p = p.replace("/textures/items/", "/textures/item/").replace("/textures/blocks/", "/textures/block/");
		return p;
	}

	private static void wipe(Path dir) throws Exception {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				if (!p.equals(dir)) {
					Files.deleteIfExists(p);
				}
			}
		}
	}

	public record Result(int applied, int skipped) {
	}

	public static Result build(Map<String, String> picks) throws Exception {
		Minecraft mc = Minecraft.getInstance();
		Path root = mc.getResourcePackDirectory().resolve(PACK_FOLDER).toAbsolutePath().normalize();
		Files.createDirectories(root);
		wipe(root);
		int ok = 0;
		int skipped = 0;
		for (VisualsData.Category c : VisualsData.categories()) {
			String id = picks.get(c.id());
			VisualsData.Style s = id == null ? null : VisualsData.style(id);
			if (s == null) {
				continue;
			}
			if (!verify(s)) {
				skipped++;
				continue;
			}
			long total = 0;
			try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(VisualsData.packFile(s)))) {
				ZipEntry e;
				byte[] buf = new byte[16384];
				while ((e = zip.getNextEntry()) != null) {
					if (e.isDirectory()) {
						continue;
					}
					String path = clean(e.getName());
					if (path == null) {
						continue;
					}
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					int n;
					long size = 0;
					while ((n = zip.read(buf)) > 0) {
						size += n;
						if (size > MAX_ENTRY) {
							break;
						}
						out.write(buf, 0, n);
					}
					total += size;
					if (size > MAX_ENTRY || total > MAX_PACK) {
						continue;
					}
					Path target = root.resolve(path).normalize();
					if (!target.startsWith(root)) {
						continue;
					}
					Files.createDirectories(target.getParent());
					Files.write(target, out.toByteArray());
				}
			}
			ok++;
		}
		String meta = "{\n  \"pack\": {\n    \"description\": \"TopClient Visuals\",\n    \"min_format\": 75,\n    \"max_format\": 75\n  }\n}\n";
		Files.writeString(root.resolve("pack.mcmeta"), meta, StandardCharsets.UTF_8);
		try (InputStream icon = VisualsPack.class.getResourceAsStream("/assets/topclient/icon.png")) {
			if (icon != null) {
				Files.write(root.resolve("pack.png"), icon.readAllBytes());
			}
		}
		return new Result(ok, skipped);
	}

	public static String activate(Map<String, String> picks) {
		load();
		applied.clear();
		applied.putAll(picks);
		saveSelection();
		Minecraft mc = Minecraft.getInstance();
		PackRepository m = mc.getResourcePackRepository();
		m.reload();
		Pack profile = m.getPack(PACK_ID);
		if (!picks.isEmpty()) {
			if (profile == null) {
				return "Minecraft did not find the TopClientVisuals pack in your resourcepacks folder";
			}
			if (!profile.getCompatibility().isCompatible()) {
				return "Minecraft says the TopClientVisuals pack is made for another version";
			}
		}
		List<String> before = new ArrayList<>(mc.options.resourcePacks);
		if (picks.isEmpty()) {
			m.removePack(PACK_ID);
		} else {
			if (m.getSelectedIds().contains(PACK_ID)) {
				m.removePack(PACK_ID);
			}
			m.addPack(PACK_ID);
		}
		mc.options.updateResourcePacks(m);
		if (before.equals(mc.options.resourcePacks)) {
			mc.reloadResourcePacks();
		}
		return null;
	}
}
