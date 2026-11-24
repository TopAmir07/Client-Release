package dev.topclient.client.clover.accounts;

import dev.topclient.client.clover.CloverClient;
import dev.topclient.client.clover.ui.Bloom;
import dev.topclient.client.clover.ui.Gfx;
import dev.topclient.client.clover.ui.Heads;
import dev.topclient.client.clover.ui.LoadingScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Account switcher: add Microsoft accounts with Microsoft's own sign-in window and switch without restarting. */
public class AccountsScreen extends Screen {
	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private int mx, my, scroll;

	private volatile MicrosoftAuth.DeviceCode code;
	private volatile String status = "";
	private volatile boolean busy, statusIsError;
	private volatile Thread worker;

	public AccountsScreen(Screen parent) {
		super(Component.literal("Accounts"));
		this.parent = parent;
		Accounts.remember();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof TitleScreen) && !(screen instanceof JoinMultiplayerScreen)) {
				return;
			}
			Accounts.remember();
			String label = "Accounts: " + Gfx.fit(Accounts.currentName(), 70);
			try {
				Screens.getWidgets(screen).add(Button.builder(Component.literal(label), b -> client.gui.setScreen(new AccountsScreen(screen)))
						.bounds(6, 6, 120, 20).build());
			} catch (RuntimeException e) {
				CloverClient.LOGGER.warn("Accounts button could not be added: {}", e.toString());
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean accent, Runnable action) {
		Bloom.button(ctx, x, y, w, h, label, accent, mx, my);
		hits.add(new Hit(x, y, w, h, action));
	}

	/** Opens the Microsoft sign-in page in Google Chrome when it is installed, otherwise the default browser. */
	static void openLink(String url) {
		SignInBrowsers.open(url);
	}

	private void say(String text, boolean error) {
		status = text;
		statusIsError = error;
	}

	private void startAdd() {
		if (busy) {
			return;
		}
		busy = true;
		code = null;
		say("Sign in on the Microsoft window that just opened...", false);
		Thread t = new Thread(() -> {
			try {
				MicrosoftAuth.Account a = MicrosoftAuth.signIn();
				if (worker == Thread.currentThread() && a != null) {
					Accounts.add(a);
					Minecraft.getInstance().execute(() -> switchTo(a));
					say("", false);
				}
			} catch (Exception e) {
				if (worker == Thread.currentThread()) {
					say(e.getMessage() == null ? "Sign-in failed." : e.getMessage(), true);
				}
			} finally {
				busy = false;
			}
		}, "TopClient account sign-in");
		t.setDaemon(true);
		worker = t;
		t.start();
	}

	private void cancelAdd() {
		Thread t = worker;
		worker = null;
		if (t != null) {
			t.interrupt();
		}
		LiveWindow.cancel();
		code = null;
		busy = false;
		say("", false);
	}

	private void switchTo(MicrosoftAuth.Account a) {
		try {
			Accounts.use(a);
			say("Now playing as " + a.name() + ".", false);
		} catch (Exception e) {
			say("Could not switch: " + e.getMessage(), true);
		}
	}

	private void useSaved(Accounts.Saved s) {
		if (busy) {
			return;
		}
		busy = true;
		say("Switching to " + s.name + "...", false);
		Thread t = new Thread(() -> {
			try {
				MicrosoftAuth.Account a = Accounts.fresh(s);
				Minecraft.getInstance().execute(() -> switchTo(a));
			} catch (Exception e) {
				say(e.getMessage() == null ? "Could not switch." : e.getMessage(), true);
			} finally {
				busy = false;
			}
		}, "TopClient account switch");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Bloom.background(ctx, width, height, width / 2F, height / 2F, Math.min(width, height) * 0.4F);
	}

	private void accountRow(GuiGraphicsExtractor ctx, int x, int y, int w, UUID id, String name, String sub, boolean on) {
		boolean over = Gfx.inside(mx, my, x, y, w, 34);
		Gfx.rect(ctx, x, y, w, 34, 8, Bloom.argb(on ? Bloom.PINK : over ? 0x8A4474 : 0x5A2A4C, on ? 0.95F : 0.7F));
		Gfx.rect(ctx, x + 1, y + 1, w - 2, 32, 7, Bloom.argb(on ? 0x46193A : over ? Bloom.CARD_HOVER : Bloom.CARD, 0.96F));
		Gfx.rect(ctx, x + 6, y + 5, 24, 24, 4, Bloom.argb(0x000000, 0.35F));
		Heads.draw(ctx, id, x + 7, y + 6, 22);
		Gfx.text(ctx, Gfx.fit(name, w - 170), x + 38, y + 7, Bloom.TEXT);
		Gfx.text(ctx, sub, x + 38, y + 19, on ? Bloom.PINK | 0xFF000000 : Bloom.TEXT_DIM);
	}

	private static UUID parse(String s) {
		try {
			return UUID.fromString(s);
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		hits.clear();
		mx = mouseX;
		my = mouseY;
		int pw = Math.min(width - 16, 400), ph = Math.min(height - 16, 320);
		int px = (width - pw) / 2, py = (height - ph) / 2;
		Bloom.panel(ctx, px, py, pw, ph);
		LoadingScreen.image(ctx, LoadingScreen.brand("flower"), px + 22, py + 20, 22, Bloom.time() * 0.4F, 0xFFFFFFFF);
		Gfx.scaled(ctx, "Accounts", px + 38, py + 10, 1.25F, Bloom.TEXT, true);
		Gfx.text(ctx, "Playing as " + Accounts.currentName(), px + 38, py + 24, Bloom.TEXT_DIM);
		button(ctx, px + pw - 62, py + 10, 52, 18, "Done", false, this::onClose);

		MicrosoftAuth.DeviceCode dc = code;
		int y = py + 42;
		if (dc != null) {
			Gfx.rect(ctx, px + 10, y, pw - 20, 96, 8, Bloom.argb(Bloom.PINK, 0.8F));
			Gfx.rect(ctx, px + 11, y + 1, pw - 22, 94, 7, Bloom.argb(Bloom.CARD, 0.97F));
			Gfx.centered(ctx, "1. Open " + dc.link().replace("https://", "") + "   2. Enter this code   3. Sign in", px + pw / 2, y + 8, Bloom.TEXT_DIM);
			Gfx.scaledCentered(ctx, dc.userCode(), px + pw / 2F, y + 24, 2.6F, Bloom.PINK | 0xFF000000, true);
			int bw = 120;
			button(ctx, px + pw / 2 - bw - 14, y + 66, bw + 10, 20, "Open sign-in page", true, () -> openLink(dc.link()));
			button(ctx, px + pw / 2 + 4, y + 66, 72, 20, "Copy code", false, () -> Minecraft.getInstance().keyboardHandler.setClipboard(dc.userCode()));
			button(ctx, px + pw / 2 + 80, y + 66, 56, 20, "Cancel", false, this::cancelAdd);
			String br = "Browser: " + SignInBrowsers.label(SignInBrowsers.chosen()) + "  (click to change)";
			button(ctx, px + 10, y + 100, pw - 20, 16, br, false, () -> {
				List<String> c = SignInBrowsers.choices();
				int i = c.indexOf(SignInBrowsers.chosen());
				SignInBrowsers.choose(c.get((i + 1) % c.size()));
			});
			y += 124;
		} else {
			if (busy) {
				button(ctx, px + 10, y, pw - 82, 22, "Waiting for the Microsoft window...", true, () -> {
				});
				button(ctx, px + pw - 68, y, 58, 22, "Cancel", false, this::cancelAdd);
			} else {
				button(ctx, px + 10, y, pw - 20, 22, "+  Sign in with Microsoft", true, this::startAdd);
			}
			button(ctx, px + 10, y + 26, pw - 20, 16, "Sign-in window: " + SignInBrowsers.label(SignInBrowsers.chosen()) + "  (click to change)", false, () -> {
				List<String> c = SignInBrowsers.choices();
				int i = c.indexOf(SignInBrowsers.chosen());
				SignInBrowsers.choose(c.get((i + 1) % c.size()));
			});
			y += 50;
		}
		if (!status.isEmpty()) {
			Gfx.centered(ctx, Gfx.fit(status, pw - 24), px + pw / 2, y, statusIsError ? 0xFFFF7D97 : Bloom.TEXT_DIM);
			y += 14;
		}

		int top = y, bottom = py + ph - 8;
		ctx.enableScissor(px + 4, top, px + pw - 4, bottom);
		int ry = top - scroll;
		int x = px + 10, w = pw - 20;
		String current = Accounts.currentName();
		String launcher = Accounts.launcherName();
		if (launcher != null) {
			boolean on = Accounts.onLauncherAccount();
			if (ry + 34 > top && ry < bottom) {
				accountRow(ctx, x, ry, w, Accounts.launcherUuid(), launcher, on ? "Playing now  -  from the launcher" : "From the launcher", on);
				if (!on) {
					button(ctx, x + w - 64, ry + 8, 56, 18, "Use", true, () -> {
						try {
							Accounts.backToLauncher();
							say("Now playing as " + launcher + ".", false);
						} catch (Exception e) {
							say("Could not switch: " + e.getMessage(), true);
						}
					});
				}
			}
			ry += 38;
		}
		for (Accounts.Saved s : new ArrayList<>(Accounts.all())) {
			if (launcher != null && s.name.equals(launcher) && Accounts.onLauncherAccount()) {
				continue;
			}
			boolean on = s.name.equals(current) && !Accounts.onLauncherAccount();
			if (ry + 34 > top && ry < bottom) {
				accountRow(ctx, x, ry, w, parse(s.uuid), s.name, on ? "Playing now" : "Microsoft account", on);
				if (!on) {
					button(ctx, x + w - 126, ry + 8, 56, 18, "Use", true, () -> useSaved(s));
				}
				button(ctx, x + w - 64, ry + 8, 56, 18, "Remove", false, () -> Accounts.remove(s));
			}
			ry += 38;
		}
		ctx.disableScissor();
		int contentH = ry + scroll - top;
		scroll = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top)), scroll));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (Gfx.inside(click.x(), click.y(), h.x(), h.y(), h.w(), h.h())) {
					h.action().run();
					return true;
				}
			}
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, scroll - (int) Math.round(verticalAmount * 16));
		return true;
	}

	@Override
	public void onClose() {
		cancelAdd();
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
