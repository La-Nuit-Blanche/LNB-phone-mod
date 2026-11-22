# LNB Phone: art direction

Version 5. The user validated a storyboard canvas built from version 4, and ten development tasks then built the phone from it. Along the way developers and reviewers found passages that were wrong, contradicted each other, said nothing or could not be built as written, and decisions were made. Version 5 brings the document in line with what was built; where the two differ, the code is right. The Feed app (section 9) is not built yet and will be built from this document. Every value here is final unless the user changes it.

## Changes since version 4

- **Rendering.** Shapes are computed at the screen's real resolution instead of coming from sprites (8.1, 16.2). Icons are exported at one density per GUI scale, now scales 2 to 9 (8.1, 16.2). The `person` icon exists at 12, 20 and 28 dp, one per avatar size (8.6, 16.2). App names are truncated when drawn instead of capped at 16 characters (7.2, 13.3).
- **GUI scales 2 and up.** The phone supports every integer GUI scale from 2, not only 2 to 4: Minecraft's Auto picks 5 on 1920 x 1200, 6 on 2560 x 1440 and 9 on 4K (Units, 8.1, 16.2). Scale 1 stays unsupported for apps; there, also no notification banner and no peek (the chime and the badge only), and Backspace goes home (8.1, 10.2, 2.3, P-15).
- **Pixel rules at every scale.** Layout arithmetic of the shell rounds halves to even, the only rule that reproduces the positions of P-3.2 and P-12.4 (2.4, 14). A 1 dp hairline is 2 real pixels at GUI scale 3 and the 3 dp tab indicator is 4 pixels tall there (8.1, 8.7).
- **Corner phone and hotbar.** The bottom-right phone overlaps the hotbar in any window narrower than about 474 GUI px, which includes the default 854 x 480 window at GUI scale 2. Accepted as it is (2.4).
- **Keys.** The phone reacts once per real key press, never to key repeats; inside the phone, Esc, the phone key and Backspace act on the first press only, while a focused text field still receives repeats. While a text field has the focus the phone key and Backspace don't close or go back, and typed keys never leave the phone (2.3).
- **Opening.** The device slides over the HUD for 250 ms and the screen opens when the slide ends; the player can still move during the slide. Another screen opening during the rise sends the phone back down. The crosshair is hidden while the phone is open, and F1 hides the sliding device (2.3, 14, P-1, P-2).
- **Home pages.** The mouse wheel turns one page per whole notch (2.3).
- **Inventory button.** With unread notifications the button opens the home screen only if the phone was put away on home or on an app's root screen; deeper in an app it reopens on that screen and shows the newest unread notification as the in-phone banner. This settles a contradiction with 2.3 (the phone keeps its screens and drafts until disconnect) in favour of drafts. Opening the phone closes the inventory properly (crafting grid and cursor items go back) and a fresh inventory opens when the phone closes. With the recipe book open the book covers most of the button: the covered part neither lights up nor takes a click, and the badge sits under the book. No button on the creative inventory (11, P-4, P-5, 19).
- **Peek and banner.** The peek is two layers: the device stays below the hotbar, the banner is drawn above chat lines; F1 hides the peek and the sliding phone. No peek while another screen is open (chat, a container, the pause menu, the death screen): chime only, the badge waits; a screen opening during a peek sends it down. Several notifications at once: the last one shows and the chime plays once. A pressed banner freezes until released; a banner or peek whose notification the server clears goes away early; clicking the banner removes it at once (10.2, 10.3, 6.1).
- **Read in place.** A notification whose target is already on screen gives no banner, the chime plays, and it counts as read. Reopening the phone onto an app left open keeps that app's badge, except for notifications the shown screen reports as visible, which are read in place. Notifications received while offline show as badges at login, with no peek and no chime (10.2, 10.3, 6.1).
- **Banner text.** Emoji are dropped and each run of other characters the font lacks becomes one `?`; the sample texts of P-8.3 and P-11.2 now end where the two lines of 10.1 end (10.1).
- **Data rules** that the document never stated: 50 notifications per player shared by all apps, the oldest of the busiest mod dropped when full, 30 days of life, titles cut to 128 characters and texts to 320, a per-mod rate backstop (new 10.4).
- **Sound.** The phone's sounds are its own sound events, each reusing the vanilla sound the document named at the same volume and pitch. Only the notification chime has a subtitle (`Phone notification` / `Notification du téléphone`); the interface sounds have none (15).
- **Text and keys in apps.** The 280-character limit counts code points after NFC normalisation (an accented letter is 1, an emoji 1, a flag 2); the counter and the Publier button use the same untrimmed count and the text is sent trimmed. Enter makes a new line in a text area and Ctrl+Enter submits; Backspace does nothing while a menu or dialog is open; Tab cycles a menu's or dialog's items; a menu that fits neither below nor above its button covers it; a double click selects a word; selected text has the new `selection` colour `#3A8DF066` (8.3, 8.7, 9.7, 9.8, 2.3).
- **Feed details.** A 17-character handle does not always fit its line: `@` plus sixteen `W` is about 205 dp, wider than the 188 dp column, so the handle line truncates too (9.2, 9.3, 19). `heart_filled` is drawn filled and stroked so that it keeps the outline heart's size (8.6). Avatars: a face is resolved from the player's UUID and name, and the default skin shows until it is known and for players the session service does not know (8.7, 9.2).
- **App glyphs and fonts.** A glyph that is not 16 x 16 is fitted: a square multiple of 16 is sampled down, a 16-wide strip shows its first frame, anything else shows the bare tile (7.2). Inter has no emoji or non-Latin glyphs: such characters show one missing-glyph box each in apps; bundling an emoji font is a new open question (8.4, 19).
- **Settings.** Section 12 places the rows 49 dp apart with separate dividers at `y 193` and `y 242`, and 8.7 says where a row's own divider sits. The three settings are stored per Minecraft account in `config/lnbphone-client.json` on the player's computer (2.4, 8.7, 12).
- **Language.** French is shown for the game languages `fr_fr`, `fr_ca` and `fr_ch`; everything else gets English (13.1).

## History

- **Version 4:** names swapped (bold real name, grey `@username` handle, two-line post header); pinning (3 at most) and deleting (own posts and replies, operators any); the language rule (English by default, French for French game languages, mockups in French); interaction flows in a new section 17, with the mockup brief and the open questions moved to 18 and 19; the kit components and states they need (danger button, loading button, menu, dialog, tooltip, error snackbar, post highlight, over-limit highlight, more and pin buttons); explicit navigation rules (Esc closes the topmost menu or dialog, otherwise the phone; a click outside closes it; the phone reopens where it was); the banner without a time and with an event title; private lines on own anonymous posts and the unseen-replies marker; the "Feed indisponible" state and new-post arrival in both feeds; proofreading fixes.
- **Version 3:** Annonces / Global tabs with Annonces first; fixed 48 dp tab indicator; French and English string table; identity from LNB Warden; operator line on anonymous posts; like and reply confirmed; thread screen; announcers and the announcer badge.
- **Version 2:** one look (Slate); pixel shell with a smooth app layer in dp; the Feed app; smooth notification banner; status bar matches the app background inside apps; moon moved; bottom-right placement by default with a Settings switch.
- **Version 1:** three variants of the phone; the user chose C "Slate".

## Units and coordinates

- **GUI px**: the unit Minecraft multiplies by the GUI scale (2x and up: Minecraft's Auto setting gives 5 on 1920 x 1200, 6 on 2560 x 1440 and 9 on 4K). Used for the pixel shell.
- **dp**: the unit of the smooth app layer. **2 dp = 1 GUI px.** At GUI scale `s`, 1 dp is `s / 2` real screen pixels: 1, 1.5 and 2 at scales 2, 3 and 4, 2.5 to 4.5 at scales 5 to 9.
- Colours are sRGB hex. `#RRGGBBAA` is a colour with alpha.
- **window** `(wx, wy)`: GUI px, origin at the top-left of the game window. `W`, `H` are the window size in GUI px.
- **device-local** `(dx, dy)`: GUI px, origin at the top-left pixel of the 140x228 device.
- **screen-local** `(sx, sy)`: GUI px, origin at the top-left pixel of the 128x196 screen = device `(6, 12)`.
- **viewport-local** `(vx, vy)`: GUI px, origin at the top-left of the app viewport = screen `(0, 11)`.
- **app-local** `(x, y)` in dp: origin at the same point as viewport-local. The viewport is **256 x 370 dp**.
- Ranges are inclusive: `x = 6..133` means pixels 6 through 133. Rects given as "at (x, y), w x h" are top-left plus size.

---

## 1. Concept

**"A pocket gadget from the overworld, running a modern OS."** The device is a chunky pixel-art handheld in the reference's tablet proportions: speaker slot, round home button, status bar, a grid of square pixel tiles, a Minecraft landscape behind them. Behind its glass runs a crisp, modern dark interface with smooth type and rounded shapes. The contrast is deliberate: the shell says "Minecraft object", the apps say "real phone".

How it stays distinct from the felt-and-gold kit (lnb-quests / LNB Economy):

| Felt-and-gold kit | LNB Phone |
|---|---|
| Warm palette: felt green `#102A20`, gold `#B6904C` / `#E8CA84`, crimson buttons | Cool slate and graphite with one accent, **Ping Blue `#3A8DF0`** |
| Textured felt, ornate frames, brackets, clasps | Pixel shell with a 1 px bevel; smooth flat app surfaces |
| Minecraft bitmap font everywhere | Bitmap font on the shell only; Inter inside apps |
| Panels float on the screen | A physical device rising from the screen edge |

Blue is the complement of gold, so the phone sits next to the quest book without fighting it. The two kits share only the 1 px near-black outline on pixel parts and the red count badge.

---

## 2. Shell metrics (pixel art, GUI px)

### 2.1 Device

- Device: **140 x 228**.
- Screen: **128 x 196** at device `(6, 12)`: `dx 6..133`, `dy 12..207`.
- Status bar: screen `sy 0..10` (128 x 11).
- App viewport: **128 x 185 GUI px = 256 x 370 dp**, screen `sy 11..195`.
- Bezel: left 6, right 6, top 12, bottom 20 (each includes the outline and the 1 px screen rim).

```
dx: 0    5 6                                   133 134  139
dy 0    ..OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO..     outline, 3-step corners
dy 1    .H  highlight row ................................L.
dy 5-6  |            [==== speaker 18x2 ====]   [LED]     |  speaker dx 61..78, LED dx 84..85
dy 11   |    r r r r r r r r r r r r r r r r r r r r r    |  screen rim (1 px ring)
dy 12   |    r +--------------------------------+ r       |  screen top
dy 12-22|    r | status bar 128x11 (pixel)      | r       |
dy 23   |    r |--------------------------------| r       |
        |    r | app viewport 128x185           | r       |
        |    r | = 256x370 dp (smooth layer)    | r       |
dy 207  |    r +--------------------------------+ r       |  screen bottom
dy 208  |    r r r r r r r r r r r r r r r r r r r r r    |  screen rim
dy 212  |                    ( home 12x12 )               |  home button dx 64..75, dy 212..223
dy 226  .L  shade row .....................................
dy 227  ..OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO..     outline
```

| Part | Pixels |
|---|---|
| Outline (`outline`) | column `dx 0` and `dx 139`, row `dy 0` and `dy 227`, plus corner stairs |
| Highlight (`body-hi`) | row `dy 1` (`dx 3..136`), column `dx 1` (`dy 3..224`), plus inner corner pixel `(2, 2)` |
| Shade (`body-lo`) | row `dy 226` (`dx 3..136`), column `dx 138` (`dy 3..224`), plus inner corner pixel `(137, 225)` |
| Body (`body`) | everything else between outline and screen rim |
| Screen rim (`screen-rim`) | 1 px ring `dx 5..134`, `dy 11..208` |
| Speaker (`speaker`) | `dx 61..78`, `dy 5..6` (18 x 2), square ends |
| Notification LED | `dx 84..85`, `dy 5..6` (2 x 2): `body-lo` with no unread, `accent` when unread (blinks, see Motion) |
| Home button | 12 x 12 at `dx 64..75`, `dy 212..223` |

### 2.2 Corner steps

Device corners are a 3-step stair. Top-left corner, 6 x 6 (O outline, H body-hi, B body, `.` transparent):

```
      x: 0 1 2 3 4 5
y0:      . . . O O O
y1:      . O O H H H
y2:      . O H B B B
y3:      O H B B B B
y4:      O H B B B B
y5:      O H B B B B
```

The other corners mirror this outline. Bottom-right uses L (body-lo) where top-left uses H. Top-right and bottom-left use B for the diagonal pixel where highlight meets shade.

| Pixel element | Corner |
|---|---|
| Device | 3-step (above) |
| Screen | 1-step: after the screen content (including the app layer) is drawn, paint the 4 screen corner pixels `(6,12)`, `(133,12)`, `(6,207)`, `(133,207)` in `screen-rim` |
| App icons, home badge, hover ring | 2-step (below) |

2-step corner (top-left, O outline, F fill):

```
      x: 0 1 2 3
y0:      . . O O
y1:      . O F F
y2:      O F F F
y3:      O F F F
```

### 2.3 Home button, navigation and input

**A hardware home button in the bottom bezel, plus a back button in the app header.** The round button is the most recognisable part of the reference, costs no screen space and is a large fixed target for a mouse.

Home button, 12 x 12 (R = `home-ring`, F = `home-fill`, `.` = body shows through):

```
. . . . R R R R . . . .
. . R R F F F F R R . .
. R F F F F F F F F R .
. R F F F F F F F F R .
R F F F F F F F F F F R
R F F F F F F F F F F R
R F F F F F F F F F F R
R F F F F F F F F F F R
. R F F F F F F F F R .
. R F F F F F F F F R .
. . R R F F F F R R . .
. . . . R R R R . . . .
```

States: hover: R becomes `accent`. Pressed: R `accent`, F `body-lo`.

The open phone is a normal game screen: the mouse cursor is free and the player does not move while it is open. That starts when the slide up ends. During the 250 ms of the slide the device is drawn over the HUD, the game keeps the mouse, the player can still move, and the phone's screen opens when the slide ends. Closing gives the mouse back at once, and the player can move while the device slides down. Any other screen that opens during the rise (chat, the pause menu, a container, the death screen) sends the phone back down behind it. The crosshair is hidden while the phone is open, and F1 (hidden HUD) hides the sliding device.

**Key presses.** The phone reacts once per real key press, never to key repeats: holding the phone key opens the phone once and, held again, closes it once. Inside the phone, Esc, the phone key and Backspace act on the first press only. While a text field has the focus, the phone key and Backspace belong to the field: they neither close the phone nor go back, a held Backspace erases character after character, and no typed key goes on to the game or to other mods' key handlers. A focused text field still receives key repeats.

| Input | Action |
|---|---|
| Up arrow (phone closed) | Open the phone on the screen it was closed on (home on the first open of a session). One press, never its repeats |
| Up arrow during a peek | Open the phone on the notification's target screen |
| Up arrow (phone open, no text field focused) | Close the phone (first press only) |
| Esc | Close the topmost menu or dialog if one is open; otherwise close the phone (first press only). The current screen is kept for the next open |
| Click outside the device | Close the phone, in both placements (in the centred placement, a click on the scrim). Clicks on the bezel do nothing, except on the home button |
| Home button click | Go to the home screen (instant) |
| Header back button, or Backspace when no text field has focus | Back one screen in the current app (Backspace: first press only). On an app's root screen Backspace does nothing, and it does nothing while a menu or dialog is open. At GUI scale 1, where no app screen shows, Backspace goes to the home screen |
| Mouse wheel on home | One page per whole notch; the fractions a trackpad sends add up to a notch, and turning back starts the count over |
| Mouse wheel in an app | Scroll 36 dp per notch |
| Ctrl+Enter | Publish in Compose, send in the reply composer |
| Enter | In a single-line field: submit it. In a text area: a new line |
| Tab, Shift+Tab | Move the focus; while a menu or dialog is open it moves among that menu's items or that dialog's buttons only |

While it is put away the phone keeps its screen, scroll positions and drafts until the player disconnects. The inventory button can show the home screen instead (section 11), but only when that loses nothing: never from deeper than an app's root screen.

### 2.4 Placements

Same device, same size, same sprites in both. **Default: bottom-right.** Each player can switch to centred in the built-in Settings app (section 12). The choice is per Minecraft account and stored on the player's computer (section 12).

| Placement | Device top-left | Backdrop |
|---|---|---|
| Bottom-right (default) | `wx = W - 146`, `wy = H - 234` (6 px from right and bottom) | none, the world stays visible |
| Centred | `wx = floor((W - 140) / 2)`, `wy = floor((H - 228) / 2)` | full-window flat scrim `#0000004D`, no menu blur |

At the minimum window 320 x 240: bottom-right puts the device at `(174, 6)`, centred at `(90, 6)`. In a 480 x 270 window (1920 x 1080 at GUI scale 4): bottom-right `(334, 36)`, centred `(170, 21)`. The notification peek always comes from the bottom-right, whatever the placement.

In the bottom-right placement the device overlaps the hotbar in any window narrower than about 474 GUI px: the hotbar is 182 wide and centred, so its right edge `W / 2 + 91` is past the device's left edge `W - 146` when `W < 474`. That includes Minecraft's default 854 x 480 window at GUI scale 2 (427 x 240). Accepted as it is: the device is drawn over the hotbar there.

**Rounding.** Positions of the pixel shell are whole GUI px. Layout arithmetic that has to land on one (a device mid-slide or mid-glide, a peek mid-rise, the scrim's alpha) rounds a half to the even neighbour (0.5 to 0, 1.5 to 2, 38.5 to 38). It is the only rule that reproduces this document's own example values: the scrim half-way in P-3.2 is `#00000026` (38.5 of `4D`'s 77; rounding halves up would give `#00000027`) and the device half-way in P-12.4 is at `y 28` (28.5; half up gives 29). The smooth layer's own rule is different and is in 8.1.

### 2.5 Home spacing grid (pixel)

Base unit 2 GUI px. Shell spacings: 2, 4, 6, 8. Screen edge to home grid: 6.

### 2.6 Pixel typography (shell only)

Minecraft default font, never scaled: glyphs 5 px wide plus 1 px spacing, 7 px cap height, 9 px line height. Used for the clock, the home label line, home badge digits, the GUI-scale message and the inventory tooltip (vanilla tooltip). Shadow on for the clock and the label line, off in badges.

---

## 3. Shell palette (Slate)

| Role | Hex | Use |
|---|---|---|
| `outline` | `#0A0C10` | device, app icon frames, inventory button |
| `body` | `#2C323D` | device body |
| `body-hi` | `#434B5A` | top/left bevel |
| `body-lo` | `#1D2129` | bottom/right bevel, LED off |
| `screen-rim` | `#06080B` | ring around the screen, screen corner pixels |
| `speaker` | `#0A0C10` | speaker slot |
| `home-ring` | `#4F586A` | home button ring |
| `home-fill` | `#23282F` | home button centre |
| `status-bg-home` | `#00000066` | status bar strip over the wallpaper |
| `status-bg-app` | `#12151B` | status bar inside apps (= app `bg`) |
| `status-ink` | `#EEF1F5` | clock, lit bars, battery frame |
| `status-dim` | `#4F586A` | unlit signal bars |
| `accent` | `#3A8DF0` | LED, hover ring, inventory hover outline |
| `accent-hi` | `#74B4FF` | top row of the lit inventory screen |
| `badge` | `#E5483C` | home and inventory badge fill |
| `badge-hi` | `#FF7A6E` | badge pulse frame |
| `badge-lo` | `#7A1A13` | badge outline |
| `badge-text` | `#FFFFFF` | badge digits |
| `screen-off` | `#101317` | inventory button screen when idle |
| `glare` | `#3A404B` | glare pixels on the idle screen |
| `scrim` | `#0000004D` | centred-placement backdrop |

The app layer has its own tokens (section 8.3), built on the same slate values.

---

## 4. Status bar (pixel)

128 x 11 at screen `sy 0..10`. Background: `status-bg-home` on the home screen and in the peek, `status-bg-app` inside apps. Every element sits on rows `sy 2..8`. Text shadow on.

| Element | Position (screen-local) | Size |
|---|---|---|
| Signal | `sx 4..14`, `sy 2..8` | 11 x 7 |
| Clock | centred: `sx = floor((128 - textWidth) / 2)`, `sy = 2` ("14:13" is 25 px wide, so `sx = 51`) | text |
| Battery | `sx 111..123`, `sy 2..8` | 13 x 7 |

### 4.1 Clock: in-game time

In-game time, 24-hour `HH:MM`. Players already see real time on their OS; in-game time tells them when night falls.

- `ticks = dayTime % 24000`; `minutes = (ticks * 1440 / 24000 + 360) % 1440`; display `HH:MM` with leading zeros. Tick 0 is 06:00.
- Refresh every client tick.
- In a fixed-time dimension (Nether, End): `--:--`.

### 4.2 Signal: server latency

4 bars, 2 px wide, 1 px gaps, heights 2, 3, 5, 7, bottom-aligned:

```
. . . . . . . . . # #
. . . . . . . . . # #
. . . . . . # # . # #
. . . . . . # # . # #
. . . # # . # # . # #
# # . # # . # # . # #
# # . # # . # # . # #
```

Lit bars `status-ink`, unlit `status-dim`. From the player's own ping: under 150 ms 4 bars, under 300 ms 3, under 600 ms 2, otherwise 1. All unlit in singleplayer.

### 4.3 Battery: the player's hunger

```
O O O O O O O O O O O O .
O . . . . . . . . . . O .
O . f f f f f f f f . O O
O . f f f f f f f f . O O
O . f f f f f f f f . O O
O . . . . . . . . . . O .
O O O O O O O O O O O O .
```

- Frame O: `status-ink`. Fill area 8 x 3 at battery-local `x 2..9`, `y 2..4`.
- Filled width `n = round(food * 8 / 20)` from the left.
- Fill: food 7 to 20 `#58C83C`; 4 to 6 `#F28A2E`; 0 to 3 `#E5483C`.
- Creative and spectator: full, green.

---

## 5. Wallpaper: "Overworld dusk"

One 128 x 196 sprite covering the whole screen, shown on the home screen and behind the peek. Flat bands and blocks, no dithering.

The moon must stay visible whatever apps are installed. With a full page, the icons, hover rings and badges cover `sx 4..124`, `sy 16..160`. The moon sits at `sy 161..168`: below the last icon row's hover ring (ends `sy 160`) and above the label line (glyphs start `sy 171`). The landscape sits low so the moon is in the sky.

| Layer | Pixels | Colour |
|---|---|---|
| Sky band 1 | `sy 0..39` | `#141B30` |
| Sky band 2 | `sy 40..79` | `#1A2440` |
| Sky band 3 | `sy 80..119` | `#222F52` |
| Sky band 4 | `sy 120..149` | `#2D3D66` |
| Sky band 5 (horizon glow) | `sy 150..195` | `#3D4F7C` |
| Stars (1 px) | `(2,24) (125,40) (1,88) (126,120) (2,146) (30,70) (64,100) (90,130) (50,150) (14,13) (99,14)` | `#C9D4F0` |
| Moon (square, Minecraft style) | `sx 106..113`, `sy 161..168` | `#E8ECF5`; craters `(108,163) (109,163) (111,166) (108,167)` in `#B8C2D8` |
| Hills | for column block `k = 0..15` (`sx 8k..8k+7`), from the hill top to `sy 185`; tops: `174, 170, 168, 170, 176, 178, 172, 168, 166, 170, 174, 178, 180, 182, 180, 176`; first row of each block is the edge colour | `#1C2438`, edge `#26304A` |
| Grass | `sy 186..189` | `sy 186` `#3D7236`, `sy 187..189` `#2E5A2A` |
| Dirt | `sy 190..195` | `#3A2A1E` |
| Dirt speckles | in every 8x8 cell (origin `sx 0, sy 190`): dark at cell `(1,2) (5,1) (3,5)` | `#2C1F16` |

The side columns `sx 0..3` and `sx 124..127` are never covered, so the stars at `x 1..2` and `x 125..126` always show. The moon hangs over the valley of blocks 13 and 14 (hill tops 182 and 180). The label line (`sy 171..178`) and the page dots (`sy 187..189`) sit over hills and grass, in white with shadow.

---

## 6. Home screen (pixel)

Inside the viewport, below the status bar.

| Element | Value |
|---|---|
| Grid | 5 columns x 6 rows = 30 apps per page |
| Icon | 20 x 20 |
| Icon position | column `c = 0..4`: `vx = 6 + 24c`; row `r = 0..5`: `vy = 8 + 24r` |
| Gaps | 4 px both ways (pitch 24) |
| Grid extent | `vx 6..121`, `vy 8..147` |
| Order | registration order, left to right, top to bottom; Settings is always last |
| Labels | none under icons |
| Label line | hovered app's name (in the player's language), centred, glyph rows `vy 160..166`, `#FFFFFF` with shadow, max 20 characters, empty when nothing is hovered |
| Page indicator | only with more than 30 apps: 3 x 3 square dots, pitch 6, centred, `vy 176..178`; active `#FFFFFF`, inactive `#FFFFFF66` |

| Icon state | Look |
|---|---|
| Normal | composed icon (section 7) |
| Hover | 24 x 24 1 px `accent` ring with 2-step corners at `(icon.x - 2, icon.y - 2)`; name on the label line |
| Pressed | icon drawn 1 px lower, ring stays |

Releasing the click on an icon opens the app with the forward transition (14).

### 6.1 Home badge (pixel)

9 px tall, 2-step corners, outline `badge-lo`, fill `badge`, digits in the bitmap font, `badge-text`, no shadow, on badge rows 1..7.

```
. . O O O O O . .
. O r r r r r O .
O r r r r r r r O
O r r r r r r r O
O r r r r r r r O
O r r r r r r r O
O r r r r r r r O
. O r r r r r O .
. . O O O O O . .
```

| Count | Text | Size | Text at (badge-local) |
|---|---|---|---|
| 0 | none | | |
| 1..9 | one digit | 9 x 9 | `(2, 1)` |
| 10+ | `9+` | 15 x 9 | `(2, 1)` |

Position: 9 x 9 at `(icon.x + 14, icon.y - 3)`; 15 x 9 at `(icon.x + 8, icon.y - 3)`. Drawn over the hover ring.

An app's badge counts its unseen notifications. **It clears when the app is opened**, by any route (home icon, banner, peek). The LED is lit while any app has a badge.

Reopening the phone onto an app that was left open keeps that app's badge (the app was not opened again), except for notifications whose target the shown screen reports as visible (the thread a notification points to): those are read in place, as one arriving there would be (10.3). Notifications received while the player was offline show as badges at login and nothing else: no peek, no chime (10.2).

---

## 7. App icon system (pixel)

Authors give the phone **a tile colour from the LNB tile palette** and **a 16 x 16 glyph** (or an ItemStack). The phone draws tile, frame and shade, so icons from different mods look like one family. App icons are pixel art everywhere they appear: home grid and notification banner (20 x 20 GUI px = 40 x 40 dp, never resized).

### 7.1 Composition (done by the phone)

On a 20 x 20 canvas:

1. Fill `(1,1)..(18,18)` with the tile `base`.
2. Row `y 1`, `x 2..17`: tile `hi`. Row `y 18`, `x 2..17`: tile `lo`.
3. Frame: 1 px `outline` ring with 2-step corners.
4. Auto-shade: glyph opaque pixels shifted 1 px down, in tile `lo`, only where the glyph is transparent.
5. Glyph at `(2, 2)`, 16 x 16.

### 7.2 Glyph rules

- 16 x 16 PNG, hard pixels, no partial alpha. Main shape inside the 12 x 12 safe area `(2,2)..(13,13)`; tails and beaks may reach the edge.
- At most 4 inks from the list below. Do not draw shadows.
- An ItemStack may replace the glyph (16 x 16 at `(2,2)`, no auto-shade).
- App names: 16 characters or fewer recommended, in both languages. The limit is not enforced: a name too long for the label line (section 6) is truncated when drawn.
- A glyph that is not 16 x 16 is fitted, with a warning in the log: a square multiple of 16 (32 x 32, 48 x 48) is sampled down with nearest-neighbour, a strip 16 wide and a multiple of 16 tall shows its first frame, anything else shows the bare tile.

| Ink | Hex |
|---|---|
| `W` white (main shape) | `#FFFFFF` |
| `L` light | `#D6DCE6` |
| `K` ink | `#1B1E23` |
| `D` tile lo | the tile's `lo` |
| one accent | `O #F28A2E`, `Y #F2B33A`, `G #5BD34A`, `R #E5483C` or `B #3A8DF0` |

### 7.3 Tile palette

| Tile | hi | base | lo |
|---|---|---|---|
| azure | `#7CC0FF` | `#3A8DF0` | `#1F5FB0` |
| teal | `#7FE3E0` | `#1FA3A0` | `#12706E` |
| green | `#9BEA7A` | `#4DB33C` | `#2D7A22` |
| orange | `#FFC48A` | `#E57A24` | `#A65116` |
| red | `#FF8F86` | `#DB4237` | `#9A2720` |
| pink | `#FFB0DA` | `#E0609F` | `#A03A70` |
| violet | `#C7A8FF` | `#8457E6` | `#56339E` |
| graphite | `#5C6372` | `#3A404B` | `#23272E` |

### 7.4 Example glyphs

**Feed**: tile azure. Inks W, L, K, O.

```
r0   ................
r1   ................
r2   ..........WWW...
r3   .........WWWWW..
r4   .........WWKWWOO
r5   .........WWWWWO.
r6   W.......WWWWWW..
r7   WW.....WWWWWWW..
r8   .WWW.WWWWWWWWW..
r9   ..WWWWWWWWWWWW..
r10  ...WWWWWLLLWWW..
r11  ....WWWLLLWWW...
r12  .....WWWWWWW....
r13  .......O.O......
r14  ................
r15  ................
```

**Messages**: tile green. Inks W, D.

```
r0   ................
r1   ................
r2   ..WWWWWWWWWWWW..
r3   .WWWWWWWWWWWWWW.
r4   .WWWWWWWWWWWWWW.
r5   .WWWWWWWWWWWWWW.
r6   .WWWDDWDDWDDWWW.
r7   .WWWDDWDDWDDWWW.
r8   .WWWWWWWWWWWWWW.
r9   .WWWWWWWWWWWWWW.
r10  ..WWWWWWWWWWWW..
r11  ..WWW...........
r12  ..WW............
r13  ..W.............
r14  ................
r15  ................
```

**Wallet**: tile violet. Inks W, L, K, Y.

```
r0   ................
r1   ................
r2   ................
r3   ....LLLLLLLLLL..
r4   .WWWWWWWWWWWWWW.
r5   .WWWWWWWWWWWWWW.
r6   .WWWWWWWWWWWWWW.
r7   .WWWWWWWWWKKKKKK
r8   .WWWWWWWWWKKYYKK
r9   .WWWWWWWWWKKYYKK
r10  .WWWWWWWWWKKKKKK
r11  .WWWWWWWWWWWWWW.
r12  .WWWWWWWWWWWWWW.
r13  .WWWWWWWWWWWWWW.
r14  ................
r15  ................
```

**Quests**: tile orange. Inks W, K, G, D.

```
r0   ................
r1   ......KKKK......
r2   ..WWWKKKKKKWWW..
r3   ..WWWWWWWWWWWW..
r4   ..WWWWWWWWWWWW..
r5   ..WWGGWKKKKKWW..
r6   ..WWGGWWWWWWWW..
r7   ..WWWWWWWWWWWW..
r8   ..WWGGWKKKKKWW..
r9   ..WWGGWWWWWWWW..
r10  ..WWWWWWWWWWWW..
r11  ..WWDDWKKKWWWW..
r12  ..WWDDWWWWWWWW..
r13  ..WWWWWWWWWWWW..
r14  ................
r15  ................
```

**Settings** (built-in, label `Réglages` / `Settings`): tile graphite. Ink W.

```
r0   ................
r1   ......WWWW......
r2   ..WW..WWWW..WW..
r3   ..WWWWWWWWWWWW..
r4   ...WWWWWWWWWW...
r5   ...WWWWWWWWWW...
r6   .WWWWW....WWWWW.
r7   .WWWWW....WWWWW.
r8   .WWWWW....WWWWW.
r9   .WWWWW....WWWWW.
r10  ...WWWWWWWWWW...
r11  ...WWWWWWWWWW...
r12  ..WWWWWWWWWWWW..
r13  ..WW..WWWW..WW..
r14  ......WWWW......
r15  ................
```

---

## 8. App design language and UI kit (smooth layer, dp)

Every app, built-in or third-party, draws inside the 256 x 370 dp viewport with this kit. The look is a modern dark mobile UI: flat surfaces, true rounded corners, a single blue accent, generous line height, thin hairlines, line icons. The one pixelated element inside apps is the player's face.

### 8.1 Rendering rules

- Anti-aliased shapes, real alpha, true rounded corners. Rounded rects, pills, circles and their strokes are coverage masks the drawing surface computes at the screen's real resolution, once per size and cached, never per frame. (Changed after the rendering spike: pre-made sprites at 2 px per dp made 1 dp strokes soft at GUI scale 3.)
- Text is Inter rasterised at the exact pixel size for the current GUI scale, with the font's kerning; widths in dp, and so line breaks, are the same at every scale.
- **No blur, no backdrop effects.** Shadows only from the pre-baked soft sprites: FAB, and the "sheet" shadow under the banner, snackbar, menu and dialog. No other element has a shadow.
- No gradients in the kit.
- Icons and the spinner are exported at **eight densities, 1 to 4.5 px per dp in steps of 0.5**, one per GUI scale from 2 to 9, and drawn 1:1. Shadows are exported at 2 px per dp and drawn with linear filtering, no mipmaps.
- Put rect edges on **even dp values** (whole GUI px), so they stay crisp at every scale. At an odd GUI scale (3, 5, 7, 9) 1 dp is a half-integer of pixels, and an edge on an odd dp value is rounded half up to a whole pixel, the same way at every position: the 3 dp tab indicator at `y 41..43` is 4 real pixels tall at GUI scale 3, not 4.5. A line is its thickness in dp times `s / 2` pixels, rounded half up and at least 1: a 1 dp hairline is 1, 2, 2, 3, 3, 4, 4 and 5 real pixels at GUI scales 2 to 9.
- The app layer is clipped to the viewport. The shell then paints its 4 screen corner pixels over it, giving the bottom corners a 1 GUI px notch on purpose.
- **Every integer GUI scale from 2 up is supported** (Auto gives 5, 6 or 9 on large screens). GUI scale 1 is not supported for apps (1 dp would be half a pixel). At GUI scale 1 the shell and home screen work, and an opened app's viewport shows only the bitmap-font line `Échelle GUI : 2+` (string `gui.scale`), centred horizontally and vertically, in `#96A0B0`, on `bg`. There the phone also shows no notification banner and no peek (the chime plays and the badge waits, 10.2), and Backspace goes to the home screen, the only way back there (2.3).

### 8.2 The seam between pixel status bar and smooth app

Inside apps the pixel status bar is painted in `status-bg-app` = `#12151B`, exactly the app `bg`, and every app header is also `bg`. The status bar and the header form one continuous dark band with no line between them. The device's own information (clock, signal, battery) stays in bitmap pixels at the very top. The app's information (title, back, tabs) starts 22 dp below in smooth type. The only line in the band is the header's bottom hairline. On the home screen the status strip stays `#00000066` over the wallpaper.

### 8.3 Colour tokens

| Token | Hex | Use |
|---|---|---|
| `bg` | `#12151B` | app background, headers, tab bar |
| `surface` | `#1A1E26` | pinned posts, segmented track, empty-state disc |
| `surface-2` | `#242A34` | notification banner, dialog, skeleton shapes |
| `surface-3` | `#313846` | snackbar, menu, tooltip, selected segment |
| `field` | `#0D1015` | text field and text area fill |
| `stroke` | `#3A4250` | input borders, secondary button border, toggle off track |
| `stroke-hover` | `#465063` | hovered input borders, toggle off track on hover |
| `hairline` | `#2A303B` | dividers, header bottom line |
| `text-1` | `#EEF1F5` | primary text, active labels |
| `text-2` | `#96A0B0` | secondary text: handles, times, counts, inactive tabs |
| `text-3` | `#5C6575` | placeholders, disabled text, private lines |
| `accent` | `#3A8DF0` | primary buttons, FAB, focus, active tab, toggle on, unread markers |
| `accent-hover` | `#529CF3` | hovered accent fills |
| `accent-pressed` | `#2E78D2` | pressed accent fills, loading buttons |
| `accent-soft` | `#3A8DF01F` | hover tint for text buttons, reply and pin; post highlight |
| `accent-soft-2` | `#3A8DF033` | pressed tint for text buttons, reply and pin |
| `on-accent` | `#FFFFFF` | text and icons on accent and danger |
| `like` | `#F2545B` | liked heart and its count |
| `like-soft` | `#F2545B1F` | hover tint behind the like icon |
| `like-soft-2` | `#F2545B33` | pressed tint behind the like icon |
| `announcer` | `#7A5CE6` | announcer badge disc |
| `on-announcer` | `#FFFFFF` | announcer badge glyph |
| `warning` | `#F2B33A` | character counter, last 20 characters |
| `danger` | `#F0564A` | over the limit, errors, destructive actions |
| `danger-hover` | `#F36D63` | hovered danger button |
| `danger-pressed` | `#D94A3F` | pressed and loading danger button |
| `danger-soft` | `#F0564A33` | over-limit character highlight |
| `badge` | `#E5483C` | count badges in apps |
| `overlay-hover` | `#FFFFFF0A` | hover on rows, cards, tabs, menu items |
| `overlay-pressed` | `#FFFFFF14` | pressed rows, cards and menu items; icon-button hover |
| `overlay-pressed-2` | `#FFFFFF1F` | pressed icon buttons |
| `scrim-app` | `#00000099` | behind a dialog, over the viewport only |
| `anon-bg` | `#3A4250` | anonymous avatar disc |
| `banner-edge` | `#FFFFFF0F` | 1 dp inner stroke of the banner |
| `focus-ring` | `#3A8DF080` | keyboard focus ring |
| `selection` | `#3A8DF066` | background behind selected text in a field or area |

### 8.4 Type

Font: **Inter** (Rasmus Andersson, SIL Open Font License 1.1), bundled as `Inter-Regular.ttf` (400) and `Inter-SemiBold.ttf` (600). No other font and no other weights in apps. Text is rasterised at the real pixel size of each GUI scale, never as an upscaled texture.

**Minimum text size: 11 dp** (11 real px at GUI scale 2).

| Style | Size / line height | Weight | Use |
|---|---|---|---|
| `title-l` | 17 / 22 dp | 600 | app header title |
| `title-m` | 14 / 18 dp | 600 | post display name, empty-state and dialog titles, banner title |
| `body` | 13 / 18 dp | 400 | post text, field text, snackbar, banner text, dialog text, menu items |
| `body-strong` | 13 / 18 dp | 600 | list row titles, setting labels |
| `label` | 13 / 16 dp | 600 | buttons, tabs, segmented control |
| `caption` | 12 / 16 dp | 400 | handles, times, counts, helper text, counter, private lines, tooltips |
| `caption-strong` | 12 / 16 dp | 600 | `Épinglé`, section headers, new-posts pill |
| `micro` | 11 / 14 dp | 600 | count badges |

Text is left-aligned, sentence case. Every fixed-width slot is sized for the longer of the French and English strings (section 13.3). Single-line slots truncate with an ellipsis character. Post text wraps on word boundaries and is never truncated in the timeline.

**Characters Inter lacks.** Inter covers Latin, Greek and Cyrillic; it has no emoji and no CJK, Arabic or other scripts. In apps such a character shows one missing-glyph box per grapheme (an emoji with its skin tone, or a flag, is one box); the text itself is never altered. The notification banner differs: it drops emoji and shows one `?` per run of other characters (10.1). Whether to bundle an emoji font is an open question for the Feed phase (19).

### 8.5 Spacing and radius

- Spacing scale (dp): **2, 4, 8, 12, 16, 24, 32**.
- Screen side padding: **12 dp** in every app.
- Content column in rows and posts: avatar at `x 12` (32 dp), text at **`x 56`**.
- Radius scale: **10 dp** (text field, text area, snackbar, menu, tooltip), **14 dp** (banner, dialog), **full** (buttons, FAB, pills, toggles, segmented control, avatars, badges, hover discs).
- Minimum hit target: 28 x 28 dp.

### 8.6 Line icons

- Grids: **12 dp** (the filled megaphone in the announcer badge, `person` in a 16 dp avatar), **16 dp** (action row, pin, lock, more, trash, alert, arrows; stroke 1.5 dp), **20 dp** (header and row icons; stroke 2 dp), **24 dp** (FAB; stroke 2 dp), **28 dp** (`person` in a 48 dp avatar; stroke 2 dp), **40 dp** (empty states; stroke 2.5 dp). `person` is drawn at 12, 20 and 28 dp, so the anonymous avatar's icon is exported at the size it is drawn.
- Live area: grid minus 2 dp on every side (20 dp icon draws inside 16 dp).
- Round caps, round joins, 2 dp corner radius on drawn rectangles.
- Outline style. Filled variants only for active states (`heart_filled`, `pin_filled`). `heart_filled` is drawn filled and stroked, so that it keeps the outline heart's size (in the mockup the heart shrank when liked).
- Exported white on transparent and tinted at draw time: `text-1` by default, `text-2` in action rows and meta, `on-accent` on accent.

Icon set: `back` (chevron left), `close` (X), `compose` (pen nib with a short line under it), `heart`, `heart_filled`, `reply` (rounded speech bubble), `pin`, `pin_filled`, `lock` (padlock, private lines), `more` (three horizontal dots), `trash` (bin), `alert` (circle with an exclamation mark), `arrow_up`, `person` (head circle and shoulders, for the anonymous avatar), `globe`, `megaphone`, `megaphone_filled` (12 dp solid silhouette, announcer badge), `inbox`.

### 8.7 Components

All positions are relative to the component unless stated. Hover tints are drawn over the component's own fill. Every pressed state lasts at least 100 ms, even on a quick click.

**App header** (44 dp tall, at `y 0..43`)

| Part | Value |
|---|---|
| Fill | `bg` |
| Bottom hairline | 1 dp at `y 43`, `hairline` |
| Back button | 32 x 32 dp icon button at `(6, 6)`, `back` icon 20 dp centred, `text-1`; hidden on an app's root screen |
| Title | `title-l`, `text-1`, line box at `y 11`; `x 44` with a back button, `x 12` without; truncated 12 dp before the trailing slot |
| Trailing slot | one icon button (32 x 32 at `(218, 6)`) or one primary button right-aligned to `x 244`, at `y 6` |

**Icon button** (32 x 32 dp; 28 x 28 dp in post cards): no fill. Hover: full-radius disc `overlay-pressed`. Pressed: disc `overlay-pressed-2`. Disabled: icon `text-3`. Focus: 2 dp `focus-ring` at a 2 dp offset.

**Tabs** (44 dp tall, used directly under the status band)

| Part | Value |
|---|---|
| Fill | `bg` |
| Tabs | equal width (2 tabs: 128 dp each), label `label` centred, line box at `y 14` |
| Inactive | label `text-2` |
| Hover (inactive) | label `text-1`, whole tab `overlay-hover` |
| Pressed (inactive) | label `text-1`, whole tab `overlay-pressed` |
| Active | label `text-1`; indicator 3 dp tall pill at `y 41..43` (4 real pixels tall at GUI scale 3, see 8.1), `accent`, **fixed width 48 dp for every tab**, centred on the tab (not on the label): with 2 tabs, `x 40..87` under the left tab and `x 168..215` under the right tab. The indicator never depends on label length |
| Unread dot | 6 dp `accent` circle, 4 dp right of the label, vertically centred on it (rule in 9.6) |
| Bottom hairline | 1 dp at `y 43`, under the indicator |
| Switch | indicator slides to the new tab in 150 ms; content swaps at once |

**Buttons** (32 dp tall, full radius, label `label` centred, focus ring as for icon buttons)

| Button | Normal | Hover | Pressed | Disabled | Loading |
|---|---|---|---|---|---|
| Primary | fill `accent`, label `on-accent`, padding 16 dp each side | fill `accent-hover` | fill `accent-pressed` | fill `hairline`, label `text-3` | fill `accent-pressed`, label hidden, 16 dp spinner in `on-accent` centred; not clickable |
| Danger | fill `danger`, label `on-accent`, padding 16 dp | fill `danger-hover` | fill `danger-pressed` | fill `hairline`, label `text-3` | fill `danger-pressed`, spinner in `on-accent` |
| Secondary | no fill, 1 dp inner border `stroke`, label `text-1`, padding 16 dp | fill `overlay-hover` | fill `overlay-pressed` | border `hairline`, label `text-3` | not used |
| Text | no fill, label `accent`, padding 8 dp | fill `accent-soft` | fill `accent-soft-2` | label `text-3` | not used |

Minimum width 64 dp for primary, danger and secondary. Buttons in fixed layouts have a fixed width sized for the longer language (section 13.3); the label stays centred. A loading button keeps its width.

**Floating action button** (FAB)

| Part | Value |
|---|---|
| Shape | 48 dp circle, fill `accent`, at `(196, 310)` (12 dp from the right and bottom of the viewport) |
| Icon | `compose` 24 dp, `on-accent`, centred |
| Shadow | `shadow/fab` sprite, 72 x 72 dp, centred on the FAB and offset 2 dp down |
| Hover / pressed | fill `accent-hover` / `accent-pressed` |
| With a snackbar | moves up to `y 262` (150 ms) while the snackbar shows |

**Text field** (single line, 40 dp tall, default width 232 dp)

| State | Look |
|---|---|
| Empty | fill `field`, 1 dp inner border `stroke`, radius 10, placeholder `body` `text-3` at `x 12`, vertically centred |
| Hover | border `stroke-hover` |
| Focused | border 2 dp `accent` (inner, no layout shift); caret 2 x 18 dp `accent`, blinking 500 ms on / 500 ms off |
| Filled | text `body` `text-1`, border `stroke` |
| Error | border 2 dp `danger`; helper `caption` `danger` 4 dp under the field |
| Disabled | fill `bg`, border `hairline`, text `text-3` |

**Text area** (multi-line, with character counter)

| Part | Value |
|---|---|
| Shape | fill `field`, 1 dp inner border `stroke` (hover `stroke-hover`, focused 2 dp `accent`), radius 10 |
| Text | `body` `text-1`, padding 12 dp on left, top and right; wraps; scrolls inside when longer than the box |
| Reserved footer | bottom 28 dp of the box hold only the counter; text never runs under it |
| Counter | `caption`, right-aligned at 12 dp from the right edge, line box 22 dp above the bottom edge; format `142/280` |
| Counter colour | 0 to 260 characters `text-2`; 261 to 280 `warning`; over 280 `danger`, and the counter shows the excess as `-5` |
| Over the limit | characters after the 280th get a `danger-soft` background behind each glyph run |
| Counting | characters are Unicode code points of the text in normalisation form C: `é` is 1 however it was typed, an emoji is 1, a flag (two code points) is 2. The counter and the send button use the same **untrimmed** count, spaces and line breaks included, so they always agree; the text is sent trimmed. An app's server counts the same way (`TextLimits`) |
| Keys | Enter makes a new line; Ctrl+Enter submits (in a single-line text field, Enter submits) |
| Placeholder | `body` `text-3` |
| Sending | text `text-2`, no caret, not editable, border `stroke` |

In both kinds of field, selected text has a `selection` background, and a double click selects a word.

**Toggle** (switch, 36 x 20 dp, full radius)

| State | Track | Knob (16 dp circle) |
|---|---|---|
| Off | `stroke` | `text-1`, at `x 2` |
| Off hover | `stroke-hover` | `text-1`, at `x 2` |
| On | `accent` | `#FFFFFF`, at `x 18` |
| On hover | `accent-hover` | `#FFFFFF`, at `x 18` |
| Disabled | `hairline` | `text-3`, at its position |

The knob slides in 100 ms. The whole row around a toggle is clickable.

**Segmented control** (32 dp tall, full radius): track fill `surface` with 1 dp inner border `stroke`; equal segments; the selected segment is a pill inset 3 dp, fill `surface-3`, label `label` `text-1`; unselected labels `text-2`, hover `text-1`. The selected pill slides in 150 ms.

**List row** (full width)

| Variant | Value |
|---|---|
| One-line, 48 dp | title `body-strong` `text-1` at `x 12`, vertically centred |
| Two-line, 56 dp | leading 32 dp avatar or 20 dp icon centred in a 32 dp box at `(12, 12)`; title `body-strong` at `x 56`, line box `y 10`; subtitle `caption` `text-2` at `x 56`, line box `y 30` |
| Trailing | toggle, chevron, or `caption` `text-2` meta, right-aligned at `x 244`, vertically centred |
| Hover / pressed | `overlay-hover` / `overlay-pressed` over the whole row |
| Divider | hairline at the row bottom, inside its height (the last dp of the 48 or 56), from `x 56` (with leading) or `x 12` (without) to `x 256`. A screen that wants its hairline between rows 49 dp apart turns this one off and places a separate divider, as Settings does (12) |

**Divider**: 1 dp `hairline`. Full width between sections; inset to `x 56` between rows with a leading avatar or icon.

**Scroll bar**: overlay, 4 dp wide, full radius, at `x 250..253`. Thumb `#FFFFFF33`, hover and drag `#FFFFFF59`. Minimum length 24 dp. No track. Visible while scrolling and while the pointer is over the list; fades out over 200 ms after 800 ms idle.

**Avatar** (the one pixelated element)

| Part | Value |
|---|---|
| Sizes | **16, 32 or 48 dp only**. These give whole-number scaling of the 8 x 8 face at every GUI scale (32 dp at scale 3 is 48 px = 6x) |
| Content | the player's 8 x 8 skin face, hat layer on top, scaled with nearest-neighbour |
| Which face | resolved from the player's UUID and name. A player on the server now: the skin the tab list has. Otherwise the profile is fetched once, in the background, from Mojang's session service by UUID, then by name if the UUID is unknown there, and kept for the session. Until it is known, and for players the session service doesn't know (offline-mode accounts, made-up names), the default skin Minecraft gives that UUID shows; the face changes by itself when the real one arrives |
| Shape | circle, computed by the drawing surface (8.1); no ring, no border |
| Anonymous | `anon-bg` disc with the `person` icon in `text-2` (20 dp icon on a 32 dp avatar, 12 dp on 16, 28 dp on 48) |

**Badges (app layer)**: count badge 16 dp tall, full radius, minimum width 16 dp, padding 4 dp each side, fill `badge`, text `micro` `#FFFFFF`; `99+` above 99. Dot badge: 8 dp circle `badge`.

**Announcer badge**: 16 dp circle, fill `announcer`, with the 12 dp `megaphone_filled` glyph in `on-announcer`, centred. No text, so it reads the same in both languages and does not imply a staff rank. Hover shows the tooltip `Peut publier des annonces` (`badge.announcer`). Shown 4 dp after the display name on every post and reply by an announcer, in both feeds and in threads.

**Tooltip** (smooth, inside apps)

| Part | Value |
|---|---|
| Shape | radius 10, fill `surface-3`, no shadow |
| Text | `caption` `text-1`, padding 8 dp horizontal, 4 dp vertical; max width 200 dp, wraps to 2 lines (24 dp tall for 1 line, 40 dp for 2) |
| Position | centred on the hovered element, 6 dp above it; below it when there is less than 8 dp above |
| Timing | appears 500 ms after the hover starts, disappears at once when the hover ends |

**Menu** (popover)

| Part | Value |
|---|---|
| Shape | 168 dp wide, radius 10, fill `surface-3`, `shadow/sheet` under it; 4 dp padding top and bottom |
| Item | 40 dp tall, icon 16 dp at `x 12` (vertically centred), label `body` at `x 40`; normal item `text-1`, destructive item icon and label `danger` |
| Item hover / pressed | `overlay-hover` / `overlay-pressed` over the item |
| Position | its top-right corner 4 dp under the bottom-right corner of the button that opened it; it opens upward instead (its bottom-right 4 dp above the button) when its bottom would pass `y 362`. A menu that fits neither below nor above the button covers the button, kept inside the viewport |
| Closing | an item click, a click anywhere else (that click does nothing else), Esc. While it is open Tab and Shift+Tab cycle its items, and Backspace does nothing |
| Timing | fades in 100 ms; closes at once |

**Dialog** (confirmation)

| Part | Value |
|---|---|
| Scrim | `scrim-app` over the whole viewport (the status bar stays as it is) |
| Card | 232 dp wide at `x 12`, vertically centred in the viewport; radius 14, fill `surface-2`, `shadow/sheet` under it; padding 16 dp |
| Title | `title-m` `text-1`, up to 2 lines |
| Text | 8 dp under the title, `body` `text-2`, up to 3 lines |
| Buttons | 16 dp under the text, right-aligned to the card's right padding: cancel (secondary, 88 dp) then, 8 dp to its right, confirm (primary or danger, 104 dp) |
| Height | `16 + title + 8 + text + 16 + 32 + 16` dp |
| Closing | cancel button, click on the scrim, Esc: same as cancel. The confirm button goes into its loading state until the server answers, then the dialog closes. While it is open Tab and Shift+Tab cycle its buttons, and Backspace does nothing (the confirm button may be waiting for the server) |
| Timing | scrim and card fade in 150 ms; fade out 100 ms |

**Snackbar**

| Part | Value |
|---|---|
| Shape | 240 x 40 dp at `(8, 322)`, radius 10, fill `surface-3`, `shadow/sheet` sprite under it |
| Text | `body` `text-1` at `x 14`, vertically centred, one line |
| Error variant | `alert` icon 16 dp in `danger` at `x 14`, text at `x 38` |
| Action | optional text button right-aligned 4 dp from the right edge: `Réessayer` (`error.retry`) |
| Timing | slides up 150 ms, holds 3000 ms (6000 ms with an action), fades out 150 ms. A new snackbar replaces the current one |

**Empty state**: block centred in the content area. A 72 dp `surface` disc with a 40 dp line icon in `text-3`; 16 dp under it the title in `title-m` `text-1`, centred, max width 200 dp, up to 2 lines; 4 dp under that the text in `body` `text-2`, centred, max width 200 dp, up to 2 lines; optional button 16 dp under that.

**Error state** (a list that cannot load): the empty-state block with icon `globe`, title `Feed indisponible` (`feed.unavailable.title`), text `Réessaie dans un instant.` (`feed.unavailable.text`), secondary button `Réessayer` (`error.retry`, 104 dp wide). While retrying, the button shows a 16 dp `accent` spinner instead of its label (same 104 dp, border `stroke`).

**Loading**

| Case | Look |
|---|---|
| First load of a list | 3 skeleton posts: avatar disc 32 dp `surface-2`; full-radius bars 10 dp tall in `surface-2` at `x 56`: 90 dp wide at `y 14`, 176 dp at `y 36`, 120 dp at `y 54`; post height 84 dp with hairlines; all pulse between 100% and 60% opacity over 1000 ms |
| More items / replies | 48 dp row with a spinner centred |
| Spinner | 20 dp ring (16 dp inside buttons), 2 dp stroke, 90 degree arc on a ring at 25% of the arc colour; `accent` by default, `on-accent` inside accent and danger buttons; one turn per 800 ms |

**Post highlight**: a post that just appeared or that a notification points to gets an `accent-soft` fill over its whole cell, held 600 ms then faded out over 400 ms.

---

## 9. The social app: "Feed"

Twitter-like in layout, not in branding: no bird logo, none of Twitter/X's colours (the accent is LNB's Ping Blue, likes are coral `#F2545B`, the announcer badge is violet), and the wording is our own. Home icon: section 7.4. UI strings are given in French; English versions are in section 13.

### 9.1 Structure

- Two top-level tabs, nothing else: **Annonces** (left, opens by default) and **Global** (right).
- Annonces: posts by announcers only. Everyone can like and reply. The compose FAB shows only for announcers.
- Global: all player posts, newest first, compose FAB for everyone.
- Thread: one post, its replies and a docked reply composer (9.7).
- Compose: full screen, from a FAB (9.8).
- The Feed root has no back button: the home button leaves the app.
- Opening Feed from its home icon lands on Annonces, scrolled to the top. Opening it from a notification lands on the notification's target (9.10). Reopening the phone returns to the Feed screen it was closed on.

### 9.2 Identity, roles and notifications

There is no registration. Everything comes from the game and from LNB Warden, the same data as the server's tab list.

| Item | Rule |
|---|---|
| Display name (bold) | the player's **real name** from LNB Warden, exactly the string the tab list shows (unchanged, may contain spaces and accents): `Alexandre Morel` |
| Handle (grey) | `@` + the **Minecraft username**: `@Alex_Craft` |
| No real name | when Warden has no real name for the player (hidden or guest), the **username is the display name** and there is **no handle**, so the same word is never printed twice: `SteveBuilds` |
| Lengths | handles are at most 17 characters (`@` + 16). Real names are capped by Warden (`RealNamesPayload.MAX_NAME_LENGTH`); every layout is checked with a 32-character real name and truncates the real name. The handle line truncates too (9.3) |
| Avatar | the face of the player's skin, resolved from their UUID and name (8.7) |
| Operators | players with op permission level 2 or higher |
| Announcers | operators, plus players an admin has granted the right to by command. Only announcers can post in Annonces, pin, and see the destination choice in Compose |
| Announcer badge | on every post and reply by an announcer, both feeds (8.7) |

Wherever a name appears in text (reply-to line, composer placeholder, notifications, private lines), it is the display name as defined above.

Feed sends a notification (banner or peek, home badge count, LED, chime) for: **a new post in Annonces**; **a reply to one of the player's posts or replies**. Likes and new Global posts never notify. The player's own actions never notify them.

### 9.3 Post card anatomy

Full-width cell on `bg`, separated by hairlines (timeline style, no boxed cards).

```
x: 12        44 56                                 214    244
   +--------+  Alexandre Morel (A)                 2 min  [...]   name line, y 10..27
   | avatar |  @Alex_Craft                                        handle line, y 28..43
   |  32dp  |  [lock] Auteur : Mika Laurent @MikaPvP              private line (when it applies)
   +--------+  Post text wraps here on as many
               lines as it needs, never cut.
               (reply) 3      (heart) 12                  (pin)   action row, 28 dp
---------------------------------------------------------------   hairline
(A) = announcer badge   [...] = more button   (pin) = pin button
```

| Part | Value |
|---|---|
| Padding | top 10 dp, bottom 4 dp, sides 12 dp |
| Avatar | 32 dp at `(12, 10)` |
| Name line | line box `y 10`, 18 dp: display name `title-m` `text-1`; announcer badge 4 dp after the name, vertically centred; relative time `caption` `text-2` right-aligned to `x 244`, or to `x 214` when the more button shows |
| Name truncation | the display name truncates so that badge + 8 dp + time always fit; badge and time never truncate |
| Handle line | line box `y 28`, 16 dp: handle `caption` `text-2` at `x 56`. Absent when there is no real name, and on anonymous posts. The handle truncates with an ellipsis at `x 244`, like any single-line slot. A typical 17-character handle is about 106 dp and fits, but the worst case, `@` plus sixteen `W`, is about 205 dp in `caption`, wider than the 188 dp column |
| More button | 28 x 28 dp icon button at `(220, 5)`, `more` icon 16 dp `text-2`; shown only when the viewer can delete the post (9.9) |
| Private line | one optional 16 dp line after the header lines (see below) |
| Reply-to line | in threads only, 16 dp, before the private line (9.7) |
| Body | `body` `text-1`, starts 2 dp under the last header or optional line, column `x 56..244` (188 dp, about 27 characters per line), word-wrapped, full length |
| Action row | 4 dp under the body, 28 dp tall |
| Divider | 1 dp `hairline`, full width, at the bottom |
| Height | `10 + 18 + 16 (handle) + 16 x optional lines + 2 + 18 x body lines + 4 + 28 + 4 + 1` dp. A 2-line post with a handle: 119 dp; without a handle: 103 dp |
| Hover / pressed | `overlay-hover` / `overlay-pressed` over the cell |
| Click | anywhere outside a button opens the thread |
| Relative time | strings `time.*` (section 13): under 1 min, minutes, hours, days, then a short date. Real-world time |

**Action row** (both feeds and threads; icons and counts vertically centred in the 28 dp row):

| Action | Position | Normal | Hover | Pressed | Active |
|---|---|---|---|---|---|
| Reply | icon `reply` 16 dp at `x 56`, count at `x 78` | icon and count `text-2` | icon and count `accent`, 28 dp `accent-soft` disc behind the icon | disc `accent-soft-2` | none |
| Like | icon `heart` 16 dp at `x 120`, count at `x 142` | icon and count `text-2` | icon and count `like`, 28 dp `like-soft` disc | disc `like-soft-2` | `heart_filled` and count in `like` |
| Pin | icon `pin` 16 dp at `x 220`, no count; **announcements only, visible only to announcers and operators** | icon `text-2` | icon `accent`, 28 dp `accent-soft` disc, tooltip `Épingler` (`action.pin`) | disc `accent-soft-2` | `pin_filled` in `accent`; hover tooltip `Désépingler` (`action.unpin`) |

Counts are `caption`; 0 is hidden (icon only); thousands use `count.thousands` (`1,2 k` / `1.2k`). The reply count is the number of replies in the post's thread. Reply opens the thread with the composer expanded and focused.

**Unseen replies marker**: on the viewer's own posts and replies that received replies they have not seen, the reply icon and count are `accent` and a 6 dp `accent` dot sits centred at `x 50`, vertically centred on the icon. It clears when the viewer opens that thread.

**Anonymous post:** anonymous avatar (8.7); display name `Anonyme` (`post.anonymous`) in `title-m` `text-2`; no handle, no announcer badge; the time stays. Announcements cannot be anonymous (9.8).

**Private line** (at most one per post, 16 dp, `lock` icon 16 dp in `text-3` at `x 56`, text `caption` `text-3` at `x 76`, truncated at `x 244`):

| Viewer | Text | Tooltip |
|---|---|---|
| The author of an anonymous post or reply | `Publié anonymement par toi` (`post.own_anonymous`) | none |
| An operator, on someone else's anonymous post or reply | `Auteur : Mika Laurent @MikaPvP` (`post.op_author`: display name, then the handle when there is a real name; `Auteur : SteveBuilds` without). The handle truncates first, then the name | `Visible uniquement par les opérateurs` (`post.op_author.tooltip`) |
| Everyone else | no line; the server never sends the author to them | |

### 9.4 Annonces feed and pinning

| Element | Value |
|---|---|
| Top | tabs at `y 0..43`, Annonces active |
| Authors | announcers only; every post carries the announcer badge |
| Pinned group | on top, **at most 3**, most recent pin first |
| Pinned cell | fill `surface`; a 16 dp meta line above the header: `pin` icon 16 dp at `x 28, y 8` in `text-2`, `Épinglé` (`post.pinned`) in `caption-strong` `text-2` at `x 56, y 8`. The rest of the cell moves down 18 dp |
| After the pins | other announcements newest first, on `bg` |
| Actions | reply and like for everyone; pin for announcers and operators |
| FAB | announcers only; opens Compose with Annonces selected |
| Empty | icon `megaphone`, title `Aucune annonce`, text `Les annonces du serveur apparaîtront ici.`; announcers also see the FAB |

**Pinning** (any announcer or operator can pin or unpin any announcement, from the feed or from the thread's main post; the operator command does the same):

| Case | Steps |
|---|---|
| Pin, fewer than 3 pinned | click the pin button: it turns to `pin_filled` `accent` at once; the post moves into the pinned group (list re-sorts, the post gets the post highlight); snackbar `Post épinglé` (`pin.done`). No confirmation |
| Pin, 3 already pinned | click the pin button: dialog `Remplacer une épingle ?` / `3 posts sont déjà épinglés. Le plus ancien sera désépinglé.`, buttons `Annuler` and `Épingler` (primary). Confirm: the oldest pin returns to its place in the list, the new one joins the pinned group, snackbar `Post épinglé` |
| Unpin | click the filled pin button: the post leaves the pinned group and returns to its date order; snackbar `Post désépinglé` (`pin.removed`). No confirmation |
| Failure | the button returns to its previous state; error snackbar `Action impossible` (`snackbar.action_failed`) |

Global posts cannot be pinned and never show the pin button.

### 9.5 Global feed and new posts

| Element | Value |
|---|---|
| Top | tabs, Global active |
| List | post cards from `y 44`, scroll bar on the right |
| FAB | compose FAB at `(196, 310)`, everyone; opens Compose with Global selected |
| Empty | icon `inbox`, title `Aucun post pour l'instant`, text `Lance la conversation !`, primary button `Écrire un post` (136 dp wide), which opens Compose |
| End of list | when older posts are loading: a 48 dp spinner row; when there are no more: a 48 dp row `Tu as tout vu` (`feed.end`) in `caption` `text-3`, centred; then 72 dp of empty space so the FAB never covers an action row |

**New posts while the feed is open** (same rule in Annonces and Global; posts arrive live from the server):

| Scroll position | Behaviour |
|---|---|
| At the top (scroll 0) | new posts are inserted at the top at once, with the post highlight |
| Scrolled down | the list does not move. A pill appears: 28 dp tall, full radius, fill `accent` (hover `accent-hover`, pressed `accent-pressed`), `arrow_up` 16 dp + `2 nouveaux posts` (`feed.new_posts`; in Annonces `feed.new_announcements`) in `caption-strong` `on-accent`, padding 12 dp, centred at `y 52`. Click: the list scrolls to the top (250 ms), the new posts are inserted with the highlight, the pill fades out. Scrolling to the top by hand does the same |
| Own new post | always inserted at the top and scrolled to (9.8) |

The pill counts all posts waiting; it updates in place when more arrive.

### 9.6 Unread dots on the tabs

One rule for both tabs: a tab shows the unread dot when it is **not** the active tab and holds posts newer than the last time the player viewed it. Viewing a tab clears its dot. Because Feed opens on Annonces, new announcements are seen on open; the Annonces dot appears only while the player is on Global and a new announcement arrives. The Global dot appears while the player is on Annonces and Global has posts newer than their last visit to Global.

### 9.7 Thread

```
y 0    [<]  Publication                                   header, 44 dp
y 44   main post (anatomy 9.3, no hover, no relative time)
       14:32 · 12 nov.                                    full timestamp
       (reply) 3        (heart) 12               (pin)
       ------------------------------------------------   hairline
       replies, oldest first (post cards)
       ...
y 306  ------------------------------------------------   composer (collapsed)
       [ Répondre à Alexandre Morel…                  ]
```

| Element | Value |
|---|---|
| Header | back button; title `Publication` (`thread.title`) |
| Main post | at `y 44`, post anatomy without hover and without the relative time on the name line (the more button still shows when allowed); pinned line, private line and pin button apply. After the body: 8 dp gap, full timestamp in `caption` `text-2` (`time.full`, `14:32 · 12 nov.`), 4 dp gap, action row, 4 dp, full-width hairline |
| Replies | post cards, oldest first, flat (one level). Each reply has reply and like (never pin) |
| Replies loading | a 48 dp spinner row under the main post |
| Reply to a reply | the composer targets that reply; the posted reply joins the same thread and shows a reply-to line `En réponse à Mika Laurent` (`post.reply_to`, `caption` `text-2`, at `x 56`) as its first optional line. Replying to the main post adds no reply-to line. An anonymous target is named `Anonyme` |
| No replies | 48 dp row under the main post, `Aucune réponse pour l'instant` (`thread.no_replies`) in `caption` `text-2`, centred |
| Bottom spacer | the composer's height + 12 dp after the last reply |
| Opening | from a card click (composer collapsed, scrolled to the top), from a reply action (composer expanded and focused), from a notification (scrolled so the notified reply is fully visible, with the post highlight) |
| Post deleted while open | back to the feed (back transition), snackbar `Ce post a été supprimé` (`thread.deleted`) |

**Reply composer** (docked at the bottom of the thread, fill `bg`, 1 dp `hairline` on its top edge):

| State | Layout (app-local, dp) |
|---|---|
| Collapsed, 64 dp (`y 306..369`) | text field 232 x 40 at `(12, 318)`, placeholder `Répondre à Alexandre Morel…` (`reply.placeholder`, the target's display name or `Anonyme`), truncated with `…` before the field's right padding. Hover: field hover state. Click: expands |
| Expanded, 200 dp (`y 170..369`) | `En réponse à Alexandre Morel` (`post.reply_to`) in `caption` `text-2`, line box at `(12, 182)`; text area 232 x 112 at `(12, 202)` with the `n/280` counter (4 text lines visible, then it scrolls); at `y 326`: toggle at `(12, 332)` with label `Anonyme` (`reply.anonymous`) in `body-strong` at `x 56`, line box `y 333`; primary button `Répondre` (`reply.send`, 96 dp wide) right-aligned to `x 244`, `y 326` |
| Répondre button | disabled while the text is empty (after trimming) or over 280 characters (counted as in 8.7, untrimmed; the text is sent trimmed) |
| Sending | button in its loading state, text area in its sending state, toggle disabled |
| Back while expanded | collapses the composer and keeps the draft (one draft per thread) |
| Success | collapses, clears the draft and the toggle, the new reply appears at the end with the post highlight and is scrolled into view, snackbar `Réponse envoyée` (`reply.sent`) |
| Failure | stays expanded with the draft intact, button enabled again, error snackbar `Échec de l'envoi` (`snackbar.send_failed`) with the action `Réessayer` |
| Expand / collapse | 150 ms, ease-out / ease-in, the thread list area resizes with it |

### 9.8 Compose

```
y 0   [X]                                        ( Publier )   header, 44 dp
y 52  (av)  Alexandre Morel
            @Alex_Craft
y 96  +------------------------------------------------+
      | Quoi de neuf sur le serveur ?                  |  text area
      |                                       142/280  |
      +------------------------------------------------+
      Publier anonymement                       [====o]   toggle row, 44 dp
      Nom masqué aux joueurs
      [   Annonces   |    Global   ]   (announcers only)
```

| Element | Value |
|---|---|
| Header | close icon button (`close`) at `(6, 6)`; no title; primary button `Publier` (`compose.post`), **88 dp wide**, right-aligned to `x 244`, at `y 6` |
| Publier button | disabled while the text is empty (after trimming) or over 280 characters (counted as in 8.7, untrimmed; the text is sent trimmed) |
| Author row | avatar 32 dp at `(12, 52)`; display name `title-m` at `x 56`, line box `y 52`; handle `caption` `text-2` at `x 56`, line box `y 70`. Without a real name: the username alone as display name, line box `y 59` (centred on the avatar) |
| Author row, anonymous on | anonymous avatar; `Anonyme` in `title-m` `text-2` at line box `y 52`; `Masqué aux joueurs` (`compose.anon_sub`) in `caption` `text-2` at line box `y 70` |
| Text area | at `(12, 96)`, 232 dp wide; **196 dp tall for players**, **152 dp for announcers**; placeholder `Quoi de neuf sur le serveur ?` (`compose.placeholder`); counter `n/280`; focused on open; Enter makes a new line, Ctrl+Enter publishes |
| Anonymous row | 44 dp, `x 12..244`, 12 dp under the text area: `Publier anonymement` (`compose.anonymous`) in `body-strong` (line box at row `y 4`), `Nom masqué aux joueurs` (`compose.anonymous.help`) in `caption` `text-2` (line box at row `y 22`), toggle right-aligned to `x 244`, centred at row `y 22`. Default off. **Hidden while Annonces is selected**; selecting Annonces also turns it off. The slot stays empty so nothing jumps |
| Destination (announcers only) | segmented control 232 x 32 at `(12, 316)`: `Annonces` / `Global`, same order as the tabs; preselected from the FAB that opened Compose. Everyone else never sees it |
| Bottom | both layouts end at `y 347` |
| Sending | Publier in its loading state, text area in its sending state, toggle and segmented control disabled, close button disabled |
| Success | back to the destination tab (back transition), scrolled to the top, the new post inserted with the post highlight, snackbar `Publié` (`compose.posted`); the draft is cleared |
| Failure | Compose stays open with the draft intact, Publier enabled again, error snackbar `Échec de l'envoi` with `Réessayer` (which publishes again) |
| Cancel | the close button returns to the feed (back transition) and **keeps the draft** (text, toggle, destination) until published; no confirmation dialog |

### 9.9 Deleting

| Who | What |
|---|---|
| Any player | their own posts and replies, including their anonymous ones |
| Operators | any post or reply |
| Announcers (not operators) | only their own, like any player |

The more button (9.3) shows on exactly the posts and replies the viewer can delete. Its menu has one item: `trash` + `Supprimer` (`menu.delete`), destructive.

| Step | Result |
|---|---|
| Click the more button | menu opens under it |
| Click `Supprimer` | dialog. Post: `Supprimer ce post ?` / `Ses réponses seront aussi supprimées. C'est définitif.` Reply: `Supprimer cette réponse ?` / `C'est définitif.` Buttons `Annuler` (secondary, 88 dp) and `Supprimer` (danger, 104 dp) |
| Confirm | `Supprimer` loads; on success the dialog closes, the cell collapses (150 ms) and disappears, snackbar `Post supprimé` / `Réponse supprimée` (`delete.post_done` / `delete.reply_done`). Deleting the main post from its thread goes back to the feed first, then the snackbar shows |
| Cancel | the dialog closes, nothing changes |
| Failure | the dialog closes, error snackbar `Action impossible` |

Deleting a post deletes its whole thread. Nothing is restorable from the app.

### 9.10 Feed notifications: content and landing

| Event | Banner title | Banner text | Landing |
|---|---|---|---|
| New announcement | `Nouvelle annonce` (`notif.announcement`) | `Lune Garnier : La saison 2 commence…` (`notif.text`: display name, then the post text) | Annonces tab, scrolled to the top, the announcement highlighted |
| Reply to your post or reply | `Nouvelle réponse` (`notif.reply`) | `Mika Laurent : Je l'ai vu près du moulin…` (`Anonyme : …` for an anonymous reply) | the thread, scrolled to the reply, highlighted; the unseen-replies marker clears |

When several notifications are waiting, a click on the app icon follows the usual rule (Annonces); the reply markers show which own posts have new replies.

---

## 10. Notifications (shell)

### 10.1 Banner (smooth)

240 x 76 dp (120 x 38 GUI px). A title and two lines of text.

| Part | Value (banner-local, dp) |
|---|---|
| Shape | radius 14, fill `surface-2`, 1 dp inner stroke `banner-edge` |
| Shadow | `shadow/sheet` sprite under it |
| App icon | the app's pixel icon at native size (20 x 20 GUI px = 40 x 40 dp) at `(12, 18)` |
| Title | `title-m` `text-1` at `x 64`, line box `y 10`, truncated at `x 228` (164 dp) |
| Text | `body` `text-2`, at `x 64`, line box `y 28`, **up to 2 lines** (36 dp), width 164 dp (about 23 characters per line), word-wrapped, ellipsis at the end of line 2 |
| Hover (phone open only) | fill `surface-3` |
| Pressed (phone open only) | fill `surface-3` with `overlay-pressed` |

**Text cleaning.** Title and text are drawn plain. Line breaks and tabs become spaces, emoji are dropped, and each run of other characters Inter lacks (CJK, Arabic and the like) becomes one `?`, so a name in an unsupported script still shows that a name is there (`田中 : salut` reads `? : salut`). The two lines above are the rule whatever the text: a longer one ends with an ellipsis on line 2, so the sample banner texts in the flows end earlier than the posts they quote.

No time is shown: a banner always describes something that just happened. Feed contents are in 9.10; other apps give a title and a text.

### 10.2 Peek (phone closed)

| Item | Value |
|---|---|
| Horizontal position | `wx = W - 146`, same as the bottom-right placement |
| Rise | **72 px**: device top at `wy = H - 72`; top bezel and screen rows `sy 0..59` visible |
| Narrow windows | when `W < 476` the phone rises **112 px** (`wy = H - 112`) so the banner clears the hotbar and food rows |
| Layering | two layers: the device is a HUD layer below the hotbar, hearts and food; the banner is drawn on its own layer above the chat lines, so a chat line never covers it. F1 (hidden HUD) hides the peek |
| Screen content | wallpaper, home-style status bar, banner. No app icons |
| Banner | screen-local `(4, 14)` GUI px = `sx 4..123`, `sy 14..51` |
| LED | lit |
| Interaction | not clickable (mouse captured). Up arrow during the peek opens the phone on the notification's target |
| Queue | a new notification during the hold swaps the banner content in place, restarts the hold and chimes. Several notifications at once (the same tick): the last one shows and the chime plays once. While the device is still sliding down, a peek waits until it is away, then rises |
| Preview off | no peek at all; the chime still plays (if sounds are on) and the badge waits on the home screen |
| Another screen open | no peek while chat, a container, the pause menu, the death screen or any other screen is open: the chime plays and the badge waits. A screen that opens during a peek sends it back down |
| GUI scale 1 | no peek: the chime plays and the badge waits (8.1) |
| Cleared | a peek whose notification the server clears, or that is read, goes away early: the phone key would lead to a target that is gone |
| At login | notifications received while the player was offline are not news: they show as badges and nothing else, with no peek and no chime |

In a 480 x 270 window the peeking device sits at `(334, 198)`.

### 10.3 Banner while the phone is open

Same banner at app-local `(8, 4)` dp (screen-local `(4, 13)` GUI px), over the current screen (home or any app), slid down from above the viewport and clipped to it. Click opens the notification's target with the forward transition and removes the banner at once. Auto-dismiss after 4000 ms (slides up). Several notifications at once: the last one shows, and the chime plays once.

- **Pressed.** While the left button is down on the banner it freezes, content and timeline. Released on it, it opens what was pressed (a notification that arrived meanwhile stays a badge). Released elsewhere, it resumes, showing the newer notification if one came, with a fresh hold. The mouse wheel over the banner does not scroll the screen under it.
- **Cleared.** A banner whose notification the server clears, or that is read, goes away early.
- **Target already on screen** (the thread it points to, or the Annonces tab for a new announcement: there the post simply arrives, 9.5): no banner. The chime plays, and the notification counts as read, so it leaves no badge. The same reading in place happens when the phone reopens onto an app left open (6.1).
- **GUI scale 1:** no banner (8.1).

### 10.4 Storage and limits (server)

Notifications are kept per player on the server and saved with the world. Apps send them through the phone's server API; the phone enforces these rules whatever the app passes in.

| Rule | Value |
|---|---|
| Slots | 50 notifications per player, **shared by all apps**. When the list is full, the oldest notification of the mod that holds the most is dropped, so a busy app cannot push out the others' |
| Lifetime | 30 days; an older notification is dropped |
| Title | cut to 128 characters (never refused: an app forwarding the start of a long post gets the start) |
| Text | cut to 320 characters, likewise. A banner shows far less (10.1) |
| Rate | a backstop per player and per mod: a burst of 20, then one every 3 seconds; the rest are dropped and logged. Far above what a real app sends, it only stops a looping app from flooding a player |

Opening an app clears that app's notifications (6.1), on the server too.

---

## 11. Inventory button (pixel)

A miniature of the phone face on a side tab, under the quest book button.

| Item | Value |
|---|---|
| Size | 27 x 28 GUI px |
| Position | `wx = guiLeft - 22` (5 px over the inventory edge), `wy = guiTop + 46` (4 px under the quest button's lower bracket, which ends at `guiTop + 41`) |
| Recipe book | the button follows `guiLeft` every frame. With the book open it covers most of the button: the covered part neither lights up nor takes a click, the uncovered part works as usual, and the badge sits under the book (see below) |
| Tooltip (vanilla) | `LNB Phone` (`inventory.tooltip`); when unread, a second line in `#3A8DF0`: `3 nouvelles notifications` (`inventory.tooltip.unread`) |
| Click, no unread | the inventory closes, as its own close would (the crafting grid and the item on the cursor go back to the inventory), and the phone opens on the screen it was closed on |
| Click, unread | the inventory closes as above. If the phone was put away on the home screen or on an app's root screen, it opens on the **home screen**, so the badges show which apps have news. If it was put away deeper in an app (a thread, Compose, a conversation with a draft), it reopens on that same screen, because 2.3 keeps screens and drafts and leaving would lose them: drafts win over the badges. The newest unread notification then shows as the in-phone banner (10.3), with no chime, so the news still shows |
| After closing the phone | a fresh inventory opens, as the inventory key would open it (the creative inventory for a player now in creative); the old one is not kept |
| Creative inventory | no button: that side belongs to the creative tabs, and the phone key works there |
| Pressed | no separate sprite: the hover sprite stays until the phone starts opening |

In a 480 x 270 window the survival inventory (176 x 166) has `guiLeft = 152`, `guiTop = 52`: the quest button is at `(130, 60)` and the phone button at `(130, 98)`.

With the recipe book open, vanilla moves the inventory right and draws the book (147 x 166 at `((W - 147) / 2 - 86, (H - 166) / 2)`; centred over the inventory in a window narrower than 379 px) over the button. In the 480 x 270 window the inventory has `guiLeft = 229` and the button is at `(207, 98)`; the book covers `x 207..226` of the button's `x 207..233`, so only its right 7 px light up and take clicks.

| Part (tab-local) | Pixels | Colour |
|---|---|---|
| Outline | column `x 0` and `x 26`, rows `y 0` and `y 27`; left corners 3-step, right corners 1-step | `outline` |
| Highlight | row `y 1`, column `x 1` | `body-hi` |
| Shade | row `y 26`, column `x 25` | `body-lo` |
| Body | the rest | `body` |
| Screen rim | ring `x 6..20`, `y 3..18` | `screen-rim` |
| Screen | `x 7..19`, `y 4..17` (13 x 14) | see states |
| Home ring | 5 x 5 at `x 11..15`, `y 20..24`, corners cut | ring `home-ring`, centre 3 x 3 `home-fill` |

| State | Screen | Extra |
|---|---|---|
| Normal | `screen-off`, glare `(17,5) (18,5) (18,6)` in `glare` | |
| Normal hover | as normal | outline `accent` |
| New notifications | `accent`; row `y 4` `accent-hi`; four 3 x 3 `#FFFFFF` dots at `x 9..11` / `x 14..16`, `y 7..9` / `y 12..14` | pixel badge (6.1) with the total unread count at `(x - 3, y - 3)`, above the tab and the quest bracket but under the recipe book when it is open; pulses when the count rises |
| New notifications hover | as above | outline `accent` |

---

## 12. Settings app and placement

Built-in app, always last on the home grid, graphite gear icon (7.4). It holds the placement switch and two toggles. The three settings are stored per Minecraft account in `config/lnbphone-client.json` on the player's computer, never on the server, so two accounts on one computer each keep their own; they apply at once.

| Element | Value (app-local, dp) |
|---|---|
| Header | title `Réglages` (`app.settings`), root screen, no back |
| Section label | `TÉLÉPHONE` (`settings.section.phone`) in `caption-strong` `text-2` at `x 12`, line box `y 56` |
| Position label | `Position à l'écran` (`settings.position`) in `body-strong` at `x 12`, line box `y 76` |
| Position control | segmented control 232 x 32 at `(12, 100)`: `Coin` / `Centre` (`settings.position.corner` / `.centre`); **default Coin** (bottom-right) |
| Divider | full width at `y 144` |
| Sounds row | one-line 48 dp row at `y 145..192`: `Sons` (`settings.sounds`), toggle (default on) |
| Divider | 1 dp `hairline` at `y 193`, from `x 12` to the right edge |
| Preview row | one-line 48 dp row at `y 194..241`: `Aperçu des notifications` (`settings.preview`), toggle (default on); off disables the peek only; banners inside the open phone, badges, LED and chime still work |
| Divider | 1 dp `hairline` at `y 242`, from `x 12` to the right edge |
| Row spacing | the two rows are **49 dp apart**: each is 48 dp and the hairline between them is a separate divider, not the row's own (which 8.7 puts inside its 48 dp) |
| Change of position | the device glides from the old position to the new one in 250 ms, ease-out; the centred scrim fades in or out over the same 250 ms |

---

## 13. Language and UI strings

### 13.1 Language rule

- **English is the default.** French is shown for the game languages `fr_fr`, `fr_ca` and `fr_ch` (the French locales of Minecraft 1.21.1); every other language gets English.
- French remains the language the strings were written in and the language of the mockups, since most players are French.
- Strings live in the mod's language files (`en_us.json` as the fallback, `fr_fr.json` for French). Minecraft itself falls back to `en_us` only, so the build of this repository's mods copies `fr_fr.json` to `fr_ca.json` and `fr_ch.json` unless a mod ships its own. Third-party apps provide both languages too.
- French typography: a no-break space (U+00A0) before `:` `?` `!` and inside `1,2 k`; the ellipsis character `…` in placeholders.
- Player content (real names, usernames, post text) is never translated.

### 13.2 String table

| ID | Français | English | Where |
|---|---|---|---|
| `app.feed` | Feed | Feed | home label line, banner |
| `app.settings` | Réglages | Settings | home label line, Settings header |
| `tab.announcements` | Annonces | Announcements | Feed tab, Compose destination |
| `tab.global` | Global | Global | Feed tab, Compose destination |
| `post.anonymous` | Anonyme | Anonymous | anonymous display name, reply target |
| `post.pinned` | Épinglé | Pinned | pinned meta line |
| `post.reply_to` | En réponse à {name} | Replying to {name} | reply-to line, expanded composer |
| `post.own_anonymous` | Publié anonymement par toi | Posted anonymously by you | private line, author |
| `post.op_author` | Auteur : {name} {handle} | Author: {name} {handle} | private line, operators ({handle} omitted without a real name) |
| `post.op_author.tooltip` | Visible uniquement par les opérateurs | Only operators can see this | operator line tooltip |
| `badge.announcer` | Peut publier des annonces | Can post announcements | announcer badge tooltip |
| `action.pin` | Épingler | Pin | pin button tooltip |
| `action.unpin` | Désépingler | Unpin | filled pin button tooltip |
| `action.more` | Plus d'options | More options | more button tooltip |
| `menu.delete` | Supprimer | Delete | more menu item |
| `time.now` | à l'instant | now | relative time, under 1 min |
| `time.minutes` | {n} min | {n}m | under 1 h |
| `time.hours` | {n} h | {n}h | under 24 h |
| `time.days` | {n} j | {n}d | under 7 days |
| `time.date` | {d} {mois} (`12 nov.`) | {d} {Mon} (`12 Nov`) | 7 days and older; year added when not the current year |
| `time.full` | {HH:mm} · {date} (`14:32 · 12 nov.`) | {HH:mm} · {date} (`14:32 · 12 Nov`) | thread main post, 24-hour in both |
| `count.thousands` | {n},{d} k (`1,2 k`) | {n}.{d}k (`1.2k`) | action row counts |
| `feed.new_posts` | 1 nouveau post / {n} nouveaux posts | 1 new post / {n} new posts | new posts pill, Global |
| `feed.new_announcements` | 1 nouvelle annonce / {n} nouvelles annonces | 1 new announcement / {n} new announcements | new posts pill, Annonces |
| `feed.end` | Tu as tout vu | You're all caught up | end of list |
| `feed.unavailable.title` | Feed indisponible | Feed unavailable | error state, error snackbar |
| `feed.unavailable.text` | Réessaie dans un instant. | Try again in a moment. | error state |
| `error.retry` | Réessayer | Retry | error state, snackbar action |
| `empty.global.title` | Aucun post pour l'instant | No posts yet | Global empty state |
| `empty.global.text` | Lance la conversation ! | Be the first to post! | Global empty state |
| `empty.global.button` | Écrire un post | Write a post | Global empty state |
| `empty.announcements.title` | Aucune annonce | No announcements | Annonces empty state |
| `empty.announcements.text` | Les annonces du serveur apparaîtront ici. | Server announcements will show up here. | Annonces empty state |
| `thread.title` | Publication | Post | thread header |
| `thread.no_replies` | Aucune réponse pour l'instant | No replies yet | thread |
| `thread.deleted` | Ce post a été supprimé | This post was deleted | snackbar |
| `reply.placeholder` | Répondre à {name}… | Reply to {name}… | collapsed composer |
| `reply.anonymous` | Anonyme | Anonymous | composer toggle label |
| `reply.send` | Répondre | Reply | composer button |
| `reply.sent` | Réponse envoyée | Reply sent | snackbar |
| `compose.post` | Publier | Post | Compose button |
| `compose.placeholder` | Quoi de neuf sur le serveur ? | What's new on the server? | Compose text area |
| `compose.anonymous` | Publier anonymement | Post anonymously | Compose toggle row |
| `compose.anonymous.help` | Nom masqué aux joueurs | Name hidden from players | Compose toggle row |
| `compose.anon_sub` | Masqué aux joueurs | Hidden from players | Compose author row, anonymous |
| `compose.posted` | Publié | Posted | snackbar |
| `snackbar.send_failed` | Échec de l'envoi | Couldn't send | error snackbar |
| `snackbar.action_failed` | Action impossible | Couldn't do that | error snackbar |
| `pin.done` | Post épinglé | Post pinned | snackbar |
| `pin.removed` | Post désépinglé | Post unpinned | snackbar |
| `dialog.cancel` | Annuler | Cancel | dialog cancel button |
| `dialog.pin_limit.title` | Remplacer une épingle ? | Replace a pin? | pin-limit dialog |
| `dialog.pin_limit.text` | 3 posts sont déjà épinglés. Le plus ancien sera désépinglé. | 3 posts are already pinned. The oldest one will be unpinned. | pin-limit dialog |
| `dialog.pin_limit.confirm` | Épingler | Pin | pin-limit dialog |
| `dialog.delete_post.title` | Supprimer ce post ? | Delete this post? | delete dialog |
| `dialog.delete_post.text` | Ses réponses seront aussi supprimées. C'est définitif. | Its replies will be deleted too. This can't be undone. | delete dialog |
| `dialog.delete_reply.title` | Supprimer cette réponse ? | Delete this reply? | delete dialog |
| `dialog.delete_reply.text` | C'est définitif. | This can't be undone. | delete dialog |
| `dialog.delete.confirm` | Supprimer | Delete | delete dialog |
| `delete.post_done` | Post supprimé | Post deleted | snackbar |
| `delete.reply_done` | Réponse supprimée | Reply deleted | snackbar |
| `notif.announcement` | Nouvelle annonce | New announcement | banner title |
| `notif.reply` | Nouvelle réponse | New reply | banner title |
| `notif.text` | {name} : {text} | {name}: {text} | banner text |
| `settings.section.phone` | TÉLÉPHONE | PHONE | Settings section label |
| `settings.position` | Position à l'écran | Position on screen | Settings |
| `settings.position.corner` | Coin | Corner | Settings segmented control |
| `settings.position.centre` | Centre | Centre | Settings segmented control |
| `settings.sounds` | Sons | Sounds | Settings |
| `settings.preview` | Aperçu des notifications | Notification preview | Settings |
| `inventory.tooltip` | LNB Phone | LNB Phone | inventory button |
| `inventory.tooltip.unread` | 1 nouvelle notification / {n} nouvelles notifications | 1 new notification / {n} new notifications | inventory button, second line |
| `gui.scale` | Échelle GUI : 2+ | GUI scale: 2+ | app viewport at GUI scale 1 (bitmap font) |

### 13.3 Sizing for the longer language

Estimates use Inter at the given style: about 7.3 dp per character for 13 dp SemiBold, 6.8 dp for 13 dp Regular, 6.2 dp for 12 dp Regular, 8 dp for 14 dp SemiBold.

| Slot | Longer string | Width | Rule |
|---|---|---|---|
| Feed tab (128 dp) | `Announcements`, about 96 dp | fits with the unread dot (+10 dp) | indicator fixed 48 dp |
| Compose `Publier` / `Post` | `Publier`, about 51 dp | button **88 dp** | fixed |
| Composer `Répondre` / `Reply` | `Répondre`, about 60 dp | button **96 dp** | fixed |
| Empty `Écrire un post` / `Write a post` | `Écrire un post`, about 100 dp | button **136 dp** | fixed |
| `Réessayer` / `Retry` | `Réessayer`, about 66 dp | button **104 dp** | fixed |
| Dialog cancel `Annuler` / `Cancel` | `Annuler`, about 52 dp | button **88 dp** | fixed |
| Dialog confirm `Supprimer`, `Épingler` | `Supprimer`, about 66 dp | button **104 dp** | fixed |
| Menu item (168 dp, label from `x 40`) | `Supprimer`, about 62 dp | fits | |
| Settings segments (116 dp each) | `Corner` / `Centre`, about 46 dp | fits | fixed by the control |
| Compose toggle label (184 dp) | `Publier anonymement`, about 139 dp | fits | |
| Compose helper (184 dp) | `Name hidden from players`, about 150 dp | fits on one line | |
| Settings row label (184 dp) | `Aperçu des notifications`, about 175 dp | fits | |
| Banner title (164 dp) | `New announcement` / `Nouvelle annonce`, about 128 dp | fits | |
| New posts pill | `{n} nouvelles annonces`, about 145 dp + icon | fits (pill about 189 dp) | |
| Private line (168 dp from `x 76`) | `Posted anonymously by you`, about 156 dp | fits; operator line truncates | handle first, then name |
| Tooltips (max 200 dp) | `Visible uniquement par les opérateurs`, about 230 dp | wraps to 2 lines | up to 2 lines |
| Empty-state texts (200 dp) | `Server announcements will show up here.` | wraps to 2 lines | up to 2 lines |
| Dialog texts (200 dp) | `Its replies will be deleted too. This can't be undone.` | wraps to 2 lines | up to 3 lines |
| Collapsed composer placeholder (208 dp) | `Répondre à` + a long name | truncates with `…` | |
| Pixel label line (120 px) | `Réglages` / `Settings`, 8 characters | fits | app names of 16 characters or fewer recommended (7.2); the line cuts a longer name at 20 characters or 120 px |
| Pixel `gui.scale` line (128 px) | `Échelle GUI : 2+`, 16 characters, about 92 px | fits | |

---

## 14. Motion

The game ticks every 50 ms. Durations are whole ticks. Animations interpolate with `(tick + partialTick)`, use quadratic easing only. The pixel shell rounds positions to whole GUI px every frame (halves to the even neighbour, 2.4). The smooth layer may use fractional dp positions and opacity.

| Motion | Duration | Easing | Detail |
|---|---|---|---|
| Open | 250 ms | ease-out | device slides up from `wy = H` to its placement; the centred scrim fades in over the same time. The phone's screen opens when the slide ends; the player can move during it (2.3) |
| Open from a peek | 250 ms | ease-out | from the peek position to the open position |
| Close | 200 ms | ease-in | slides down to `wy = H`; scrim fades out. The mouse is back at once and the player can move |
| Placement change | 250 ms | ease-out | old position to new |
| Open screen / forward (app from home, thread, Compose, banner target) | 150 ms | ease-out | new screen slides in from `x +256 dp` over the current one |
| Back | 150 ms | ease-in | current screen slides out to `x +256 dp`, revealing the previous one |
| Home button | instant | | |
| Tab switch | 150 ms | ease-out | the 48 dp indicator slides; content swaps at once |
| Segmented control | 150 ms | ease-out | selected pill slides |
| Reply composer expand / collapse | 150 ms | ease-out / ease-in | |
| Toggle | 100 ms | ease-out | knob slides, track colour switches at the midpoint |
| Like | 100 ms | ease-out | heart swaps to filled and goes 1.0 -> 1.2 -> 1.0 scale |
| Scroll to top (pill) | 250 ms | ease-out | |
| Post highlight | 600 ms hold, 400 ms fade | linear | |
| Cell removal (delete) | 150 ms | ease-in | cell height collapses to 0, its content fades out |
| Pinned re-sort | instant | | the moved post gets the post highlight |
| Menu | 100 ms fade in, closes at once | linear | |
| Dialog | 150 ms fade in, 100 ms fade out | linear | |
| Tooltip | 500 ms delay, appears at once | | |
| Snackbar | 150 ms in, 3000 ms hold (6000 ms with an action), 150 ms fade | ease-out / linear | |
| FAB lift with snackbar | 150 ms | ease-out | |
| New posts pill | 150 ms | ease-out | slides down from `y 36` to `y 52` with fade-in; fades out 150 ms |
| Scroll bar | 200 ms fade after 800 ms idle | linear | |
| Skeleton pulse | 1000 ms loop | linear | opacity 100% -> 60% -> 100% |
| Spinner | 800 ms per turn | linear | |
| Caret | 500 ms on / 500 ms off | step | |
| Peek rise / hold / sink | 200 / 4000 / 200 ms | ease-out / - / ease-in | to `wy = H - 72` (or `H - 112`) |
| In-phone banner | 150 ms down, 4000 ms hold, 150 ms up | ease-out / ease-in | |
| Badge pulse (pixel) | 1500 ms | step | fill alternates `badge-hi` / `badge` every 250 ms, 3 cycles |
| LED | while any badge | step | 500 ms `accent`, 1500 ms `body-lo` |
| Pressed states | min 100 ms | | |
| Hover | instant | | |

---

## 15. Sound

The phone's sounds are the mod's own sound events (`sounds.json`, client-side), each reusing the vanilla sound the table names at the same volume and pitch. They are the phone's own so that subtitles can say what they are: **only the notification chime has a subtitle** (`Phone notification` / `Notification du téléphone`); the interface sounds have none, as vanilla's button click has none. All are muted when the Settings `Sons` toggle is off, and follow the Master volume slider.

| Event | Phone sound event | Vanilla sound played | Volume | Pitch |
|---|---|---|---|---|
| Open | `lnbphone:phone.open` | `minecraft:item.spyglass.use` | 0.6 | 1.4 |
| Close | `lnbphone:phone.close` | `minecraft:item.spyglass.stop_using` | 0.6 | 1.4 |
| Tap (icon, button, row, toggle, tab, pin, menu item) | `lnbphone:ui.tap` | `minecraft:block.note_block.hat` | 0.35 | 2.0 |
| Like | `lnbphone:ui.like` | `minecraft:block.note_block.chime` | 0.3 | 2.0 |
| Post or reply sent | `lnbphone:ui.sent` | `minecraft:entity.experience_orb.pickup` | 0.4 | 1.2 |
| Notification | `lnbphone:notification` | `minecraft:block.note_block.chime`, twice, 100 ms apart | 0.6 | 1.26, then 1.68 |
| Error snackbar / disabled tap | `lnbphone:ui.error` | `minecraft:block.note_block.bass` | 0.3 | 0.7 |

Notifications that arrive in the same tick chime once (10.3).

---

## 16. Sprite list

Namespace `lnbphone`.

### 16.1 Pixel sprites (shell)

Folder `assets/lnbphone/textures/gui/sprites/`, 1 texel = 1 GUI px, nearest-neighbour. Nine-slice sprites get a `.png.mcmeta` with `"gui": {"scaling": {"type": "nine_slice", "width": W, "height": H, "border": B}}`.

| Sprite | Size (GUI px) | Scaling | Content |
|---|---|---|---|
| `phone/frame` | 140 x 228 | none | outline, bevel, body, rim, speaker; screen transparent; LED and home areas in `body` |
| `phone/home_button`, `_hover`, `_pressed` | 12 x 12 each | none | section 2.3 |
| `status/signal_0` .. `status/signal_4` | 11 x 7 each | none | 0 to 4 lit bars |
| `status/battery` | 13 x 7 | none | frame only |
| `wallpaper/dusk` | 128 x 196 | none | section 5, moon at `sx 106..113`, `sy 161..168` |
| `home/icon_frame` | 20 x 20 | none | outline ring, 2-step corners |
| `home/icon_hover` | 24 x 24 | none | 1 px `accent` ring |
| `home/badge` | 9 x 9 | nine-slice, border 4 | pixel badge |
| `home/badge_pulse` | 9 x 9 | nine-slice, border 4 | `badge-hi` fill |
| `inventory/button`, `_hover`, `_new`, `_new_hover` | 27 x 28 each | none | section 11 |
| `icon/feed`, `icon/messages`, `icon/wallet`, `icon/quests`, `icon/settings` | 16 x 16 each | none | glyphs, section 7.4 |

Code fills (no sprite): status strip, LED, battery fill, icon tile and bands, label line, page dots.

### 16.2 Smooth sprites (apps and banner)

Folder `assets/lnbphone/textures/gui/smooth/`. Standalone textures with linear filtering and no mipmaps, not GUI atlas sprites. All of them are drawn by the generator in `tools/sprites`.

Shapes (rounded rects, pills, circles, strokes, the avatar's circle) are not sprites: the drawing surface computes them at the screen's real resolution (section 8.1).

Shadows, at 2 px per dp, black with the 45% opacity baked in:

| Sprite | Size dp (px) | Nine-slice border dp | Content |
|---|---|---|---|
| `shadow/fab` | 72 x 72 (144 x 144) | none | soft round shadow for a 48 dp circle: 8 dp blur. Drawn centred on the circle and 2 dp lower |
| `shadow/sheet` | 48 x 48 (96 x 96) | 22 | soft shadow for radius 10 to 14 rects (banner, snackbar, menu, dialog): 8 dp blur, the 2 dp drop baked in; drawn 8 dp larger than the element on each side |

Icons and the spinner, white with alpha and tinted at draw time, at `<density>/icon<grid>/<name>.png` and `<density>/spinner.png`, where `<density>` is the pixels per dp: `1x`, `1_5x`, `2x`, `2_5x`, `3x`, `3_5x`, `4x` or `4_5x`, for GUI scale 2 to 9. An icon's size in pixels is its grid times the density, and it is drawn 1:1.

| Icons | Grid dp | Content |
|---|---|---|
| `icon12/megaphone_filled` | 12 | solid silhouette, announcer badge |
| `icon12/person` | 12 | stroke 1.5 dp, anonymous avatar at 16 dp |
| `icon16/reply`, `heart`, `heart_filled`, `pin`, `pin_filled`, `lock`, `more`, `trash`, `alert`, `arrow_up` | 16 | stroke 1.5 dp. `heart_filled` is filled and stroked, so it keeps the outline heart's size |
| `icon20/back`, `close`, `person`, `globe`, `megaphone`, `inbox` | 20 | stroke 2 dp |
| `icon24/compose` | 24 | stroke 2 dp |
| `icon28/person` | 28 | stroke 2 dp, anonymous avatar at 48 dp |
| `icon40/inbox`, `megaphone`, `globe` | 40 | stroke 2.5 dp, empty and error states |
| `spinner` | 20 | 90 degree arc on a 25% ring, 2 dp stroke, rotated at draw time (drawn at 16 dp inside buttons) |

Fonts: `assets/lnbphone/font/inter_regular.ttf`, `assets/lnbphone/font/inter_semibold.ttf`, with the OFL licence text alongside.

---

## 17. Interaction flows

Every flow is a numbered list of frames. Each step is one frame of the storyboard. A static frame cannot show motion, so the caption names the transition that leads to the next frame; where a step shows a frame in the middle of a transition, the state gives the exact position.

### 17.0 Conventions

**Frame types**

| Code | What is drawn | Scale |
|---|---|---|
| **W** | the whole game window, 480 x 270 GUI px (1920 x 1080 at GUI scale 4): flat backdrop (sky `#7EAEFA`, ground `#5DAA3C` from `wy 170`), a 182 x 22 `#8B8B8B` hotbar at `(149, 248)`, and the phone or the inventory | 2 CSS px per GUI px (960 x 540) |
| **P** | the device alone (140 x 228 GUI px) with its screen | GUI scale 3 (420 x 684 CSS px) |
| **P1** | the device alone at GUI scale 1 | 1 CSS px per GUI px |

Unless a step says otherwise: placement bottom-right (device at `(334, 36)` in W frames); French UI; the viewer is **Camille Roux `@Cam_Roux`**, a regular player who is neither operator nor announcer. Operator steps use **Lune Garnier `@Admin_Lune`** (operator and announcer); announcer steps use **Kael_Builder** (announcer by command, no real name). Sample content is in 18.3.

**Cursor**: the standard arrow cursor, its tip at the given point. "No cursor" means the mouse is captured by the game. In P frames, positions are app-local dp unless marked "GUI px"; in W frames, window GUI px.

**State shorthand**: "hover X" means X is drawn in its hover state with the cursor on it; "pressed X" means the pressed state, mouse button down.

### 17.1 Phone flows

**P-1 Open with the Up arrow** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | normal play, HUD, phone closed | no cursor | Playing. The player presses the Up arrow. |
| 2 | W | device mid-slide at `(334, 153)`, home screen on it | no cursor | The phone slides up from the bottom edge (250 ms, ease-out). The player can still move: the screen opens, and the mouse becomes free, when the slide ends. |
| 3 | W | device open at `(334, 36)`, home screen, nothing hovered | `(240, 135)` | Open on the home screen. The mouse is free; the player stands still. |

**P-2 Close the phone** (4 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | open on home | `(240, 135)` | Three ways to close: Esc, the Up arrow again, or a click outside the phone. |
| 2 | W | open on home, click marker on the world | `(120, 100)`, pressed | A click anywhere outside the device closes it. Clicks on the bezel do nothing. |
| 3 | W | device mid-slide at `(334, 153)` | no cursor | The phone slides down (200 ms, ease-in). The mouse is back at once. |
| 4 | W | normal play | no cursor | Closed. It will reopen on the screen it was closed on. |

**P-3 Open and close in the centred placement** (4 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | normal play, Settings has Centre selected | no cursor | Centred placement. The player presses the Up arrow. |
| 2 | W | device mid-slide at `(170, 146)`, scrim at half opacity (`#00000026`) | no cursor | The phone slides up to the centre while the scrim fades in (250 ms). |
| 3 | W | device at `(170, 21)`, scrim `#0000004D`, home | `(60, 135)` on the scrim | Open in the centre. A click on the dimmed area closes it. |
| 4 | W | device mid-slide down at `(170, 146)`, scrim half | no cursor | Closing slides it down and fades the scrim (200 ms). |

**P-4 Open from the inventory button, no news** (5 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | survival inventory open; quest button at `(130, 60)`, phone button `inventory/button` at `(130, 98)` | `(240, 200)` | The phone button sits under the quest book button. |
| 2 | W | phone button `inventory/button_hover`, vanilla tooltip `LNB Phone` | `(143, 112)` | Hover: accent outline and tooltip. |
| 3 | W | inventory gone, device mid-slide at `(334, 153)` showing the Feed Global screen (last screen) | no cursor | Click: the inventory closes (its crafting grid and cursor items go back) and the phone slides up (250 ms). |
| 4 | P | Feed, Global, as it was left | `(128, 200)` | The phone opens on the screen it was closed on. |
| 5 | W | inventory open again, phone closed | `(240, 200)` | Closing the phone (Esc) brings a fresh inventory back. |

**P-5 Open from the inventory button, with news** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | inventory open, phone button `inventory/button_new` with the pixel badge `3` at `(127, 95)` | `(240, 200)` | Unread notifications: the mini screen lights up and a red count appears. |
| 2 | W | `inventory/button_new_hover`, tooltip `LNB Phone` / `3 nouvelles notifications` (second line `#3A8DF0`) | `(143, 112)` | Hover shows how many. |
| 3 | P | home screen, Feed icon badge `3`, LED lit | `(128, 200)` | With news, the phone opens on the home screen so the badges show where it is (250 ms slide), when it was put away on home or on an app's root screen. Put away deeper in an app, it reopens there with the newest notification as a banner (11). |

**P-6 Home: hover, press, open an app** (5 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | home, nothing hovered, label line empty | GUI px `(70, 120)` (screen-local, empty grid area) | The home screen. Icons have no labels. |
| 2 | P | hover Feed (column 0, row 0): accent ring, label line `Feed` | GUI px `(16, 29)` (screen-local, icon centre) | Hover: a blue ring and the app's name on the line above the hills. |
| 3 | P | pressed Feed: icon 1 px lower, ring | same | Press: the icon sinks 1 px. |
| 4 | P | transition: Feed screen at `x +128 dp` (half-way) over the home screen | same | Release: the app slides in from the right (150 ms). |
| 5 | P | Feed, Annonces, loaded (F-1 step 2) | same | Feed opens on Annonces. |

**P-7 Leave an app** (9 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | thread of post 1, collapsed composer; hover back button (disc `overlay-pressed`) | `(22, 22)` | Inside an app: the back arrow sits top-left. |
| 2 | P | pressed back button (disc `overlay-pressed-2`) | `(22, 22)` | Press. |
| 3 | P | transition: thread at `x +128 dp`, Global feed revealed underneath | `(22, 22)` | The screen slides away to the right (150 ms). Backspace does the same when no text field is focused. |
| 4 | P | Feed, Global, scroll position kept | `(22, 22)` | Back on the previous screen. |
| 5 | P | Feed, Global; home button `phone/home_button_hover` | GUI px `(70, 218)` (device-local) | The round home button in the bezel. |
| 6 | P | `phone/home_button_pressed` | same | Press. |
| 7 | P | home screen, no hover | same | Home, with no animation. |
| 8 | W | from Feed Global: device mid-slide down at `(334, 153)` | no cursor | Inside an app, Esc closes the phone (200 ms); the app screen is kept. |
| 9 | W | after Up: device open at `(334, 36)` on Feed Global | `(240, 135)` | Up reopens it right where it was. |

**P-8 Notification while the phone is closed** (8 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | normal play, phone closed | no cursor | A new announcement is posted while the player is playing. |
| 2 | W | device mid-rise at `(334, 234)`, peek screen (wallpaper, home status bar, banner) | no cursor | The phone rises 72 px from the bottom-right with the chime (200 ms). |
| 3 | W | device at `(334, 198)`; banner: Feed icon, `Nouvelle annonce`, `Lune Garnier : La saison 2 commence samedi à…`; LED lit | no cursor | The banner holds for 4 s. Pressing Up now opens the announcement (P-9). |
| 4 | W | device mid-sink at `(334, 234)` | no cursor | Nothing pressed: the phone sinks away (200 ms). |
| 5 | W | later, after Up: device open at `(334, 36)` on home (the screen it was closed on), Feed badge `1`, LED lit | `(240, 135)` | Opening the phone later: a red badge on Feed and the LED blinking. |
| 6 | P | home, hover Feed with badge `1`, label `Feed` | GUI px `(16, 29)` | Hover the badged app. |
| 7 | P | Feed, Annonces, post 6 at the top with the post highlight | GUI px `(16, 29)` | Feed opens on Annonces with the new post highlighted (150 ms slide). The badge is cleared. |
| 8 | P | home (after the home button), Feed icon without badge, LED off | GUI px `(70, 218)` | Back home: no badge, LED off. |

**P-9 Open straight from the peek** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | W | peek at `(334, 198)`; banner `Nouvelle réponse`, `Mika Laurent : Je l'ai vu près du moulin, côté est !` | no cursor | Someone replied to Camille's post. The player presses Up during the peek. |
| 2 | W | device mid-rise at `(334, 117)`, screen showing the thread | no cursor | The phone rises from the peek to fully open (250 ms). |
| 3 | P | thread of post 8, scrolled to reply R4 with the post highlight; composer collapsed | `(128, 200)` | It lands on the reply. Feed's badge is cleared. |

**P-10 Notification while the phone is open on home** (5 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | home, nothing hovered | GUI px `(70, 120)` | Phone open on the home screen. |
| 2 | P | banner mid-slide at app-local `y -36` (half visible), over the icons | same | A notification: the banner slides down from the top (150 ms), with the chime. |
| 3 | P | banner at `(8, 4)`: `Nouvelle réponse` / `Mika Laurent : Je l'ai vu près du moulin, côté est !`; Feed badge `1` under it | same | The banner holds 4 s over the icons. |
| 4 | P | hover banner (fill `surface-3`) | `(128, 42)` | Hover. Pressed, the banner would freeze until released. |
| 5 | P | thread of post 8, reply R4 highlighted | `(128, 42)` | Click: the banner goes at once, the thread opens (150 ms slide) and the badge clears. |

**P-11 Notification while another app is open** (4 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Settings screen | `(128, 300)` | Phone open in Settings. |
| 2 | P | banner at `(8, 4)` over the Settings header: `Nouvelle annonce` / `Kael_Builder : Événement PvP dimanche à 15:00 à…` | same | The banner appears over any app. |
| 3 | P | banner mid-slide up at `y -36` | same | Not clicked: after 4 s it slides up (150 ms). |
| 4 | P | home (after the home button), Feed badge `1` | GUI px `(70, 218)` | The news waits as a badge on the home screen. |

**P-12 Settings: placement** (8 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | home, hover Réglages (last icon, column 4, row 0), label `Réglages` | GUI px `(112, 29)` | Settings is always the last app. |
| 2 | P | Réglages screen, `Coin` selected | `(128, 300)` | Settings: the phone position, sounds and notification preview. |
| 3 | P | hover `Centre` segment (label `text-1`) | `(186, 116)` | Hover the other position. |
| 4 | W | pill on `Centre`; device mid-glide at `(252, 28)`, scrim at half opacity (`#00000026`) | `(433, 117)` (where `Centre` was when clicked) | Click: the phone glides to the centre while the scrim fades in (250 ms). |
| 5 | W | device at `(170, 21)`, scrim `#0000004D`, Réglages screen | `(433, 117)` | Now centred. The choice is saved for this player. |
| 6 | W | hover `Coin` segment | `(211, 102)` | Switching back. |
| 7 | W | device mid-glide at `(252, 28)`, scrim half | `(211, 102)` | It glides back to the corner (250 ms). |
| 8 | W | device at `(334, 36)`, no scrim, `Coin` selected | `(211, 102)` | Back in the corner. |

**P-13 Settings: sounds** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Réglages; `Sons` toggle on, hover (track `accent-hover`) | `(226, 169)` | The sound toggle, on by default. |
| 2 | P | `Sons` off (track `stroke`, knob at `x 2`), hover (track `stroke-hover`) | same | Off (the knob slides in 100 ms). |
| 3 | P | same, caption only | same | With sounds off the phone is silent, notification chime included. |

**P-14 Settings: notification preview** (5 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Réglages; `Aperçu des notifications` on, hover | `(226, 218)` | The notification preview controls the peek. |
| 2 | P | toggle off | same | Turned off. |
| 3 | W | normal play, phone closed, no peek | no cursor | A notification arrives: nothing rises. The chime still plays if sounds are on. |
| 4 | W | after Up: phone open on home, Feed badge `1`, LED lit | `(240, 135)` | The news waits as a badge. |
| 5 | P | phone open on home; another notification arrives: banner at `(8, 4)` over the icons | `(128, 200)` | Banners inside the open phone still show; only the peek is off. |

**P-15 GUI scale 1** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P1 | home at GUI scale 1, hover Feed | GUI px `(16, 29)` | At GUI scale 1 the phone and its home screen still work. |
| 2 | P1 | Feed opened: status bar, viewport `bg` with the bitmap line `Échelle GUI : 2+` centred in `#96A0B0` | same | Apps need GUI scale 2 or more; the app area says so. |
| 3 | P1 | home button pressed, home screen | GUI px `(70, 218)` | The home button still works, and so does Backspace. A notification here gives no banner and no peek, only the chime and the badge (10.2). |

### 17.2 Feed flows

**F-1 Open Feed: loading, then Annonces** (2 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Feed: tabs (Annonces active, indicator at `x 40..87`), 3 skeleton posts at 60% opacity | `(128, 200)` | First open of the session: skeletons pulse while posts load. |
| 2 | P | Annonces: pinned posts 5 and 9, then 6 and 7; Global tab with its unread dot | same | Loaded: pinned announcements first, then the newest. |

**F-2 Empty feeds** (4 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Annonces empty: megaphone disc, `Aucune annonce`, `Les annonces du serveur apparaîtront ici.` | `(128, 300)` | No announcements yet (players see no compose button here). |
| 2 | P | Global empty: inbox disc, `Aucun post pour l'instant`, `Lance la conversation !`, button `Écrire un post`, FAB | same | Global is empty: two ways to write the first post. |
| 3 | P | hover `Écrire un post` (fill `accent-hover`) | on the button | Hover. |
| 4 | P | Compose, empty, focused | on the button position | Click: Compose slides in (150 ms). |

**F-3 Feed unavailable** (7 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Annonces, skeletons | `(128, 300)` | Feed is loading... |
| 2 | P | error state: globe disc, `Feed indisponible`, `Réessaie dans un instant.`, `Réessayer` | same | ...but the Feed service does not answer. |
| 3 | P | hover `Réessayer` (fill `overlay-hover`) | on the button | Hover Retry. |
| 4 | P | `Réessayer` loading (accent spinner, 104 dp) | on the button | Click: the button spins while Feed retries. |
| 5 | P | skeletons | same | The list reloads. |
| 6 | P | Annonces loaded | same | Back to normal. |
| 7 | P | Global with posts shown from the cache; error snackbar `Feed indisponible` + `Réessayer`; FAB lifted to `y 262` | `(128, 200)` | If posts were already loaded, they stay and a snackbar offers a retry. |

**F-4 Switch tabs and the unread dot** (6 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Annonces active; Global label with the unread dot | `(128, 200)` | Global has posts Camille hasn't seen: a blue dot. |
| 2 | P | hover Global tab (label `text-1`, `overlay-hover`) | `(192, 22)` | Hover the Global tab. |
| 3 | P | Global active, indicator at `x 168..215`, dot gone, posts 8, 1, 2, 3, 4 | same | Click: the indicator slides (150 ms) and the dot clears. |
| 4 | P | Global active; banner `Nouvelle annonce` / `Kael_Builder : Événement PvP…`; Annonces label now with the dot | `(128, 200)` | A new announcement while on Global: a banner and a dot on Annonces. |
| 5 | P | hover Annonces tab | `(64, 22)` | Back to Annonces. |
| 6 | P | Annonces active; post 10 first after the pinned group, highlighted; dot gone | same | The new announcement is highlighted; the dot clears. |

**F-5 Scroll and new posts** (8 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global at the top | `(128, 200)` | The top of Global. |
| 2 | P | Global scrolled 300 dp; scroll bar thumb visible (hover `#FFFFFF59`) | same | Scrolling: 36 dp per wheel notch; a thin bar appears on the right. |
| 3 | P | same scroll; pill `2 nouveaux posts` at `y 52` | same | Two posts arrive. The list does not jump; a pill appears. |
| 4 | P | hover pill (fill `accent-hover`) | `(128, 66)` | Hover the pill. |
| 5 | P | mid-scroll (list at 150 dp), pill at 50% opacity | same | Click: the list scrolls to the top (250 ms). |
| 6 | P | at the top, 2 new posts (N1, N2) highlighted | same | The new posts are highlighted for a moment. |
| 7 | P | scrolled to the end: last post, then a spinner row | `(128, 200)` | At the end, older posts load. |
| 8 | P | last post, then `Tu as tout vu`, then empty space | same | Nothing older: the end of the feed. |

**F-6 Like and unlike** (6 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; post 3 action row, hover like: heart and `8` in `like`, `like-soft` disc | on post 3's heart | Hover the heart. |
| 2 | P | pressed like (`like-soft-2` disc) | same | Press. |
| 3 | P | liked: `heart_filled`, count `9`, both `like`; heart at 1.2 scale | same | Liked: the heart fills with a small pop (100 ms) and the count goes up at once. |
| 4 | P | liked, hover (disc `like-soft`) | same | Hover again. |
| 5 | P | unliked: `heart`, `8`, hover colours | same | Click again: unliked. |
| 6 | P | heart back to its previous state; error snackbar `Action impossible` | same | If the server refuses, the heart goes back and a snackbar says so. |

**F-7 Open a thread** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; hover post 1 (cell `overlay-hover`) | `(150, 70)` on post 1's text | Hover a post. |
| 2 | P | pressed post 1 (`overlay-pressed`) | same | Press. |
| 3 | P | thread of post 1: main post with `14:32 · 12 nov.`, replies R1, R2, R3, collapsed composer `Répondre à Alexandre Morel…` | same | The thread slides in (150 ms): the post, its replies, and a reply field at the bottom. |

**F-8 Write and send a reply** (8 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | thread of post 1; hover collapsed field (border `stroke-hover`) | `(128, 338)` | Hover the reply field. |
| 2 | P | composer expanded: `En réponse à Alexandre Morel`, empty focused text area with caret, counter `0/280`, toggle off, `Répondre` disabled | same | Click: the composer opens (150 ms) and is ready to type. |
| 3 | P | text `J'arrive dans 5 min`, counter `19/280`, `Répondre` enabled | same | Typing. |
| 4 | P | hover `Répondre` (fill `accent-hover`) | `(196, 342)` | Hover Reply. |
| 5 | P | `Répondre` loading; text area sending (text `text-2`, no caret); toggle disabled | same | Sending: the button spins. |
| 6 | P | composer collapsed; new reply R5 (Camille) at the end, highlighted; snackbar `Réponse envoyée` | same | Sent: the reply appears at the end of the thread. |
| 7 | P | (failure branch, from step 5) composer expanded with the draft, `Répondre` enabled; error snackbar `Échec de l'envoi` + `Réessayer` | same | If sending fails, the draft stays and Retry is offered. |
| 8 | P | hover snackbar `Réessayer` (`accent-soft`) | on `Réessayer` | Retry sends it again (back to step 5). |

**F-9 Reply anonymously** (4 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | expanded composer with text `Il y en a encore ?`; hover the `Anonyme` toggle (off hover) | `(30, 342)` | Before sending, the Anonymous toggle. |
| 2 | P | toggle on | same | On: this reply will be anonymous. |
| 3 | P | `Répondre` loading | `(196, 342)` | Send. |
| 4 | P | new reply at the end: anonymous avatar, `Anonyme`, private line `Publié anonymement par toi`, more button; snackbar `Réponse envoyée` | same | Posted as Anonymous. Only Camille sees the line saying it is hers. |

**F-10 Reply from the feed, and to a reply** (5 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; hover reply on post 1 (icon and `3` in `accent`, `accent-soft` disc) | on post 1's reply icon | The reply button on a post. |
| 2 | P | thread of post 1 with the composer expanded and focused, `En réponse à Alexandre Morel` | same | Click: the thread opens with the composer ready (150 ms slide). |
| 3 | P | in the thread, hover reply on R1 (Mika Laurent) | on R1's reply icon | Replying to a reply instead. |
| 4 | P | composer retargeted: `En réponse à Mika Laurent` | same | The composer now targets Mika. |
| 5 | P | after sending: new reply with reply-to line `En réponse à Mika Laurent` above its text | `(128, 200)` | The reply joins the same thread and says whom it answers. |

**F-11 Compose a post (player)** (12 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; hover FAB (`accent-hover`) | `(220, 334)` | The compose button. |
| 2 | P | pressed FAB (`accent-pressed`) | same | Press. |
| 3 | P | Compose: author row Camille Roux / `@Cam_Roux`, empty text area focused, placeholder, `0/280`, toggle off, `Publier` disabled | same | Compose slides in (150 ms), ready to type. |
| 4 | P | text = 18.3 text C1 cut at 142 characters, counter `142/280` `text-2`, `Publier` enabled | `(128, 200)` | Typing. |
| 5 | P | 265 characters, counter `265/280` `warning` | same | The counter turns amber in the last 20 characters. |
| 6 | P | 285 characters, counter `-5` `danger`, the last 5 characters on `danger-soft`, `Publier` disabled | same | Over 280: red, the excess is marked, Publish is off. |
| 7 | P | 270 characters, counter `270/280` `warning`, `Publier` enabled | same | Trimmed back under the limit. |
| 8 | P | hover the anonymous row toggle (off hover) | `(226, 330)` | The Post anonymously option. |
| 9 | P | toggle on; author row: anonymous avatar, `Anonyme`, `Masqué aux joueurs` | same | On: the author row shows how others will see it. |
| 10 | P | hover `Publier` (`accent-hover`) | `(200, 22)` | Publish. |
| 11 | P | `Publier` loading; text area sending; toggle and close disabled | same | Sending. |
| 12 | P | Global at the top, the new anonymous post first, highlighted, with `Publié anonymement par toi`; snackbar `Publié`; FAB lifted | same | Published: back to Global (150 ms) with the post on top. |

**F-12 Publishing fails** (4 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Compose with text, `Publier` loading | `(200, 22)` | Publishing... |
| 2 | P | Compose unchanged, `Publier` enabled; error snackbar `Échec de l'envoi` + `Réessayer` | same | It failed. Compose stays open with the draft. |
| 3 | P | hover `Réessayer` in the snackbar | on `Réessayer` | Retry. |
| 4 | P | `Publier` loading again | same | It publishes again (then F-11 step 12). |

**F-13 Cancel Compose and keep the draft** (3 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Compose with text; hover close (disc `overlay-pressed`) | `(22, 22)` | Close without publishing. |
| 2 | P | Global (after the 150 ms back slide) | same | Back to the feed, no question asked. |
| 3 | P | Compose reopened from the FAB with the same text and settings | `(220, 334)` | The draft is still there. |

**F-14 An announcer composes** (8 steps; viewer Kael_Builder)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Annonces with the FAB (announcers only); hover FAB | `(220, 334)` | Announcers get a compose button on Annonces. |
| 2 | P | Compose: author row `Kael_Builder` alone (no handle), text area 152 dp, anonymous slot empty, segmented `Annonces` selected | same | Compose opens with Annonces chosen. No anonymous option for announcements. |
| 3 | P | hover `Global` segment | `(186, 332)` | The destination switch. |
| 4 | P | `Global` selected; the anonymous row appears | same | For Global, posting anonymously is possible. |
| 5 | P | anonymous toggle on | `(226, 286)` | Turned on. |
| 6 | P | `Annonces` selected again; anonymous row hidden; toggle is off | `(70, 332)` | Back to Annonces: the option hides and switches off. |
| 7 | P | text `Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn.`, `Publier` loading | `(200, 22)` | Publishing the announcement. |
| 8 | P | Annonces, new post at the top of the non-pinned list, highlighted; snackbar `Publié` | same | It is out; every player gets a New announcement notification. |

**F-15 Pin an announcement** (7 steps; viewer Lune Garnier, operator)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Annonces; pinned posts 5 and 9; post 6 with the pin button (outline) in its action row | `(128, 200)` | Announcers and operators see a pin button on announcements. |
| 2 | P | hover post 6's pin (accent, `accent-soft` disc); tooltip `Épingler` above it | on post 6's pin | Hover: Pin. |
| 3 | P | post 6 now in the pinned group (3 pins), highlighted, `pin_filled` in `accent`; snackbar `Post épinglé` | same position | Click: it joins the pinned posts at the top. |
| 4 | P | 3 pinned (6, 5, 9); hover the pin on post 7 | on post 7's pin | A fourth one. |
| 5 | P | dialog `Remplacer une épingle ?` / `3 posts sont déjà épinglés. Le plus ancien sera désépinglé.`, `Annuler`, `Épingler` | same | The limit is 3: confirm replacing the oldest. |
| 6 | P | hover `Épingler` in the dialog | on the confirm button | Confirm. |
| 7 | P | pinned group 7, 6, 5; post 9 back in date order; post 7 highlighted; snackbar `Post épinglé` | same | The oldest pin drops back into the list. |

**F-16 Unpin** (3 steps; viewer Lune Garnier)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | pinned post 7; hover its filled pin; tooltip `Désépingler` | on post 7's pin | Hover a filled pin: Unpin. |
| 2 | P | post 7 back in date order, outline pin; snackbar `Post désépinglé` | same position | Click: it returns to its place. No confirmation. |
| 3 | P | thread of post 5 (pinned): main post with the filled pin in its action row | on the pin | The same button exists on the post in its thread. |

**F-17 What an operator sees on an anonymous post** (4 steps; viewer Lune Garnier)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; post 2 (anonymous) with the private line `Auteur : Mika Laurent @MikaPvP` and a more button | `(128, 300)` | Operators see who wrote an anonymous post, in grey with a lock. |
| 2 | P | hover the private line; tooltip `Visible uniquement par les opérateurs` (2 lines) | on the line | Only operators see this. |
| 3 | P | thread of post 1: reply R2 (anonymous) shows the private line `Auteur : SteveBuilds` | `(128, 200)` | Anonymous replies get the line too (here without a handle: SteveBuilds has no real name). |
| 4 | P | the same post 2 as Camille (regular player): no private line, no more button | same | Everyone else sees only Anonymous. |

**F-18 Delete your own post** (8 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; post 8 (Camille's own) with the more button; hover it (disc `overlay-pressed`); tooltip `Plus d'options` | `(234, 19)` relative to post 8 | Your own posts have a "more" button. |
| 2 | P | menu open under it: `trash` + `Supprimer` in `danger`; hover the item | on `Supprimer` | One option: Delete. |
| 3 | P | dialog `Supprimer ce post ?` / `Ses réponses seront aussi supprimées. C'est définitif.`, `Annuler`, `Supprimer` (danger) | `(128, 200)` | A confirmation, because it cannot be undone. |
| 4 | P | hover `Supprimer` (`danger-hover`) | on the danger button | Confirm. |
| 5 | P | `Supprimer` loading | same | Deleting. |
| 6 | P | dialog gone; post 8's cell half collapsed, content at 50% | same | The post folds away (150 ms). |
| 7 | P | Global without post 8; snackbar `Post supprimé` | same | Deleted. |
| 8 | P | (cancel branch, from step 3) hover `Annuler`; then the feed unchanged | on `Annuler` | Cancel, Esc or a click on the dark area leaves everything as it was. |

**F-19 An operator deletes a reply** (4 steps; viewer Lune Garnier)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | thread of post 1; every reply has a more button; menu open on R2 | on `Supprimer` | Operators can delete any post or reply. |
| 2 | P | dialog `Supprimer cette réponse ?` / `C'est définitif.` | `(128, 200)` | Confirm. |
| 3 | P | R2 collapsing | same | It folds away. |
| 4 | P | thread without R2; R3's reply-to line still reads `En réponse à Anonyme`; snackbar `Réponse supprimée` | same | Deleted. Replies to it stay. |

**F-20 Feed notifications and where they land** (6 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | Global; post 8 (Camille's) with the unseen-replies marker: dot at `x 50`, reply icon and `2` in `accent` | `(128, 200)` | Your own post got replies you haven't read: the reply count turns blue with a dot. |
| 2 | P | Global; banner `Nouvelle réponse` / `Mika Laurent : Je l'ai vu près du moulin, côté est !` | same | A new reply arrives while browsing. |
| 3 | P | hover banner | `(128, 42)` | Hover. |
| 4 | P | thread of post 8 scrolled to R4, highlighted | same | Click: straight to the reply (150 ms slide). |
| 5 | P | Global after back: post 8's marker gone | `(22, 22)` | Back: the marker has cleared. |
| 6 | P | from a `Nouvelle annonce` banner: Annonces at the top, the announcement highlighted | `(128, 42)` | A new-announcement banner lands on Annonces with the post highlighted. |

**F-21 A thread's post is deleted while you read it** (2 steps)

| # | Frame | State | Cursor | Caption |
|---|---|---|---|---|
| 1 | P | thread of post 3 | `(128, 200)` | An operator deletes this post while Camille reads it. |
| 2 | P | Global (after the 150 ms back slide), post 3 gone; snackbar `Ce post a été supprimé` | same | Back to the feed, with a note. |

### 17.3 Flow index

| Flow | Steps |
|---|---|
| P-1 Open with the Up arrow | 3 |
| P-2 Close the phone | 4 |
| P-3 Centred placement open and close | 4 |
| P-4 Inventory button, no news | 5 |
| P-5 Inventory button, with news | 3 |
| P-6 Home: hover, press, open an app | 5 |
| P-7 Leave an app | 9 |
| P-8 Notification while closed | 8 |
| P-9 Open from the peek | 3 |
| P-10 Notification over home | 5 |
| P-11 Notification over another app | 4 |
| P-12 Settings: placement | 8 |
| P-13 Settings: sounds | 3 |
| P-14 Settings: notification preview | 5 |
| P-15 GUI scale 1 | 3 |
| F-1 Open Feed | 2 |
| F-2 Empty feeds | 4 |
| F-3 Feed unavailable | 7 |
| F-4 Tabs and unread dot | 6 |
| F-5 Scroll and new posts | 8 |
| F-6 Like and unlike | 6 |
| F-7 Open a thread | 3 |
| F-8 Write and send a reply | 8 |
| F-9 Reply anonymously | 4 |
| F-10 Reply from the feed, and to a reply | 5 |
| F-11 Compose a post | 12 |
| F-12 Publishing fails | 4 |
| F-13 Cancel Compose, keep the draft | 3 |
| F-14 An announcer composes | 8 |
| F-15 Pin an announcement | 7 |
| F-16 Unpin | 3 |
| F-17 Operator view of an anonymous post | 4 |
| F-18 Delete your own post | 8 |
| F-19 An operator deletes a reply | 4 |
| F-20 Feed notifications and landing | 6 |
| F-21 Post deleted while reading | 2 |
| **Total** | **186** |

---

## 18. Mockup brief (storyboard canvas)

The canvas is the storyboard of section 17, plus a few reference sheets. The user validates it before any code is written.

### 18.1 How to render

- **P frames: GUI scale 3.** 1 GUI px = 3 CSS px, 1 dp = 1.5 CSS px. **W frames:** 2 CSS px per GUI px, the phone inside drawn at the same 2x (1 dp = 1 CSS px). **P1 frames:** 1 CSS px per GUI px.
- Pixel shell: every texel as specified, nearest-neighbour, the Minecraft bitmap font for shell text.
- App layer: HTML/CSS/SVG in a 256 x 370 dp box over the viewport, clipped. Inter 400 and 600, font sizes in dp x scale / 2. Icons as SVG strokes (8.6). Avatars: the face grids below, nearest-neighbour inside a circle.
- Hairlines: 1 dp rounded to whole CSS px.
- Each flow is one row of frames in step order, titled with its code and name (17.3), with each frame's caption under it. Draw the cursor where a step gives one; mark a click with a small ring around the cursor tip on "pressed" steps.
- French UI throughout, except reference sheet 5.

### 18.2 Reference sheets (before the flows)

1. **Home** (P): the 5 example apps on row 0, Settings hovered (`Réglages` on the label line), Feed with badge 3; the moon visible.
2. **Feed screens** (P): Annonces (as Camille), Global (as Camille), thread of post 1 (as Lune, operator, so R2 shows its private line), Compose (as Camille).
3. **Name layouts** (P, one post each): real name and handle (post 1); no real name (post 3); a 32-character real name truncated (`Marie-Charlotte de La Villemarqué`, `@MCVillemarque`); anonymous as a regular player; anonymous as its author; anonymous as an operator.
4. **Kit sheet** (P scale): every component of 8.7 in every listed state, the announcer badge, the icon set at 12/16/20/24/40 dp, the type scale.
5. **English check** (P at GUI scale 2): Feed Global in English UI (post text stays as written), to check legibility and the longer English strings.

### 18.3 Sample content

| # | Where | Display name | Handle | Author role | Time | Text | Replies | Likes |
|---|---|---|---|---|---|---|---|---|
| 1 | Global | Alexandre Morel | @Alex_Craft | player | 2 min | Des diamants à la mine du spawn, couche -54. Premier arrivé, premier servi ! | 3 | 12 (liked by the viewer) |
| 2 | Global | Anonyme (author Mika Laurent @MikaPvP) | | player | 5 min | Merci à celui qui a laissé tourner la ferme à creepers près du marché, j'ai de la poudre à canon pour un mois. | 4 | 31 |
| 3 | Global | SteveBuilds | (none) | player, no real name | 1 h | J'ouvre ma boutique ce soir à 21:00 près de la porte nord. Kits redstone, livres enchantés et quelques teintures rares. Venez tôt, le stock est petit ! | 0 | 8 |
| 4 | Global | Mika Laurent | @MikaPvP | player | 3 h | gg à tous à l'arène | 0 | 0 |
| 8 | Global | Camille Roux | @Cam_Roux | the viewer | 20 min | Quelqu'un a vu mon loup ? Il s'appelle Biscuit et il porte un collier bleu. | 2 (unseen) | 4 |
| 5 | Annonces, pinned 2 nov. | Lune Garnier | @Admin_Lune | operator, announcer | 2 j | Règlement mis à jour : lisez /rules avant vendredi. Le grief dans la zone du marché vaut désormais 3 jours de ban. | 6 | 54 |
| 9 | Annonces, pinned 28 oct. | Lune Garnier | @Admin_Lune | operator, announcer | 26 oct. | Bienvenue sur le serveur ! Le guide des débutants est au spawn, derrière la fontaine. | 12 | 88 |
| 6 | Annonces | Lune Garnier | @Admin_Lune | operator, announcer | 1 j | La saison 2 commence samedi à 18:00. L'End sera réinitialisé à 17:00. | 2 | 40 |
| 7 | Annonces | Kael_Builder | (none) | announcer by command | 3 j | Maintenance cette nuit de 02:00 à 02:30, le serveur redémarrera deux fois. | 0 | 17 |
| 10 | Annonces (F-4, F-14) | Kael_Builder | (none) | announcer by command | à l'instant | Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn. | 0 | 0 |
| N1, N2 | Global (F-5) | Alexandre Morel / Mika Laurent | @Alex_Craft / @MikaPvP | players | à l'instant | Quelqu'un pour une partie de spleef ? / Je suis partant ! | 0 | 0 |
| R1 | thread of 1 | Mika Laurent | @MikaPvP | player | 1 min | J'arrive ! | 0 | 1 |
| R2 | thread of 1 | Anonyme (author SteveBuilds) | | player | à l'instant | Il en reste encore ? | 0 | 0 |
| R3 | thread of 1, reply to R2 | Alexandre Morel | @Alex_Craft | player | à l'instant | Oui, deux filons côté est. (reply-to line `En réponse à Anonyme`) | 0 | 2 |
| R4 | thread of 8 | Mika Laurent | @MikaPvP | player | à l'instant | Je l'ai vu près du moulin, côté est ! | 0 | 0 |

Thread of post 1: main post timestamp `14:32 · 12 nov.` Text C1 (F-11): `Qui est chaud pour une virée dans le Nether ce soir ? Rendez-vous au portail du spawn à 21:00. Prenez des potions de résistance au feu, des blocs pour faire des ponts et de quoi manger, on risque d'y passer un bon moment avant de trouver une forteresse.` (repeat or cut it to reach the counts in F-11).

Faces (8 x 8):

```
Steve            Alex             Mika             Lune
HHHHHHHH         OOOOOOOO         KKKKKKKK         PPPPPPPP
HHHHHHHH         OOOOOOOO         KKKKKKKK         PPPPPPPP
HSSSSSSH         OSSSSOOO         KKSSSSKK         PPSSSSPP
SSSSSSSS         SSSSSSSO         KSSSSSSK         PSSSSSSP
SWBSSBWS         SWGSSGWO         SWESSEWS         PWESSEWP
SSSNNSSS         SSSSSSSO         SSSSSSSS         PSSSSSSP
SSMSSMSS         SSSMMSSS         SSSMMSSS         PSSMMSSP
SSMMMMSS         SSSSSSSS         SSSSSSSS         PSSSSSSP

Steve: H #2F1F0F  S #B4846A  W #FFFFFF  B #523D89  N #87563C  M #6A4030
Alex:  O #D9772B  S #F2C8A0  W #FFFFFF  G #4E8A3A  M #C88E6F
Mika:  K #1C1C22  S #C68E6A  W #FFFFFF  E #2B6E4F  M #8A5038
Lune:  P #7A5CE6  S #E8BFA0  W #FFFFFF  E #3A8DF0  M #B07A60
Kael:   Mika's grid with K = #6B3A1E
Camille: Alex's grid with O = #2F1F0F
```

Faces by username: SteveBuilds = Steve, Alex_Craft = Alex, MikaPvP = Mika, Admin_Lune = Lune, Kael_Builder = Kael, Cam_Roux = Camille.

---

## 19. Open questions and assumptions

Accepted defaults: the app is called "Feed", the clock shows in-game time, the battery shows hunger.

**Confirmed by the user on 2026-11-22**, after the storyboard canvas:

1. Without a real name, the Minecraft username becomes the bold name and no handle is shown.
2. Deleting is in version 1: players delete their own posts and replies, operators delete anything, with a confirmation. Deleting a post deletes its replies. Nothing can be restored.
3. Pinning: limit of 3 pinned announcements; pinning a 4th asks to replace the oldest.
4. The player cannot walk while the phone is open in version 1 (it is a normal game screen). They can still move during the 250 ms slide that precedes it (2.3).

**Shown on the storyboard canvas and not objected to** (built this way unless the user says otherwise):

1. The post header is two lines (name, then `@handle`), because a real name, a 16-character handle and the time do not fit on one line in 188 dp. Long real names truncate; the handle truncates only in the worst case, sixteen wide letters after the `@` (9.3). Warden caps real names at 64 characters.
2. Any announcer can pin or unpin any announcement.
3. Esc closes the phone even inside an app (after closing a menu or dialog); the phone reopens on the same screen. With unread notifications, the inventory button opens the home screen instead, but only from home or an app's root screen; from deeper in an app it reopens that screen, since drafts win over badges (11).
4. A Feed badge clears when Feed is opened. Replies to your own posts stay marked in the feed (blue reply count) until you open the thread.

**Open for the Feed phase** (found while building the phone; to settle when Feed is designed in detail):

1. Emoji and other scripts. Inter has no emoji and no non-Latin glyphs (8.4), so a post with an emoji shows one missing-glyph box per emoji in the apps, while the notification banner drops emoji. Whether to bundle an emoji font, and with what size, licence and colour handling, is open. The 280-character count already counts an emoji as one character (8.7).

**Known slips in the Feed sample data** (found while drawing the canvas; to fix before the Feed app is built, they do not affect the phone):

1. F-4.3 lists Global as posts 8, 1, 2, 3, 4, but post 8 is 20 minutes old; newest first is 1, 2, 8, 3, 4.
2. P-8.7 puts post 6 "at the top" although pinned posts 5 and 9 sit above it, and dates it `1 j` although it was just announced. P-8 notifies post 6 while F-4, F-14 and F-20 use post 10 as the new announcement.
3. F-7.3 promises replies R1 to R3 visible; with the reply field docked at `y 306`, only R1 fits under the main post.
4. Post 8 has 2 unseen replies but only R4 has text; post 5 has 6 replies with no text.
5. The "32-character" name `Marie-Charlotte de La Villemarqué` is 33 characters and has no face or post text.
6. F-18.8 asks for two states in one frame.
