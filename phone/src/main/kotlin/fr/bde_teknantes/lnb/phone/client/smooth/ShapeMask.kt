package fr.bde_teknantes.lnb.phone.client.smooth

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Anti-aliased coverage masks of rounded rectangles, computed at the screen's own pixel size. They are computed once
 * per shape and pixel size and kept in the mask atlas, never per frame.
 */
object ShapeMask {
    /** Samples per pixel along each axis: 16 x 16 gives 256 coverage levels, the full range of an 8-bit alpha. */
    private const val SAMPLES = 16

    /**
     * Farther than this from the edge, a pixel lies wholly on one side of it: its samples are at most `15/32 x
     * sqrt(2)` (0.66) px from its centre, and the distance to a rounded rectangle changes by at most 1 per px.
     */
    private const val CERTAIN = 0.75f

    /**
     * Coverage (0..255, row by row) of a [width] x [height] px rounded rectangle whose corners have radius [radius]
     * px. With [stroke] > 0, only a stroke of that width along the inside of the edge.
     *
     * Only pixels the edge passes near are sampled (16 x 16); the others are known from their centre's distance to the
     * edge, so the cost follows the perimeter, not the area: a 48 dp circle at GUI scale 9 takes about 1000 sampled
     * pixels instead of 47 000. The result is the same as sampling every pixel.
     */
    fun roundedRect(width: Int, height: Int, radius: Float, stroke: Float = 0f): ByteArray {
        require(width > 0 && height > 0) { "Mask size must be positive, was ${width}x$height" }
        val r = radius.coerceIn(0f, minOf(width, height) / 2f)
        val outer = Rounded(width.toFloat(), height.toFloat(), 0f, r)
        val inner = if (stroke > 0f) Rounded(width - 2 * stroke, height - 2 * stroke, stroke, max(r - stroke, 0f)) else null

        val mask = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cx = x + 0.5f
                val cy = y + 0.5f
                val outerSide = outer.side(cx, cy)
                val innerSide = inner?.side(cx, cy) ?: OUTSIDE
                mask[y * width + x] = when {
                    outerSide == OUTSIDE || innerSide == INSIDE -> 0
                    outerSide == INSIDE && innerSide == OUTSIDE -> -1 // 255
                    else -> sample(x, y, outer, inner)
                }
            }
        }
        return mask
    }

    private const val INSIDE = -1
    private const val EDGE = 0
    private const val OUTSIDE = 1

    private fun sample(x: Int, y: Int, outer: Rounded, inner: Rounded?): Byte {
        var hits = 0
        for (sy in 0 until SAMPLES) {
            val py = y + (sy + 0.5f) / SAMPLES
            for (sx in 0 until SAMPLES) {
                val px = x + (sx + 0.5f) / SAMPLES
                if (outer.contains(px, py) && (inner == null || !inner.contains(px, py))) hits++
            }
        }
        return ((hits * 255 + SAMPLES * SAMPLES / 2) / (SAMPLES * SAMPLES)).toByte()
    }

    /** A rounded rectangle at (inset, inset), [w] x [h], corner radius [r] (at most half the smaller side). */
    private class Rounded(val w: Float, val h: Float, val inset: Float, val r: Float) {
        private val cx = inset + w / 2f
        private val cy = inset + h / 2f

        fun contains(x: Float, y: Float): Boolean {
            if (w <= 0f || h <= 0f) return false
            val dx = max(abs(x - cx) - (w / 2f - r), 0f)
            val dy = max(abs(y - cy) - (h / 2f - r), 0f)
            if (abs(x - cx) > w / 2f || abs(y - cy) > h / 2f) return false
            return dx * dx + dy * dy <= r * r
        }

        /** Whether the pixel centred on ([x], [y]) is wholly [INSIDE], wholly [OUTSIDE], or near the [EDGE]. */
        fun side(x: Float, y: Float): Int {
            if (w <= 0f || h <= 0f) return OUTSIDE
            // Exact signed distance to the rounded rectangle: negative inside, zero exactly where contains() flips.
            val qx = abs(x - cx) - (w / 2f - r)
            val qy = abs(y - cy) - (h / 2f - r)
            val ox = max(qx, 0f)
            val oy = max(qy, 0f)
            val distance = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - r
            return when {
                distance > CERTAIN -> OUTSIDE
                distance < -CERTAIN -> INSIDE
                else -> EDGE
            }
        }
    }
}
