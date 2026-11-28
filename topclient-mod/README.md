# TopClient (Fabric, Minecraft 26.2)

TopClient by TopAmir_: a legit Fabric client mod with HUD mods, visuals, utilities, profiles and a mod menu on Right Shift. No combat advantages: no auto-clicker, no reach, no hitbox expansion, no aim help.

## Crosshair editor (Clover > Visual > Crosshair)

Open **Open crosshair editor** from the Crosshair module.

- **SIMPLE:** quick controls for opacity, size, gap, thickness, centre dot and outline.
- **PRESETS:** choose a built-in crosshair or one of your saved drawings.
- **CUSTOM:** draw on the centred 7x7 grid (switch between 7, 9, 11, 13 and 15); left-click/drag draws, right-click erases. Mirror horizontally or vertically, invert, clear, undo and redo.
- **Share:** press **Share**, paste a `TCX1-` code into the field and press Enter to import; use **Copy share code** to copy the current design.
- **Colour:** HSV picker, RGB sliders, `#RRGGBB` or `#AARRGGBB` hex, opacity and one saved favourite colour.
- **Render options:** vanilla blending, movement/bow/attack dynamic gap, spectator visibility, health colour shift, custom scale and an elytra status tint.
- **Context crosshairs:** enable separate shapes and colours for allied or opposing players, and optionally show the custom crosshair in third person.

Saved drawings and crosshair settings are stored in the normal TopClient config. The custom editor keeps its existing 15x15 backing format and shows a centred, resizable window so older drawings remain compatible.

## Performance and connection stability

### Vulkan on Minecraft 26.2
- TopClient uses Sodium rather than shipping a second renderer. Sodium 0.9.x for Minecraft 26.2 has experimental Vulkan support through Minecraft's **Video Settings → Graphics API** option.
- Vulkan is optional and should be tested separately. Keep OpenGL as the fallback if a mod, shader pack, or driver behaves incorrectly.
- Do not install the legacy standalone VulkanMod alongside the native Sodium/Minecraft Vulkan backend.
- Iris shader support may not be available while Vulkan is selected. Switch back to OpenGL when using an OpenGL-based shader setup.

### Ping stability HUD
- The Ping HUD can show server-reported latency, rolling min/average/max values, and an estimated jitter value.
- Jitter is the smoothed average change between consecutive server-reported ping samples; it helps identify an unstable connection.
- This is monitoring, not a way to bypass server latency. The client cannot remove distance to the server or control server-side tick/network delays.

## Credits
TopClient is a fork of the AfrikaClient 26.2 mod, which is itself based on lvstrng's client source. The original licence (`LICENSE`) still applies, and the history of the base mod is kept below.

## Building
- Offline, as done for every release: `tools/build.sh` (javac against the 26.2 game jars, then MixinVerify).
- With Gradle: `gradlew build` (Java 21). The Gradle files target 26.2 / Loom 1.16 without a mappings step, but they weren't run in the workspace these builds came from.

See `PLAN.md` for the roadmap and the list of which mods exist and which are missing.

---

## History of the base mod

# AfrikaClient 2.1

See CHANGES.md for what is new in 2.1 (Item Physics, Colour Boost, account switcher, loading screen).

## Building
Install Java 21 (JDK), then in this folder run `gradlew build` (Windows: `gradlew.bat build`). The mod is `build/libs/AfrikaClient-2.1.0.jar`.

# AfrikaClient 2.0

Fabric client for **Minecraft 1.21.11**. Based on lvstrng's AfrikaClient source: read `LICENSE` before you share or publish it (the original README asked that the client not be sold or passed off as someone else's).

## What's new in 2.0

### New menu (Right Shift)
- Floating window with a grouped sidebar: **Combat** (Combat, Crystal, Defense), **Player** (Movement, Utility, Network), **Visual** (Render), **Clover** (HUD, Visual, Utility, Capes) and **System** (Client).
- Every module is a card with its own switch, description, keybind chip and a **Settings** link (or right-click the card).
- The settings panel slides in from the right with switches, sliders, range sliders, mode pickers, keybind capture, text fields, colour swatches and buttons. Hover a setting to see what it does. **Reset to defaults** is at the bottom.
- **Search everything**: just start typing, or press Ctrl+F. It searches the Afrika and Clover modules together.
- **All / Enabled / Disabled** filters, counts in the sidebar, and a status bar with server, FPS, ping and active modules.
- 19 themes plus Custom: Savanna (default), Nebula, Crimson, Ocean, Forest, Mono, Midnight, Sakura, Sunset, Royal, Neon, Arctic, Lava, Mint, Grape, Coffee, Cyber, Clover and Afrika. Each tints the window colours too. Click the theme name in the settings to open the picker with colour swatches. Change them in Customize > Theme.
- **Sharp text**: the menu draws its text 1:1 with your screen's pixels, using the bundled **Inter** font (or Quicksand, your system font, or the Minecraft font in Client > AfrikaClient > Menu Font).
- **Icons for everything**: every module, Clover mod, page and button has its own icon (sword, crystal, anchor, totem, keyboard, clock...).
- **Customize page** (System > Customize): Theme, Sounds, Background, Window, Cards, Text, Icons, Falling Particles and Skin Walker, each on its own card.
- **Your skin's face** is shown as the logo and in the profile spot (Minecraft's default skin until yours has loaded).
- **Fast**: corners, circles and shadows are drawn from small cached pictures and text layout is remembered, so the menu sends about 5x fewer draws to the game each frame.
- **Sounds** (Customize > Sounds): 6 sound packs (Soft, Crisp, Bubble, Glass, Wood, Retro), volume, pitch, a preview button, and separate switches for switch-on/off, clicks, slider ticks, open/close and hover sounds.
- **Backgrounds** (Customize > Background): Dim, Gradient, Aurora, Fireflies, Starfield, your own Picture, or None, with a darkness slider.
- **More options** in Customize > Window and Cards: open animation (Slide, Zoom, Fade, None), card size (Comfortable / Compact), columns (Auto, 2, 3, 4), icons and descriptions on or off.

### Skin Walker (Customize > Skin Walker)
- One second after you open the menu, a real 3D model of your skin walks in from the right edge of the screen and stops in the free space right of the menu window (never on top of it).
- It turns smoothly to face you (true 3D: head, body, arms and legs with both skin layers and shading, arms attached at the shoulders), then strikes a pose.
- 12 poses: **Aura** (floats up with glow, flames and sparks in your theme colours), Wave, Calm, Point (points at the menu), Dab, Victory, T-Pose, Zombie, Salute, Crossed arms, Dance, or **Random** (a different one every time).
- Your name then floats up into a Minecraft-style name tag (the game's font on the see-through dark box). Long names get a smaller tag so it never covers the menu.
- Click your skin to strike the pose again. Options: on/off, pose, aura effects, show name, size, and the delay before it walks in.

### Icons (Customize > Icons)
- **Outline** (the clean icons), **Minecraft** (a real item for every module and page: a diamond sword for Combat, an end crystal for Crystal, a totem for totem modules and so on, drawn by the game so texture packs show) or **Emoji** (colourful Noto emoji).
- Icon tile: Rounded, Circle, Square or None (with None the icon itself lights up when the module is on).

### Falling Particles (Customize > Falling Particles)
- Things that drift down behind (or in front of) the menu: Clovers, Halloween (pumpkins, ghosts, bats, candy), Christmas (trees, presents, bells, reindeer, cookies), Snow, Sakura, Autumn leaves, Valentine, Easter, Party, Space, Ocean, Money, Fruits, Africa and Minecraft items.
- **Seasonal** (default) picks the theme from today's date (Halloween in October, Christmas in December...).
- Amount, fall speed, size, opacity, spin and layer.

### Visuals (Client > Visuals)
- Opens the Afrika Visuals menu: 454 texture styles in 25 groups (crystals, totems, pearls, anchors, swords, axes, maces, armor, golden apples, sky and more), each with a preview picture.
- Left-click a style to equip it (one per group), right-click for a big preview (use the arrows or arrow keys to flip through). Equipped shows your whole look; Shuffle all, Clear and Random help you try things.
- Nothing changes until you press Apply on the bar at the bottom. Undo goes back to what is applied. Apply builds one resource pack called AfrikaVisuals in your resourcepacks folder and turns it on.
- Presets save your whole look and load it again in one click.
- Search box at the top searches every style (just start typing).
- Customization: menu background (the game, or up to 8 of your own PNG/JPG pictures, drop them on the window or paste a path, darken slider), live preview, window look (Minecraft, Classic, Modern UI, Glass, Flat), size, window opacity, accent colour (swatches or hex), 19 themes (Glass, Halloween, Crimson, Amethyst, Amber, Sakura, Mono, Ocean, Emerald, Sunset, Midnight, Neon, Christmas, Lava, Ice, Gold, Afrika, Galaxy, Mint) with optional decorations (pumpkins, bats, snow, petals, stars, bubbles, embers) and Reset the look.
- Installing the styles: close Minecraft and run `tools\install-visuals.bat` once. It downloads the packs from https://github.com/BestOfAfrika/afrika-packs/releases/tag/v1 (about 600 MB), keeps only the packs, previews and index.json (no jars, no scripts), checks every pack against its SHA-256 fingerprint and puts them in `.minecraft\config\afrikaclient\visuals\`. For another launcher, drag your instance's .minecraft folder onto the .bat. If you already downloaded the zip, put it next to the script as `afrika-packs.zip` and it is used instead of downloading.
- Connections: none. The Visuals menu itself never goes online; it only reads the files the installer put on your PC. Every pack is checked against its fingerprint again before it is applied, and only texture files are copied into the pack.
- Sky styles need a sky mod that reads OptiFine skies (for example FabricSkyBoxes).

### Auto Safe Anchor (Crystal)
- Works together with Anchor Macro. With Anchor Macro on and Auto Safe Anchor off, Anchor Macro works exactly as before.
- With both on: when the anchor is charged, it places a glowstone block on the side of the anchor facing you, then blows the anchor up, so the glowstone takes most of the blast instead of you.
- Settings: Speed (Legit, Normal, Fast, Instant), Place Delay, Explode Delay, Only When Safe (won't blow it up if the glowstone can't go down, for example when you stand right next to the anchor) and Switch Back.
- Needs glowstone in your hotbar.

### Totem Particles (Clover > Visual)
- Totem pop particles in your own colours: add 2, 3 or up to 8 colours (+ Add a colour / Remove), shown as a random mix, fading through the list, or rainbow.
- Amount multiplier (0.25x to 5x), size, speed, how long they last, and glow in the dark.
- Uses AfrikaClient's own white sparkle pictures, so the colours beat texture packs that recolour totem particles, and the colour is set again every tick so other mods do not override it. Only you see it.

### Sounds
- 13 sound packs now: Soft, Crisp, Bubble, Glass, Wood, Retro, plus new **Pop, Keys** (mechanical keyboard), **SciFi, Chime, Drop, Arcade** and **Minecraft** (the game's own click, note block and chest sounds).

### Fixes
- Spotify Lyrics work again: they are looked up on lrclib.net (a free lyrics site). Only the song name, artist, album and length are sent, nothing about you. Switch off "Find lyrics online" in the mod to use only your own `.lrc` files in `config/afrikaclient/lyrics/`.
- Themes now recolour the whole menu (window, sidebar, cards, borders) and the background behind it, not just the accent.
- The logo text can no longer be changed; the client is always called AfrikaClient.
- The key that opens the menu no longer types its letter into the search box. While you type in the search, the menu key types instead of closing the menu.

### Clover section (legit mods)
All 68 Clover mods are built in under **Clover** in the sidebar: Keystrokes, CPS, FPS, Armor/Potion status, Spotify HUD and lyrics, Freelook, Zoom, Motion Blur, Shulker Preview, Time Changer, Crosshair, Block Outline and the rest. Each one keeps its own settings and toggle key.
- **HUD Editor** (button at the top of the Clover pages) to drag the HUD boxes around.
- **Capes**: the 42 capes plus "Your own cape" from a PNG. Hover a cape to try it on (F5 to look at yourself).
- **Favourite servers** tab in the server list, plus profiles and "enable/disable all", in Clover > Utility > Clover Options.
- **Not included** (as asked): emotes, kill effects, wings, hats, auras, the Clover badge, the Clover title screen. (The account switcher was added in 2.1.)

### Nametags (Clover > Visual)
- **Clear background**: only the name, no dark box behind it (or Vanilla, or your own opacity).
- **Name colour**: Default (keeps team colours), White, Custom or Rainbow, and Bold.
- **Extra info**: health (number, hearts or percent, coloured green/yellow/red), ping and distance.
- **Show my own name tag** in third person (F5).
- Only changes what you see; nothing is sent to the server.

### Modules sorted properly
Modules were moved into categories that match what they do (for example Auto Clicker moved to Combat, the totem and potion modules to Defense, Ping Spoof and Fake Lag to Network, Freecam to Render). The source folders match the menu now.

## Security cleanup
Everything that downloaded files, went online, hid the client, or faked file details was removed:

| Removed | What it did |
|---|---|
| Self Destruct module + `Utils.replaceModFile` | Downloaded a jar from the internet and overwrote the client's own file with it |
| Jar timestamp code in `AfrikaClient` | Saved the jar's modified date so it could be put back later to cover traces |
| `EncryptedString` (386 uses) | Scrambled every text string in memory so it couldn't be found by a scanner |
| Hidden config folder | Settings were stored in `%TEMP%/UJHfsGGjbPfVZ/a.json` |
| Fake `immediatelyfast` asset folder | Assets now live under `assets/afrikaclient` |
| Clover Microsoft sign-in (account switcher) | Sent login tokens to Microsoft/Xbox servers |
| Clover badge | Set a hidden bit in the skin-settings packet that told servers you use Clover |
| Old `build/`, `.gradle/`, `logs/` folders | Old compiled jars that couldn't be checked against the source, and a log with your username |

After the cleanup, **no file contains a web address** and nothing opens a network connection. The only programs the client starts are local ones for the Spotify HUD (PowerShell on Windows, `playerctl` on Linux, `osascript` on Mac) to read the song that's playing, plus your file explorer when you click "Open folder" in its settings. The one feature that can go online is Spotify lyrics (lrclib.net), and it says so in its own section above with a toggle. The Visuals installer `tools/install-visuals.ps1` downloads from one fixed GitHub address and is not part of the mod. Some antivirus programs flag a mod that starts PowerShell. That's this script: `assets/afrikaclient/spotify/media_windows.ps1`.

`gradle/wrapper/gradle-wrapper.jar` contains only Gradle's own classes. If you want to be sure, compare its SHA-256 with the list at gradle.org/release-checksums, or run `gradle wrapper` to make a fresh one.

## Settings files
Everything is in `.minecraft/config/afrikaclient/`:
- `profile.json`: Afrika modules and their settings (stored by name, readable).
- `clover.properties`, `profiles/`, `spotify.json`, `spotify/`, `lyrics/`: Clover mods.
- `visuals/` (packs, previews, your menu pictures in `visuals/backgrounds/`), `visuals-selection.properties`, `visuals-presets.properties`, `visuals-customize.properties`: Visuals.

The first time 2.0 starts, it copies your settings from the old hidden file. The menu's own look starts fresh because those options changed. The old file isn't deleted; you can remove `%TEMP%/UJHfsGGjbPfVZ` yourself.

## Build
1. Install JDK 21.
2. In the project folder run `gradlew build` (Windows: `gradlew.bat build`).
3. The jar is in `build/libs/`. Put it in `.minecraft/mods/` with Fabric API `0.141.3+1.21.11`. **Remove the separate Clover Client jar** because Clover is built in now and both would load twice.

## Credits
- Emoji: Noto Color Emoji by Google (Apache 2.0 / OFL), see assets/afrikaclient/emoji/LICENSE.txt
- Icons: Material Design Icons by Pictogrammers (Apache 2.0), see `assets/afrikaclient/icons/LICENSE.txt`.
- Font: Inter by The Inter Project Authors (SIL Open Font License 1.1), see `assets/afrikaclient/font/INTER_LICENSE.txt`.

## Notes
- Module keybinds now only work in game (not while typing in chat or a menu). The menu key still closes the menu.
- Versions in `gradle.properties` were raised to match Clover's (yarn `build.6`, loader `0.19.3`, Fabric API `0.141.3`).
