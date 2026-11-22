package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Dialog
import fr.bde_teknantes.lnb.phone.api.client.ui.EmptyState
import fr.bde_teknantes.lnb.phone.api.client.ui.Fab
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconButton
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollState
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.SkeletonPost
import fr.bde_teknantes.lnb.phone.api.client.ui.SnackbarAction
import fr.bde_teknantes.lnb.phone.api.client.ui.Spacer
import fr.bde_teknantes.lnb.phone.api.client.ui.SpinnerRow
import fr.bde_teknantes.lnb.phone.api.client.ui.Tabs
import fr.bde_teknantes.lnb.phone.api.client.ui.TextArea
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.ToggleRow
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at

/** What a Feed list shows. */
enum class FeedContent { LOADING, LOADED, EMPTY, ERROR, ERROR_RETRYING }

/** What a sample Feed keeps for the session, as Feed would: the tab, each tab's scroll, the Compose draft. */
class FeedSession {
    var tab = 0
    val scrolls = listOf(ScrollState(), ScrollState())
    val draft = ComposeDraft()
}

/**
 * Feed's two tabs (art direction 9.4 and 9.5) with sample posts, built from the kit to check that a Feed screen is
 * short to write. What lasts (the tab, the scroll) lives in a [FeedSession] kept by the caller.
 */
class FeedScreen(
    private val session: FeedSession = FeedSession(),
    private val content: FeedContent = FeedContent.LOADED,
    private val viewerCanPin: Boolean = false,
    private val viewerIsAnnouncer: Boolean = false,
    private val endOfList: Boolean = false,
    private val viewerIsOperator: Boolean = false,
) : KitScreen() {
    private val tabs = add(Tabs(listOf(tr("tab.announcements"), tr("tab.global"))).apply {
        selected = session.tab
        onSelect = { switchTo(it) }
    }.at(0f, 0f))
    private var list = add(ScrollList(session.scrolls[session.tab]).at(0f, 44f, 256f, 326f))
    private var empty: Widget? = null
    private val fab = addFloating(Fab { phone.push(ComposeScreen(session.draft, announcer = viewerIsAnnouncer, destination = session.tab)) })

    /** The cards shown, for capture scripts. */
    val cards = ArrayList<PostCard>()

    init {
        fill()
    }

    private fun switchTo(index: Int) {
        session.tab = index
        fill()
    }

    private fun fill() {
        remove(list)
        list = add(ScrollList(session.scrolls[session.tab]).at(0f, 44f, 256f, 326f))
        cards.clear()
        empty?.let(::remove)
        empty = null
        val tab = session.tab
        tabs.setUnread(1, tab == 0)
        fab.visible = tab == 1 || viewerIsAnnouncer
        when (content) {
            FeedContent.LOADING -> repeat(3) { list.add(SkeletonPost()) }
            FeedContent.EMPTY -> empty = add(
                if (tab == 1) {
                    EmptyState(Icon.INBOX, tr("empty.global.title")).apply {
                        text = tr("empty.global.text")
                        button = Button(tr("empty.global.button")) { fab.click() }.apply { fixedWidth = 136f }
                    }
                } else {
                    EmptyState(Icon.MEGAPHONE, tr("empty.announcements.title")).apply { text = tr("empty.announcements.text") }
                }.at(0f, 44f, 256f, 326f),
            )
            FeedContent.ERROR, FeedContent.ERROR_RETRYING -> {
                val retry = Button(tr("error.retry")).apply {
                    kind = Button.Kind.SECONDARY
                    fixedWidth = 104f
                    loading = content == FeedContent.ERROR_RETRYING
                }
                retry.onClick = { retry.loading = true }
                empty = add(EmptyState(Icon.GLOBE, tr("feed.unavailable.title")).apply {
                    text = tr("feed.unavailable.text")
                    button = retry
                }.at(0f, 44f, 256f, 326f))
            }
            FeedContent.LOADED -> {
                val posts = if (tab == 0) FeedSample.announcements(viewerCanPin) else FeedSample.global(viewerIsOperator)
                for (post in posts) {
                    val card = PostCard(post)
                    card.onClick = { phone.push(ThreadScreen(post)) }
                    card.onDelete = { confirmDelete(card) }
                    cards += card
                    list.add(card)
                }
                if (tab == 1) {
                    list.add(if (endOfList) MessageRow(tr("feed.end")) else SpinnerRow())
                    list.add(Spacer(72f))
                }
            }
        }
    }

    private fun confirmDelete(card: PostCard) {
        showDialog(Dialog(tr("dialog.delete_post.title"), tr("dialog.cancel"), tr("dialog.delete.confirm")).apply {
            text = tr("dialog.delete_post.text")
            danger = true
            onConfirm = { dialog ->
                dialog.close()
                card.collapse()
                showSnackbar(tr("delete.post_done"))
            }
        })
    }

    /** For the gallery: the pin-limit dialog, its confirm button loading. */
    fun showPinLimitDialog(loading: Boolean) {
        val dialog = Dialog(tr("dialog.pin_limit.title"), tr("dialog.cancel"), tr("dialog.pin_limit.confirm")).apply {
            text = tr("dialog.pin_limit.text")
            onConfirm = {}
        }
        showDialog(dialog)
        if (loading) dialog.confirm()
    }

    /** For the gallery: the delete dialog of post [index]. */
    fun showDeleteDialog(index: Int) = confirmDelete(cards[index])

    /** For the gallery: the "more" menu of post [index]. */
    fun openMenu(index: Int) = cards[index].more?.click()

    /** For the gallery: a snackbar, plain or as an error with Retry. */
    fun snackbar(error: Boolean) {
        if (error) showErrorSnackbar(tr("feed.unavailable.title"), SnackbarAction(tr("error.retry")) {}) else showSnackbar(tr("delete.post_done"))
    }

    /** For the gallery: scrolls so that post [index] is at the top. */
    fun scrollToCard(index: Int) = list.scrollTo(cards[index].y)

    /** For the gallery: scrolls the list. */
    fun scrollTo(offset: Float) = list.scrollTo(offset)
}

/** A thread opened from a card: the post on its own, for the sample (the real thread is Feed's, 9.7). */
class ThreadScreen(post: Post) : KitScreen() {
    init {
        add(Header(tr("thread.title")).at(0f, 0f))
        val list = add(ScrollList().at(0f, 44f, 256f, 326f))
        list.add(PostCard(post))
        list.add(MessageRow(tr("thread.no_replies")).apply { color = Colors.TEXT_2 })
    }
}

/** What a Compose draft holds: kept by the app, so closing Compose keeps it (9.8). */
class ComposeDraft {
    val text = TextState()
    var anonymous = false
    var destination = 1
}

/** Compose (art direction 9.8), from the kit. Sending succeeds after a second, back on the feed with "Posted". */
class ComposeScreen(
    private val draft: ComposeDraft = ComposeDraft(),
    private val announcer: Boolean = false,
    destination: Int = draft.destination,
) : KitScreen() {
    private val author = if (announcer) FeedSample.KAEL else FeedSample.CAMILLE
    private val close = IconButton(Icon.CLOSE) { phone.pop() }
    private val publish = Button(tr("compose.post")) { send() }.apply { fixedWidth = 88f }
    private val area = TextArea(draft.text).apply {
        placeholder = tr("compose.placeholder")
        limit = LIMIT
    }
    private val anonymous = ToggleRow(tr("compose.anonymous")).apply {
        helper = tr("compose.anonymous.help")
        toggle.checked = draft.anonymous
        toggle.onChange = { draft.anonymous = it }
    }
    private val destinationControl = SegmentedControl(listOf(tr("tab.announcements"), tr("tab.global"))).apply {
        selected = destination
        onSelect = {
            draft.destination = it
            if (it == 0) {
                anonymous.toggle.checked = false
                draft.anonymous = false
            }
            refresh()
        }
    }
    private var sendingFor = -1

    init {
        draft.destination = destination
        add(Header(null).apply {
            leading = close
            trailing = publish
        }.at(0f, 0f))
        add(AuthorRow().at(0f, 52f, 256f, 34f))
        val areaHeight = if (announcer) 152f else 196f
        add(area.at(12f, 96f, 232f, areaHeight))
        add(anonymous.at(12f, 96f + areaHeight + 12f, 232f, 44f))
        if (announcer) add(destinationControl.at(12f, 316f))
        area.onChange = { refresh() }
        area.onSubmit = { publish.click() }
        refresh()
    }

    override fun onScreenShown() {
        focus(area)
    }

    private fun refresh() {
        publish.enabled = TextLimits.canSend(draft.text.text, LIMIT)
        anonymous.visible = !announcer || draft.destination == 1
    }

    override fun onTick() {
        if (sendingFor < 0) return
        // The sample's "server" answers after a second.
        if (++sendingFor >= 20) {
            sendingFor = -1
            draft.text.clear()
            draft.anonymous = false
            PhoneSounds.sent()
            showSnackbar(tr("compose.posted"))
            phone.pop()
        }
    }

    /** The sending state (9.8): Publier loading, the text area sending, the other controls disabled. */
    fun send() {
        publish.loading = true
        area.sending = true
        anonymous.enabled = false
        destinationControl.enabled = false
        close.enabled = false
        sendingFor = 0
    }

    /** For the gallery: stays in the sending state instead of answering. */
    fun sendForever() {
        send()
        sendingFor = -1
    }

    fun setText(text: String) {
        draft.text.text = text
        refresh()
    }

    fun setAnonymous(on: Boolean) {
        anonymous.toggle.checked = on
        draft.anonymous = on
    }

    /** Avatar, display name and handle, or the anonymous preview. */
    private inner class AuthorRow : Widget() {
        override fun render(canvas: AppCanvas) {
            if (draft.anonymous) {
                Avatar.paintSkin(canvas, 12f, 0f, Avatar.Size.MEDIUM, null)
                canvas.drawText(tr("post.anonymous"), 56f, 0f, TextStyle.TITLE_M, Colors.TEXT_2)
                canvas.drawText(tr("compose.anon_sub"), 56f, 18f, TextStyle.CAPTION, Colors.TEXT_2)
            } else {
                Avatar.paintSkin(canvas, 12f, 0f, Avatar.Size.MEDIUM, author.skin())
                val handle = author.handle
                canvas.drawText(author.displayName, 56f, if (handle == null) 7f else 0f, TextStyle.TITLE_M, Colors.TEXT_1)
                handle?.let { canvas.drawText(it, 56f, 18f, TextStyle.CAPTION, Colors.TEXT_2) }
            }
        }
    }

    private companion object {
        const val LIMIT = 280
    }
}
