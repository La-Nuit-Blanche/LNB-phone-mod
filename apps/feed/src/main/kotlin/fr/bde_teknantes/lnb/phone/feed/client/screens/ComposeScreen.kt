package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconButton
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.LabeledToggle
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.SnackbarAction
import fr.bde_teknantes.lnb.phone.api.client.ui.TextArea
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.client.DraftOutcome
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.FeedWriting
import fr.bde_teknantes.lnb.phone.feed.client.widgets.FrameHook
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind

/**
 * Compose (art direction 9.8): who the post will show as, the text area with its counter, `Publier anonymement`, and
 * for announcers the destination, preselected from the button that opened it. Publier is disabled while the text
 * can't be sent ([FeedWriting.canSend]); Ctrl+Enter publishes. The draft is the session's: the close button goes back
 * and keeps it (text, toggle, destination), and a failure keeps it with its key for Retry.
 *
 * Published, it goes back to the feed, which shows the post's tab ([onPosted]) with the post highlighted, and says
 * `Publié`.
 *
 * @param destination The tab whose button opened Compose; only announcers can choose Announcements.
 * @param onPosted Called with the published post's tab, just before going back.
 */
class ComposeScreen(
    private val session: FeedSession,
    private val strings: FeedStrings,
    destination: FeedKind,
    private val onPosted: (FeedKind) -> Unit = {},
) : KitScreen() {
    private val draft = session.compose

    /** How the session's notices are told here, as on the feed and the thread. */
    private val actions = PostActions(this, session, strings)

    /**
     * Announcers choose where it goes; everyone else posts on Global. An announcer who loses the right while Compose is
     * open falls back to Global: the destination control goes, the text and the toggle stay.
     */
    var announcer: Boolean = session.canCompose(FeedKind.ANNOUNCEMENTS)
        private set

    private val close = IconButton(Icon.CLOSE) { phone.pop() }
    private val publish = Button(strings.get("compose.post")) { send() }.apply { fixedWidth = PUBLISH_WIDTH }
    private val areaHeight = if (announcer) AREA_ANNOUNCER else AREA_PLAYER

    val area: TextArea = TextArea(draft.text).apply {
        placeholder = strings.get("compose.placeholder")
        limit = Feed.MAX_TEXT
        onSubmit = { publish.click() }
    }
    private val anonymous = LabeledToggle(strings.get("compose.anonymous")).apply {
        helper = strings.get("compose.anonymous.help")
        toggle.checked = draft.anonymous
        toggle.onChange = { draft.anonymous = it }
    }
    private val destinationControl = SegmentedControl(listOf(strings.get("tab.announcements"), strings.get("tab.global"))).apply {
        // Changing the destination leaves the anonymous toggle as it is (9.8).
        onSelect = { draft.destination = FeedKind.entries[it] }
    }

    init {
        // An answer that came while no Compose was open (the player left Feed while it was sent) was for a screen that
        // is gone: this one wasn't waiting for it, and doesn't tell it.
        draft.takeOutcome()
        // A draft already written keeps its destination; a fresh one takes the tab whose button opened Compose (9.8).
        draft.destination = FeedWriting.destinationOnOpen(draft.text.text, draft.anonymous, draft.destination, destination, announcer)
        destinationControl.selected = draft.destination.ordinal
        add(FrameHook { refresh() })
        add(Header(null).apply {
            leading = close
            trailing = publish
        }.at(0f, 0f))
        add(AuthorRow().at(0f, AUTHOR_Y, 256f, 34f))
        add(area.at(12f, AREA_Y, 232f, areaHeight))
        add(anonymous.at(12f, AREA_Y + areaHeight + 12f, 232f, 44f))
        if (announcer) add(destinationControl.at(12f, DESTINATION_Y, 232f, 32f))
    }

    override fun onScreenShown() {
        focus(area)
    }

    // The session's timers run from the app, whatever screen shows: never session.tick() here.
    override fun onTick() {
        val outcome = draft.takeOutcome()
        outcome?.let(::outcome)
        // Going back to the feed: what the session has to say is told there, after `Publié`.
        if (outcome is DraftOutcome.Sent) return
        // Every notice waiting, in order, as the other screens tell them.
        while (true) actions.report(session.takeNotice() ?: break)
    }

    /** The controls as the draft is: Publier's rule, and the sending state (9.8). */
    private fun refresh() {
        val sending = draft.sending
        publish.loading = sending
        publish.enabled = sending || FeedWriting.canSend(draft.text.text)
        area.sending = sending
        anonymous.enabled = !sending
        anonymous.toggle.checked = draft.anonymous
        destinationControl.enabled = !sending
        destinationControl.selected = draft.destination.ordinal
        close.enabled = !sending
        if (announcer && !sending && session.state != null && !session.canCompose(FeedKind.ANNOUNCEMENTS)) dropDestination()
    }

    /** The announcer right went: Global, without the destination control, in the player's layout (9.8). */
    private fun dropDestination() {
        announcer = false
        draft.destination = FeedKind.GLOBAL
        remove(destinationControl)
        area.at(12f, AREA_Y, 232f, AREA_PLAYER)
        anonymous.at(12f, AREA_Y + AREA_PLAYER + 12f, 232f, 44f)
    }

    private fun send() {
        if (draft.sending || !FeedWriting.canSend(draft.text.text)) return
        if (!session.sendPost()) session.writeBlock()?.let { showErrorSnackbar(strings.get(FeedWriting.snackbarKey(it))) }
    }

    private fun outcome(outcome: DraftOutcome) {
        when (outcome) {
            is DraftOutcome.Sent -> {
                PhoneSounds.sent()
                // Carried over to the feed, under the published post.
                showSnackbar(strings.get("compose.posted"))
                onPosted(outcome.post.feed)
                phone.pop()
            }
            DraftOutcome.SendFailed -> showErrorSnackbar(strings.get("snackbar.send_failed"), SnackbarAction(strings.get("error.retry")) { send() })
            is DraftOutcome.TooFast -> showErrorSnackbar(strings.get("snackbar.too_fast", outcome.seconds))
            is DraftOutcome.Refused -> showErrorSnackbar(strings.get(FeedWriting.snackbarKey(outcome.block)))
            // Refused for good (not allowed): no Retry, the draft stays.
            DraftOutcome.ActionFailed -> showErrorSnackbar(strings.get("snackbar.action_failed"))
            DraftOutcome.ThreadGone -> {}
        }
        // Not sent: the text area has the keyboard again, to fix the text (and Backspace erases, it doesn't close).
        if (outcome !is DraftOutcome.Sent) focus(area)
    }

    /**
     * Who the post shows as (9.8): the player's face, name and handle (the name alone, centred on the face, without a
     * real name); with the toggle on, the anonymous avatar, `Anonyme` and `Masqué aux joueurs`.
     */
    private inner class AuthorRow : Widget() {
        override fun render(canvas: AppCanvas) {
            val me = session.state?.me
            if (draft.anonymous || me == null) {
                Avatar.paintSkin(canvas, 12f, 0f, Avatar.Size.MEDIUM, null)
                canvas.drawTextTruncated(strings.get("post.anonymous"), NAME_X, 0f, NAME_WIDTH, TextStyle.TITLE_M, Colors.TEXT_2)
                canvas.drawTextTruncated(strings.get("compose.anon_sub"), NAME_X, 18f, NAME_WIDTH, TextStyle.CAPTION, Colors.TEXT_2)
                return
            }
            Avatar.paintPlayer(canvas, 12f, 0f, Avatar.Size.MEDIUM, me.uuid, me.username)
            val handle = me.handle
            canvas.drawTextTruncated(me.displayName, NAME_X, if (handle == null) 7f else 0f, NAME_WIDTH, TextStyle.TITLE_M, Colors.TEXT_1)
            handle?.let { canvas.drawTextTruncated(it, NAME_X, 18f, NAME_WIDTH, TextStyle.CAPTION, Colors.TEXT_2) }
        }
    }

    companion object {
        /** `Publier` / `Post`, sized for the longer (13.3). */
        const val PUBLISH_WIDTH = 88f

        const val AUTHOR_Y = 52f
        const val AREA_Y = 96f
        const val AREA_PLAYER = 196f
        const val AREA_ANNOUNCER = 152f
        const val DESTINATION_Y = 316f

        private const val NAME_X = 56f
        private const val NAME_WIDTH = 188f
    }
}
