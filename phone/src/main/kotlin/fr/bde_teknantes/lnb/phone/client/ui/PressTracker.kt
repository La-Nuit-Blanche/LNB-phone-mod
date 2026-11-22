package fr.bde_teknantes.lnb.phone.client.ui

import fr.bde_teknantes.lnb.phone.api.client.ui.Motion

/**
 * The pressed state of one clickable thing (art direction 8.7): pressed while the button is held over it, and for at
 * least 100 ms in all, even after a quick click. Times are the screen clock in ms.
 */
internal class PressTracker(private val minimumMs: Float = Motion.MIN_PRESS_MS) {
    private var pressedAt = Float.NEGATIVE_INFINITY
    private var shownUntil = Float.NEGATIVE_INFINITY

    /** Whether a press is in progress (button down, started on this thing). */
    var holding = false
        private set

    fun press(now: Float) {
        holding = true
        pressedAt = now
        shownUntil = now + minimumMs
    }

    /** Ends the press; true when it counts as a click (released over the thing). */
    fun release(now: Float, over: Boolean): Boolean {
        if (!holding) return false
        holding = false
        // A click keeps its pressed look for the rest of the minimum; a press dragged off and let go ends now.
        shownUntil = if (over) maxOf(now, pressedAt + minimumMs) else now
        return over
    }

    /** A click from the keyboard: the pressed look for the minimum time, starting [now]. */
    fun flash(now: Float) {
        pressedAt = now
        shownUntil = now + minimumMs
    }

    /** Drops a press in progress without a click (the screen was hidden, the widget disabled). */
    fun cancel() {
        holding = false
        shownUntil = Float.NEGATIVE_INFINITY
    }

    /** Whether to draw the pressed state at [now], the pointer being [over] the thing or not. */
    fun isPressed(now: Float, over: Boolean): Boolean = (holding && over) || (!holding && now < shownUntil)
}
