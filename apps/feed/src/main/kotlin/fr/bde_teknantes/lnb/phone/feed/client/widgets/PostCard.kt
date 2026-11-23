package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.AnnouncerBadge
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconButton
import fr.bde_teknantes.lnb.phone.api.client.ui.KitDrawing
import fr.bde_teknantes.lnb.phone.api.client.ui.Radius
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.feed.client.FeedFormat
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.PostView

/** What a card's buttons do: the screen that shows it decides. */
interface PostCardActions {
    /** A click on the cell outside its buttons: the thread. */
    fun open(post: PostView)

    fun reply(post: PostView)

    fun like(post: PostView, card: PostCard)

    fun pin(post: PostView)

    /** The more button, [anchor] for its menu. */
    fun more(post: PostView, anchor: Widget)
}

/**
 * A post in a list (art direction 9.3): the face, the name with the announcer badge, the handle, the time, the
 * private and reply-to lines, the text, and the action row; the `Épinglé` line and the `surface` fill when pinned; the
 * unseen-replies marker; the post highlight. Its height follows its text ([PostCardLayout]), so it sits in a scrolling
 * list as is.
 *
 * It shows what [post] returns each frame, so counts, the heart and pins change in place; [look] and [time] the same.
 */
class PostCard(
    /** The post as it is now; null once it went (the card is about to leave its list). */
    private val post: () -> PostView?,
    private val strings: FeedStrings,
    private val actions: PostCardActions,
) : Clickable() {
    /** The buttons and lines the card shows. */
    var look: CardLook = CardLook(more = false, pin = false)

    /** The relative time shown, from the post's date. */
    var time: (PostView) -> String = { "" }

    private var last: PostView? = post()
    private var layout: PostCardLayout? = null
    private var highlightAt = Float.NEGATIVE_INFINITY

    val reply: ActionButton = add(ActionButton(Icon.REPLY, Icon.REPLY, ActionButton.Tint.ACCENT))
    val like: ActionButton = add(ActionButton(Icon.HEART, Icon.HEART_FILLED, ActionButton.Tint.LIKE).apply { silent = true })
    val pin: ActionButton = add(ActionButton(Icon.PIN, Icon.PIN_FILLED, ActionButton.Tint.ACCENT))
    val more: IconButton = add(IconButton(Icon.MORE).apply {
        size = 28f
        tint = Colors.TEXT_2
        tooltip = strings.get("action.more")
    })
    private val badge = add(AnnouncerBadge().apply { tooltip = strings.get("badge.announcer") })
    private val privateArea = add(TooltipArea().apply { tooltip = strings.get("post.mod_author.tooltip") })

    init {
        onClick = { current()?.let(actions::open) }
        reply.onClick = { current()?.let(actions::reply) }
        like.onClick = { current()?.let { actions.like(it, this) } }
        pin.onClick = { current()?.let(actions::pin) }
        more.onClick = { current()?.let { actions.more(it, more) } }
        refresh()
    }

    /** The post shown: the current one, or the last one seen once it went. */
    fun current(): PostView? = post() ?: last

    /** The layout for what is shown now, made again when the post, its look or its time changes. */
    fun cardLayout(): PostCardLayout? {
        val p = current() ?: return null
        val shownTime = if (look.time) time(p) else ""
        layout?.let { if (it.post === p && it.look == look && it.timeText == shownTime) return it }
        return PostCardLayout(p, look, shownTime, TextMetrics, strings).also { layout = it }
    }

    /** The post highlight (8.7): an `accent-soft` fill held 600 ms, then faded out over 400 ms. */
    fun highlight() {
        highlightAt = now
    }

    /** Whether the highlight is on now. */
    val isHighlighted: Boolean get() = now - highlightAt < HIGHLIGHT_MS + FADE_MS

    override fun heightFor(width: Float): Float = cardLayout()?.height ?: 0f

    override fun update() {
        post()?.let { last = it }
        refresh()
    }

    private fun refresh() {
        val l = cardLayout() ?: return
        val p = l.post
        val row = l.actionY
        reply.countText = FeedFormat.count(p.replies, strings)
        reply.marked = p.unseenReplies > 0
        reply.at(50f, row, ActionButton.DISC + reply.countWidth(), ActionButton.DISC)
        like.countText = FeedFormat.count(p.likes, strings)
        like.active = p.liked
        like.at(114f, row, ActionButton.DISC + like.countWidth(), ActionButton.DISC)
        pin.visible = look.pin
        pin.active = l.pinned
        pin.tooltip = strings.get(if (l.pinned) "action.unpin" else "action.pin")
        pin.at(214f, row, ActionButton.DISC, ActionButton.DISC)
        more.visible = look.more
        more.at(220f, 5f + l.shift)
        badge.visible = l.badge
        // Vertically centred on the 18 dp name line, 4 dp after the name.
        badge.at(PostCardLayout.CONTENT_X + l.nameWidth + 4f, l.nameY + 1f)
        privateArea.visible = l.privateIsModerator
        l.privateY?.let { privateArea.at(PostCardLayout.CONTENT_X, it, PostCardLayout.BODY_WIDTH, PostCardLayout.LINE) }
    }

    override fun render(canvas: AppCanvas) {
        val l = cardLayout() ?: return
        val p = l.post
        val h = l.height
        if (l.pinned) canvas.fillRect(0f, 0f, width, h, Colors.SURFACE)
        KitDrawing.stateTint(canvas, interaction, 0f, 0f, width, h)
        val since = now - highlightAt
        if (since < HIGHLIGHT_MS + FADE_MS) {
            val alpha = if (since < HIGHLIGHT_MS) 1f else 1f - (since - HIGHLIGHT_MS) / FADE_MS
            canvas.fillRect(0f, 0f, width, h, Colors.withAlpha(Colors.ACCENT_SOFT, alpha))
        }
        if (showsFocusRing) KitDrawing.focusRing(canvas, 4f, 4f, width - 8f, h - 9f, Radius.SMALL - 4f)
        if (l.pinned) {
            canvas.drawIcon(Icon.PIN, 28f, 8f, Colors.TEXT_2, 16f)
            canvas.drawText(strings.get("post.pinned"), PostCardLayout.CONTENT_X, 8f, TextStyle.CAPTION_STRONG, Colors.TEXT_2)
        }
        val author = p.author
        if (l.anonymous || author == null) {
            Avatar.paintSkin(canvas, 12f, 10f + l.shift, Avatar.Size.MEDIUM, null)
        } else {
            Avatar.paintPlayer(canvas, 12f, 10f + l.shift, Avatar.Size.MEDIUM, author.uuid, author.username)
        }
        canvas.drawText(l.nameShown, PostCardLayout.CONTENT_X, l.nameY, TextStyle.TITLE_M, if (l.anonymous) Colors.TEXT_2 else Colors.TEXT_1)
        if (look.time) canvas.drawText(l.timeText, l.timeRight - l.timeWidth, l.nameY + 1f, TextStyle.CAPTION, Colors.TEXT_2)
        l.handleY?.let { y -> l.handleShown?.let { canvas.drawText(it, PostCardLayout.CONTENT_X, y, TextStyle.CAPTION, Colors.TEXT_2) } }
        l.replyToY?.let { y ->
            look.replyTo?.let { canvas.drawTextTruncated(it, PostCardLayout.CONTENT_X, y, PostCardLayout.BODY_WIDTH, TextStyle.CAPTION, Colors.TEXT_2) }
        }
        l.privateY?.let { y ->
            canvas.drawIcon(Icon.LOCK, PostCardLayout.CONTENT_X, y, Colors.TEXT_3, 16f)
            canvas.drawText(l.privateText!!, 76f, y, TextStyle.CAPTION, Colors.TEXT_3)
        }
        l.bodyLines.forEachIndexed { i, line ->
            canvas.drawText(line, PostCardLayout.CONTENT_X, l.bodyY + i * TextStyle.BODY.lineHeight, TextStyle.BODY, Colors.TEXT_1)
        }
        // The unseen-replies marker's dot: 6 dp, centred at x 50, on the reply icon's middle.
        if (p.unseenReplies > 0) canvas.fillCircle(47f, l.actionY + 11f, 6f, Colors.ACCENT)
        canvas.hairline(0f, width, h - 1f)
    }

    /** An invisible strip that shows a tooltip: the moderator's private line. */
    private class TooltipArea : Widget()

    companion object {
        const val HIGHLIGHT_MS = 600f
        const val FADE_MS = 400f
    }
}
