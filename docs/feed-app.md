# Feed: the app's design

The design of Feed, the first real app of the LNB Phone: a mod of its own, `apps/feed` (mod id `lnbphone_feed`), built with the `lnbphone.app` plugin, depending on the phone (`lnbphone`) and on LNB Warden (`lnbwarden`). This page says how the mod is cut, what crosses the network between the player's client and the Minecraft server, what the server does with the website, and how each screen of the art direction's section 9 is fed.

It sits on two documents of the LNB website repository, which are the contract and win if this page disagrees with them:

- `docs/feed.md`: the feature on the site (rules, data, rights, flags, privacy).
- `docs/feed-mod.md`: every route under `/api/feed/server/`, the `feed` stream, problem codes, limits and test vectors.

Field names, problem codes and limits here are those of `feed-mod.md`. Read `docs/writing-an-app.md` first: the sides, threads and the "screens are thrown away" model it explains are assumed below.

## 1. What is decided where

| Question | Decided by |
| --- | --- |
| Who may read, write, announce, moderate, pin, delete | the site, on every request; the mod and the client only use rights to choose what to show |
| Whether a text is accepted, and the text stored | the site (the client and the server check first with `TextLimits`, the same rule, so the site never refuses what the player was allowed to send) |
| Every per-viewer field (liked, mine, can delete, can pin, unseen replies, the author of an anonymous post) | the site, per viewer |
| Who is notified | the site (a queue the server drains) |
| Read state (tab dots, unseen-replies markers) | the site, per LNB account |
| How it looks and moves | the client, from the art direction |

The Minecraft server holds no state that matters after a restart: caches of the online players' rights, who has Feed open, and nothing on disk.

## 2. Module layout

```text
apps/feed/
  build.gradle.kts                 plugins { id("lnbphone.app") }, and the Warden jar (below)
  gradle.properties                mod_id=lnbphone_feed, mod_name=LNB Feed, lnbwarden_version_range
  libs/lnbwarden-<version>.jar     not committed: built from lnb-warden with ./gradlew jar
  src/main/templates/META-INF/neoforge.mods.toml
  src/main/kotlin/fr/bde_teknantes/lnb/phone/feed/
    FeedMod.kt          @Mod("lnbphone_feed"): config, payloads, server events; the client behind the dist check
    Feed.kt             APP_ID (lnbphone_feed:feed) and the limits (280, 3 pins, page sizes)
    FeedTarget.kt       notification targets: format and parse
    model/              PostView, Author, ReplyRef, FeedKind, Rights, PlayerState, Unread, Pin, CountUpdate,
                        RemovedItem, Outcome, and their stream codecs
    rules/FeedText.kt   the site's text rule (clean, NFC, trim, fold, count), pure Kotlin
    network/            the payloads and FeedNetwork.register
    server/             the logical server: a dedicated server, or a single-player game's integrated one
      FeedServer.kt     payload handlers, watchers, per-player limits
      FeedConfig.kt     lnbphone_feed-server.toml
      RightsCache.kt    online players' state, 5 minutes at most
      Relay.kt          stream events to the watchers' clients
      FeedNotifier.kt   the notification queue to PhoneNotifications; taking notifications back
      FeedCommand.kt    /feed announcer
      backend/FeedBackend.kt          the interface and BackendResult
      backend/site/SiteBackend.kt     the website, through Warden's ApiClient
      backend/site/FeedEvents.kt      the site's `feed` stream
      backend/memory/MemoryBackend.kt the stand-in, in memory
      backend/memory/SampleContent.kt the art direction's sample posts (18.3)
    client/             the player's client only
      FeedClient.kt     registers the app, the payload handlers, clearing at disconnection
      FeedSession.kt    the client's state (section 8)
      FeedRequests.kt   request IDs and what each waits for
      screens/          FeedScreen, ThreadScreen, ComposeScreen
      widgets/          PostCard, ReplyComposer, NewPostsPill, PrivateLine
  src/main/resources/assets/lnbphone_feed/
    lang/en_us.json, fr_fr.json
    textures/gui/feed.png             the home icon (art direction 7.4)
  src/test/kotlin/...                 section 11
```

- `settings.gradle.kts` gets `include(":apps:feed")`.
- The Warden jar is taken as `lnb-quests` takes it: `val wardenJar = fileTree("libs") { include("lnbwarden*.jar") }`, then `compileOnly(wardenJar)`, `localRuntime(wardenJar)` and `testImplementation(wardenJar)`. `libs/` is git-ignored; the README of `apps/feed` says how to build the jar.
- `neoforge.mods.toml` declares `lnbwarden` as required, `side="BOTH"`, `ordering="AFTER"`, with `lnbwarden_version_range`. The plugin appends the dependency on `lnbphone`.
- **Sides.** `model`, `rules`, `network`, `FeedTarget` and `Feed` are common and load everywhere. `server` is common code that only the logical server runs: it may use Warden's `server` classes and `PhoneNotifications`, never a client class. `client` is reached only from behind `FMLEnvironment.dist.isClient`, named in full, or from inside a client payload handler's lambda. A dedicated server start (`./gradlew :apps:feed:runServer`) is part of every check.
- **Threads.** Payload handlers run on the server thread (NeoForge's default) and the client thread. Every backend call is asynchronous and answers on another thread: hop back with `server.execute { }` before touching players or sending payloads, and with `Minecraft.getInstance().execute { }` on the client.

## 3. The backend

### 3.1 The interface

Everything the server asks of the site goes through one interface, so the memory backend can stand in for it:

```kotlin
interface FeedBackend {
    fun player(player: UUID): CompletableFuture<BackendResult<PlayerState>>
    fun page(viewer: UUID, feed: FeedKind, before: String?, limit: Int): CompletableFuture<BackendResult<FeedPage>>
    fun thread(viewer: UUID, threadId: UUID, after: String?, limit: Int): CompletableFuture<BackendResult<ThreadPage>>
    fun views(viewers: List<UUID>, ids: List<UUID>): CompletableFuture<BackendResult<Map<UUID, List<PostView>>>>
    fun createPost(key: UUID, player: UUID, feed: FeedKind, text: String, anonymous: Boolean): CompletableFuture<BackendResult<Created>>
    fun createReply(key: UUID, player: UUID, threadId: UUID, replyTo: UUID?, text: String, anonymous: Boolean): CompletableFuture<BackendResult<Created>>
    fun like(player: UUID, postId: UUID, liked: Boolean): CompletableFuture<BackendResult<LikeState>>
    fun pin(player: UUID, postId: UUID, pinned: Boolean, replace: Boolean): CompletableFuture<BackendResult<PinState>>
    fun delete(player: UUID, postId: UUID): CompletableFuture<BackendResult<Unit>>
    fun markFeedSeen(player: UUID, feed: FeedKind, upTo: Instant): CompletableFuture<BackendResult<Unit>>
    fun markThreadSeen(player: UUID, threadId: UUID): CompletableFuture<BackendResult<Unit>>
    fun pendingNotifications(): CompletableFuture<BackendResult<NotificationBatch>>
    fun ackNotifications(ids: List<UUID>): CompletableFuture<BackendResult<Unit>>
    fun setAnnouncer(target: UUID, announcer: Boolean, issuer: Issuer?): CompletableFuture<BackendResult<AnnouncerState>>
    fun announcers(): CompletableFuture<BackendResult<List<AnnouncerEntry>>>

    /** Starts delivering the site's changes to [listener], until [stop]. */
    fun start(server: MinecraftServer, listener: FeedEventListener)
    fun stop()
}

sealed interface BackendResult<out T> {
    data class Ok<T>(val body: T) : BackendResult<T>
    /** The site refused: a status and its `data.problem`, with what some problems carry. */
    data class Refused(val status: Int, val problem: String?, val oldestPin: UUID? = null, val retryAfterSeconds: Int? = null) : BackendResult<Nothing>
    /** 503 `disabled`, with the flag that is off. */
    data class Disabled(val flag: String?) : BackendResult<Nothing>
    /** No answer, a 5xx, a 401, or a body that doesn't parse. */
    data class Unavailable(val reason: String) : BackendResult<Nothing>
}

interface FeedEventListener {
    fun onConnected()                                  // after every successful connection (catch up: feed-mod.md 8.3)
    fun onDisconnected(disabled: Boolean)              // the stream dropped, or answers 503 `disabled`
    fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant)
    fun onDeleted(items: List<RemovedItem>)            // { id, feed, threadId }
    fun onCounts(items: List<CountUpdate>)             // { id, likes, replies }
    fun onPins(pinned: List<Pin>)                      // { id, pinnedAt }, the whole group
    fun onRights(players: List<UUID>)
    fun onNotification()
}
```

The model classes carry exactly the JSON fields of `feed-mod.md` (section 3 for `PostView`, 4.1 for `PlayerState`, 4.12 for `NotificationBatch` with `items` and `more`). `Created` is `{ replayed, post }`, `LikeState` `{ id, likes, liked }`, `PinState` `{ pinned, unpinned }`, `AnnouncerState` `{ uuid, username, announcer, viaRole }`, `Issuer` `{ uuid, username }`.

### 3.2 `SiteBackend`

- Reads `api.url` and `api.token` from Warden's `ServerConfig` at each call, as `QuestsApi.connection` does; with either blank, every call is `Unavailable("api.url and api.token aren't set in lnbwarden-server.toml")`.
- Builds requests with `ApiClient.request(url, path, token)` and sends them with `ApiClient.HTTP.sendAsync`, decoding the bodies itself with `ApiClient.GSON`: `ApiClient.send` only keeps `data.problem`, and Feed needs `data.oldest` (`pinLimit`) and the `Retry-After` header (`rateLimited`). Timeout: 10 seconds for every call, the art direction's threshold for "Feed unavailable" (9.12).
- Records every outcome with `ApiHealth.record("feed <route>", …)`, turned into Warden's `ApiResult` (`Ok`, `Http(status, problem)` or `Network`), so operators hear of an outage once.
- Maps answers: 2xx is `Ok`; 503 with `disabled` is `Disabled(data.flag)`; 401, other 5xx, timeouts, connection errors and unparsable bodies are `Unavailable`; any other status is `Refused`.
- `FeedEvents` keeps `GET /api/feed/server/events` open on its own daemon thread, written like `QuestEvents` in `lnb-quests`: `Last-Event-ID` kept in memory, a connection dropped after 75 silent seconds, a backoff from 1 to 30 seconds, 60 seconds while the site answers 503 `disabled`, `onConnected` after each successful connection, `onDisconnected` once when a connection is lost or refused (not again for each failed retry), events parsed and handed to the listener (which hops to the server thread). Unknown event types are ignored.

### 3.3 `MemoryBackend`

The site's rules, in memory, for unit tests, the screenshot harness and developing the app without a site. It answers with completed futures and calls the listener right after each change, on the caller's thread.

| Topic | Behaviour |
| --- | --- |
| Data | Posts, replies, likes, pins, read state and the notification queue in memory, lost when the server stops. A `Clock` is injected, so tests and screenshots have fixed times. |
| Identity | Every player is linked, each Minecraft account its own LNB account, `realName` null, except players listed as guests in the config (read only) and the sample authors (who have the art direction's names). |
| Rights | `moderate` is the op permission level 2 or more (a stand-in for the site's roles, in memory mode only). `announce` is moderate or a grant from `/feed announcer add`. `write` is linked. Nobody is banned. `writing` is true unless `/feed dev writing off`. |
| Rules | The same as the site's: `FeedText` for text (with `emptyText`, `tooLong`), `anonymousAnnouncement`, idempotency by key with a fingerprint (`replayed`, `idempotencyMismatch`, `notFound` after a delete), 3 pins with `pinLimit` and `replace`, `notPinnable`, deletion of a thread with its main post, 30-day retention ignored (nothing is purged in a session), per-viewer views by the same rules, the rate limits of `feed-mod.md` section 7 (switchable off for tests), page and thread sizes, keyset cursors. |
| Notifications | Queued as on the site (`players` and `except`), drained by the same `FeedNotifier` code. |
| Events | `onPost`, `onDeleted`, `onCounts`, `onPins`, `onRights` and `onNotification` as the site sends them; `onConnected` once at start. |
| Dev switches | `/feed dev offline on|off` makes every call `Unavailable` and reports the stream down (the "Feed unavailable" and "Connection lost" states), `/feed dev disabled on|off` answers `Disabled("feed")` (the switched-off state), `/feed dev writing on|off` the `feed.writing` flag. Operators only, memory mode only. |
| Seed | `sample` loads the art direction's sample content (section 18.3: the authors, posts 1 to 10, replies, likes and pins) for screenshots; `none` starts empty. |

### 3.4 Choosing one

`config/lnbphone_feed-server.toml`, registered as a COMMON config like Warden's server config, read by the logical server only:

```toml
[backend]
# site: LNB's website, through api.url and api.token in lnbwarden-server.toml.
# memory: in-process stand-in, for development and tests. Nothing is kept.
kind = "site"

[memory]
seed = "none"        # none | sample
guests = []          # usernames that are guests (read only)
rateLimits = true
```

Changed only by a restart.

## 4. The server side

### 4.1 Watchers

A **watcher** is an online player who opened Feed since they joined (the `open` payload). Live changes are sent to watchers only; anyone else gets fresh data when they open Feed. A player stops being a watcher when they leave.

### 4.2 Rights

`RightsCache` keeps each online player's `PlayerState` (`feed-mod.md` 4.1). It reads it when the player joins (in the background: nothing waits for it), at every `open` (the tab dots in it must be fresh), and again at once on a `rights` event naming the player, on a `notAllowed`, `banned` or `notLinked` answer to their action, for every online player on `onConnected`, and whenever a copy older than 5 minutes is about to be used (the command); it forgets it when they leave. Each new state goes to the player (`state` payload) if they watch, and the command tree is sent again (`server.commands.sendCommands(player)`), so `/feed announcer` appears or disappears.

The server never computes a right. It uses the cached state for the command's visibility only; every action goes to the backend, which decides.

### 4.3 Requests from clients

Every request payload carries a `requestId` chosen by the client; the server answers it with the same ID, on the server thread, once the backend answered. Before calling the backend the server checks, in this order:

1. The sender is a `ServerPlayer` from the payload context. It is the `player` and the `viewer` of every call; no payload names a player.
2. The per-player limits: at most 4 backend calls in flight for the player; reads (`open`, `load_feed`, `load_thread`) a burst of 20 then 10 a second; writes (`create_post`, `create_reply`, `like`, `pin`, `delete`) a burst of 10 then 1 a second; `seen_*` at most 2 a second, the extra ones dropped. Over a limit, the server answers `RATE_LIMITED` without calling the site. These are a backstop against a modified client; the site's per-account limits are the real ones.
3. The fields: text through `TextLimits.canSend(text.trim(), 280)`, sent trimmed; a cursor of at most 128 characters; anything else is bounded by the codecs. A payload that fails answers `INVALID` (the client never sends one).

Then it calls the backend and maps the result to an `Outcome` (section 5.3).

### 4.4 Relaying the stream

`Relay` is the `FeedEventListener`; it hops to the server thread and:

| Event | What the server does |
| --- | --- |
| `onPost` | Calls `views` with the post and the watchers (chunks of 200; posts announced in the same tick share a call, up to 20), and sends each watcher their own views (`live`). Never one player's view to another. |
| `onDeleted` | Sends `removed` to the watchers, and takes back the notifications pointing at the rows (6.3). |
| `onCounts` | Sends `counts` to the watchers. |
| `onPins` | Sends `pins` to the watchers. |
| `onRights` | Refreshes those players (4.2). |
| `onNotification` | Drains the queue (6.1). |
| `onConnected` | Drains the queue, refreshes every online player's state (sent to those who watch, with `available: OK`), and sends `reset` to the watchers: their caches are stale. |
| `onDisconnected` | Sends the watchers `state` with `available: UNAVAILABLE` (or `DISABLED`), keeping their last `PlayerState`: the app shows "Connection lost" over what is loaded (art direction 9.12). |

### 4.5 When the site is away or Feed is off

- An `Unavailable` answer is `UNAVAILABLE` to the client; a `Disabled` one is `DISABLED` (and, for `feed.writing`, the server refreshes the player's state, whose `writing` is then false).
- Nothing on a player's join waits for the site (the state read at join runs in the background), so the site being away never delays joining.
- The stream reconnects on its own; `onConnected` catches up.

## 5. Payloads

Registered from `FeedMod` on both sides, with `event.registrar("1")`; bump the version when a payload changes. Every string codec is bounded (`ByteBufCodecs.stringUtf8(max)`, `max` in UTF-16 units), lists with `ByteBufCodecs.list(max)`, UUIDs with `UUIDUtil.STREAM_CODEC`, times as epoch milliseconds in a `VAR_LONG`, optional fields with `ByteBufCodecs.optional`. A payload past a bound fails to decode and the connection drops, which only a modified client can cause.

### 5.1 Shared shapes

| Shape | Fields and bounds |
| --- | --- |
| `FeedKind` | `ANNOUNCEMENTS` or `GLOBAL` (a byte) |
| `Author` | `uuid`; `username` (16); `realName` (optional, 128 units: 64 code points); `announcer` |
| `ReplyRef` | `id` (optional UUID); `anonymous`; `name` (optional, 128) |
| `PostView` | `id`; `feed`; `threadId` (optional); `replyTo` (optional `ReplyRef`); `text` (560: 280 code points); `createdAt`; `anonymous`; `author` (optional `Author`); `likes`, `replies` (var ints); `pinnedAt` (optional); `liked`, `mine`, `canDelete`, `canPin`, `unseenReplies`, `answersMe` |
| `Rights` | `write`, `announce`, `moderate` |
| `Unread` | `announcements`, `global` |
| `PlayerState` | `linked`, `banned`, `writing`, `me` (optional `Author`), `rights`, `unread` |
| `Pin` | `id`, `pinnedAt` |
| `CountUpdate` | `id`, `likes`, `replies` |
| `RemovedItem` | `id`, `feed`, `threadId` (optional) |
| `Outcome` | see 5.3 (a byte) |

A page of 50 posts with 3 pinned is at most about 110 KB, far under the 1 MiB a server-to-client payload may hold.

### 5.2 The payloads

Paths are under `lnbphone_feed:`.

**Client to server.** Anyone connected may send any of them; the server checks each as 4.3 says, then the site decides.

| Payload | Fields | What the server does |
| --- | --- | --- |
| `open` | none | Makes the sender a watcher, reads their state again (`player`), answers `state`. |
| `load_feed` | `requestId`, `feed`, `before` (optional, 128) | `page(sender, feed, before, 20)`, answers `page`. |
| `load_thread` | `requestId`, `threadId`, `after` (optional, 128) | `thread(sender, threadId, after, 50)`, answers `thread`. |
| `create_post` | `requestId`, `key` (UUID), `feed`, `text` (560), `anonymous` | Checks the text, `createPost(key, sender, …)`, answers `result` with the post. |
| `create_reply` | `requestId`, `key`, `threadId`, `replyTo` (optional), `text` (560), `anonymous` | Checks the text, `createReply(…)`, answers `result` with the reply. |
| `like` | `requestId`, `postId`, `liked` | `like(sender, postId, liked)`, answers `result` with `likes` and `liked`. |
| `pin` | `requestId`, `postId`, `pinned`, `replace` | `pin(…)`, answers `result` with the pins (and `oldestPin` on `PIN_LIMIT`). |
| `delete` | `requestId`, `postId` | `delete(sender, postId)`, answers `result`. |
| `seen_feed` | `feed`, `upTo` (time) | `markFeedSeen(sender, feed, upTo)` for a linked sender; no answer. |
| `seen_thread` | `threadId` | `markThreadSeen(sender, threadId)` for a linked sender; no answer. |

**Server to client.** Only the server sends them; the client takes them as they come, on its thread.

| Payload | Fields | Sent to |
| --- | --- | --- |
| `state` | `available` (`OK`, `UNAVAILABLE` or `DISABLED`), `state` (optional `PlayerState`) | the sender of `open`; a watcher whose state was refreshed; the watchers when the stream drops or comes back |
| `page` | `requestId`, `outcome`, `feed`, `pinned` (at most 3), `posts` (at most 50), `next` (optional, 128) | the sender of `load_feed` |
| `thread` | `requestId`, `outcome`, `post` (optional), `replies` (at most 50), `next` (optional, 128) | the sender of `load_thread` |
| `result` | `requestId`, `outcome`, `post` (optional), `likes` (optional), `liked` (optional), `pinned` (optional list, at most 3), `unpinned` (optional), `oldestPin` (optional), `retryAfter` (optional seconds) | the sender of a write |
| `live` | `posts` (at most 20), each the receiver's own view | watchers, on `onPost` |
| `removed` | `items` (at most 512 per payload; more are split) | watchers |
| `counts` | `items` (at most 256 per payload) | watchers |
| `pins` | `pinned` (at most 3) | watchers |
| `reset` | none | watchers, on `onConnected` |

### 5.3 Outcomes

| Outcome | From | The app shows |
| --- | --- | --- |
| `OK` | 2xx | the result |
| `UNAVAILABLE` | `Unavailable`; a 400 without a problem | a load with nothing on screen: the `feed.unavailable.*` block; over a loaded list: `feed.connection_lost`; loading more: the retry row; a write: `snackbar.send_failed` with Retry (post, reply) or `snackbar.action_failed` (like, pin, delete) (art direction 9.12) |
| `DISABLED` | 503 `disabled` | a load with nothing on screen: the `feed.disabled.*` block; otherwise as `UNAVAILABLE`. For `feed.writing`, the new state also hides composing |
| `RATE_LIMITED` | 429, or the server's own limits | `snackbar.too_fast` with the seconds; the draft stays |
| `NOT_LINKED` | 404 `notLinked` | `guest.snackbar` (the new state follows, which turns the app to its guest look) |
| `BANNED` | 403 `banned` | `snackbar.banned` (the new state follows) |
| `NOT_ALLOWED` | 403 `notAllowed` | `snackbar.action_failed` (the new state follows) |
| `NOT_FOUND` | 404 `notFound` | the post leaves the lists; in its thread, back to the feed with "This post was deleted" |
| `PIN_LIMIT` | 409 `pinLimit` | the "Replace a pin?" dialog (it only reaches the server when two announcers pin at once) |
| `NOT_PINNABLE` | 409 `notPinnable` | "Couldn't do that" |
| `INVALID` | `emptyText`, `tooLong`, `anonymousAnnouncement`, `idempotencyMismatch`, a payload the server refused | "Couldn't send"; logged on the server |

## 6. Notifications

### 6.1 Draining the queue

`FeedNotifier` drains the backend's queue (`pendingNotifications`, deliver, `ackNotifications` with the IDs delivered, again while `more`), one drain at a time: at `onConnected`, on `onNotification`, and every 5 minutes. It delivers with the app ID `lnbphone_feed:feed`:

- **`announcement`**: to every player of the world, minus `except` (the author's accounts): the UUIDs of the `<uuid>.dat` files in `server.getWorldPath(LevelResource.PLAYER_DATA_DIR)` and every online player. `PhoneNotifications.send(players, appId, title, text, target)`, in one call. Not `sendToEveryone`, which can't leave the author out: a player's own action never notifies them.
- **`reply`**: to `players`, every account linked to the recipient's LNB account. An empty `players` is acknowledged without sending.

| Kind | Title | Text | Target |
| --- | --- | --- | --- |
| `announcement` | `Component.translatable("notif.lnbphone_feed.announcement")` | `Component.translatable("notif.lnbphone_feed.text", name, post.text)` | `post:<postId>` |
| `reply` | `Component.translatable("notif.lnbphone_feed.reply")` | the same, `name` being `Component.translatable("lnbphone_feed.post.anonymous")` when `post.author` is null | `reply:<feed>:<threadId>:<replyId>` |

`name` is the author's display name (`realName ?: username`). The player's words are arguments, never a key. The phone cuts the text to 320 characters between graphemes and keeps a banner to two lines. A row is acknowledged once `send` was called: a crash in between delivers it twice at the next drain, which is better than losing it.

### 6.2 Targets

`FeedTarget` formats and parses them (`feed` is `announcements` or `global`):

| Target | Opens |
| --- | --- |
| `post:<postId>` | `FeedScreen` on Announcements, scrolled to the top, the post highlighted |
| `reply:<feed>:<threadId>:<replyId>` | `FeedScreen` on that feed, then `ThreadScreen` of the thread, scrolled to the reply and highlighted |

At most 93 characters. Anything else (an older or unknown target) opens Feed on Announcements.

### 6.3 Taking them back

On `onDeleted`, for each item, `PhoneNotifications.clearTargetForEveryone(appId, target)` with `post:<id>` for a main post and `reply:<feed>:<threadId>:<id>` for a reply. A post deleted before delivery has no row left in the queue (the site drops it).

### 6.4 What the phone does with them

Badge, LED, banner or peek and chime, delivery at the next login for offline players, 50 slots per player shared by every app, 30 days, the per-mod backstop (a burst of 20, then one every 3 seconds): all of this is the phone's (art direction 10). Opening Feed clears its notifications. A screen that already shows the target says so through `showsTarget` (section 9), and the phone then plays the chime without a banner.

## 7. The command

```text
/feed announcer add <username>
/feed announcer remove <username>
/feed announcer list
```

- **Who may run it.** A player whose cached state has `rights.moderate` (a site admin or Warden admin), or the server console. The command's `requires` checks exactly that: for a player, the cached `moderate`; for anything else, permission level 4, which the console has and command blocks and functions don't. The tree is sent again when a player's state changes (4.2). The site checks again: a player's call carries them as `issuer`, and the site answers `issuerNotAllowed` unless they moderate. The console's carries no issuer.
- **The target** is a Minecraft username, online or not. Its UUID is the offline UUID of the name (`UUIDUtil.createOfflinePlayerUUID(username)`), as the server runs in offline mode.
- `add` and `remove` call `setAnnouncer(uuid, true|false, issuer)`; `list` calls `announcers()`. Answers, in the issuer's language: done; already so; `viaRole` (they are a moderator, so an announcer already); `notLinked` (no LNB account); `impliedByRole` (a moderator can't lose it); `issuerNotAllowed`; unavailable.
- A change made here sends a `rights` event like one made in the admin, so the target's app follows within a second.
- There is no command to pin or delete: both are done in the app, by announcers and moderators.

## 8. The client's state

`FeedSession` is an object of the client, used on the client thread only, cleared at disconnection (`ClientPlayerNetworkEvent.LoggingOut`, hopping to the client thread) and marked stale on `reset`. Screens are rebuilt at every visit and read everything from it (and their arguments), which also makes them testable with `PhoneTester`. A `revision` counter goes up at every change; screens catch up in `onTick`.

| State | Content | Bounds |
| --- | --- | --- |
| `availability` | `UNKNOWN`, `LOADING`, `OK`, `UNAVAILABLE`, `DISABLED` | |
| `state` | the last `PlayerState`: linked, banned, writing, `me`, rights, unread | |
| `unread` | one flag per feed, from `state.unread`, then set by live posts, cleared by viewing | |
| `posts` | one copy of every post and reply the client holds, by ID, so counts, pins and markers apply everywhere at once | entries no feed or thread refers to are dropped |
| `feeds` | per feed: the pinned IDs (Announcements), the IDs newest first, `next`, loading and error flags, `stale`, `waiting` (live posts not shown yet: the pill's count) and the list's `ScrollState` | 200 posts per feed: older ones are dropped and reloaded on scroll |
| `threads` | per thread: the reply IDs oldest first, `next`, loading and error flags, `stale`, its `ScrollState` | the 16 most recent threads |
| `compose` | the Compose draft: a `TextState` (multiline, 1,000 characters, so the counter can show past 280), the anonymous toggle, the destination, the idempotency key, sending | one |
| `replyDrafts` | per thread: a `TextState`, the anonymous toggle, the target (`replyTo` and its display name), the key, sending | the 20 most recent threads |
| `pendingLikes` | likes shown before the server answered, to undo on failure | |
| `requests` | the requests waiting for an answer, by `requestId`, with what to do with it | |
| `highlight` | posts to highlight once when shown (new posts, a notification's target) | |

How it moves:

- **Opening Feed** (the root screen shown) sends `open`. The `state` answer sets rights, `me`, the dots and availability. A feed or thread that is missing or stale loads when shown (`load_feed`, `load_thread`).
- **A page** replaces the feed's first page (or appends a later one), puts the views in `posts`, and clears `stale` and `waiting`.
- **A live post** (`live`) goes into `posts`. A main post goes to the top of its feed when that list is shown at scroll 0 (highlighted), otherwise into `waiting` (the pill). If the post is in the feed not on screen and isn't `mine`, that tab's dot comes on. A reply joins its thread if loaded; with `answersMe`, the marker comes on for the thread's main post and for the reply it answers, unless that thread is on screen (then it is marked seen at once). A post already held (the author's own, from `result`) is replaced, never doubled.
- **`removed`** drops the rows from `posts`, the feeds and the threads. The thread on screen whose main post went pops back to the feed with "This post was deleted".
- **`counts`** and **`pins`** update `posts`; a post unpinned returns to its date order in the Announcements list.
- **`reset`** marks every feed and thread stale; the screen showing one reloads its first page and **merges** it, so the list catches up without jumping (art direction 9.12): posts newer than the newest held go in as live posts do (at the top at scroll 0, otherwise behind the pill), held posts missing from the page within its time span are dropped (deleted meanwhile), and the rest are updated in place. Drafts stay.
- **Connection lost.** A `state` with `available: UNAVAILABLE` or `DISABLED` while something is loaded shows `feed.connection_lost` with Retry and keeps everything readable. While unavailable, the client sends `open` again about every 15 seconds, and at once on Retry; an `OK` answer (or the `reset` that follows a reconnection) clears the snackbar with no message.
- **Viewing a tab** sends `seen_feed` with the newest `createdAt` among the posts shown there (pinned included), once a second at most, and clears that tab's dot. **Opening a thread** sends `seen_thread` once its first page is shown, and clears its markers in `posts`.
- **Writing.** Sending a draft gives it a key if it has none (`UUID.randomUUID()`), and keeps the key until the draft succeeds or changes (text, toggle, destination or reply target), so the Retry after a failure reuses it. Success clears the draft and inserts the post (at the top of its feed, scrolled to; or at the end of its thread, scrolled into view). Failure keeps the draft and its key.
- **Likes** show at once and are undone if the result isn't `OK`. **Pins** show at once as well (art direction 9.4); with 3 pinned the dialog comes first, and confirming sends `replace = true`. **Deletes** wait for the result, then collapse the cell.
- **Guests** (`state.linked` false) get the art direction's guest look (9.11): no compose button, the read-only strip in place of the reply composer, likes drawn but inactive with `guest.snackbar`, no more or pin button (their views never allow them). **Banned players and `writing` off** get the same look with their own snackbar (`snackbar.banned`, `snackbar.writing_off`, section 10); a banned player can't be online in practice, so this is for the moment between a ban and Warden's kick.

## 9. The screens

How each part of the art direction's section 9 is fed. "Session" is `FeedSession`; "post" fields are `PostView`'s.

| Art direction | Screen or widget | Fed by |
| --- | --- | --- |
| 9.1 Structure | `FeedScreen` (root: the two tabs, Announcements first), `ThreadScreen`, `ComposeScreen`. `targetScreens` builds the stack of 6.2. Reopening the phone returns to the screen it was closed on (the phone keeps it). | session |
| 9.2 Identity | `PostCard`: display name `realName ?: username`, handle `"@" + username` only with a `realName`, the face from `author.uuid` and `author.username` (`Avatar.showPlayer`), the badge from `author.announcer`. The names are the site's: first name and initial. | post `author` |
| 9.2 Roles | The compose button on Announcements and the destination control in Compose from `state.rights.announce`; on Global from `state.rights.write` and `state.writing`. | session `state` |
| 9.3 Post card | Counts from `likes` and `replies` (on a reply, the replies that answer it); the heart from `liked`; the more button from `canDelete`; the pin button from `canPin`; the unseen-replies marker from `unseenReplies`; the time from `createdAt` (real time). | post |
| 9.3 Anonymous | `anonymous` with `author` null: the anonymous avatar and "Anonymous". The private line: "Posted anonymously by you" when `mine`; "Author: {name} {handle}" when `author` is filled and not `mine` (the viewer moderates). | post |
| 9.4 Pins | The pinned group from the feed's pinned IDs, by `pinnedAt`; the dialog from the group's size (and `PIN_LIMIT`); `pins` keeps it in step. | session, `pin`, `pins` |
| 9.5 Global and new posts | Pages from `load_feed`; the spinner row and "You're all caught up" from `next`; live posts and the pill from `live` and `waiting`. | session, `live` |
| 9.6 Unread dots | `Tabs.setUnread` from the session's `unread`; viewing a tab sends `seen_feed`. | session, `state`, `live` |
| 9.7 Thread | `load_thread`, 50 replies per page, more on scroll with the spinner row at the end; the reply-to line from `replyTo` ("Anonymous" when `replyTo.anonymous`); the composer's target and placeholder from the replied post's display name, or "Anonymous"; "This post was deleted" from `removed` or `NOT_FOUND`. `showsTarget` is true for a `reply:` target of this thread. | session, `thread`, `removed` |
| 9.8 Compose | The author row from `state.me`; the destination control for announcers; the anonymous row hidden on Announcements; the draft and its key in the session; `create_post`. | session |
| 9.9 Deleting | The more button from `canDelete`; `delete`; the cell collapses on `OK`. | post, `result` |
| 9.10 Notifications | Section 6. `FeedScreen` on Announcements, at scroll 0, answers `showsTarget` true for a `post:` target. | phone |
| 9.11 Guests | `state.linked` false: the guest look (section 8); `empty.global.text_guest` on an empty Global. | session `state` |
| 9.12 Feed unavailable (F-3, F-22 to F-24) | `availability` and each load's outcome (5.3): the unavailable or switched-off block when nothing is loaded, `feed.connection_lost` over a loaded list with the 15-second retry, the retry row when a later page fails, the merge on `reset`. | session, `state`, `reset` |

## 10. Strings the art direction doesn't have yet

The app uses section 13.2 of the art direction (version 6), guests' and unavailability strings included, and needs these besides. They are proposed here for the art direction's owner to place and word; the keys are under the mod's namespace.

| Key | Français | English | Where |
| --- | --- | --- | --- |
| `snackbar.banned` | Impossible de publier pendant une sanction | You can't post during a sanction | a banned player (`BANNED`) |
| `snackbar.writing_off` | La publication est suspendue pour l'instant | Posting is paused for now | `writing` off |
| `snackbar.too_fast` | Doucement ! Réessaie dans {n} s | Slow down! Try again in {n}s | `RATE_LIMITED` |
| `command.*` | | | the `/feed announcer` answers (7) |

## 11. Where the design and the contract differ

Checked against version 6 of the art direction (moderators, announcers as a site role, first names and initials, guests, the unavailable states), which this page follows. What remains for its owner to settle:

1. **The role's name.** The art direction calls the site role `announcer`; the contract names it `feed-announcer`, after the site's other roles (`warden-admin`, `events-manager`, `economy-admin`). Players never see it.
2. **The reply count of a reply.** Section 9.3 says "the number of replies in the post's thread". The contract counts, on a reply, the replies that answer it; on a main post, its thread.
3. **Threads are paged**, 50 replies at a time, with the spinner row (and 9.12's retry row) at the end of the list as in feeds; section 9.7 only shows the first load.
4. **The announcer badge** follows the author's role now (like the name). An announcement by someone who lost the role shows no badge, although 9.4 says every announcement carries it.
5. **Banned players and `writing` off** aren't drawn; section 8 gives them the guest look with snackbars of their own (section 10).
6. **The command's syntax** (section 7), which the art direction leaves out, and **no pin command**: pinning is in the app only.

Nothing else in section 9 or flows F-1 to F-25 needs more than the contract gives.

## 12. Developing and testing

### 12.1 Without a site

`./gradlew :apps:feed:runClient` with `backend.kind = "memory"` in `apps/feed/run/config/lnbphone_feed-server.toml` (and `memory.seed = "sample"` for content). Make yourself an operator (single player with cheats, or `/op`) to be a moderator; `/feed announcer add <name>` for an announcer; list a name under `memory.guests` to try reading as a guest; `/feed dev offline on` for the unavailable screens. The screenshot harness runs this way, with the sample seed and a fixed clock.

### 12.2 Against a local site

As `feed-mod.md` section 14 says: the site on `http://localhost:3000` with `feed` on, Warden's `lnbwarden-server.toml` pointing at it with the secret, `backend.kind = "site"`, and the dev account linked through Warden's device flow on the local `/link`.

### 12.3 Tests

- `FeedTextTest`: the text vectors of `feed-mod.md` 13.1, against `FeedText` and `TextLimits.canSend` (whatever the client allows, the rule accepts).
- `MemoryBackendTest`: the vectors 13.2 to 13.6 (idempotency, anonymity by viewer, pagination, pins, unread and markers).
- `FeedTargetTest`: format, parse, unknown and malformed targets.
- `PayloadCodecTest`: every payload round-trips, and one past each bound fails to decode.
- `RelayTest` and `FeedNotifierTest` with the memory backend: a post reaches each watcher as their own view; an announcement leaves its author out; a deletion takes its notifications back; a reconnection drains, refreshes and resets.
- `FeedSessionTest`: live posts at the top or in the pill, no doubles, counts and pins applied once, `removed` in an open thread, drafts and keys across failures, `reset`.
- Screens with `PhoneTester`, fed from `FeedSession`: the art direction's states (empty, unavailable, pinned group, anonymous lines, compose for a player and for an announcer, the pin-limit dialog, the delete dialog).
- A dedicated server start, which must not mention a client class.
