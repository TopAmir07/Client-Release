package dev.topclient.client.gui;

import dev.topclient.client.module.modules.client.ClickGUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class FallingParticles {
	private static final class Flake {
		float x, y;
		float speed;
		float sway, swayAmount, swaySpeed;
		float rot, rotSpeed;
		float size;
		int kind;
	}

	private static final Item[] MINECRAFT = {Items.DIAMOND, Items.EMERALD, Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING,
			Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.GOLD_INGOT, Items.NETHER_STAR, Items.COOKIE, Items.END_CRYSTAL};

	private final List<Flake> flakes = new ArrayList<>();
	private final Random random = new Random();
	private String themeShown = "";
	private int lastH;

	public static String theme() {
		ClickGUI.FallTheme t = ClickGUI.fallTheme.getMode();
		if (t == ClickGUI.FallTheme.None) {
			return "";
		}
		if (t == ClickGUI.FallTheme.Seasonal) {
			return seasonal(LocalDate.now());
		}
		return t.name();
	}

	static String seasonal(LocalDate d) {
		int month = d.getMonthValue();
		int day = d.getDayOfMonth();
		return switch (month) {
			case 1 -> "Snow";
			case 2 -> day <= 15 ? "Valentine" : "Snow";
			case 3 -> "Clovers";
			case 4 -> "Easter";
			case 5 -> "Sakura";
			case 6 -> "Fruits";
			case 7 -> "Ocean";
			case 8 -> "Africa";
			case 9 -> "Autumn";
			case 10 -> "Halloween";
			case 11 -> "Autumn";
			default -> day >= 26 ? "Party" : "Christmas";
		};
	}

	private int kinds(String theme) {
		if (theme.equals("Minecraft")) {
			return MINECRAFT.length;
		}
		String[] pics = Emoji.THEMES.get(theme);
		return pics == null ? 0 : pics.length;
	}

	private void respawn(Flake f, int H, boolean anywhere, int kinds) {
		f.x = random.nextFloat();
		f.y = anywhere ? random.nextFloat() * H : -40F - random.nextFloat() * 60F;
		f.speed = 38F + random.nextFloat() * 52F;
		f.sway = random.nextFloat() * 6.283F;
		f.swayAmount = 10F + random.nextFloat() * 26F;
		f.swaySpeed = 0.6F + random.nextFloat() * 0.9F;
		f.rot = random.nextFloat() * 6.283F;
		f.rotSpeed = (random.nextFloat() - 0.5F) * 2.2F;
		f.size = 0.65F + random.nextFloat() * 0.7F;
		f.kind = kinds > 0 ? random.nextInt(kinds) : 0;
	}

	public void render(GuiGraphicsExtractor ctx, int W, int H, float dt) {
		String theme = theme();
		if (theme.isEmpty()) {
			flakes.clear();
			themeShown = "";
			return;
		}
		int kinds = kinds(theme);
		if (kinds == 0) {
			return;
		}
		int want = Math.max(0, Math.min(150, ClickGUI.fallAmount.getValueInt()));
		boolean fresh = !theme.equals(themeShown) || Math.abs(H - lastH) > 4;
		if (fresh) {
			flakes.clear();
			themeShown = theme;
			lastH = H;
		}
		while (flakes.size() < want) {
			Flake f = new Flake();
			respawn(f, H, true, kinds);
			flakes.add(f);
		}
		while (flakes.size() > want) {
			flakes.remove(flakes.size() - 1);
		}

		float speed = (float) ClickGUI.fallSpeed.getValue();
		float baseSize = 26F * (float) ClickGUI.fallSize.getValue();
		boolean spin = ClickGUI.fallSpin.getValue();
		int alpha = Math.round(255 * (float) ClickGUI.fallOpacity.getValue() / 100F);
		boolean minecraft = theme.equals("Minecraft");
		String[] pics = minecraft ? null : Emoji.THEMES.get(theme);

		for (Flake f : flakes) {
			f.y += f.speed * speed * dt;
			f.sway += f.swaySpeed * dt;
			if (spin) {
				f.rot += f.rotSpeed * dt;
			}
			float size = baseSize * f.size;
			if (f.y - size > H) {
				respawn(f, H, false, kinds);
			}
			float x = f.x * W + (float) Math.sin(f.sway) * f.swayAmount;
			if (minecraft) {
				Item item = MINECRAFT[f.kind % MINECRAFT.length];
				ItemIcons.draw(ctx, item, x, f.y, size);
			} else {
				float rot = spin ? f.rot : (float) Math.cos(f.sway) * 0.35F;
				Pictures.draw(ctx, pics[f.kind % pics.length], x, f.y, size, rot, alpha);
			}
		}
	}
}
