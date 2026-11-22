package fr.bde_teknantes.lnb.phone.client.smooth

import kotlin.math.ceil
import kotlin.math.max

/**
 * How a rounded rectangle is drawn from one small mask tile: corners of [cornerSize] px around a 1 px middle row and
 * column that stretch. Each axis slices on its own: an axis too short for two corners (a pill of odd pixel height, a
 * small circle) is taken whole from the tile, so the tile still serves every length of the other axis. Apart from the
 * canvas so the slicing can be checked against a full-size mask without a GPU.
 */
object NineSlice {
    /** Corner size of the tile: `ceil(radius)` px, so the middle row and column are straight, and at least the stroke. */
    fun cornerSize(radiusPx: Float, strokePx: Int): Int = max(ceil(radiusPx - 1e-3f).toInt(), strokePx)

    /** Whether an axis [length] px long holds two corners, and so slices. */
    fun slices(length: Int, corner: Int): Boolean = length >= 2 * corner

    /** The tile's size along an axis [length] px long: corners around a 1 px middle, or the whole length. */
    fun tileSize(length: Int, corner: Int): Int = if (slices(length, corner)) 2 * corner + 1 else length

    /** Whether a [width] x [height] px shape slices along both axes. */
    fun fits(width: Int, height: Int, corner: Int): Boolean = slices(width, corner) && slices(height, corner)

    /** [forEachSlice] for a shape that slices along both axes, from a `2c + 1` square tile. */
    inline fun forEachSlice(
        l: Int, t: Int, r: Int, b: Int, c: Int,
        slice: (l: Int, t: Int, r: Int, b: Int, u0: Int, v0: Int, u1: Int, v1: Int) -> Unit,
    ) = forEachSlice(l, t, r, b, c, sliceX = true, sliceY = true, slice)

    /**
     * Calls [slice] for each non-empty part of pixels [l]..[r] x [t]..[b] with the texels of the tile it shows:
     * destination left, top, right, bottom, then source u0, v0, u1, v1. Along an axis that slices, the tile is `2c + 1`
     * texels (corners 1:1, the middle texel stretched); along one that does not, it is the whole length, 1:1.
     */
    inline fun forEachSlice(
        l: Int, t: Int, r: Int, b: Int, c: Int, sliceX: Boolean, sliceY: Boolean,
        slice: (l: Int, t: Int, r: Int, b: Int, u0: Int, v0: Int, u1: Int, v1: Int) -> Unit,
    ) {
        val w = r - l
        val h = b - t
        val xs = if (sliceX) intArrayOf(l, l + c, r - c, r) else intArrayOf(l, r, r, r)
        val us = if (sliceX) intArrayOf(0, c, c + 1, 2 * c + 1) else intArrayOf(0, w, w, w)
        val ys = if (sliceY) intArrayOf(t, t + c, b - c, b) else intArrayOf(t, b, b, b)
        val vs = if (sliceY) intArrayOf(0, c, c + 1, 2 * c + 1) else intArrayOf(0, h, h, h)
        for (row in 0..2) {
            if (ys[row + 1] <= ys[row]) continue
            for (col in 0..2) {
                if (xs[col + 1] <= xs[col]) continue
                slice(xs[col], ys[row], xs[col + 1], ys[row + 1], us[col], vs[row], us[col + 1], vs[row + 1])
            }
        }
    }
}
