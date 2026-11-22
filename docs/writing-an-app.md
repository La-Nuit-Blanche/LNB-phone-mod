# Writing an app for LNB Phone

An app is a mod of its own that depends on the phone. It adds itself to the phone when the game starts; nothing in
the phone changes. This guide takes you from an empty folder to an app with two screens, a list, a text field, state
that survives the player leaving, and a notification sent by your server that opens the app on the right screen.

It walks through **Ping**, the example app in the demo mod (`apps/demo`, package `ping`): pick a player, write a
short message, and it reaches their phone as a notification; opening it lands on the screen to answer them. Every
code sample below is lifted from Ping or from code compiled with the demo's tests, and the build checks that they
still match (`GuideSamplesTest`).

You need Java 21, the repository building (`./gradlew build`), Kotlin, and the NeoForge basics: a mod class, events,
payloads. The reference for every class named here is its KDoc, in `phone/src/main/kotlin/fr/bde_teknantes/lnb/phone/api/`;
this guide is the path through it.

## How the phone runs an app

Read this part first. Most mistakes made with the phone are mistakes about this model, not about a call.

### An app and its screens

- A **`PhoneApp`** is what the home screen shows (an id, a name, an icon) and a way to create the app's first screen.
  Your mod registers it once, on the client, while the game starts (`RegisterPhoneAppsEvent`).
- An **`AppScreen`** is one screen of the app. You will nearly always extend **`KitScreen`**, a screen built from the
  UI kit's widgets (`api/client/ui/`), which draws them and routes the mouse and the keyboard to them.
- An app is a **stack of screens**. A screen reaches the phone through its `phone` property: `push` opens a screen on
  top, `pop` goes back, `replace` swaps the current one, `home` leaves the app, `close` puts the phone away. The phone
  draws the slides, shows the back arrow in your header and maps Backspace to "back".
- **Screens are thrown away.** Each time the player opens the app, from the home screen or from a notification, the
  phone calls your factory and gets new screens. When the player leaves the app (the home button, another app, a
  notification of another app, disconnecting) every screen is closed and never shown again. Putting the phone away
  (Esc, the phone key, a click outside) is different: the app keeps its screens and the phone reopens on them.
- Screens draw in **dp**, in a viewport of 256 x 370 dp under the status bar, whatever the window or the GUI scale.
  The kit's widgets already follow the phone's look; the art direction (`design/art-direction.md`, section 8) is what
  the kit implements, and you shouldn't need to read it.

### Where state lives

Because screens are rebuilt, a value has to live somewhere that lasts as long as it must:

| Where | Lasts | Ping keeps there |
|---|---|---|
| A field of a screen or widget | Until the screen closes: popped, or the player leaves the app | Nothing that matters after the screen |
| An object of your app, on the client | Until you clear it | Drafts, the conversations, the list's scroll position (cleared at disconnection) |
| Your server | As long as you store it | Nothing: Ping stores no messages |

The phone keeps one thing for you on the server: notifications, per player, saved with the world.

The kit is built for this: a `TextField` or `TextArea` takes a `TextState`, a `ScrollList` takes a `ScrollState`. Keep
those in your app's object and hand them to the new widgets each time; the text and the scroll position carry over.

### Sides

A **dedicated server** has no client classes: no `Minecraft`, no screens, nothing from the phone's `api.client`
package. A single-player game runs a client and a server in one process, so code that wrongly loads a client class on
the server works there and crashes the dedicated server. Plan for it from the start:

- Your **mod class is common code**: it runs on both sides. So are `api.PhoneNotifications`, `api.AppTarget` and
  `api.TextLimits`.
- Everything that touches the phone's screens, the kit or `Minecraft` goes in a `client` package of your own, and is
  reached only from behind a `FMLEnvironment.dist.isClient` check, named by its full name; or, for a client payload
  handler, from inside the handler's lambda (the class then loads only when a payload arrives, which never happens
  on a dedicated server).
- `PhoneNotifications` works on the logical server: a dedicated server, or the integrated server of a single-player
  game. A client can't send a notification; it asks your server, which sends it.

The only way to be sure is to start a dedicated server with your mod (see "Try it" below).

### Threads

- **The client thread** (also called the render thread) runs screens, widgets, ticks, key presses and client payload
  handlers. Everything in `api.client` belongs to it, and `Phone` throws if called from another thread.
- **The server thread** runs commands, server events and server payload handlers (NeoForge's default).
- **Other threads** are yours: an HTTP call's callback, a `CompletableFuture` pool, an executor, a payload handler you
  asked NeoForge to run on the network thread. From there, hop over before touching anything:
  `Minecraft.getInstance().execute { ... }` on the client, `server.execute { ... }` on the server.
  `PhoneNotifications` is the exception: it may be called from any thread.

A late answer is harmless once on the client thread: a screen's `phone` handle does nothing after the screen closed,
so an answer that arrives after the player left can't drag them back into your app.

### What you may extend

- Extend `AppScreen`, `KitScreen`, `Widget` and `Clickable`, and override what their KDoc calls hooks.
- Pass lambdas for `AppScreenFactory` and `AppTargetScreens` (`PhoneApp`'s last two arguments).
- **Don't implement `Phone` or `AppCanvas`.** The phone gives you instances; later versions will add methods to them,
  which would break a class of yours.
- Widgets take in their constructor only what they can't exist without; everything else is a property you set with
  `apply { ... }`. New settings arrive as new properties, so your code keeps compiling against a newer phone.
- Some enums are complete for good (`Interaction`, `Label.Align`, `Avatar.Size`: a `when` may list every value);
  others will grow (`TextStyle`, `Button.Kind`, `Shadow`: keep an `else` branch). Each enum's KDoc says which.
- An app built against phone 0.1.x declares it accepts phones from 0.1.0 up to 0.2 (the build writes that range): a
  phone whose API may have broken stops at the loader's screen instead of crashing in the game.

## Building Ping, step by step

### 1. A subproject

An app lives in this repository under `apps/<name>/`. Make the folder and add it to `settings.gradle.kts`, next to
the demo:

<!-- sample: settings.gradle.kts -->
```kotlin
include(":phone")
include(":apps:demo")
```

Its `build.gradle.kts` applies the app plugin, which adds the dependency on the phone, writes the phone dependency
into your mod's metadata, and gives you the runs:

<!-- sample: apps/demo/build.gradle.kts -->
```kotlin
plugins {
    id("lnbphone.app")
}
```

Its `gradle.properties` names the mod (versions are shared, in the root `gradle.properties`):

<!-- sample: apps/demo/gradle.properties -->
```properties
mod_id=lnbphone_demo
mod_name=LNB Phone Demo
mod_description=Example app for LNB Phone, used in development. Never shipped.
```

The mod id is lowercase letters, digits and `_`, and is the namespace of your app's id, assets and strings. `lnbphone`
is taken: the phone keeps that namespace for its own apps and leaves out any app that uses it.

Copy `apps/demo/src/main/templates/META-INF/neoforge.mods.toml` as it is: it is filled from `gradle.properties`, and
the plugin appends the dependency on `lnbphone` itself.

<!-- sample: apps/demo/src/main/templates/META-INF/neoforge.mods.toml -->
```toml
modLoader="kotlinforforge"
# ...
[[mods]]
modId="${mod_id}"
# ...
# The dependency on lnbphone is appended by the lnbphone.app plugin, with the range of the phone built against.
```

Then the language files, `src/main/resources/assets/<mod id>/lang/en_us.json` and `fr_fr.json`. Every text a player
sees goes through a key present in both (English is the default; players whose game is in French get French).

```text
apps/<name>/
  build.gradle.kts
  gradle.properties
  src/main/templates/META-INF/neoforge.mods.toml
  src/main/kotlin/...                       your code
  src/main/resources/assets/<mod id>/lang/en_us.json, fr_fr.json
  src/main/resources/assets/<mod id>/textures/gui/<icon>.png
```

`./gradlew :apps:<name>:runClient` now starts the game with the phone and your mod, in `apps/<name>/run/`;
`./gradlew :apps:<name>:runServer` starts a dedicated server. The jar is `apps/<name>/build/libs/<mod id>-<version>.jar`,
and it needs `lnbphone` on the server and on every client.

### 2. Starting the app

Ping's start-up is common code. It registers Ping's payloads (both sides need them), then starts the client side
behind the check, naming its class in full so that the server never loads it:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/Ping.kt -->
```kotlin
object Ping {
    /** The app's id: registered on the client, named by the server when it sends a notification. */
    val APP_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "ping")

    /** The longest ping, in characters as [fr.bde_teknantes.lnb.phone.api.TextLimits] counts them. */
    const val MAX_LENGTH = 100

    fun init(modBus: IEventBus) {
        // Both sides: the client sends pings, the server receives them.
        modBus.addListener(PingNetwork::register)
        // Behind the check, by its full name, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) fr.bde_teknantes.lnb.phone.demo.ping.client.PingClient.init(modBus)
    }
}
```

The demo mod hosts development tools besides Ping, so its mod class calls `Ping.init`. In a mod of your own, those
lines are your mod class's constructor:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/DemoApp.kt -->
```kotlin
@Mod(DemoApp.ID)
class DemoApp(modBus: IEventBus) {
    init {
        // ...
        Ping.init(modBus)
    }

    companion object {
        const val ID = "lnbphone_demo"
        // ...
    }
}
```

The id in `@Mod` is the `mod_id` of your `gradle.properties`. Kotlin for Forge hands the constructor your mod's
event bus (`net.neoforged.bus.api.IEventBus`); `FMLEnvironment` is `net.neoforged.fml.loading.FMLEnvironment`.

The client side registers the app. `RegisterPhoneAppsEvent` is fired once, on the mod bus, after client setup:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingClient.kt -->
```kotlin
object PingClient {
    fun init(modBus: IEventBus) {
        modBus.addListener(::registerApps)
        NeoForge.EVENT_BUS.addListener(::onLoggingOut)
    }

    private fun registerApps(event: RegisterPhoneAppsEvent) {
        event.register(
            PhoneApp(
                id = Ping.APP_ID,
                name = Component.translatable("app.lnbphone_demo.ping"),
                icon = AppIcon.Glyph(TileColor.PINK, ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "textures/gui/ping.png")),
                // ...
            ) { PlayersScreen() },
        )
    }
```

The last argument creates the first screen, a new one each time the app opens. The home screen lists apps in the
order they were registered (mods in loading order), with the phone's Settings app last. Two apps with the same id:
the first one wins and the log says so.

### 3. The name and the icon

The name is a translatable component; keep it to 16 characters or fewer in both languages, or it is cut on the home
screen's label line:

<!-- sample: apps/demo/src/main/resources/assets/lnbphone_demo/lang/en_us.json -->
```json
  "app.lnbphone_demo.ping": "Ping",
```

The icon is a tile colour from the phone's palette (`TileColor`) and a 16 x 16 PNG of yours, which the phone frames
and shades so that every app's icon looks like one family. Draw it with hard pixels (fully opaque or fully
transparent), the main shape inside the 12 x 12 square from (2, 2) to (13, 13), at most 4 colours among white
`#FFFFFF`, light `#D6DCE6`, ink `#1B1E23`, the tile's `lo` colour and one accent (`#F28A2E`, `#F2B33A`, `#5BD34A`,
`#E5483C` or `#3A8DF0`), and no shadow. Ping's is `assets/lnbphone_demo/textures/gui/ping.png`, a white speech bubble
with an ink exclamation mark. For a first version, `AppIcon.Item(tile, stack)` puts a Minecraft item on the tile
instead.

### 4. The first screen: a list

Ping opens on the players online. A `KitScreen` creates its widgets in its constructor, places them with `at(x, y)`
(top-left corner, in dp; width and height when the widget has no natural size) and `add`s them:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PlayersScreen.kt -->
```kotlin
class PlayersScreen : KitScreen() {
    private val list = ScrollList(PingSession.playersScroll)

    init {
        add(Header(I18n.get("app.lnbphone_demo.ping")).at(0f, 0f))
        add(list.at(0f, 44f, 256f, 326f))
    }

    // Each time the screen shows (opened, or back from a ping): players come and go.
    override fun onScreenShown() {
        val mc = Minecraft.getInstance()
        val players = mc.connection?.onlinePlayers.orEmpty().map { it.profile }.sortedBy { it.name.lowercase() }
        list.removeAll()
        for (player in players) {
            list.add(ListRow(player.name) { phone.push(PingScreen(player.id, player.name)) }.apply {
                if (player.id == mc.player?.uuid) subtitle = I18n.get("lnbphone_demo.ping.you")
                leading = Avatar().apply { showPlayer(player.id, player.name) }
                trailing = IconView(Icon.CHEVRON).apply { color = Colors.TEXT_2 }
            })
        }
    }
}
```

What it shows:

- **Layout.** The header is 44 dp at the top; the content takes the rest, 256 x 326 dp from y 44. Screens keep 12 dp
  from the sides (`Spacing.SCREEN_PADDING`). Put edges on even dp values so they stay sharp at every GUI scale.
- **Text.** Widgets take plain strings, so translate on the client with `I18n.get(key)`
  (`net.minecraft.client.resources.language.I18n`, a client class). A screen is built when it
  opens, so it picks up the player's language each time.
- **Hooks.** `KitScreen` keeps `AppScreen`'s drawing and input methods for itself. You override `onScreenShown`,
  `onScreenHidden`, `onTick`, `onKey` and `onBack`. Here the rows are rebuilt each time the screen shows, so a player
  who joined while the player was in a conversation is there on the way back.
- **Kept state.** The list's position is a `ScrollState` from `PingSession` (step 6): coming back to Ping later, the
  list is where the player left it.
- **Draw it yourself.** When the kit has no widget for something, extend `Widget` (or `Clickable` for something that
  reacts to clicks) and draw in `render` with the `AppCanvas` methods. The kit's gallery in the demo (the "UI kit"
  app, `client/kit/Gallery.kt`) shows every widget; the sample Feed (`client/kit/FeedScreens.kt` and `PostCard.kt`)
  composes them into real screens, with a custom widget.

### 5. A second screen and navigation

A click on a row pushes the screen to write to that player: `phone.push(PingScreen(player.id, player.name))`. The
new screen slides in; the list stays underneath, alive, and comes back when the player goes back. A `Header` shows a
back arrow on every screen but the app's first, and Backspace does the same when no text field has the focus. There is
nothing to write for either.

`phone` is available once the phone has the screen: in `onScreenShown`, in input handlers, in the lambdas your
widgets call. Not in the constructor itself; a lambda written there is fine, since it runs later.

### 6. Text input, and state that survives the screen

The screen to write to a player has a text field, a send button, and the pings exchanged with them:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
class PingScreen(private val player: UUID, private val name: String) : KitScreen() {
    private val draft = PingSession.draft(player)
    private val field = TextField(draft)
    private val send = Button(I18n.get("lnbphone_demo.ping.send")) { send() }
    private val conversation = ScrollList()
    private var shown = 0

    init {
        add(Header(name).at(0f, 0f))
        // ...
        add(field.apply {
            placeholder = I18n.get("lnbphone_demo.ping.placeholder")
            onChange = { updateSend() }
            onSubmit = { send() }
        }.at(12f, 120f))
        // Wide enough for the longer of "Send" and "Envoyer".
        add(send.apply { fixedWidth = 88f }.atRight(244f, 168f))
        updateSend()
        // ...
    }

    override fun onScreenShown() {
        // Ready to type: the field takes the keys, so Backspace erases instead of going back.
        field.requestFocus()
    }
```

The field's text is not the screen's: it is a `TextState` from the app's session object. The screen is new at every
visit; the state is the same one, so a draft left half-written is there when the player comes back, even through the
home screen or a notification:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingSession.kt -->
```kotlin
object PingSession {
    /** One line of a conversation: sent by the player ([mine]) or received. */
    class Message(val text: String, val mine: Boolean)

    /** How far the list of players is scrolled. */
    val playersScroll = ScrollState()

    private val drafts = HashMap<UUID, TextState>()
    private val conversations = HashMap<UUID, MutableList<Message>>()

    /** The unsent text for [player]: the field shows it again when the player comes back. */
    fun draft(player: UUID): TextState = drafts.getOrPut(player) { TextState("", false, Ping.MAX_LENGTH) }
```

`TextState("", false, Ping.MAX_LENGTH)` is a single-line text of at most 100 characters: what goes past is cut as it
is typed or pasted. A `TextArea` (several lines, with a character counter when you set its `limit`) takes a `TextState`
the same way.

An object lives as long as the game, across servers: clear what belongs to a server when the player leaves it. The
event may come from another thread, so the clearing hops to the client thread:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingClient.kt -->
```kotlin
    private fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        // Drafts belong to the server just left. Hop to the client thread, where the session is used.
        Minecraft.getInstance().execute { PingSession.clear() }
    }
```

While a text field has the focus, the screen's `hasTextFocus` is true and the phone leaves the keys to it: Backspace
erases instead of going back, and the phone's own key types instead of putting the phone away. Esc still puts the
phone away. `KitScreen` does this for you.

Sending checks the text with `TextLimits`, the rule the server applies too, so the two never disagree:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
    private fun updateSend() {
        send.enabled = TextLimits.canSend(draft.text, Ping.MAX_LENGTH)
    }

    private fun send() {
        val text = draft.text.trim()
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH)) return
        PacketDistributor.sendToServer(SendPingPayload(player, text))
        PingSession.sent(player, text)
        draft.clear()
        PhoneSounds.sent()
        showSnackbar(I18n.get("lnbphone_demo.ping.sent"))
        showNewMessages()
    }
```

The kit plays the tap sound on every click and the error sound on a click on something disabled. `PhoneSounds` has
the others (`sent`, `like`); all of them follow the player's Sounds setting. `showSnackbar` shows a short message at
the bottom; a snackbar shown just before a `pop` is carried over to the screen underneath.

### 7. Talking to your server

The phone carries notifications, nothing else: your app sends its own payloads. Ping has two, registered on both sides
from `Ping.init`:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingNetwork.kt -->
```kotlin
object PingNetwork {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(SendPingPayload.TYPE, SendPingPayload.STREAM_CODEC, PingServer::handle)
        // The client's handler is named in full inside the lambda: its class loads only when a payload arrives, which
        // never happens on a dedicated server.
        registrar.playToClient(PingReceivedPayload.TYPE, PingReceivedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession.received(payload.from, payload.text)
        }
    }
}
```

Both handlers run on the game thread of their side, so the client one may touch `PingSession` directly. The
conversation screen picks new pings up in `onTick`: a screen reads the state, the network code writes it, and neither
holds the other.

### 8. A notification that opens the right screen

The server checks what it received, passes the ping on, and notifies the recipient:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingServer.kt -->
```kotlin
    fun handle(payload: SendPingPayload, context: IPayloadContext) {
        val sender = context.player() as? ServerPlayer ?: return
        // The client checks the text too, but a server never trusts what a client sends.
        val text = payload.text.trim()
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH)) return
        // Only players who have been on this server: a notification to anyone else would wait 30 days for nothing.
        val server = sender.server
        if (server.profileCache?.get(payload.to)?.isPresent != true) return

        // For the conversation on the recipient's screen, if they are here.
        server.playerList.getPlayer(payload.to)?.let { PacketDistributor.sendToPlayer(it, PingReceivedPayload(sender.uuid, text)) }
        // The notification: a badge, a banner or the phone peeking, and the way to the screen to answer. Offline
        // players get it when they next log in.
        PhoneNotifications.send(
            payload.to,
            Ping.APP_ID,
            title = Component.translatable("notif.lnbphone_demo.ping.title"),
            // The player's text goes in as an argument, never as the key: see PhoneNotifications.
            text = Component.translatable("notif.lnbphone_demo.ping.text", sender.gameProfile.name, text),
            target = PingTarget(sender.uuid, sender.gameProfile.name).toString(),
        )
    }
```

What `PhoneNotifications.send` gives the player: a badge on the app's icon and the phone's light blinking; a banner
over the open phone, or the closed phone peeking up from the corner with the banner (unless the player turned the
preview off); a chime (unless sounds are off). A player who is offline gets it at their next login, as a badge. Each
player keeps the newest 50 notifications, for 30 days at most; opening the app, by any route, clears its
notifications. `clearApp` and `clearTarget` take them back (a deleted post, say).

- **Text.** Title and text are translatable components with keys in your language files, so they are shown in the
  recipient's language even if sent while they were away. What a player wrote goes in as an argument, never as the
  key. The title names the event in a few words (`New ping`); the text starts with who did it (`Dev: Meet me at the
  mill`). The banner shows one line of title and two of text; styles are dropped.

<!-- sample: apps/demo/src/main/resources/assets/lnbphone_demo/lang/fr_fr.json -->
```json
  "notif.lnbphone_demo.ping.title": "Nouveau ping",
  "notif.lnbphone_demo.ping.text": "%s : %s",
```

  (French puts a no-break space, U+00A0, before `:`, `?` and `!`, and placeholders end with the `…` character.)

- **Target.** The target is a short string that only your app understands, stored with the notification and handed
  back to your app when the player opens it. Keep it to an id or two (at most 256 characters; never the content
  itself), give it a prefix, and read it defensively: an old notification may reach a newer version of your app.

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingTarget.kt -->
```kotlin
data class PingTarget(val player: UUID, val name: String) {
    override fun toString() = "$PREFIX$player/$name"

    companion object {
        private const val PREFIX = "from:"

        fun parse(target: String): PingTarget? {
            if (!target.startsWith(PREFIX)) return null
            val id = target.substring(PREFIX.length).substringBefore('/')
            val name = target.substringAfter('/', missingDelimiterValue = "")
            if (name.isEmpty()) return null
            val player = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return PingTarget(player, name)
        }
    }
}
```

On the client, `PhoneApp`'s `targetScreens` turns a target into the stack of screens to open, root first. Ping opens
the conversation over the list, so that "back" leads where it would have if the player had picked the sender
themselves. An empty list (a target it doesn't know) opens the app on its first screen:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingClient.kt -->
```kotlin
                // A ping's notification opens the screen to answer its sender, over the list as if picked from it.
                targetScreens = { target ->
                    val from = PingTarget.parse(target)
                    if (from == null) emptyList() else listOf(PlayersScreen(), PingScreen(from.player, from.name))
                },
```

These are new screens like any others; they find the draft and the conversation in `PingSession`.

Last, a screen can tell the phone that it already shows what a notification points to. The phone asks the shown
screen when a notification of its app arrives; if it answers true, no banner covers it (the chime still plays) and the
notification counts as read. Ping's conversation shows the new ping as it arrives:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
    // A ping from this player lands in the conversation on screen: the phone needs no banner over it.
    override fun showsTarget(target: String) = PingTarget.parse(target)?.player == player
```

### 9. Try it

```sh
./gradlew :apps:demo:runClient
```

Make a world, press the Up arrow, open Ping (the pink icon), click your own name: Ping lets you ping yourself, to try
it alone. Type and press Enter: "Ping sent" shows, and the ping appears twice in the conversation, sent on the right
and received on the left. Go home and back into Ping: the list is where you left it, and the draft and the
conversation are still there.

Then check it as a real server will run it:

- **A dedicated server**: `./gradlew :apps:demo:runServer` (the first time, it stops and asks you to accept
  Minecraft's EULA in `apps/demo/run/eula.txt`). It must start without an error mentioning a client class.
- **Two players**: a ping from another player shows a banner if your phone is open elsewhere, or the phone peeks up
  if it is closed; opening it lands on the conversation with them. `TESTING.md` lists what to try.

Logic that doesn't need the game (parsing a target, a layout, a store) is worth a unit test. Keep it in plain classes,
as `PingTarget` is, and test it with JUnit under `src/test/kotlin`; see `PingTargetTest`. Tests run with the game
loaded, which loads Kotlin twice: use JUnit's `assertInstanceOf` and `assertThrows`, not kotlin.test's `assertIs` and
`assertFailsWith`.

## Mistakes to avoid

Each of these compiles, and most of them work in single player.

**Keeping state in a screen.** The phone builds new screens at every visit, so this field starts empty every time:

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
// Wrong: the phone builds a new screen at every visit, so what was typed is gone the next time.
class ForgetfulNoteScreen : KitScreen() {
    private val field = add(TextField().at(12f, 56f))
}

// Right: the text lives in an object of the app, which outlives the screens; each new screen hands it to its field.
object NoteState {
    val draft = TextState("", false)
}

class NoteScreen : KitScreen() {
    private val field = add(TextField(NoteState.draft).at(12f, 56f))
}
```

**Touching a client class from common code.** Your mod class and your server handlers run on the dedicated server,
where client classes don't exist:

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
// Wrong: the mod class runs on the dedicated server too, and PingClient is a client class.
class CarelessMod(modBus: IEventBus) {
    init {
        PingClient.init(modBus)
    }
}

// Wrong: payload handlers on the server side run on the dedicated server, where Minecraft (the client) doesn't exist.
fun carelessHandle(payload: SendPingPayload, context: IPayloadContext) {
    val name = Minecraft.getInstance().player?.name
}

// Right: on the server, the player who sent the payload comes from the context.
fun handle(payload: SendPingPayload, context: IPayloadContext) {
    val sender = context.player() as? ServerPlayer ?: return
}
```

The fix for the first is step 2: the check, and the client class named in full behind it.

**Calling the phone from another thread.** A callback of an HTTP call or of a `CompletableFuture` runs on some other
thread, and the phone throws there:

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
    // Wrong: thenAccept runs on the pool's thread, and the phone throws when called from there.
    fun openCareless() {
        CompletableFuture.supplyAsync { fetchName() }.thenAccept { name ->
            phone.push(ProfileScreen(name))
        }
    }

    // Right: hop over to the client thread first. If the player has left the screen meanwhile, push does nothing.
    fun open() {
        CompletableFuture.supplyAsync { fetchName() }.thenAccept { name ->
            Minecraft.getInstance().execute { phone.push(ProfileScreen(name)) }
        }
    }
```

The same goes for widgets and your session object: change them on the client thread only.

**Implementing what the phone implements.** `Phone` and `AppCanvas` are given to you; a later phone adds methods to
them, and a class of yours that implements them stops loading. A `when` over an enum that may grow needs an `else`:

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
// Wrong: only the phone implements Phone (and AppCanvas). A later phone adds a method to it and this class breaks.
class FakePhone : Phone {
    // ...
}

// Right: TextStyle may get new values in a later phone, so a `when` over it keeps an `else`.
fun isHeading(style: TextStyle) = when (style) {
    TextStyle.TITLE_L, TextStyle.TITLE_M -> true
    else -> false
}
```

**Creating a screen once and reusing it.** `PhoneApp`'s factories and `push` need a new screen each time; a screen
already on the phone, or closed, is refused.

**Throwing.** If your screen throws, the phone logs the error, closes your app and goes home; the game carries on. It
is a safety net, not error handling: the player loses what they were doing.

## Where to look next

- The KDoc of `api/` and `api/client/`: `PhoneApp`, `AppScreen`, `Phone`, `AppCanvas`, `PhoneNotifications`,
  `AppTarget`, `TextLimits`; and of the kit in `api/client/ui/`, starting with `KitScreen` and `Widget`.
- The demo's other apps: the kit's gallery ("UI kit") and the sample Feed ("Feed (sample)"), described in
  `apps/demo/README.md`.
- `design/art-direction.md` for the look of the phone and of the Feed app (section 9), if you need a value the kit
  doesn't give.
