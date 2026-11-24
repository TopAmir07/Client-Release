# TopClient (unreleased)

- **Compact Chat** (Utility): identical repeated chat lines are joined into one line with a counter, like `Hello (5x)`. Settings: grouping time (5-600 s), players never compacted, words never compacted. Different players or different texts are never joined. Switch it off to see every line again. Compiled-checked only for syntax; not tested in-game.
- **FPS** (HUD): new options frame time (ms) and min / avg / max FPS over the last 5 seconds, measured from real drawn frames, plus an update interval. **Ping**: optional min / avg / max and an update interval; it still shows the latency the server reports (tab list value).
- **Settings and config reads are cached** (performance): every setting, "is this mod on", HUD position and equipped cosmetic used to be looked up in the config and parsed from text on every call, many times per frame, with new strings each time. They are now read once and refreshed only when the config actually changes (`Config.version()`). The Clock builds its time format once.
- **HUD text is rebuilt less often**: FPS and Ping reuse their text between updates instead of rebuilding it several times per frame.
- **Calculator removed**: the locked Calculator placeholder and its category entries are gone.

# TopClient 1.1.0 (Minecraft 26.2)

- **New main menu**: dark moving background (soft theme-coloured glows, drifting particles that follow the mouse), the TopClient wordmark, big buttons that slide in one after another (Singleplayer, Multiplayer, Integrated Mods, Options, Quit), your skin on the right with Switch account, and a bottom bar with **INTEGRATED MODS** in the middle plus Screenshots, Crosshair, HUD editor, Cosmetics, Fabric mods, Accounts, Language and Background. Backgrounds: moving, Minecraft panorama or plain dark; the classic menu is still there.
- **Fullbright** (Visuals): see in caves and at night, like Night Vision, with a Strength slider.
- **1.7 Animations** (Animations), no longer locked: swing while eating/drinking (block-hit), sword block pose on right click, and no item dip after hits. Each part has its own switch.
- **Particle Changer** (replaces Particles): 18 kinds, each with its own on/off and amount (0/25/50/75/100%): fireworks, crits, enchant crits, sweep, damage hearts, explosions, totem, potions, block break, block hit, smoke, flames, water, lava, portal, redstone, hearts and everything else. Turning fireworks off never touches crits.
- **Crosshair editor** (Lunar style): presets, built-in shapes, draw your own on a 15x15 grid (pencil, eraser, mirror, invert, clear), save, edit and delete your crosshairs, colour swatches and a full colour picker, opacity, size, gap, thickness, outline, centre dot, dynamic, live preview on four backgrounds.
- **Clear HUD**: every HUD mod has no background box by default (background opacity 0%, adjustable). New Text opacity and Text outline settings. Keystrokes have Clear (thin outline), Filled or Letters only keys. Your old backgrounds are switched off once when you update.
- **HUD editor** (Lunar style): top bar with HUD Mods list (switch each one on/off), Snap (centre and edges, with guide lines), Reset layout, Save layout (to your profile), Mod menu and Done; reset one mod; right click a mod to hide it; a pretend game screen when opened from the main menu.
- **Screenshots**: gallery grouped by day, big viewer with arrows, Open, Copy path, Rename and Delete (asks first). From the main menu, the pause menu, the mod menu and the Multiplayer screen.
- **Fabric Mods**: every loaded mod with its icon, version, description, authors and licence; built-in parts marked; Configure through Mod Menu when it's installed; Open mods folder.
- **Multiplayer screen**: Fabric Mods, Cosmetics, Screenshots and TopClient Mods buttons.
- **Pause menu bar**: HUD editor and Screenshots buttons added.
- **Nametags**: a TopClient mark before your own name, drawn only on your screen.

# TopClient 1.0.1 (Minecraft 26.2)

- HUD editor: the key press that opened it no longer closes it straight away.

# TopClient 1.0.0 (Minecraft 26.2)

First TopClient release, by TopAmir_. Built on the AfrikaClient 26.2 mod (see README for credits).

- **New mod menu (Right Shift)**: centred window with MODS / SETTINGS / WAYPOINTS tabs; mod cards with favourite star, OPTIONS + gear and an ENABLED / DISABLED / LOCKED bar; categories (HUD, PvP, Performance, Visuals, Information, Utility, Animations, Miscellaneous), NEW, live search, sorting, grid or list view.
- **Profiles**: Default, plus as many as you like. Each one saves every mod's state, settings and HUD position. Rename, delete, save as new, and share as a code.
- **Colours**: Obsidian theme by default (pure black, dark purple). Presets, an HSV colour picker with hex input and R/G/B sliders, gradient and rainbow modes, separate ENABLED/DISABLED/LOCKED colours, window opacity, background darkening and blur.
- **Title and pause menu bar**: TopClient logo with Cosmetics, MODS and Profile buttons.
- **HUD editor**: drag a mod to move it; drag the arrow on its corner to resize it.
- **New mods**: Toggle Sneak, Toggle Status, Chat Filter, Crosshair editor, Fog, Low Fire & Shield, Weather Changer, Particles, Waypoints, Minimap, Boss Bar, Hit Counter, Hand Position, Chat Snippets, Timer, Performance. Zoom now scrolls further and goes past the FOV slider. Keystrokes can show CPS in the mouse keys.
- **Removed**: Target HUD (a PvP advantage).
- Legit only: no auto-clicker, reach, hitboxes or aim help. Mods that some servers don't allow say so in their description.
