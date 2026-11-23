package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.EmptyState
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.SnackbarAction
import fr.bde_teknantes.lnb.phone.api.client.ui.SpinnerRow
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.DraftOutcome
import fr.bde_teknantes.lnb.phone.feed.client.FeedFormat
import fr.bde_teknantes.lnb.phone.feed.client.FeedNotice
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.FeedWriting
import fr.bde_teknantes.lnb.phone.feed.client.LikeAttempt
import fr.bde_teknantes.lnb.phone.feed.client.NewPosts
import fr.bde_teknantes.lnb.phone.feed.client.Rows
import fr.bde_teknantes.lnb.phone.feed.client.ThreadLayout
import fr.bde_teknantes.lnb.phone.feed.client.widgets.CardLook
import fr.bde_teknantes.lnb.phone.feed.client.widgets.FollowingSpacer
import fr.bde_teknantes.lnb.phone.feed.client.widgets.FrameHook
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCard
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCardActions
import fr.bde_teknantes.lnb.phone.feed.client.widgets.ReadOnlyStrip
import fr.bde_teknantes.lnb.phone.feed.client.widgets.ReplyComposer
import fr.bde_teknantes.lnb.phone.feed.client.widgets.RetryRow
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import java.time.ZoneId
import java.util.UUID

/**
 * A thread (art direction 9.7): the main post with its full timestamp, its replies oldest first and paged by 50, and
 * the reply composer docked at the bottom, or the read-only strip of 9.11 for whoever may not write. Live replies join
 * at the end, the thread is marked seen while it shows, and a post deleted while open goes back to the feed with
 * `Ce post a été supprimé`.
 *
 * Everything shown comes from [session] and the arguments, so a test fills a session by hand and plays the screen with
 * the phone's `PhoneTester`.
 *
 * @param expand Opened from a reply button: the composer expanded and focused.
 * @param landOn Opened from a reply's notification: scrolled so that this reply is fully visible, highlighted.
 */
class ThreadScreen(
    private val session: FeedSession,
    private val strings: FeedStrings,
    val threadId: UUID,
    private val expand: Boolean = false,
    landOn: UUID? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : KitScreen(), PostCardActions {
    private val thread = session.thread(threadId)
    private val draft = session.replyDraft(threadId)
    private val actions = PostActions(this, session, strings)
    private var landing: UUID? = landOn

    // First, so that the rows are in line with the session before the list lays them out in the same frame.
    private val hook = add(FrameHook { sync() })
    private val list = add(ScrollList(thread.scroll).at(0f, ThreadLayout.LIST_TOP, 256f, ThreadLayout.BOTTOM - ThreadLayout.LIST_TOP))
    private var block: EmptyState? = null
    private var blockDisabled: Boolean? = null
    private var blockRetrying = false
    val composer: ReplyComposer = add(ReplyComposer(draft, strings, ::targetName) { send() })
    private val strip = add(ReadOnlyStrip(""))
    private val header = add(Header(strings.get("thread.title")).at(0f, 0f))

    private var mainCard: PostCard? = null
    private val cards = HashMap<UUID, PostCard>()
    private val loadingRow = SpinnerRow()
    private val firstRetryRow = RetryRow(strings.get("error.retry")) { session.retryThread(threadId) }
    private val moreRetryRow = RetryRow(strings.get("error.retry")) { session.retryReplies(threadId) }
    private val noRepliesRow = MessageRow(strings.get("thread.no_replies")).apply { color = Colors.TEXT_2 }
    private val spacer = FollowingSpacer { ThreadLayout.spacer(dockHeight()) }

    private var synced = -1
    private var shownOnce = false
    private var focusComposer = false
    private var leaving = false
    private var connectionLostAt = Float.NaN

    // Replies seen at the last sync, and whether the thread then took live replies at its end: one that appears
    // afterwards just arrived, and gets the post highlight.
    private val known = HashSet<UUID>()
    private var openEnded = false

    init {
        // From a card or a reply button, the thread opens at its top; from a notification, at the reply.
        if (landOn == null) thread.scroll.offset = 0f
        composer.visible = false
        strip.visible = false
    }

    // ---- The screen's life ----

    override fun onScreenShown() {
        if (!shownOnce) {
            shownOnce = true
            // Opened from a notification, the feed under it hasn't been shown: the player becomes a watcher here.
            if (landing != null) session.open()
            // The composer shows once the main post is known: the focus goes to it then (sync).
            if (expand && session.writeBlock() == null) {
                composer.expand(focus = false)
                focusComposer = true
            }
        }
        session.showThread(threadId)
        session.loadThreadIfNeeded(threadId)
    }

    override fun onScreenHidden() {
        session.showThread(null)
    }

    // The session's timers run from the app, whatever screen shows: never session.tick() here.
    override fun onTick() {
        if (leaving) return
        session.loadThreadIfNeeded(threadId)
        val notice = session.takeNotice()
        // The player's own delete of the main post says `Post supprimé`, someone else's `Ce post a été supprimé`: both
        // set the thread's flag, so the notice is looked at first.
        if (notice is FeedNotice.Deleted && notice.postId == threadId) return leave(ownDelete = true)
        if (thread.deleted) {
            leave(ownDelete = actions.deletedHere(threadId))
            return
        }
        actions.tick()
        draft.takeOutcome()?.let(::outcome)
        notice?.let(::report)
    }

    /** Back while the composer is open closes it and keeps the draft (9.7). */
    override fun onBack(): Boolean {
        if (!composer.expanded) return false
        composer.collapse()
        return true
    }

    /** A reply's notification for this thread: it shows here, and the phone needs no banner over it. */
    override fun showsTarget(target: String): Boolean = (FeedTarget.parse(target) as? FeedTarget.Reply)?.threadId == threadId

    /**
     * The main post went (deleted here, by a moderator, or found gone): back to the feed (9.7, 9.9), with `Post
     * supprimé` when the player deleted it from here ([ownDelete]), `Ce post a été supprimé` otherwise. The snackbar is
     * carried over to the feed, so it shows there after the back slide.
     */
    private fun leave(ownDelete: Boolean) {
        if (leaving) return
        leaving = true
        showSnackbar(strings.get(if (ownDelete) "delete.post_done" else "thread.deleted"))
        phone.pop()
    }

    // ---- What the player does ----

    override fun open(post: PostView) {}

    override fun reply(post: PostView) {
        val block = session.writeBlock()
        if (block != null) return actions.blocked(block)
        draft.target = FeedWriting.targetOf(post)
        composer.expand(focus = true)
    }

    override fun like(post: PostView, card: PostCard) {
        when (session.toggleLike(post.id)) {
            LikeAttempt.SENT -> if (session.post(post.id)?.liked == true) {
                card.like.pop()
                PhoneSounds.like()
            } else {
                PhoneSounds.tap()
            }
            LikeAttempt.BLOCKED -> session.writeBlock()?.let(actions::blocked)
            LikeAttempt.IGNORED -> {}
        }
    }

    override fun pin(post: PostView) = actions.pin(post)

    override fun more(post: PostView, anchor: Widget) = actions.more(post, anchor)

    private fun send() {
        if (!session.sendReply(threadId)) session.writeBlock()?.let { showErrorSnackbar(strings.get(FeedWriting.snackbarKey(it))) }
    }

    private fun targetName(): String? = FeedWriting.targetName(draft.target, session.post(threadId))

    private fun outcome(outcome: DraftOutcome) {
        when (outcome) {
            is DraftOutcome.Sent -> {
                composer.collapse()
                PhoneSounds.sent()
                showSnackbar(strings.get("reply.sent"))
            }
            DraftOutcome.SendFailed -> showErrorSnackbar(strings.get("snackbar.send_failed"), SnackbarAction(strings.get("error.retry")) { send() })
            is DraftOutcome.TooFast -> showErrorSnackbar(strings.get("snackbar.too_fast", outcome.seconds))
            is DraftOutcome.Refused -> showErrorSnackbar(strings.get(FeedWriting.snackbarKey(outcome.block)))
            // The thread went: leave() says so.
            DraftOutcome.ThreadGone -> {}
        }
    }

    private fun report(notice: FeedNotice) {
        if (actions.report(notice)) return
        when (notice) {
            FeedNotice.ConnectionLost -> {
                showErrorSnackbar(strings.get("feed.connection_lost"), SnackbarAction(strings.get("error.retry")) { session.retryConnection() })
                connectionLostAt = now
            }
            FeedNotice.ConnectionBack -> if (now - connectionLostAt < CONNECTION_SNACKBAR_MS) {
                dismissSnackbar()
                connectionLostAt = Float.NaN
            }
            else -> {}
        }
    }

    // ---- Catching up with the session ----

    /** The dock's height now: the composer (animated), the strip, or none while the main post isn't known. */
    private fun dockHeight(): Float = when {
        composer.visible -> composer.preferredHeight
        strip.visible -> ThreadLayout.STRIP
        else -> 0f
    }

    /** How much of the list shows above the dock once it settles. */
    private fun visibleHeight(): Float = when {
        composer.visible -> ThreadLayout.visibleHeight(composer.targetHeight)
        strip.visible -> ThreadLayout.visibleHeight(ThreadLayout.STRIP)
        else -> list.height
    }

    private fun sync() {
        if (thread.deleted) return
        val main = session.post(threadId)
        val block = session.writeBlock()
        composer.visible = main != null && block == null
        strip.visible = main != null && block != null
        if (block != null) {
            strip.text = strings.get(FeedWriting.stripKey(block))
            if (composer.expanded) composer.collapse()
            focusComposer = false
        }
        if (focusComposer && composer.visible) {
            focusComposer = false
            focus(composer.area)
        }
        if (!thread.loadingFirst) blockRetrying = false
        val showBlock = main == null && (thread.firstError != null || blockRetrying)
        syncBlock(showBlock)
        list.visible = !showBlock
        if (session.revision != synced || cards.values.any { it.isCollapsed }) {
            synced = session.revision
            syncRows(main, showBlock)
        }
        afterSync()
    }

    /** The error block under the header (9.12): a thread that can't load with nothing of it known. */
    private fun syncBlock(show: Boolean) {
        val disabled = if (show) thread.firstError == Outcome.DISABLED || (thread.firstError == null && blockDisabled == true) else null
        if (disabled != blockDisabled || (show != (block != null))) {
            block?.let(::remove)
            block = null
            blockDisabled = disabled
            if (show) {
                val words = if (disabled == true) "feed.disabled" else "feed.unavailable"
                block = add(EmptyState(Icon.GLOBE, strings.get("$words.title")).apply {
                    text = strings.get("$words.text")
                    button = Button(strings.get("error.retry")) {
                        blockRetrying = true
                        session.retryThread(threadId)
                    }.apply {
                        kind = Button.Kind.SECONDARY
                        fixedWidth = FeedScreen.RETRY_WIDTH
                    }
                }.at(0f, ThreadLayout.LIST_TOP, 256f, ThreadLayout.BOTTOM - ThreadLayout.LIST_TOP))
            }
        }
        block?.button?.loading = blockRetrying && thread.loadingFirst
    }

    private fun syncRows(main: PostView?, showBlock: Boolean) {
        val desired = ArrayList<Widget>()
        val writes = session.writeBlock() == null
        if (main != null) {
            val card = mainCard ?: newCard(threadId).also { mainCard = it }
            card.look = CardLook(
                more = main.canDelete && writes,
                pin = main.canPin && writes,
                time = false,
                fullTime = FeedFormat.fullTime(main.createdAt, session.clock(), zone, strings),
                hover = false,
            )
            desired += card
        } else if (!showBlock) {
            desired += loadingRow
        }
        val replies = thread.replies
        val shown = replies.toHashSet()
        if (main != null) {
            if (!thread.loaded) {
                desired += if (thread.firstError != null) firstRetryRow else loadingRow
            } else {
                for (id in replies) {
                    val post = session.post(id) ?: continue
                    val card = cards.getOrPut(id) { newCard(id) }
                    card.look = CardLook(more = post.canDelete && writes, pin = false, replyTo = FeedWriting.replyToLine(post, strings))
                    desired += card
                }
                if (replies.isEmpty() && !thread.hasMore) desired += noRepliesRow
                if (thread.hasMore) desired += if (thread.moreFailed) moreRetryRow else loadingRow
            }
        }
        desired += spacer
        // Replies the player deleted here fold away where they stood; others' go at once.
        val folding = HashSet<Widget>()
        for ((id, card) in cards.entries.toList()) if (id !in shown) {
            if (actions.foldsAway(id) && !card.isCollapsed) {
                card.collapse()
                folding += card
            } else {
                cards.remove(id)
                actions.forget(id)
            }
        }
        val rows = Rows.keepInPlace(desired, list.children, folding)
        for (row in list.children.toList()) if (row !in rows) list.remove(row)
        rows.forEachIndexed { i, row ->
            if (list.children.getOrNull(i) !== row) {
                if (row.parent === list) list.remove(row)
                list.add(i, row)
            }
        }
        list.layoutRows()
        // Replies that just arrived at the end of a thread already open get the highlight.
        if (thread.loaded) {
            if (openEnded) for (id in replies) if (id !in known) cards[id]?.highlight()
            known.clear()
            known += replies
            openEnded = !thread.hasMore
        }
    }

    /** The rules that need the rows laid out: the landing reply, an own reply, highlights, the next page. */
    private fun afterSync() {
        if (!thread.loaded) return
        session.takeThreadReveal(threadId)?.let { reveal(it) }
        landing?.let { id ->
            when {
                cards[id]?.parent === list -> {
                    landing = null
                    reveal(id)
                    cards[id]?.highlight()
                }
                thread.hasMore && !thread.moreFailed -> session.loadMoreReplies(threadId)
                else -> landing = null
            }
        }
        for ((id, card) in cards) if (card.parent === list && session.takeHighlight(id)) card.highlight()
        mainCard?.let { if (session.takeHighlight(threadId)) it.highlight() }
        // The spinner row in view: the next 50 replies.
        if (thread.hasMore && loadingRow.parent === list && loadingRow.y < list.offset + visibleHeight()) session.loadMoreReplies(threadId)
    }

    /** Scrolls the least that shows the reply [id] fully above the dock. */
    private fun reveal(id: UUID) {
        val card = cards[id]?.takeIf { it.parent === list } ?: return
        list.layoutRows()
        list.scrollTo(NewPosts.revealOffset(card.y, card.height, list.offset, visibleHeight()))
    }

    private fun newCard(id: UUID) = PostCard({ session.post(id) }, strings, this).apply {
        time = { FeedFormat.relativeTime(it.createdAt, session.clock(), zone, strings) }
    }

    private companion object {
        /** A `Connexion perdue` snackbar, with its action, is gone after this. */
        const val CONNECTION_SNACKBAR_MS = 6300f
    }
}
