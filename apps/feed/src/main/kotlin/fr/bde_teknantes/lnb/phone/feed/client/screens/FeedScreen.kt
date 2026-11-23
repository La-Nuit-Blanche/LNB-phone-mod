package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.EmptyState
import fr.bde_teknantes.lnb.phone.api.client.ui.Fab
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.SkeletonPost
import fr.bde_teknantes.lnb.phone.api.client.ui.SnackbarAction
import fr.bde_teknantes.lnb.phone.api.client.ui.Spacer
import fr.bde_teknantes.lnb.phone.api.client.ui.SpinnerRow
import fr.bde_teknantes.lnb.phone.api.client.ui.Tabs
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFormat
import fr.bde_teknantes.lnb.phone.feed.client.FeedNotice
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.LikeAttempt
import fr.bde_teknantes.lnb.phone.feed.client.NewPosts
import fr.bde_teknantes.lnb.phone.feed.client.Place
import fr.bde_teknantes.lnb.phone.feed.client.WriteBlock
import fr.bde_teknantes.lnb.phone.feed.client.widgets.CardLook
import fr.bde_teknantes.lnb.phone.feed.client.widgets.NewPostsPill
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCard
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCardActions
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCardLayout
import fr.bde_teknantes.lnb.phone.feed.client.widgets.RetryRow
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import java.time.ZoneId
import java.util.UUID

/**
 * Feed's root screen (art direction 9.1 to 9.6, 9.11, 9.12): the Annonces and Global tabs with their unread dots, each
 * tab's list of post cards with paging, its empty state, its skeletons and its error block; the new-posts pill; the
 * compose button where the player may write; the snackbars of a lost connection and of refused actions.
 *
 * Everything shown comes from [session] and the arguments, so a test fills a session by hand and plays the screen with
 * the phone's `PhoneTester`.
 *
 * @param fromHome Opened from the home icon or an announcement's notification: the tab starts at its top.
 */
class FeedScreen(
    private val session: FeedSession,
    private val strings: FeedStrings,
    initialTab: FeedKind,
    fromHome: Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : KitScreen(), PostCardActions {
    /** The tab shown. */
    var tab: FeedKind = initialTab
        private set

    private val tabs = add(Tabs(listOf(strings.get("tab.announcements"), strings.get("tab.global"))).apply {
        selected = initialTab.ordinal
        onSelect = { switchTo(FeedKind.entries[it]) }
    }.at(0f, 0f))
    private val views = FeedKind.entries.associateWith { kind -> add(FeedTabView(kind).at(0f, TOP, 256f, HEIGHT)) }
    private val pill = add(NewPostsPill().apply { onClick = { releaseFromPill() } })
    private val fab = addFloating(Fab { compose(tab) })

    private var opened = false

    init {
        // The app is open from now until this root screen closes: the session's timers run all that time.
        session.appOpened(this)
        if (fromHome) session.feed(initialTab).scroll.offset = 0f
        for ((kind, view) in views) view.visible = kind == tab
    }

    // ---- The screen's life ----

    override fun onScreenShown() {
        session.show(tab)
        if (!opened) {
            opened = true
            session.open()
        }
        session.loadIfNeeded(tab)
    }

    override fun onScreenHidden() {
        session.show(null)
    }

    override fun onClosed() {
        session.appClosed(this)
    }

    override fun onTick() {
        session.loadIfNeeded(tab)
        if (session.feed(tab).loaded) session.markViewed(tab)
        session.takeNotice()?.let(::report)
    }

    /**
     * True for an announcement's notification when Annonces shows that announcement fully (`docs/feed-app.md` 6.4): no
     * banner over it then. A new announcement waiting under the pinned group, out of view, still gets its banner.
     */
    override fun showsTarget(target: String): Boolean {
        val post = FeedTarget.parse(target) as? FeedTarget.Post ?: return false
        return tab == FeedKind.ANNOUNCEMENTS && views.getValue(tab).showsFully(post.postId)
    }

    // ---- What the player does ----

    private fun switchTo(kind: FeedKind) {
        if (kind == tab) return
        tab = kind
        for ((k, view) in views) view.visible = k == kind
        session.show(kind)
        session.loadIfNeeded(kind)
    }

    /** The pill (art direction 9.5): the waiting posts go in, and the list scrolls (250 ms) to the first. */
    private fun releaseFromPill() = views.getValue(tab).releaseAndReveal()

    override fun open(post: PostView) = openThread(post, compose = false)

    override fun reply(post: PostView) = openThread(post, compose = session.writeBlock() == null)

    override fun like(post: PostView, card: PostCard) {
        when (session.toggleLike(post.id)) {
            LikeAttempt.SENT -> {
                if (session.post(post.id)?.liked == true) {
                    card.like.pop()
                    PhoneSounds.like()
                } else {
                    PhoneSounds.tap()
                }
            }
            // Guests, banned players, writing off (9.11): the heart stays, a plain snackbar says why, the error sound.
            LikeAttempt.BLOCKED -> {
                session.writeBlock()?.let { showSnackbar(blockText(it)) }
                PhoneSounds.error()
            }
            LikeAttempt.IGNORED -> {}
        }
    }

    override fun pin(post: PostView) = pinClicked(post)

    override fun more(post: PostView, anchor: Widget) = moreClicked(post, anchor)

    /**
     * Opens the thread of [post] (art direction 9.7): from the card with the composer collapsed, from its reply button
     * with the composer expanded and focused ([compose]; never for a guest). The thread screen is the next task's
     * (M3): until it exists, nothing opens.
     */
    @Suppress("UNUSED_PARAMETER")
    fun openThread(post: PostView, compose: Boolean) {}

    /**
     * Opens Compose with [destination] selected (art direction 9.8), from the compose button or the empty Global's
     * button. Compose is the next task's (M3): until it exists, nothing opens.
     */
    @Suppress("UNUSED_PARAMETER")
    fun compose(destination: FeedKind) {}

    /**
     * The pin button of [post] (art direction 9.4): `FeedSession.togglePin` is ready, the dialog for a fourth pin is
     * the next task's (M3). Until then, nothing happens.
     */
    @Suppress("UNUSED_PARAMETER")
    fun pinClicked(post: PostView) {}

    /**
     * The more button of [post] (art direction 9.9): its menu, the delete dialog and `FeedSession.delete` are the
     * next task's (M3). Until then, nothing opens.
     */
    @Suppress("UNUSED_PARAMETER")
    fun moreClicked(post: PostView, anchor: Widget) {}

    // ---- Snackbars ----

    private fun report(notice: FeedNotice) {
        when (notice) {
            FeedNotice.ConnectionLost -> session.connectionSnackbar =
                showErrorSnackbar(strings.get("feed.connection_lost"), SnackbarAction(strings.get("error.retry")) { session.retryConnection() })
            // Back with no message: that `Connexion perdue` goes if it still shows, wherever it was shown; any other stays.
            FeedNotice.ConnectionBack -> {
                session.connectionSnackbar?.dismiss()
                session.connectionSnackbar = null
            }
            FeedNotice.ActionFailed -> showErrorSnackbar(strings.get("snackbar.action_failed"))
            is FeedNotice.TooFast -> showErrorSnackbar(strings.get("snackbar.too_fast", notice.seconds))
            is FeedNotice.Refused -> showErrorSnackbar(blockText(notice.block))
            // Pins and deletes are started by the next task's screens (M3), which report these.
            is FeedNotice.PinDone, is FeedNotice.PinLimit, is FeedNotice.Deleted -> {}
        }
    }

    private fun blockText(block: WriteBlock) = strings.get(
        when (block) {
            WriteBlock.GUEST -> "guest.snackbar"
            WriteBlock.BANNED -> "snackbar.banned"
            WriteBlock.WRITING_OFF -> "snackbar.writing_off"
        },
    )

    // ---- A tab's content ----

    /** What a tab shows: skeletons while its first page loads, the error block, its empty state, or its list. */
    private enum class Mode { SKELETONS, BLOCK, EMPTY, LIST }

    /**
     * One tab's content, under the tabs: kept for the screen's life so that each card keeps its state (hover, a heart
     * popping, a highlight) while the lists change around it. Only the tab shown is updated and drawn.
     */
    private inner class FeedTabView(val kind: FeedKind) : Widget() {
        private val feed = session.feed(kind)
        val list: ScrollList = add(ScrollList(feed.scroll))
        private val skeletons = add(ScrollList())
        private var block: EmptyState? = null
        private var blockKey: Any? = null
        private val cards = HashMap<UUID, PostCard>()
        private val loadingRow = SpinnerRow()
        private val retryRow = RetryRow(strings.get("error.retry")) { session.retryMore(kind) }
        private val endRow = MessageRow(strings.get("feed.end"))
        private val spacer = Spacer(FAB_ROOM)
        private var synced = -1
        private var mode = Mode.SKELETONS

        init {
            repeat(3) { skeletons.add(SkeletonPost()) }
        }

        override fun layout() {
            list.at(0f, 0f, width, height)
            skeletons.at(0f, 0f, width, height)
            block?.at(0f, 0f, width, height)
        }

        /** Before each frame of the tab shown: catch up with the session, then the rules that need the layout. */
        override fun update() {
            if (session.revision != synced) sync(anchor = true)
            tabs.setUnread(FeedKind.ANNOUNCEMENTS.ordinal, session.isUnread(FeedKind.ANNOUNCEMENTS))
            tabs.setUnread(FeedKind.GLOBAL.ordinal, session.isUnread(FeedKind.GLOBAL))
            fab.visible = session.canCompose(kind)
            block?.button?.loading = feed.retrying
            if (mode != Mode.LIST && mode != Mode.EMPTY) {
                pill.show(false)
                return
            }
            newPosts()
            session.takeLanding(kind)?.let { reveal(it, fromTop = true) }
            session.takeReveal(kind)?.let { reveal(it, fromTop = true) }
            for ((id, card) in cards) if (session.wantsHighlight(id) && card.parent === list) {
                session.takeHighlight(id)
                card.highlight()
            }
            // The spinner row in view: the next page.
            if (loadingRow.parent === list && loadingRow.y < list.offset + list.height) session.loadMore(kind)
        }

        /** Waiting posts: in at once when their place is in view, otherwise the pill (art direction 9.5). */
        private fun newPosts() {
            val first = feed.waiting.firstOrNull()?.let(session::post)
            if (first == null) {
                pill.show(false)
                return
            }
            val place = NewPosts.place(placeTop(), heightOf(first), list.offset, list.height)
            if (place == Place.VISIBLE) {
                session.releaseWaiting(kind)
                sync(anchor = false)
                pill.show(false)
                return
            }
            val count = feed.waiting.size
            val id = if (kind == FeedKind.ANNOUNCEMENTS) "feed.new_announcements" else "feed.new_posts"
            pill.label = if (count == 1) strings.get("$id.one") else strings.get("$id.other", count)
            pill.arrow = place == Place.ABOVE
            pill.show(true)
        }

        /** The click on the pill: the posts go in where they belong, and the list scrolls to the first (250 ms). */
        fun releaseAndReveal() {
            val released = session.releaseWaiting(kind)
            pill.show(false)
            if (released.isEmpty()) return
            // Rows inserted above the view push it down: anchored, it stays, then scrolls to them.
            sync(anchor = true)
            val card = cards[released.first()] ?: return
            list.layoutRows()
            list.scrollTo(NewPosts.revealOffset(card.y, card.height, list.offset, list.height), animate = true)
        }

        /** Scrolls to the top, or further so that [id] is fully visible (an own post, a notification's target). */
        private fun reveal(id: UUID, fromTop: Boolean) {
            if (session.revision != synced) sync(anchor = false)
            val card = cards[id]?.takeIf { it.parent === list } ?: return
            list.layoutRows()
            val start = if (fromTop) 0f else list.offset
            list.scrollTo(NewPosts.revealOffset(card.y, card.height, start, list.height))
            if (session.takeHighlight(id)) card.highlight()
        }

        /** Whether the card of [id] is fully on screen. */
        fun showsFully(id: UUID): Boolean {
            if (!visible || mode != Mode.LIST) return false
            val card = cards[id]?.takeIf { it.parent === list } ?: return false
            return NewPosts.place(card.y, card.height, list.offset, list.height) == Place.VISIBLE && card.y >= list.offset - 0.5f
        }

        /** Where a new post goes: the top of Global, under the pinned group in Annonces. */
        private fun placeTop(): Float = feed.pinned.sumOf { id -> cards[id]?.heightFor(width)?.toDouble() ?: 0.0 }.toFloat()

        private fun heightOf(post: PostView): Float =
            PostCardLayout(post, lookOf(post), timeOf(post), TextMetrics, strings).height

        private fun lookOf(post: PostView): CardLook {
            val writes = session.writeBlock() == null
            // The pin button on an announcement's main post only, whatever the view says (art direction 9.3).
            val pinnable = post.canPin && post.feed == FeedKind.ANNOUNCEMENTS && !post.isReply
            return CardLook(more = post.canDelete && writes, pin = pinnable && writes)
        }

        private fun timeOf(post: PostView) = FeedFormat.relativeTime(post.createdAt, session.clock(), zone, strings)

        private fun modeNow(): Mode = when {
            !feed.loaded -> if (feed.firstError != null) Mode.BLOCK else Mode.SKELETONS
            feed.pinned.isEmpty() && feed.posts.isEmpty() && feed.waiting.isEmpty() -> Mode.EMPTY
            else -> Mode.LIST
        }

        /** Brings the widgets in line with the session. [anchor]: rows changing above the view don't move it. */
        fun sync(anchor: Boolean) {
            synced = session.revision
            mode = modeNow()
            skeletons.visible = mode == Mode.SKELETONS
            list.visible = mode == Mode.LIST
            syncBlock()
            syncRows(anchor && list.offset > 0.5f)
        }

        private fun syncBlock() {
            val key: Any? = when (mode) {
                Mode.BLOCK -> feed.firstError == Outcome.DISABLED
                Mode.EMPTY -> session.canCompose(kind)
                else -> null
            }
            val wanted = if (mode == Mode.BLOCK || mode == Mode.EMPTY) mode to key else null
            if (wanted == blockKey) return
            blockKey = wanted
            block?.let(::remove)
            block = when (mode) {
                Mode.BLOCK -> errorBlock(disabled = key == true)
                Mode.EMPTY -> emptyBlock()
                else -> null
            }?.let { add(it.at(0f, 0f, width, height)) }
        }

        /** The error state of 8.7 (9.12): unavailable, or switched off, with Retry. */
        private fun errorBlock(disabled: Boolean): EmptyState {
            val words = if (disabled) "feed.disabled" else "feed.unavailable"
            return EmptyState(Icon.GLOBE, strings.get("$words.title")).apply {
                text = strings.get("$words.text")
                button = Button(strings.get("error.retry")) { session.retryFirst(kind) }.apply {
                    kind = Button.Kind.SECONDARY
                    fixedWidth = RETRY_WIDTH
                }
            }
        }

        /** The empty Annonces (9.4) or Global (9.5, and 9.11 for whoever can't write). */
        private fun emptyBlock(): EmptyState = if (kind == FeedKind.ANNOUNCEMENTS) {
            EmptyState(Icon.MEGAPHONE, strings.get("empty.announcements.title")).apply { text = strings.get("empty.announcements.text") }
        } else {
            val writes = session.canCompose(FeedKind.GLOBAL)
            EmptyState(Icon.INBOX, strings.get("empty.global.title")).apply {
                text = strings.get(if (writes) "empty.global.text" else "empty.global.text_guest")
                if (writes) button = Button(strings.get("empty.global.button")) { compose(FeedKind.GLOBAL) }.apply { fixedWidth = WRITE_WIDTH }
            }
        }

        private fun syncRows(anchor: Boolean) {
            if (mode != Mode.LIST) return
            val ids = feed.pinned + feed.posts
            val shown = ids.toHashSet()
            for (id in cards.keys.toList()) if (id !in shown) cards.remove(id)?.let(list::remove)
            val desired = ArrayList<Widget>(ids.size + 2)
            for (id in ids) {
                val card = cards.getOrPut(id) { newCard(id) }
                session.post(id)?.let { card.look = lookOf(it) }
                desired += card
            }
            desired += when {
                !feed.hasMore -> endRow
                feed.moreFailed -> retryRow
                else -> loadingRow
            }
            desired += spacer
            // The first row in view keeps its place on screen while rows change above it.
            val kept = if (anchor) list.children.firstOrNull { it.y + it.height > list.offset } else null
            val keptAt = kept?.let { it.y - list.offset }
            for (row in list.children.toList()) if (row !in desired) list.remove(row)
            desired.forEachIndexed { i, row ->
                if (list.children.getOrNull(i) !== row) {
                    if (row.parent === list) list.remove(row)
                    list.add(i, row)
                }
            }
            list.layoutRows()
            if (kept != null && keptAt != null && kept.parent === list) list.scrollTo(kept.y - keptAt)
        }

        private fun newCard(id: UUID) = PostCard({ session.post(id) }, strings, this@FeedScreen).apply { time = ::timeOf }
    }

    companion object {
        /** The content area, under the tabs. */
        const val TOP = 44f
        const val HEIGHT = 326f

        /** Empty space after the end row, so the compose button never covers an action row (9.5). */
        const val FAB_ROOM = 72f
        const val RETRY_WIDTH = 104f
        const val WRITE_WIDTH = 136f
    }
}
