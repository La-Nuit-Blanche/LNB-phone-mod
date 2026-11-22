package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

class NineSliceTest {
    /** The pixels drawn by slicing [tile] (a `2c + 1` square) over a [width] x [height] shape, nearest sampling. */
    private fun compose(tile: ByteArray, c: Int, width: Int, height: Int): ByteArray {
        val n = 2 * c + 1
        val out = ByteArray(width * height)
        val hits = IntArray(width * height)
        NineSlice.forEachSlice(0, 0, width, height, c) { l, t, r, b, u0, v0, u1, v1 ->
            for (y in t until b) {
                for (x in l until r) {
                    val u = u0 + floor((x - l + 0.5f) * (u1 - u0) / (r - l)).toInt()
                    val v = v0 + floor((y - t + 0.5f) * (v1 - v0) / (b - t)).toInt()
                    out[y * width + x] = tile[v * n + u]
                    hits[y * width + x]++
                }
            }
        }
        assertTrue(hits.all { it == 1 }, "slices cover every pixel of ${width}x$height exactly once")
        return out
    }

    /** Asserts that the sliced tile draws the same pixels as a mask computed at full size. */
    private fun assertSlicesLikeFullMask(width: Int, height: Int, radius: Float, stroke: Int, context: String) {
        val c = NineSlice.cornerSize(radius, stroke)
        assertTrue(NineSlice.fits(width, height, c), context)
        val tile = ShapeMask.roundedRect(2 * c + 1, 2 * c + 1, radius, stroke.toFloat())
        val full = ShapeMask.roundedRect(width, height, radius, stroke.toFloat())
        assertArrayEquals(full, compose(tile, c, width, height), context)
    }

    @Test
    fun `corners are the radius rounded up, and at least the stroke`() {
        assertEquals(15, NineSlice.cornerSize(15f, 0))
        assertEquals(14, NineSlice.cornerSize(13.5f, 0))
        assertEquals(1, NineSlice.cornerSize(0.5f, 0))
        // Float noise just above a whole radius does not add a pixel.
        assertEquals(15, NineSlice.cornerSize(15.0004f, 0))
        assertEquals(16, NineSlice.cornerSize(15.01f, 0))
        assertEquals(3, NineSlice.cornerSize(1f, 3))
        assertEquals(2, NineSlice.cornerSize(0f, 2))
    }

    @Test
    fun `slices tile the destination exactly, at every position`() {
        for (c in 0..4) {
            for (w in 2 * c..2 * c + 5) {
                for (h in 2 * c..2 * c + 3) {
                    val hits = HashMap<Pair<Int, Int>, Int>()
                    var area = 0
                    NineSlice.forEachSlice(-7, 13, -7 + w, 13 + h, c) { l, t, r, b, u0, v0, u1, v1 ->
                        assertTrue(r > l && b > t)
                        // Corners are drawn 1:1; the middle row and column stretch one texel.
                        if (u1 - u0 != 1) assertEquals(r - l, u1 - u0)
                        if (v1 - v0 != 1) assertEquals(b - t, v1 - v0)
                        assertTrue(u0 >= 0 && v0 >= 0 && u1 <= 2 * c + 1 && v1 <= 2 * c + 1)
                        area += (r - l) * (b - t)
                        for (y in t until b) for (x in l until r) hits.merge(x to y, 1, Int::plus)
                    }
                    assertEquals(w * h, area, "c=$c ${w}x$h")
                    assertTrue(hits.values.all { it == 1 } && hits.size == w * h, "c=$c ${w}x$h")
                }
            }
        }
    }

    @Test
    fun `shapes smaller than two corners do not slice`() {
        assertTrue(NineSlice.fits(30, 30, 15))
        assertFalse(NineSlice.fits(29, 40, 15))
        assertFalse(NineSlice.fits(40, 29, 15))
    }

    @Test
    fun `a sliced shape is pixel for pixel the full-size mask`() {
        val random = Random(7)
        repeat(400) {
            val radius = random.nextInt(0, 41) / 2f
            val stroke = if (random.nextBoolean()) 0 else random.nextInt(1, 5)
            val c = NineSlice.cornerSize(radius, stroke)
            val width = 2 * c + random.nextInt(0, 30)
            val height = 2 * c + random.nextInt(0, 30)
            if (width == 0 || height == 0) return@repeat
            assertSlicesLikeFullMask(width, height, radius, stroke, "${width}x$height radius $radius stroke $stroke")
        }
    }

    /** Shapes of the art direction (8.5, 8.7, 9.x), in dp: width, height, radius, stroke. */
    private val kit = listOf(
        listOf(232f, 40f, 10f, 0f), // text field
        listOf(232f, 40f, 10f, 1f), // text field border
        listOf(232f, 196f, 10f, 1f), // compose text area border
        listOf(232f, 48f, 10f, 0f), // snackbar
        listOf(232f, 64f, 14f, 0f), // banner
        listOf(232f, 64f, 14f, 1f), // banner edge
        listOf(232f, 160f, 14f, 0f), // dialog
        listOf(88f, 32f, 999f, 0f), // Publier, a full-radius button
        listOf(104f, 32f, 999f, 1f), // secondary button border
        listOf(88f, 32f, 999f, 2f), // focus ring
        listOf(232f, 32f, 999f, 0f), // segmented control track
        listOf(32f, 32f, 32f, 0f), // avatar
        listOf(28f, 28f, 28f, 0f), // hover disc
        listOf(16f, 16f, 16f, 0f), // count badge
        listOf(6f, 6f, 6f, 0f), // unseen replies dot
    )

    @Test
    fun `the kit's shapes slice exactly at GUI scale 2, 3 and 4`() {
        val unsliced = ArrayList<String>()
        for (scale in 2..4) {
            val grid = PixelGrid(scale)
            for ((w, h, radiusDp, strokeDp) in kit) {
                // As SmoothCanvas.roundRect does, at an even-dp position.
                val pw = grid.snap(12f + w) - grid.snap(12f)
                val ph = grid.snap(10f + h) - grid.snap(10f)
                val radius = min(grid.toPx(radiusDp), min(pw, ph) / 2f)
                val stroke = if (strokeDp > 0f) grid.strokePx(strokeDp) else 0
                val c = NineSlice.cornerSize(radius, stroke)
                val context = "scale $scale: ${w}x$h dp, radius $radiusDp, stroke $strokeDp"
                if (NineSlice.fits(pw, ph, c)) {
                    assertSlicesLikeFullMask(pw, ph, radius, stroke, context)
                } else {
                    unsliced += context
                }
            }
        }
        // Only shapes whose odd pixel size leaves no middle draw their own full mask, which is just as exact.
        assertEquals(listOf("scale 3: 6.0x6.0 dp, radius 6.0, stroke 0.0"), unsliced)
    }
}
