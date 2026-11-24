package dev.topclient.client.clover.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.Config;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Hats from the launcher's Cosmetics page. Each hat is a list of textured quads in the player
 * model's head space (assets/topclient/hats/<id>.json + textures/hats/<id>.png), drawn on your
 * own head only. Everything comes from inside this mod's jar; nothing is downloaded.
 */
public final class Hats {
	/** A moving part (e.g. one wing): swings around its pivot. */
	public record Part(float px, float py, float pz, char axis, float amp, float period, float phase, float spin, float bob, float bobp, float[][] quads) {
	}

	/** Whole-model motion (pets): bob/hop up and down and sway around a pivot. rots: {axis(0=x,1=y,2=z), amp, period, phase}. */
	/** An animated bone (Cubees): its quads in rest pose and one 3x4 matrix per animation frame (rows, pixels). */
	public record Bone(float[][] quads, float[][] mats) {
	}

	public record Root(float px, float py, float pz, float bob, float bobp, float hop, float hopp, float[][] rots) {
	}

	public record Model(String id, Identifier texture, float tw, float th, float fh, int frames, float fps, boolean translucent, boolean glow, boolean body,
						float[][] quads, Part[] parts, Root root, boolean skin, Bone[] bones, float animFps) {
		/** Texture row offset for the current animation frame. */
		public float frameOffset() {
			if (frames <= 1) {
				return 0;
			}
			// double maths on a small time value: a float of the full clock only changes every ~2 minutes
			double secs = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
			int f = (int) (secs * Math.max(1F, fps)) % frames;
			return f * fh;
		}
	}

	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");
	private static final Map<String, Optional<Model>> CACHE = new ConcurrentHashMap<>();

	private Hats() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> pr) {
				helper.register(new HatFeature((RenderLayerParent) pr));
			}
		});
	}

	/** The hat the player picked in the launcher, or null. */
	public static Model current() {
		return current("cos.hat");
	}

	/** The item in a slot ("cos.hat", "cos.band", "cos.wings", "cos.buddy" or "cos.aura"), or null. */
	public static Model current(String key) {
		String id;
		try {
			id = Config.get(key, "none");
		} catch (RuntimeException e) {
			return null;
		}
		if (id == null || id.equals("none") || id.isEmpty()) {
			return null;
		}
		// performance: this runs every frame for every slot, so skip the checks while nothing changed
		Object[] memo = CURRENT.get(key);
		String owned = Config.get("cos.owned", "");
		if (memo != null && memo[0] == id && memo[1] == owned) {
			return (Model) memo[2];
		}
		Model m = !ID.matcher(id).matches() || !Cosmetics.owned(id) ? null : CACHE.computeIfAbsent(id, Hats::load).orElse(null);
		CURRENT.put(key, new Object[]{id, owned, m});
		return m;
	}

	private static final java.util.Map<String, Object[]> CURRENT = new java.util.HashMap<>();

	/** A model by id (another TopClient player's pick). Only ids of items that ship with the mod load. */
	public static Model byId(String id) {
		if (id == null || !ID.matcher(id).matches()) {
			return null;
		}
		return CACHE.computeIfAbsent(id, Hats::load).orElse(null);
	}

	/** The same model for slim (3px arm) skins, when there is one (Mini-Me). */
	public static Model slimVariant(Model m) {
		if (m == null || !m.skin()) {
			return m;
		}
		return CACHE.computeIfAbsent(m.id() + "_slim", Hats::load).orElse(m);
	}

	private static Root root(JsonObject o) {
		if (o == null) {
			return null;
		}
		JsonArray pv = o.getAsJsonArray("pivot");
		JsonArray rs = o.has("rot") ? o.getAsJsonArray("rot") : new JsonArray();
		float[][] rots = new float[rs.size()][];
		for (int i = 0; i < rots.length; i++) {
			JsonArray r = rs.get(i).getAsJsonArray();
			String ax = r.get(0).getAsString();
			rots[i] = new float[] {ax.startsWith("x") ? 0 : ax.startsWith("z") ? 2 : 1, r.get(1).getAsFloat(), Math.max(0.05F, r.get(2).getAsFloat()), r.size() > 3 ? r.get(3).getAsFloat() : 0F};
		}
		return new Root(pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat(),
				o.has("bob") ? o.get("bob").getAsFloat() : 0F, o.has("bobp") ? Math.max(0.05F, o.get("bobp").getAsFloat()) : 2F,
				o.has("hop") ? o.get("hop").getAsFloat() : 0F, o.has("hopp") ? Math.max(0.05F, o.get("hopp").getAsFloat()) : 1F, rots);
	}

	private static Bone[] bones(JsonObject o) {
		if (!o.has("bones")) {
			return new Bone[0];
		}
		JsonArray bs = o.getAsJsonArray("bones");
		Bone[] out = new Bone[bs.size()];
		for (int i = 0; i < out.length; i++) {
			JsonObject b = bs.get(i).getAsJsonObject();
			JsonArray ms = b.getAsJsonArray("m");
			float[][] mats = new float[Math.max(1, ms.size())][];
			for (int f = 0; f < ms.size(); f++) {
				JsonArray m = ms.get(f).getAsJsonArray();
				mats[f] = new float[12];
				for (int j = 0; j < 12; j++) {
					mats[f][j] = m.get(j).getAsFloat();
				}
			}
			if (ms.isEmpty()) {
				mats[0] = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
			}
			out[i] = new Bone(quads(b.getAsJsonArray("q")), mats);
		}
		return out;
	}

	private static float[][] quads(JsonArray qs) {
		float[][] quads = new float[qs == null ? 0 : qs.size()][];
		for (int i = 0; i < quads.length; i++) {
			JsonArray a = qs.get(i).getAsJsonArray();
			float[] q = new float[a.size()];
			for (int k = 0; k < q.length; k++) {
				q[k] = a.get(k).getAsFloat();
			}
			quads[i] = q;
		}
		return quads;
	}

	private static Optional<Model> load(String id) {
		try {
			Identifier file = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "hats/" + id + ".json");
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
			if (res.isEmpty()) {
				return Optional.empty();
			}
			try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				float[][] quads = quads(o.getAsJsonArray("q"));
				Part[] parts = new Part[0];
				if (o.has("parts")) {
					JsonArray ps = o.getAsJsonArray("parts");
					parts = new Part[ps.size()];
					for (int i = 0; i < parts.length; i++) {
						JsonObject p = ps.get(i).getAsJsonObject();
						JsonArray pv = p.getAsJsonArray("pivot");
						String ax = p.has("axis") ? p.get("axis").getAsString() : "y";
						parts[i] = new Part(pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat(), ax.isEmpty() ? 'y' : ax.charAt(0),
								p.get("amp").getAsFloat(), Math.max(0.05F, p.get("period").getAsFloat()), p.has("phase") ? p.get("phase").getAsFloat() : 0F,
								p.has("spin") ? p.get("spin").getAsFloat() : 0F, p.has("bob") ? p.get("bob").getAsFloat() : 0F,
								p.has("bobp") ? Math.max(0.05F, p.get("bobp").getAsFloat()) : 2F, quads(p.getAsJsonArray("q")));
					}
				}
				float th = o.get("th").getAsFloat();
				int frames = o.has("frames") ? Math.max(1, o.get("frames").getAsInt()) : 1;
				return Optional.of(new Model(id, Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "textures/hats/" + id + ".png"),
						o.get("tw").getAsFloat(), th, o.has("fh") ? o.get("fh").getAsFloat() : th, frames,
						o.has("fps") ? o.get("fps").getAsFloat() : 8F, o.has("translucent") && o.get("translucent").getAsBoolean(), o.has("glow") && o.get("glow").getAsBoolean(),
						o.has("slot") && "body".equals(o.get("slot").getAsString()), quads, parts,
						o.has("root") ? root(o.getAsJsonObject("root")) : null, o.has("skin") && o.get("skin").getAsBoolean(), bones(o),
						o.has("anim") ? Math.max(1F, o.getAsJsonObject("anim").get("fps").getAsFloat()) : 12F));
			}
		} catch (Exception e) {
			CloverClient.LOGGER.warn("Hat {} could not be loaded: {}", id, e.toString());
			return Optional.empty();
		}
	}
}
