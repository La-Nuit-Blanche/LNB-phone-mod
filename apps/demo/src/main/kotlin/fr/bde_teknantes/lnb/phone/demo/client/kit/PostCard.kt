package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.AnnouncerBadge
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Easing
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconButton
import fr.bde_teknantes.lnb.phone.api.client.ui.Interaction
import fr.bde_teknantes.lnb.phone.api.client.ui.MenuItem
import fr.bde_teknantes.lnb.phone.api.client.ui.Motion
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMeasurer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Tween
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at

/**
 * Where everything of a post card goes (art direction 9.3), from the post and the measured text alone: plain logic, so
 * it can be tested with a fake [TextMeasurer]. All y values are from the top of the cell, in dp.
 */
class PostCardLayout(post: Post, measurer: TextMeasurer) {
    /** 18 dp of "Pinned" line above the header of a pinned post. */
    val shift = if (post.pinned) 18f else 0f
    val nameY = 10f + shift
    val handleY: Float? = if (post.anonymous || post.author.handle == null) null else nameY + 18f
    private val afterHeader = (handleY?.let { it + 16f } ?: (nameY + 18f))
    val replyToY: Float? = post.replyTo?.let { afterHeader }
    val privateY: Float? = post.privateLine?.let { afterHeader + (if (replyToY != null) 16f else 0f) }
    private val optionalLines = (if (replyToY != null) 1 else 0) + (if (privateY != null) 1 else 0)
    val bodyY = afterHeader + 16f * optionalLines + 2f
    val bodyLines: List<String> = measurer.wrap(post.text, TextStyle.BODY, BODY_WIDTH)
    val actionY = bodyY + bodyLines.size * TextStyle.BODY.lineHeight + 4f

    /** The cell's full height, its bottom divider included. */
    val height = actionY + 28f + 4f + 1f

    /** Where the time ends: before the more button when it shows. */
    val timeRight = if (post.canDelete) 214f else 244f
    val timeWidth = measurer.measure(post.time(), TextStyle.CAPTION)
    val badge = post.author.announcer && !post.anonymous

    /** Room for the name: the badge (4 + 16 dp) and 8 dp before the time always fit. */
    val nameMaxWidth = timeRight - timeWidth - 8f - (if (badge) 20f else 0f) - CONTENT_X

    companion object {
        const val CONTENT_X = 56f
        const val BODY_WIDTH = 188f
    }
}

/**
 * A post in a timeline (art direction 9.3), built from the kit: a [Clickable] cell whose whole surface opens the post,
 * with action buttons, an optional "more" button, the announcer badge and the private line as children. Its height
 * follows its text, so it sits in a [fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList] as is.
 */
class PostCard(val post: Post, onOpen: (() -> Unit)? = null) : Clickable(onOpen) {
    private val highlight = Tween(0f)
    private var highlightAt = Float.NEGATIVE_INFINITY
    private val collapse = Tween(1f)
    private var layout: PostCardLayout? = null
    private var layoutText: String? = null

    val reply = add(ActionButton(Icon.REPLY, Icon.REPLY, ActionButton.Tint.ACCENT).apply {
        count = post.replies
        marked = post.unseenReplies
    })
    val like = add(ActionButton(Icon.HEART, Icon.HEART_FILLED, ActionButton.Tint.LIKE).apply {
        count = post.likes
        active = post.liked
        silent = true
        onClick = { toggleLike() }
    })
    val pin: ActionButton? = if (post.canPin) add(ActionButton(Icon.PIN, Icon.PIN_FILLED, ActionButton.Tint.ACCENT).apply {
        active = post.pinned
        tooltip = tr(if (post.pinned) "action.unpin" else "action.pin")
    }) else null
    val more: IconButton? = if (post.canDelete) add(IconButton(Icon.MORE, 28f, Colors.TEXT_2).apply { tooltip = tr("action.more") }) else null
    val badge: Widget? = if (post.author.announcer && !post.anonymous) add(AnnouncerBadge(tr("badge.announcer"))) else null
    val privateArea: Widget? = post.privateLine?.let { add(TooltipArea(post.privateTooltip)) }

    init {
        more?.onClick = {
            screen?.showMenu(more!!, listOf(MenuItem(tr("menu.delete"), Icon.TRASH, destructive = true) { onDelete?.invoke() }))
        }
    }

    /** What "Delete" in the more menu does. */
    var onDelete: (() -> Unit)? = null

    private fun cardLayout(): PostCardLayout {
        val text = post.text
        layout?.takeIf { layoutText === text }?.let { return it }
        return PostCardLayout(post, TextMetrics).also {
            layout = it
            layoutText = text
        }
    }

    private fun toggleLike() {
        post.liked = !post.liked
        post.likes += if (post.liked) 1 else -1
        like.active = post.liked
        like.count = post.likes
        if (post.liked) {
            like.pop()
            PhoneSounds.like()
        } else {
            PhoneSounds.tap()
        }
    }

    /** The post highlight (8.7): accent tint held 600 ms, then faded out over 400 ms. */
    fun highlight() {
        highlightAt = now
    }

    /** Folds the cell away (150 ms, ease-in), its content fading; it then has no height. */
    fun collapse() = collapse.animateTo(0f, now, Motion.SHORT_MS, Easing.IN)

    override fun heightFor(width: Float): Float = cardLayout().height * collapse.value(now)

    override fun layout() {
        val l = cardLayout()
        val row = l.actionY
        reply.at(50f, row, 28f + reply.countWidth(), 28f)
        like.at(114f, row, 28f + like.countWidth(), 28f)
        pin?.at(214f, row, 28f, 28f)
        more?.at(220f, 5f + l.shift)
        badge?.let {
            val nameWidth = minOf(TextMetrics.measure(post.author.displayName, TextStyle.TITLE_M), l.nameMaxWidth)
            it.at(56f + nameWidth + 4f, l.nameY + 1f)
        }
        privateArea?.at(56f, l.privateY!!, 188f, 16f)
    }

    override fun update() {
        layout()
        if (collapse.value(now) < 1f) visible = collapse.value(now) > 0f
    }

    override fun render(canvas: AppCanvas) {
        val l = cardLayout()
        val fold = collapse.value(now)
        canvas.pushClip(0f, 0f, width, height)
        canvas.pushAlpha(fold)
        if (post.pinned) canvas.fillRect(0f, 0f, width, l.height, Colors.SURFACE)
        when (interaction) {
            Interaction.HOVER -> canvas.fillRect(0f, 0f, width, l.height, Colors.OVERLAY_HOVER)
            Interaction.PRESSED -> canvas.fillRect(0f, 0f, width, l.height, Colors.OVERLAY_PRESSED)
            else -> {}
        }
        val since = now - highlightAt
        if (since < 1000f) {
            val a = if (since < 600f) 1f else 1f - (since - 600f) / 400f
            canvas.fillRect(0f, 0f, width, l.height, Colors.withAlpha(Colors.ACCENT_SOFT, a))
        }
        if (post.pinned) {
            canvas.drawIcon(Icon.PIN, 28f, 8f, Colors.TEXT_2, 16f)
            canvas.drawText(tr("post.pinned"), 56f, 8f, TextStyle.CAPTION_STRONG, Colors.TEXT_2)
        }
        Avatar.paint(canvas, 12f, 10f + l.shift, if (post.anonymous) null else post.author.skin())
        val name = if (post.anonymous) tr("post.anonymous") else post.author.displayName
        canvas.drawTextTruncated(name, 56f, l.nameY, l.nameMaxWidth, TextStyle.TITLE_M, if (post.anonymous) Colors.TEXT_2 else Colors.TEXT_1)
        canvas.drawText(post.time(), l.timeRight - l.timeWidth, l.nameY + 1f, TextStyle.CAPTION, Colors.TEXT_2)
        l.handleY?.let { canvas.drawText(post.author.handle!!, 56f, it, TextStyle.CAPTION, Colors.TEXT_2) }
        l.replyToY?.let { canvas.drawTextTruncated(tr("post.reply_to", post.replyTo!!), 56f, it, 188f, TextStyle.CAPTION, Colors.TEXT_2) }
        l.privateY?.let {
            canvas.drawIcon(Icon.LOCK, 56f, it, Colors.TEXT_3, 16f)
            canvas.drawTextTruncated(post.privateLine!!, 76f, it, 168f, TextStyle.CAPTION, Colors.TEXT_3)
        }
        l.bodyLines.forEachIndexed { i, line -> canvas.drawText(line, 56f, l.bodyY + i * TextStyle.BODY.lineHeight, TextStyle.BODY, Colors.TEXT_1) }
        if (reply.marked) canvas.fillCircle(47f, l.actionY + 11f, 6f, Colors.ACCENT)
        canvas.hairline(0f, width, l.height - 1f)
    }

    override fun renderOver(canvas: AppCanvas) {
        canvas.popAlpha()
        canvas.popClip()
    }

    /** An invisible strip that shows a tooltip: the private line's "Only operators can see this". */
    private class TooltipArea(text: String?) : Widget() {
        init {
            tooltip = text
        }
    }
}

/**
 * A button of a post's action row (art direction 9.3): a 16 dp icon with a count after it, a 28 dp tinted disc behind
 * the icon under the pointer. A widget an app writes itself on top of [Clickable], where the kit has nothing that fits.
 */
class ActionButton(private val icon: Icon, private val activeIcon: Icon, private val tint: Tint) : Clickable() {
    enum class Tint(val color: Int, val soft: Int, val soft2: Int) {
        ACCENT(Colors.ACCENT, Colors.ACCENT_SOFT, Colors.ACCENT_SOFT_2),
        LIKE(Colors.LIKE, Colors.LIKE_SOFT, Colors.LIKE_SOFT_2),
    }

    private var poppedAt = Float.NEGATIVE_INFINITY

    var count = 0
    var active = false

    /** The unseen-replies marker: icon and count in the accent (the dot is drawn by the card). */
    var marked = false

    /** Count text: hidden at 0, thousands shortened. */
    val countText: String get() = when {
        count <= 0 -> ""
        count < 1000 -> count.toString()
        else -> "${count / 1000}.${count % 1000 / 100}k"
    }

    fun countWidth() = if (countText.isEmpty()) 0f else TextMetrics.measure(countText, TextStyle.CAPTION)

    /** The like pop: 1.0, 1.2, 1.0 over 100 ms. */
    fun pop() {
        poppedAt = now
    }

    override fun render(canvas: AppCanvas) {
        val look = interaction
        when (look) {
            Interaction.HOVER -> canvas.fillCircle(0f, 0f, 28f, tint.soft)
            Interaction.PRESSED -> canvas.fillCircle(0f, 0f, 28f, tint.soft2)
            else -> {}
        }
        val hot = look == Interaction.HOVER || look == Interaction.PRESSED
        val color = if (active || marked || hot) tint.color else Colors.TEXT_2
        val t = ((now - poppedAt) / Motion.QUICK_MS).coerceIn(0f, 1f)
        val scale = if (t < 1f) 1f + 0.2f * (1f - kotlin.math.abs(2f * t - 1f)) else 1f
        val size = 16f * scale
        canvas.drawIcon(if (active) activeIcon else icon, 14f - size / 2f, 14f - size / 2f, color, size)
        if (countText.isNotEmpty()) canvas.drawText(countText, 28f, 6f, TextStyle.CAPTION, color)
    }
}
