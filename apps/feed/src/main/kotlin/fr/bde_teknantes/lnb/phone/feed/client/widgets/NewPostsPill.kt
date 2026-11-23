package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Easing
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.Interaction
import fr.bde_teknantes.lnb.phone.api.client.ui.KitDrawing
import fr.bde_teknantes.lnb.phone.api.client.ui.Motion
import fr.bde_teknantes.lnb.phone.api.client.ui.Radius
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import kotlin.math.ceil

/**
 * The new-posts pill (art direction 9.5): 28 dp, an accent pill centred at y 52 over the list, `arrow_up` and the
 * count of posts waiting when the first one's place is above the visible area, the count alone otherwise. It slides
 * down from y 36 while fading in (150 ms) and fades out (150 ms). The app draws it: the kit has no such widget.
 */
class NewPostsPill : Clickable() {
    /** The label, `2 nouveaux posts`. */
    var label = ""

    /** Whether the arrow shows: the first new post's place is above the visible area. */
    var arrow = false

    private var shownAt = Float.NEGATIVE_INFINITY
    private var hiddenAt = Float.NEGATIVE_INFINITY
    private var showing = false

    init {
        visible = false
    }

    /** Whether the pill is out (or coming out). */
    val isOut: Boolean get() = showing

    /** Brings the pill out, or sends it away; animated once the screen has been seen. */
    fun show(show: Boolean) {
        if (show == showing) return
        showing = show
        // Hidden, the pill isn't drawn: whether the player has seen the screen is its parent's to say.
        val animate = parent?.shouldAnimate == true
        if (show) {
            visible = true
            shownAt = if (animate) now else Float.NEGATIVE_INFINITY
        } else {
            hiddenAt = if (animate) now else Float.NEGATIVE_INFINITY
        }
    }

    /** The pill's width: padding 12, the arrow and its 4 dp gap, the label, padding 12; even, so it stays sharp. */
    val pillWidth: Float
        get() {
            val content = TextMetrics.measure(label, TextStyle.CAPTION_STRONG) + if (arrow) ICON + GAP else 0f
            return ceil((content + 2 * PADDING) / 2f) * 2f
        }

    private fun progress(): Float =
        if (showing) Easing.OUT.ease(((now - shownAt) / Motion.SHORT_MS).coerceIn(0f, 1f)) else 1f - ((now - hiddenAt) / Motion.SHORT_MS).coerceIn(0f, 1f)

    override fun update() {
        val w = pillWidth
        val slide = if (showing) progress() else 1f
        setBounds(((VIEWPORT - w) / 2f).toInt().toFloat(), FROM_Y + (Y - FROM_Y) * slide, w, HEIGHT)
        visible = showing || progress() > 0f
    }

    // Clicks only while it is out: not while it fades away.
    override fun hitTest(localX: Float, localY: Float) = if (showing) super.hitTest(localX, localY) else null

    override fun render(canvas: AppCanvas) {
        val alpha = progress()
        if (alpha <= 0f) return
        canvas.pushAlpha(alpha)
        val fill = when (interaction) {
            Interaction.HOVER -> Colors.ACCENT_HOVER
            Interaction.PRESSED -> Colors.ACCENT_PRESSED
            else -> Colors.ACCENT
        }
        canvas.fillRoundRect(0f, 0f, width, height, Radius.FULL, fill)
        if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, width, height, Radius.FULL)
        val textWidth = TextMetrics.measure(label, TextStyle.CAPTION_STRONG)
        var x = (width - textWidth - if (arrow) ICON + GAP else 0f) / 2f
        if (arrow) {
            canvas.drawIcon(Icon.ARROW_UP, x, (HEIGHT - ICON) / 2f, Colors.ON_ACCENT, ICON)
            x += ICON + GAP
        }
        canvas.drawText(label, x, (HEIGHT - TextStyle.CAPTION_STRONG.lineHeight) / 2f, TextStyle.CAPTION_STRONG, Colors.ON_ACCENT)
        canvas.popAlpha()
    }

    companion object {
        const val Y = 52f
        const val FROM_Y = 36f
        const val HEIGHT = 28f
        const val PADDING = 12f
        const val ICON = 16f
        const val GAP = 4f
        private const val VIEWPORT = 256f
    }
}
