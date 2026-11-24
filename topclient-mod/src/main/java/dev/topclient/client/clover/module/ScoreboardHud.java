package dev.topclient.client.clover.module;

import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the server's sidebar scoreboard as a normal TopClient HUD element, so it can be
 * moved, resized (scroll wheel in Move HUD), made see-through or have its background removed.
 * It only reads the scoreboard the server already sends to the game; nothing is sent anywhere.
 * When this module is off, Minecraft draws its own scoreboard as usual.
 */
public final class ScoreboardHud extends HudModule {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static ScoreboardHud INSTANCE;

	private final Setting numbers;
	private final Setting showTitle;
	private final Setting divider;
	private final Setting maxLines;

	private record Row(Component name, Component score) {
	}

	private Component title = Component.empty();
	private List<Row> rows = List.of();
	private boolean present;
	private long builtAt;
	private int cachedW;
	private boolean lastPreview = true;

	public ScoreboardHud() {
		super("scoreboard", "Scoreboard", "Move, resize and restyle the server's scoreboard. In Move HUD: drag it, scroll to resize.", 100, 50);
		header("Scoreboard");
		numbers = bool("numbers", "Show red numbers", true);
		showTitle = bool("title", "Show title", true);
		divider = bool("divider", "Line under title", false);
		maxLines = number("lines", "Max lines", 1, 15, 1, 15);
		INSTANCE = this;
	}

	@Override
	protected boolean defaultEnabled() {
		return false; // everything starts off: switch it on with Right Shift
	}

	/** True when we draw the scoreboard ourselves, so the vanilla one is hidden. */
	public static boolean active() {
		return INSTANCE != null && INSTANCE.enabled();
	}

	private static Objective objective(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return null;
		}
		Scoreboard sb = mc.level.getScoreboard();
		Objective obj = null;
		PlayerTeam team = sb.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = team.getColor().map(c -> DisplaySlot.valueOf("TEAM_" + c.name())).orElse(null);
			if (slot != null) {
				obj = sb.getDisplayObjective(slot);
			}
		}
		return obj != null ? obj : sb.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	private void build(Minecraft mc, boolean preview) {
		long now = System.nanoTime();
		if (now - builtAt < 50_000_000L && preview == lastPreview) {
			return;
		}
		builtAt = now;
		lastPreview = preview;
		List<Row> out = new ArrayList<>();
		Objective obj = objective(mc);
		present = obj != null;
		if (obj != null) {
			Scoreboard sb = obj.getScoreboard();
			NumberFormat fmt = obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
			title = obj.getDisplayName();
			List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
			entries.removeIf(PlayerScoreEntry::isHidden);
			entries.sort(ORDER);
			int limit = maxLines.getInt();
			for (PlayerScoreEntry e : entries) {
				if (out.size() >= limit) {
					break;
				}
				Component name = PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName());
				out.add(new Row(name, e.formatValue(fmt)));
			}
		} else if (preview) {
			title = Component.literal("Scoreboard").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
			out.add(new Row(Component.literal("Drag me anywhere"), Component.literal("3").withStyle(ChatFormatting.RED)));
			out.add(new Row(Component.literal("Scroll to resize"), Component.literal("2").withStyle(ChatFormatting.RED)));
			out.add(new Row(Component.literal("play.server.net").withStyle(ChatFormatting.GOLD), Component.literal("1").withStyle(ChatFormatting.RED)));
		}
		rows = out;
		int w = showTitle.getBool() ? Gfx.font().width(title) : 0;
		boolean nums = numbers.getBool();
		for (Row r : out) {
			int rw = Gfx.font().width(r.name()) + (nums ? 9 + Gfx.font().width(r.score()) : 0);
			w = Math.max(w, rw);
		}
		cachedW = Math.max(40, w);
	}

	@Override
	public boolean available(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return false;
		}
		build(mc, false);
		return present;
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
		builtAt = 0;
		build(mc, preview);
		if (!present && !preview) {
			return;
		}
		super.render(ctx, mc, preview);
	}

	@Override
	public int width(Minecraft mc) {
		build(mc, lastPreview);
		return cachedW;
	}

	@Override
	public int height(Minecraft mc) {
		build(mc, lastPreview);
		int h = rows.size() * 9;
		if (showTitle.getBool()) {
			h += 10 + (divider.getBool() ? 2 : 0);
		}
		return Math.max(9, h - 1);
	}

	@Override
	protected void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
		var font = Gfx.font();
		int base = 0xFF000000 | textColor.getColor();
		boolean sh = shadow.getBool();
		int w = cachedW;
		int y = 0;
		if (showTitle.getBool()) {
			ctx.text(font, title, (w - font.width(title)) / 2, y, base, sh);
			y += 10;
			if (divider.getBool()) {
				ctx.fill(0, y - 1, w, y, Theme.argb(Theme.accent(), 200));
				y += 2;
			}
		}
		boolean nums = numbers.getBool();
		int align = this.align.getMode();
		for (Row r : rows) {
			int nameW = font.width(r.name());
			int scoreW = nums ? font.width(r.score()) : 0;
			int nx = switch (align) {
				case 1 -> nums ? 0 : (w - nameW) / 2;
				case 2 -> nums ? 0 : w - nameW;
				default -> 0;
			};
			ctx.text(font, r.name(), nx, y, base, sh);
			if (nums) {
				ctx.text(font, r.score(), w - scoreW, y, base, sh);
			}
			y += 9;
		}
	}
}
