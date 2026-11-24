package dev.topclient.client.utils;
import dev.topclient.client.module.modules.client.ClickGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.function.Consumer;

import static dev.topclient.client.TopClient.mc;

public final class RenderUtils {
	public static boolean rendering3D = true;
	private static int projectionDepth;


	public static float tickProgress() {
		return mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
	}

	public static float frameDelta() {
		return mc.getDeltaTracker().getGameTimeDeltaTicks();
	}

	public static double deltaTime() {
		return mc.getFps() > 0 ? (1.0000 / mc.getFps()) : 1;
	}

	public static float fast(float end, float start, float multiple) {
		return (1 - Mth.clamp((float) (deltaTime() * multiple), 0, 1)) * end + Mth.clamp((float) (deltaTime() * multiple), 0, 1) * start;
	}


	public static void unscaledProjection(GuiGraphicsExtractor context) {
		if (projectionDepth++ == 0) {
			float scaleFactor = (float) Minecraft.getInstance().getWindow().getGuiScale();
			context.pose().pushMatrix();
			context.pose().scale(1.0F / scaleFactor, 1.0F / scaleFactor);
		}
		rendering3D = false;
	}

	public static void scaledProjection(GuiGraphicsExtractor context) {
		if (projectionDepth == 0) {
			rendering3D = true;
			return;
		}

		if (--projectionDepth == 0) {
			context.pose().popMatrix();
		}
		rendering3D = true;
	}





	public static void renderRoundedQuad(GuiGraphicsExtractor context, Color color, double x, double y, double x2, double y2, double corner1, double corner2, double corner3, double corner4, double samples) {
		int left = (int) Math.floor(Math.min(x, x2));
		int top = (int) Math.floor(Math.min(y, y2));
		int right = (int) Math.ceil(Math.max(x, x2));
		int bottom = (int) Math.ceil(Math.max(y, y2));

		int width = right - left;
		int height = bottom - top;
		if (width <= 0 || height <= 0) {
			return;
		}

		int radiusTopLeft = clampRadius(corner1, width, height);
		int radiusTopRight = clampRadius(corner2, width, height);
		int radiusBottomLeft = clampRadius(corner3, width, height);
		int radiusBottomRight = clampRadius(corner4, width, height);
		int packedColor = color.getRGB();

		for (int row = 0; row < height; row++) {
			int leftInset = getLeftInsetForRow(row, height, radiusTopLeft, radiusBottomLeft);
			int rightInset = getRightInsetForRow(row, height, radiusTopRight, radiusBottomRight);
			fillRow(context, left + leftInset, right - rightInset, top + row, packedColor);
		}
	}

	public static void renderRoundedQuad(GuiGraphicsExtractor context, Color color, double x, double y, double x1, double y1, double rad, double samples) {
		renderRoundedQuad(context, color, x, y, x1, y1, rad, rad, rad, rad, samples);
	}









	private static int clampRadius(double radius, int width, int height) {
		return Math.max(0, Math.min((int) Math.round(radius), Math.min(width, height) / 2));
	}

	private static int getLeftInsetForRow(int row, int height, int radiusTop, int radiusBottom) {
		int inset = 0;
		if (radiusTop > 0 && row < radiusTop) {
			inset = Math.max(inset, getCornerInset(radiusTop, row));
		}
		if (radiusBottom > 0 && row >= height - radiusBottom) {
			inset = Math.max(inset, getCornerInset(radiusBottom, height - 1 - row));
		}
		return inset;
	}

	private static int getRightInsetForRow(int row, int height, int radiusTop, int radiusBottom) {
		int inset = 0;
		if (radiusTop > 0 && row < radiusTop) {
			inset = Math.max(inset, getCornerInset(radiusTop, row));
		}
		if (radiusBottom > 0 && row >= height - radiusBottom) {
			inset = Math.max(inset, getCornerInset(radiusBottom, height - 1 - row));
		}
		return inset;
	}

	private static int getCornerInset(int radius, int offsetFromEdge) {
		double distance = radius - (offsetFromEdge + 0.5D);
		double inside = (radius * radius) - (distance * distance);
		if (inside <= 0.0D) {
			return radius;
		}

		return Math.max(0, (int) Math.ceil(radius - Math.sqrt(inside)));
	}

	private static void fillRow(GuiGraphicsExtractor context, int left, int right, int y, int color) {
		if (right <= left) {
			return;
		}

		context.fill(left, y, right, y + 1, color);
	}



}
