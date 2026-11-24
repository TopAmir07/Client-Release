package dev.topclient.client.clover.module;

import dev.topclient.client.clover.mixin.DisplayEntityAccessor;
import dev.topclient.client.clover.mixin.TextDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class SkyLyrics {
	private static final int PREV = 0, CUR = 1, NEXT = 2;
	private static final int LINE_WIDTH = 520;
	private static final Display.TextDisplay[] SIGNS = new Display.TextDisplay[3];
	private static ClientLevel world;
	private static int nextId = -3_000_000;
	private static float yaw = Float.NaN;

	private SkyLyrics() {
	}

	static void clear() {
		for (int i = 0; i < SIGNS.length; i++) {
			if (SIGNS[i] != null && world != null) {
				try {
					world.removeEntity(SIGNS[i].getId(), Entity.RemovalReason.DISCARDED);
				} catch (RuntimeException ignored) {
				}
			}
			SIGNS[i] = null;
		}
		world = null;
	}

	static Display.TextDisplay make(ClientLevel w) {
		Entity e = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(w, EntitySpawnReason.LOAD);
		if (!(e instanceof Display.TextDisplay t)) {
			return null;
		}
		t.setId(nextId--);
		if (nextId > -2_500_000) {
			nextId = -3_000_000;
		}
		DisplayEntityAccessor d = (DisplayEntityAccessor) (Object) t;
		d.topclient$setBillboardMode(Display.BillboardConstraints.CENTER);
		d.topclient$setBrightness(new Brightness(15, 15));
		d.topclient$setViewRange(6F);
		((TextDisplayAccessor) (Object) t).topclient$setLineWidth(LINE_WIDTH);
		w.addEntity(t);
		return t;
	}

	static void tick(Minecraft mc, SpotifyLyrics lyrics) {
		ClientLevel w = mc.level;
		SpotifyLyrics.Now now = w == null || mc.player == null || !lyrics.skyOn() ? null : lyrics.now();
		if (now == null) {
			clear();
			return;
		}
		if (w != world) {
			clear();
			world = w;
		}
		for (int i = 0; i < SIGNS.length; i++) {
			if (SIGNS[i] == null || SIGNS[i].isRemoved()) {
				SIGNS[i] = make(w);
				if (SIGNS[i] == null) {
					clear();
					return;
				}
			}
		}

		float playerYaw = mc.player.getYRot();
		int follow = lyrics.skyFollow.getMode();
		if (Float.isNaN(yaw) || follow == 1) {
			yaw = playerYaw;
		} else if (follow == 0) {
			float diff = ((playerYaw - yaw) % 360 + 540) % 360 - 180;
			yaw += diff * 0.03F;
		}
		double dist = lyrics.skyDistance.getFloat();
		double elev = Math.toRadians(lyrics.skyElevation.getFloat());
		double ry = Math.toRadians(yaw);
		double dx = -Math.sin(ry) * Math.cos(elev), dz = Math.cos(ry) * Math.cos(elev), dy = Math.sin(elev);
		double cx = mc.player.getX() + dx * dist;
		double cy = mc.player.getEyeY() + dy * dist;
		double cz = mc.player.getZ() + dz * dist;

		float scale = (float) (lyrics.sizeSetting() * dist / 6.5);
		float small = scale * 0.55F;
		int mode = lyrics.animationMode();
		float ease = mode == 3 ? 1F : now.ease();
		float pop = mode == 1 ? 0.75F + 0.25F * ease : 1F;
		float slide = mode == 0 ? (1 - ease) * scale * 0.12F : 0F;
		float line = scale * 0.27F;

		int[] c = lyrics.colorsForSky();
		String cur = now.pause() ? "♪ ♪ ♪" : now.cur();
		if (mode == 2 && !now.pause()) {
			cur = cur.substring(0, Math.max(0, Math.min(cur.length(), Math.round(cur.length() * ease))));
		}
		int filled = lyrics.karaokeOn() && !now.pause() ? Math.round(cur.length() * now.progress()) : cur.length();

		int rows = Math.max(1, (int) Math.ceil(mc.font.width(cur) * (lyrics.skyBold.getBool() ? 1.1F : 1F) / LINE_WIDTH));
		place(SIGNS[CUR], cx, cy - line * 0.5 - slide, cz, scale * pop);
		place(SIGNS[PREV], cx, cy - line * 0.5 + rows * line + line * 0.25 - slide, cz, small);
		place(SIGNS[NEXT], cx, cy - line * 1.05 - small * 0.27F - slide, cz, small);

		text(SIGNS[CUR], karaoke(cur, filled, c[0], c[1], lyrics), alpha(ease), lyrics, c);
		text(SIGNS[PREV], plain(now.prev(), c[0], lyrics), alpha(0.5F * ease), lyrics, c);
		text(SIGNS[NEXT], plain(now.next(), c[0], lyrics), alpha(0.4F * ease), lyrics, c);
	}

	static byte alpha(float a) {
		return (byte) Math.max(26, Math.min(255, Math.round(a * 255)));
	}

	static void place(Display.TextDisplay e, double x, double y, double z, float scale) {
		e.snapTo(x, y, z, 0, 0);
		((DisplayEntityAccessor) (Object) e).topclient$setTransformation(
				new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
	}

	static Style style(int rgb, SpotifyLyrics lyrics) {
		Style s = Style.EMPTY.withColor(rgb & 0xFFFFFF).withBold(lyrics.skyBold.getBool());
		if (lyrics.skyFont.getMode() == 1) {
			s = s.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft", "uniform")));
		}
		return s;
	}

	static Component plain(String t, int rgb, SpotifyLyrics lyrics) {
		return Component.literal(t).setStyle(style(rgb, lyrics));
	}

	private static Component karaoke(String t, int filled, int base, int hl, SpotifyLyrics lyrics) {
		int n = Math.max(0, Math.min(t.length(), filled));
		MutableComponent out = Component.empty();
		if (n > 0) {
			out.append(Component.literal(t.substring(0, n)).setStyle(style(hl, lyrics)));
		}
		if (n < t.length()) {
			out.append(Component.literal(t.substring(n)).setStyle(style(base, lyrics)));
		}
		return out;
	}

	static void text(Display.TextDisplay e, Component t, byte opacity, SpotifyLyrics lyrics, int[] c) {
		TextDisplayAccessor a = (TextDisplayAccessor) (Object) e;
		a.topclient$setText(t);
		a.topclient$setTextOpacity(opacity);
		byte flags = 0;
		if (lyrics.shadowOn()) {
			flags |= 0x01;
		}
		if (lyrics.skyThrough.getBool()) {
			flags |= 0x02;
		}
		a.topclient$setDisplayFlags(flags);
		a.topclient$setBackground(lyrics.bandOn() ? (0x55 << 24) | (c[2] & 0xFFFFFF) : 0);
	}
}
