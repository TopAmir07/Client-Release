package dev.topclient.client.clover.module;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ChatModules {
	private ChatModules() {
	}

	public static final class InfiniteChat extends Module {
		static InfiniteChat instance;
		private final Setting lines = number("lines", "Lines to keep", 500, 20000, 500, 10000);

		public InfiniteChat() {
			super("infinitechat", "Infinite Chat History", "Keeps thousands of chat lines instead of only 100, so you can scroll far back.", Category.UTILITY);
			isNew = true;
			instance = this;
		}
	}

	public static int historyLimit(int vanilla) {
		InfiniteChat c = InfiniteChat.instance;
		return c != null && c.enabled() ? Math.max(vanilla, c.lines.getInt()) : vanilla;
	}

	/** Hides chat lines that contain words you list (only on your screen). */
	public static final class ChatFilter extends Module {
		static ChatFilter instance;
		private final Setting words = text("words", "Hide lines containing (comma separated)", "");
		private final Setting ignoreCase = bool("ignoreCase", "Ignore upper/lower case", true);
		private String cachedRaw;
		private String[] cached = new String[0];

		public ChatFilter() {
			super("chatfilter", "Chat Filter", "Hides chat messages that contain words you choose. Only you stop seeing them.", Category.UTILITY);
			instance = this;
		}

		private String[] list() {
			String raw = words.getText();
			if (!raw.equals(cachedRaw)) {
				cachedRaw = raw;
				java.util.List<String> out = new java.util.ArrayList<>();
				for (String w : raw.split(",")) {
					if (!w.isBlank()) {
						out.add(w.strip());
					}
				}
				cached = out.toArray(new String[0]);
			}
			return cached;
		}
	}

	/** True when a message should be hidden by the Chat Filter. */
	public static boolean filtered(net.minecraft.network.chat.Component message) {
		ChatFilter f = ChatFilter.instance;
		if (f == null || !f.enabled() || message == null) {
			return false;
		}
		String[] list = f.list();
		if (list.length == 0) {
			return false;
		}
		boolean ic = f.ignoreCase.getBool();
		String text = message.getString();
		if (ic) {
			text = text.toLowerCase(java.util.Locale.ROOT);
		}
		for (String w : list) {
			if (text.contains(ic ? w.toLowerCase(java.util.Locale.ROOT) : w)) {
				return true;
			}
		}
		return false;
	}

	/** Groups identical consecutive chat lines into one line with a counter, like "Hello (5x)". */
	public static final class CompactChat extends Module {
		static CompactChat instance;
		private final Setting seconds = number("seconds", "Group repeats within (seconds)", 5, 600, 5, 60);
		private final Setting skipNames = text("skipNames", "Never compact lines from these players (comma separated)", "");
		private final Setting skipWords = text("skipWords", "Never compact lines containing (comma separated)", "");
		private String lastKey;
		private long lastNanos;
		private int count;
		private String rawNames, rawWords;
		private String[] names = new String[0], words = new String[0];
		private static java.lang.reflect.Field allField;
		private static java.lang.reflect.Method refresh;
		private static boolean reflectionFailed;

		public CompactChat() {
			super("compactchat", "Compact Chat", "Joins identical repeated chat lines into one line with a light-grey counter, like Hello [5x]. Turn it off to see every line again. Only your screen changes.", Category.UTILITY);
			isNew = true;
			instance = this;
		}

		private static String[] split(String raw) {
			java.util.List<String> out = new java.util.ArrayList<>();
			for (String w : raw.split(",")) {
				if (!w.isBlank()) {
					out.add(w.strip().toLowerCase(java.util.Locale.ROOT));
				}
			}
			return out.toArray(new String[0]);
		}

		/** True when this line must never be grouped (ignored player or protected keyword). */
		private boolean skipped(String text) {
			String n = skipNames.getText(), w = skipWords.getText();
			if (!n.equals(rawNames)) {
				rawNames = n;
				names = split(n);
			}
			if (!w.equals(rawWords)) {
				rawWords = w;
				words = split(w);
			}
			String lower = text.toLowerCase(java.util.Locale.ROOT);
			for (String x : words) {
				if (lower.contains(x)) {
					return true;
				}
			}
			for (String x : names) {
				if (lower.startsWith(x + ":") || lower.startsWith("<" + x + ">") || lower.contains(" " + x + ":")) {
					return true;
				}
			}
			return false;
		}

		/** Removes the newest chat line so the counted version can replace it. False if that is not possible. */
		private static boolean removeNewest(Object chat) {
			if (reflectionFailed || chat == null) {
				return false;
			}
			try {
				if (allField == null || refresh == null) {
					Class<?> c = net.minecraft.client.gui.components.ChatComponent.class;
					allField = c.getDeclaredField("allMessages");
					allField.setAccessible(true);
					refresh = c.getDeclaredMethod("refreshTrimmedMessages");
					refresh.setAccessible(true);
				}
				java.util.List<?> all = (java.util.List<?>) allField.get(chat);
				if (all.isEmpty()) {
					return false;
				}
				all.remove(0);
				refresh.invoke(chat);
				return true;
			} catch (ReflectiveOperationException | RuntimeException e) {
				reflectionFailed = true;
				return false;
			}
		}
	}

	/** Called for every new chat line: returns the line with a counter when it repeats the one before. */
	public static Component compact(Component message, Object chat) {
		CompactChat c = CompactChat.instance;
		if (c == null || !c.enabled() || message == null) {
			return message;
		}
		String key = message.getString();
		if (key.isBlank() || c.skipped(key)) {
			c.lastKey = null;
			return message;
		}
		long now = System.nanoTime();
		boolean repeat = key.equals(c.lastKey) && now - c.lastNanos <= c.seconds.getInt() * 1_000_000_000L
				&& CompactChat.removeNewest(chat);
		c.lastNanos = now;
		if (!repeat) {
			c.lastKey = key;
			c.count = 1;
			return message;
		}
		c.count++;
		return message.copy().append(Component.literal(" [" + c.count + "x]").withStyle(ChatFormatting.GRAY));
	}

	public static final class ChatTimestamps extends Module {
		static ChatTimestamps instance;
		private final Setting h24 = bool("h24", "24 hour clock", true);
		private final Setting seconds = bool("seconds", "Show seconds", false);
		private final Setting color = mode("color", "Colour", 0, "Grey", "Green", "Gold", "Aqua", "White");

		public ChatTimestamps() {
			super("chattime", "Chat Timestamps", "Puts the time in front of every chat message, like [14:32].", Category.UTILITY);
			isNew = true;
			instance = this;
		}

		ChatFormatting formatting() {
			return switch (color.getMode()) {
				case 1 -> ChatFormatting.GREEN;
				case 2 -> ChatFormatting.GOLD;
				case 3 -> ChatFormatting.AQUA;
				case 4 -> ChatFormatting.WHITE;
				default -> ChatFormatting.GRAY;
			};
		}

		String time() {
			String p = (h24.getBool() ? "HH:mm" : "h:mm") + (seconds.getBool() ? ":ss" : "") + (h24.getBool() ? "" : " a");
			return LocalTime.now().format(DateTimeFormatter.ofPattern(p));
		}
	}

	public static Component stamp(Component message) {
		ChatTimestamps t = ChatTimestamps.instance;
		if (t == null || !t.enabled() || message == null) {
			return message;
		}
		try {
			MutableComponent out = Component.literal("[" + t.time() + "] ").withStyle(t.formatting());
			return out.append(message);
		} catch (RuntimeException e) {
			return message;
		}
	}
}
