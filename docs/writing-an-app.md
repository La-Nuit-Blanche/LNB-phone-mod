# Writing an app for LNB Phone

An app is a mod of its own that depends on the phone. It adds itself to the phone when the game starts; nothing in
the phone changes. This guide takes you from an empty folder to an app with two screens, a list, a text field, state
that survives the player leaving, its own exchanges with your server, and a notification sent by your server that
opens the app on the right screen.

It walks through **Ping**, the example app in the demo mod (`apps/demo`, package `ping`): pick a player who is online,
write a short message, and it appears in your conversation with them on their phone, with a notification that opens
it. Every code sample below is lifted from Ping or from code compiled with the demo's tests, and the build checks that
they still match (`GuideSamplesTest`).

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
| An object of your app, on the client | Until you clear it | Drafts, conversations, the list's scroll position (cleared at disconnection) |
| Your server | As long as you store it | Nothing: a ping only travels between two connected players |

**A notification is a pointer, not a container.** The phone keeps notifications per player on the server, but the
slots are shared by every app and limited (50 per player), they go when the player opens your app, and they expire
after 30 days. Whatever the player must be able to read lives with your app, sent by your server; the notification
only says that something is there and where. Ping sends the text of a ping in its own payload and refuses a ping to a
player who isn't online, since nobody would keep it for them.

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
  game. A client can't send a notification; it asks your server, which decides.

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
- `Phone`, `AppCanvas` and `TextMeasurer` are the phone's: it gives you instances, only it can make them, and later
  versions add methods to them. To test, use the phone's test support (step 9).
- Widgets take in their constructor only what they can't exist without; everything else is a property you set with
  `apply { ... }`. New settings arrive as new properties, so your code keeps compiling against a newer phone.
- Some enums are complete for good (`Interaction`, `Label.Align`, `Avatar.Size`: a `when` may list every value);
  others will grow (`TextStyle`, `Button.Kind`, `Shadow`: keep an `else` branch). Each enum's KDoc says which.
- An app accepts phones from the version it was built against up to the next minor version (the next major from 1.0
  on): built against 0.1.2, it accepts `[0.1.2,0.2)`. The build writes that range; a phone whose API may have broken
  then stops at the loader's screen instead of crashing in the game.

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

Its `gradle.properties` names the mod:

<!-- sample: apps/demo/gradle.properties -->
```properties
mod_id=lnbphone_demo
mod_name=LNB Phone Demo
mod_description=Example app for LNB Phone, used in development. Never shipped.
```

The mod id is lowercase letters, digits and `_`, and is the namespace of your app's id, assets and strings. `lnbphone`
is taken: the phone keeps that namespace for its own apps and leaves out any app that uses it.

Copy `apps/demo/src/main/templates/META-INF/neoforge.mods.toml`. It is filled from the `gradle.properties` files, and
the plugin appends the dependency on `lnbphone` itself. As it is, it carries the phone repository's values: change
`issueTrackerURL` and `displayURL` in your copy, and set `mod_version`, `mod_authors` or `mod_license` in your app's
`gradle.properties` to replace the shared ones from the root `gradle.properties`.

<!-- sample: apps/demo/src/main/templates/META-INF/neoforge.mods.toml -->
```toml
modLoader="kotlinforforge"
# ...
issueTrackerURL="https://github.com/La-Nuit-Blanche/LNB-phone-mod/issues"
# ...
[[mods]]
modId="${mod_id}"
version="${mod_version}"
# ...
authors="${mod_authors}"
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

Ping's start-up is common code. It registers Ping's payloads (both sides need them) and a server event, then starts
the client side behind the check, naming its class in full so that the server never loads it:

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
        // The server, dedicated or integrated, forgets a player who leaves.
        NeoForge.EVENT_BUS.addListener(PingServer::onLoggedOut)
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
`#E5483C` or `#3A8DF0`), and no shadow. A picture of another size is fitted, with a warning in the log. Ping's is
`assets/lnbphone_demo/textures/gui/ping.png`, a white speech bubble with an ink exclamation mark. For a first version,
`AppIcon.Item(tile, stack)` puts a Minecraft item on the tile instead.

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

The kit's classes are in `fr.bde_teknantes.lnb.phone.api.client.ui`, and so are `at` and `atRight`, which are
extension functions: without an IDE, import them by name. Ping's conversation screen imports:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.Spacer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.api.client.ui.atRight
// ...
import net.minecraft.client.resources.language.I18n
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID
```

What the list screen shows:

- **Layout.** The header is 44 dp at the top; the content takes the rest, 256 x 326 dp from y 44. Screens keep 12 dp
  from the sides (`Spacing.SCREEN_PADDING`). Put edges on even dp values so they stay sharp at every GUI scale.
- **Text.** Widgets take plain strings, shown as given, so translate on the client with `I18n.get(key)`
  (`net.minecraft.client.resources.language.I18n`, a client class). A screen is built when it opens, so it picks up
  the player's language each time; and when the player changes the game's language, the phone sends the open app
  home, so no screen built in the old language is seen again. What you keep outside your screens (drafts, scroll
  positions) stays.
- **Hooks.** `KitScreen` keeps `AppScreen`'s drawing and input methods for itself. You override `onScreenShown`,
  `onScreenHidden`, `onTick`, `onKey` and `onBack`, and `AppScreen`'s `onClosed`: called once when the screen leaves
  the phone for good, it is where you stop what the screen started (a listener, a request). Here the rows are rebuilt
  each time the screen shows, so a player who joined meanwhile is there on the way back.
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

The conversation screen has a text field, a send button, and the pings exchanged with that player:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
class PingScreen(private val player: UUID, private val name: String) : KitScreen() {
    private val draft = PingSession.draft(player)
    private val field = TextField(draft)
    private val send = Button(I18n.get("lnbphone_demo.ping.send")) { send() }
    private val conversation = ScrollList()
    private var shownRevision = -1

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
```

The field's text is not the screen's: it is a `TextState` from the app's session object. The screen is new at every
visit; the state is the same one, so a draft left half-written is there when the player comes back, even through the
home screen or a notification:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingSession.kt -->
```kotlin
object PingSession {
    /** The most messages kept per conversation: pings keep coming, memory doesn't. */
    const val MAX_MESSAGES = 50
    // ...
    private val drafts = HashMap<UUID, TextState>()
    private val conversations = HashMap<UUID, ArrayDeque<Message>>()
    private val waiting = HashSet<UUID>()
    private val outcomes = HashMap<UUID, PingOutcome>()

    /** Goes up at every change of a conversation, so that screens know when to show them again. */
    var revision = 0
        private set

    /** The unsent text for [player]: the field shows it again when the player comes back. */
    fun draft(player: UUID): TextState = drafts.getOrPut(player) { TextState.singleLine(maxLength = Ping.MAX_LENGTH) }
```

`TextState.singleLine(maxLength = Ping.MAX_LENGTH)` is a single-line text of at most 100 characters: what goes past is
cut as it is typed or pasted. A `TextArea` (several lines, with a character counter when you set its `limit`) takes a
`TextState.multiline()` the same way.

Everything that keeps coming is bounded. A conversation keeps its newest 50 messages, so a talkative player (or a
modified client) can't make the screen grow without end:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingSession.kt -->
```kotlin
    private fun add(player: UUID, message: Message) {
        val messages = conversations.getOrPut(player) { ArrayDeque() }
        messages.addLast(message)
        if (messages.size > MAX_MESSAGES) messages.removeFirst()
        revision++
    }
```

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

The screen shows the session and catches up with it at every tick: it rebuilds the conversation when the session's
revision has moved, and reports the server's answer to the last ping (step 7):

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
    override fun onTick() {
        when (PingSession.takeOutcome(player)) {
            PingOutcome.SENT -> {
                PhoneSounds.sent()
                showSnackbar(I18n.get("lnbphone_demo.ping.sent"))
            }
            PingOutcome.OFFLINE -> showErrorSnackbar(I18n.get("lnbphone_demo.ping.refused.offline", name))
            PingOutcome.TOO_FAST -> showErrorSnackbar(I18n.get("lnbphone_demo.ping.refused.too_fast"))
            null -> {}
        }
        showConversation()
    }
```

The kit plays the tap sound on every click, the error sound on a click on something disabled and with an error
snackbar. `PhoneSounds` has the others (`sent`, `like`); all of them follow the player's Sounds setting. A snackbar
shown just before a `pop` is carried over to the screen underneath.

### 7. Talking to your server

The phone carries notifications, nothing else: your app sends its own payloads. Ping has three, registered on both
sides from `Ping.init`. Their text codec is bounded, so the server never reads more than a ping can hold:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingNetwork.kt -->
```kotlin
// Bounded: a client can't make the server read more than a ping can hold. Twice the limit in UTF-16 units, since a
// character outside the basic plane takes two.
private val TEXT_CODEC = ByteBufCodecs.stringUtf8(Ping.MAX_LENGTH * 2)
```

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingNetwork.kt -->
```kotlin
object PingNetwork {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("2")
        registrar.playToServer(SendPingPayload.TYPE, SendPingPayload.STREAM_CODEC, PingServer::handle)
        // The client's handlers are named in full inside the lambdas: their class loads only when a payload arrives,
        // which never happens on a dedicated server.
        registrar.playToClient(PingAnswerPayload.TYPE, PingAnswerPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession.answered(payload.to, payload.text, payload.outcome)
        }
        registrar.playToClient(PingReceivedPayload.TYPE, PingReceivedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession.received(payload.from, payload.text)
        }
    }
}
```

The client sends a ping and waits for the server's answer before counting it as sent:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreen.kt -->
```kotlin
    private fun send() {
        val text = draft.text.trim()
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH) || PingSession.isWaiting(player)) return
        PacketDistributor.sendToServer(SendPingPayload(player, text))
        PingSession.sending(player)
        // The server's answer adds the ping to the conversation, or puts the text back if it was refused.
        draft.clear()
    }
```

The answer comes to the session, on the client thread like every client payload handler; a refused ping goes back to
the field, so the player loses nothing:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingSession.kt -->
```kotlin
    fun answered(player: UUID, text: String, outcome: PingOutcome) {
        waiting -= player
        outcomes[player] = outcome
        if (outcome == PingOutcome.SENT) {
            add(player, Message(text, mine = true))
        } else {
            // Refused: the text comes back to the field, unless the player has started another one.
            val draft = draft(player)
            if (draft.text.isEmpty()) draft.text = text
        }
    }
```

Bump the registrar's version (`"2"`) whenever a payload changes: a client and a server with different versions then
refuse to connect instead of misreading each other.

### 8. The server decides, and notifies

**Never trust what a client sends.** A modified client can send any payload, with any content, as often as it likes;
the checks on the client are for the player's comfort, the checks on the server are the real ones. Ping's server
checks the text again with the same rule as the client (`TextLimits`), lets each player send one ping a second, and
only to a player who is connected; it answers every ping, then passes it on with a notification:

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingServer.kt -->
```kotlin
    fun handle(payload: SendPingPayload, context: IPayloadContext) {
        val sender = context.player() as? ServerPlayer ?: return
        val text = payload.text.trim()
        // The client never sends that: no answer.
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH)) return
        val now = Util.getMillis()
        val last = lastPing[sender.uuid]
        if (last != null && now - last < COOLDOWN_MS) return answer(sender, payload, PingOutcome.TOO_FAST)
        // The text lives in the recipient's session (PingSession), so a recipient who isn't here could never read it.
        val recipient = sender.server.playerList.getPlayer(payload.to) ?: return answer(sender, payload, PingOutcome.OFFLINE)
        lastPing[sender.uuid] = now

        // The content, for the conversation on the recipient's phone.
        PacketDistributor.sendToPlayer(recipient, PingReceivedPayload(sender.uuid, text))
        // The notification points to it: a badge, a banner or the phone peeking, and the way to the conversation.
        PhoneNotifications.send(
            recipient,
            Ping.APP_ID,
            title = Component.translatable("notif.lnbphone_demo.ping.title"),
            // The player's text goes in as an argument, never as the key: see PhoneNotifications.
            text = Component.translatable("notif.lnbphone_demo.ping.text", sender.gameProfile.name, text),
            target = PingTarget(sender.uuid, sender.gameProfile.name).toString(),
        )
        answer(sender, payload, PingOutcome.SENT)
    }
```

Without the cooldown, one modified client could keep a player's phone chiming without end and fill their notification
slots, which every app shares. When a player leaves, the server forgets them, and clears their Ping notifications: the
texts were in the session that just ended, so the notifications would point to nothing.

<!-- sample: apps/demo/src/main/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/PingServer.kt -->
```kotlin
    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity.uuid
        lastPing.remove(player)
        // The texts of the player's pings left with their session: notifications would now point to nothing.
        PhoneNotifications.clearApp(player, Ping.APP_ID)
    }
```

What `PhoneNotifications.send` gives the player: a badge on the app's icon and the phone's light blinking; a banner
over the open phone, or the closed phone peeking up from the corner with the banner (unless the player turned the
preview off); a chime (unless sounds are off). A player who is offline gets it when they next log in, as a badge.
Opening the app, by any route, clears its notifications. `clearApp` and `clearTarget` take them back.

- **Text.** Title and text are translatable components with keys in your language files, so they are shown in the
  recipient's language. What a player wrote goes in as an argument, never as the key (see the mistakes below). The
  title names the event in a few words (`New ping`); the text starts with who did it (`Dev: Meet me at the mill`).
  The banner shows one line of title and two of text; styles are dropped.

<!-- sample: apps/demo/src/main/resources/assets/lnbphone_demo/lang/fr_fr.json -->
```json
  "notif.lnbphone_demo.ping.title": "Nouveau ping",
  "notif.lnbphone_demo.ping.text": "%s\u00a0: %s",
```

  French puts a no-break space before `:`, `?` and `!` (written `\u00a0`, so that it shows in reviews), and
  placeholders end with the `…` character.

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

These are new screens like any others; they find the draft and the conversation in `PingSession`. A target is a
place to land, nothing more: whatever the player then does there goes through your server, which checks it.

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
it alone. Type and press Enter: "Ping sent", and the ping appears twice in the conversation, received on the left and
sent on the right. Press Enter again at once with another text: "Wait a moment", and the text is back in the field.
Go home and back into Ping: the list is where you left it, and the draft and the conversation are still there.

Then check it as a real server will run it:

- **A dedicated server**: `./gradlew :apps:demo:runServer` (the first time, it stops and asks you to accept
  Minecraft's EULA in `apps/demo/run/eula.txt`). It must start without an error mentioning a client class.
- **Two players**: a ping from another player shows a banner if your phone is open elsewhere, or the phone peeks up
  if it is closed; opening it lands on the conversation with them. `TESTING.md` lists what to try.

Logic that doesn't need the game (parsing a target, a session's rules, a layout) is worth a unit test. Keep it in
plain classes, as `PingTarget` and `PingSession` are, and test it with JUnit under `src/test/kotlin`; see
`PingTargetTest` and `PingSessionTest`. Tests run with the game loaded, which loads Kotlin twice: use JUnit's
`assertInstanceOf` and `assertThrows`, not kotlin.test's `assertIs` and `assertFailsWith`.

Your screens can be tested too, with `PhoneTester` (`fr.bde_teknantes.lnb.phone.testing`), which the app plugin puts on
your tests' classpath and never in your jar. It is a phone without the game: it opens your app, passes your screens
frames, ticks, clicks, keys and typed text as the phone does, navigates as the phone does, and records the sounds
instead of playing them. Text is measured as if every character were half its size wide, and no language is loaded,
so `I18n.get` returns its key. Its KDoc has the rest.

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/ping/client/PingScreenTest.kt -->
```kotlin
    // A phone without the game, open on the conversation with Dev.
    private val tester = PhoneTester { PingScreen(dev, "Dev") }

    @AfterEach
    fun tearDown() {
        tester.close()
        PingSession.clear()
    }

    @Test
    fun `Send waits for something to send`() {
        // The send button, right-aligned to x 244 at y 168: disabled, a click on it answers with the error sound.
        tester.click(200f, 184f)
        assertEquals(listOf(PhoneSound.ERROR), tester.sounds)
    }
```

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
    val draft = TextState.singleLine()
}

class NoteScreen : KitScreen() {
    private val field = add(TextField(NoteState.draft).at(12f, 56f))
}
```

**Using a notification to carry content.** Notifications are shared, limited, cleared when the app opens and expire.
Keep what the player must read with your app, sent by your server; the notification points to it (see "Where state
lives").

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

**Trusting what a client sends.** A modified client sends what it wants, as often as it wants. Bound every codec
(`ByteBufCodecs.stringUtf8(max)`, not `STRING_UTF8`, which reads up to 32767 characters), check everything again on
the server with the same rules as the client, and limit how often a player may act. Ping's `TEXT_CODEC` (step 7)
and `PingServer.handle` (step 8) do all three.

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
// Wrong: STRING_UTF8 reads up to 32767 characters, whatever the app allows, and the handler takes it as it comes.
val CARELESS_TEXT_CODEC: StreamCodec<ByteBuf, String> = ByteBufCodecs.STRING_UTF8
```

**Using what a player wrote as a translation key.** A key resolves to whatever language entry has that name, of any
mod or of the game, and shows raw when there is none: a player could make their text read as any string of the game,
in every recipient's language.

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
// Wrong: the player's text is used as the key. A player who writes "death.attack.lava" makes the notification read as
// that string of the game, in every recipient's language; any key of any mod works the same.
fun carelessNotificationText(sender: ServerPlayer, text: String): Component = Component.translatable(text)

// Right: the key is yours, and the player's words go in as arguments, shown as they are.
fun notificationText(sender: ServerPlayer, text: String): Component =
    Component.translatable("notif.lnbphone_demo.ping.text", sender.gameProfile.name, text)
```

**Taking a target for a permission.** Any server-side code, of any mod, can send a notification to your app's id with
any target. A target says where to land; it proves nothing about what the player may do there. Whatever is done from
that screen is a request to your server, which checks it like any other:

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
// Wrong: the screen acts because a notification led there. Any server-side code can notify your app's id with any
// target, so landing on this screen proves nothing about what the player may do.
class CarelessDeleteScreen(private val post: Long) : KitScreen() {
    override fun onScreenShown() {
        PacketDistributor.sendToServer(DeletePostPayload(post))
    }
}
```

**Listing every value of an enum that grows.** A later phone may add values to `TextStyle`, `Button.Kind` or
`Shadow` (each enum's KDoc says whether it grows); a `when` that lists them all without an `else` then breaks:

<!-- sample: apps/demo/src/test/kotlin/fr/bde_teknantes/lnb/phone/demo/guide/GuideSnippets.kt -->
```kotlin
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
- The phone's own Settings app, a second and smaller example: one screen with a segmented control and toggles,
  registered like any app (`phone/.../client/settings/SettingsApp.kt` and `SettingsScreen.kt`; its classes are
  internal to the phone, but it uses only the public kit).
- The demo's other apps: the kit's gallery ("UI kit") and the sample Feed ("Feed (sample)"), described in
  `apps/demo/README.md`.
- `design/art-direction.md` for the look of the phone and of the Feed app (section 9), if you need a value the kit
  doesn't give.
