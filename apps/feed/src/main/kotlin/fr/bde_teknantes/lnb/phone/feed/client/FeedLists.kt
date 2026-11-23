package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollState
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import java.util.UUID

/**
 * One feed as the client holds it (`docs/feed-app.md` 8): IDs only, the views being in [FeedSession.post]. Read by
 * the screens; changed by [FeedSession] alone.
 */
class FeedList internal constructor(val kind: FeedKind) {
    internal val pinnedIds = ArrayList<UUID>()
    internal val postIds = ArrayList<UUID>()
    internal val waitingIds = ArrayList<UUID>()

    /** The pinned group, most recent pin first (Announcements only). */
    val pinned: List<UUID> get() = pinnedIds

    /** The other posts shown, newest first. */
    val posts: List<UUID> get() = postIds

    /** Live posts not shown yet, newest first: the pill's count (art direction 9.5). */
    val waiting: List<UUID> get() = waitingIds

    /** Whether a first page arrived: the list shows, or its empty state. */
    var loaded = false
        internal set

    /** Whether older posts can be loaded: the spinner row at the end, else "You're all caught up". */
    var hasMore = false
        internal set

    /** The cursor of the next page; null with [hasMore] means "from the start again" (after the list was trimmed). */
    internal var next: String? = null

    var loadingFirst = false
        internal set

    var loadingMore = false
        internal set

    /** Why the last first page failed while nothing was loaded: the unavailable or switched-off block. */
    var firstError: Outcome? = null
        internal set

    /** Older posts couldn't be loaded: the retry row (art direction 9.12). */
    var moreFailed = false
        internal set

    /** What is held may be out of date (a `reset`): reloaded and merged when shown. */
    var stale = false
        internal set

    /** The error block's Retry is waiting for the answer to `open`. */
    var retrying = false
        internal set

    /** Where the list is scrolled, kept across screens. */
    val scroll = ScrollState()

    /**
     * The post at the top of the view, kept up to date by the screen that shows the list: trimming the list never drops
     * it, nor a page under it, so the player finds their place again.
     */
    internal var shownTop: UUID? = null

    /**
     * A first page that didn't reach what is held (more than a page posted while the list was stale) waits behind the
     * pill, and when it is let in the list starts again from it, going on with this cursor ([FeedSession.releaseWaiting]).
     */
    internal var restartPending = false
    internal var restartNext: String? = null

    /** For each post held, the cursor its page was loaded with, to load it again once trimmed (null: first page, live). */
    internal val loadedWith = HashMap<UUID, String?>()

    /** The tick before which a failed or refused first page isn't asked again. */
    internal var reloadNotBefore = 0

    /** The tick before which a next page the server refused as too frequent isn't asked again. */
    internal var moreNotBefore = 0

    /** Every post this list refers to. */
    internal fun allIds(): Sequence<UUID> = pinnedIds.asSequence() + postIds.asSequence() + waitingIds.asSequence()

    internal fun holds(id: UUID) = id in pinnedIds || id in postIds || id in waitingIds

    internal fun removeId(id: UUID): Boolean {
        loadedWith.remove(id)
        return pinnedIds.remove(id) or postIds.remove(id) or waitingIds.remove(id)
    }

    internal fun reset() {
        pinnedIds.clear()
        postIds.clear()
        waitingIds.clear()
        loadedWith.clear()
        loaded = false
        hasMore = false
        next = null
        loadingFirst = false
        loadingMore = false
        firstError = null
        moreFailed = false
        stale = false
        retrying = false
        reloadNotBefore = 0
        moreNotBefore = 0
        shownTop = null
        restartPending = false
        restartNext = null
        scroll.offset = 0f
    }
}

/**
 * One thread as the client holds it: its main post's ID is [id], its replies oldest first. Read by the thread screen;
 * changed by [FeedSession] alone.
 */
class ThreadState internal constructor(val id: UUID) {
    internal val replyIds = ArrayList<UUID>()

    /** The replies loaded, oldest first. */
    val replies: List<UUID> get() = replyIds

    internal val aheadIds = LinkedHashSet<UUID>()

    /**
     * The player's own replies shown at the end before the pages that come before them were loaded (art direction
     * 9.7): the list has a gap before them, which the screen fills by loading those pages.
     */
    val ahead: Set<UUID> get() = aheadIds

    /** Whether the main post and a first page of replies arrived. */
    var loaded = false
        internal set

    var hasMore = false
        internal set

    internal var next: String? = null

    var loadingFirst = false
        internal set

    var loadingMore = false
        internal set

    /** Why the first page failed while nothing was loaded: the error block under the header. */
    var firstError: Outcome? = null
        internal set

    /** Later replies couldn't be loaded: the retry row. */
    var moreFailed = false
        internal set

    var stale = false
        internal set

    /** The main post was deleted (a `removed`, or `NOT_FOUND`): the screen goes back with "This post was deleted". */
    var deleted = false
        internal set

    val scroll = ScrollState()

    internal var reloadNotBefore = 0
    internal var moreNotBefore = 0
}
