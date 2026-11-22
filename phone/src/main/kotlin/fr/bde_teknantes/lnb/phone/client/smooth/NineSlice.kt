package fr.bde_teknantes.lnb.phone.client.smooth

import kotlin.math.ceil
import kotlin.math.max

/**
 * How a rounded rectangle is drawn from one small mask tile: corners of [cornerSize] px around a 1 px middle row and
 * column that stretch. Apart from the canvas so the slicing can be checked against a full-size mask without a GPU.
 */
object NineSlice {
    /** Corner size of the tile: `ceil(radius)` px, so the middle row and column are straight, and at least the stroke. */
    fun cornerSize(radiusPx: Float, strokePx: Int): Int = max(ceil(radiusPx - 1e-3f).toInt(), strokePx)

    /** Whether a [width] x [height] px shape holds two corners each way; smaller ones get a mask of their own. */
    fun fits(width: Int, height: Int, corner: Int): Boolean = width >= 2 * corner && height >= 2 * corner

    /**
     * Calls [slice] for each non-empty part of pixels [l]..[r] x [t]..[b] with the texels of a `2c + 1` square tile it
     * shows: destination left, top, right, bottom, then source u0, v0, u1, v1.
     */
    inline fun forEachSlice(
        l: Int, t: Int, r: Int, b: Int, c: Int,
        slice: (l: Int, t: Int, r: Int, b: Int, u0: Int, v0: Int, u1: Int, v1: Int) -> Unit,
    ) {
        val xs = intArrayOf(l, l + c, r - c, r)
        val ys = intArrayOf(t, t + c, b - c, b)
        val us = intArrayOf(0, c, c + 1, 2 * c + 1)
        for (row in 0..2) {
            if (ys[row + 1] <= ys[row]) continue
            for (col in 0..2) {
                if (xs[col + 1] <= xs[col]) continue
                slice(xs[col], ys[row], xs[col + 1], ys[row + 1], us[col], us[row], us[col + 1], us[row + 1])
            }
        }
    }
}
