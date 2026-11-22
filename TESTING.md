# Testing LNB Phone

Until now the phone has been checked by unit tests and by an automated client taking screenshots, with the sound
muted and a single player. Nobody has played with it. The first part of this file is the plan for a person in the
game before a release; the second describes the automated checks.

## In-game test plan

Build with `./gradlew build`. You need:

- a dedicated NeoForge 21.1 server (21.1.264 or later) with Kotlin for Forge 5.12 or later,
  `phone/build/libs/lnbphone-<version>.jar` and `apps/demo/build/libs/lnbphone_demo-<version>.jar`;
- two clients, players A and B (two Minecraft accounts), with the same three mods, the sound on and the master volume
  up; A is an operator. Install lnb-quests on A's client as well, for its inventory button.

The demo mod is never shipped, but it holds what these tests use: Ping (send a short message to a player, who gets it
as a notification), Hello (every click opens one more screen), the "UI kit" gallery, the sample Feed and
`/phonedemo`. Items 1 to 6 can be done alone in single player with `./gradlew :apps:demo:runClient`.

1. **Start.** Both clients start without an error. The mod list shows "LNB Phone" and "LNB Phone Demo". In Controls,
   the "LNB Phone" category has "Open the phone" on the Up arrow. The client log lists the apps:
   `Phone apps (5): lnbphone_demo:hello, lnbphone_demo:kit, lnbphone_demo:feed_sample, lnbphone_demo:ping, lnbphone:settings`.
2. **Open and close.** In a world, press Up: the phone slides up into the bottom-right corner (a quarter of a second)
   with a soft spyglass sound. The world stays visible, neither darkened nor blurred, and the game isn't paused. The
   mouse is free and the player can't walk. Close it each way: Esc, Up again, a click on the world. It slides down
   with the spyglass's closing sound. A click on the bezel does nothing. Hold Up down: the phone opens once and stays
   open, without flickering.
3. **Home and status bar.** Hovering an icon draws a blue ring and writes the app's name above the hills; pressing
   sinks it one pixel; releasing opens the app, which slides in from the right. The clock shows the in-game time
   (`06:00` at dawn) and `--:--` in the Nether. The signal bars are all dim in single player and lit on the server
   (fewer with a high ping). The battery follows hunger: green when fed, orange at 6 food points or less, red at 3 or
   less; full in creative.
4. **Moving around.** Open Hello and click: a new screen slides in each time. Backspace goes back one screen; on the
   first screen it does nothing. In "UI kit", the header's back arrow does the same. The round home button returns
   home at once. Inside an app, Esc puts the phone away; Up brings it back on the same screen, with lists where they
   were scrolled. Leave the world and come back: the phone opens on the home screen.
5. **Both placements.** Open Settings (the last icon): "Position on screen", "Corner" / "Centre". Choose Centre: the
   phone glides to the middle of the screen while the world dims; a click on the dimmed area closes it; it opens and
   closes in the centre. Back to Corner: it glides back. Quit the game and start it again: the choice is kept. Log in
   with the other account on the same computer: it has its own choice.
6. **Typing.** In Ping, open your own name and type in the field. Then bind "Open the phone" to a letter (P) and
   type a word with that letter: the letter is typed, the phone stays. With text in the field, Backspace erases and
   never goes back; Esc puts the phone away and the text is still there when it comes back. Also try: Shift+arrows,
   Ctrl+A, Ctrl+C / Ctrl+V (paste a long text: it stops at 100 characters), Ctrl+Backspace, a double click on a word,
   accents and a dead key (`^` then `e` gives `ê` on a French keyboard), Tab to move between the field and the
   button, Enter to send. Keys that mean something in the game (E, T, Q, the hotbar digits) type into the field and do
   nothing else. In "Feed (sample)", Compose (the round button): Ctrl+Enter publishes, and the counter turns amber in the last
   20 characters and red past 280.
7. **Ping between two players.** On the server, A opens Ping: A ("You") and B are listed with their skins. A opens B,
   writes, presses Enter: "Ping sent", a short experience-orb sound, the ping on the right. On B's side, each in turn:
   - phone closed: it rises a little from the bottom-right corner with a banner (Ping's pink icon, "New ping",
     "A: the text") and two rising chimes, stays 4 seconds, sinks; the phone's light blinks. Press Up while it shows:
     the phone opens on the conversation with A, the ping on the left; back leads to the list of players;
   - phone open on the home screen: the banner slides down over the icons and holds 4 seconds; Ping's icon has a
     badge `1`; a click on the banner opens the conversation; the badge is gone;
   - phone open in another app ("UI kit"): the banner shows over it;
   - phone open on the conversation with A: no banner, the chime only, and the ping appears in the conversation;
     afterwards no badge waits on the home screen;
   - several pings in a row while the banner shows: the banner's text is replaced, one chime each time.
   Long texts end with an ellipsis on the banner's second line. A ping with an emoji shows it in the conversation (the
   font has no emoji; whatever replaces it, it must not break the layout).
8. **Notifications that wait.** B leaves. A pings B twice. B joins: no banner and no sound at login, a badge `2` on
   Ping, the light blinks. Again, but stop the server (`stop`) and start it before B joins: the badge is still there.
   `/phonedemo notify B 3` (A, operator) while B is away, then B joins and opens it: Hello opens on "Hello 3", and
   back leads to 2 then 1. `/phonedemo clear B`: the badge goes. Ten notifications (`/phonedemo notify B`, repeated):
   the badge shows `9+`.
9. **Settings for notifications.** "Notification preview" off: with the phone closed nothing rises, the chime plays,
   the badge waits; with the phone open, banners still show. "Sounds" off: no sound at all, not even the chime, the
   taps or the spyglass. Both back on.
10. **The inventory button.** Open the survival inventory: a small phone sits on the left edge, under the quest
    book's button (and in the same place without lnb-quests). Hover: a blue outline and the tooltip "LNB Phone".
    Click: the inventory closes and the phone opens on the screen it was left on; closing the phone brings the
    inventory back. With unread notifications the button's screen lights up with the count, the tooltip adds
    "2 new notifications" in blue, the count pulses when it rises, and a click opens the phone on the home screen.
    Open the recipe book: the button follows the inventory. The creative inventory has no button.
11. **Sounds.** Nobody has heard them yet. Listen to each and say if one is too loud, too quiet, or grating: opening
    and closing (spyglass, pitched up), a tap on an icon, a button or a row (a high hi-hat), a ping sent (experience
    orb), a click on a disabled button such as Send with an empty field (a low bass note), a notification (two
    chimes a fifth apart). They follow the Master volume slider.
12. **Dedicated server.** The server starts and stops without an error, and its log never mentions a client class
    (`Attempted to load class net/minecraft/client`, `invalid dist`). With both players connected and doing the above,
    the server log stays clean.
13. **GUI scales.** At GUI scale 2, 3 and 4 the text inside apps is sharp and the shapes have clean edges. Change the
    scale while the phone is open: it moves to its new place and stays usable. At GUI scale 1 the phone and its home
    screen work, and an app shows "GUI scale: 2+". With "Auto" on a large screen (scale 5 or more), the phone still
    looks right.
14. **Small windows.** Make the window as small as Minecraft allows: the phone fits in the corner and in the centre.
    In a narrow window, a notification with the phone closed rises higher, clear of the hotbar and the food bar.
    Toggle fullscreen (F11) with the phone open.
15. **Other screens.** With the chat open, a notification gives no peek (chime and badge only). Open the pause menu
    during a peek: the peek goes away. Die with the phone open: the death screen shows, and the phone is put away. In
    the sample Feed, open a menu (the three dots on a post) and press Esc: the menu closes, not the phone.
16. **What only the real game shows.** The first time the phone opens after the game starts, or after a GUI scale
    change, it prepares its fonts: no stutter should be noticeable. Faces on the list and in avatars are the players'
    real skins on the online server (the development client only has default skins). Typing with an input method for
    other scripts, and pasting from another program, put text in the field. On a Mac, Alt moves by words. Disconnect
    from a server while the phone is open on an app, and join another one: the phone starts from the home screen,
    and Ping has forgotten its drafts.

## Automated checks

### Unit tests

`./gradlew build` runs them; `./gradlew test` runs only them. They run with the game loaded (NeoForge's unit test
launcher), and cover what can be reasoned about without a window: the navigation model, the shell's state machine and
its handling of every key, the layout maths, text layout and editing, the kit's widgets and focus, the notification
store, its limits and its sync, the decision of what a notification shows, the settings file. The demo's tests also
check that every code sample of `docs/writing-an-app.md` is still found in the file it quotes.

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
| `ping` | | Ping, the example app: its icon, the list, a ping typed and sent, the notification it brings back opened at its target, the draft and the conversation kept; GUI scale 2 to 4 and French |
| `banner` | | the notification banner's states and frames; a real notification sent by `/phonedemo` and read back |
| `shell` | `-PdemoApps=6` | the phone's flows: open, close, the home screen, an app, back, the home button, the centred placement, badges, a click outside, GUI scale 1 |
| `shell-pages` | `-PdemoApps=35` | the home screen's second page |
| `shell-window` | `-PcaptureSize=1920x1080` | the art direction's whole-window frames, at 480 x 270 GUI px |
| `notifications` | `-PdemoApps=6 -PcaptureSize=1920x1080` | notifications reaching the phone: peek, banner, badges, the inventory button, the preview off |
| `notifications-persist` | `-PdemoApps=6` | a notification kept across leaving the world and coming back |

Scenarios are in `apps/demo/src/main/kotlin/.../demo/client/capture/`; add one to `CaptureScenarios.kt`. They click
home icons by their place, so a new app registered by the demo goes after the existing ones.

What the captures can't check: sounds, a second player, a dedicated server, real skins, and how the phone feels in the
hand. That is what the plan above is for.
