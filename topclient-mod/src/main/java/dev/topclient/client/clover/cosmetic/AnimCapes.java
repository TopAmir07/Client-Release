package dev.topclient.client.clover.cosmetic;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.ui.Textures;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Animated Petal Shop capes. Each one is a strip of frames (512x256 each) inside the mod.
 * Frames are only turned into textures when they are actually needed: the cape you wear,
 * plus a single still frame for each menu tile (tiles animate while hovered).
 */
public final class AnimCapes {
	public static final String[][] LIST = {
		{"anim_veins_crimson", "Rift Veins (Crimson)", "16", "100"},
		{"anim_veins_violet", "Rift Veins (Violet)", "16", "100"},
		{"anim_veins_cyan", "Rift Veins (Cyan)", "16", "100"},
		{"anim_veins_gold", "Rift Veins (Gold)", "16", "100"},
		{"petal_anim_nebula_rose", "Drift Nebula (Rose)", "16", "100"},
		{"anim_nebula_teal", "Drift Nebula (Teal)", "16", "100"},
		{"anim_nebula_ember", "Drift Nebula (Ember)", "16", "100"},
		{"petal_anim_nebula_ice", "Drift Nebula (Ice)", "16", "100"},
		{"anim_starfall_midnight", "Starfall (Midnight)", "16", "100"},
		{"anim_starfall_dusk", "Starfall (Dusk)", "16", "100"},
		{"anim_starfall_emerald", "Starfall (Emerald)", "16", "100"},
		{"anim_starfall_sunrise", "Starfall (Sunrise)", "16", "100"},
		{"anim_clouds_sky", "Cloud Drift (Sky)", "16", "100"},
		{"anim_clouds_candy", "Cloud Drift (Candy)", "16", "100"},
		{"anim_clouds_sunset", "Cloud Drift (Sunset)", "16", "100"},
		{"anim_clouds_storm", "Cloud Drift (Storm)", "16", "100"},
		{"anim_moon_silver", "Moon Cycle (Silver)", "16", "100"},
		{"anim_moon_blood", "Moon Cycle (Blood)", "16", "100"},
		{"anim_moon_honey", "Moon Cycle (Honey)", "16", "100"},
		{"anim_moon_mint", "Moon Cycle (Mint)", "16", "100"},
		{"anim_petals_sakura", "Petal Fall (Sakura)", "16", "100"},
		{"anim_petals_midnight", "Petal Fall (Midnight)", "16", "100"},
		{"anim_petals_autumn", "Petal Fall (Autumn)", "16", "100"},
		{"anim_petals_frost", "Petal Fall (Frost)", "16", "100"},
		{"anim_embers_forge", "Embers (Forge)", "16", "100"},
		{"anim_embers_spirit", "Embers (Spirit)", "16", "100"},
		{"anim_embers_toxic", "Embers (Toxic)", "16", "100"},
		{"anim_embers_soul", "Embers (Soul)", "16", "100"},
		{"petal_anim_aurora_northern", "Aurora (Northern)", "16", "100"},
		{"petal_anim_aurora_rose", "Aurora (Rose)", "16", "100"},
		{"anim_aurora_solar", "Aurora (Solar)", "16", "100"},
		{"anim_aurora_arctic", "Aurora (Arctic)", "16", "100"},
		{"anim_waves_ocean", "Tide (Ocean)", "16", "100"},
		{"anim_waves_lagoon", "Tide (Lagoon)", "16", "100"},
		{"anim_waves_dusk", "Tide (Dusk)", "16", "100"},
		{"anim_waves_deep", "Tide (Deep)", "16", "100"},
		{"anim_digital_green", "Data Rain (Green)", "16", "100"},
		{"anim_digital_blue", "Data Rain (Blue)", "16", "100"},
		{"anim_digital_red", "Data Rain (Red)", "16", "100"},
		{"anim_digital_gold", "Data Rain (Gold)", "16", "100"},
		{"anim_lava_classic", "Magma (Classic)", "16", "100"},
		{"anim_lava_blue_flame", "Magma (Blue Flame)", "16", "100"},
		{"anim_lava_venom", "Magma (Venom)", "16", "100"},
		{"anim_lava_amethyst", "Magma (Amethyst)", "16", "100"},
		{"anim_glitch_classic", "Glitch Bloom (Classic)", "16", "100"},
		{"anim_glitch_mono", "Glitch Bloom (Mono)", "16", "100"},
		{"anim_glitch_acid", "Glitch Bloom (Acid)", "16", "100"},
		{"anim_glitch_ice", "Glitch Bloom (Ice)", "16", "100"},
		{"anim_plasma_rainbow", "Plasma (Rainbow)", "16", "100"},
		{"anim_plasma_sunset", "Plasma (Sunset)", "16", "100"},
		{"anim_plasma_ocean", "Plasma (Ocean)", "16", "100"},
		{"anim_plasma_candy", "Plasma (Candy)", "16", "100"},
		{"anim_circuit_cyan", "Circuit (Cyan)", "16", "100"},
		{"anim_circuit_lime", "Circuit (Lime)", "16", "100"},
		{"anim_circuit_magenta", "Circuit (Magenta)", "16", "100"},
		{"anim_circuit_gold", "Circuit (Gold)", "16", "100"},
		{"anim_fireflies_forest", "Firefly Grove (Forest)", "16", "100"},
		{"anim_fireflies_night", "Firefly Grove (Night)", "16", "100"},
		{"anim_fireflies_dream", "Firefly Grove (Dream)", "16", "100"},
		{"anim_fireflies_swamp", "Firefly Grove (Swamp)", "16", "100"},
		{"anim_snow_night", "Snowfall (Night)", "16", "100"},
		{"anim_snow_pine", "Snowfall (Pine)", "16", "100"},
		{"anim_snow_berry", "Snowfall (Berry)", "16", "100"},
		{"anim_snow_dawn", "Snowfall (Dawn)", "16", "100"},
		{"anim_storm_grey", "Thunderhead (Grey)", "16", "100"},
		{"anim_storm_purple", "Thunderhead (Purple)", "16", "100"},
		{"anim_storm_red", "Thunderhead (Red)", "16", "100"},
		{"anim_storm_green", "Thunderhead (Green)", "16", "100"},
		{"petal_anim_galaxy_violet", "Spiral Galaxy (Violet)", "16", "100"},
		{"petal_anim_galaxy_blue", "Spiral Galaxy (Blue)", "16", "100"},
		{"anim_galaxy_gold", "Spiral Galaxy (Gold)", "16", "100"},
		{"anim_galaxy_rose", "Spiral Galaxy (Rose)", "16", "100"},
		{"petal_anim_kente_royal", "Kente Weave (Royal)", "16", "100"},
		{"anim_kente_sunset", "Kente Weave (Sunset)", "16", "100"},
		{"anim_kente_ocean", "Kente Weave (Ocean)", "16", "100"},
		{"anim_kente_bloom", "Kente Weave (Bloom)", "16", "100"},
		{"anim_savanna_golden", "Savanna Sun (Golden)", "16", "100"},
		{"anim_savanna_rose", "Savanna Sun (Rose)", "16", "100"},
		{"anim_savanna_blood", "Savanna Sun (Blood)", "16", "100"},
		{"anim_savanna_violet", "Savanna Sun (Violet)", "16", "100"},
		{"anim_holo_prism", "Hologram (Prism)", "16", "100"},
		{"anim_holo_pearl", "Hologram (Pearl)", "16", "100"},
		{"anim_holo_oil", "Hologram (Oil)", "16", "100"},
		{"anim_holo_sunny", "Hologram (Sunny)", "16", "100"},
		{"petal_anim_portal_ender", "Vortex (Ender)", "16", "100"},
		{"anim_portal_nether", "Vortex (Nether)", "16", "100"},
		{"anim_portal_rift", "Vortex (Rift)", "16", "100"},
		{"anim_portal_abyss", "Vortex (Abyss)", "16", "100"},
		{"anim_synth_retro", "Synthwave (Retro)", "16", "100"},
		{"anim_synth_mint", "Synthwave (Mint)", "16", "100"},
		{"anim_synth_inferno", "Synthwave (Inferno)", "16", "100"},
		{"anim_synth_ice", "Synthwave (Ice)", "16", "100"},
		{"anim_fire_flame", "Wildfire (Flame)", "16", "100"},
		{"anim_fire_soul_fire", "Wildfire (Soul Fire)", "16", "100"},
		{"anim_fire_cursed", "Wildfire (Cursed)", "16", "100"},
		{"anim_fire_pink", "Wildfire (Pink)", "16", "100"},
		{"anim_prism_amethyst", "Crystal (Amethyst)", "16", "100"},
		{"anim_prism_emerald", "Crystal (Emerald)", "16", "100"},
		{"anim_prism_ruby", "Crystal (Ruby)", "16", "100"},
		{"anim_prism_diamond", "Crystal (Diamond)", "16", "100"},
		{"petal_anim_meteor_violet", "Meteor Night (Violet)", "16", "100"},
		{"anim_meteor_ember", "Meteor Night (Ember)", "16", "100"},
		{"anim_meteor_ice", "Meteor Night (Ice)", "16", "100"},
		{"anim_meteor_jade", "Meteor Night (Jade)", "16", "100"},
		{"petal_anim_peaks_aurora", "Northern Peaks (Aurora)", "16", "100"},
		{"petal_anim_peaks_rose", "Northern Peaks (Rose)", "16", "100"},
		{"anim_peaks_gold", "Northern Peaks (Gold)", "16", "100"},
		{"anim_peaks_ice", "Northern Peaks (Ice)", "16", "100"},
		{"anim_koi_classic", "Koi Pond (Classic)", "16", "100"},
		{"anim_koi_gold", "Koi Pond (Gold)", "16", "100"},
		{"anim_koi_night", "Koi Pond (Night)", "16", "100"},
		{"anim_koi_jade", "Koi Pond (Jade)", "16", "100"},
		{"petal_anim_city_pink", "Neon City (Pink)", "16", "100"},
		{"petal_anim_city_cyan", "Neon City (Cyan)", "16", "100"},
		{"anim_city_red", "Neon City (Red)", "16", "100"},
		{"anim_city_lime", "Neon City (Lime)", "16", "100"},
		{"petal_anim_blackhole_solar", "Event Horizon (Solar)", "16", "100"},
		{"petal_anim_blackhole_cosmic", "Event Horizon (Cosmic)", "16", "100"},
		{"anim_blackhole_cold", "Event Horizon (Cold)", "16", "100"},
		{"anim_blackhole_blood", "Event Horizon (Blood)", "16", "100"},
		{"anim_waterfall_jungle", "Waterfall (Jungle)", "16", "100"},
		{"anim_waterfall_canyon", "Waterfall (Canyon)", "16", "100"},
		{"anim_waterfall_night", "Waterfall (Night)", "16", "100"},
		{"anim_waterfall_magic", "Waterfall (Magic)", "16", "100"},
		{"anim_lavalamp_groovy", "Lava Lamp (Groovy)", "16", "100"},
		{"anim_lavalamp_ocean", "Lava Lamp (Ocean)", "16", "100"},
		{"anim_lavalamp_bubblegum", "Lava Lamp (Bubblegum)", "16", "100"},
		{"anim_lavalamp_toxic", "Lava Lamp (Toxic)", "16", "100"},
		{"anim_helix_plasma", "Helix (Plasma)", "16", "100"},
		{"anim_helix_gene", "Helix (Gene)", "16", "100"},
		{"anim_helix_fire", "Helix (Fire)", "16", "100"},
		{"anim_helix_frost", "Helix (Frost)", "16", "100"},
		{"anim_equalizer_neon", "Beat Drop (Neon)", "16", "100"},
		{"anim_equalizer_sunset", "Beat Drop (Sunset)", "16", "100"},
		{"anim_equalizer_matrix", "Beat Drop (Matrix)", "16", "100"},
		{"anim_equalizer_ice", "Beat Drop (Ice)", "16", "100"},
		{"anim_dunes_sahara", "Desert Night (Sahara)", "16", "100"},
		{"anim_dunes_namib", "Desert Night (Namib)", "16", "100"},
		{"anim_dunes_silver", "Desert Night (Silver)", "16", "100"},
		{"anim_dunes_dusk", "Desert Night (Dusk)", "16", "100"},
		{"anim_rain_city", "Rainy Window (City)", "16", "100"},
		{"anim_rain_cozy", "Rainy Window (Cozy)", "16", "100"},
		{"anim_rain_blue", "Rainy Window (Blue)", "16", "100"},
		{"anim_rain_neon", "Rainy Window (Neon)", "16", "100"},
		{"anim_kaleido_prism", "Kaleidoscope (Prism)", "16", "100"},
		{"anim_kaleido_ocean", "Kaleidoscope (Ocean)", "16", "100"},
		{"anim_kaleido_royal", "Kaleidoscope (Royal)", "16", "100"},
		{"anim_kaleido_ember", "Kaleidoscope (Ember)", "16", "100"},
		{"anim_tesla_blue", "Tesla Core (Blue)", "16", "100"},
		{"anim_tesla_violet", "Tesla Core (Violet)", "16", "100"},
		{"anim_tesla_red", "Tesla Core (Red)", "16", "100"},
		{"anim_tesla_green", "Tesla Core (Green)", "16", "100"}
	};
	private static final Map<String, int[]> INFO = new HashMap<>(); // id -> {frames, ms}
	static {
		for (String[] c : LIST) {
			INFO.put(c[0], new int[]{Integer.parseInt(c[2]), Integer.parseInt(c[3])});
		}
	}
	private static final Map<String, Identifier> FRAMES = new HashMap<>();
	private static final Map<String, Boolean> MISSING = new HashMap<>();
	// decoded strips kept briefly so a cape's frames don't decode the PNG 16 times
	private static final LinkedHashMap<String, BufferedImage> STRIPS = new LinkedHashMap<>(4, .75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
			return size() > 2;
		}
	};

	private AnimCapes() {
	}

	public static boolean is(String id) {
		return id != null && INFO.containsKey(id);
	}

	/** The frame that should show right now (or frame 0 when still). */
	public static Identifier frame(String id, boolean animate) {
		int[] in = INFO.get(id);
		if (in == null) {
			return null;
		}
		int f = animate ? (int) ((System.currentTimeMillis() / in[1]) % in[0]) : 0;
		Identifier t = texture(id, f);
		if (t == null && f != 0) {
			t = texture(id, 0);
		}
		return t;
	}

	private static Identifier texture(String id, int f) {
		String key = id + "#" + f;
		Identifier have = FRAMES.get(key);
		if (have != null || MISSING.containsKey(id)) {
			return have;
		}
		try {
			BufferedImage strip = STRIPS.get(id);
			if (strip == null) {
				try (InputStream in = AnimCapes.class.getResourceAsStream("/assets/" + CloverClient.MOD_ID + "/textures/cosmetics/capes/anim/" + id + ".png")) {
					strip = in == null ? null : ImageIO.read(in);
				}
				if (strip == null) {
					MISSING.put(id, true);
					return null;
				}
				STRIPS.put(id, strip);
			}
			int fw = strip.getWidth(), fh = fw / 2;
			if ((f + 1) * fh > strip.getHeight()) {
				return null;
			}
			NativeImage img = Textures.fromBuffered(strip.getSubimage(0, f * fh, fw, fh));
			Identifier tid = Identifier.fromNamespaceAndPath(CloverClient.MOD_ID, "anim/" + id + "/" + f);
			Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(tid::toString, img));
			FRAMES.put(key, tid);
			return tid;
		} catch (Exception e) {
			MISSING.put(id, true);
			return null;
		}
	}
}
