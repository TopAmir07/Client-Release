package dev.topclient.client.module.modules.client;

import dev.topclient.client.TopClient;
import dev.topclient.client.gui.ClickGui;
import dev.topclient.client.module.Category;
import dev.topclient.client.module.Module;
import dev.topclient.client.module.setting.BooleanSetting;
import dev.topclient.client.module.setting.ModeSetting;
import dev.topclient.client.module.setting.NumberSetting;
import dev.topclient.client.module.setting.SectionSetting;
import dev.topclient.client.module.setting.StringSetting;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {
	public enum AnimationMode {
		Normal, Positive, Off
	}

	public enum ThemeMode {
		Savanna, Nebula, Crimson, Ocean, Forest, Mono, Midnight, Sakura, Sunset, Royal, Neon, Arctic, Lava, Mint, Grape, Coffee, Cyber, Clover, Gold, Custom
	}

	public enum MenuFont {
		Inter, Quicksand, System, Minecraft
	}

	public enum Backdrop {
		Dim, Gradient, Aurora, Fireflies, Starfield, Picture, None
	}

	public enum OpenAnimation {
		Slide, Zoom, Fade, None
	}

	public enum WalkerPose {
		Aura, Wave, Calm, Point, Dab, Victory, TPose, Zombie, Salute, Crossed, Dance, Random
	}

	public enum IconStyle {
		Outline, Minecraft, Emoji
	}

	public enum TileShape {
		Rounded, Circle, Square, None
	}

	public enum FallTheme {
		None, Seasonal, Clovers, Halloween, Christmas, Snow, Sakura, Autumn, Valentine, Easter, Party, Space, Ocean, Money, Fruits, Africa, Minecraft
	}

	public enum FallLayer {
		Behind, InFront
	}

	public enum CardSize {
		Comfortable, Compact
	}

	public enum Columns {
		Auto, Two, Three, Four
	}

	public static final ModeSetting<ThemeMode> themeMode = new ModeSetting<>("Theme", ThemeMode.Savanna, ThemeMode.class)
			.setDescription("Accent colours of the menu. Custom uses Red / Green / Blue");
	public static final NumberSetting red = new NumberSetting("Red", 0, 255, 255, 1)
			.setDescription("Custom theme and ESP colour (red)");
	public static final NumberSetting green = new NumberSetting("Green", 0, 255, 138, 1)
			.setDescription("Custom theme and ESP colour (green)");
	public static final NumberSetting blue = new NumberSetting("Blue", 0, 255, 61, 1)
			.setDescription("Custom theme and ESP colour (blue)");
	public static final BooleanSetting classicMenu = new BooleanSetting("Classic Menu", false)
			.setDescription("Right Shift opens the classic menu instead of the TopClient mod menu");
	public static final BooleanSetting rainbow = new BooleanSetting("Rainbow", false)
			.setDescription("Rainbow colours for ESP, HUD and other render modules");
	public static final BooleanSetting breathing = new BooleanSetting("Breathing", true)
			.setDescription("Pulsing colour for render modules (when rainbow is off)");

	public static final NumberSetting guiScale = new NumberSetting("Menu Size", 0.7, 1.5, 1.0, 0.05)
			.setDescription("Makes the whole menu bigger or smaller");
	public static final NumberSetting alphaWindow = new NumberSetting("Panel Opacity", 150, 255, 245, 1)
			.setDescription("How solid the menu window is");
	public static final NumberSetting roundQuads = new NumberSetting("Roundness", 0, 8, 4, 1)
			.setDescription("Corner roundness of the menu");
	public static final ModeSetting<OpenAnimation> openAnimation = new ModeSetting<>("Open Animation", OpenAnimation.Slide, OpenAnimation.class)
			.setDescription("How the menu appears when you open it");
	public static final ModeSetting<AnimationMode> animationMode = new ModeSetting<>("Animations", AnimationMode.Normal, AnimationMode.class)
			.setDescription("Hover and switch animations. Off = instant");

	public static final ModeSetting<Backdrop> backdrop = new ModeSetting<>("Background", Backdrop.Aurora, Backdrop.class)
			.setDescription("What is drawn behind the menu");
	public static final NumberSetting backdropDim = new NumberSetting("Darkness", 0, 100, 55, 1)
			.setDescription("How much the game behind the menu is darkened");
	public static final StringSetting backdropPicture = new StringSetting("Picture", "")
			.setDescription("Full path to a .png or .jpg on your PC, used when Background is Picture");

	public static final BooleanSetting cardIcons = new BooleanSetting("Icons", true)
			.setDescription("Shows an icon for every module and mod");
	public static final BooleanSetting cardDescriptions = new BooleanSetting("Descriptions", true)
			.setDescription("Shows what each module does on its card");
	public static final ModeSetting<CardSize> cardSize = new ModeSetting<>("Card Size", CardSize.Comfortable, CardSize.class)
			.setDescription("Compact fits more modules on the screen");
	public static final NumberSetting cardOpacity = new NumberSetting("Card Opacity", 0, 100, 100, 1)
			.setDescription("How see-through the module cards are (100 = solid)");
	public static final NumberSetting previewOpacity = new NumberSetting("Preview Opacity", 0, 100, 100, 1)
			.setDescription("How see-through the texture preview tiles in Visuals are (100 = solid)");
	public static final ModeSetting<Columns> columns = new ModeSetting<>("Columns", Columns.Auto, Columns.class)
			.setDescription("How many cards side by side");

	public static final ModeSetting<MenuFont> menuFont = new ModeSetting<>("Menu Font", MenuFont.Inter, MenuFont.class)
			.setDescription("Font of the menu. Inter is the clearest");
	public static final BooleanSetting customFont = new BooleanSetting("Smooth HUD Font", true)
			.setDescription("Smooth font for the TopClient HUD (off = Minecraft font)");

	public static final ModeSetting<IconStyle> iconStyle = new ModeSetting<>("Icon Theme", IconStyle.Outline, IconStyle.class)
			.setDescription("Outline icons, real Minecraft items (a sword, a totem...) or colourful emoji");
	public static final ModeSetting<TileShape> iconTile = new ModeSetting<>("Icon Tile", TileShape.Rounded, TileShape.class)
			.setDescription("The shape behind each card's icon");

	public static final ModeSetting<FallTheme> fallTheme = new ModeSetting<>("Falling Theme", FallTheme.Seasonal, FallTheme.class)
			.setDescription("Things that fall behind the menu. Seasonal picks one for today's date");
	public static final NumberSetting fallAmount = new NumberSetting("Amount", 5, 120, 30, 1)
			.setDescription("How many fall at the same time");
	public static final NumberSetting fallSpeed = new NumberSetting("Fall Speed", 0.3, 3, 1, 0.1)
			.setDescription("How fast they fall");
	public static final NumberSetting fallSize = new NumberSetting("Particle Size", 0.5, 2.5, 1, 0.05)
			.setDescription("How big they are");
	public static final NumberSetting fallOpacity = new NumberSetting("Particle Opacity", 20, 100, 85, 1)
			.setDescription("How see-through they are");
	public static final BooleanSetting fallSpin = new BooleanSetting("Spin", false)
			.setDescription("Spin as they fall (off = a gentle tilt)");
	public static final ModeSetting<FallLayer> fallLayer = new ModeSetting<>("Layer", FallLayer.Behind, FallLayer.class)
			.setDescription("Behind the menu window or in front of it");

	public static final BooleanSetting walker = new BooleanSetting("Skin Walker", true)
			.setDescription("Your skin walks up next to the menu when you open it");
	public static final ModeSetting<WalkerPose> walkerPose = new ModeSetting<>("Pose", WalkerPose.Aura, WalkerPose.class)
			.setDescription("What your skin does once it is there. Aura floats and powers up");
	public static final BooleanSetting walkerEffects = new BooleanSetting("Aura Effects", true)
			.setDescription("Glow, flames and sparks around your skin in the Aura pose");
	public static final BooleanSetting walkerName = new BooleanSetting("Show Name", true)
			.setDescription("Your name floats up above your skin's head");
	public static final NumberSetting walkerSize = new NumberSetting("Walker Size", 0.6, 1.4, 1.0, 0.05)
			.setDescription("How big your skin is next to the menu");
	public static final NumberSetting walkerDelay = new NumberSetting("Walk In Delay", 0, 3, 1, 0.1)
			.setDescription("Seconds after opening the menu before your skin walks in");

	public static final BooleanSetting showStats = new BooleanSetting("Status Bar", true)
			.setDescription("Shows server, FPS and ping at the bottom of the menu");
	public static final BooleanSetting antiAliasing = new BooleanSetting("MSAA", true)
			.setDescription("Smoother ESP lines. Can cost FPS with many tracers");

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeTheme() {
		return java.util.List.of(themeMode, red, green, blue, rainbow, breathing);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeBackground() {
		return java.util.List.of(backdrop, backdropDim, backdropPicture);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeWindow() {
		return java.util.List.of(guiScale, alphaWindow, roundQuads, openAnimation, animationMode);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeCards() {
		return java.util.List.of(cardIcons, cardDescriptions, cardSize, columns, cardOpacity, previewOpacity);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeText() {
		return java.util.List.of(menuFont, customFont);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeIcons() {
		return java.util.List.of(iconStyle, iconTile, cardIcons);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeFalling() {
		return java.util.List.of(fallTheme, fallAmount, fallSpeed, fallSize, fallOpacity, fallSpin, fallLayer);
	}

	public static java.util.List<dev.topclient.client.module.setting.Setting<?>> customizeWalker() {
		return java.util.List.of(walker, walkerPose, walkerEffects, walkerName, walkerSize, walkerDelay);
	}

	public boolean onCustomizePage(dev.topclient.client.module.setting.Setting<?> s) {
		if (s instanceof SectionSetting) {
			return !s.getName().toString().equals("Other");
		}
		return customizeTheme().contains(s) || customizeBackground().contains(s) || customizeWindow().contains(s)
				|| customizeCards().contains(s) || customizeText().contains(s) || customizeWalker().contains(s)
				|| customizeIcons().contains(s) || customizeFalling().contains(s);
	}


	public ClickGUI() {
		super("TopClient",
				"Menu key, status bar and other client options. Themes, sounds and looks are in Customize",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				Category.CLIENT);

		addSettings(new SectionSetting("Menu"), classicMenu, new SectionSetting("Theme"), themeMode, red, green, blue, rainbow, breathing,
				new SectionSetting("Window"), guiScale, alphaWindow, roundQuads, openAnimation, animationMode,
				new SectionSetting("Background"), backdrop, backdropDim, backdropPicture,
				new SectionSetting("Cards"), cardIcons, cardDescriptions, cardSize, columns, cardOpacity, previewOpacity,
				new SectionSetting("Text"), menuFont, customFont,
				new SectionSetting("Icons"), iconStyle, iconTile,
				new SectionSetting("Falling Particles"), fallTheme, fallAmount, fallSpeed, fallSize, fallOpacity, fallSpin, fallLayer,
				new SectionSetting("Skin Walker"), walker, walkerPose, walkerEffects, walkerName, walkerSize, walkerDelay,
				new SectionSetting("Other"), showStats, antiAliasing);
	}

	@Override
	public void onEnable() {
		if (!(mc.gui.screen() instanceof InventoryScreen) && !classicMenu.getValue()) {
			// TopClient mod menu (Right Shift)
			dev.topclient.client.top.ModMenuScreen.open(mc.gui.screen() instanceof ClickGui ? null : mc.gui.screen());
			super.onEnable();
			return;
		}
		if (!(mc.gui.screen() instanceof ClickGui))
			TopClient.INSTANCE.previousScreen = mc.gui.screen();

		if (TopClient.INSTANCE.clickGui != null) {
			mc.setScreenAndShow(TopClient.INSTANCE.clickGui);
		} else if (mc.gui.screen() instanceof InventoryScreen) {
			TopClient.INSTANCE.guiInitialized = true;
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {

		if (mc.gui.screen() instanceof dev.topclient.client.top.ModMenuScreen mm) {
			mm.closeNow();
		} else if (mc.gui.screen() instanceof ClickGui) {
			TopClient.INSTANCE.clickGui.onGuiClose();
		} else if (mc.gui.screen() instanceof InventoryScreen) {
			TopClient.INSTANCE.guiInitialized = false;
		}

		super.onDisable();
	}
}
