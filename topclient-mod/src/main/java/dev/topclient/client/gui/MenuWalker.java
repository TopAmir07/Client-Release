package dev.topclient.client.gui;

import dev.topclient.client.module.modules.client.ClickGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

public final class MenuWalker {
	private static final float SPEED = 24F;
	private static final float TURN = 0.5F;
	private static final float POSE_IN = 0.6F;
	private static final float NAME_DELAY = 0.35F;
	private static final float NAME_TIME = 1.1F;
	private static final float WALK_YAW = (float) Math.toRadians(-68);
	private static final float STAND_YAW = (float) Math.toRadians(-16);

	private static final ClickGUI.WalkerPose[] RANDOM_POSES = {
			ClickGUI.WalkerPose.Aura, ClickGUI.WalkerPose.Wave, ClickGUI.WalkerPose.Point, ClickGUI.WalkerPose.Dab,
			ClickGUI.WalkerPose.Victory, ClickGUI.WalkerPose.TPose, ClickGUI.WalkerPose.Zombie, ClickGUI.WalkerPose.Salute,
			ClickGUI.WalkerPose.Crossed, ClickGUI.WalkerPose.Dance, ClickGUI.WalkerPose.Calm};

	private float burstAt = -1F;
	private final int[] box = new int[4];
	private ClickGUI.WalkerPose randomPick = ClickGUI.WalkerPose.Aura;
	private final SkinModel.Pose pose = new SkinModel.Pose();
	private final SkinModel.Pose target = new SkinModel.Pose();

	public void reset() {
		burstAt = -1F;
		box[2] = 0;
		randomPick = RANDOM_POSES[(int) (Math.random() * RANDOM_POSES.length)];
	}

	public int[] box() {
		return box;
	}

	public void poke(float sinceOpen) {
		burstAt = sinceOpen;
	}

	private ClickGUI.WalkerPose chosenPose() {
		ClickGUI.WalkerPose p = ClickGUI.walkerPose.getMode();
		return p == ClickGUI.WalkerPose.Random ? randomPick : p;
	}

	public void render(GuiGraphicsExtractor ctx, int W, int wx, int wy, int ww, int wh, float sinceOpen) {
		box[2] = 0;
		if (!ClickGUI.walker.getValue()) {
			return;
		}
		PlayerSkin skin;
		try {
			skin = SkinHead.get();
		} catch (RuntimeException e) {
			return;
		}
		if (skin == null || skin.body() == null || skin.body().texturePath() == null) {
			return;
		}
		Identifier tex = skin.body().texturePath();
		boolean slim = skin.model() == PlayerModelType.SLIM;

		float t = sinceOpen - (float) ClickGUI.walkerDelay.getValue();
		if (t < 0) {
			return;
		}

		int right = wx + ww;
		int gap = W - right;
		float ui = Draw.ui;
		float scale = Math.min(wh * 0.42F / 32F, 9F) * (float) ClickGUI.walkerSize.getValue();
		scale = Math.min(scale, (gap - 24F) / 34F);
		scale = Math.round(scale * ui) / ui;
		if (scale * ui < 2F) {
			return;
		}

		float half = 16F * scale;
		float targetX = Math.min(right + 12F + half, W - half - 6F);
		float feetY = wy + wh - 2F;
		float startX = W + 14F * scale;
		float walkTime = Math.max(0.6F, (startX - targetX) / scale / SPEED);

		ClickGUI.WalkerPose chosen = chosenPose();
		boolean effects = ClickGUI.walkerEffects.getValue() && chosen == ClickGUI.WalkerPose.Aura;
		int accent = GuiTheme.accent();
		int accent2 = GuiTheme.accent2();
		int tint = Draw.fade(0xFFFFFFFF);

		Matrix3x2fStack m = ctx.pose();
		float x;
		float in = 0F;
		float standing = 0F;
		if (t < walkTime) {
			float k = t / walkTime;
			float eased = k < 0.8F ? k / 0.8F * 0.86F : 0.86F + (1F - (float) Math.pow(1F - (k - 0.8F) / 0.2F, 2)) * 0.14F;
			x = startX + (targetX - startX) * eased;
			walking(pose, t, 1F);
			pose.yaw = WALK_YAW;
		} else if (t < walkTime + TURN) {
			float p = smooth((t - walkTime) / TURN);
			x = targetX;
			walking(pose, t, 1F - p);
			pose.yaw = WALK_YAW + (STAND_YAW - WALK_YAW) * p;
			pose.headYaw = 0.35F * (1F - p);
		} else {
			x = targetX;
			standing = t - walkTime - TURN;
			float poseT = standing;
			if (burstAt >= 0 && sinceOpen > burstAt && sinceOpen - burstAt < POSE_IN * 2) {
				float b = sinceOpen - burstAt;
				poseT = Math.min(standing, b < POSE_IN ? POSE_IN - b : b - POSE_IN);
			}
			in = smooth(Math.min(1F, poseT / POSE_IN));
			neutral(pose, standing);
			pose.yaw = STAND_YAW;
			posed(target, chosen, standing);
			pose.blend(target, in);
		}

		if (effects && in > 0F) {
			auraBack(ctx, x, feetY, scale, pose.lift, in, standing, accent, accent2);
		}
		shadow(ctx, x, feetY, scale, 1F - Math.min(1F, pose.lift / 6F));
		m.pushMatrix();
		m.translate(x, feetY);
		m.scale(scale, scale);
		SkinModel.draw(ctx, tex, slim, pose, tint);
		m.popMatrix();
		if (effects && in > 0F) {
			auraFront(ctx, x, feetY, scale, pose.lift, in, standing, accent, accent2);
		}
		float[] b = SkinModel.lastBounds();
		box[0] = Math.round(x + b[0] * scale);
		box[1] = Math.round(feetY + b[1] * scale);
		box[2] = Math.round((b[2] - b[0]) * scale);
		box[3] = Math.round((b[3] - b[1]) * scale);

		if (t >= walkTime + TURN && ClickGUI.walkerName.getValue()) {
			nameTag(ctx, x, feetY, scale, pose.lift, standing, right, W);
		}
	}

	private static float smooth(float k) {
		k = Math.max(0F, Math.min(1F, k));
		return k * k * (3F - 2F * k);
	}

	private static float back(float k) {
		k = Math.max(0F, Math.min(1F, k));
		float c = 1.4F;
		float q = k - 1F;
		return 1F + (c + 1F) * q * q * q + c * q * q;
	}

	private static void zero(SkinModel.Pose p) {
		p.lift = 0;
		p.headYaw = p.headPitch = p.headRoll = 0;
		for (int i = 0; i < 3; i++) {
			p.rightArm[i] = p.leftArm[i] = p.rightLeg[i] = p.leftLeg[i] = 0;
		}
	}

	private static void walking(SkinModel.Pose p, float t, float amount) {
		zero(p);
		float s = (float) Math.sin(t * 9F) * amount;
		p.rightLeg[0] = -0.7F * s;
		p.leftLeg[0] = 0.7F * s;
		p.rightArm[0] = 0.6F * s;
		p.leftArm[0] = -0.6F * s;
		p.rightArm[2] = -0.05F;
		p.leftArm[2] = 0.05F;
		p.lift = Math.abs((float) Math.sin(t * 9F)) * 0.6F * amount;
		p.headYaw = 0.35F;
		p.headPitch = (float) Math.sin(t * 18F) * 0.02F * amount;
	}

	private static void neutral(SkinModel.Pose p, float time) {
		zero(p);
		float breathe = (float) Math.sin(time * 2.2F);
		p.rightArm[2] = -0.05F - breathe * 0.02F;
		p.leftArm[2] = 0.05F + breathe * 0.02F;
	}

	private static void posed(SkinModel.Pose p, ClickGUI.WalkerPose pose, float time) {
		neutral(p, time);
		p.yaw = STAND_YAW;
		float s1 = (float) Math.sin(time * 2.1F);
		switch (pose) {
			case Aura -> {
				float shake = (float) Math.sin(time * 23F) * 0.012F;
				p.lift = 2.6F + s1 * 0.7F;
				p.rightArm[0] = -0.15F;
				p.rightArm[2] = -0.62F - shake - s1 * 0.04F;
				p.leftArm[0] = -0.15F;
				p.leftArm[2] = 0.62F + shake + s1 * 0.04F;
				p.rightLeg[2] = -0.08F - (float) Math.sin(time * 1.7F) * 0.03F;
				p.leftLeg[2] = 0.08F + (float) Math.sin(time * 1.7F + 1F) * 0.03F;
				p.headPitch = -0.14F;
				p.headRoll = (float) Math.sin(time * 1.3F) * 0.03F;
			}
			case Wave -> {
				float cycle = time % 6F;
				float k = cycle < 2.6F ? Math.min(1F, Math.min(cycle, 2.6F - cycle) / 0.3F) : 0F;
				p.leftArm[2] = 0.05F + (2.55F + (float) Math.sin(cycle * 8F) * 0.35F - 0.05F) * k;
				p.leftArm[0] = -0.15F * k;
				p.headRoll = -(float) Math.sin(time * 2F) * 0.06F * k;
				p.headYaw = 0.12F * k;
			}
			case Point -> {
				p.yaw = STAND_YAW - 0.25F;
				p.rightArm[2] = -1.5F;
				p.rightArm[0] = -0.12F + s1 * 0.03F;
				p.headYaw = -0.55F;
				p.headPitch = 0.05F;
			}
			case Dab -> {
				p.rightArm[0] = -1.3F;
				p.rightArm[2] = 0.55F;
				p.leftArm[2] = 2.2F;
				p.leftArm[0] = -0.2F;
				p.headPitch = 0.5F;
				p.headYaw = -0.45F;
				p.headRoll = 0.15F;
			}
			case Victory -> {
				float hop = Math.max(0F, (float) Math.sin(time * 5F));
				p.lift = hop * 1.5F;
				p.rightArm[2] = -2.6F - hop * 0.12F;
				p.leftArm[2] = 2.6F + hop * 0.12F;
				p.headPitch = -0.2F;
			}
			case TPose -> {
				p.rightArm[2] = -1.5708F;
				p.leftArm[2] = 1.5708F;
			}
			case Zombie -> {
				float sway = (float) Math.sin(time * 1.6F) * 0.08F;
				p.rightArm[0] = -1.5F + sway;
				p.leftArm[0] = -1.5F - sway;
				p.headRoll = sway;
				p.headPitch = 0.1F;
			}
			case Salute -> {
				p.rightArm[0] = -2.0F;
				p.rightArm[2] = -0.62F;
				p.headPitch = -0.05F;
			}
			case Crossed -> {
				p.rightArm[0] = -1.25F;
				p.rightArm[1] = 0.78F;
				p.leftArm[0] = -1.15F;
				p.leftArm[1] = -0.78F;
				p.headPitch = -0.12F;
				p.headYaw = 0.1F;
			}
			case Dance -> {
				float s = (float) Math.sin(time * 6F);
				float c = (float) Math.cos(time * 6F);
				p.rightArm[2] = s * 0.6F;
				p.leftArm[2] = s * 0.6F;
				p.rightArm[0] = c * 0.35F;
				p.leftArm[0] = -c * 0.35F;
				p.rightLeg[2] = -s * 0.12F;
				p.leftLeg[2] = -s * 0.12F;
				p.yaw = STAND_YAW + c * 0.18F;
				p.lift = Math.abs(c) * 0.9F;
				p.headRoll = s * 0.08F;
			}
			default -> {
			}
		}
	}

	private static void nameTag(GuiGraphicsExtractor ctx, float cx, float feetY, float scale, float lift, float standing, int windowRight, int W) {
		float k = (standing - NAME_DELAY) / NAME_TIME;
		if (k <= 0F) {
			return;
		}
		String name = SkinHead.name();
		if (name == null || name.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font tr = mc.font;
		float ui = Draw.ui;
		float font = Math.max(1F, Math.round(scale * 0.45F * ui)) / ui;
		int tw = tr.width(name);
		int boxW = tw + 2;
		float room = 2F * Math.min(cx - windowRight - 6F, W - 6F - cx);
		if (boxW * font > room) {
			font = Math.max(1F, (float) Math.floor(room / boxW * ui)) / ui;
		}

		float pos = back(k);
		float alpha = smooth(Math.min(1F, k * 2.2F));
		float endY = feetY - (32F + lift + 4F) * scale - 10F * font;
		float startY = feetY - (18F + lift) * scale;
		float y = startY + (endY - startY) * pos;
		int a = Math.round(alpha * 255);
		if (a < 6) {
			return;
		}
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(Math.round((cx - boxW * font / 2F) * ui) / ui, Math.round(y * ui) / ui);
		m.scale(font, font);
		ctx.fill(0, 0, boxW, 10, Draw.argb(Math.round(alpha * 64), 0x000000));
		ctx.text(tr, name, 1, 1, Draw.argb(a, 0xFFFFFF), false);
		m.popMatrix();
	}

	private static void shadow(GuiGraphicsExtractor ctx, float cx, float feetY, float scale, float strength) {
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(cx, feetY);
		m.scale(1F, 0.22F);
		Backdrop.blob(ctx, 0, 0, 11F * scale, Draw.argb(Math.round(Math.max(0F, strength) * 150), 0x000000));
		m.popMatrix();
	}

	private static float rnd(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	private static void auraBack(GuiGraphicsExtractor ctx, float cx, float feetY, float s, float lift, float in, float time, int a1, int a2) {
		float pulse = 0.85F + 0.15F * (float) Math.sin(time * 3.1F);
		float body = feetY - (16F + lift) * s;
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(cx, body);
		m.scale(0.75F, 1.15F);
		Backdrop.blob(ctx, 0, 0, 30F * s * pulse, Draw.argb(Math.round(in * 120), a1));
		Backdrop.blob(ctx, 0, 0, 19F * s, Draw.argb(Math.round(in * 95), a2));
		m.popMatrix();
		m.pushMatrix();
		m.translate(cx, feetY);
		m.scale(1F, 0.2F);
		Backdrop.blob(ctx, 0, 0, 20F * s * pulse, Draw.argb(Math.round(in * 170), a1));
		m.popMatrix();
		flames(ctx, cx, feetY, s, lift, in, time, a1, a2, 0);
	}

	private static void auraFront(GuiGraphicsExtractor ctx, float cx, float feetY, float s, float lift, float in, float time, int a1, int a2) {
		flames(ctx, cx, feetY, s, lift, in, time, a1, a2, 1);
		for (int i = 0; i < 10; i++) {
			float life = 1.3F + rnd(i, 7) * 1.1F;
			float p = ((time + rnd(i, 8) * life) % life) / life;
			float x = cx + (rnd(i, 9) * 2F - 1F) * 12F * s + (float) Math.sin(time * 2F + i) * s;
			float y = feetY - p * (40F + lift) * s;
			float a = (float) Math.sin(p * Math.PI) * in;
			float r = Math.max(1F, s * (0.35F + rnd(i, 10) * 0.3F));
			Backdrop.blob(ctx, x, y, r * 4F, Draw.argb(Math.round(a * 140), Draw.mix(a1, 0xFFFFFF, 0.35F)));
			Draw.circle(ctx, x, y, r, Draw.argb(Math.round(a * 235), Draw.mix(a1, 0xFFFFFF, 0.75F)));
		}
	}

	private static void flames(GuiGraphicsExtractor ctx, float cx, float feetY, float s, float lift, float in, float time, int a1, int a2, int layer) {
		Matrix3x2fStack m = ctx.pose();
		int n = layer == 0 ? 14 : 8;
		for (int i = 0; i < n; i++) {
			int id = i + layer * 50;
			float life = 0.9F + rnd(id, 1) * 0.8F;
			float p = ((time + rnd(id, 2) * life) % life) / life;
			float side = rnd(id, 3) * 2F - 1F;
			float x = cx + side * (layer == 0 ? 10F : 7F) * s * (1F - p * 0.35F);
			float y = feetY - (2F + p * (30F + lift)) * s;
			float r = (layer == 0 ? 5F : 3.2F) * s * (1F - p * 0.6F);
			float a = (float) Math.sin(p * Math.PI) * in * (layer == 0 ? 0.75F : 0.45F);
			int c = Draw.mix(a1, a2, rnd(id, 4));
			m.pushMatrix();
			m.translate(x, y);
			m.scale(0.6F, 1.7F);
			Backdrop.blob(ctx, 0, 0, r, Draw.argb(Math.round(a * 255), c));
			m.popMatrix();
		}
	}
}
