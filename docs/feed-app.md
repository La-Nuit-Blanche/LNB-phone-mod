# Feed: the app's design

Status: built. This page is Feed's design, kept true to the code; `apps/feed/README.md` is the guide to installing,
configuring, running and releasing it.

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
  example-config/                  the memory backend's lnbphone_feed-server.toml and Warden's lnbwarden-server.toml,
                                   copied into a run folder by prepareMemoryRun and by the capture run
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
      FeedServer.kt     wires the game's events and the payloads to the running server's FeedService; chooses the backend
      FeedService.kt    watchers, the clients' requests, the relay and the notifier, over a backend and a FeedHost
      FeedHost.kt       what the service needs from the running server (a fake in tests)
      PlayerLimits.kt   per-player and whole-server limits; ThrottledLog.kt the log's own rate limit
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
      screens/          FeedScreen, ThreadScreen, ComposeScreen, FeedTargetScreens, PostActions
      widgets/          PostCard (and its layout), ReplyComposer, NewPostsPill, ActionButton, RetryRow
  src/main/resources/assets/lnbphone_feed/
    lang/en_us.json, fr_fr.json
    textures/gui/feed.png             the home icon (art direction 7.4), drawn by tools/sprites: never edited by hand
  src/capture/kotlin/...              the screenshot harness (section 12.4): a source set the jar never holds
  src/test/kotlin/...                 section 12.3
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

    /** Starts delivering the site's changes to [listener] (on any thread: it hops to the server thread), until [stop]. */
    fun start(listener: FeedEventListener)
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
- Builds requests with `ApiClient.request(url, path, token)` and sends them with `ApiClient.HTTP.sendAsync`, decoding the bodies itself with `ApiClient.GSON`: `ApiClient.send` only keeps `data.problem`, and Feed needs `data.oldest` (`pinLimit`) and the `Retry-After` header (`rateLimited`). Timeout: 10 seconds for every call, the art direction's threshold for "Feed unavailable" (9.12). The request's timeout only covers the wait for the headers, so each call also has a total deadline of 12 seconds, body included: past it the exchange is cancelled and the call is `Unavailable` (and a failure for `ApiHealth`), so a site that stalls mid-answer can't hold a player's slot or the notifier's drain. The stream is long-lived on purpose and has its own watchdog instead.
- Records every outcome with `ApiHealth.record("feed <route>", …)`, turned into Warden's `ApiResult` (`Ok`, `Http(status, problem)` or `Network`), so operators hear of an outage once.
- Maps answers: 2xx is `Ok`; 503 with `disabled` is `Disabled(data.flag)`; 401, other 5xx, timeouts, connection errors and unparsable bodies are `Unavailable`; any other status is `Refused`.
- `FeedEvents` keeps `GET /api/feed/server/events` open on its own daemon thread, written like `QuestEvents` in `lnb-quests`: `Last-Event-ID` kept in memory, a connection dropped after 75 silent seconds (and its answer's headers awaited as long, a safety net: the site opens the stream with a ping, but a site or proxy that holds it back sends them only with the 25-second ping), a backoff from 1 to 30 seconds (back to 1 only after a connection that stayed up a minute, so a proxy that accepts and drops the stream can't bring a catch-up every second), 60 seconds while the site answers 503 `disabled`, `onConnected` after each successful connection, `onDisconnected` once when a connection is lost or refused (not again for each failed retry, except a refusal as switched off, told at each attempt: a player's own read may have found Feed back in between, while a flag went on and off again), events parsed and handed to the listener (which hops to the server thread). Unknown event types are ignored.

### 3.3 `MemoryBackend`

The site's rules, in memory, for unit tests, the screenshot harness and developing the app without a site. It answers with completed futures and calls the listener right after each change, on the caller's thread.

| Topic | Behaviour |
| --- | --- |
| Data | Posts, replies, likes, pins, read state and the notification queue in memory, lost when the server stops. A `Clock` is injected, so tests and screenshots have fixed times. |
| Identity | Every player is linked, each Minecraft account its own LNB account, `realName` null, except players listed as guests in the config (read only) and the sample authors (who have the art direction's names). |
| Rights | `moderate` is the op permission level 2 or more (a stand-in for the site's roles, in memory mode only). `announce` is moderate or a grant from `/feed announcer add`. `write` is linked. Nobody is banned. `writing` is true unless `/feed dev writing false`. |
| Rules | The same as the site's: `FeedText` for text (with `emptyText`, `tooLong`), anonymous posts in both feeds, idempotency by key with a fingerprint (`replayed`, `idempotencyMismatch`, `notFound` after a delete), 3 pins with `pinLimit` and `replace`, `notPinnable`, deletion of a thread with its main post, 30-day retention ignored (nothing is purged in a session), per-viewer views by the same rules, the rate limits of `feed-mod.md` section 7 (switchable off for tests), page and thread sizes, keyset cursors. |
| Notifications | Queued as on the site (`players` and `except`), drained by the same `FeedNotifier` code. |
| Events | `onPost`, `onDeleted`, `onCounts`, `onPins`, `onRights` and `onNotification` as the site sends them; `onConnected` once at start. |
| Dev switches | `/feed dev offline true|false` makes every call `Unavailable` and reports the stream down (the "Feed unavailable" and "Connection lost" states), `/feed dev disabled true|false` answers `Disabled("feed")` (the switched-off state), `/feed dev writing true|false` the `feed.writing` flag. Operators only, memory mode only. |
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

Changed only by a restart. The site backend runs on a dedicated server only: a single-player or LAN world with `kind = "site"` calls nothing and Feed says it is unavailable there, as LNB Warden never calls the site from one either. The memory backend works in any world.

## 4. The server side

### 4.1 Watchers

A **watcher** is an online player who opened Feed since they joined (the `open` payload). Live changes are sent to watchers only; anyone else gets fresh data when they open Feed. A player stops being a watcher when they leave.

### 4.2 Rights

`RightsCache` keeps each online player's `PlayerState` (`feed-mod.md` 4.1). It reads it when the player joins (in the background: nothing waits for it), at every `open` (the tab dots in it must be fresh), and again at once on a `rights` event naming the player, on a `notAllowed`, `banned` or `notLinked` answer to their action, for every online player on `onConnected`, and whenever a copy older than 5 minutes is about to be used (the command: read at the next tick, and not again within a minute of the last read, answered or not); it forgets it when they leave. Reads are numbered per player, and an older one answered after a newer one is not kept. Each new state goes to the player (`state` payload) if they watch, and when its `moderate` right differs from the copy before (none counting as false) the command tree is sent again (`server.commands.sendCommands(player)`), so `/feed announcer` appears or disappears.

The server never computes a right. It uses the cached state for the command's visibility only; every action goes to the backend, which decides.

### 4.3 Requests from clients

Every request payload carries a `requestId` chosen by the client; the server answers it with the same ID, on the server thread, once the backend answered. Before calling the backend the server checks, in this order:

1. The sender is a `ServerPlayer` from the payload context. It is the `player` and the `viewer` of every call; no payload names a player.
2. The limits (`PlayerLimits`). The site lets the whole Minecraft server make 1,200 calls a minute, shared by every player, so the server keeps each client to a small share of it and all of them together well under it, leaving room for its own calls (the relay, the queue, the reads at each reconnection):
   - at most 4 backend calls in flight for the player;
   - per kind: reads (`open`, `load_feed`, `load_thread`, and the state read the server makes for the player at join or after a `notAllowed`, `banned`, `notLinked` or `feed.writing` refusal) a burst of 20, then 1 a second; writes (`create_post`, `create_reply`, `like`, `pin`, `delete`) a burst of 10, then 1 every 2 seconds; `seen_*` a burst of 3, then 1 every 2 seconds, the extra ones dropped (the client sends `seen_feed` when what it shows changes, not on a timer);
   - per player, all kinds together: a burst of 40, then 90 a minute;
   - the whole server, every player together: a burst of 100, then 600 a minute.

   Leaving resets nothing: a client that leaves and comes back finds the allowance it left, and answers to calls made before it left free the slots they took. Over a limit, the server answers `RATE_LIMITED` (1 second) without calling the site; an `open` past it gets `state` with `UNAVAILABLE` and the last state known, so the client's 15-second retry takes over; seen marks past it are dropped, and a state read past it is skipped. No other request goes unanswered. A player scrolling both feeds, opening threads and liking quickly stays far under these; the site's per-account limits are the ones about behaviour.
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
| `onDisconnected` | Sends the watchers `state` with `available: UNAVAILABLE` (or `DISABLED`) and no `PlayerState`: each client keeps its own, which may be newer than the server's copy (dots cleared since); the app shows "Connection lost" over what is loaded (art direction 9.12). Told again at each refusal as switched off, it adds no new snackbar. |

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
| `PostView` | `id`; `feed`; `threadId` (optional); `replyTo` (optional `ReplyRef`); `text` (560: 280 code points); `createdAt`; `anonymous`; `author` (optional `Author`); `likes`, `replies` (var ints); `pinnedAt` (optional); `liked`, `mine`, `canDelete`, `canPin`; `unseenReplies` (var int); `answersMe` |
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
| `RATE_LIMITED` | 429, or the server's own limits | `snackbar.too_fast` with the seconds (`retryAfter`, or 1 for the server's own limits), an error snackbar without an action; the draft stays (art direction 9.11) |
| `NOT_LINKED` | 404 `notLinked` | `guest.snackbar` (the new state follows, which turns the app to its guest look) |
| `BANNED` | 403 `banned` | `snackbar.banned`, as an error snackbar without an action when a draft was refused (the new state follows) |
| `NOT_ALLOWED` | 403 `notAllowed` | `snackbar.action_failed` (the new state follows) |
| `NOT_FOUND` | 404 `notFound` | the post leaves the lists; in its thread, back to the feed with `thread.deleted` |
| `PIN_LIMIT` | 409 `pinLimit` | the "Replace a pin?" dialog (it only reaches the server when two announcers pin at once) |
| `NOT_PINNABLE` | 409 `notPinnable` | `snackbar.action_failed` |
| `INVALID` | `emptyText`, `tooLong`, `idempotencyMismatch`, a payload the server refused | `snackbar.send_failed`; logged on the server |

## 6. Notifications

### 6.1 Draining the queue

`FeedNotifier` drains the backend's queue (`pendingNotifications`, deliver, `ackNotifications` with the IDs delivered, again while `more`), one drain at a time: at `onConnected`, on `onNotification`, and every 5 minutes. It delivers with the app ID `lnbphone_feed:feed`:

- **`announcement`**: `PhoneNotifications.sendToEveryone(appId, title, text, target, except)`, every player of the world but the author's accounts (6.5). A player's own action never notifies them, an anonymous announcement included; `except` is handed to the phone and goes nowhere else (no log, no payload).
- **`reply`**: to `players`, every account linked to the recipient's LNB account. An empty `players` is acknowledged without sending.

| Kind | Title | Text | Target |
| --- | --- | --- | --- |
| `announcement` | `Component.translatable("notif.lnbphone_feed.announcement")` | `Component.translatable("notif.lnbphone_feed.text", name, post.text)` | `post:<postId>` |
| `reply` | `Component.translatable("notif.lnbphone_feed.reply")` | the same | `reply:<feed>:<threadId>:<replyId>` |

`name` is the author's display name (`realName ?: username`), or `Component.translatable("lnbphone_feed.post.anonymous")` when `post.author` is null (an anonymous announcement or reply): "Anonymous: …". The player's words are arguments, never a key. The phone cuts the text to 320 characters between graphemes and keeps a banner to two lines. A row is acknowledged once `send` was called: a crash in between delivers it twice at the next drain, which is better than losing it.

### 6.2 Targets

`FeedTarget` formats and parses them (`feed` is `announcements` or `global`):

| Target | Opens |
| --- | --- |
| `post:<postId>` | `FeedScreen` on Announcements, scrolled to the top, or further when the announcement sits under the pinned group out of view, so that it is fully visible; highlighted |
| `reply:<feed>:<threadId>:<replyId>` | `FeedScreen` on that feed, then `ThreadScreen` of the thread, scrolled to the reply and highlighted |

At most 93 characters. Anything else (an older or unknown target) opens Feed on Announcements.

### 6.3 Taking them back

On `onDeleted`, for each item, `PhoneNotifications.clearTargetForEveryone(appId, target)` with `post:<id>` for a main post and `reply:<feed>:<threadId>:<id>` for a reply. A post deleted before delivery has no row left in the queue (the site drops it).

### 6.4 What the phone does with them

Badge, LED, banner or peek and chime, delivery at the next login for offline players, 50 slots per player shared by every app, 30 days, the per-mod backstop (a burst of 20, then one every 3 seconds): all of this is the phone's (art direction 10). Opening Feed clears its notifications. A screen that already shows the target says so through `showsTarget` (section 9), and the phone then plays the chime without a banner. `FeedScreen` answers true for a `post:` target only when that announcement is fully visible on screen: a new announcement waiting under the pinned group, out of view, still gets its banner.

### 6.5 The phone's addition: notifying everyone except some players

The phone has two overloads of `PhoneNotifications.sendToEveryone` that leave some players out. Feed needs them, since an announcement must not notify its author, but they are general, so they live in the phone (`phone/`, in this repository) like the rest of the notification API. They came in phone 0.1.1, beside the existing functions, whose signatures and behaviour did not change. Feed doesn't run on 0.1.1, though: an app's `lnbphone` range, which the app plugin writes, starts at the phone it is built against and stops before the next minor version, so Feed 0.2.0 needs a phone from 0.2.0 up to, not including, 0.3. And 0.2.0 is not compatible with apps built against 0.1: `KitScreen.showSnackbar` and `showErrorSnackbar` now return a `SnackbarHandle` where they returned nothing, which changes their binary signature, so such an app must be rebuilt (its own range, up to 0.2, already keeps it off a 0.2 phone).

```kotlin
/**
 * Notifies every player who has played on this world, connected or not, except [except] (the author of an
 * announcement, who must not be told of their own post). See the other [sendToEveryone] for who "every player" is,
 * and the [send] by UUID for the other parameters. [except] is read during the call; UUIDs in it that aren't players
 * of the world are ignored, and an empty collection sends to everyone.
 */
@JvmStatic
fun sendToEveryone(appId: ResourceLocation, title: Component, text: Component, target: String?, except: Collection<UUID>)

/** As the other [sendToEveryone] with [except], leading to your app's first screen. */
@JvmStatic
fun sendToEveryone(appId: ResourceLocation, title: Component, text: Component, except: Collection<UUID>) =
    sendToEveryone(appId, title, text, null, except)
```

- **Who gets it**: the players of `NotificationHost.knownPlayers()` (the world's `playerdata/<uuid>.dat` files and the connected players, read when the sending runs on the server thread) minus `except`. A player in `except` gets nothing, connected or not: no badge, banner, peek or chime.
- **Offline players** get it as any notification sent by UUID: stored with the world, shown as a badge at their next login (no peek, no chime: art direction 10.2), dropped after 30 days. A player who first joins afterwards doesn't get it.
- **Checks and threads** as the existing `sendToEveryone`: the arguments are checked in the caller's thread (`Request`), `except` is copied there, and the sending runs on the server thread. The per-mod backstop and the shared 50 slots apply to each recipient as for any notification.
- **Inside**: `NotificationService.sendToEveryone(host, appId, title, text, target, except: Set<UUID>)` sends to `host.knownPlayers() - except`; the existing service function calls it with an empty set.
- **Tests** beside the existing ones: an excluded player, connected and offline, gets nothing; the others get it; an empty `except` behaves as before; an unknown UUID in `except` changes nothing.

## 7. The command

```text
/feed announcer add <username>
/feed announcer remove <username>
/feed announcer list
```

- **Who may run it.** A player whose cached state has `rights.moderate` (a site admin or Warden admin), or the server console. The command's `requires` checks exactly that: for a player, the cached `moderate`; for anything else, permission level 4, which the console has and command blocks and functions don't. A player acts only as themselves: the command must come from them (the source that started it, which `execute` never changes), that player must be the one the server's player list holds for their UUID (not a mod's fake player made with a moderator's UUID), and the command must still run as them, so `execute as <moderator>` run by an operator, a command block or a function never acts in a moderator's name; from anything that isn't a player, it goes as the console only at level 4, which `execute` can't raise. Functions run at the server's `function-permission-level` (2 by default): a function reaches the console's path only if the server's owner raised it to 4. The tree is sent again when a player's `moderate` right changes (4.2). The site checks again: a player's call carries them as `issuer`, and the site answers `issuerNotAllowed` unless they moderate. The console's carries no issuer.
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
- **A live post** (`live`) goes into `posts`. A main post is inserted at its place (the top of Global; under the pinned group in Announcements), highlighted, when it would be fully visible there; otherwise it goes into `waiting`, which feeds the pill (art direction 9.5): its count, and its icon, `arrow_up` when the first waiting post's place is above the visible area, none when it is below or only partly visible (Announcements at scroll 0 with the pinned group pushing it down). A click on the pill, or scrolling until that place is fully visible, inserts the waiting posts and scrolls so the first is fully visible. If the post is in the feed not on screen and isn't `mine`, that tab's dot comes on. A reply joins its thread if loaded; with `answersMe`, and unless the reply is the viewer's own (`mine`), the marker goes up by one on what it answers when that is the viewer's (their reply, or their main post), and on the thread's main post when that main post is the viewer's, never on someone else's post, as the site counts it (`feed-mod.md` section 3). If that thread is on screen, nothing is raised: it is marked seen at once. A post already held (the author's own, from `result`) is replaced, never doubled.
- **`removed`** drops the rows from `posts`, the feeds and the threads. The thread on screen whose main post went pops back to the feed with "This post was deleted".
- **`counts`** and **`pins`** update `posts`; a post unpinned returns to its date order in the Announcements list.
- **`reset`** marks every feed and thread stale; the screen showing one reloads its first page and **merges** it, so the list catches up without jumping (art direction 9.12): posts newer than the newest held go in as live posts do (at the top at scroll 0, otherwise behind the pill), held posts missing from the page within its time span are dropped (deleted meanwhile), and the rest are updated in place. When more than a page was posted meanwhile, so that the page doesn't reach what is held, its posts still wait behind the pill as live posts do (with the tab's dot, then the highlight), the reader staying where they are; once they are let in, the list starts again from them and older posts load again on scroll from the page's cursor. Drafts stay.
- **Connection lost.** A `state` with `available: UNAVAILABLE` or `DISABLED` while something is loaded shows `feed.connection_lost` with Retry and keeps everything readable. While unavailable, the client sends `open` again about every 15 seconds, and at once on Retry; an `OK` answer (or the `reset` that follows a reconnection) clears the snackbar with no message.
- **Viewing a tab** sends `seen_feed` with the newest `createdAt` among the posts shown there (pinned included) when it changes, at most once every 2 seconds per feed, and clears that tab's dot. **Opening a thread** sends `seen_thread` once its first page is shown (and again, coalesced, when replies arrive while it is read), at most once every 2 seconds per thread, and clears its markers in `posts`. A mark that must wait waits per feed or thread and goes when it may, also once the player left the tab; all of them share the server's allowance (4.3: a burst of 3, then one every 2 seconds).
- **Writing.** Sending a draft gives it a key if it has none (`UUID.randomUUID()`), and keeps the key until the draft succeeds or changes (text, toggle, destination or reply target), so the Retry after a failure reuses it. Success clears the draft and inserts the post at its place (the top of Global, under the pinned group in Announcements), scrolled so that it is fully visible; a reply at the end of its thread, scrolled into view. Failure keeps the draft and its key.
- **Likes** show at once and are undone if the result isn't `OK`. **Pins** show at once as well (art direction 9.4); with 3 pinned the dialog comes first, and confirming sends `replace = true`. **Deletes** wait for the result, then collapse the cell.
- **Guests** (`state.linked` false) get the art direction's guest look (9.11): no compose button, the read-only strip in place of the reply composer, likes drawn but inactive with `guest.snackbar`, no more or pin button (their views never allow them). **Banned players and `writing` off** get the same look with their own words (`snackbar.banned`, `snackbar.writing_off`, art direction 9.11); a banned player can't be online in practice, so this is for the moment between a ban and Warden's kick. One exception: the more and pin buttons follow the post views' `canDelete` and `canPin` whatever the state (the pin on an announcement's main post only). The site still deletes and pins while `feed.writing` is off (`feed-mod.md` 11.2), so an author deletes their own posts, a moderator any post, and an announcer pins, with the usual dialogs and snackbars; a guest's views never allow either, and a banned player keeps what their views allow.

## 9. The screens

How each part of the art direction's section 9 is fed. "Session" is `FeedSession`; "post" fields are `PostView`'s.

| Art direction | Screen or widget | Fed by |
| --- | --- | --- |
| 9.1 Structure | `FeedScreen` (root: the two tabs, Announcements first), `ThreadScreen`, `ComposeScreen`. `targetScreens` builds the stack of 6.2. Reopening the phone returns to the screen it was closed on (the phone keeps it). | session |
| 9.2 Identity | `PostCard`: display name `realName ?: username`, handle `"@" + username` only with a `realName`, the face from `author.uuid` and `author.username` (`Avatar.showPlayer`), the badge from `author.announcer`. The names are the site's: first name and initial. | post `author` |
| 9.2 Roles | The compose button on Announcements and the destination control in Compose from `state.rights.announce`; on Global from `state.rights.write` and `state.writing`. | session `state` |
| 9.3 Post card | Counts from `likes` and `replies` (on a reply, the replies that answer it); the heart from `liked`; the more button from `canDelete`; the pin button from `canPin`; the unseen-replies marker when `unseenReplies` is above 0; the time from `createdAt` (real time). | post |
| 9.3 Anonymous | `anonymous` with `author` null: the anonymous avatar and "Anonymous", no handle, no badge, in both feeds (an anonymous announcement too). The private line: "Posted anonymously by you" when `mine`; "Author: {name} {handle}" when `author` is filled and not `mine` (the viewer moderates). | post |
| 9.4 Pins | The pinned group from the feed's pinned IDs, by `pinnedAt`; the dialog from the group's size (and `PIN_LIMIT`); `pins` keeps it in step. | session, `pin`, `pins` |
| 9.5 Global and new posts | Pages from `load_feed`; the spinner row and "You're all caught up" from `next`; live posts and the pill from `live` and `waiting` (section 8: a post that would not be fully visible once inserted waits; the pill shows `arrow_up` when the first waiting post's place is above the visible area, and no icon when it is below or only partly visible, as in Announcements under the pins). | session, `live` |
| 9.6 Unread dots | `Tabs.setUnread` from the session's `unread`; viewing a tab sends `seen_feed`. | session, `state`, `live` |
| 9.7 Thread | `load_thread`, 50 replies per page, more on scroll with the spinner row at the end; the reply-to line from `replyTo` ("Anonymous" when `replyTo.anonymous`); the composer's target and placeholder from the replied post's display name, or "Anonymous"; "This post was deleted" from `removed` or `NOT_FOUND`. `showsTarget` is true for a `reply:` target of this thread. | session, `thread`, `removed` |
| 9.8 Compose | The author row from `state.me`; the destination control for announcers; the anonymous row usable whichever destination is selected (changing it leaves the toggle as it is); the draft and its key in the session; `create_post`. | session |
| 9.9 Deleting | The more button from `canDelete`; `delete`; the cell collapses on `OK`. | post, `result` |
| 9.10 Notifications | Section 6. `FeedScreen` answers `showsTarget` true for a `post:` target only when Announcements is shown and that announcement is fully visible (6.4). | phone |
| 9.11 Guests, banned players, writing off | `state.linked` false, `state.banned`, or `state.writing` false: the guest look (section 8), with `guest.*`, `snackbar.banned` or `snackbar.writing_off`; `empty.global.text_guest` on an empty Global; `snackbar.too_fast` on `RATE_LIMITED`. The more and pin buttons follow the views' `canDelete` and `canPin`, writing off included. | session `state`, post views |
| 9.12 Feed unavailable (F-3, F-22 to F-24) | `availability` and each load's outcome (5.3): the unavailable or switched-off block when nothing is loaded, `feed.connection_lost` over a loaded list with the 15-second retry, the retry row when a later page fails, the merge on `reset`. | session, `state`, `reset` |

## 10. Strings the art direction doesn't have yet

Every string the app shows is in the art direction's section 13.2, guests', unavailability and refusal snackbars included (its 9.11 says where each of `guest.*`, `snackbar.banned`, `snackbar.writing_off` and `snackbar.too_fast` shows; `snackbar.too_fast` has no Retry action). What it still lacks are the answers of the `/feed announcer` command (section 7), which are chat lines, not phone strings: `command.lnbphone_feed.announcer.added`, `.removed`, `.already`, `.via_role`, `.not_linked`, `.implied_by_role`, `.not_allowed`, `.unavailable` and `.list` (with `.list.empty`), in `en_us.json` and `fr_fr.json`, to be worded by the art direction's owner. The mod adds three more: `.already_not` (removing someone who wasn't an announcer: the site answers both cases the same, so the command reads the target's state first to tell them apart), `.invalid_name` (what was typed isn't a Minecraft username) and `command.lnbphone_feed.dev.set` (the memory backend's `/feed dev` switches). Its wording of all of them is a first draft.

## 11. Where the design and the contract differ

Checked against the art direction as of commit `e8ced9b` (version 6 with the user's later decisions: `feed-announcer`, anonymous announcements, the reply count of a reply, paged threads, the badge following the role, the banned and writing-off look and its strings, the pill's arrow). Nothing in section 9 or flows F-1 to F-25 needs more than the contract gives, and nothing there contradicts it. What the art direction leaves to this page on purpose is the command's syntax (section 7); pinning has no command and is done in the app.

## 12. Developing and testing

### 12.1 Without a site

```
./gradlew :apps:feed:prepareMemoryRun
./gradlew :apps:feed:runClient
```

`prepareMemoryRun` copies `apps/feed/example-config/` into `apps/feed/run/config` (keeping a file that is already there,
unless `-Poverwrite=true`): `backend.kind = "memory"` with `memory.seed = "sample"` for content, and Warden's `auth.mode`
at `off` so that the development account may join. `runServer` (with `-Pserver_port=<port>`, and `eula=true` in
`apps/feed/run/eula.txt`) starts a dedicated server the same way; joining it with `runClient` makes two players. Make
yourself an operator (single player with cheats, or `/op`) to be a moderator; `/feed announcer add <name>` for an
announcer; list a name under `memory.guests` to try reading as a guest; `/feed dev offline true`, `disabled true` and
`writing false` for the unavailable, switched-off and writing-paused screens. The screenshot harness runs this way, with
the sample seed and a fixed clock.

### 12.2 Against a local site

As `feed-mod.md` section 14 says: the site on `http://localhost:3000` with `feed` on, Warden's `lnbwarden-server.toml`
pointing at it with the secret, `backend.kind = "site"`, and the dev account linked through Warden's device flow on the
local `/link`. The memory setup of 12.1 must not be left in `run/config`: edit its files.

### 12.3 Tests

`./gradlew :apps:feed:test` (about 600 tests; `./gradlew build` runs them with everything else). They run with the game
loaded, in packages that follow the code:

- `rules/FeedTextTest`: the text vectors of `feed-mod.md` 13.1, against `FeedText` and `TextLimits.canSend` (whatever the
  client allows, the rule accepts).
- `server/backend/memory/`: `MemoryBackendTest` and `MemoryBackendRulesTest`, the vectors 13.2 to 13.6 (idempotency,
  anonymity by viewer with the anonymous announcement, pagination, pins, unread and markers).
- `server/backend/site/`: `SiteBackendTest`, `SiteBackendHttpTest`, `SiteJsonBoundsTest` and `StalledSiteTest` against a
  fake site (`FakeSite`: every route, every error, a body that stalls), and `FeedEventsTest` for the stream and its
  reconnections.
- `server/`: `FeedServiceTest` and `FeedRequestsTest` (requests, limits), `RightsCacheTest`, `RelayTest` (a post reaches
  each watcher as their own view; a deletion; a reconnection resets), `FeedNotifierTest` (an announcement, anonymous or
  not, leaves its author out and reads "Anonymous: ..."; a deletion takes its notifications back; the drain),
  `FeedCommandTest` and `FeedCommandActorTest` (who may run `/feed announcer`, and as whom), `BackendChoiceTest`,
  `SiteBudgetTest`.
- `network/`: `PayloadCodecTest` (every payload round-trips, and one past each bound fails to decode) and
  `HostilePayloadTest`. `FeedTargetTest`: format, parse, unknown and malformed targets. `FeedIconTest`: the icon ships.
- `client/`: `FeedSession*Test` (live posts at the top or in the pill, no doubles, counts and pins applied once, `removed`
  in an open thread, drafts and keys across failures, `reset`), `FeedWritingTest`, `ThreadRulesTest`, `ThreadSeenTest`,
  and `client/screens/` plays the screens with `PhoneTester`, fed from `FeedSession`: the art direction's states (empty,
  unavailable, pinned group, anonymous lines, compose for a player and for an announcer, the pin-limit dialog, the
  delete dialog, the guest look, landing from a notification).
- In `phone/`, the tests of 6.5 for `sendToEveryone` with `except` (`NotificationServiceTest`). In `tools/sprites`, the
  test that Feed's icon is what the generator draws.
- A dedicated server start (`./gradlew :apps:feed:runServer`), which must not mention a client class: `TESTING.md` says
  how, and what the log must show.

### 12.4 The screenshot harness

`./gradlew :apps:feed:runCapture -Pcapture=root|guest|thread|compose -PcaptureDir=<absolute directory>`, with
`-PcaptureLang`, `-PcaptureScale`, `-PcaptureSize` and `-PcaptureUser` as options. It runs in a game folder of its own,
`apps/feed/build/capture-run`, written by `prepareCapture` from `example-config/`, and plays the scenarios of
`src/capture/` against the memory backend with the sample seed and a fixed clock, one step per client tick, as the
art direction's flows F-1 to F-25 describe. `TESTING.md` lists what each shows, and what the harness refuses to shoot
(a window too small for the GUI scale, a minimised window).

### 12.5 Releasing

A tag `v<version>` makes CI build the phone and Feed as that version and publish both jars. Feed's range on the phone
is `[<version>, next minor)` (the plugin writes it), and its range on Warden is `lnbwarden_version_range`, fixed in
`apps/feed/gradle.properties`.
