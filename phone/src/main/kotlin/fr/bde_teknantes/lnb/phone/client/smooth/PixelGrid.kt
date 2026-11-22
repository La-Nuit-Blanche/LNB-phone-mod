package fr.bde_teknantes.lnb.phone.client.smooth

import kotlin.math.floor
import kotlin.math.max

/**
 * The real-pixel grid of one GUI scale, seen from the smooth layer. 2 dp = 1 GUI px, so one dp is `guiScale / 2`
 * pixels: 1, 1.5 and 2 at GUI scale 2, 3 and 4.
 *
 * Every edge goes through [snap] on its own, rather than a position plus a snapped size: two shapes that share an
 * edge in dp then share it on screen, with no gap or overlap, even where 1 dp is 1.5 px.
 */
class PixelGrid(val guiScale: Int) {
    init {
        require(guiScale >= 1) { "GUI scale must be at least 1, was $guiScale" }
    }

    val pxPerDp: Float = guiScale / 2f

    fun toPx(dp: Float): Float = dp * pxPerDp

    fun toDp(px: Float): Float = px / pxPerDp

    /** The pixel edge nearest to [dp]. Halves round up, the same way at every position. */
    fun snap(dp: Float): Int = floor(dp * pxPerDp + 0.5f).toInt()

    /**
     * Thickness in whole pixels of a line [dp] thick: the nearest whole number, never below 1 (art direction 8.1).
     * A 1 dp hairline is 1, 2 and 2 px at GUI scale 2, 3 and 4.
     */
    fun strokePx(dp: Float): Int = max(1, floor(dp * pxPerDp + 0.5f).toInt())
}
