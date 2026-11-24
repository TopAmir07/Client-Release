# TopClient mod: plan

## Status (1.1.0)
1.1.0 adds the main menu redesign, Fullbright, 1.7 Animations, Particle Changer, crosshair editor
screen, clear HUD, the Lunar-style HUD editor, Screenshots and Fabric Mods screens and the
Multiplayer buttons. Compiled and mixin-checked against 26.2; not tested in-game.

## Status (1.0.0)
Steps 0-8 and 9a-9e are done: theme system, widgets, title/pause bar, mod menu (cards, profiles,
filter/search/sort, colour customisation, waypoints tab) and the mods below. Everything was
compiled against Minecraft 26.2 and every mixin was checked against the game, but nothing has
been tested in-game yet.

Added in TopClient: Toggle Sneak, Toggle Status, Chat Filter, Crosshair editor, Fog, Low Fire &
Shield (+ pumpkin), Weather Changer, Particles, Waypoints, Minimap, Boss Bar, Hit Counter, Hand
Position, Chat Snippets, Timer, Performance; Zoom rewritten (scroll zoom), Keystrokes CPS option.
Removed: Target HUD (PvP advantage).
Still LOCKED (planned): Replay, 1.7 Animations, Hit Colour, Calculator. Not done: tab list and
hotbar customisation, chat background/size (the game's own chat settings cover these).


## Starting point (analysis)

The TopClient mod is a fork of the existing 26.2 client mod. That mod is ~27,000 lines and already has:

| Area | What exists | Where |
|---|---|---|
| Module system | ~70 "clover" modules (`Module`, `HudModule`, `Setting`) and 5 older modules (`module/…`) | `clover/module`, `module/` |
| One model for the menu | `GuiModule` / `GuiSetting` wrap both module systems (toggle, slider, range, mode, key, text, colour, header, action) | `gui/model` |
| Menu on Right Shift | `ClickGui`, an immediate-mode screen drawn in real screen pixels (`Draw`, `UiFont`, `Icons`) | `gui/ClickGui.java` |
| HUD editor | Drag, snap and scale for every `HudModule` | `clover/screen/HudEditorScreen.java` |
| Profiles | One `profile.json` for the older modules plus `clover.properties` for everything else | `managers/ProfileManager`, `clover/Config` |
| Accounts | In-game Microsoft account switcher | `clover/accounts` |
| Title screen | Custom title screen | `clover/ui/AfrikaTitleScreen.java` |
| Themes | `GuiTheme` (menu) and `clover/ui/Theme` (HUD), both with hard-coded colours | `gui/GuiTheme`, `clover/ui/Theme` |

What doesn't fit TopClient and gets removed in step 0: the old brand (names, logo, mod id, packages, assets), the owner name tag that downloads a list from GitHub (a network connection), and the importer for an old temp-folder config.

**Testing:** this workspace can compile against the real 26.2 game jars and check every mixin target with `MixinVerify`. It can't start Minecraft. So every step below is **compiled and mixin-checked, not tested in-game** unless it says otherwise.

## Steps (one commit each, compilable after each)

0. Fork and rebrand: `dev.topclient.client`, mod id `topclient`, assets `topclient`, config folder `config/topclient`. Owner tag and legacy import removed. LICENSE and credits kept.
1. **Theme system:** one `TopTheme` object for every colour (Obsidian default: #000000 OLED, dark purple accent), presets, rainbow, separate status colours. Saved in the config.
2. **Widgets:** `Widgets` and `Ui` context (hit-testing without per-frame object churn), Button, Chip, ModCard, Slider, Toggle, ColorPicker (HSV square + hue + hex + RGB sliders), Scrollbar, TextField.
3. **Main menu overlay:** logo, then [cosmetics] [MODS] [profile] on the title screen and the pause screen.
4. **Mod menu skeleton:** centred window at about 60% width, header (wordmark, MODS / SETTINGS / WAYPOINTS tabs, X). Opens on Right Shift instead of the old menu, which stays reachable as "Classic menu".
5. **Cards:** 3-column grid, star favourite, OPTIONS + gear, status bar (ENABLED / DISABLED / LOCKED).
6. **Profiles:** sidebar list, rename, save as new, profile sharing (copy/paste code), EDIT HUD LAYOUT. Per-profile saving of module state and HUD positions.
7. **Filter and search:** ALL, NEW, 8 categories, live search, sort (A–Z / favourites first / enabled first), grid or list view.
8. **Colour customisation:** SETTINGS → Appearance with picker, presets, opacity/blur, rainbow, status colours, reset.
9+. **Mods in the requested order:** core group, chat/scoreboard/crosshair, visuals, waypoints/minimap, rest.

## Categories

The 8 requested categories are a mapping in `ModCategories` (module id → category). The module classes stay as they are.

## Exists / missing

✓ = exists, ◐ = exists but needs the requested extras, ✗ = missing, 🔒 = shown as LOCKED.

### HUD
| Requested | Status | Existing module |
|---|---|---|
| FPS | ✓ | fps (+ FPS Graph) |
| CPS left/right | ✓ | cps |
| Ping | ✓ | ping |
| Coordinates | ✓ | coords (+ chunk, nether, death coords) |
| Direction / compass | ✓ | direction, compassbar |
| Clock | ✓ | clock, analogclock, date |
| Memory | ✓ | memory |
| Time of day | ✓ | worldtime, daycycle |
| Biome | ✓ | biome |
| Keystrokes (WASD, mouse, space) | ◐ | keystrokes, but no CPS inside the keys |
| Armor status | ✓ | armor |
| Item counter / item info | ✓ | itemcount, helditem |
| Potion effects | ✓ | potions |
| Saturation | ✓ | saturation |
| Combo counter | ✓ | combo |
| Scoreboard | ✓ | scoreboard (position, background, hide numbers) |
| Bossbar | ✗ | |
| Tab list | ✗ | |
| Chat (background, size, history, timestamps, filter) | ◐ | infinitechat, chattime; no background, size or filter |
| Own nametag with background | ✓ | nametags |
| Crosshair editor | ◐ | crosshair is colour only; shape, size and dynamic are missing |
| Hotbar / hand position | ✗ | |
| Server address, playtime, session | ✓ | server, playtime, session, sessionstats |
| Reach display | ✓ | reach (display only) |
| Hit counter | ✗ | |
| Damage indicator | ◐ | health (health numbers) |

### Visuals
| Requested | Status | Existing module |
|---|---|---|
| Zoom (key, scroll, smooth) | ◐ | zoom has key + smooth; no scroll zoom |
| Freelook | ✓ | freelook |
| 1.7 animations | ✗ | |
| Item physics | ✓ | itemphysics |
| Block overlay | ✓ | blockoutline |
| Hitbox display | ✗ | |
| Motion blur | ✓ | motionblur |
| Time changer | ✓ | timechanger |
| Weather changer | ✗ | (weather is a display) |
| Fog | ✗ | |
| Low fire / shield / pumpkin | ✗ | |
| Totem overlay | ✓ | totemparticles |
| Particles | ✗ | |
| Hit colour / damage tint | ◐ | nohurtcam (no shake) only |
| Capes / cosmetics (local) | ✓ | capes, capephysics |
| Waypoints, minimap | ✗ | |

### Utility
| Requested | Status | Existing module |
|---|---|---|
| Toggle sprint | ✓ | togglesprint |
| Toggle sneak | ✗ | |
| Replay | 🔒 | LOCKED (not realistic here) |
| Screenshot helper | ✓ | built in (rename after screenshot) |
| Text snippets / quick-chat | ✗ | messages only, typed by you |
| Stopwatch | ✓ | stopwatch |
| Timer | ✓ | timer |
| Spotify lyrics | ✓ | spotify, lyrics |

### Performance
| Requested | Status | Existing module |
|---|---|---|
| FPS boost, entity culling, animation reduction, fast chat, lazy HUD, render-distance presets | ✗ | Missing. Planned as one "Performance" module with sections. Lazy HUD becomes an internal change. |

### Existing extras with no slot in the list (kept, sorted into categories)
Speed, player count, day counter, totem counter, arrow counter, durability alert, weather, colour boost, horse stats, shulker preview, slot highlights, low-health warning, pickup notifier, movement status, light level, Y level, rotation, XP, kills, server TPS, entity count, nearby players, dimension, inventory space, break reminder, auto GG, target HUD, Spotify HUD.

**Server rules:** freelook, zoom, minimap, auto GG and reach display get a note in their description saying some servers don't allow them.
