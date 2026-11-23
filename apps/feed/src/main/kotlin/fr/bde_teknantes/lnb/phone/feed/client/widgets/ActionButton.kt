package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.Interaction
import fr.bde_teknantes.lnb.phone.api.client.ui.KitDrawing
import fr.bde_teknantes.lnb.phone.api.client.ui.Motion
import fr.bde_teknantes.lnb.phone.api.client.ui.Radius
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import kotlin.math.abs

/**
 * A button of a post's action row (art direction 9.3): a 16 dp icon in a 28 dp disc that tints under the pointer, and
 * its count 6 dp after the disc. The kit has nothing that fits, so the app draws it on [Clickable]. Placed with its
 * left edge on the disc's (x 50 for reply, 114 for like, 214 for pin), as wide as the disc and the count.
 */
class ActionButton(private val icon: Icon, private val activeIcon: Icon, private val tint: Tint) : Clickable() {
    /** The colours of a button: the reply and pin buttons in the accent, the heart in coral. */
    enum class Tint(val color: Int, val soft: Int, val soft2: Int) {
        ACCENT(Colors.ACCENT, Colors.ACCENT_SOFT, Colors.ACCENT_SOFT_2),
        LIKE(Colors.LIKE, Colors.LIKE_SOFT, Colors.LIKE_SOFT_2),
    }

    private var poppedAt = Float.NEGATIVE_INFINITY

    /** The count as shown (empty for 0: the icon alone). */
    var countText = ""

    /** The active state: `heart_filled` or `pin_filled`, in the tint. */
    var active = false

    /** The unseen-replies marker: icon and count in the accent (the card draws the dot). */
    var marked = false

    /** Width of the count, 0 without one. */
    fun countWidth(): Float = if (countText.isEmpty()) 0f else TextMetrics.measure(countText, TextStyle.CAPTION)

    /** The like pop: the heart goes 1.0, 1.2, 1.0 over 100 ms. */
    fun pop() {
        poppedAt = now
    }

    override fun render(canvas: AppCanvas) {
        val look = interaction
        when (look) {
            Interaction.HOVER -> canvas.fillCircle(0f, 0f, DISC, tint.soft)
            Interaction.PRESSED -> canvas.fillCircle(0f, 0f, DISC, tint.soft2)
            else -> {}
        }
        if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, DISC, DISC, Radius.FULL)
        val hot = look == Interaction.HOVER || look == Interaction.PRESSED
        val color = if (active || marked || hot) tint.color else Colors.TEXT_2
        val t = ((now - poppedAt) / Motion.QUICK_MS).coerceIn(0f, 1f)
        val scale = if (t < 1f) 1f + 0.2f * (1f - abs(2f * t - 1f)) else 1f
        val size = ICON * scale
        canvas.drawIcon(if (active) activeIcon else icon, DISC / 2f - size / 2f, DISC / 2f - size / 2f, color, size)
        if (countText.isNotEmpty()) canvas.drawText(countText, DISC, (DISC - TextStyle.CAPTION.lineHeight) / 2f, TextStyle.CAPTION, color)
    }

    companion object {
        const val DISC = 28f
        const val ICON = 16f
    }
}
