package dev.topclient.client.clover.module;

import dev.topclient.client.clover.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class TextHud extends HudModule {
	public final Setting brackets;

	protected TextHud(String id, String name, String description, int defX, int defY) {
		super(id, name, description, defX, defY);
		this.brackets = mode("brackets", "Brackets", 0, "None", "[ ]", "( )", "< >", "{ }");
		this.brackets.look = true;
		settings.remove(brackets);
		settings.add(settings.indexOf(align) + 1, brackets);
	}

	private String[] cache;
	private long cacheAt;

	/** How long (ms) the text may be reused before it is rebuilt. 0 = rebuilt on every call (the default). */
	protected long cacheMillis() {
		return 0;
	}

	private String[] cachedLines(Minecraft mc) {
		long ms = cacheMillis();
		if (ms <= 0) {
			return lines(mc);
		}
		long now = System.nanoTime();
		if (cache == null || now - cacheAt >= ms * 1_000_000L) {
			cache = lines(mc);
			cacheAt = now;
		}
		return cache;
	}

	protected String[] shown(Minecraft mc) {
		String[] ls = cachedLines(mc);
		int b = brackets.getMode();
		if (b == 0) {
			return ls;
		}
		String open = new String[]{"", "[", "(", "<", "{"}[b];
		String close = new String[]{"", "]", ")", ">", "}"}[b];
		String[] out = new String[ls.length];
		for (int i = 0; i < ls.length; i++) {
			out[i] = open + ls[i] + close;
		}
		return out;
	}

	protected abstract String[] lines(Minecraft mc);

	@Override
	public int width(Minecraft mc) {
		int w = 10;
		for (String l : shown(mc)) {
			w = Math.max(w, Gfx.font().width(l));
		}
		return w;
	}

	@Override
	public int height(Minecraft mc) {
		return Math.max(1, cachedLines(mc).length) * 10 - 2;
	}

	@Override
	protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
		String[] ls = shown(mc);
		int w = width(mc);
		for (int i = 0; i < ls.length; i++) {
			int lw = Gfx.font().width(ls[i]);
			int x = switch (align.getMode()) {
				case 1 -> (w - lw) / 2;
				case 2 -> w - lw;
				default -> 0;
			};
			line(ctx, ls[i], x, i * 10);
		}
	}
}
