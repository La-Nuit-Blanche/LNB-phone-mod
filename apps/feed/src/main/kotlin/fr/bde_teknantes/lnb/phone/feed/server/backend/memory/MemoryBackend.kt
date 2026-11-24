package fr.bde_teknantes.lnb.phone.feed.server.backend.memory

import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.rules.FeedText
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerEntry
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerState
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Created
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationPost
import fr.bde_teknantes.lnb.phone.feed.server.backend.PinState
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * What the memory backend needs to know of the game's players: their names (for the author block) and whether they
 * are operators (the stand-in for the site's moderator roles). [MemoryPlayers] gives the running server's.
 */
interface PlayerDirectory {
    /** The username of [player], connected or not, or null if the server has never seen them. */
    fun username(player: UUID): String?

    /** Whether [player] has the permission level 2 or more. */
    fun isOperator(player: UUID): Boolean
}

/**
 * The site's rules, in memory (`docs/feed-app.md` 3.3), for unit tests, the screenshot harness and developing the app
 * without a site. Nothing is kept: everything is lost when the server stops.
 *
 * - **Identity.** Every player the [directory] knows is linked, each Minecraft account its own LNB account, with no
 *   real name; except the [guests] (usernames, read only) and the sample authors ([SampleContent]), who have the art
 *   direction's names. A player the directory doesn't know is treated as a guest.
 * - **Rights.** `moderate` is the operator level (or a sample moderator), `announce` is moderate or a grant from
 *   `/feed announcer add`, `write` is linked; nobody is banned; `writing` is on unless [setWriting] turned it off.
 * - **Rules** as `docs/feed-mod.md` says, with the same order of checks, problem codes and vectors (13.2 to 13.6).
 *
 * Answers are completed futures, and the listener is called right after each change, on the caller's thread, once the
 * change is complete. IDs come from a counter and times from the injected [clock], truncated to milliseconds and kept
 * increasing for what it creates, so a run with a fixed clock is the same every time.
 */
class MemoryBackend(
    private val clock: Clock,
    private val directory: PlayerDirectory,
    guests: Collection<String> = emptyList(),
    /** The site's per-account limits (`feed-mod.md` 7); off for tests that post a lot. */
    private val rateLimits: Boolean = true,
) : FeedBackend {
    private val guests: Set<String> = guests.map { it.lowercase() }.toSet()

    private class Post(
        val id: UUID,
        val feed: FeedKind,
        val threadId: UUID?,
        val replyTo: UUID?,
        val text: String,
        val createdAt: Instant,
        val anonymous: Boolean,
        /** The Minecraft account it was posted from, and its name then, as the site stores them. */
        val author: UUID,
        val authorName: String,
        var deletedAt: Instant? = null,
        var pinnedAt: Instant? = null,
        /** Sample content only: likes and replies counted without rows behind them. */
        val baseLikes: Int = 0,
        val baseReplies: Int = 0,
    ) {
        val isMain get() = threadId == null
        val deleted get() = deletedAt != null
        val thread: UUID get() = threadId ?: id
    }

    private data class Identity(val username: String, val realName: String?)

    private data class Fingerprint(val player: UUID, val feed: FeedKind?, val thread: UUID?, val replyTo: UUID?, val text: String, val anonymous: Boolean)

    private data class KeyEntry(val fingerprint: Fingerprint, val post: UUID)

    private enum class Action(vararg val windows: Pair<Duration, Int>) {
        POST(Duration.ofMinutes(1) to 3, Duration.ofHours(1) to 15),
        REPLY(Duration.ofMinutes(1) to 6, Duration.ofHours(1) to 60),
        LIKE(Duration.ofMinutes(1) to 60),
    }

    private val posts = LinkedHashMap<UUID, Post>()
    private val likes = HashMap<UUID, MutableSet<UUID>>()
    private val keys = HashMap<UUID, KeyEntry>()
    private val feedSeen = HashMap<Pair<UUID, FeedKind>, Instant>()
    private val threadSeen = HashMap<Pair<UUID, UUID>, Instant>()
    private val queue = ArrayList<QueuedNotification>()
    private val granted = HashSet<UUID>()
    private val sampleModerators = HashSet<UUID>()
    private val sampleIdentities = HashMap<UUID, Identity>()
    private val actions = HashMap<Pair<UUID, Action>, ArrayDeque<Instant>>()
    private var counter = 0L
    private var lastIssued = Instant.EPOCH

    private var offline = false
    private var disabled = false
    private var writing = true

    @Volatile
    private var listener: FeedEventListener? = null

    // ---- Lifecycle and dev switches ----

    override fun start(listener: FeedEventListener) {
        this.listener = listener
        listener.onConnected()
    }

    override fun stop() {
        listener = null
    }

    /** `/feed dev offline`: every call is `Unavailable` and the stream reads as down. */
    fun setOffline(on: Boolean) = switch { offline = on }

    /** `/feed dev disabled`: every call answers `Disabled("feed")` and the stream refuses to connect. */
    fun setDisabled(on: Boolean) = switch { disabled = on }

    /** `/feed dev writing`: the `feed.writing` flag. */
    fun setWriting(on: Boolean) = locked { writing = on }

    val isOffline: Boolean get() = locked { offline }
    val isDisabled: Boolean get() = locked { disabled }
    val isWriting: Boolean get() = locked { writing }

    private fun switch(change: () -> Unit) {
        val (wasUp, isUp, isDisabledNow) = locked {
            val before = !offline && !disabled
            change()
            Triple(before, !offline && !disabled, disabled)
        }
        val target = listener ?: return
        if (wasUp && !isUp) target.onDisconnected(isDisabledNow)
        if (!wasUp && isUp) target.onConnected()
    }

    // ---- Reads ----

    override fun player(player: UUID) = answer {
        gate()?.let { return@answer it }
        BackendResult.Ok(stateOf(player))
    }

    override fun page(viewer: UUID, feed: FeedKind, before: String?, limit: Int) = answer {
        gate()?.let { return@answer it }
        if (limit !in 1..Feed.MAX_PAGE) return@answer badRequest()
        val cursor = before?.let { Cursor.parse(it) ?: return@answer badRequest() }
        val pinned = if (feed == FeedKind.ANNOUNCEMENTS && cursor == null) {
            posts.values.filter { it.pinnedAt != null && !it.deleted }.sortedByDescending { it.pinnedAt }.map { view(it, viewer) }
        } else {
            emptyList()
        }
        val candidates = posts.values
            .filter { it.isMain && it.feed == feed && !it.deleted && it.pinnedAt == null }
            .filter { cursor == null || cursor.isAfterNewestFirst(it.createdAt, it.id) }
            .sortedWith(NEWEST_FIRST)
        val page = candidates.take(limit)
        val next = if (candidates.size > limit) Cursor(page.last().createdAt, page.last().id).toString() else null
        BackendResult.Ok(FeedPage(feed, pinned, page.map { view(it, viewer) }, next))
    }

    override fun thread(viewer: UUID, threadId: UUID, after: String?, limit: Int) = answer {
        gate()?.let { return@answer it }
        if (limit !in 1..Feed.MAX_PAGE) return@answer badRequest()
        val cursor = after?.let { Cursor.parse(it) ?: return@answer badRequest() }
        val main = posts[threadId]?.takeIf { it.isMain && !it.deleted } ?: return@answer notFound()
        val candidates = posts.values
            .filter { it.threadId == main.id && !it.deleted }
            .filter { cursor == null || cursor.isAfterOldestFirst(it.createdAt, it.id) }
            .sortedWith(OLDEST_FIRST)
        val page = candidates.take(limit)
        val next = if (candidates.size > limit) Cursor(page.last().createdAt, page.last().id).toString() else null
        BackendResult.Ok(ThreadPage(view(main, viewer), page.map { view(it, viewer) }, next))
    }

    override fun views(viewers: List<UUID>, ids: List<UUID>) = answer {
        gate()?.let { return@answer it }
        if (viewers.size !in 1..Feed.MAX_VIEWERS || viewers.toSet().size != viewers.size) return@answer badRequest()
        if (ids.size !in 1..Feed.MAX_VIEW_IDS || ids.toSet().size != ids.size) return@answer badRequest()
        val shown = ids.mapNotNull { id -> posts[id]?.takeIf { !it.deleted } }
        val result = LinkedHashMap<UUID, List<PostView>>()
        for (viewer in viewers) result[viewer] = shown.map { view(it, viewer) }
        BackendResult.Ok(result as Map<UUID, List<PostView>>)
    }

    // ---- Writes ----

    override fun createPost(key: UUID, player: UUID, feed: FeedKind, text: String, anonymous: Boolean) = answer {
        gate(writes = true)?.let { return@answer it }
        if (text.length > Feed.MAX_RAW_TEXT) return@answer badRequest()
        val cleaned = FeedText.clean(text)
        val fingerprint = Fingerprint(player, feed, null, null, cleaned, anonymous)
        replay(key, fingerprint, player)?.let { return@answer it }
        val identity = linkedIdentity(player) ?: return@answer refused(404, "notLinked")
        if (feed == FeedKind.ANNOUNCEMENTS && !rightsOf(player).announce) return@answer refused(403, "notAllowed")
        checkText(cleaned)?.let { return@answer it }
        limit(player, Action.POST)?.let { return@answer it }

        val post = Post(nextId("post"), feed, null, null, cleaned, issueTime(), anonymous, player, identity.username)
        posts[post.id] = post
        keys[key] = KeyEntry(fingerprint, post.id)
        record(player, Action.POST)
        emit { it.onPost(post.id, post.feed, null, post.createdAt) }
        if (feed == FeedKind.ANNOUNCEMENTS) {
            queue += QueuedNotification(
                nextId("notification"), QueuedNotification.Kind.ANNOUNCEMENT, post.createdAt,
                players = null, except = accountsOf(player), post = notificationPost(post),
            )
            emit { it.onNotification() }
        }
        BackendResult.Ok(Created(false, view(post, player)))
    }

    override fun createReply(key: UUID, player: UUID, threadId: UUID, replyTo: UUID?, text: String, anonymous: Boolean) = answer {
        gate(writes = true)?.let { return@answer it }
        if (text.length > Feed.MAX_RAW_TEXT) return@answer badRequest()
        val cleaned = FeedText.clean(text)
        val fingerprint = Fingerprint(player, null, threadId, replyTo, cleaned, anonymous)
        replay(key, fingerprint, player)?.let { return@answer it }
        val identity = linkedIdentity(player) ?: return@answer refused(404, "notLinked")
        val main = posts[threadId]?.takeIf { it.isMain && !it.deleted } ?: return@answer notFound()
        val target = if (replyTo == null) {
            main
        } else {
            posts[replyTo]?.takeIf { it.threadId == main.id && !it.deleted } ?: return@answer notFound()
        }
        checkText(cleaned)?.let { return@answer it }
        limit(player, Action.REPLY)?.let { return@answer it }

        val reply = Post(nextId("post"), main.feed, main.id, replyTo, cleaned, issueTime(), anonymous, player, identity.username)
        posts[reply.id] = reply
        keys[key] = KeyEntry(fingerprint, reply.id)
        record(player, Action.REPLY)
        emit { it.onPost(reply.id, reply.feed, main.id, reply.createdAt) }
        val changed = listOfNotNull(main, target.takeIf { it !== main }).map(::counts)
        emit { it.onCounts(changed) }
        if (accountOf(target.author) != accountOf(player)) {
            queue += QueuedNotification(
                nextId("notification"), QueuedNotification.Kind.REPLY, reply.createdAt,
                players = accountsOf(target.author), except = emptyList(), post = notificationPost(reply),
            )
            emit { it.onNotification() }
        }
        BackendResult.Ok(Created(false, view(reply, player)))
    }

    override fun like(player: UUID, postId: UUID, liked: Boolean) = answer {
        gate(writes = true)?.let { return@answer it }
        linkedIdentity(player) ?: return@answer refused(404, "notLinked")
        val post = posts[postId]?.takeIf { !it.deleted } ?: return@answer notFound()
        limit(player, Action.LIKE)?.let { return@answer it }
        record(player, Action.LIKE)
        val likers = likes.getOrPut(post.id) { HashSet() }
        val changed = if (liked) likers.add(accountOf(player)) else likers.remove(accountOf(player))
        if (changed) {
            val update = counts(post)
            emit { it.onCounts(listOf(update)) }
        }
        BackendResult.Ok(LikeState(post.id, likesOf(post), liked))
    }

    override fun pin(player: UUID, postId: UUID, pinned: Boolean, replace: Boolean) = answer {
        gate()?.let { return@answer it }
        linkedIdentity(player) ?: return@answer refused(404, "notLinked")
        if (!rightsOf(player).announce) return@answer refused(403, "notAllowed")
        val post = posts[postId]?.takeIf { !it.deleted } ?: return@answer notFound()
        // Only pinning refuses a reply or a Global post: unpinning what isn't pinned changes nothing and answers the
        // group, whatever the post (feed-mod.md 4.8).
        if (pinned && (!post.isMain || post.feed != FeedKind.ANNOUNCEMENTS)) return@answer refused(409, "notPinnable")
        var unpinned: UUID? = null
        var changed = false
        if (pinned && post.pinnedAt == null) {
            val group = pinnedPosts()
            if (group.size >= Feed.MAX_PINS) {
                val oldest = group.last()
                if (!replace) return@answer BackendResult.Refused(409, "pinLimit", oldestPin = oldest.id)
                oldest.pinnedAt = null
                unpinned = oldest.id
            }
            post.pinnedAt = issueTime()
            changed = true
        } else if (!pinned && post.pinnedAt != null) {
            post.pinnedAt = null
            changed = true
        }
        val group = pinGroup()
        if (changed) emit { it.onPins(group) }
        BackendResult.Ok(PinState(group, unpinned))
    }

    override fun delete(player: UUID, postId: UUID) = answer {
        gate()?.let { return@answer it }
        linkedIdentity(player) ?: return@answer refused(404, "notLinked")
        val post = posts[postId] ?: return@answer notFound()
        val allowed = accountOf(post.author) == accountOf(player) || rightsOf(player).moderate
        if (post.deleted) return@answer if (allowed) BackendResult.Ok(Unit) else notFound()
        if (!allowed) return@answer refused(403, "notAllowed")

        val now = issueTime()
        val removed = if (post.isMain) listOf(post) + posts.values.filter { it.threadId == post.id && !it.deleted } else listOf(post)
        val wasPinned = post.pinnedAt != null
        for (row in removed) {
            row.deletedAt = now
            row.pinnedAt = null
        }
        val removedIds = removed.map { it.id }.toSet()
        queue.removeAll { it.post.id in removedIds }
        val items = removed.map { RemovedItem(it.id, it.feed, it.threadId) }
        emit { it.onDeleted(items) }
        if (!post.isMain) {
            val main = posts.getValue(post.thread)
            val target = post.replyTo?.let { posts[it] }?.takeIf { !it.deleted }
            val changed = listOfNotNull(main, target).map(::counts)
            emit { it.onCounts(changed) }
        }
        if (wasPinned) {
            val group = pinGroup()
            emit { it.onPins(group) }
        }
        BackendResult.Ok(Unit)
    }

    override fun markFeedSeen(player: UUID, feed: FeedKind, upTo: Instant) = answer {
        gate()?.let { return@answer it }
        if (linkedIdentity(player) == null) return@answer BackendResult.Ok(Unit)
        val key = accountOf(player) to feed
        val capped = minOf(upTo.truncatedTo(ChronoUnit.MILLIS), currentTime())
        val stored = feedSeen[key]
        if (stored == null || capped > stored) feedSeen[key] = capped
        BackendResult.Ok(Unit)
    }

    override fun markThreadSeen(player: UUID, threadId: UUID) = answer {
        gate()?.let { return@answer it }
        posts[threadId]?.takeIf { it.isMain && !it.deleted } ?: return@answer notFound()
        if (linkedIdentity(player) == null) return@answer BackendResult.Ok(Unit)
        threadSeen[accountOf(player) to threadId] = currentTime()
        BackendResult.Ok(Unit)
    }

    // ---- Notifications ----

    override fun pendingNotifications() = answer {
        gate()?.let { return@answer it }
        BackendResult.Ok(NotificationBatch(queue.take(Feed.MAX_NOTIFICATIONS), queue.size > Feed.MAX_NOTIFICATIONS))
    }

    override fun ackNotifications(ids: List<UUID>) = answer {
        gate()?.let { return@answer it }
        if (ids.size !in 1..Feed.MAX_NOTIFICATIONS) return@answer badRequest()
        val acknowledged = ids.toSet()
        queue.removeAll { it.id in acknowledged }
        BackendResult.Ok(Unit)
    }

    // ---- Announcers ----

    override fun setAnnouncer(target: UUID, announcer: Boolean, issuer: Issuer?) = answer {
        gate()?.let { return@answer it }
        if (issuer != null && !(linkedIdentity(issuer.uuid) != null && rightsOf(issuer.uuid).moderate)) {
            return@answer refused(403, "issuerNotAllowed")
        }
        val identity = linkedIdentity(target) ?: return@answer refused(404, "notLinked")
        if (moderates(target)) {
            return@answer if (announcer) BackendResult.Ok(AnnouncerState(target, identity.username, true, true)) else refused(409, "impliedByRole")
        }
        val changed = if (announcer) granted.add(accountOf(target)) else granted.remove(accountOf(target))
        if (changed) emit { it.onRights(listOf(target)) }
        BackendResult.Ok(AnnouncerState(target, identity.username, announcer, false))
    }

    override fun announcers() = answer {
        gate()?.let { return@answer it }
        val entries = granted.filter { !moderates(it) }.sortedBy { identityOf(it)?.username?.lowercase() }.mapNotNull { account ->
            val identity = identityOf(account) ?: return@mapNotNull null
            AnnouncerEntry(identity.realName, listOf(AnnouncerEntry.Account(account, identity.username)))
        }
        BackendResult.Ok(entries)
    }

    // ---- Seeding (SampleContent) ----

    /** A sample author: a linked account with the art direction's name, an operator or not whatever the server says. */
    internal fun addSampleAuthor(uuid: UUID, username: String, realName: String?, moderator: Boolean = false, announcer: Boolean = false) = locked {
        sampleIdentities[uuid] = Identity(username, realName)
        if (moderator) sampleModerators += uuid
        if (announcer) granted += uuid
    }

    /** Inserts a post as it would be after the fact, with no event, key or notification. Returns its ID. */
    internal fun insert(
        author: UUID,
        feed: FeedKind,
        text: String,
        createdAt: Instant,
        threadId: UUID? = null,
        replyTo: UUID? = null,
        anonymous: Boolean = false,
        pinnedAt: Instant? = null,
        baseLikes: Int = 0,
        baseReplies: Int = 0,
    ): UUID = locked {
        val name = identityOf(author)?.username ?: error("Unknown sample author $author")
        val post = Post(
            nextId("post"), feed, threadId, replyTo, text, createdAt.truncatedTo(ChronoUnit.MILLIS), anonymous, author, name,
            pinnedAt = pinnedAt?.truncatedTo(ChronoUnit.MILLIS), baseLikes = baseLikes, baseReplies = baseReplies,
        )
        posts[post.id] = post
        if (post.createdAt > lastIssued) lastIssued = post.createdAt
        post.id
    }

    internal fun insertLike(player: UUID, postId: UUID) = locked { likes.getOrPut(postId) { HashSet() } += accountOf(player) }

    // ---- The rules ----

    private fun stateOf(player: UUID): PlayerState {
        val identity = linkedIdentity(player)
            ?: return PlayerState(linked = false, banned = false, writing = writing, me = null, rights = Rights.NONE, unread = Unread.NONE)
        val rights = rightsOf(player)
        val me = Author(player, identity.username, identity.realName, rights.announce)
        return PlayerState(linked = true, banned = false, writing = writing, me = me, rights = rights, unread = unreadOf(player))
    }

    private fun identityOf(player: UUID): Identity? =
        sampleIdentities[player] ?: directory.username(player)?.let { Identity(it, null) }

    /** The identity of a linked player, or null for a guest (listed, or unknown to the server). */
    private fun linkedIdentity(player: UUID): Identity? =
        identityOf(player)?.takeIf { it.username.lowercase() !in guests }

    private fun moderates(player: UUID): Boolean =
        linkedIdentity(player) != null && (player in sampleModerators || directory.isOperator(player))

    private fun rightsOf(player: UUID): Rights {
        if (linkedIdentity(player) == null) return Rights.NONE
        val moderate = moderates(player)
        return Rights(write = true, announce = moderate || accountOf(player) in granted, moderate = moderate)
    }

    /** Each Minecraft account is its own LNB account here. */
    private fun accountOf(player: UUID): UUID = player

    /** The Minecraft accounts linked to [account]. */
    private fun accountsOf(account: UUID): List<UUID> = listOf(account)

    private fun unreadOf(player: UUID): Unread {
        val account = accountOf(player)
        fun unread(feed: FeedKind): Boolean {
            val seen = feedSeen[account to feed]
            return posts.values.any { it.isMain && it.feed == feed && !it.deleted && accountOf(it.author) != account && (seen == null || it.createdAt > seen) }
        }
        return Unread(unread(FeedKind.ANNOUNCEMENTS), unread(FeedKind.GLOBAL))
    }

    private fun authorOf(post: Post): Author {
        val identity = identityOf(post.author)
        return Author(post.author, post.authorName, identity?.realName, rightsOf(post.author).announce)
    }

    private fun displayName(post: Post): String = identityOf(post.author)?.realName ?: post.authorName

    private fun view(post: Post, viewer: UUID): PostView {
        val linked = linkedIdentity(viewer) != null
        val rights = rightsOf(viewer)
        val account = accountOf(viewer)
        val mine = linked && accountOf(post.author) == account
        val replyTo = post.replyTo?.let { posts[it] }?.let { target ->
            ReplyRef(target.id, target.anonymous, if (target.anonymous) null else displayName(target))
        }
        val answersMe = linked && !post.isMain && run {
            val target = post.replyTo?.let { posts[it] } ?: posts[post.thread]
            target != null && accountOf(target.author) == account
        }
        return PostView(
            id = post.id,
            feed = post.feed,
            threadId = post.threadId,
            replyTo = replyTo,
            text = post.text,
            createdAt = post.createdAt,
            anonymous = post.anonymous,
            author = if (post.anonymous && !mine && !rights.moderate) null else authorOf(post),
            likes = likesOf(post),
            replies = repliesOf(post),
            pinnedAt = post.pinnedAt,
            liked = linked && likes[post.id]?.contains(account) == true,
            mine = mine,
            canDelete = mine || rights.moderate,
            canPin = post.isMain && post.feed == FeedKind.ANNOUNCEMENTS && rights.announce,
            unseenReplies = if (mine) unseenReplies(post, account) else 0,
            answersMe = answersMe,
        )
    }

    /**
     * Replies that answer [post] of [account] (or, on a main post, that answer [account] anywhere in its thread), not
     * by [account], newer than its last opening of the thread.
     */
    private fun unseenReplies(post: Post, account: UUID): Int {
        val opened = threadSeen[account to post.thread]
        return posts.values.count { reply ->
            reply.threadId == post.thread && !reply.deleted && accountOf(reply.author) != account &&
                (opened == null || reply.createdAt > opened) &&
                if (post.isMain) {
                    reply.replyTo == null || posts[reply.replyTo]?.let { accountOf(it.author) == account } == true
                } else {
                    reply.replyTo == post.id
                }
        }
    }

    private fun likesOf(post: Post) = post.baseLikes + (likes[post.id]?.size ?: 0)

    private fun repliesOf(post: Post): Int = post.baseReplies + posts.values.count {
        !it.deleted && if (post.isMain) it.threadId == post.id else it.replyTo == post.id
    }

    private fun counts(post: Post) = CountUpdate(post.id, likesOf(post), repliesOf(post))

    private fun pinnedPosts(): List<Post> = posts.values.filter { it.pinnedAt != null && !it.deleted }.sortedByDescending { it.pinnedAt }

    private fun pinGroup(): List<Pin> = pinnedPosts().map { Pin(it.id, it.pinnedAt!!) }

    private fun notificationPost(post: Post) =
        NotificationPost(post.id, post.feed, post.threadId, post.text, post.anonymous, if (post.anonymous) null else authorOf(post))

    /** The idempotency check (`feed-mod.md` 6): after the flags, before everything else. Null for a new key. */
    private fun replay(key: UUID, fingerprint: Fingerprint, player: UUID): BackendResult<Created>? {
        val entry = keys[key] ?: return null
        val post = posts.getValue(entry.post)
        // The post went: the client stops retrying and clears the draft.
        if (post.deleted) return notFound()
        if (entry.fingerprint != fingerprint) return refused(409, "idempotencyMismatch")
        return BackendResult.Ok(Created(true, view(post, player)))
    }

    private fun checkText(cleaned: String): BackendResult<Nothing>? {
        val count = FeedText.count(cleaned)
        return when {
            count == 0 -> refused(400, "emptyText")
            count > Feed.MAX_TEXT -> refused(400, "tooLong")
            else -> null
        }
    }

    private fun limit(player: UUID, action: Action): BackendResult<Nothing>? {
        if (!rateLimits) return null
        val now = clock.instant()
        val times = actions[accountOf(player) to action] ?: return null
        var wait = Duration.ZERO
        for ((window, max) in action.windows) {
            val inWindow = times.filter { it > now.minus(window) }
            if (inWindow.size >= max) {
                val freeAt = inWindow.first().plus(window)
                if (Duration.between(now, freeAt) > wait) wait = Duration.between(now, freeAt)
            }
        }
        if (wait.isZero) return null
        val seconds = ((wait.toMillis() + 999) / 1000).toInt().coerceAtLeast(1)
        return BackendResult.Refused(429, "rateLimited", retryAfterSeconds = seconds)
    }

    private fun record(player: UUID, action: Action) {
        if (!rateLimits) return
        val now = clock.instant()
        val times = actions.getOrPut(accountOf(player) to action) { ArrayDeque() }
        times.addLast(now)
        val longest = action.windows.maxOf { it.first }
        while (times.isNotEmpty() && times.first() <= now.minus(longest)) times.removeFirst()
    }

    private fun gate(writes: Boolean = false): BackendResult<Nothing>? = when {
        offline -> BackendResult.Unavailable("memory backend offline (/feed dev offline)")
        disabled -> BackendResult.Disabled("feed")
        writes && !writing -> BackendResult.Disabled("feed.writing")
        else -> null
    }

    private fun refused(status: Int, problem: String) = BackendResult.Refused(status, problem)

    private fun notFound() = refused(404, "notFound")

    private fun badRequest() = BackendResult.Refused(400, null)

    private fun nextId(kind: String): UUID = UUID.nameUUIDFromBytes("$NAMESPACE:$kind:${counter++}".toByteArray())

    /** The clock, to the millisecond, and later than anything created before: so a fixed clock keeps an order. */
    private fun issueTime(): Instant {
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        lastIssued = if (now > lastIssued) now else lastIssued.plusMillis(1)
        return lastIssued
    }

    /** "Now" for capping: never before what was created, or a fixed clock could never catch up with its posts. */
    private fun currentTime(): Instant = maxOf(clock.instant().truncatedTo(ChronoUnit.MILLIS), lastIssued)

    // ---- Plumbing ----

    private val pendingEvents = ArrayList<(FeedEventListener) -> Unit>()

    private fun emit(event: (FeedEventListener) -> Unit) {
        pendingEvents += event
    }

    private inline fun <R> locked(block: () -> R): R = synchronized(this, block)

    /** Runs [call] under the lock, then hands the events it made to the listener, outside the lock, in order. */
    private fun <T> answer(call: () -> BackendResult<T>): CompletableFuture<BackendResult<T>> {
        val (result, events) = locked {
            pendingEvents.clear()
            val result = call()
            // A refusal changes nothing, whatever was emitted before it.
            val events = if (result is BackendResult.Ok) pendingEvents.toList() else emptyList()
            pendingEvents.clear()
            result to events
        }
        val target = listener
        if (target != null) for (event in events) event(target)
        return CompletableFuture.completedFuture(result)
    }

    /** A keyset cursor on `(createdAt, id)`, written `<epoch ms>:<uuid>`. */
    private data class Cursor(val createdAt: Instant, val id: UUID) {
        override fun toString() = "${createdAt.toEpochMilli()}:$id"

        /** After this cursor in newest-first order. */
        fun isAfterNewestFirst(time: Instant, other: UUID) = time < createdAt || (time == createdAt && other < id)

        /** After this cursor in oldest-first order. */
        fun isAfterOldestFirst(time: Instant, other: UUID) = time > createdAt || (time == createdAt && other > id)

        companion object {
            fun parse(text: String): Cursor? {
                if (text.length > Feed.MAX_CURSOR) return null
                val millis = text.substringBefore(':', "").toLongOrNull() ?: return null
                val id = runCatching { UUID.fromString(text.substringAfter(':')) }.getOrNull() ?: return null
                return Cursor(Instant.ofEpochMilli(millis), id)
            }
        }
    }

    private companion object {
        const val NAMESPACE = "lnbphone_feed:memory"
        private val NEWEST_FIRST = compareByDescending<Post> { it.createdAt }.thenByDescending { it.id }
        private val OLDEST_FIRST = compareBy<Post> { it.createdAt }.thenBy { it.id }
    }
}
