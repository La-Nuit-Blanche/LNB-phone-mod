package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import kotlin.math.abs

/**
 * The loading spinner (art direction 8.7): a quarter arc on a faint ring, one turn per 800 ms. 20 dp by default, 16
 * inside buttons; in the accent unless told otherwise.
 */
class Spinner : Widget() {
    /** 20 dp, or 16 inside buttons. */
    var size = 20f

    var color = Colors.ACCENT

    override val preferredWidth get() = size
    override val preferredHeight get() = size

    override fun render(canvas: AppCanvas) = paint(canvas, (width - size) / 2f, (height - size) / 2f, color, now)

    /** Draws a spinner at ([x], [y]) for the clock time [now], for widgets that draw one inside themselves. */
    fun paint(canvas: AppCanvas, x: Float, y: Float, color: Int, now: Float) =
        canvas.drawSpinner(x, y, size, color, now / Motion.SPINNER_TURN_MS)
}

/** A 48 dp row with a spinner in the middle: more items, or replies, are loading. */
class SpinnerRow : Widget() {
    private val spinner = Spinner()

    override val preferredWidth get() = 256f
    override val preferredHeight get() = 48f

    override fun render(canvas: AppCanvas) = spinner.paint(canvas, (width - 20f) / 2f, (height - 20f) / 2f, Colors.ACCENT, now)
}

/**
 * A 48 dp row with a short caption centred in it: the end of a list ("You're all caught up", in [Colors.TEXT_3]), or a
 * list with nothing in it yet ("No replies yet", in [Colors.TEXT_2]).
 */
class MessageRow(var text: String) : Widget() {
    /** [Colors.TEXT_3] at the end of a list, [Colors.TEXT_2] for an empty one. */
    var color = Colors.TEXT_3

    override val preferredWidth get() = 256f
    override val preferredHeight get() = 48f

    override fun render(canvas: AppCanvas) {
        val shown = canvas.truncate(text, TextStyle.CAPTION, width - 24f)
        canvas.drawTextCentered(shown, width / 2f, (height - TextStyle.CAPTION.lineHeight) / 2f, TextStyle.CAPTION, color)
    }
}

/**
 * A placeholder post while a list loads for the first time (art direction 8.7): an avatar disc and three bars, 84 dp
 * with its divider, pulsing between full and 60% opacity once a second. Show three.
 */
class SkeletonPost : Widget() {
    override val preferredWidth get() = 256f
    override val preferredHeight get() = 84f

    override fun render(canvas: AppCanvas) {
        canvas.pushAlpha(pulse(now))
        canvas.fillCircle(12f, 10f, 32f, Colors.SURFACE_2)
        canvas.fillRoundRect(56f, 14f, 90f, 10f, Radius.FULL, Colors.SURFACE_2)
        canvas.fillRoundRect(56f, 36f, 176f, 10f, Radius.FULL, Colors.SURFACE_2)
        canvas.fillRoundRect(56f, 54f, 120f, 10f, Radius.FULL, Colors.SURFACE_2)
        canvas.popAlpha()
        canvas.hairline(0f, width, height - 1f)
    }

    companion object {
        /** Opacity of skeletons at [now]: 100% to 60% and back, linearly, over 1000 ms. */
        fun pulse(now: Float): Float {
            val phase = (now % 1000f) / 1000f
            return 1f - 0.4f * (1f - abs(1f - 2f * phase))
        }
    }
}

/** Empty space of a fixed height in a list: under the last row so a floating button never covers it. */
class Spacer(height: Float) : Widget() {
    private val fixed = height
    override val preferredHeight get() = fixed
    override val preferredWidth get() = 256f
}
