# Testing LNB Phone

Until now the phone has been checked by unit tests and by an automated client taking screenshots, with the sound
muted, one player, and no other mod. Nobody has played with it. The first part of this file is the plan for people in
the game before a release; the second describes the automated checks.

## In-game test plan

Build with `./gradlew build`. You need:

- a dedicated NeoForge 21.1 server (21.1.264 or later) with Kotlin for Forge 5.12 or later,
  `phone/build/libs/lnbphone-<version>.jar` and `apps/demo/build/libs/lnbphone_demo-<version>.jar`, and nothing else;
- two clients, players A and B (two Minecraft accounts), with the same three mods, the sound on, the master volume up;
  A is an operator. A third client without the phone, for item 18.

The demo mod is never shipped, but it holds what these tests use: Ping (send a short message to a player who is
online; they get it in their conversation with you, with a notification), Hello (every click opens one more screen,
a right click opens a vanilla dialog), the "UI kit" gallery, the sample Feed and `/phonedemo` (a test notification
from Hello). Items 1 to 9 can be done alone in single player with `./gradlew :apps:demo:runClient`. Items 26 to 29
need the server's real modpack.

Each item says what to do and what should be seen; anything else is a failure to report, with the log
(`logs/latest.log` of the client or the server) and a screenshot (F2).

### The phone alone

1. **Start.** Both clients start without an error. The mod list shows "LNB Phone" and "LNB Phone Demo". In Controls,
   the "LNB Phone" category has "Open the phone" on the Up arrow. The client log lists the apps:
   `Phone apps (5): lnbphone_demo:hello, lnbphone_demo:kit, lnbphone_demo:feed_sample, lnbphone_demo:ping, lnbphone:settings`.
2. **Open and close.** In a world, press Up: the phone slides up into the bottom-right corner in a quarter of a second,
   with a soft spyglass sound. The world stays visible, neither darkened nor blurred, and the game isn't paused. The
   mouse is free and the player can't walk. Close it each way: Esc, Up again, a click on the world. It slides down
   with the spyglass's closing sound. A click on the bezel does nothing.
3. **Held keys.** Hold Up: the phone opens once and stays open, without flickering; hold it again: it closes once.
   In Hello, a few screens deep, hold Backspace: the phone goes back one screen only, not to the first. In Ping's
   field, hold Backspace: the text is erased character after character, and the screen stays.
4. **Home and status bar.** Hovering an icon draws a blue ring and writes the app's name above the hills; pressing
   sinks it one pixel; releasing opens the app, which slides in from the right. The clock shows the in-game time
   (`06:00` at dawn) and `--:--` in the Nether. The signal bars are all dim in single player and lit on the server
   (fewer with a high ping). The battery follows hunger: green when fed, orange at 6 food points or less, red at 3 or
   less; full in creative.
5. **Moving around.** Open Hello and click: a new screen slides in each time. Backspace goes back one screen; on the
   first screen it does nothing. In "UI kit", the header's back arrow does the same. The round home button returns
   home at once. Inside an app, Esc puts the phone away; Up brings it back on the same screen, with lists where they
   were scrolled. Leave the world and come back: the phone opens on the home screen.
6. **A vanilla dialog.** In Hello, right click: a vanilla confirmation screen replaces the phone. Press either button:
   the phone is back, open on the same Hello screen, without sliding in again, and works (click, Backspace, Esc). A
   failure is a phone that slides in anew, stays blank, or ignores the mouse.
7. **Both placements.** Open Settings (the last icon): "Position on screen", "Corner" / "Centre". Choose Centre: the
   phone glides to the middle of the screen while the world dims; a click on the dimmed area closes it; it opens and
   closes in the centre. Back to Corner: it glides back. Quit the game and start it again: the choice is kept. Log in
   with the other account on the same computer: it has its own choice.
8. **Typing.** In Ping, open your own name and type in the field. Bind "Open the phone" to a letter (P) and type a word
   with that letter: the letter is typed, the phone stays. Esc puts the phone away and the text is still there when it
   comes back. Also: Shift+arrows, Ctrl+A, Ctrl+C / Ctrl+X / Ctrl+V (paste a long text: it stops at 100 characters),
   Ctrl+Backspace, a double click on a word, Tab to move between the field and the button, Enter to send. Keys that
   mean something in the game (E, T, Q, the hotbar digits) type into the field and do nothing else. In "Feed
   (sample)", Compose (the round button): Ctrl+Enter publishes, the counter turns amber in the last 20 characters and
   red past 280.
   **Emoji.** In Compose, open Windows' emoji panel (Win+.) and pick a few emoji, one with a skin tone, a family,
   the French flag, a keycap (1️⃣) and the red heart: each appears as a white outline emoji at the size of the text,
   on the same line, never as a box (the panel sends them through the game's character input as pairs of halves).
   The caret stands after the emoji just picked; Left and Right step over a whole emoji, flag or family, and one
   Backspace deletes it whole; a double click on a word next to an emoji selects the word. A toned emoji draws as its
   plain emoji (the font has one colour). The counter counts 2 for a flag and for a toned emoji, 1 for a plain one.
   Long text full of emoji wraps between emoji, never inside a family. In Ping, send a ping with emoji: the
   conversation shows them. "UI kit" has emoji pages at the end of its list (text area, post card, every kind of
   sequence in Regular and SemiBold).
9. **A French keyboard (AZERTY).** Same field, Windows with a French layout: a dead key (`^` then `e` gives `ê`, `¨`
   then `i` gives `ï`), and AltGr characters `@` (AltGr+à), `#` (AltGr+") and `€` (AltGr+E): each types its character
   and nothing else, although Windows reports AltGr as Ctrl+Alt (a failure: the text is all selected, or the character
   doesn't come). Ctrl+A, Ctrl+C, Ctrl+V and Ctrl+X still select, copy, paste and cut: on AZERTY they are the keys
   labelled A, C, V and X.

### Two players

10. **Ping.** On the server, A opens Ping: A ("You") and B are listed with their skins. A opens B, writes, presses
    Enter: "Ping sent", a short experience-orb sound, the ping on the right. A sends another within a second: "Wait a
    moment" with an error sound, and the text is back in the field. B pings A back; both conversations show both
    sides.
11. **Notifications on B's side**, each in turn:
    - phone closed: it rises a little from the bottom-right corner with a banner (Ping's pink icon, "New ping",
      "A: the text") and two rising chimes, stays 4 seconds, sinks; the phone's light blinks. Press Up while it shows:
      the phone opens on the conversation with A; back leads to the list of players;
    - phone open on the home screen: the banner slides down over the icons and holds 4 seconds; Ping's icon has a
      badge `1`; a click on the banner opens the conversation; the badge is gone. Press on the banner and hold: it
      stays until you let go;
    - phone open in another app ("UI kit"): the banner shows over it;
    - phone open on the conversation with A: no banner, the chime only, and the ping appears in the conversation;
      afterwards no badge waits on the home screen;
    - several pings in a row while the banner shows: its text is replaced, and each one chimes;
    - `/phonedemo notify B` then, at once, `/phonedemo clear B` (A): the banner or the peek goes away early.
    Long texts end with an ellipsis on the banner's second line. A ping with emoji (typed with the emoji panel, see
    item 8): the banner shows them as in the conversation, in the text's grey; a ping in Japanese reads `?`.
12. **Offline and restarts.** B leaves; A opens B's conversation and sends: "B isn't online", the text stays in the
    field. With B away, `/phonedemo notify B 3` twice (A). B joins: no banner and no sound at login, a badge `2` on
    Hello, the light blinks. Open it: Hello opens on "Hello 3", and back leads to 2 then 1. Again, but stop the server
    (`stop`) and start it before B joins: the badges are still there. B pings A while A's phone is closed; A doesn't
    open it, leaves and joins again: no Ping badge waits (the text was in A's session, which ended), and A's Ping has
    forgotten its conversations. `/phonedemo clear B`: the badges go. Ten notifications (`/phonedemo notify B`
    repeated): the badge shows `9+`.
13. **Settings for notifications.** "Notification preview" off: with the phone closed nothing rises, the chime plays,
    the badge waits; with the phone open, banners still show. "Sounds" off: no sound at all, not even the chime, the
    taps or the spyglass. Both back on.
14. **When nothing may rise.** A notification while B has the chat open (or any other screen): no peek, the chime only.
    B presses F1 (hidden HUD): a notification gives no peek, and F1 also hides a phone that is sliding. At GUI scale 1,
    a notification gives no banner and no peek, only the chime and the badge. Open the pause menu during a peek: the
    peek goes away. A chat line arriving during a peek: the banner is drawn over the chat text, while the phone's
    body stays under the hotbar and the hearts.
15. **The inventory button.** Open the survival inventory: a small phone sits on its left edge. Hover: a blue outline
    and the tooltip "LNB Phone". Click: the inventory closes and the phone opens on the screen it was left on; closing
    the phone brings the inventory back. With unread notifications, the button's screen lights up with the count, the
    tooltip adds "2 new notifications" in blue, and the count pulses when it rises. A click with unread notifications
    opens the phone on the home screen if it was put away on home or on an app's first screen; if it was put away
    deeper in an app (in a conversation with a draft), it reopens there, the draft intact, and the newest unread
    notification shows as the banner. The creative inventory has no button.
16. **Items while the inventory closes.** Put items in the 2 x 2 crafting grid, and hold another stack on the cursor,
    then click the phone button: the phone opens, and when it closes the inventory that comes back has every item in
    it, none on the ground, none lost. Open the recipe book: it covers most of the button; the part it covers neither
    lights up nor takes a click, and the uncovered part works.
17. **Sounds.** Nobody has heard them yet. With the game's own sounds around (walk on gravel, open a chest, a note
    block), listen to each and say if one is too loud, too quiet or grating next to them: opening and closing
    (spyglass, pitched up), a tap on an icon, a button or a row (a high hi-hat), a ping sent (experience orb), a click
    on a disabled button such as Send with an empty field (a low bass note), a notification (two chimes a fifth
    apart). They follow the Master volume slider. Turn subtitles on (Accessibility): the notification shows
    "Phone notification", the other phone sounds show nothing. The subtitle box may cover part of the peek's banner
    for a moment; that is known.
18. **A player without the phone.** The third client, without `lnbphone`, tries to join: it is refused at once with a
    message naming the missing mod or its channels, and the server log shows the refusal without an error. A failure
    is a client that joins and then errors, or a server that logs an exception.
19. **Dedicated server.** The server starts and stops without an error, and its log never mentions a client class
    (`Attempted to load class net/minecraft/client`, `invalid dist`). With both players doing the above, the server
    log stays clean.

### In French

Set both clients to French (Options, Language, Français) and redo items 2, 7, 10, 11 and 15 quickly. Every text of
the phone, Settings, Ping, the banner, the inventory tooltip and the controls screen is in French; no raw key such as
`settings.lnbphone.sounds` shows anywhere; French typography holds (`Ping envoyé`, a space before `:` in `A : …`).
Then set "Français (Canada)": the same French. Last, open Settings, put the phone away with Esc, and switch the
game back to English: the phone opens on the home screen, and Settings, opened again, is in English. A draft typed in
Ping before the switch is still in its field.

### Screens, machines and resource packs

20. **GUI scales.** At GUI scale 2, 3 and 4, the text inside apps is sharp and the shapes have clean edges. Change the
    scale while the phone is open: it moves to its new place and stays usable. At GUI scale 1 the phone and its home
    screen work, and an app shows "GUI scale: 2+". On a 1440p or 4K screen with "Auto" (scale 5 to 9, never captured):
    the shell stays crisp pixel art (no blurred or doubled pixels on the device, the icons, the clock), the text in
    apps stays sharp and evenly spaced, and lines that should be one pixel stay even. A failure looks like soft text,
    a 1-pixel line drawn 2 pixels thick on one side, or icons misaligned with their ring.
21. **Small windows.** Make the window as small as Minecraft allows: the phone fits in the corner and in the centre.
    In a narrow window, a notification with the phone closed rises higher, clear of the hotbar and the food bar.
    Toggle fullscreen (F11) with the phone open.
22. **Warm-up.** Join a world and, as soon as you can, have a notification sent (the peek) and open the phone, then
    change the GUI scale and open it again: no stutter on the first peek or the first open.
23. **A long list.** With the F3 screen's frame time showing, open a list of hundreds of rows (the sample Feed
    scrolled to the end, or Ping's list on a full server) and scroll fast with the wheel and the thumb: the frame time
    stays where it is with the phone closed. A failure is a drop that grows with the number of rows.
24. **Other systems.** As far as the team can: macOS (Option moves by words in a text field, Cmd is not Ctrl), Linux,
    and a laptop with an integrated GPU (Intel or AMD). Everything above should look and behave the same; a failure
    is usually blank or black shapes in apps, or a crash when the phone opens (the log names a shader).
25. **Resource packs.** A pack that replaces Minecraft's default font: the shell (clock, label line, badges) uses it,
    the apps keep Inter. A pack that replaces Inter (`assets/lnbphone/font/inter_regular.ttf`) or an app's glyph
    (`assets/lnbphone_demo/textures/gui/ping.png`, at 16 x 16 and at 32 x 32): the new font or picture shows; a glyph of
    another size is fitted, with a warning in the log; nothing crashes. Reload with F3+T while the phone is open.
    A pack that replaces the emoji font (`assets/lnbphone/font/noto_emoji.ttf`) with a broken file: an error in the
    log, and emoji come from the phone's own copy; nothing crashes.

### In the server's real modpack

The server runs about forty mods (Create, JEI, Jade, Emotecraft, Distant Horizons, `lnb-quests`, `lnb-economy`, LNB
Warden and more), with their own client settings. Install the phone on that modpack, server and client.

26. **Keys.** In Controls, look for conflicts with "Open the phone" (Up arrow): a conflicting binding is shown in red
    in both places, and pressing the key then does both things. Press Up in the world, in each mod's screens, and with
    JEI's search field focused: the phone opens only from the world, and nothing else reacts to that press. Rebind it
    if a mod needs Up, and check the phone follows.
27. **Next to the quest book.** Open the survival inventory: the phone's button sits under the quest book's button of
    `lnb-quests`, both fully visible, both clickable, the badges of each drawn over the other's bracket without
    hiding it. With JEI's side panels shown (and its bookmarks on the left), the phone button stays visible and
    clickable, or a panel covers it entirely: report which, with a screenshot.
28. **HUD and screens of other mods.** With a minimap in the bottom-right, Jade's tooltip, Distant Horizons and
    Emotecraft's wheel: a peek rising in the corner doesn't hide something important, and doesn't sit under another
    HUD element. Open another mod's screen (Create's, a backpack, JEI's recipe view) while the phone is open, and while
    it is rising: the other screen shows, the phone is put away, and nothing stays drawn over it.
29. **Shaders.** With the server's shader pack on (Iris or Oculus, if the modpack has one): the phone, its apps and the
    peek draw as without shaders, not dimmed, tinted or hidden by the world's effects.

## Automated checks

### Unit tests

`./gradlew build` runs them; `./gradlew test` runs only them. They run with the game loaded (NeoForge's unit test
launcher), and cover what can be reasoned about without a window: the navigation model, the shell's state machine and
its handling of every key, notification alerts and the inventory button's logic, the layout maths, text layout and
editing, the kit's widgets and focus, the notification store, its limits and its sync, the settings file and the
Settings app (played through `PhoneTester`, the test support apps use too, in `phone/src/testing/`). The demo's tests
check Ping's session, target and conversation screen, and that every code sample of
`docs/writing-an-app.md` is still found in the file it quotes.

### Screenshots

```sh
./gradlew :apps:demo:runCapture -Pcapture=<scenario> [-PcaptureDir=<absolute directory>] [-PdemoApps=<n>] [-PcaptureSize=<w>x<h>]
```

starts a client that plays a scenario by itself (GUI scales, screens, clicks, keys and typing through the game's own
input handlers, a throwaway flat world) and quits; a watchdog kills it after 7 minutes. Shots go to `captureDir`, or
`apps/demo/run/captures/<scenario>/`. The window is 1280 x 960 unless `captureSize` says otherwise; `demoApps` fills
the home screen with sample icons. A fresh `apps/demo/run/` needs an `options.txt` containing
`onboardAccessibility:false` before the first run. The sound is muted during captures.

| Scenario | Flags | Shows |
|---|---|---|
| `smooth` | | the smooth layer's test sheet at GUI scale 2 to 4 and forced 5, 6 and 9, after a resource reload, over a world |
| `kit` | | every page of the kit's gallery and the composed Feed screens, two at a time, at GUI scale 2 to 4 |
| `kit-phone` | | the kit inside the phone, in a world: hover, a slide caught half way, the sample Feed scrolled, a menu, a dialog, Compose typed into and sent |
| `ping` | | Ping, the example app: a ping typed and sent, the peek and the banner of pings arriving, the badge, landing on the conversation, the two refusals; GUI scale 2 to 4 and French |
| `banner` | | the notification banner's states and frames; a real notification sent by `/phonedemo` and read back |
| `shell` | `-PdemoApps=6` | the phone's flows: open, close, the home screen, an app, back, the home button, the centred placement, badges, a click outside, GUI scale 1 |
| `shell-pages` | `-PdemoApps=35` | the home screen's second page |
| `shell-window` | `-PcaptureSize=1920x1080` | the art direction's whole-window frames, at 480 x 270 GUI px |
| `settings` | | the Settings app: placement, sounds, preview, at GUI scale 2 to 4, in French and English; ends centred and silent |
| `settings-window` | `-PcaptureSize=1920x1080` | the next launch after `settings`: both choices kept, then the placement's window frames |
| `notifications` | `-PdemoApps=6 -PcaptureSize=1920x1080` | notifications reaching the phone: peek, banner, badges, the inventory button, the preview off |
| `notifications-persist` | `-PdemoApps=6` | a notification kept across leaving the world and coming back |
| `emoji` | | emoji in a text area (caret after one), a field, a snackbar, a post card, rows, every kind of sequence in Regular and SemiBold, the banners, at GUI scale 2 to 4; two hundred distinct emoji at forced scale 9 |
| `notifications-review` | `-PdemoApps=6 -PcaptureSize=1920x1080` | the peek's banner over a chat line, with subtitles, under F1; the inventory button with news on an app's first screen and deeper; the crafting grid after the phone; the recipe book over the button |

Scenarios are in `apps/demo/src/main/kotlin/.../demo/client/capture/`; add one to `CaptureScenarios.kt`. Some click
home icons by their place, so a new app registered by the demo goes after the existing ones; `HomeSlots` finds an
icon by its app's id.

What the captures can't check: sounds, a second player, a dedicated server, other mods, real skins, scales above 4
outside forced shots, and how the phone feels in the hand. That is what the plan above is for.
