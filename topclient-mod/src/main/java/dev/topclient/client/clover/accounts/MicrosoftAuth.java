package dev.topclient.client.clover.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Microsoft sign-in, like Dawn: a small window opens straight on Microsoft's own login page
 * (login.live.com, Minecraft's official sign-in app), the player signs in there, and we get a
 * Minecraft token. Talks only to Microsoft, Xbox Live and Minecraft's own servers. Passwords never
 * pass through the game, and accounts are only stored on this PC.
 */
public final class MicrosoftAuth {
	/** Minecraft's own public sign-in app (the one the official launcher uses). */
	private static final String LIVE_ID = "00000000402b5328";
	private static final String LIVE_REDIRECT = "https://login.live.com/oauth20_desktop.srf";
	/** Only for refreshing accounts added with the old sign-in codes (before 3.13). */
	private static final String OLD_CODE_ID = "00000000441cc96b";
	private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
	private static final Set<String> HOSTS = Set.of("login.live.com", "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "api.minecraftservices.com");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();

	public record DeviceCode(String userCode, String deviceCode, String link, int interval, long expiresAt) {
	}

	public record Account(String name, UUID uuid, String accessToken, long expiresAt, String refreshToken) {
	}

	public static final class AuthException extends IOException {
		public AuthException(String msg) {
			super(msg);
		}
	}

	private MicrosoftAuth() {
	}

	private static JsonObject send(HttpRequest.Builder b, String url) throws IOException {
		URI uri = URI.create(url);
		if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
			throw new AuthException("Blocked untrusted host " + uri.getHost());
		}
		try {
			HttpResponse<String> r = HTTP.send(b.uri(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").build(),
					HttpResponse.BodyHandlers.ofString());
			JsonObject o = r.body() == null || r.body().isBlank() ? new JsonObject() : JsonParser.parseString(r.body()).getAsJsonObject();
			o.addProperty("_status", r.statusCode());
			return o;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("Cancelled");
		} catch (IllegalStateException | com.google.gson.JsonParseException e) {
			throw new AuthException("Unexpected answer from " + uri.getHost());
		}
	}

	private static JsonObject form(String url, Map<String, String> fields) throws IOException {
		StringJoiner j = new StringJoiner("&");
		fields.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return send(HttpRequest.newBuilder().header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(j.toString())), url);
	}

	private static JsonObject json(String url, JsonObject body, String bearer) throws IOException {
		HttpRequest.Builder b = HttpRequest.newBuilder().header("Content-Type", "application/json");
		if (bearer != null) {
			b.header("Authorization", "Bearer " + bearer);
		}
		b.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		return send(b, url);
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	private static int status(JsonObject o) {
		return o.has("_status") ? o.get("_status").getAsInt() : 0;
	}

	/** Opens the Microsoft sign-in window and waits until the player has signed in. Blocks. */
	public static Account signIn() throws IOException {
		String q = "client_id=" + LIVE_ID + "&response_type=code&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)
				+ "&redirect_uri=" + URLEncoder.encode(LIVE_REDIRECT, StandardCharsets.UTF_8) + "&prompt=select_account";
		String back = LiveWindow.run("https://login.live.com/oauth20_authorize.srf?" + q, LIVE_REDIRECT);
		String code = param(back, "code");
		if (code == null) {
			throw new AuthException("Sign-in was cancelled.");
		}
		JsonObject r = form("https://login.live.com/oauth20_token.srf", Map.of("client_id", LIVE_ID, "code", code,
				"grant_type", "authorization_code", "redirect_uri", LIVE_REDIRECT, "scope", SCOPE));
		String access = str(r, "access_token");
		if (access == null) {
			throw new AuthException("Microsoft didn't finish the sign-in. Try again.");
		}
		return minecraft(access, str(r, "refresh_token"));
	}

	private static String param(String url, String key) {
		int q = url.indexOf('?');
		if (q < 0) {
			return null;
		}
		for (String kv : url.substring(q + 1).split("[&#]")) {
			int e = kv.indexOf('=');
			if (e > 0 && kv.substring(0, e).equals(key)) {
				return java.net.URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	public static Account refresh(String refreshToken) throws IOException {
		// accounts from the sign-in window use Minecraft's app; older ones used the code app
		for (String id : new String[]{LIVE_ID, OLD_CODE_ID}) {
			JsonObject r = form("https://login.live.com/oauth20_token.srf", Map.of("client_id", id,
					"grant_type", "refresh_token", "refresh_token", refreshToken, "scope", SCOPE));
			String access = str(r, "access_token");
			if (access != null) {
				String newRefresh = str(r, "refresh_token");
				return minecraft(access, newRefresh == null ? refreshToken : newRefresh);
			}
		}
		throw new AuthException("This account's sign-in expired. Remove it and add it again.");
	}

	private static Account minecraft(String msAccess, String refresh) throws IOException {
		JsonObject xbl = null;
		for (String ticket : new String[]{msAccess, "t=" + msAccess, "d=" + msAccess}) {
			JsonObject props = new JsonObject();
			props.addProperty("AuthMethod", "RPS");
			props.addProperty("SiteName", "user.auth.xboxlive.com");
			props.addProperty("RpsTicket", ticket);
			JsonObject body = new JsonObject();
			body.add("Properties", props);
			body.addProperty("RelyingParty", "http://auth.xboxlive.com");
			body.addProperty("TokenType", "JWT");
			xbl = json("https://user.auth.xboxlive.com/user/authenticate", body, null);
			if (status(xbl) == 200 && str(xbl, "Token") != null) {
				break;
			}
		}
		if (xbl == null || str(xbl, "Token") == null) {
			throw new AuthException("Xbox Live sign-in failed.");
		}
		String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

		JsonObject props = new JsonObject();
		props.addProperty("SandboxId", "RETAIL");
		JsonArray tokens = new JsonArray();
		tokens.add(str(xbl, "Token"));
		props.add("UserTokens", tokens);
		JsonObject body = new JsonObject();
		body.add("Properties", props);
		body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		body.addProperty("TokenType", "JWT");
		JsonObject xsts = json("https://xsts.auth.xboxlive.com/xsts/authorize", body, null);
		if (str(xsts, "Token") == null) {
			long xerr = xsts.has("XErr") ? xsts.get("XErr").getAsLong() : 0;
			if (xerr == 2148916233L) {
				throw new AuthException("This Microsoft account has no Xbox profile yet. Sign in once at minecraft.net.");
			}
			if (xerr == 2148916238L) {
				throw new AuthException("This is a child account. An adult must add it to a Microsoft Family.");
			}
			throw new AuthException("Xbox security sign-in failed.");
		}

		JsonObject mcBody = new JsonObject();
		mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + str(xsts, "Token"));
		JsonObject mc = json("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody, null);
		String mcToken = str(mc, "access_token");
		if (mcToken == null) {
			throw new AuthException("Minecraft sign-in failed.");
		}
		long expires = System.currentTimeMillis() + (mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400) * 1000L - 300_000L;

		JsonObject profile = send(HttpRequest.newBuilder().header("Authorization", "Bearer " + mcToken).GET(),
				"https://api.minecraftservices.com/minecraft/profile");
		if (status(profile) == 404) {
			throw new AuthException("This account doesn't own Minecraft: Java Edition.");
		}
		String id = str(profile, "id"), name = str(profile, "name");
		if (id == null || name == null) {
			throw new AuthException("Couldn't load the Minecraft profile.");
		}
		UUID uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
		return new Account(name, uuid, mcToken, expires, refresh);
	}
}
