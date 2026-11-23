package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.api.client.ui.SnackbarHandle
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.time.Instant
import java.util.EnumMap
import java.util.UUID

/** What the client knows of Feed's reachability (`docs/feed-app.md` 8). */
enum class FeedAvailability {
    /** Nothing asked yet. */
    UNKNOWN,

    /** `open` sent, no answer yet. */
    LOADING,
    OK,
    UNAVAILABLE,
    DISABLED,
}

/** Something the screen shown reports once, as a snackbar (art direction 9.4, 9.9, 9.11, 9.12). */
sealed interface FeedNotice {
    /** `Connexion perdue`, an error snackbar with Retry ([FeedSession.retryConnection]). */
    data object ConnectionLost : FeedNotice

    /** Feed is back: a `Connexion perdue` still showing goes, with no message. */
    data object ConnectionBack : FeedNotice

    /** `Action impossible`. */
    data object ActionFailed : FeedNotice

    /** `Doucement ! Attends {n} s`. */
    data class TooFast(val seconds: Int) : FeedNotice

    /** An action refused for who the player is: the block's text. */
    data class Refused(val block: WriteBlock) : FeedNotice

    /** `Post épinglé` or `Post désépinglé`. */
    data class PinDone(val pinned: Boolean) : FeedNotice

    /** The server found 3 pins already (two announcers at once): the "Replace a pin?" dialog for [postId]. */
    data class PinLimit(val postId: UUID) : FeedNotice

    /** `Post supprimé` or `Réponse supprimée`. */
    data class Deleted(val postId: UUID, val reply: Boolean) : FeedNotice
}

/** What a click on a heart did. */
enum class LikeAttempt {
    /** Shown at once and sent. */
    SENT,

    /** The player may not write ([FeedSession.writeBlock]): nothing changes, the screen says why. */
    BLOCKED,

    /** Nothing to like (the post went). */
    IGNORED,
}

/** What a click on a pin button did. */
enum class PinAttempt { SENT, NEEDS_REPLACE, BLOCKED, IGNORED }

/**
 * Feed's state on the client (`docs/feed-app.md` 8): availability, the player's state and rights, one copy of every
 * post and reply held, the two feeds with their pages, pinned group, waiting posts and scroll, the threads, the drafts,
 * the unread dots, the likes and pins shown before the server answered, and the requests waiting for an answer.
 *
 * Screens are rebuilt at every visit and read everything from here; they catch up with [revision] in their tick. The
 * server's payloads come in through the `on…` functions, requests go out through [send]. Client thread only.
 *
 * Plain logic: no game class is touched, so a test fills a session by hand, with [send] recording what goes out and
 * [clock] fixed.
 */
class FeedSession(
    private val send: (CustomPacketPayload) -> Unit,
    /** Real time, for relative times and the pin shown before the server answers. */
    val clock: () -> Instant = Instant::now,
) {
    /** Goes up at every change a screen may show. */
    var revision = 0
        private set

    var availability = FeedAvailability.UNKNOWN
        private set

    /** The player's last state (rights, `me`, the dots), kept while the site is away; null until the first. */
    var state: PlayerState? = null
        private set

    private val posts = HashMap<UUID, PostView>()
    private val feeds = EnumMap<FeedKind, FeedList>(FeedKind::class.java).apply {
        for (kind in FeedKind.entries) put(kind, FeedList(kind))
    }
    private val unread = EnumMap<FeedKind, Boolean>(FeedKind::class.java)

    // Access order: the most recently used last, so the eldest goes first.
    private val threads = LinkedHashMap<UUID, ThreadState>(16, 0.75f, true)
    private val replyDrafts = LinkedHashMap<UUID, ReplyDraft>(16, 0.75f, true)

    /** The Compose draft. */
    val compose = ComposeDraft()

    private val requests = HashMap<Int, PendingRequest>()

    // When each request stops being waited for: a lost answer must not leave skeletons or a spinner for ever.
    private val deadlines = HashMap<Int, Int>()
    private val draftsAwaitingState = HashMap<Any, Int>()

    // Threads reloaded because a reply was answered NOT_FOUND: if they are gone, their draft hears it.
    private val threadsToCheck = HashSet<UUID>()
    private val ids = RequestIds()
    private val pendingLikes = HashMap<UUID, PendingLike>()
    private val pendingPins = HashMap<UUID, List<Pin>>()

    // Pins waiting for their answer since which the server sent its group: on failure, that group stands.
    private val pinsMovedSince = HashSet<UUID>()
    private val deleting = HashSet<UUID>()
    private val highlights = HashSet<UUID>()
    private val reveals = EnumMap<FeedKind, UUID>(FeedKind::class.java)
    private val threadReveals = HashMap<UUID, UUID>()

    // Posts and replies known to be gone, so that a late `live` or `result` doesn't bring them back.
    private val gone = BoundedSet(GONE_REMEMBERED)

    // Live replies whose marker was raised, so that a reply delivered twice raises it once.
    private val raisedFor = BoundedSet(GONE_REMEMBERED)

    /**
     * The `Connexion perdue` snackbar showing, kept here rather than by the screen that showed it, so that whichever
     * Feed screen hears that Feed is back ([FeedNotice.ConnectionBack]) takes that one away, and no other.
     */
    var connectionSnackbar: SnackbarHandle? = null

    private var landing: UUID? = null
    // What the screen shown should say, in order, with the tick each came: two in one tick are both told, and one too
    // old to mean anything (it came while no screen could tell it) is dropped.
    private val notices = ArrayDeque<Pair<FeedNotice, Int>>()

    /** Queues a notice (null clears them all). */
    private var notice: FeedNotice?
        get() = notices.lastOrNull()?.first
        set(value) {
            if (value == null) {
                notices.clear()
                return
            }
            notices.addLast(value to ticks)
            while (notices.size > MAX_NOTICES) notices.removeFirst()
        }

    /** The feed on screen, set by the feed screen while it shows one; null otherwise. */
    var viewing: FeedKind? = null
        private set

    /** The thread on screen, set by the thread screen; null otherwise. */
    var viewingThread: UUID? = null
        private set
    private var threadSeenSent: UUID? = null

    private var ticks = 0

    /** How many `reset`s came: a page asked before the last one answers with what the site held before it. */
    private var resets = 0
    private val openApps = HashSet<Any>()
    private var nextAutoRetry = 0
    private var manualRetry = false
    private var retryDeadline = 0
    // Seen marks: the newest time queued per feed, what waits to go, and when each feed or thread last went. They
    // share one allowance, as the server counts them: a burst of SEEN_BURST, then one per SEEN_EVERY_TICKS.
    private val lastSeenSent = EnumMap<FeedKind, Instant>(FeedKind::class.java)
    private val pendingSeen = EnumMap<FeedKind, Instant>(FeedKind::class.java)
    private val seenFeedAt = EnumMap<FeedKind, Int>(FeedKind::class.java)
    private val pendingThreadSeen = LinkedHashSet<UUID>()
    private val seenThreadAt = HashMap<UUID, Int>()
    private var seenAllowance = SEEN_BURST
    private var seenRefilled = 0

    // ---- Reading ----

    /** The view of [id] held, or null. */
    fun post(id: UUID): PostView? = posts[id]

    fun feed(kind: FeedKind): FeedList = feeds.getValue(kind)

    /** The thread of [id], made if new, and kept as the most recently used of the [MAX_THREADS]. */
    fun thread(id: UUID): ThreadState {
        threads[id]?.let { return it }
        val thread = ThreadState(id)
        threads[id] = thread
        trimThreads()
        return thread
    }

    /** The thread of [id] if held. */
    fun threadOrNull(id: UUID): ThreadState? = threads[id]

    /** The tab dot of [kind] (art direction 9.6). The tabs hide it on the active tab. */
    fun isUnread(kind: FeedKind): Boolean = unread[kind] == true

    /** Why the player may not write now, or null when they may (or their state isn't known yet). */
    fun writeBlock(): WriteBlock? {
        val s = state ?: return null
        return when {
            !s.linked -> WriteBlock.GUEST
            s.banned -> WriteBlock.BANNED
            !s.writing -> WriteBlock.WRITING_OFF
            else -> null
        }
    }

    /** Whether the compose button shows on [kind]: announcers on Announcements, players with an account on Global. */
    fun canCompose(kind: FeedKind): Boolean {
        val s = state ?: return false
        if (writeBlock() != null) return false
        return if (kind == FeedKind.ANNOUNCEMENTS) s.rights.announce else s.rights.write
    }

    /** Whether something is on screen to keep readable when the connection drops. */
    val hasLoadedContent: Boolean get() = feeds.values.any { it.loaded } || threads.values.any { it.loaded }

    /** Whether [id] is being deleted (the dialog's confirm button loads). */
    fun isDeleting(id: UUID) = id in deleting

    /** Whether the pin or unpin of [id] waits for the server (the "Replace a pin?" dialog's confirm button loads). */
    fun isPinning(id: UUID) = id in pendingPins

    /** The reply draft of [threadId], made if new, kept as the most recently used of the [MAX_REPLY_DRAFTS]. */
    fun replyDraft(threadId: UUID): ReplyDraft {
        replyDrafts[threadId]?.let { return it }
        val draft = ReplyDraft(threadId)
        replyDrafts[threadId] = draft
        while (replyDrafts.size > MAX_REPLY_DRAFTS) replyDrafts.remove(replyDrafts.keys.first())
        return draft
    }

    /** What the screen shown should say next, once each, oldest first; null when nothing (recent) is left. */
    fun takeNotice(): FeedNotice? {
        while (notices.isNotEmpty()) {
            val (notice, at) = notices.removeFirst()
            if (ticks - at <= NOTICE_MAX_AGE_TICKS) return notice
        }
        return null
    }

    /** Whether [id] should get the post highlight now (once). */
    fun takeHighlight(id: UUID): Boolean = highlights.remove(id)

    /** Whether [id] is waiting for its highlight. */
    fun wantsHighlight(id: UUID): Boolean = id in highlights

    /** A post of [kind] to scroll into view (an own post just published), once. */
    fun takeReveal(kind: FeedKind): UUID? = reveals.remove(kind)

    /** A reply of the thread of [threadId] to scroll into view (an own reply), once. */
    fun takeThreadReveal(threadId: UUID): UUID? = threadReveals.remove(threadId)

    // ---- What the screens tell ----

    /**
     * Feed's root screen [owner] is on the phone (from its creation until it closes): the app is open, and
     * [FeedClient][fr.bde_teknantes.lnb.phone.feed.client.FeedClient] ticks the session ([tick]) whatever screen shows.
     */
    fun appOpened(owner: Any) {
        openApps += owner
    }

    /** [owner] left the phone for good. */
    fun appClosed(owner: Any) {
        openApps -= owner
        // Nothing is left to tell them on: they would show stale at the next opening.
        if (openApps.isEmpty()) notices.clear()
    }

    /** Whether Feed is open on the phone (shown or not): its timers run. */
    val isAppOpen: Boolean get() = openApps.isNotEmpty()

    /** The feed screen shows [kind] (or nothing, null): live posts elsewhere light that tab's dot. */
    fun show(kind: FeedKind?) {
        if (viewing == kind) return
        val left = viewing
        viewing = kind
        // A list the player stopped looking at is trimmed to its bound now, not under their eyes.
        if (left != null) trimFeed(feed(left))
    }

    /** The thread screen shows [threadId] (or nothing, null). Its first page shown, it is marked seen. */
    fun showThread(threadId: UUID?) {
        if (viewingThread != threadId) threadSeenSent = null
        viewingThread = threadId
        if (threadId != null && threads[threadId]?.loaded == true) markThreadSeen(threadId)
    }

    /**
     * The player is viewing [kind]: its dot clears, and the site hears the newest post shown (pinned included) when it
     * changed, once every 2 seconds at most (`seen_feed`).
     */
    fun markViewed(kind: FeedKind) {
        if (unread[kind] == true) {
            unread[kind] = false
            changed()
        }
        val list = feed(kind)
        if (!list.loaded || state?.linked != true) return
        val newest = (list.pinnedIds.asSequence() + list.postIds.asSequence()).mapNotNull { posts[it]?.createdAt }.maxOrNull() ?: return
        val last = lastSeenSent[kind]
        if (last != null && !newest.isAfter(last)) return
        // Queued, and sent as soon as the allowance lets it, even once the player left the tab.
        lastSeenSent[kind] = newest
        pendingSeen[kind] = newest
        flushSeen()
    }

    /** Sends the seen marks waiting, each feed and thread at most once every 2 seconds, within the shared allowance. */
    private fun flushSeen() {
        if (pendingSeen.isEmpty() && pendingThreadSeen.isEmpty()) return
        if (state?.linked != true) {
            pendingSeen.clear()
            pendingThreadSeen.clear()
            return
        }
        if (seenAllowance >= SEEN_BURST) {
            seenRefilled = ticks
        } else {
            val gained = (ticks - seenRefilled) / SEEN_EVERY_TICKS
            if (gained > 0) {
                seenAllowance = minOf(SEEN_BURST, seenAllowance + gained)
                seenRefilled += gained * SEEN_EVERY_TICKS
            }
        }
        for (kind in pendingSeen.keys.toList()) {
            if (seenAllowance == 0) return
            if (ticks - (seenFeedAt[kind] ?: Int.MIN_VALUE / 2) < SEEN_EVERY_TICKS) continue
            val upTo = pendingSeen.remove(kind) ?: continue
            seenFeedAt[kind] = ticks
            spendSeen()
            send(SeenFeedPayload(kind, upTo))
        }
        for (threadId in pendingThreadSeen.toList()) {
            if (seenAllowance == 0) return
            if (ticks - (seenThreadAt[threadId] ?: Int.MIN_VALUE / 2) < SEEN_EVERY_TICKS) continue
            pendingThreadSeen.remove(threadId)
            seenThreadAt[threadId] = ticks
            spendSeen()
            send(SeenThreadPayload(threadId))
        }
    }

    private fun spendSeen() {
        if (seenAllowance == SEEN_BURST) seenRefilled = ticks
        seenAllowance--
    }

    /**
     * The notification of an announcement was opened (art direction 9.10): Announcements lands on [postId], scrolled
     * so that it is fully visible, highlighted. Taken by the feed screen once the list is loaded ([takeLanding]).
     */
    fun land(postId: UUID) {
        landing = postId
        changed()
    }

    /**
     * The post to land on in [kind], once its list is loaded: highlighted, and brought out of the waiting posts when it
     * waits there. Null when there is none, or not yet.
     */
    fun takeLanding(kind: FeedKind): UUID? {
        val id = landing ?: return null
        val list = feed(kind)
        if (kind != FeedKind.ANNOUNCEMENTS || !list.loaded) return null
        // A list about to reload may bring the post: wait for it, and land then (or drop it if it isn't there).
        if (!list.holds(id) && (list.stale || list.loadingFirst)) return null
        landing = null
        if (id in feed(kind).waitingIds) releaseWaiting(kind)
        if (feed(kind).holds(id)) highlights += id
        return id
    }

    /**
     * Inserts the waiting posts of [kind] at their place, highlighted (a click on the pill, or the place scrolled into
     * view). Returns them, newest first.
     */
    fun releaseWaiting(kind: FeedKind): List<UUID> {
        val list = feed(kind)
        if (list.waitingIds.isEmpty()) return emptyList()
        val released = list.waitingIds.toList()
        list.waitingIds.clear()
        for (id in released.asReversed()) {
            insertByDate(list, id)
            highlights += id
        }
        changed()
        return released
    }

    // ---- Requests ----

    /** Feed was opened (the root screen shown): the player becomes a watcher and gets their state. */
    fun open() {
        if (availability == FeedAvailability.UNKNOWN) availability = FeedAvailability.LOADING
        send(OpenPayload)
        changed()
    }

    /** Loads the first page of [kind] when it is missing or stale (`docs/feed-app.md` 8), not after a failure. */
    fun loadIfNeeded(kind: FeedKind) {
        val list = feed(kind)
        if (list.loadingFirst || list.retrying) return
        val due = if (list.loaded) list.stale else list.firstError == null
        if (due && ticks >= list.reloadNotBefore) loadFirst(list)
    }

    /** Loads the next page of [kind], as its end comes into view. */
    fun loadMore(kind: FeedKind) {
        val list = feed(kind)
        if (!list.loaded || !list.hasMore || list.loadingMore || list.moreFailed || list.loadingFirst) return
        if (ticks < list.moreNotBefore) return
        list.loadingMore = true
        request(PendingRequest.LoadFeed(kind, more = true, before = list.next)) { LoadFeedPayload(it, kind, list.next) }
        changed()
    }

    /** The retry row of [kind]: the spinner row again, and the page asked again. */
    fun retryMore(kind: FeedKind) {
        feed(kind).moreFailed = false
        loadMore(kind)
    }

    /**
     * The error block's Retry (art direction 9.12, F-3): asks the server whether Feed is back (`open`); on `OK` the list
     * loads (skeletons, then the posts), otherwise the block returns.
     */
    fun retryFirst(kind: FeedKind) {
        val list = feed(kind)
        if (list.retrying || list.loadingFirst) return
        list.retrying = true
        retryDeadline = ticks + RETRY_TIMEOUT_TICKS
        send(OpenPayload)
        changed()
    }

    /** The `Connexion perdue` snackbar's Retry: `open` at once; if it fails again, the snackbar shows again. */
    fun retryConnection() {
        manualRetry = true
        nextAutoRetry = ticks + AUTO_RETRY_TICKS
        send(OpenPayload)
    }

    /** Loads [threadId] when it is missing or stale. */
    fun loadThreadIfNeeded(threadId: UUID) {
        val thread = thread(threadId)
        if (thread.loadingFirst || thread.deleted) return
        val due = if (thread.loaded) thread.stale else thread.firstError == null
        if (due && ticks >= thread.reloadNotBefore) loadThreadFirst(thread)
    }

    /** The thread's error block Retry. */
    fun retryThread(threadId: UUID) {
        val thread = thread(threadId)
        thread.firstError = null
        loadThreadIfNeeded(threadId)
    }

    fun loadMoreReplies(threadId: UUID) {
        val thread = thread(threadId)
        if (!thread.loaded || !thread.hasMore || thread.loadingMore || thread.moreFailed || thread.loadingFirst) return
        if (ticks < thread.moreNotBefore) return
        thread.loadingMore = true
        request(PendingRequest.LoadThread(threadId, more = true, after = thread.next)) { LoadThreadPayload(it, threadId, thread.next) }
        changed()
    }

    fun retryReplies(threadId: UUID) {
        thread(threadId).moreFailed = false
        loadMoreReplies(threadId)
    }

    /**
     * A click on the heart of [postId] (art direction 9.3, F-6): shown at once and sent; undone if the answer isn't `OK`.
     * Blocked for guests, banned players and while writing is off (9.11).
     */
    fun toggleLike(postId: UUID): LikeAttempt {
        if (writeBlock() != null) return LikeAttempt.BLOCKED
        val post = posts[postId] ?: return LikeAttempt.IGNORED
        val liked = !post.liked
        val pending = pendingLikes.getOrPut(postId) { PendingLike(post.liked, post.likes) }
        posts[postId] = post.copy(liked = liked, likes = (post.likes + if (liked) 1 else -1).coerceAtLeast(0))
        pending.outstanding += request(PendingRequest.Like(postId)) { LikePayload(it, postId, liked) }
        changed()
        return LikeAttempt.SENT
    }

    /** Publishes the Compose draft (art direction 9.8), with its key kept across a Retry. */
    fun sendPost(): Boolean {
        val draft = compose
        val text = draft.text.text.trim()
        if (draft.sending || writeBlock() != null || !sendable(draft.text.text)) return false
        val key = draft.key.forSending(draft.fingerprint())
        draft.sending = true
        draft.outcome = null
        val feed = draft.destination
        val anonymous = draft.anonymous
        request(PendingRequest.CreatePost(key)) { CreatePostPayload(it, key, feed, text, anonymous) }
        changed()
        return true
    }

    /** Sends the reply draft of [threadId] (art direction 9.7). */
    fun sendReply(threadId: UUID): Boolean {
        val draft = replyDraft(threadId)
        val text = draft.text.text.trim()
        if (draft.sending || writeBlock() != null || !sendable(draft.text.text)) return false
        val key = draft.key.forSending(draft.fingerprint())
        draft.sending = true
        draft.outcome = null
        val replyTo = draft.target?.replyTo
        val anonymous = draft.anonymous
        request(PendingRequest.CreateReply(threadId, key)) { CreateReplyPayload(it, key, threadId, replyTo, text, anonymous) }
        changed()
        return true
    }

    /**
     * A click on the pin button of [postId] (art direction 9.4): shown at once and sent. With 3 pinned and not
     * [replace], nothing is sent: the screen asks first, then calls again with [replace].
     */
    fun togglePin(postId: UUID, replace: Boolean): PinAttempt {
        if (writeBlock() != null) return PinAttempt.BLOCKED
        val post = posts[postId] ?: return PinAttempt.IGNORED
        // Only an announcement's main post, whatever the view says (a broken server).
        if (!post.canPin || post.isReply || post.feed != FeedKind.ANNOUNCEMENTS || postId in pendingPins) return PinAttempt.IGNORED
        val pinning = post.pinnedAt == null
        val group = pinGroup()
        if (pinning && !replace && group.size >= Feed.MAX_PINS) return PinAttempt.NEEDS_REPLACE
        pendingPins[postId] = group
        // Shown first in the group: after the group's latest pin, never by this client's clock, which may be behind the
        // site's (the site's answer then gives the real time).
        val at = group.maxOfOrNull { it.pinnedAt }?.plusMillis(1) ?: clock()
        val shown = if (pinning) (listOf(Pin(postId, at)) + group).take(Feed.MAX_PINS) else group.filter { it.id != postId }
        applyPinGroup(shown)
        if (pinning) highlights += postId
        request(PendingRequest.Pin(postId, pinning)) { PinPayload(it, postId, pinning, replace) }
        changed()
        return PinAttempt.SENT
    }

    /** Deletes [postId] once the player confirmed (art direction 9.9): the cell goes when the server agrees. */
    fun delete(postId: UUID): Boolean {
        if (postId in deleting || posts[postId]?.canDelete != true) return false
        deleting += postId
        request(PendingRequest.Delete(postId, reply = posts[postId]?.isReply == true)) { DeletePayload(it, postId) }
        changed()
        return true
    }

    /** Requests past their deadline count as answered `UNAVAILABLE`, as if the server had said so. */
    private fun expireRequests() {
        if (deadlines.isEmpty()) return
        for ((id, deadline) in deadlines.entries.toList()) {
            val pending = requests[id]
            if (pending == null) {
                deadlines.remove(id)
                continue
            }
            if (ticks < deadline) continue
            deadlines.remove(id)
            when (pending) {
                is PendingRequest.LoadFeed -> onPage(PagePayload.failed(id, Outcome.UNAVAILABLE, pending.feed))
                is PendingRequest.LoadThread -> onThread(ThreadPayload.failed(id, Outcome.UNAVAILABLE))
                else -> onResult(ResultPayload(id, Outcome.UNAVAILABLE))
            }
        }
    }

    /**
     * Called 20 times a second while Feed is open on the phone ([isAppOpen]), from one place (the client's tick), never
     * from a screen, so that no timer stops while another screen is on top: the automatic retry while the connection is
     * lost, the deadlines, the throttles.
     */
    fun tick() {
        ticks++
        flushSeen()
        settleAwaitingDrafts(timedOut = true)
        expireRequests()
        val lost = availability == FeedAvailability.UNAVAILABLE || availability == FeedAvailability.DISABLED
        if (lost && hasLoadedContent && (viewing != null || viewingThread != null) && ticks >= nextAutoRetry) {
            nextAutoRetry = ticks + AUTO_RETRY_TICKS
            send(OpenPayload)
        }
        if (ticks == retryDeadline) {
            // `open` went unanswered: the block comes back rather than spinning for ever.
            var any = false
            for (list in feeds.values) if (list.retrying) {
                list.retrying = false
                any = true
            }
            if (any) changed()
        }
    }

    // ---- The server's payloads ----

    fun onState(payload: StatePayload) {
        val before = availability
        availability = when (payload.available) {
            Availability.OK -> FeedAvailability.OK
            Availability.UNAVAILABLE -> FeedAvailability.UNAVAILABLE
            Availability.DISABLED -> FeedAvailability.DISABLED
        }
        payload.state?.let { s ->
            state = s
            settleAwaitingDrafts(timedOut = false)
            for (kind in FeedKind.entries) unread[kind] = s.unread[kind] && viewing != kind
            // A thread shown before the player's state was known (opened from a notification) is marked seen now.
            viewingThread?.let { if (threads[it]?.loaded == true) markThreadSeen(it) }
        }
        val wasLost = before == FeedAvailability.UNAVAILABLE || before == FeedAvailability.DISABLED
        if (availability == FeedAvailability.OK) {
            if (wasLost && hasLoadedContent) notice = FeedNotice.ConnectionBack
            manualRetry = false
            for (list in feeds.values) if (list.retrying) {
                list.retrying = false
                list.firstError = null
                loadFirst(list)
            }
            // A block left by an earlier failure goes too: the list loads again when shown, without a Retry.
            for (list in feeds.values) if (!list.loaded && !list.loadingFirst) list.firstError = null
            for (thread in threads.values) if (!thread.loaded && !thread.deleted && !thread.loadingFirst) thread.firstError = null
        } else {
            if (hasLoadedContent && (!wasLost || manualRetry)) {
                notice = FeedNotice.ConnectionLost
                nextAutoRetry = ticks + AUTO_RETRY_TICKS
            }
            manualRetry = false
            val outcome = if (availability == FeedAvailability.DISABLED) Outcome.DISABLED else Outcome.UNAVAILABLE
            for (list in feeds.values) if (list.retrying) {
                list.retrying = false
                if (!list.loaded) list.firstError = outcome
            }
        }
        changed()
    }

    fun onPage(payload: PagePayload) {
        val request = requests[payload.requestId] as? PendingRequest.LoadFeed ?: return
        requests.remove(payload.requestId)
        val list = feed(request.feed)
        if (!request.more) {
            list.loadingFirst = false
            if (payload.outcome == Outcome.OK) {
                if (list.loaded) mergeFirstPage(list, payload) else replaceFirstPage(list, payload)
                list.firstError = null
                // Asked before the last reset: shown, but loaded again and merged.
                list.stale = request.generation != resets
                if (list.stale) list.reloadNotBefore = 0
            } else if (payload.outcome == Outcome.RATE_LIMITED) {
                // Not a failure to show: the server's limits ask to wait a moment (the skeletons stay).
                list.reloadNotBefore = ticks + READ_RETRY_TICKS
            } else if (list.loaded) {
                // A stale list that couldn't reload stays readable, and tries again later.
                list.reloadNotBefore = ticks + AUTO_RETRY_TICKS
                if (payload.outcome == Outcome.UNAVAILABLE || payload.outcome == Outcome.DISABLED) connectionLost(payload.outcome)
            } else {
                list.firstError = payload.outcome
            }
        } else {
            list.loadingMore = false
            when {
                payload.outcome == Outcome.OK && list.loaded -> appendPage(list, payload, request.before)
                // The spinner row stays, and asks again in a moment.
                payload.outcome == Outcome.RATE_LIMITED -> list.moreNotBefore = ticks + READ_RETRY_TICKS
                else -> list.moreFailed = list.loaded
            }
        }
        collect()
        changed()
    }

    fun onThread(payload: ThreadPayload) {
        val request = requests[payload.requestId] as? PendingRequest.LoadThread ?: return
        requests.remove(payload.requestId)
        val thread = threads[request.threadId] ?: return
        val main = payload.post
        if (!request.more) {
            thread.loadingFirst = false
            when {
                payload.outcome == Outcome.OK && main != null && main.id == thread.id && !main.isReply -> {
                    threadsToCheck.remove(thread.id)
                    posts[main.id] = incoming(main)
                    mergeThreadPage(thread, payload.replies, payload.next)
                    thread.firstError = null
                    thread.stale = request.generation != resets
                    if (thread.stale) thread.reloadNotBefore = 0
                    if (viewingThread == thread.id) markThreadSeen(thread.id)
                }
                payload.outcome == Outcome.NOT_FOUND -> {
                    if (threadsToCheck.remove(thread.id)) replyDrafts[thread.id]?.outcome = DraftOutcome.ThreadGone
                    threadGone(thread.id)
                }
                payload.outcome == Outcome.RATE_LIMITED -> thread.reloadNotBefore = ticks + READ_RETRY_TICKS
                thread.loaded -> thread.reloadNotBefore = ticks + AUTO_RETRY_TICKS
                else -> thread.firstError = if (payload.outcome == Outcome.OK) Outcome.UNAVAILABLE else payload.outcome
            }
        } else {
            thread.loadingMore = false
            if (payload.outcome == Outcome.OK && thread.loaded) {
                for (reply in payload.replies) {
                    if (reply.threadId != thread.id || reply.id in gone) continue
                    if (reply.id in thread.replyIds) {
                        // Shown ahead of the pages (an own reply): the pages reached it.
                        thread.aheadIds.remove(reply.id)
                        continue
                    }
                    posts[reply.id] = incoming(reply)
                    thread.replyIds += reply.id
                }
                thread.next = payload.next
                thread.hasMore = payload.next != null
                // Replies shown ahead of the pages stay last, the pages filling the gap before them.
                if (!thread.hasMore) thread.aheadIds.clear()
                thread.replyIds.sortWith(compareBy { posts[it]?.createdAt ?: Instant.MAX })
            } else if (payload.outcome == Outcome.NOT_FOUND) {
                threadGone(thread.id)
            } else if (payload.outcome == Outcome.RATE_LIMITED) {
                thread.moreNotBefore = ticks + READ_RETRY_TICKS
            } else {
                thread.moreFailed = thread.loaded
            }
        }
        collect()
        changed()
    }

    fun onResult(payload: ResultPayload) {
        val request = requests.remove(payload.requestId) ?: return
        when (request) {
            is PendingRequest.Like -> likeAnswered(request, payload)
            is PendingRequest.CreatePost -> postAnswered(payload)
            is PendingRequest.CreateReply -> replyAnswered(request, payload)
            is PendingRequest.Pin -> pinAnswered(request, payload)
            is PendingRequest.Delete -> deleteAnswered(request, payload)
            // A read answered with `result`: only a broken server sends that.
            is PendingRequest.LoadFeed, is PendingRequest.LoadThread -> requests[payload.requestId] = request
        }
        changed()
    }

    /** New posts and replies, each the player's own view (`docs/feed-app.md` 8, "A live post"). */
    fun onLive(views: List<PostView>) {
        for (view in views) {
            if (view.id in gone) continue
            if (view.isReply) liveReply(view) else livePost(view)
        }
        changed()
    }

    fun onRemoved(items: List<RemovedItem>) {
        for (item in items) removePost(item.id)
        collect()
        changed()
    }

    fun onCounts(items: List<CountUpdate>) {
        for (item in items) {
            val post = posts[item.id] ?: continue
            val pending = pendingLikes[item.id]
            if (pending != null) {
                // The heart the player just clicked stays as shown; the answer to the like settles the count.
                pending.confirmedLikes = item.likes
                posts[item.id] = post.copy(replies = item.replies)
            } else {
                posts[item.id] = post.copy(likes = item.likes, replies = item.replies)
            }
        }
        changed()
    }

    fun onPins(group: List<Pin>) {
        pinsMovedSince += pendingPins.keys
        applyPinGroup(group)
        changed()
    }

    /** The server reconnected to the site: every list and thread is reloaded and merged when shown. */
    fun onReset() {
        resets++
        for (list in feeds.values) if (list.loaded) {
            list.stale = true
            list.reloadNotBefore = 0
        }
        for (thread in threads.values) if (thread.loaded) {
            thread.stale = true
            thread.reloadNotBefore = 0
        }
        val lost = availability == FeedAvailability.UNAVAILABLE || availability == FeedAvailability.DISABLED
        if (hasLoadedContent && lost) notice = FeedNotice.ConnectionBack
        changed()
    }

    /** The player left the server: nothing held belongs to the next one. */
    fun clear() {
        availability = FeedAvailability.UNKNOWN
        state = null
        posts.clear()
        feeds.values.forEach { it.reset() }
        unread.clear()
        threads.clear()
        replyDrafts.clear()
        compose.clear()
        compose.destination = FeedKind.GLOBAL
        compose.outcome = null
        requests.clear()
        deadlines.clear()
        pendingLikes.clear()
        pendingPins.clear()
        pinsMovedSince.clear()
        draftsAwaitingState.clear()
        threadsToCheck.clear()
        deleting.clear()
        highlights.clear()
        reveals.clear()
        threadReveals.clear()
        gone.clear()
        raisedFor.clear()
        landing = null
        notice = null
        connectionSnackbar = null
        viewing = null
        viewingThread = null
        threadSeenSent = null
        manualRetry = false
        lastSeenSent.clear()
        pendingSeen.clear()
        seenFeedAt.clear()
        pendingThreadSeen.clear()
        seenThreadAt.clear()
        changed()
    }

    // ---- Inside ----

    /**
     * A load says Feed can't be reached: `Connexion perdue` once, as a `state` says it (not again at each reload that
     * fails), and the 15-second `open` takes over until Feed is back.
     */
    private fun connectionLost(outcome: Outcome) {
        if (availability == FeedAvailability.UNAVAILABLE || availability == FeedAvailability.DISABLED) return
        availability = if (outcome == Outcome.DISABLED) FeedAvailability.DISABLED else FeedAvailability.UNAVAILABLE
        notice = FeedNotice.ConnectionLost
        nextAutoRetry = ticks + AUTO_RETRY_TICKS
    }

    private fun changed() {
        revision++
    }

    private fun request(pending: PendingRequest, payload: (Int) -> CustomPacketPayload): Int {
        val id = ids.next()
        requests[id] = pending
        deadlines[id] = ticks + ANSWER_TIMEOUT_TICKS
        send(payload(id))
        return id
    }

    private fun sendable(text: String) = TextLimits.canSend(text.trim(), Feed.MAX_TEXT)

    private fun loadFirst(list: FeedList) {
        list.loadingFirst = true
        list.firstError = null
        request(PendingRequest.LoadFeed(list.kind, more = false, before = null, generation = resets)) { LoadFeedPayload(it, list.kind, null) }
        changed()
    }

    private fun loadThreadFirst(thread: ThreadState) {
        thread.loadingFirst = true
        thread.firstError = null
        request(PendingRequest.LoadThread(thread.id, more = false, after = null, generation = resets)) { LoadThreadPayload(it, thread.id, null) }
        changed()
    }

    private fun usable(view: PostView, kind: FeedKind) = view.feed == kind && !view.isReply && view.id !in gone

    private fun replaceFirstPage(list: FeedList, page: PagePayload) {
        list.pinnedIds.clear()
        list.postIds.clear()
        list.waitingIds.clear()
        list.loadedWith.clear()
        if (list.kind == FeedKind.ANNOUNCEMENTS) {
            for (view in page.pinned) if (usable(view, list.kind) && view.id !in list.pinnedIds) {
                posts[view.id] = incoming(view)
                list.pinnedIds += view.id
            }
        }
        for (view in page.posts) if (usable(view, list.kind) && !list.holds(view.id)) {
            posts[view.id] = incoming(view.copy(pinnedAt = null))
            list.postIds += view.id
            list.loadedWith[view.id] = null
        }
        list.next = page.next
        list.hasMore = page.next != null
        list.moreFailed = false
        list.loaded = true
    }

    private fun appendPage(list: FeedList, page: PagePayload, before: String?) {
        for (view in page.posts) {
            if (!usable(view, list.kind) || list.holds(view.id)) continue
            posts[view.id] = incoming(view.copy(pinnedAt = null))
            list.postIds += view.id
            list.loadedWith[view.id] = before
        }
        list.next = page.next
        list.hasMore = page.next != null
    }

    /**
     * A first page over a list already held, after a `reset` (`docs/feed-app.md` 8): posts newer than the newest held
     * arrive as live posts do, held posts the page lacks within its span went meanwhile, the rest is updated in place.
     */
    private fun mergeFirstPage(list: FeedList, page: PagePayload) {
        val pageOldest = page.posts.filter { usable(it, list.kind) }.lastOrNull()?.createdAt
        val heldNewest = list.postIds.firstOrNull()?.let(posts::get)?.createdAt
        if (page.next != null && pageOldest != null && heldNewest != null && heldNewest.isBefore(pageOldest)) {
            // More than a page was posted meanwhile: the page doesn't reach what is held, and what lies between would
            // never load. The page starts the list again, as a first load does.
            replaceFirstPage(list, page)
            return
        }
        if (list.kind == FeedKind.ANNOUNCEMENTS) {
            val pinned = page.pinned.filter { usable(it, list.kind) && it.pinnedAt != null }
            for (view in pinned) posts[view.id] = incoming(view)
            applyPinGroup(pinned.map { Pin(it.id, it.pinnedAt!!) })
        }
        val fresh = page.posts.filter { usable(it, list.kind) }
        val inPage = fresh.mapTo(HashSet()) { it.id } + list.pinnedIds
        val newestHeld = list.postIds.firstOrNull()?.let(posts::get)?.createdAt
        val oldest = fresh.lastOrNull()?.createdAt
        // With no next page, the page is the whole feed; otherwise it covers what is strictly newer than its oldest
        // post only: a post of that same time may have been cut onto the next page by the keyset.
        val covers = { id: UUID -> page.next == null || (oldest != null && posts[id]?.createdAt?.isAfter(oldest) == true) }
        for (id in (list.postIds + list.waitingIds)) if (id !in inPage && covers(id)) {
            list.removeId(id)
            gone += id
        }
        for (view in fresh) {
            val held = list.holds(view.id)
            posts[view.id] = incoming(if (view.id in list.pinnedIds) view else view.copy(pinnedAt = null))
            if (held) continue
            if (newestHeld == null || view.createdAt.isAfter(newestHeld)) {
                insertWaiting(list, view.id)
                if (!view.mine && viewing != list.kind) unread[list.kind] = true
            } else {
                insertByDate(list, view.id)
                list.loadedWith[view.id] = null
            }
        }
        val oldestHeld = list.postIds.lastOrNull()?.let(posts::get)?.createdAt
        if (page.next == null) {
            list.next = null
            list.hasMore = false
        } else if (oldest == null || oldestHeld == null || !oldestHeld.isBefore(oldest)) {
            // The held list ends within the page: the page's cursor is the one to go on with.
            list.next = page.next
            list.hasMore = true
        }
        list.loaded = true
    }

    private fun mergeThreadPage(thread: ThreadState, replies: List<PostView>, next: String?) {
        val fresh = replies.filter { it.threadId == thread.id && it.id !in gone }
        if (!thread.loaded) {
            thread.replyIds.clear()
            for (reply in fresh) if (reply.id !in thread.replyIds) {
                posts[reply.id] = incoming(reply)
                thread.replyIds += reply.id
            }
            thread.next = next
            thread.hasMore = next != null
            thread.loaded = true
            return
        }
        val inPage = fresh.mapTo(HashSet()) { it.id }
        val newest = fresh.lastOrNull()?.createdAt
        // As for a feed: strictly older than the page's newest reply, or the whole thread without a next page.
        val covers = { id: UUID -> next == null || (newest != null && posts[id]?.createdAt?.isBefore(newest) == true) }
        thread.replyIds.removeAll { id -> (id !in inPage && covers(id)).also { if (it) gone += id } }
        for (reply in fresh) {
            posts[reply.id] = incoming(reply)
            if (reply.id in thread.replyIds) continue
            var i = thread.replyIds.size
            while (i > 0 && posts[thread.replyIds[i - 1]]?.createdAt?.isAfter(reply.createdAt) == true) i--
            thread.replyIds.add(i, reply.id)
        }
        // With a next page, the thread goes on from it: replies made meanwhile come after what is held, and the next
        // pages skip what is held already. Keeping an older end would leave them out.
        thread.next = next
        thread.hasMore = next != null
    }

    private fun livePost(view: PostView) {
        val list = feed(view.feed)
        val held = list.holds(view.id)
        if (held) {
            posts[view.id] = incoming(if (view.id in list.pinnedIds) view else view.copy(pinnedAt = null))
            return
        }
        if (list.loaded) {
            posts[view.id] = incoming(view.copy(pinnedAt = null))
            insertWaiting(list, view.id)
        }
        if (!view.mine && viewing != view.feed) unread[view.feed] = true
    }

    private fun liveReply(view: PostView) {
        val threadId = view.threadId ?: return
        val thread = threads[threadId]
        val held = thread?.replyIds?.contains(view.id) == true
        if (held) {
            posts[view.id] = incoming(view)
        } else if (thread != null && thread.loaded && !thread.hasMore) {
            posts[view.id] = incoming(view)
            thread.replyIds += view.id
        }
        if (!view.answersMe || view.mine || !raisedFor.add(view.id)) return
        if (viewingThread == threadId) {
            // The player is reading this thread: seen at once, nothing raised.
            threadSeenSent = null
            markThreadSeen(threadId)
            return
        }
        // As the site counts it (feed-mod.md 3): on what the reply answers when that is the player's, and on the
        // thread's main post when that is the player's; never on someone else's post.
        val answered = view.replyTo?.id ?: threadId
        raiseMarker(answered)
        if (answered != threadId) raiseMarker(threadId)
    }

    private fun raiseMarker(id: UUID) {
        val post = posts[id] ?: return
        if (post.mine) posts[id] = post.copy(unseenReplies = post.unseenReplies + 1)
    }

    private fun markThreadSeen(threadId: UUID) {
        if (threadSeenSent == threadId) return
        for ((id, post) in posts.entries.toList()) {
            if ((id == threadId || post.threadId == threadId) && post.unseenReplies != 0) posts[id] = post.copy(unseenReplies = 0)
        }
        // Coalesced: however many replies come while the thread is read, one mark goes every 2 seconds at most.
        // Remembered only once queued: without the state yet, the state's arrival queues it (onState).
        if (state?.linked == true) {
            threadSeenSent = threadId
            pendingThreadSeen += threadId
            flushSeen()
        }
        changed()
    }

    private fun insertWaiting(list: FeedList, id: UUID) {
        if (id in list.waitingIds) return
        val at = posts[id]?.createdAt ?: return
        var i = 0
        while (i < list.waitingIds.size && posts[list.waitingIds[i]]?.createdAt?.isAfter(at) == true) i++
        list.waitingIds.add(i, id)
        while (list.waitingIds.size > MAX_FEED) list.waitingIds.removeAt(list.waitingIds.size - 1)
    }

    /** Puts [id] in [list]'s posts at its date, unless its date lies past what is loaded (a later page has it). */
    private fun insertByDate(list: FeedList, id: UUID) {
        if (id in list.postIds) return
        val at = posts[id]?.createdAt ?: return
        var i = 0
        while (i < list.postIds.size && posts[list.postIds[i]]?.createdAt?.isAfter(at) != false) i++
        if (i == list.postIds.size && list.hasMore && list.postIds.isNotEmpty()) return
        list.postIds.add(i, id)
    }

    private fun pinGroup(): List<Pin> = feed(FeedKind.ANNOUNCEMENTS).pinnedIds.mapNotNull { id -> posts[id]?.pinnedAt?.let { Pin(id, it) } }

    /** The whole pinned group, most recent pin first (a `pins` payload, a page, a pin's answer). */
    private fun applyPinGroup(group: List<Pin>) {
        val list = feed(FeedKind.ANNOUNCEMENTS)
        val pinned = group.distinctBy { it.id }.sortedByDescending { it.pinnedAt }.take(Feed.MAX_PINS)
        val ids = pinned.map { it.id }
        for (id in list.pinnedIds.toList()) if (id !in ids) {
            // Unpinned: back to its date order in the list.
            list.pinnedIds.remove(id)
            posts[id]?.let { posts[id] = it.copy(pinnedAt = null) }
            insertByDate(list, id)
        }
        var unknown = false
        list.pinnedIds.clear()
        for (pin in pinned) {
            val post = posts[pin.id]
            if (post == null || post.feed != FeedKind.ANNOUNCEMENTS || post.isReply) {
                unknown = true
                continue
            }
            posts[pin.id] = post.copy(pinnedAt = pin.pinnedAt)
            list.postIds.remove(pin.id)
            list.waitingIds.remove(pin.id)
            list.pinnedIds += pin.id
        }
        // A post pinned that the client doesn't hold: the first page brings it.
        if (unknown && list.loaded) {
            list.stale = true
            list.reloadNotBefore = 0
        }
    }

    private fun removePost(id: UUID) {
        gone += id
        for (list in feeds.values) list.removeId(id)
        for (thread in threads.values) thread.replyIds.remove(id)
        if (threads.containsKey(id)) threadGone(id)
        pendingLikes.remove(id)
        pendingPins.remove(id)
        pinsMovedSince.remove(id)
        highlights.remove(id)
        posts.remove(id)
    }

    private fun threadGone(threadId: UUID) {
        val thread = threads[threadId] ?: return
        thread.deleted = true
        thread.loadingFirst = false
        thread.loadingMore = false
        thread.replyIds.clear()
        thread.aheadIds.clear()
        gone += threadId
        for (list in feeds.values) list.removeId(threadId)
        posts.remove(threadId)
    }

    /**
     * The answer to one click on a heart. The site's state is the last one an `OK` answered (by request order, however
     * the answers arrive); the heart shows it once every click is answered, until then what the player clicked. A
     * refusal says so only for the last click: an earlier one is overtaken by it.
     */
    private fun likeAnswered(request: PendingRequest.Like, payload: ResultPayload) {
        val id = request.postId
        val pending = pendingLikes[id] ?: return
        pending.outstanding -= payload.requestId
        val liked = payload.liked
        val likes = payload.likes
        if (payload.outcome == Outcome.OK) {
            // An OK without its fields (a broken server) confirms nothing, and freezes nothing either.
            if (liked != null && likes != null && payload.requestId > pending.confirmedBy) {
                pending.confirmedLiked = liked
                pending.confirmedLikes = likes
                pending.confirmedBy = payload.requestId
            }
        } else if (payload.outcome == Outcome.NOT_FOUND) {
            pendingLikes.remove(id)
            removePost(id)
            collect()
            return
        } else if (pending.outstanding.none { it > payload.requestId }) {
            notice = actionNotice(payload)
        }
        if (pending.outstanding.isNotEmpty()) return
        pendingLikes.remove(id)
        posts[id]?.let { posts[id] = it.copy(liked = pending.confirmedLiked, likes = pending.confirmedLikes) }
    }

    private fun postAnswered(payload: ResultPayload) {
        val draft = compose
        draft.sending = false
        val post = payload.post
        if (payload.outcome == Outcome.NOT_FOUND) {
            // The key's post was published, then deleted (feed-mod.md 11): retrying can't help, the draft goes.
            draft.clear()
            draft.outcome = DraftOutcome.ActionFailed
            return
        }
        if (payload.outcome != Outcome.OK || post == null) {
            draftFailure(payload, draft)?.let { settleDraft(draft, it) } ?: run { draft.sending = true }
            return
        }
        draft.clear()
        draft.outcome = DraftOutcome.Sent(post)
        if (post.id in gone || post.isReply) return
        val list = feed(post.feed)
        posts[post.id] = incoming(post.copy(pinnedAt = null))
        if (!list.loaded) return
        // Always at its place, scrolled into view (art direction 9.5): the posts waiting before it come along.
        list.waitingIds.remove(post.id)
        releaseWaiting(post.feed)
        // At its date: a post of someone else that came live meanwhile may be newer.
        if (!list.holds(post.id)) insertByDate(list, post.id)
        highlights += post.id
        reveals[post.feed] = post.id
    }

    private fun replyAnswered(request: PendingRequest.CreateReply, payload: ResultPayload) {
        val draft = replyDrafts[request.threadId]
        draft?.sending = false
        val reply = payload.post
        if (payload.outcome == Outcome.NOT_FOUND) {
            // The site says so when the thread went, and also when only the reply answered went: the thread is loaded
            // again, and leaves only if that reload says it is gone ([DraftOutcome.ThreadGone] then follows).
            draft?.outcome = DraftOutcome.ActionFailed
            threads[request.threadId]?.takeIf { it.loaded }?.let {
                it.stale = true
                it.reloadNotBefore = 0
                threadsToCheck += it.id
            }
            return
        }
        if (payload.outcome != Outcome.OK || reply == null) {
            if (draft != null) draftFailure(payload, draft)?.let { settleDraft(draft, it) } ?: run { draft.sending = true }
            return
        }
        draft?.clear()
        draft?.outcome = DraftOutcome.Sent(reply)
        if (reply.id in gone || reply.threadId != request.threadId) return
        val thread = threads[request.threadId] ?: return
        posts[reply.id] = incoming(reply)
        if (thread.loaded && reply.id !in thread.replyIds) {
            thread.replyIds += reply.id
            // Later pages not loaded yet: the reply shows at the end at once (art direction 9.7), ahead of them, and
            // the screen loads them into the gap ([ThreadState.ahead]).
            if (thread.hasMore) thread.aheadIds += reply.id
        }
        if (reply.id in thread.replyIds) {
            highlights += reply.id
            threadReveals[thread.id] = reply.id
        }
    }

    private fun pinAnswered(request: PendingRequest.Pin, payload: ResultPayload) {
        val before = pendingPins.remove(request.postId)
        val moved = pinsMovedSince.remove(request.postId)
        if (payload.outcome == Outcome.OK) {
            payload.pinned?.let(::applyPinGroup)
            notice = FeedNotice.PinDone(request.pinned)
            return
        }
        if (payload.outcome == Outcome.NOT_FOUND) {
            // The post went (feed-app.md 5.3): it leaves the lists.
            removePost(request.postId)
            collect()
            return
        }
        // Put back as it was at the click, unless the server sent its group since: that one stands.
        if (!moved) before?.let(::applyPinGroup)
        notice = if (payload.outcome == Outcome.PIN_LIMIT) FeedNotice.PinLimit(request.postId) else actionNotice(payload)
    }

    private fun deleteAnswered(request: PendingRequest.Delete, payload: ResultPayload) {
        deleting -= request.postId
        if (payload.outcome == Outcome.OK || payload.outcome == Outcome.NOT_FOUND) {
            // Read when it was sent: a `removed` may have taken the post before this answer.
            val reply = request.reply
            removePost(request.postId)
            collect()
            if (payload.outcome == Outcome.OK) notice = FeedNotice.Deleted(request.postId, reply)
        } else {
            notice = actionNotice(payload)
        }
    }

    /** What a refused like, pin or delete says (`docs/feed-app.md` 5.3). */
    private fun actionNotice(payload: ResultPayload): FeedNotice = when (payload.outcome) {
        Outcome.RATE_LIMITED -> FeedNotice.TooFast(payload.retryAfter?.coerceIn(1, 3600) ?: 1)
        Outcome.NOT_LINKED -> FeedNotice.Refused(WriteBlock.GUEST)
        Outcome.BANNED -> FeedNotice.Refused(WriteBlock.BANNED)
        else -> FeedNotice.ActionFailed
    }

    /** What a refused draft says: the draft stays, with its key. */
    /**
     * What a refused draft says, each refusal as what it is (`docs/feed-app.md` 5.3, art direction 9.11); the draft
     * stays, with its key. Only a real failure to send offers Retry. Null: not known yet (see [awaitWritingState]).
     */
    private fun draftFailure(payload: ResultPayload, draft: Any): DraftOutcome? = when (payload.outcome) {
        Outcome.RATE_LIMITED -> DraftOutcome.TooFast(payload.retryAfter?.coerceIn(1, 3600) ?: 1)
        Outcome.NOT_LINKED -> DraftOutcome.Refused(WriteBlock.GUEST)
        Outcome.BANNED -> DraftOutcome.Refused(WriteBlock.BANNED)
        Outcome.NOT_ALLOWED -> DraftOutcome.ActionFailed
        Outcome.DISABLED -> awaitWritingState(draft)
        else -> DraftOutcome.SendFailed
    }

    /**
     * `DISABLED` says either that Feed is switched off (a failure to send, with Retry) or that writing is (its own words,
     * no Retry); the server reads the player's state again in the second case only. So the draft keeps sending until
     * that state comes, or until it is clear none will.
     */
    private fun awaitWritingState(draft: Any): DraftOutcome? = when {
        availability == FeedAvailability.DISABLED -> DraftOutcome.SendFailed
        state?.writing == false -> DraftOutcome.Refused(WriteBlock.WRITING_OFF)
        else -> {
            draftsAwaitingState[draft] = ticks + WRITING_STATE_TICKS
            null
        }
    }

    private fun settleDraft(draft: Any, outcome: DraftOutcome) {
        when (draft) {
            is ComposeDraft -> {
                draft.sending = false
                draft.outcome = outcome
            }
            is ReplyDraft -> {
                draft.sending = false
                draft.outcome = outcome
            }
        }
        changed()
    }

    /** The drafts refused as `DISABLED` that wait for the state to say why (writing off) or for their deadline. */
    private fun settleAwaitingDrafts(timedOut: Boolean) {
        if (draftsAwaitingState.isEmpty()) return
        for ((draft, deadline) in draftsAwaitingState.entries.toList()) {
            val outcome = when {
                state?.writing == false -> DraftOutcome.Refused(WriteBlock.WRITING_OFF)
                !timedOut || ticks >= deadline -> DraftOutcome.SendFailed
                else -> continue
            }
            draftsAwaitingState.remove(draft)
            settleDraft(draft, outcome)
        }
    }

    /**
     * A list the player isn't looking at keeps its newest [MAX_FEED] posts, or more when they were reading further down;
     * the older ones load again on scroll.
     */
    private fun trimFeed(list: FeedList) {
        // Never what the player was reading, nor a page under it: they come back to their place.
        val reading = list.shownTop?.let { list.postIds.indexOf(it) } ?: -1
        val keep = maxOf(MAX_FEED, reading + 1 + Feed.FEED_PAGE)
        if (list.postIds.size <= keep) return
        val kept = list.postIds.subList(0, keep).toList()
        val dropped = list.postIds.subList(keep, list.postIds.size).toList()
        list.postIds.clear()
        list.postIds += kept
        dropped.forEach { list.loadedWith.remove(it) }
        // The page of the oldest kept post is asked again; what is still held is skipped.
        list.next = list.loadedWith[kept.last()]
        list.hasMore = true
        list.moreFailed = false
        // A later page in flight would land after the new end, leaving a hole: its answer is dropped.
        requests.entries.removeIf { (_, r) -> r is PendingRequest.LoadFeed && r.feed == list.kind && r.more }
        list.loadingMore = false
        collect()
        changed()
    }

    private fun trimThreads() {
        while (threads.size > MAX_THREADS) {
            val eldest = threads.keys.firstOrNull { it != viewingThread } ?: return
            threads.remove(eldest)
        }
        collect()
    }

    /**
     * A view from the server about to be held: the heart of a post whose like waits for its answer stays as the player
     * clicked it (the answer settles it), whatever page, live copy or thread brings the view meanwhile.
     */
    private fun incoming(view: PostView): PostView {
        if (view.id !in pendingLikes) return view
        val shown = posts[view.id] ?: return view
        return view.copy(liked = shown.liked, likes = shown.likes)
    }

    /** Drops the views no list or thread refers to any more. */
    private fun collect() {
        val kept = HashSet<UUID>()
        for (list in feeds.values) list.allIds().forEach { kept += it }
        for (thread in threads.values) {
            kept += thread.id
            kept += thread.replyIds
        }
        posts.keys.retainAll(kept)
    }

    /** A like shown before the server answered: what to put back if the last click fails. */
    private class PendingLike(var confirmedLiked: Boolean, var confirmedLikes: Int) {
        /** The clicks not answered yet. */
        val outstanding = HashSet<Int>()

        /** The request whose `OK` gave the confirmed state: an earlier one answered later doesn't overwrite it. */
        var confirmedBy = 0
    }

    /** A set that forgets its oldest entries past [max]. */
    private class BoundedSet(private val max: Int) {
        private val items = LinkedHashSet<UUID>()

        fun add(id: UUID): Boolean {
            val added = items.add(id)
            while (items.size > max) items.remove(items.first())
            return added
        }

        operator fun plusAssign(id: UUID) {
            add(id)
        }

        operator fun contains(id: UUID) = id in items

        fun clear() = items.clear()
    }

    companion object {
        /** Posts per feed when it isn't shown (`docs/feed-app.md` 8); also the most posts waiting behind the pill. */
        const val MAX_FEED = 200

        const val MAX_THREADS = 16
        const val MAX_REPLY_DRAFTS = 20

        /** The longest draft: past 280, so the counter can show how far over the text went (art direction 8.7). */
        const val MAX_DRAFT = 1000

        /** About 15 seconds between automatic `open`s while the connection is lost (art direction 9.12). */
        const val AUTO_RETRY_TICKS = 300

        /** How long the error block's Retry waits for `open`'s answer. */
        const val RETRY_TIMEOUT_TICKS = 300

        /**
         * A feed's or a thread's seen mark at most once every 2 seconds, and only when what is shown changed: the
         * server drops seen marks past a burst of 3, then one every 2 seconds (`docs/feed-app.md` 4.3), which all the
         * marks share here too ([SEEN_BURST]). Paced at 2.2 seconds, a margin over the server's 2: marks that reach it
         * a little late (the network, client ticks catching up after a lag) still find the allowance refilled.
         */
        const val SEEN_EVERY_TICKS = 44

        /** How long a draft refused as `DISABLED` waits for the state that says whether writing is off. */
        const val WRITING_STATE_TICKS = 240

        /** A notice not told within this long (5 seconds) is dropped: it would no longer mean anything. */
        const val NOTICE_MAX_AGE_TICKS = 100

        private const val MAX_NOTICES = 8

        /**
         * How long an answer is waited for (30 seconds): well past the server's own 12-second deadline on the site, so
         * only an answer lost on the way gets there.
         */
        const val ANSWER_TIMEOUT_TICKS = 600

        /** Seen marks that may go at once before the 2-second pace. */
        const val SEEN_BURST = 3

        /**
         * A read the server refused as too frequent (`RATE_LIMITED`) is asked again after this: pages carry no
         * `retryAfter`, and the server's own limit lets one read through a second.
         */
        const val READ_RETRY_TICKS = 40

        private const val GONE_REMEMBERED = 1024
    }
}
