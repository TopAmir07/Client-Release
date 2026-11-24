package dev.topclient.client.gui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;

public final class SkinModel {
	public static final class Pose {
		public float yaw;
		public float lift;
		public float headYaw, headPitch, headRoll;
		public final float[] rightArm = new float[3];
		public final float[] leftArm = new float[3];
		public final float[] rightLeg = new float[3];
		public final float[] leftLeg = new float[3];

		public Pose set(Pose o) {
			yaw = o.yaw;
			lift = o.lift;
			headYaw = o.headYaw;
			headPitch = o.headPitch;
			headRoll = o.headRoll;
			System.arraycopy(o.rightArm, 0, rightArm, 0, 3);
			System.arraycopy(o.leftArm, 0, leftArm, 0, 3);
			System.arraycopy(o.rightLeg, 0, rightLeg, 0, 3);
			System.arraycopy(o.leftLeg, 0, leftLeg, 0, 3);
			return this;
		}

		public Pose blend(Pose o, float k) {
			yaw += (o.yaw - yaw) * k;
			lift += (o.lift - lift) * k;
			headYaw += (o.headYaw - headYaw) * k;
			headPitch += (o.headPitch - headPitch) * k;
			headRoll += (o.headRoll - headRoll) * k;
			for (int i = 0; i < 3; i++) {
				rightArm[i] += (o.rightArm[i] - rightArm[i]) * k;
				leftArm[i] += (o.leftArm[i] - leftArm[i]) * k;
				rightLeg[i] += (o.rightLeg[i] - rightLeg[i]) * k;
				leftLeg[i] += (o.leftLeg[i] - leftLeg[i]) * k;
			}
			return this;
		}
	}

	private static final float VIEW_PITCH = (float) Math.toRadians(10);
	private static final float LX = 0.36F, LY = 0.80F, LZ = 0.48F;
	private static final float L_LEN = (float) Math.sqrt(LX * LX + LY * LY + LZ * LZ);

	private SkinModel() {
	}

	private static final class Part {
		float w, h, d, px, py, pz, cx, cy, cz;
		float[] rot;
		int u, v, ou, ov;
		float grow;
		float depth;
		boolean head;
	}

	private static final Part[] PARTS = new Part[6];
	private static final Part[] ORDER = new Part[6];

	static {
		for (int i = 0; i < 6; i++) {
			PARTS[i] = new Part();
		}
	}

	private static float r00, r01, r02, r10, r11, r12, r20, r21, r22;
	private static float b00, b01, b02, b10, b11, b12, b20, b21, b22;
	private static float lift;
	private static float minX, minY, maxX, maxY;

	public static float[] lastBounds() {
		return new float[]{minX, minY, maxX, maxY};
	}

	private static void setPart(Part p, float w, float h, float d, float px, float py, float pz, float cx, float cy, float cz,
								float[] rot, int u, int v, int ou, int ov, float grow, boolean head) {
		p.w = w;
		p.h = h;
		p.d = d;
		p.px = px;
		p.py = py;
		p.pz = pz;
		p.cx = cx;
		p.cy = cy;
		p.cz = cz;
		p.rot = rot;
		p.u = u;
		p.v = v;
		p.ou = ou;
		p.ov = ov;
		p.grow = grow;
		p.head = head;
	}

	private static void limbMatrix(float a, float b, float c) {
		float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
		float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
		float cc = (float) Math.cos(c), sc = (float) Math.sin(c);
		float x00 = 1, x01 = 0, x02 = 0, x10 = 0, x11 = ca, x12 = -sa, x20 = 0, x21 = sa, x22 = ca;
		float y00 = cb * x00 + sb * x20, y01 = cb * x01 + sb * x21, y02 = cb * x02 + sb * x22;
		float y10 = x10, y11 = x11, y12 = x12;
		float y20 = -sb * x00 + cb * x20, y21 = -sb * x01 + cb * x21, y22 = -sb * x02 + cb * x22;
		r00 = cc * y00 - sc * y10;
		r01 = cc * y01 - sc * y11;
		r02 = cc * y02 - sc * y12;
		r10 = sc * y00 + cc * y10;
		r11 = sc * y01 + cc * y11;
		r12 = sc * y02 + cc * y12;
		r20 = y20;
		r21 = y21;
		r22 = y22;
	}

	private static void bodyMatrix(float yaw) {
		float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
		float cp = (float) Math.cos(VIEW_PITCH), sp = (float) Math.sin(VIEW_PITCH);
		float y00 = cy, y01 = 0, y02 = sy, y10 = 0, y11 = 1, y12 = 0, y20 = -sy, y21 = 0, y22 = cy;
		b00 = y00;
		b01 = y01;
		b02 = y02;
		b10 = cp * y10 - sp * y20;
		b11 = cp * y11 - sp * y21;
		b12 = cp * y12 - sp * y22;
		b20 = sp * y10 + cp * y20;
		b21 = sp * y11 + cp * y21;
		b22 = sp * y12 + cp * y22;
	}

	private static void point(Part p, float x, float y, float z, float[] out) {
		x += p.cx;
		y += p.cy;
		z += p.cz;
		float jx = r00 * x + r01 * y + r02 * z + p.px;
		float jy = r10 * x + r11 * y + r12 * z + p.py + lift;
		float jz = r20 * x + r21 * y + r22 * z + p.pz;
		out[0] = b00 * jx + b01 * jy + b02 * jz;
		out[1] = b10 * jx + b11 * jy + b12 * jz;
		out[2] = b20 * jx + b21 * jy + b22 * jz;
	}

	private static void dir(float x, float y, float z, float[] out) {
		float jx = r00 * x + r01 * y + r02 * z;
		float jy = r10 * x + r11 * y + r12 * z;
		float jz = r20 * x + r21 * y + r22 * z;
		out[0] = b00 * jx + b01 * jy + b02 * jz;
		out[1] = b10 * jx + b11 * jy + b12 * jz;
		out[2] = b20 * jx + b21 * jy + b22 * jz;
	}

	public static void draw(GuiGraphicsExtractor ctx, Identifier tex, boolean slim, Pose pose, int argb) {
		float aw = slim ? 3F : 4F;
		float ax = slim ? 0.5F : 1F;
		setPart(PARTS[0], 4, 12, 4, -1.9F, 12, 0, 0, -6, 0, pose.rightLeg, 0, 16, 0, 32, 0.25F, false);
		setPart(PARTS[1], 4, 12, 4, 1.9F, 12, 0, 0, -6, 0, pose.leftLeg, 16, 48, 0, 48, 0.25F, false);
		setPart(PARTS[2], 8, 12, 4, 0, 24, 0, 0, -6, 0, null, 16, 16, 16, 32, 0.25F, false);
		setPart(PARTS[3], aw, 12, 4, -5, 22, 0, -ax, -4, 0, pose.rightArm, 40, 16, 40, 32, 0.25F, false);
		setPart(PARTS[4], aw, 12, 4, 5, 22, 0, ax, -4, 0, pose.leftArm, 32, 48, 48, 48, 0.25F, false);
		float[] headRot = {pose.headPitch, pose.headYaw, pose.headRoll};
		setPart(PARTS[5], 8, 8, 8, 0, 24, 0, 0, 4, 0, headRot, 0, 0, 32, 0, 0.5F, true);

		lift = pose.lift;
		bodyMatrix(pose.yaw);
		float[] c = new float[3];
		for (int i = 0; i < 6; i++) {
			Part p = PARTS[i];
			float[] r = p.rot;
			if (r == null) {
				limbMatrix(0, 0, 0);
			} else {
				limbMatrix(r[0], r[1], r[2]);
			}
			point(p, 0, 0, 0, c);
			p.depth = c[2];
			ORDER[i] = p;
		}
		Arrays.sort(ORDER, (a, b) -> Float.compare(a.depth, b.depth));

		minX = minY = Float.MAX_VALUE;
		maxX = maxY = -Float.MAX_VALUE;
		Matrix3x2fStack m = ctx.pose();
		for (Part p : ORDER) {
			float[] r = p.rot;
			if (r == null) {
				limbMatrix(0, 0, 0);
			} else {
				limbMatrix(r[0], r[1], r[2]);
			}
			drawBox(ctx, m, tex, p, 0F, p.u, p.v, argb);
			drawBox(ctx, m, tex, p, p.grow, p.ou, p.ov, argb);
		}
	}

	private static final float[] A = new float[3], B = new float[3], C = new float[3], N = new float[3];

	private static void drawBox(GuiGraphicsExtractor ctx, Matrix3x2fStack m, Identifier tex, Part p, float grow, int u, int v, int argb) {
		float hw = p.w / 2F + grow, hh = p.h / 2F + grow, hd = p.d / 2F + grow;
		int w = Math.round(p.w), h = Math.round(p.h), d = Math.round(p.d);
		side(ctx, m, tex, p, -hw, hh, hd, hw, hh, hd, -hw, -hh, hd, 0, 0, 1, u + d, v + d, w, h, argb);
		side(ctx, m, tex, p, hw, hh, -hd, -hw, hh, -hd, hw, -hh, -hd, 0, 0, -1, u + 2 * d + w, v + d, w, h, argb);
		side(ctx, m, tex, p, -hw, hh, -hd, -hw, hh, hd, -hw, -hh, -hd, -1, 0, 0, u, v + d, d, h, argb);
		side(ctx, m, tex, p, hw, hh, hd, hw, hh, -hd, hw, -hh, hd, 1, 0, 0, u + d + w, v + d, d, h, argb);
		side(ctx, m, tex, p, -hw, hh, -hd, hw, hh, -hd, -hw, hh, hd, 0, 1, 0, u + d, v, w, d, argb);
		side(ctx, m, tex, p, -hw, -hh, hd, hw, -hh, hd, -hw, -hh, -hd, 0, -1, 0, u + d + w, v, w, d, argb);
	}

	private static void side(GuiGraphicsExtractor ctx, Matrix3x2fStack m, Identifier tex, Part p,
							 float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
							 float nx, float ny, float nz, int u, int v, int fw, int fh, int argb) {
		dir(nx, ny, nz, N);
		if (N[2] <= 0.0005F) {
			return;
		}
		point(p, ax, ay, az, A);
		point(p, bx, by, bz, B);
		point(p, cx, cy, cz, C);
		float sax = A[0], say = -A[1];
		float sbx = B[0], sby = -B[1];
		float scx = C[0], scy = -C[1];
		track(sax, say);
		track(sbx, sby);
		track(scx, scy);
		track(sbx + scx - sax, sby + scy - say);

		float light = (N[0] * LX + N[1] * LY + N[2] * LZ) / L_LEN;
		float k = 0.6F + 0.4F * Math.max(0F, light);
		int a = (argb >>> 24) & 0xFF;
		int rr = Math.round(((argb >> 16) & 0xFF) * k);
		int gg = Math.round(((argb >> 8) & 0xFF) * k);
		int bb = Math.round((argb & 0xFF) * k);
		int color = (a << 24) | (rr << 16) | (gg << 8) | bb;

		m.pushMatrix();
		m.mul(new Matrix3x2f((sbx - sax) / fw, (sby - say) / fw, (scx - sax) / fh, (scy - say) / fh, sax, say));
		ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, u, v, fw, fh, 64, 64, color);
		m.popMatrix();
	}

	private static void track(float x, float y) {
		minX = Math.min(minX, x);
		maxX = Math.max(maxX, x);
		minY = Math.min(minY, y);
		maxY = Math.max(maxY, y);
	}
}
