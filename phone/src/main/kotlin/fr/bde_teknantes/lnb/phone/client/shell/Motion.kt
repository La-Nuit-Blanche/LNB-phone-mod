package fr.bde_teknantes.lnb.phone.client.shell

/**
 * Timings of the shell's animations (art direction 14), in game ticks of 50 ms. Animations run on
 * `tick + partialTick`, so they move every frame although their length is a whole number of ticks.
 */
internal object Motion {
    const val OPEN_TICKS = 5f
    const val CLOSE_TICKS = 4f
    const val GLIDE_TICKS = 5f
    const val SCREEN_TICKS = 3f

    /** The shortest time a pressed state stays drawn, even on a quick click. */
    const val PRESS_MIN_TICKS = 2f

    /** LED blink: 500 ms lit, then 1500 ms off. */
    const val LED_ON_TICKS = 10
    const val LED_CYCLE_TICKS = 40

    /** Badge pulse: the fill alternates every 250 ms, 3 cycles (1500 ms), starting on the light frame. */
    const val PULSE_STEP_TICKS = 5
    const val PULSE_TICKS = 30

    /** How far an animation of [duration] ticks started at [start] has run at [now], from 0 to 1, before easing. */
    fun linear(start: Float, duration: Float, now: Float): Float = ((now - start) / duration).coerceIn(0f, 1f)

    /** Whether the LED is lit at [ticks] while it blinks. */
    fun ledLit(ticks: Long): Boolean = Math.floorMod(ticks, LED_CYCLE_TICKS.toLong()) < LED_ON_TICKS

    /** Whether a badge whose pulse started at [start] shows its light frame at [ticks]. */
    fun pulseLit(start: Long, ticks: Long): Boolean {
        val elapsed = ticks - start
        return elapsed in 0 until PULSE_TICKS && (elapsed / PULSE_STEP_TICKS) % 2 == 0L
    }

    /**
     * The pixel shell moves on whole GUI px (art direction 14). Halves go to the even neighbour: that is how the
     * flows' mid-slide frames are rounded (145.5 to 146 in P-3.2, 28.5 to 28 in P-12.4).
     */
    fun snap(value: Float): Int = Math.rint(value.toDouble()).toInt()

    fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
}

/** The shell uses quadratic easing only (art direction 14). */
internal enum class Easing {
    EASE_OUT {
        override fun apply(t: Float): Float {
            val left = 1f - t.coerceIn(0f, 1f)
            return 1f - left * left
        }
    },
    EASE_IN {
        override fun apply(t: Float): Float {
            val clamped = t.coerceIn(0f, 1f)
            return clamped * clamped
        }
    },
    ;

    abstract fun apply(t: Float): Float
}

/** Where the device is drawn and how dark the scrim behind it is, for one frame. */
internal data class DevicePose(val x: Int, val y: Int, val scrim: Float)

/**
 * The device moving from where it was ([fromX], [fromY], with [fromScrim]) towards a target given at each frame: the
 * target follows the window size and the placement, which may change while the device moves.
 */
internal class DeviceSlide(
    val fromX: Float,
    val fromY: Float,
    val fromScrim: Float,
    val start: Float,
    val duration: Float,
    val easing: Easing,
) {
    /** Eased progress at [now], 0 to 1. */
    fun progress(now: Float): Float = easing.apply(Motion.linear(start, duration, now))

    fun isDone(now: Float): Boolean = now >= start + duration

    /** The pose at eased [progress] on the way to ([toX], [toY]) and [toScrim]; positions on whole GUI px. */
    fun pose(progress: Float, toX: Int, toY: Int, toScrim: Float) = DevicePose(
        Motion.snap(Motion.lerp(fromX, toX.toFloat(), progress)),
        Motion.snap(Motion.lerp(fromY, toY.toFloat(), progress)),
        Motion.lerp(fromScrim, toScrim, progress),
    )

    companion object {
        /** Open: up from the window's bottom edge (or from where a closing device was) to the placement, 250 ms. */
        fun open(from: DevicePose, now: Float) = DeviceSlide(from.x.toFloat(), from.y.toFloat(), from.scrim, now, Motion.OPEN_TICKS, Easing.EASE_OUT)

        /** Close: down to the window's bottom edge, 200 ms, ease-in. */
        fun close(from: DevicePose, now: Float) = DeviceSlide(from.x.toFloat(), from.y.toFloat(), from.scrim, now, Motion.CLOSE_TICKS, Easing.EASE_IN)

        /** Placement change: from the old position to the new one, 250 ms, ease-out. */
        fun glide(from: DevicePose, now: Float) = DeviceSlide(from.x.toFloat(), from.y.toFloat(), from.scrim, now, Motion.GLIDE_TICKS, Easing.EASE_OUT)
    }
}

/**
 * A slide between two screens of the viewport (art direction 14): forward, the new screen comes in from the right over
 * the old one (150 ms, ease-out); back, the old screen leaves to the right and uncovers the new one (150 ms, ease-in).
 */
internal class ScreenSlide(val forward: Boolean, val start: Float) {
    fun progress(now: Float): Float = (if (forward) Easing.EASE_OUT else Easing.EASE_IN).apply(Motion.linear(start, Motion.SCREEN_TICKS, now))

    fun isDone(now: Float): Boolean = now >= start + Motion.SCREEN_TICKS

    /**
     * How far right of the viewport's left edge the moving screen is at eased [progress], in whole GUI px: the
     * incoming screen when going forward, the outgoing one when going back. 256 dp is the full width (128 GUI px).
     */
    fun offset(progress: Float): Int = Motion.snap(Device.VIEWPORT_WIDTH * if (forward) 1f - progress else progress)
}
