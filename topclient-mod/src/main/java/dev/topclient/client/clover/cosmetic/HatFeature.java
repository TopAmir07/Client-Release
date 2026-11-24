package dev.topclient.client.clover.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.resources.Identifier;

/** Draws your hat, headband, wings, shoulder buddy and aura (your own player only; third person, inventory, etc.). */
public class HatFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
	public HatFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
		super(context);
	}

	@Override
	public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || state.isInvisible) {
			return;
		}
		// You: what you picked. Other TopClient players: what their skin's invisible tag says.
		boolean own = state.id == mc.player.getId();
		if (!own && state.distanceToCameraSq > 64 * 64) {
			return; // performance: other players' extras only within 64 blocks
		}
		java.util.Map<String, String> tag = own ? null : Cosmetics.tagOf(state.skin);
		if (!own && tag.isEmpty()) {
			return;
		}
		SkinPick sp = skinOf(state.skin);
		for (String[] slot : SLOTS) {
			Hats.Model m = own ? Hats.current(slot[0]) : Hats.byId(tag.get(slot[1]));
			if (m != null) {
				draw(m, m.body() ? getParentModel().body : getParentModel().head, sp, matrices, queue, light);
			}
		}
	}

	private static final String[][] SLOTS = {{"cos.hat", "h"}, {"cos.band", "b"}, {"cos.wings", "w"}, {"cos.buddy", "y"}, {"cos.aura", "a"}};

	private static void draw(Hats.Model m, ModelPart attach, SkinPick sp, PoseStack matrices, SubmitNodeCollector queue, int light) {
		if (m == null) {
			return;
		}
		Identifier tex = m.texture();
		if (m.skin()) {
			if (sp == null) {
				return;
			}
			tex = sp.tex();
			if (sp.slim()) {
				m = Hats.slimVariant(m);
			}
		}
		final Hats.Model mm = m;
		RenderType layer = m.translucent() ? RenderTypes.entityTranslucent(tex) : RenderTypes.entityCutout(tex);
		float dv = m.frameOffset();
		final int lt = m.glow() ? 0xF000F0 : light;
		matrices.pushPose();
		attach.translateAndRotate(matrices);
		double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
		Hats.Root r = m.root();
		if (r != null) {
			// pets: float, hop and sway as one piece (their own parts still move on top)
			float lift = (float) (r.bob() * Math.sin(t / r.bobp() * Math.PI * 2) + r.hop() * Math.abs(Math.sin(t / r.hopp() * Math.PI)));
			matrices.translate(r.px() / 16F, (r.py() - lift) / 16F, r.pz() / 16F);
			for (float[] q : r.rots()) {
				float a = (float) (q[1] * Math.sin((t / q[2] + q[3]) * Math.PI * 2));
				matrices.mulPose((q[0] == 0 ? Axis.XP : q[0] == 2 ? Axis.ZP : Axis.YP).rotationDegrees(a));
			}
			matrices.translate(-r.px() / 16F, -r.py() / 16F, -r.pz() / 16F);
		}
		if (m.quads().length > 0) {
			float[][] qs = m.quads();
			queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(qs, mm, dv, entry, vc, lt));
		}
		for (Hats.Part p : m.parts()) {
			// spin: full turns (orbiting auras); otherwise a back-and-forth swing. bob: up and down.
			float angle = p.spin() != 0F ? (float) (360.0 * ((t / p.spin() + p.phase()) % 1.0))
					: (float) (p.amp() * Math.sin((t / p.period() + p.phase()) * Math.PI * 2));
			float lift = p.bob() == 0F ? 0F : (float) (p.bob() * Math.sin((t / p.bobp() + p.phase()) * Math.PI * 2));
			matrices.pushPose();
			matrices.translate(p.px() / 16F, (p.py() - lift) / 16F, p.pz() / 16F);
			Axis ax = p.axis() == 'x' ? Axis.XP : p.axis() == 'z' ? Axis.ZP : Axis.YP;
			matrices.mulPose(ax.rotationDegrees(angle));
			matrices.translate(-p.px() / 16F, -p.py() / 16F, -p.pz() / 16F);
			float[][] qs = p.quads();
			queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(qs, mm, dv, entry, vc, lt));
			matrices.popPose();
		}
		// animated bones (Cubees): their real idle animation, blended between frames
		if (m.bones().length > 0) {
			double fr = t * m.animFps();
			for (Hats.Bone b : m.bones()) {
				int n = b.mats().length;
				double ff = fr % n;
				int i0 = (int) ff;
				float k = (float) (ff - i0);
				float[] a = b.mats()[i0], c = b.mats()[(i0 + 1) % n];
				float[] x = new float[12];
				for (int j = 0; j < 12; j++) {
					x[j] = a[j] + (c[j] - a[j]) * k;
				}
				matrices.pushPose();
				matrices.mulPose(new org.joml.Matrix4f(x[0], x[4], x[8], 0, x[1], x[5], x[9], 0, x[2], x[6], x[10], 0, x[3] / 16F, x[7] / 16F, x[11] / 16F, 1));
				float[][] bq = b.quads();
				queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(bq, mm, dv, entry, vc, lt));
				matrices.popPose();
			}
		}
		matrices.popPose();
	}


	private record SkinPick(Identifier tex, boolean slim) {
	}

	/** The player's skin as the game already has it (no download), for Mini-Me style items. */
	private static SkinPick skinOf(Object skinObj) {
		try {
			var skin = (net.minecraft.world.entity.player.PlayerSkin) skinObj;
			if (skin == null || skin.body() == null || skin.body().texturePath() == null) {
				return null;
			}
			return new SkinPick(skin.body().texturePath(), String.valueOf(skin.model()).toUpperCase(java.util.Locale.ROOT).contains("SLIM"));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static void emit(float[][] quads, Hats.Model m, float dv, PoseStack.Pose entry, VertexConsumer vc, int light) {
		float tw = m.tw(), th = m.th();
		for (float[] q : quads) {
			float nx = q[20], ny = q[21], nz = q[22];
			for (int i = 0; i < 4; i++) {
				vc.addVertex(entry, q[i * 3] / 16F, q[i * 3 + 1] / 16F, q[i * 3 + 2] / 16F)
						.setColor(-1)
						.setUv(q[12 + i * 2] / tw, (q[13 + i * 2] + dv) / th)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(entry, nx, ny, nz);
			}
		}
	}
}
