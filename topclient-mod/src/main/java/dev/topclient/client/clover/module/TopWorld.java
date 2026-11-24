package dev.topclient.client.clover.module;

import com.mojang.blaze3d.platform.NativeImage;
import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.top.Waypoints;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/** Waypoints (markers in the world) and the minimap. */
public final class TopWorld {
	private TopWorld() {
	}

	/**
	 * Shows your waypoints in the world as a label with the distance and a beam, drawn on the HUD.
	 * Add and edit them in the WAYPOINTS tab of the mod menu.
	 */
	public static final class WaypointsMod extends Module {
		static WaypointsMod instance;
		private final Setting beam = bool("beam", "Beam", true);
		private final Setting dist = bool("distance", "Show distance", true);
		private final Setting maxDist = number("max", "Hide beyond (blocks, 0 = never)", 0, 10000, 100, 0);
		private final Setting allDims = bool("allDims", "Show waypoints from other dimensions", false);

		public WaypointsMod() {
			super("waypoints", "Waypoints", "Marks places you save in the WAYPOINTS tab with a label, distance and beam. Some servers don't allow waypoints: check their rules.", Category.VISUAL);
			instance = this;
		}

		@Override
		protected boolean defaultEnabled() {
			return true;
		}
	}

	/** Called by the HUD every frame before the HUD mods. */
	static void drawWaypoints(GuiGraphicsExtractor ctx, Minecraft mc) {
		WaypointsMod w = WaypointsMod.instance;
		if (w == null || !w.enabled() || mc.level == null || mc.player == null) {
			return;
		}
		var list = Waypoints.list();
		if (list.isEmpty()) {
			return;
		}
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 eye = cam.position();
		double yaw = Math.toRadians(cam.yRot()), pitch = Math.toRadians(cam.xRot());
		// camera axes (Minecraft: yaw 0 looks south, +Z; east is on the left)
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // up = right x forward (right.y = 0)
		double tan = Math.tan(Math.toRadians(cam.getFov()) / 2);
		int W = ctx.guiWidth(), H = ctx.guiHeight();
		String dim = Waypoints.dimension();
		float max = w.maxDist.getFloat();
		for (Waypoints.Waypoint p : list) {
			if (!p.visible || (!w.allDims.getBool() && p.dim != null && !p.dim.equals(dim))) {
				continue;
			}
			double d = Waypoints.distance(p);
			if (max > 0 && d > max) {
				continue;
			}
			double[] base = project(p.x + 0.5 - eye.x, p.y - eye.y, p.z + 0.5 - eye.z, fx, fy, fz, rx, rz, ux, uy, uz, tan, W, H);
			if (base == null) {
				continue;
			}
			int col = 0xFF000000 | p.color;
			if (w.beam.getBool()) {
				double[] top = project(p.x + 0.5 - eye.x, p.y + 80 - eye.y, p.z + 0.5 - eye.z, fx, fy, fz, rx, rz, ux, uy, uz, tan, W, H);
				int bx = (int) Math.round(base[0]);
				int y0 = (int) Math.round(Math.min(base[1], top == null ? 0 : top[1])), y1 = (int) Math.round(base[1]);
				ctx.fill(bx - 1, Math.max(0, y0), bx + 1, y1, (0x66 << 24) | p.color);
			}
			String label = p.name + (w.dist.getBool() ? "  " + Math.round(d) + "m" : "");
			int tw = Gfx.font().width(label);
			int lx = (int) Math.round(base[0]) - tw / 2, ly = (int) Math.round(base[1]) - 22;
			lx = Math.max(2, Math.min(W - tw - 2, lx));
			ly = Math.max(2, Math.min(H - 12, ly));
			ctx.fill(lx - 3, ly - 2, lx + tw + 3, ly + 10, 0x99000000);
			ctx.fill(lx - 3, ly + 10, lx + tw + 3, ly + 11, col);
			ctx.text(Gfx.font(), label, lx, ly, 0xFFFFFFFF, false);
			int px = (int) Math.round(base[0]), py = (int) Math.round(base[1]);
			ctx.fill(px - 2, py - 2, px + 2, py + 2, col);
		}
	}

	/** World offset from the eye to GUI coordinates, or null when behind the camera. */
	private static double[] project(double dx, double dy, double dz, double fx, double fy, double fz, double rx, double rz,
									double ux, double uy, double uz, double tan, int W, int H) {
		double z = dx * fx + dy * fy + dz * fz;
		if (z < 0.1) {
			return null;
		}
		double x = dx * rx + dz * rz;
		double y = dx * ux + dy * uy + dz * uz;
		double k = (H / 2.0) / (tan * z);
		return new double[]{W / 2.0 + x * k, H / 2.0 - y * k};
	}

	/** Minimap: a top-down map around you, north up, with your direction and your waypoints. */
	public static final class Minimap extends HudModule {
		private final Setting size = number("size", "Size", 48, 160, 8, 96);
		private final Setting zoom = mode("zoom", "Blocks across", 1, "32", "64", "128");
		private final Setting showWaypoints = bool("wps", "Show waypoints", true);
		private static final int TEX = 128;
		private DynamicTexture tex;
		private Identifier id;
		private long lastUpdate;
		private int cx, cz;

		public Minimap() {
			super("minimap", "Minimap", "A small top-down map around you, north up, with your waypoints. Some servers don't allow minimaps: check their rules.", 72, 1);
		}

		@Override
		public boolean available(Minecraft mc) {
			return mc.level != null && mc.player != null;
		}

		@Override
		public int width(Minecraft mc) {
			return size.getInt();
		}

		@Override
		public int height(Minecraft mc) {
			return size.getInt();
		}

		private int span() {
			return switch (zoom.getMode()) {
				case 0 -> 32;
				case 2 -> 128;
				default -> 64;
			};
		}

		/** Re-reads the blocks under the map twice a second (one column per pixel, from the heightmap). */
		private void update(Minecraft mc) {
			if (tex == null) {
				tex = new DynamicTexture(() -> "topclient minimap", TEX, TEX, false);
				id = Identifier.fromNamespaceAndPath("topclient", "minimap");
				mc.getTextureManager().register(id, tex);
			}
			long now = System.currentTimeMillis();
			int pxb = mc.player.getBlockX(), pzb = mc.player.getBlockZ();
			if (now - lastUpdate < 500 && Math.abs(pxb - cx) < 2 && Math.abs(pzb - cz) < 2) {
				return;
			}
			lastUpdate = now;
			cx = pxb;
			cz = pzb;
			NativeImage img = tex.getPixels();
			if (img == null) {
				return;
			}
			int span = span();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int ty = 0; ty < TEX; ty++) {
				int wz = cz - span / 2 + ty * span / TEX;
				for (int tx = 0; tx < TEX; tx++) {
					int wx = cx - span / 2 + tx * span / TEX;
					int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
					pos.set(wx, h, wz);
					BlockState st = mc.level.getBlockState(pos);
					MapColor mcol = st.getMapColor(mc.level, pos);
					int argb;
					if (mcol == MapColor.NONE) {
						argb = 0xFF101014;
					} else {
						int northH = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) - 1;
						MapColor.Brightness b = h > northH ? MapColor.Brightness.HIGH : h < northH ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
						argb = mcol.calculateARGBColor(b) | 0xFF000000;
					}
					img.setPixel(tx, ty, argb);
				}
			}
			tex.upload();
		}

		@Override
		protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
			int s = size.getInt();
			if (mc.player == null || mc.level == null) {
				ctx.fill(0, 0, s, s, 0xFF101014);
				ctx.text(Gfx.font(), "Map", s / 2 - 8, s / 2 - 4, 0xFFFFFFFF, true);
				return;
			}
			update(mc);
			ctx.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0F, 0F, s, s, TEX, TEX, TEX, TEX);
			int col = 0xFF000000 | dev.topclient.client.top.TopTheme.ACCENT;
			ctx.fill(0, 0, s, 1, col);
			ctx.fill(0, s - 1, s, s, col);
			ctx.fill(0, 0, 1, s, col);
			ctx.fill(s - 1, 0, s, s, col);
			ctx.text(Gfx.font(), "N", s / 2 - 2, 2, 0xFFFFFFFF, true);
			// waypoints
			float scale = s / (float) span();
			if (showWaypoints.getBool()) {
				String dim = Waypoints.dimension();
				for (Waypoints.Waypoint w : Waypoints.list()) {
					if (!w.visible || (w.dim != null && !w.dim.equals(dim))) {
						continue;
					}
					float mx = s / 2F + (w.x - cx) * scale, my = s / 2F + (w.z - cz) * scale;
					mx = Math.max(2, Math.min(s - 3, mx));
					my = Math.max(2, Math.min(s - 3, my));
					ctx.fill(Math.round(mx) - 2, Math.round(my) - 2, Math.round(mx) + 2, Math.round(my) + 2, 0xFF000000 | w.color);
				}
			}
			// you: a small arrow pointing where you look
			double yaw = Math.toRadians(mc.player.getYRot());
			float c = s / 2F;
			for (int i = 0; i < 6; i++) {
				float px = c - (float) Math.sin(yaw) * i, py = c + (float) Math.cos(yaw) * i;
				ctx.fill(Math.round(px) - 1, Math.round(py) - 1, Math.round(px) + 1, Math.round(py) + 1, 0xFFFFFFFF);
			}
			ctx.fill(Math.round(c) - 2, Math.round(c) - 2, Math.round(c) + 2, Math.round(c) + 2, col);
		}
	}
}
