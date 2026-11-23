# Testing LNB Phone

Until now the phone and Feed have been checked by unit tests and by an automated client taking screenshots, with the
sound muted, one player, and no other mod. Nobody has played with them. The first part of this file is the plan for
people in the game before a release (the phone's items first, then Feed's); the second describes the automated checks.

## In-game test plan

Build with `./gradlew build`. You need:

- a dedicated NeoForge 21.1 server (21.1.264 or later) with Kotlin for Forge 5.12 or later,
  `phone/build/libs/lnbphone-<version>.jar` and `apps/demo/build/libs/lnbphone_demo-<version>.jar`, and nothing else
  (Feed's part below adds two jars);
- two clients, players A and B (two Minecraft accounts), with the same three mods, the sound on, the master volume up;
  A is an operator. A third client without the phone, for item 18.

The demo mod is never shipped, but it holds what these tests use: Ping (send a short message to a player who is
online; they get it in their conversation with you, with a notification), Hello (every click opens one more screen,
a right click opens a vanilla dialog), the "UI kit" gallery, "Post sample" (a feed of made-up posts built from the kit,
which is not the Feed app) and `/phonedemo` (a test notification from Hello). Items 1 to 9 can be done alone in single player with `./gradlew :apps:demo:runClient`. Items 26 to 29
need the server's real modpack.

Each item says what to do and what should be seen; anything else is a failure to report, with the log
(`logs/latest.log` of the client or the server) and a screenshot (F2).

### The phone alone

1. **Start.** Both clients start without an error. The mod list shows "LNB Phone" and "LNB Phone Demo". In Controls,
   the "LNB Phone" category has "Open the phone" on the Up arrow. The client log lists the apps:
   `Phone apps (5): lnbphone_demo:hello, lnbphone_demo:kit, lnbphone_demo:feed_sample, lnbphone_demo:ping, lnbphone:settings`
   (six, with `lnbphone_feed:feed`, when Feed's jar is there too).
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
   mean something in the game (E, T, Q, the hotbar digits) type into the field and do nothing else. In "Post
   sample", Compose (the round button): Ctrl+Enter publishes, the counter turns amber in the last 20 characters and
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
23. **A long list.** With the F3 screen's frame time showing, open a list of hundreds of rows ("Post sample"
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

### Feed

Feed has been checked by unit tests and by an automated client taking screenshots against its in-memory backend, with
the sound muted and one player. Nobody has played with it in a world, nobody has heard its sounds, and it has never run
against the real website with a person at the keyboard. This part is the plan for that. It adds to the setup above:
Feed's jar (`apps/feed/build/libs/lnbphone_feed-<version>.jar`) and LNB Warden's (`apps/feed/libs/lnbwarden-<version>.jar`,
0.5.0 or later) in the `mods` folder of the server and of both clients.

Items 30 to 37 use Feed's in-memory backend, so they need no website. Before the server's first start, put
`apps/feed/example-config/lnbphone_feed-server.toml` and `lnbwarden-server.toml` in its `config` folder
(`./gradlew :apps:feed:prepareMemoryRun` does it for `apps/feed/run/config`, which `:apps:feed:runServer` and
`:apps:feed:runClient` use). They select `backend.kind = "memory"` with the sample posts, and Warden's `auth.mode = "OFF"`
so that both accounts may join. A is an operator: in memory mode that makes A a moderator (the website's roles are not
there). B is a plain player. The sample posts are dated from the server's start, and nothing is kept when it stops.

Items 38 to 44 need the website: a site with the `feed` flag on, Warden's `api.url` and `api.token` set in
`lnbwarden-server.toml` (with `auth.mode` at `required` or `optional`), `backend.kind = "site"`, and the Minecraft
accounts of A and B linked to LNB accounts. The LNB website's repository says how to run it locally.

30. **Start.** The server and both clients start without an error. The server log has the warning `Feed uses its memory
    backend (sample seed): nothing is kept, for development only`, which is expected here. The mod list shows "LNB
    Feed"; the client log's list of phone apps holds `lnbphone_feed:feed`; the home screen has Feed's icon, a white glyph
    on an azure tile. A third client without Feed's jar is refused at once with a message naming the missing mod.
31. **Reading.** B opens Feed: it opens on "Announcements", with the pinned posts first, each under a "Pinned" line. A
    card shows a face, a name (a first name and an initial, bold), the handle in grey, the text, the time (`2m`, `3h`)
    and the reply and like counts. An anonymous post shows "Anonymous", a grey person and no handle. Scroll, then open
    "Global": the posts are newest first, and the end of the list reads "You're all caught up" (while a page loads, a
    spinner shows there). Press Up and Up again: the phone is back on the same tab, at the same place. Leave the world and
    join again: Feed opens afresh on Announcements, and no post from the last visit shows before the new list arrives.
32. **Posting.** B, on Global, presses the round button: Compose opens with the field focused. Type: the counter turns
    amber in the last 20 characters and red past 280, and "Post" is disabled when the text is empty or past 280. Press
    "Post" (or Ctrl+Enter): the button shows a spinner, then the snackbar says "Posted", and the post is at the top of
    Global, highlighted. A's phone, with Feed open on Global at the top, shows it at once, highlighted, with no reload.
    Scroll A's list down and have B post again: A's list does not move, a pill reads "1 new post" with an up arrow;
    a click scrolls up to it. Post four times within a minute: the fourth is refused with "Slow down! Wait N s" and the
    text stays in the field. Close Compose with text in it and open it again: the draft is still there.
33. **Replies and their notifications.** A posts on Global. B opens the post and replies, then replies to A's reply.
    With A's phone closed: it rises with the banner "New reply" and "B: the text", with two chimes; a click on the banner
    (or Up, then the banner) opens the thread, scrolled to the reply, which is highlighted. With A's phone open on the home
    screen or in another app: the banner shows over it, and Feed's icon has a badge. With A's phone open on that thread:
    no banner, the chime only, and the reply appears in the thread. B is never notified of B's own reply. A's reply to
    B's post notifies B the same way. Then A leaves; B replies; A joins: no banner and no chime at login, a `1` on Feed's
    icon, and opening Feed clears it. On A's card, a marker shows that the post has replies A has not read, until the
    thread has been opened.
34. **Announcements.** A (a moderator, so an announcer) presses the round button on "Announcements": Compose shows a
    control choosing between "Announcements" and "Global", with "Announcements" selected. A posts an announcement. B,
    with the phone closed, gets "New announcement" and "A: the text"; B, on Global with the phone open, gets the banner
    and a dot on the "Announcements" tab (the dot goes when the tab is shown). B, on Announcements, gets a pill without
    an arrow when the announcement would sit under the pinned posts, out of view. A is not notified of A's own
    announcement. Post an announcement anonymously: B's banner reads "Anonymous: the text", the card shows "Anonymous" with
    no badge, and A is not notified. An announcer's cards carry a violet badge, whose tooltip reads "Can post
    announcements". B, offline when it is posted, finds a badge at login and no banner.
35. **Likes.** B likes a post of A in both feeds: the heart fills at once and the count goes up. A, with Feed open on that
    post, sees the count move within a second, with no reload. B unlikes: the count goes back. Click the heart quickly
    a dozen times: the count never goes negative or counts one player twice, and a like the server refuses (too fast)
    goes back with "Slow down! Wait N s". Likes never notify anyone.
36. **Announcer and moderator.** A types `/feed` and the chat completes `announcer`; B has no `/feed` command. A runs
    `/feed announcer list`: "No announcers besides the moderators." `/feed announcer add B`: "B can now post
    announcements."; B's phone shows the round button on "Announcements" within a second, with no new login, and the
    destination control in Compose. `/feed announcer remove B` takes it away again. Adding an operator reads "A is a
    moderator, so already posts announcements." A pins an announcement from its more menu in the feed or with the pin
    button of its thread: "Post pinned", the post moves into the pinned group on B's open "Announcements" within a
    second. Pin until three are pinned, then a fourth: the dialog "Replace a pin?" appears, and confirming unpins the
    oldest. Unpin: "Post unpinned". A deletes a post of B's (more menu, "Delete", the dialog "Delete this post?"): it
    leaves B's open feed within a second, with its replies, and B, inside that thread, is taken back to the feed with
    "This post was deleted". B has no more menu on A's posts, and has one on B's own. A deletes an announcement while B is
    offline: B joins and finds no badge for it. On an anonymous post, A (moderator) sees a line "Author: B" with a tooltip
    "Only moderators can see this", B (the author) sees "Posted anonymously by you", and a third player sees neither.
37. **A guest, writing paused, and the site away.** Put B's username under `memory.guests` and restart: B has no round
    button on either tab; a click on a heart shows "LNB account needed" with the error sound and changes no count; a thread
    shows a strip "LNB account needed to reply" instead of the composer; B has no more or pin button, and still gets the
    announcements' banners. Remove B from the list and restart. `/feed dev writing off` (A, memory mode only): within
    seconds nobody has a round button, the thread strip reads "Writing is paused", and a heart click shows it too; `/feed
    dev writing on` brings everything back. `/feed dev offline on`: a Feed that is open keeps its list readable and
    shows "Connection lost" with "Retry"; a Feed opened afresh shows "Feed unavailable" with "Retry". `/feed dev offline
    off`: the open Feed recovers by itself within about 15 seconds, and "Retry" does it at once. `/feed dev disabled on`:
    a Feed opened afresh shows "Feed is switched off".
38. **A dedicated server restart.** Stop the server (`stop`) while B has Feed open on a thread: B returns to the menu with
    no error in either log. Start it again and join: Feed opens afresh on Announcements and shows the new server's
    content (in memory mode the sample again: what was posted is gone, and the announcers added by command with it),
    never a post or draft of the last visit. A notification that was delivered before the stop is still on the phone.
    With the site backend, redo it: the posts are still there, and a notification the site queued while the server was
    down is delivered when it is back (item 40).
39. **The website: posts made in the browser.** Signed in on the website with the LNB account of A, post, reply and like
    from the browser. With Feed open on A's phone, the post appears at the top of Global within a few seconds (or behind
    the pill), and the counts move with no reload. A post written in the game appears on the website's Feed page. A reply
    written in the browser to a post of B's notifies B in the game, with the same banner as item 33. An announcement
    written in the browser by an announcer notifies the players in the game, the author excepted.
40. **The website: a player who was offline.** B leaves. In the browser, an account other than B's replies to a post of B's
    and an announcer posts an announcement; B joins: Feed's icon shows a badge `2`, with no banner and no sound at
    login; opening Feed clears it and the reply and announcement are where the badge's targets said. Redo it with the
    Minecraft server stopped while the browser posts, then started: the notifications arrive within a few minutes of its
    start (a reconnection drains the queue at once), exactly once each.
41. **The website: the site off.** With Feed open on B's phone, stop the site (or block its address). B's list stays,
    readable, with "Connection lost" and "Retry". A Feed opened afresh shows "Feed unavailable". Joining the Minecraft
    server is as quick as ever, and its log says once that the site is unreachable instead of once for every call, and
    once that the event stream is disconnected. Start the site: within about a minute (the stream retries between 1 and
    30 seconds) Feed catches up, `Connected to LNB's Feed event stream` is in the log, and posts made meanwhile show
    without the list jumping, behind the pill if B has scrolled.
42. **The website: Feed off, writing off.** Switch the `feed` flag off on the site: a Feed opened afresh shows "Feed is
    switched off" with "Retry", an open one shows "Connection lost"; switch it on: Feed recovers within a minute (the
    stream retries every 60 seconds while the site answers that it is off). Switch `feed.writing` off: the round buttons go,
    the strip reads "Writing is paused"; on: they come back.
43. **The website: roles.** Give B the announcer role on the website's People page: the round button on "Announcements"
    appears on B's phone within a second, with no new login; take it away: it goes. `/feed announcer add` and `remove`
    work from the server's console and for an `admin` or `warden-admin` account in game; for an operator who is not a
    moderator on the site, `/feed` is absent, and from a command block or `execute as` it is refused. A moderator deletes
    anyone's post and sees the author of an anonymous one, in game and in the admin.
44. **Single-player, and a site that is not set up.** With `backend.kind = "site"` in a single-player world, Feed opens on
    "Feed unavailable", the log says `Feed calls LNB's website from dedicated servers only`, and the site's access log
    shows no call from that game. With `api.url` or `api.token` blank on a dedicated server, Feed is unavailable and the
    log holds no stack trace. With the memory backend, Feed works in single-player.
45. **Sounds.** With the game's own sounds around, listen to what Feed adds to the phone's: a tap on a heart, a reply
    button, the round button, "Post" and "Reply" when they succeed, a heart refused to a guest (the low error note), the
    pin and unpin, a notification from Feed (the same two chimes as every app's). Say if one is too loud, too quiet or
    grating, as in item 17. "Sounds" off in Settings: Feed makes no sound at all.
46. **Feed in French.** Set the game to French and open Feed: every text is French, with no raw key (`lnbphone_feed.`) and
    French typography (`Annonces`, times such as `2 min` and `3 h`, a space before `:` in a banner such as `Lune G. : …`),
    the commands' answers too (`/feed announcer list`). "Français (Canada)" reads the same. Switch back to English with
    Feed open: the phone goes home and Feed, opened again, is in English; a draft typed in Compose is still there.
47. **Other mods' key bindings.** In the server's real modpack, with Compose or a reply field focused: letters that other
    mods bind (JEI's search key, Emotecraft's wheel, a minimap's keys) type into the field and do nothing else; Ctrl+Enter
    publishes and no other mod takes it; the phone's key does what item 8 says. Feed's own screens never leave a key press
    for the world to see.
48. **GUI scales 5 and above.** At 5, 6 and 9 (a 1440p or 4K screen on "Auto"), Feed's cards, faces, badges, counts, the pill
    and the snackbars are sharp and evenly spaced; the heart's pop and the pill's slide do not tear; a 1-pixel hairline
    between cards is even. At GUI scale 1, Feed shows "GUI scale: 2+" like every app.
49. **Two accounts of one LNB account, and a changed name.** Link two Minecraft accounts to one LNB account and have the
    person choose, on the website, which posts: a post shows the name chosen on the site. Change the real-name setting on
    the site ("show my real name" off): the cards in game, old ones included, show the username alone, with no handle,
    after the next load. Delete the LNB account from the admin: its posts, replies and likes leave the open feeds.

## Automated checks

### Unit tests

`./gradlew build` runs them; `./gradlew test` runs only them. They run with the game loaded (NeoForge's unit test
launcher), and cover what can be reasoned about without a window: the navigation model, the shell's state machine and
its handling of every key, notification alerts and the inventory button's logic, the layout maths, text layout and
editing, the kit's widgets and focus, the notification store, its limits and its sync, the settings file and the
Settings app (played through `PhoneTester`, the test support apps use too, in `phone/src/testing/`). The demo's tests
check Ping's session, target and conversation screen, and that every code sample of
`docs/writing-an-app.md` is still found in the file it quotes.

### Screenshots of the phone

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
| `kit` | | every page of the kit's gallery and the screens of "Post sample", two at a time, at GUI scale 2 to 4 |
| `kit-phone` | | the kit inside the phone, in a world: hover, a slide caught half way, "Post sample" scrolled, a menu, a dialog, Compose typed into and sent |
| `ping` | | Ping, the example app: a ping typed and sent, the peek and the banner of pings arriving, the badge, landing on the conversation, the two refusals; GUI scale 2 to 4 and French |
| `banner` | | the notification banner's states and frames; a real notification sent by `/phonedemo` and read back |
| `shell` | `-PdemoApps=6` | the phone's flows: open, close, the home screen, an app, back, the home button, the centred placement, badges, a click outside, GUI scale 1 |
| `shell-pages` | `-PdemoApps=35` | the home screen's second page |
| `shell-window` | `-PcaptureSize=1920x1080` | the art direction's whole-window frames, at 480 x 270 GUI px |
| `settings` | | the Settings app: placement, sounds, preview, at GUI scale 2 to 4, in French and English; ends centred and silent |
| `settings-window` | `-PcaptureSize=1920x1080` | the next launch after `settings`: both choices kept, then the placement's window frames |
| `notifications` | `-PdemoApps=6 -PcaptureSize=1920x1080` | notifications reaching the phone: peek, banner, badges, the inventory button, the preview off |
| `notifications-persist` | `-PdemoApps=6` | a notification kept across leaving the world and coming back |
| `emoji` | | emoji in a text area (caret after one), a field, a snackbar, a post card, rows, every kind of sequence in Regular and SemiBold, the banners, at GUI scale 2 to 4; a hundred emoji joined by ZWJ in a post and a banner; two hundred distinct emoji at forced scale 9 |
| `notifications-review` | `-PdemoApps=6 -PcaptureSize=1920x1080` | the peek's banner over a chat line, with subtitles, under F1; the inventory button with news on an app's first screen and deeper; the crafting grid after the phone; the recipe book over the button |

Scenarios are in `apps/demo/src/main/kotlin/.../demo/client/capture/`; add one to `CaptureScenarios.kt`. Some click
home icons by their place, so a new app registered by the demo goes after the existing ones; `HomeSlots` finds an
icon by its app's id.

What the captures can't check: sounds, a second player, a dedicated server, other mods, real skins, scales above 4
outside forced shots, and how the phone feels in the hand. That is what the plan above is for.

### Feed's automated checks

Feed's unit tests (about 600, `./gradlew :apps:feed:test`, part of `./gradlew build`) cover the text rule and the
payloads (every one round-trips and a hostile one is refused), the memory backend against the contract's test vectors,
the website backend against a fake site (answers, errors, a stalled body, the event stream and its reconnections), the
server's rights, limits, relay, notifier and command, the client's session (live posts, the pill, counts, pins, resets,
drafts and their keys), and the screens played through `PhoneTester` in each of the art direction's states. The
phone's own tests cover `sendToEveryone` with `except`. `tools/sprites`' test fails when Feed's icon is stale.

**A dedicated server start** is part of every check of Feed, since a client class loaded on a server is the worst bug
this project can ship:

```sh
./gradlew :apps:feed:runServer -Pserver_port=25571
```

It needs `apps/feed/run/eula.txt` with `eula=true` (accepting Minecraft's EULA is a person's decision: no tool creates
it), and a configuration in `apps/feed/run/config`: `./gradlew :apps:feed:prepareMemoryRun` writes the memory backend's.
The log must show the line `Feed uses its memory backend` and `Done`, and never `Attempted to load class
net/minecraft/client` or `invalid dist`. Stop it with `stop` in its console, or by its process id (the Java process whose
command line holds `fml.modFolders=lnbphone_feed`; never stop Java processes by name or by port, they may belong to
other runs).

**Screenshots of Feed** come from its own harness, a smaller cousin of the demo's (Feed cannot depend on the demo):

```sh
./gradlew :apps:feed:runCapture -Pcapture=<root|guest|thread|compose> [-PcaptureDir=<absolute directory>] \
    [-PcaptureLang=<fr_fr|en_us>] [-PcaptureScale=<GUI scale>] [-PcaptureSize=<w>x<h>] [-PcaptureUser=<name>]
```

The task first writes the run's game folder, `apps/feed/build/capture-run`, from the committed `apps/feed/example-config/`
(the memory backend with the sample posts, `Visiteur_42` a guest, Warden's authentication off) and an `options.txt` that
skips the first-launch screen, so nothing is set up by hand and the developer's `apps/feed/run` is never touched. The
client then plays the scenario in a world of its own (flat, creative, no cheats: the player is not an operator),
with the sound muted and a fixed clock (12 November 2026, 14:34 in Paris, the moment the mockups draw), and quits; a
watchdog kills it after 6 minutes. Shots go to `captureDir`, or `apps/feed/build/capture-run/captures/feed/`. The
defaults are French at GUI scale 3 in a 1280 x 960 window, as Camille R. (`Cam_Roux`), or as `Visiteur_42` for `guest`.
`captureLang` and `captureScale` change the language and the scale the scenario starts with, and with either set the
scenario leaves out its closing shots in English at GUI scales 2 and 4 (they would repeat the others). Minecraft allows
GUI scale n only in a window of at least 320n x 240n pixels (1280 x 960 for scale 4, 1600 x 1200 for scale 5): ask for
the window with `captureSize`, which the desktop may still shrink to fit the screen (on the developer's machine a 1600 x 1200
request came out 1600 x 1175, too small for scale 5, so that scale is for the hand test). The run stops with `Feed capture stopped: ...` in
the log and a non-zero exit code, and takes no shot, when the scale in force is not the one asked for, when the window
is minimised or when the render target is not the window: an early run once came out blank for that reason. The harness also detaches the game's mouse and keyboard
callbacks when it starts, so the real mouse can move over the window while it runs (a click was once lost to that);
leave the window alone anyway: it opens on the desktop for about a minute.

| Scenario | Shows | Needs |
|---|---|---|
| `root` | Announcements with its pinned posts, Global, the pill with its arrow and without it, an announcement's banner and the dot on its tab, landing from that banner, the connection lost, Feed unavailable, Feed switched off, writing paused (a like refused with the reason), then English at GUI scale 2 and 4 | the memory backend with the sample seed |
| `guest` | the guest's look: no compose button, a like refused, the thread's strip, a refused reply | `Visiteur_42` under `memory.guests`, which `captureUser` defaults to for this scenario |
| `thread` | a thread from its card, the reply composer collapsed, hovered, expanded and typed into, a reply to a reply, sending, an own reply deleted, the no-replies row, landing on a reply from its notification, deleting an own post | the sample seed |
| `compose` | Compose as a player (typing, the counter's colours, over the limit, anonymous, sending, posted, a failure, the closed draft kept) and as an announcer (the destination, anonymous), pinning (tooltip, the replace dialog, the group), then English at GUI scale 2 | the sample seed; the scenario makes Camille an announcer itself, through the backend |

`FeedScenarios.kt` and `WriteScenarios.kt` are in `apps/feed/src/capture/`, a source set the jar never holds; a new
scenario is added to `FeedScenarios.get`. What the captures can't check: the website backend, a second player, a
dedicated server, the sounds, scales above 4, the real skins, and how it feels in the hand. That is what items 30 to 49
are for.
