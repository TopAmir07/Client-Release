package dev.topclient.client.clover.accounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.topclient.client.clover.CloverClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saved Microsoft accounts and switching between them while the game runs.
 * Accounts are stored in config/topclient/accounts.dat, encrypted with Windows' own
 * protection (only your Windows user on this PC can read them).
 */
public final class Accounts {
	public static final class Saved {
		public String name;
		public String uuid;
		public String accessToken;
		public long expiresAt;
		public String refreshToken;
	}

	private static final Gson GSON = new GsonBuilder().create();
	private static List<Saved> list;
	private static Object launcherSession;
	private static String launcherName;
	private static UUID launcherUuid;
	private static String launcherToken;

	private Accounts() {
	}

	private static Path file() {
		return dev.topclient.client.LegitConfig.dir().resolve("accounts.dat");
	}

	public static synchronized List<Saved> all() {
		if (list == null) {
			list = new ArrayList<>();
			try {
				Path f = file();
				if (Files.isRegularFile(f)) {
					String text = new String(unprotect(Files.readAllBytes(f)), StandardCharsets.UTF_8);
					List<Saved> got = GSON.fromJson(text, new TypeToken<List<Saved>>() {
					}.getType());
					if (got != null) {
						list.addAll(got);
					}
				}
			} catch (Exception e) {
				CloverClient.LOGGER.warn("Could not read saved accounts: {}", e.toString());
			}
		}
		return list;
	}

	private static synchronized void save() {
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.write(f, protect(GSON.toJson(all()).getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			CloverClient.LOGGER.warn("Could not save accounts: {}", e.toString());
		}
	}

	// --- Windows DPAPI through JNA (bundled with Minecraft); plain on other systems ---
	private static byte[] protect(byte[] data) {
		try {
			byte[] enc = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data);
			byte[] out = new byte[enc.length + 4];
			System.arraycopy("DPA1".getBytes(StandardCharsets.US_ASCII), 0, out, 0, 4);
			System.arraycopy(enc, 0, out, 4, enc.length);
			return out;
		} catch (Throwable t) {
			// On Windows the sign-in data is only ever saved encrypted. If encryption isn't
			// available, nothing is written (you stay signed in until the game closes).
			if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
				throw new IllegalStateException("Windows encryption is not available, accounts not saved");
			}
			return ("B64:" + Base64.getEncoder().encodeToString(data)).getBytes(StandardCharsets.US_ASCII);
		}
	}

	private static byte[] unprotect(byte[] data) {
		String head = new String(data, 0, Math.min(4, data.length), StandardCharsets.US_ASCII);
		if (head.equals("DPA1")) {
			byte[] enc = new byte[data.length - 4];
			System.arraycopy(data, 4, enc, 0, enc.length);
			return com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(enc);
		}
		if (head.equals("B64:")) {
			return Base64.getDecoder().decode(new String(data, 4, data.length - 4, StandardCharsets.US_ASCII));
		}
		return new byte[]{'[', ']'};
	}

	public static synchronized void add(MicrosoftAuth.Account a) {
		all().removeIf(s -> s.uuid.equals(a.uuid().toString()));
		Saved s = new Saved();
		s.name = a.name();
		s.uuid = a.uuid().toString();
		s.accessToken = a.accessToken();
		s.expiresAt = a.expiresAt();
		s.refreshToken = a.refreshToken();
		all().add(0, s);
		save();
	}

	public static synchronized void remove(Saved s) {
		all().remove(s);
		save();
	}

	public static String currentName() {
		try {
			return Minecraft.getInstance().getUser().getName();
		} catch (RuntimeException e) {
			return "";
		}
	}

	public static UUID launcherUuid() {
		return launcherUuid;
	}

	public static String launcherName() {
		remember();
		return launcherName;
	}

	public static boolean onLauncherAccount() {
		remember();
		return launcherSession != null && Minecraft.getInstance().getUser() == launcherSession;
	}

	/** Remembers the account the launcher started the game with, so you can switch back. */
	public static void remember() {
		if (launcherSession == null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.getUser() != null) {
				launcherSession = mc.getUser();
				launcherName = mc.getUser().getName();
				launcherUuid = mc.getUser().getProfileId();
				launcherToken = mc.getUser().getAccessToken();
			}
		}
	}

	/** Refreshes the token if needed (network: call off the main thread). */
	public static MicrosoftAuth.Account fresh(Saved s) throws Exception {
		if (s.accessToken != null && System.currentTimeMillis() < s.expiresAt) {
			return new MicrosoftAuth.Account(s.name, UUID.fromString(s.uuid), s.accessToken, s.expiresAt, s.refreshToken);
		}
		MicrosoftAuth.Account a = MicrosoftAuth.refresh(s.refreshToken);
		add(a);
		return a;
	}

	public static void backToLauncher() throws Exception {
		remember();
		if (launcherSession != null) {
			setSession(Minecraft.getInstance(), launcherSession, launcherName, launcherUuid, launcherToken);
		}
	}

	/** Switches the game to this account (call on the main thread). */
	public static void use(MicrosoftAuth.Account a) throws Exception {
		remember();
		Minecraft mc = Minecraft.getInstance();
		Object session = makeSession(mc.getUser().getClass(), a.name(), a.uuid(), a.accessToken());
		setSession(mc, session, a.name(), a.uuid(), a.accessToken());
	}

	private static Object makeSession(Class<?> type, String name, UUID uuid, String token) throws Exception {
		Exception last = null;
		for (Constructor<?> c : type.getDeclaredConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			Object[] args = new Object[p.length];
			int strings = 0;
			boolean ok = true;
			for (int i = 0; i < p.length; i++) {
				if (p[i] == String.class) {
					args[i] = strings == 0 ? name : strings == 1 ? token : "";
					strings++;
				} else if (p[i] == UUID.class) {
					args[i] = uuid;
				} else if (p[i] == Optional.class) {
					args[i] = Optional.empty();
				} else if (p[i].isEnum()) {
					Object[] values = p[i].getEnumConstants();
					Object pick = values[values.length - 1];
					for (Object v : values) {
						if (((Enum<?>) v).name().equalsIgnoreCase("msa")) {
							pick = v;
						}
					}
					args[i] = pick;
				} else {
					ok = false;
				}
			}
			if (!ok || strings < 2) {
				continue;
			}
			try {
				c.setAccessible(true);
				return c.newInstance(args);
			} catch (Exception e) {
				last = e;
			}
		}
		throw last != null ? last : new IllegalStateException("No way to build a session");
	}

	private static void setSession(Minecraft mc, Object session, String name, UUID uuid, String token) throws Exception {
		Class<?> sessionType = mc.getUser().getClass();
		boolean set = false;
		for (Field f : Minecraft.class.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers()) && f.getType() == sessionType) {
				f.setAccessible(true);
				f.set(mc, session);
				set = true;
			}
		}
		if (!set) {
			throw new IllegalStateException("Could not switch account");
		}
		// Best effort: profile, chat-signing keys and social services for the new account.
		for (Field f : Minecraft.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			try {
				if (f.getType() == com.mojang.authlib.GameProfile.class) {
					Constructor<?> c = com.mojang.authlib.GameProfile.class.getConstructor(UUID.class, String.class);
					f.setAccessible(true);
					f.set(mc, c.newInstance(uuid, name));
				}
			} catch (Throwable t) {
				CloverClient.LOGGER.debug("Profile update skipped: {}", t.toString());
			}
		}
		try {
			Object authService = null;
			Field apiField = null;
			for (Field f : Minecraft.class.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				if (com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					authService = f.get(mc);
				} else if (f.getType() == com.mojang.authlib.minecraft.UserApiService.class) {
					apiField = f;
				}
			}
			if (authService != null && apiField != null) {
				Object api = ((com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService) authService).createUserApiService(token);
				apiField.setAccessible(true);
				apiField.set(mc, api);
				for (Field f : Minecraft.class.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					for (Method m : f.getType().getDeclaredMethods()) {
						Class<?>[] p = m.getParameterTypes();
						if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == f.getType() && p.length == 3
								&& p[0] == com.mojang.authlib.minecraft.UserApiService.class && p[1] == sessionType && p[2] == Path.class) {
							m.setAccessible(true);
							f.setAccessible(true);
							f.set(mc, m.invoke(null, api, session, FabricLoader.getInstance().getGameDir()));
						}
					}
				}
			}
		} catch (Throwable t) {
			CloverClient.LOGGER.info("Chat keys for the new account will load next restart ({})", t.toString());
		}
		CloverClient.LOGGER.info("Switched account to {}", name);
	}
}
