package fr.bde_teknantes.lnb.phone.api.client.ui

/**
 * How animations move. The phone uses quadratic easing only (art direction 14): [OUT] for what arrives or opens,
 * [IN] for what leaves or closes, [LINEAR] for fades and loops.
 */
fun interface Easing {
    /** Maps progress [t] (0 to 1) to eased progress (0 to 1). */
    fun ease(t: Float): Float

    companion object {
        val LINEAR = Easing { it }

        /** Fast, then slowing down. */
        val OUT = Easing { 1f - (1f - it) * (1f - it) }

        /** Slow, then speeding up. */
        val IN = Easing { it * it }
    }
}

/**
 * The phone's animation timings, in milliseconds (art direction 14). Durations are whole game ticks of 50 ms.
 */
object Motion {
    /** One game tick. */
    const val TICK_MS = 50f

    /** Screens sliding in and out, tabs, segmented controls, snackbars sliding in, the FAB lifting. */
    const val SHORT_MS = 150f

    /** Toggles, menus fading in, dialogs fading out, the like pop. */
    const val QUICK_MS = 100f

    /** Scrolling a list back to the top. */
    const val SCROLL_TO_TOP_MS = 250f

    /** A pressed state lasts at least this long, even after a quick click. */
    const val MIN_PRESS_MS = 100f

    /** How long the pointer rests on something before its tooltip shows. */
    const val TOOLTIP_DELAY_MS = 500f

    /** One turn of the spinner. */
    const val SPINNER_TURN_MS = 800f

    /** The caret is shown, then hidden, this long each. */
    const val CARET_BLINK_MS = 500f
}

/**
 * A number that moves to a new value over time: a position, an opacity, a height. Time is the screen's clock in
 * milliseconds ([Widget.now]); the tween keeps no clock of its own, so it can be tested with plain numbers.
 *
 * ```kotlin
 * private val knob = Tween(2f)
 * fun toggle() = knob.animateTo(if (on) 18f else 2f, now, Motion.QUICK_MS)
 * override fun render(canvas: AppCanvas) { canvas.fillCircle(knob.value(now), 2f, 16f, Colors.TEXT_1) }
 * ```
 */
class Tween(initial: Float) {
    private var from = initial
    private var start = 0f
    private var duration = 0f
    private var easing = Easing.OUT

    /** Where the tween is going (or is). */
    var target = initial
        private set

    /** Starts moving from where it is at [now] to [value], over [durationMs]. Nothing changes if it already goes there. */
    fun animateTo(value: Float, now: Float, durationMs: Float, easing: Easing = Easing.OUT) {
        if (value == target) return
        from = value(now)
        target = value
        start = now
        duration = durationMs
        this.easing = easing
    }

    /** Jumps to [value] at once. */
    fun snapTo(value: Float) {
        from = value
        target = value
        duration = 0f
    }

    /** The value at [now]. */
    fun value(now: Float): Float {
        if (duration <= 0f) return target
        val t = ((now - start) / duration).coerceIn(0f, 1f)
        return from + (target - from) * easing.ease(t)
    }

    /** Progress of the current move at [now], 0 to 1 (1 when still). */
    fun progress(now: Float): Float = if (duration <= 0f) 1f else ((now - start) / duration).coerceIn(0f, 1f)

    /** Whether it is still moving at [now]. */
    fun isRunning(now: Float): Boolean = duration > 0f && now < start + duration
}
