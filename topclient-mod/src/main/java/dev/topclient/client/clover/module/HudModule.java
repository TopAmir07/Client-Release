package dev.topclient.client.clover.module;

import dev.topclient.client.clover.Config;
import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class HudModule extends Module {
	public final Setting scale;
	public final Setting textColor;
	public final Setting chroma;
	public final Setting chromaSpeed;
	public final Setting shadow;
	public final Setting outline;
	public final Setting textAlpha;
	public final Setting align;
	public final Setting background;
	public final Setting backgroundColor;
	public final Setting backgroundAlpha;
	public final Setting rounded;
	public final Setting padding;
	public final Setting border;
	public final Setting borderColor;

	protected HudModule(String id, String name, String description, int defX, int defY) {
		super(id, name, description, Category.HUD);
		this.defX = defX / 100F;
		this.defY = defY / 100F;
		look(header("Size"));
		this.scale = look(number("scale", "Scale", 0.5F, 2.5F, 0.1F, 1.0F));
		look(header("Text"));
		this.textColor = look(color("text", "Text colour", 0xFFFFFF));
		this.chroma = look(bool("chroma", "Chroma (rainbow) text", false));
		this.chromaSpeed = look(number("chromaSpeed", "Chroma speed", 0.2F, 3F, 0.1F, 1F));
		this.textAlpha = look(number("textAlpha", "Text opacity", 0.2F, 1F, 0.05F, 1F));
		this.shadow = look(bool("shadow", "Text shadow", true));
		this.outline = look(bool("outline", "Text outline (easier to read on bright spots)", false));
		this.align = look(mode("align", "Text alignment", 0, "Left", "Center", "Right"));
		look(header("Background"));
		this.background = look(bool("bg", "Background", false)); // clear by default, like Lunar
		this.backgroundColor = look(color("bgColor", "Background colour", 0x000000));
		this.backgroundAlpha = look(number("bgAlpha", "Background opacity", 0F, 1F, 0.05F, 0F));
		this.rounded = look(mode("round", "Corners", 1, "Square", "Soft", "Round"));
		this.padding = look(number("pad", "Padding", 0, 8, 1, 3));
		look(header("Border"));
		this.border = look(bool("border", "Border", false));
		this.borderColor = look(color("borderColor", "Border colour", Theme.CLOVER_GREEN));
	}

	private static Setting look(Setting s) {
		s.look = true;
		return s;
	}

	private final float defX;
	private final float defY;

	private final String keyX = "mod." + id + ".x";
	private final String keyY = "mod." + id + ".y";
	private int posVer = -1;
	private float posXVal;
	private float posYVal;

	private void refreshPos() {
		if (posVer != Config.version()) {
			posXVal = Config.getFloat(keyX, defX);
			posYVal = Config.getFloat(keyY, defY);
			posVer = Config.version();
		}
	}

	public float posX() {
		refreshPos();
		return posXVal;
	}

	public float posY() {
		refreshPos();
		return posYVal;
	}

	public void setPos(float x, float y) {
		Config.setFloat(keyX, Math.max(0F, Math.min(1F, x)));
		Config.setFloat(keyY, Math.max(0F, Math.min(1F, y)));
	}

	public void resetPos() {
		Config.set(keyX, null);
		Config.set(keyY, null);
	}

	public abstract int width(Minecraft mc);

	public abstract int height(Minecraft mc);

	protected abstract void draw(GuiGraphicsExtractor ctx, Minecraft mc);

	public boolean available(Minecraft mc) {
		return mc.player != null && mc.level != null;
	}

	protected int pad() {
		return padding.getInt();
	}

	public int screenW(GuiGraphicsExtractor ctx, Minecraft mc) {
		return Math.round((width(mc) + pad() * 2) * scale.getFloat());
	}

	public int screenH(GuiGraphicsExtractor ctx, Minecraft mc) {
		return Math.round((height(mc) + pad() * 2) * scale.getFloat());
	}

	public int screenX(int sw, int boxW) {
		return Math.round(posX() * Math.max(0, sw - boxW));
	}

	public int screenY(int sh, int boxH) {
		return Math.round(posY() * Math.max(0, sh - boxH));
	}

	public void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
		if (!preview && !available(mc)) {
			return;
		}
		int bw = screenW(ctx, mc);
		int bh = screenH(ctx, mc);
		int x = screenX(ctx.guiWidth(), bw);
		int y = screenY(ctx.guiHeight(), bh);
		int pad = pad();
		int w = width(mc) + pad * 2, h = height(mc) + pad * 2;
		int r = switch (rounded.getMode()) {
			case 0 -> 0;
			case 1 -> 2;
			default -> 4;
		};
		var m = ctx.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(scale.getFloat(), scale.getFloat());
		if (background.getBool() && backgroundAlpha.getFloat() > 0F) {
			int a = Math.round(backgroundAlpha.getFloat() * 255);
			Gfx.rect(ctx, 0, 0, w, h, r, (a << 24) | backgroundColor.getColor());
		}
		if (border.getBool()) {
			Gfx.frame(ctx, -1, -1, w + 2, h + 2, r == 0 ? 0 : r + 1, 0xFF000000 | borderColor.getColor());
		}
		m.pushMatrix();
		m.translate(pad, pad);
		draw(ctx, mc);
		m.popMatrix();
		m.popMatrix();
	}

	/** Alpha byte for text, from "Text opacity". */
	protected int textA() {
		return Math.round(Math.max(0.2F, Math.min(1F, textAlpha.getFloat())) * 255F) << 24;
	}

	protected int colorAt(int x) {
		if (!chroma.getBool()) {
			return textA() | textColor.getColor();
		}
		float t = (System.currentTimeMillis() % 100000L) / 1000F * chromaSpeed.getFloat() * 0.25F;
		float hue = (t - x / 160F) % 1F;
		if (hue < 0) {
			hue += 1F;
		}
		return textA() | Theme.hsb(hue, 0.65F, 1F);
	}

	/** Black outline around text: the text drawn one pixel off in four directions. */
	protected void outlineText(GuiGraphicsExtractor ctx, String s, int x, int y) {
		if (!outline.getBool()) {
			return;
		}
		int c = (Math.round((textA() >>> 24) * 0.85F) << 24);
		ctx.text(Gfx.font(), s, x - 1, y, c, false);
		ctx.text(Gfx.font(), s, x + 1, y, c, false);
		ctx.text(Gfx.font(), s, x, y - 1, c, false);
		ctx.text(Gfx.font(), s, x, y + 1, c, false);
	}

	protected void line(GuiGraphicsExtractor ctx, String s, int x, int y) {
		outlineText(ctx, s, x, y);
		if (!chroma.getBool()) {
			ctx.text(Gfx.font(), s, x, y, colorAt(x), shadow.getBool() && !outline.getBool());
			return;
		}
		int cx = x;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			ctx.text(Gfx.font(), ch, cx, y, colorAt(cx), shadow.getBool() && !outline.getBool());
			cx += Gfx.font().width(ch);
			i += Character.charCount(cp);
		}
	}

	/**
	 * One time after updating: every HUD mod goes clear (no background box), like the new
	 * defaults. Anything changed after this is kept.
	 */
	public static void clearBackgroundsOnce(java.util.List<? extends Module> mods) {
		if (Config.getBool("top.hudClearV1", false)) {
			return;
		}
		for (Module m : mods) {
			if (m instanceof HudModule) {
				Config.set("mod." + m.id + ".bg", null);
				Config.set("mod." + m.id + ".bgAlpha", null);
			}
		}
		Config.setBool("top.hudClearV1", true);
		Config.saveIfNeeded(true);
	}
}
