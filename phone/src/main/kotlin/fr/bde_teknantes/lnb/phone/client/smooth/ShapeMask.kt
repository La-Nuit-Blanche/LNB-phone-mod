package fr.bde_teknantes.lnb.phone.client.smooth

import kotlin.math.abs
import kotlin.math.max

/**
 * Anti-aliased coverage masks of rounded rectangles, computed at the screen's own pixel size. They are computed once
 * per shape and pixel size and kept in the mask atlas, never per frame.
 */
object ShapeMask {
    /** Samples per pixel along each axis: 16 x 16 gives 256 coverage levels, the full range of an 8-bit alpha. */
    private const val SAMPLES = 16

    /**
     * Coverage (0..255, row by row) of a [width] x [height] px rounded rectangle whose corners have radius [radius]
     * px. With [stroke] > 0, only a stroke of that width along the inside of the edge.
     */
    fun roundedRect(width: Int, height: Int, radius: Float, stroke: Float = 0f): ByteArray {
        require(width > 0 && height > 0) { "Mask size must be positive, was ${width}x$height" }
        val r = radius.coerceIn(0f, minOf(width, height) / 2f)
        val outer = Rounded(width.toFloat(), height.toFloat(), 0f, r)
        val inner = if (stroke > 0f) Rounded(width - 2 * stroke, height - 2 * stroke, stroke, max(r - stroke, 0f)) else null

        val mask = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var hits = 0
                for (sy in 0 until SAMPLES) {
                    val py = y + (sy + 0.5f) / SAMPLES
                    for (sx in 0 until SAMPLES) {
                        val px = x + (sx + 0.5f) / SAMPLES
                        if (outer.contains(px, py) && (inner == null || !inner.contains(px, py))) hits++
                    }
                }
                mask[y * width + x] = ((hits * 255 + SAMPLES * SAMPLES / 2) / (SAMPLES * SAMPLES)).toByte()
            }
        }
        return mask
    }

    /** A rounded rectangle at (inset, inset), [w] x [h], corner radius [r]. */
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
    }
}
