package fr.bde_teknantes.lnb.phone.client.notifications

import kotlin.math.sqrt

/** The durations of a banner's appearance, in milliseconds (art direction 14). */
data class BannerMotion(val enterMs: Double, val holdMs: Double, val exitMs: Double) {
    companion object {
        /** In-phone banner: 150 ms down (ease-out), 4000 ms hold, 150 ms up (ease-in). */
        val IN_PHONE = BannerMotion(150.0, 4000.0, 150.0)

        /** Peek: the phone rises in 200 ms (ease-out), holds 4000 ms, sinks in 200 ms (ease-in). */
        val PEEK = BannerMotion(200.0, 4000.0, 200.0)

        /** A game tick, in milliseconds. Animations run on `(tick + partialTick) * TICK_MS` (14). */
        const val TICK_MS = 50.0
    }
}

/**
 * One banner's (or peek's) life over time: it comes out, holds, goes back, as a [progress] from 0 (hidden) to 1 (fully
 * out) with quadratic easing. Plain logic over times in milliseconds that the caller supplies, typically
 * `(ticks + partialTick) * BannerMotion.TICK_MS`, so it can be tested without waiting.
 *
 * The in-phone banner's top is [BannerLayout.inPhoneY] of the progress; a peeking phone's top is
 * `H - rise * progress`, rounded to whole GUI px.
 *
 * @param startMs when it starts coming out.
 */
class BannerTimeline(private val motion: BannerMotion, startMs: Double) {
    // When the current way out started (possibly in the past, to resume from a partial position), and when the way
    // back starts.
    private var enterStart = startMs
    private var exitStart = startMs + motion.enterMs + motion.holdMs

    /** How far out the banner is at [nowMs]: 0 hidden, 1 fully out, eased. */
    fun progress(nowMs: Double): Float {
        val p = when {
            nowMs < enterStart -> 0.0
            nowMs < enterStart + motion.enterMs -> easeOut((nowMs - enterStart) / motion.enterMs)
            nowMs < exitStart -> 1.0
            nowMs < exitStart + motion.exitMs -> 1.0 - easeIn((nowMs - exitStart) / motion.exitMs)
            else -> 0.0
        }
        return p.toFloat()
    }

    /** Whether it has gone back for good at [nowMs]: the caller drops it. */
    fun isFinished(nowMs: Double): Boolean = nowMs >= exitStart + motion.exitMs

    /** Whether it is at rest, fully out: the time for hover and click on the in-phone banner. */
    fun isHolding(nowMs: Double): Boolean = nowMs >= enterStart + motion.enterMs && nowMs < exitStart

    /**
     * A new notification takes this banner's place (10.2 "Queue"): the content swaps in place and the hold starts
     * over. While still coming out, it carries on; while going back, it comes out again from where it is.
     */
    fun restart(nowMs: Double) {
        when {
            nowMs < enterStart + motion.enterMs -> exitStart = enterStart + motion.enterMs + motion.holdMs
            nowMs < exitStart -> exitStart = nowMs + motion.holdMs
            else -> {
                val p = progress(nowMs).toDouble()
                // The time into the way out at which easeOut reaches p.
                enterStart = nowMs - (1.0 - sqrt(1.0 - p)) * motion.enterMs
                exitStart = enterStart + motion.enterMs + motion.holdMs
            }
        }
    }

    /** Goes back now, from where it is (dismissed before the end of its hold). Nothing changes if it already is. */
    fun dismiss(nowMs: Double) {
        if (nowMs >= exitStart) return
        val p = progress(nowMs).toDouble()
        // The time into the way back at which 1 - easeIn reaches p.
        exitStart = nowMs - sqrt(1.0 - p) * motion.exitMs
        // The way out must be over before the way back starts, or progress() would still read it.
        enterStart = minOf(enterStart, exitStart - motion.enterMs)
    }

    private fun easeOut(t: Double) = 1.0 - (1.0 - t) * (1.0 - t)

    private fun easeIn(t: Double) = t * t
}
