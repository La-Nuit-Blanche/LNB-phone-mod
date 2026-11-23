package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.FeedMod
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.FeedRequest
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Created
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.PinState
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Feed's logical server, apart from the game's events ([FeedServer] wires those): the watchers, the clients'
 * requests with the server's own checks, the stream relay and the notifier, over a [FeedBackend] and a [FeedHost].
 * Server thread only, except what the backend calls back, which hops there.
 *
 * Every request acts for its sender only: the `player` and `viewer` of each backend call is the UUID the network
 * layer took from the payload's context, never one a client named. Answers go to the sender only.
 */
class FeedService(val backend: FeedBackend, val host: FeedHost, sink: FeedNotificationSink) {
    /** Online players who opened Feed since they joined: they get live changes. */
    private val watchers = LinkedHashSet<UUID>()
    val rights = RightsCache(backend, host) { it in watchers }
    val notifier = FeedNotifier(backend, host, sink)
    val relay = Relay(backend, host, watchers, rights, notifier)
    private val limits = PlayerLimits(host::millis)

    // What a client can make the server say goes through here, so that a modified one can't flood the log.
    private val log = ThrottledLog(FeedMod.LOGGER, host::millis)
    private var lastDrain = 0L
    private var lastPrune = 0L

    fun isWatcher(player: UUID) = player in watchers

    /** Starts the stream and drains the queue (`feed-mod.md` 9.1: when the server has started). */
    fun start() {
        lastDrain = host.millis()
        backend.start(relay)
        notifier.drain()
    }

    fun stop() = backend.stop()

    fun tick() {
        relay.flush()
        rights.tick()
        val now = host.millis()
        if (now - lastDrain >= DRAIN_EVERY_MS) {
            lastDrain = now
            notifier.drain()
        }
        if (now - lastPrune >= PRUNE_EVERY_MS) {
            lastPrune = now
            limits.prune()
        }
    }

    /**
     * Nothing waits for it: the site being away never delays joining. The read counts against the player's limits,
     * so that leaving and joining again in a loop can't make the server ask the site without end.
     */
    fun onJoin(player: UUID) {
        refreshCharged(player)
    }

    /** The player's limits stay: a client that comes back finds the allowance it left. */
    fun onLeave(player: UUID) {
        watchers -= player
        rights.forget(player)
    }

    /** Reads [player]'s state again for the player's sake, within their limits; skipped past them. */
    private fun refreshCharged(player: UUID) {
        if (!limits.tryStart(player, PlayerLimits.Kind.READ)) return
        rights.refresh(player).whenComplete { _, _ -> limits.finish(player) }
    }

    // ---- Requests ----

    /** `open`: the sender becomes a watcher and gets their state again (which also carries the tab dots). */
    fun handleOpen(player: UUID) {
        watchers += player
        // Past a limit (the player's own, or the whole server's when many open Feed at once, from the same
        // notification say), the site isn't asked: the player gets "unavailable" with the last state known, and the
        // client's retry, about every 15 seconds, takes over.
        if (!limits.tryStart(player, PlayerLimits.Kind.READ)) return host.send(player, StatePayload(Availability.UNAVAILABLE, rights.state(player)))
        rights.refresh(player).thenAccept { result ->
            limits.finish(player)
            // On success the state went to the player as a watcher; otherwise they keep the last one, with the reason.
            if (result !is BackendResult.Ok) host.send(player, StatePayload(availabilityOf(result), rights.state(player)))
        }
    }

    fun handleRequest(player: UUID, request: FeedRequest) {
        val kind = if (request is LoadFeedPayload || request is LoadThreadPayload) PlayerLimits.Kind.READ else PlayerLimits.Kind.WRITE
        if (!limits.tryStart(player, kind)) return host.send(player, failed(request, Outcome.RATE_LIMITED, retryAfter = 1))
        val call = call(player, request)
        if (call == null) {
            limits.finish(player)
            log.warn("malformed", "Feed refused a malformed {} request", request.type().id().path)
            return host.send(player, failed(request, Outcome.INVALID))
        }
        call.whenComplete { result, error ->
            val answer = result ?: BackendResult.Unavailable("${request.type().id().path}: ${error?.javaClass?.simpleName}")
            host.execute {
                limits.finish(player)
                host.send(player, answerTo(player, request, answer))
            }
        }
    }

    fun handleSeenFeed(player: UUID, feed: FeedKind, upTo: Instant) = seen(player) { backend.markFeedSeen(player, feed, upTo) }

    fun handleSeenThread(player: UUID, threadId: UUID) = seen(player) { backend.markThreadSeen(player, threadId) }

    /** Marking seen is for linked players only; extra calls past the limit are dropped. No answer. */
    private fun seen(player: UUID, call: () -> CompletableFuture<BackendResult<Unit>>) {
        if (rights.state(player)?.linked != true) return
        if (!limits.tryStart(player, PlayerLimits.Kind.SEEN)) return
        call().whenComplete { _, _ -> host.execute { limits.finish(player) } }
    }

    /** The backend call for [request], or null when its fields fail the server's checks (`feed-app.md` 4.3). */
    private fun call(player: UUID, request: FeedRequest): CompletableFuture<BackendResult<Any>>? = when (request) {
        is LoadFeedPayload -> if (!cursorOk(request.before)) null else backend.page(player, request.feed, request.before, Feed.FEED_PAGE).erased()
        is LoadThreadPayload -> if (!cursorOk(request.after)) null else backend.thread(player, request.threadId, request.after, Feed.THREAD_PAGE).erased()
        is CreatePostPayload -> sendable(request.text)?.let { backend.createPost(request.key, player, request.feed, it, request.anonymous).erased() }
        is CreateReplyPayload -> sendable(request.text)?.let {
            backend.createReply(request.key, player, request.threadId, request.replyTo, it, request.anonymous).erased()
        }
        is LikePayload -> backend.like(player, request.postId, request.liked).erased()
        is PinPayload -> backend.pin(player, request.postId, request.pinned, request.replace).erased()
        is DeletePayload -> backend.delete(player, request.postId).erased()
    }

    private fun <T : Any> CompletableFuture<BackendResult<T>>.erased(): CompletableFuture<BackendResult<Any>> = thenApply { it }

    /** The text as sent to the site (trimmed), or null when the phone's own rule refuses it. */
    private fun sendable(text: String): String? = text.trim().takeIf { TextLimits.canSend(it, Feed.MAX_TEXT) }

    private fun cursorOk(cursor: String?) = cursor == null || cursor.length <= Feed.MAX_CURSOR

    private fun answerTo(player: UUID, request: FeedRequest, result: BackendResult<Any>): CustomPacketPayload {
        if (result !is BackendResult.Ok) {
            val outcome = outcomeOf(player, request, result)
            val refused = result as? BackendResult.Refused
            val retryAfter = if (outcome == Outcome.RATE_LIMITED) refused?.retryAfterSeconds ?: 1 else null
            return failed(request, outcome, oldestPin = refused?.oldestPin, retryAfter = retryAfter)
        }
        val id = request.requestId
        return when (val body = result.body) {
            is FeedPage ->
                PagePayload(id, Outcome.OK, body.feed, body.pinned.take(Feed.MAX_PINS), body.posts.take(Feed.MAX_PAGE), body.next)
            is ThreadPage ->
                ThreadPayload(id, Outcome.OK, body.post, body.replies.take(Feed.MAX_PAGE), body.next)
            is Created -> ResultPayload(id, Outcome.OK, post = body.post)
            is LikeState -> ResultPayload(id, Outcome.OK, likes = body.likes, liked = body.liked)
            is PinState ->
                ResultPayload(id, Outcome.OK, pinned = body.pinned.take(Feed.MAX_PINS), unpinned = body.unpinned)
            else -> ResultPayload(id, Outcome.OK)
        }
    }

    /**
     * The outcome of a failed call (`feed-app.md` 5.3), with what follows: the player's state read again when it says
     * their rights or the writing flag changed. Logs route and problem only: never a player or a text.
     */
    private fun outcomeOf(player: UUID, request: FeedRequest, result: BackendResult<*>): Outcome {
        val route = request.type().id().path
        return when (result) {
            is BackendResult.Ok -> Outcome.OK
            is BackendResult.Unavailable -> Outcome.UNAVAILABLE
            is BackendResult.Disabled -> {
                if (result.flag == "feed.writing") refreshCharged(player)
                Outcome.DISABLED
            }
            is BackendResult.Refused -> when (result.problem) {
                "rateLimited" -> Outcome.RATE_LIMITED
                "notLinked" -> Outcome.NOT_LINKED.also { refreshCharged(player) }
                "banned" -> Outcome.BANNED.also { refreshCharged(player) }
                "notAllowed" -> Outcome.NOT_ALLOWED.also { refreshCharged(player) }
                "notFound" -> Outcome.NOT_FOUND
                "pinLimit" -> Outcome.PIN_LIMIT
                "notPinnable" -> Outcome.NOT_PINNABLE
                "emptyText", "tooLong" -> {
                    log.warn("text", "The site refused a Feed {} as {}, which the server's own check let through", route, result.problem)
                    Outcome.INVALID
                }
                "idempotencyMismatch" -> {
                    log.error("idempotency", "Feed {}: an idempotency key was reused for another request (a bug, or a forged client)", route)
                    Outcome.INVALID
                }
                else -> {
                    if (result.status == 429) return Outcome.RATE_LIMITED
                    log.warn("unexpected", "Unexpected answer from the site to a Feed {}: HTTP {} {}", route, result.status, result.problem ?: "(no problem)")
                    Outcome.UNAVAILABLE
                }
            }
        }
    }

    private fun failed(request: FeedRequest, outcome: Outcome, oldestPin: UUID? = null, retryAfter: Int? = null): CustomPacketPayload =
        when (request) {
            is LoadFeedPayload -> PagePayload.failed(request.requestId, outcome, request.feed)
            is LoadThreadPayload -> ThreadPayload.failed(request.requestId, outcome)
            else -> ResultPayload(request.requestId, outcome, oldestPin = oldestPin, retryAfter = retryAfter)
        }

    private fun availabilityOf(result: BackendResult<*>) =
        if (result is BackendResult.Disabled) Availability.DISABLED else Availability.UNAVAILABLE

    private companion object {
        const val DRAIN_EVERY_MS = 5 * 60 * 1000L
        const val PRUNE_EVERY_MS = 60 * 1000L
    }
}
