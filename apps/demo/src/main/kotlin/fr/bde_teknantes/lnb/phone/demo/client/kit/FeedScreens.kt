package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.CharacterCounter
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Dialog
import fr.bde_teknantes.lnb.phone.api.client.ui.EmptyState
import fr.bde_teknantes.lnb.phone.api.client.ui.Fab
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconButton
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
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
import fr.bde_teknantes.lnb.phone.api.client.ui.Toggle
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.api.client.ui.atRight

/** What a Feed list shows. */
enum class FeedContent { LOADING, LOADED, EMPTY, ERROR, ERROR_RETRYING }

/**
 * Feed's two tabs (art direction 9.4 and 9.5) with sample posts, built from the kit to check that a Feed screen is
 * short to write. [scroll] is kept by the caller, as Feed would keep it for the session.
 */
class FeedScreen(
    private var tab: Int = 0,
    private val content: FeedContent = FeedContent.LOADED,
    private val viewerCanPin: Boolean = false,
    private val viewerIsAnnouncer: Boolean = false,
    private val scroll: ScrollState = ScrollState(),
    private val endOfList: Boolean = false,
    private val viewerIsOperator: Boolean = false,
) : KitScreen() {
    private val tabs = add(Tabs(listOf(tr("tab.announcements"), tr("tab.global")), tab) { switchTo(it) }.at(0f, 0f))
    private val list = add(ScrollList(scroll).at(0f, 44f, 256f, 326f))
    private var empty: Widget? = null
    private val fab = add(Fab { phone.push(ComposeScreen(announcer = viewerIsAnnouncer, destination = tab)) })

    /** The cards shown, for capture scripts. */
    val cards = ArrayList<PostCard>()

    init {
        fill()
    }

    private fun switchTo(index: Int) {
        tab = index
        scroll.offset = 0f
        fill()
    }

    private fun fill() {
        list.removeAll()
        cards.clear()
        empty?.let(::remove)
        empty = null
        tabs.setUnread(1, tab == 0)
        fab.visible = tab == 1 || viewerIsAnnouncer
        when (content) {
            FeedContent.LOADING -> repeat(3) { list.add(SkeletonPost()) }
            FeedContent.EMPTY -> empty = add(
                if (tab == 1) {
                    EmptyState(Icon.INBOX, tr("empty.global.title"), tr("empty.global.text"), Button(tr("empty.global.button"), fixedWidth = 136f))
                } else {
                    EmptyState(Icon.MEGAPHONE, tr("empty.announcements.title"), tr("empty.announcements.text"))
                }.at(0f, 44f, 256f, 326f),
            )
            FeedContent.ERROR, FeedContent.ERROR_RETRYING -> {
                val retry = Button(tr("error.retry"), Button.Kind.SECONDARY, 104f)
                retry.loading = content == FeedContent.ERROR_RETRYING
                empty = add(EmptyState(Icon.GLOBE, tr("feed.unavailable.title"), tr("feed.unavailable.text"), retry).at(0f, 44f, 256f, 326f))
            }
            FeedContent.LOADED -> {
                val posts = if (tab == 0) FeedSample.announcements(viewerCanPin) else FeedSample.global(viewerIsOperator)
                for (post in posts) {
                    val card = PostCard(post) {}
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
        // The FAB floats over everything: keep it last.
        remove(fab)
        add(fab)
    }

    private fun confirmDelete(card: PostCard) {
        showDialog(
            Dialog(tr("dialog.delete_post.title"), tr("dialog.delete_post.text"), tr("dialog.cancel"), tr("dialog.delete.confirm"), danger = true) { dialog ->
                dialog.close()
                card.collapse()
                showSnackbar(tr("delete.post_done"))
            },
        )
    }

    /** For the gallery: the pin-limit dialog, its confirm button loading. */
    fun showPinLimitDialog(loading: Boolean) {
        val dialog = Dialog(tr("dialog.pin_limit.title"), tr("dialog.pin_limit.text"), tr("dialog.cancel"), tr("dialog.pin_limit.confirm")) {}
        showDialog(dialog)
        if (loading) dialog.confirm()
    }

    /** For the gallery: the delete dialog of post [index]. */
    fun showDeleteDialog(index: Int) = confirmDelete(cards[index])

    /** For the gallery: the "more" menu of post [index]. */
    fun openMenu(index: Int) {
        list.layoutRows()
        cards[index].more?.click()
    }

    /** For the gallery: a snackbar, plain or as an error with Retry. */
    fun snackbar(error: Boolean) {
        if (error) showSnackbar(tr("feed.unavailable.title"), SnackbarAction(tr("error.retry")) {}, error = true) else showSnackbar(tr("delete.post_done"))
    }

    /** For the gallery: scrolls so that post [index] is at the top. */
    fun scrollToCard(index: Int) {
        list.layoutRows()
        list.scrollTo(cards[index].y)
    }

    /** For the gallery: scrolls the list. */
    fun scrollTo(offset: Float) = list.scrollTo(offset)
}

/** What a Compose draft holds: kept by the app, so closing Compose keeps it (9.8). */
class ComposeDraft {
    val text = TextState()
    var anonymous = false
    var destination = 0
}

/** Compose (art direction 9.8), from the kit. */
class ComposeScreen(
    private val announcer: Boolean = false,
    destination: Int = 1,
    private val draft: ComposeDraft = ComposeDraft().also { it.destination = destination },
) : KitScreen() {
    private val author = if (announcer) FeedSample.KAEL else FeedSample.CAMILLE
    private val close = IconButton(Icon.CLOSE) { phone.pop() }
    private val publish = Button(tr("compose.post"), fixedWidth = 88f) { send() }
    private val area = TextArea(draft.text, tr("compose.placeholder"), limit = LIMIT)
    private val toggle = Toggle(draft.anonymous) { draft.anonymous = it }
    private val anonRow = Clickable { toggle.toggle() }
    private val destinationControl = SegmentedControl(listOf(tr("tab.announcements"), tr("tab.global")), draft.destination) {
        draft.destination = it
        if (it == 0) {
            toggle.checked = false
            draft.anonymous = false
        }
    }

    init {
        add(Header(null, leading = close, trailing = publish).at(0f, 0f))
        add(AuthorRow().at(0f, 52f, 256f, 34f))
        val areaHeight = if (announcer) 152f else 196f
        add(area.at(12f, 96f, 232f, areaHeight))
        anonRow.add(Label(tr("compose.anonymous"), TextStyle.BODY_STRONG).at(0f, 4f, 184f, 18f))
        anonRow.add(Label(tr("compose.anonymous.help"), TextStyle.CAPTION, Colors.TEXT_2).at(0f, 22f, 184f, 16f))
        anonRow.add(toggle.atRight(232f, 12f))
        add(anonRow.at(12f, 96f + areaHeight + 12f, 232f, 44f))
        if (announcer) add(destinationControl.at(12f, 316f))
        area.onChange = { update() }
        update()
    }

    override fun onShown() {
        focus(area)
    }

    private fun update() {
        publish.enabled = CharacterCounter.canSend(draft.text.text, LIMIT)
        anonRow.visible = !announcer || draft.destination == 1
    }

    override fun onTick() = update()

    /** For the gallery: the sending state. */
    fun send() {
        publish.loading = true
        area.sending = true
        toggle.enabled = false
        destinationControl.enabled = false
        close.enabled = false
    }

    /** For the gallery: focus the text area as on open, without the phone. */
    fun focusText() = focus(area)

    fun setText(text: String) {
        draft.text.text = text
        update()
    }

    fun setAnonymous(on: Boolean) {
        toggle.checked = on
        draft.anonymous = on
    }

    /** Avatar, display name and handle, or the anonymous preview. */
    private inner class AuthorRow : Widget() {
        override fun render(canvas: fr.bde_teknantes.lnb.phone.api.client.AppCanvas) {
            if (draft.anonymous) {
                Avatar.paint(canvas, 12f, 0f, null)
                canvas.drawText(tr("post.anonymous"), 56f, 0f, TextStyle.TITLE_M, Colors.TEXT_2)
                canvas.drawText(tr("compose.anon_sub"), 56f, 18f, TextStyle.CAPTION, Colors.TEXT_2)
            } else {
                Avatar.paint(canvas, 12f, 0f, author.skin())
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
