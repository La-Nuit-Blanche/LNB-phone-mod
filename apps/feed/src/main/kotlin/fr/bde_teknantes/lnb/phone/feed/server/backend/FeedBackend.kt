package fr.bde_teknantes.lnb.phone.feed.server.backend

import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Everything the Minecraft server asks of the site (`docs/feed-mod.md` 4), behind one interface so that the memory
 * backend can stand in for it. `player` is always the acting player and `viewer` the player who will see the answer:
 * the sender of the payload, never a UUID a client named.
 *
 * Every call is asynchronous and never throws: failures are [BackendResult] values, and the future may complete on
 * any thread. Callers hop back to the server thread before touching players or sending payloads.
 */
interface FeedBackend {
    fun player(player: UUID): CompletableFuture<BackendResult<PlayerState>>
    fun page(viewer: UUID, feed: FeedKind, before: String?, limit: Int): CompletableFuture<BackendResult<FeedPage>>
    fun thread(viewer: UUID, threadId: UUID, after: String?, limit: Int): CompletableFuture<BackendResult<ThreadPage>>

    /** Each viewer's own views of [ids]: the map holds only the viewers asked for, each with only their own views. */
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

    /**
     * Starts delivering the site's changes to [listener], until [stop]. The listener may be called on any thread.
     * (The design passes the server too; neither backend needs it, and leaving it out lets tests start one.)
     */
    fun start(listener: FeedEventListener)
    fun stop()
}

sealed interface BackendResult<out T> {
    data class Ok<T>(val body: T) : BackendResult<T>

    /** The site refused: a status and its `data.problem`, with what some problems carry. */
    data class Refused(val status: Int, val problem: String?, val oldestPin: UUID? = null, val retryAfterSeconds: Int? = null) : BackendResult<Nothing>

    /** 503 `disabled`, with the flag that is off (`feed`, `feed.serverApi` or `feed.writing`). */
    data class Disabled(val flag: String?) : BackendResult<Nothing>

    /** No answer, a 5xx, a 401, or a body that doesn't parse. [reason] is for the log: it holds no player content. */
    data class Unavailable(val reason: String) : BackendResult<Nothing>
}

/** The site's changes, from its `feed` stream (`feed-mod.md` 8). Called on any thread. */
interface FeedEventListener {
    /** After every successful connection: catch up (`feed-mod.md` 8.3). */
    fun onConnected()

    /**
     * The stream dropped, or answers 503 `disabled` ([disabled]); once per loss, not for each failed retry, except a
     * refusal as switched off, told at each try (once a minute).
     */
    fun onDisconnected(disabled: Boolean)
    fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant)
    fun onDeleted(items: List<RemovedItem>)
    fun onCounts(items: List<CountUpdate>)

    /** The whole pinned group after a change, most recent pin first. */
    fun onPins(pinned: List<Pin>)
    fun onRights(players: List<UUID>)
    fun onNotification()
}

/** A page of a feed for one viewer (`feed-mod.md` 4.2). [pinned] is filled on Announcements' first page only. */
data class FeedPage(val feed: FeedKind, val pinned: List<PostView>, val posts: List<PostView>, val next: String?)

/** A thread for one viewer: the main post and a page of replies, oldest first (`feed-mod.md` 4.3). */
data class ThreadPage(val post: PostView, val replies: List<PostView>, val next: String?)

/** A created post or reply, for its author; [replayed] when the idempotency key had already made it. */
data class Created(val replayed: Boolean, val post: PostView)

data class LikeState(val id: UUID, val likes: Int, val liked: Boolean)

/** The pinned group after a pin or unpin, most recent first, and the post unpinned to make room, if any. */
data class PinState(val pinned: List<Pin>, val unpinned: UUID?)

/** Who ran `/feed announcer`: a player (null for the server console). */
data class Issuer(val uuid: UUID, val username: String)

data class AnnouncerState(val uuid: UUID, val username: String, val announcer: Boolean, val viaRole: Boolean)

/** A person with the `feed-announcer` role (`feed-mod.md` 4.15). */
data class AnnouncerEntry(val realName: String?, val accounts: List<Account>) {
    data class Account(val uuid: UUID, val username: String)
}

/** Waiting notifications, oldest first; call again after acknowledging while [more] (`feed-mod.md` 4.12). */
data class NotificationBatch(val items: List<QueuedNotification>, val more: Boolean)

/**
 * A notification waiting on the site. For a `reply`, [players] are the recipient's linked accounts; for an
 * `announcement`, [players] is null (everyone) and [except] the author's accounts. Both can name the author of an
 * anonymous post: they go to the phone and nowhere else, never into a log or a payload (`feed-mod.md` 12).
 */
data class QueuedNotification(
    val id: UUID,
    val kind: Kind,
    val createdAt: Instant,
    val players: List<UUID>?,
    val except: List<UUID>,
    val post: NotificationPost,
) {
    enum class Kind(val wire: String) {
        ANNOUNCEMENT("announcement"),
        REPLY("reply");

        companion object {
            fun fromWire(text: String?): Kind? = entries.firstOrNull { it.wire == text }
        }
    }

    // No players, no text: what is safe to log.
    override fun toString() = "QueuedNotification($id, $kind)"
}

/** The public part of a post, with no viewer fields: [author] is null when anonymous, for everyone. */
data class NotificationPost(
    val id: UUID,
    val feed: FeedKind,
    val threadId: UUID?,
    val text: String,
    val anonymous: Boolean,
    val author: Author?,
) {
    override fun toString() = "NotificationPost($id)"
}
