package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * GUI scales above 4 are common ("auto" picks 5 at 1920 x 1200, 6 at 2560 x 1440, 9 at 4K): masks must stay exact
 * and cheap there, and slicing must work axis by axis.
 */
class HighScaleTest {
    /** The mask as it was computed before: every pixel sampled 16 x 16. The fast path must give the same bytes. */
    private fun bruteForce(width: Int, height: Int, radius: Float, stroke: Float): ByteArray {
        val r = radius.coerceIn(0f, minOf(width, height) / 2f)
        fun inside(x: Float, y: Float, w: Float, h: Float, inset: Float, rr: Float): Boolean {
            if (w <= 0f || h <= 0f) return false
            val cx = inset + w / 2f
            val cy = inset + h / 2f
            val dx = max(abs(x - cx) - (w / 2f - rr), 0f)
            val dy = max(abs(y - cy) - (h / 2f - rr), 0f)
            if (abs(x - cx) > w / 2f || abs(y - cy) > h / 2f) return false
            return dx * dx + dy * dy <= rr * rr
        }
        return ByteArray(width * height) { i ->
            val x = i % width
            val y = i / width
            var hits = 0
            for (sy in 0 until 16) for (sx in 0 until 16) {
                val px = x + (sx + 0.5f) / 16
                val py = y + (sy + 0.5f) / 16
                val inOuter = inside(px, py, width.toFloat(), height.toFloat(), 0f, r)
                val inInner = stroke > 0f && inside(px, py, width - 2 * stroke, height - 2 * stroke, stroke, max(r - stroke, 0f))
                if (inOuter && !inInner) hits++
            }
            ((hits * 255 + 128) / 256).toByte()
        }
    }

    @Test
    fun `masks are byte for byte what sampling every pixel gives`() {
        val random = Random(42)
        repeat(300) {
            val w = random.nextInt(1, 90)
            val h = random.nextInt(1, 90)
            val radius = random.nextInt(0, 100) / 4f
            val stroke = if (random.nextBoolean()) 0f else random.nextInt(1, 6).toFloat()
            assertArrayEquals(bruteForce(w, h, radius, stroke), ShapeMask.roundedRect(w, h, radius, stroke), "${w}x$h r $radius s $stroke")
        }
    }

    @Test
    fun `a 48 dp circle at GUI scale 9 is cheap`() {
        // 216 px: about 47 000 pixels, 12 million samples by brute force.
        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            ShapeMask.roundedRect(216, 216, 108f)
            ShapeMask.roundedRect(216, 216, 108f, 9f)
        }
    }

    /** Pixels drawn by slicing [tile] ([tw] x [th]) over a [width] x [height] shape with nearest sampling. */
    private fun compose(tile: ByteArray, tw: Int, c: Int, sliceX: Boolean, sliceY: Boolean, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        val hits = IntArray(width * height)
        NineSlice.forEachSlice(0, 0, width, height, c, sliceX, sliceY) { l, t, r, b, u0, v0, u1, v1 ->
            for (y in t until b) for (x in l until r) {
                val u = u0 + floor((x - l + 0.5f) * (u1 - u0) / (r - l)).toInt()
                val v = v0 + floor((y - t + 0.5f) * (v1 - v0) / (b - t)).toInt()
                out[y * width + x] = tile[v * tw + u]
                hits[y * width + x]++
            }
        }
        assertTrue(hits.all { it == 1 }, "slices cover every pixel of ${width}x$height exactly once")
        return out
    }

    private fun assertSlicesExactly(width: Int, height: Int, radius: Float, stroke: Int, context: String) {
        val c = NineSlice.cornerSize(radius, stroke)
        val sliceX = NineSlice.slices(width, c)
        val sliceY = NineSlice.slices(height, c)
        val tw = NineSlice.tileSize(width, c)
        val th = NineSlice.tileSize(height, c)
        val tile = ShapeMask.roundedRect(tw, th, radius, stroke.toFloat())
        val full = ShapeMask.roundedRect(width, height, radius, stroke.toFloat())
        assertArrayEquals(full, compose(tile, tw, c, sliceX, sliceY, width, height), context)
    }

    @Test
    fun `each axis slices on its own, exactly`() {
        val random = Random(9)
        repeat(500) {
            val width = random.nextInt(1, 80)
            val height = random.nextInt(1, 80)
            val radius = min(random.nextInt(0, 81) / 2f, min(width, height) / 2f)
            val stroke = if (random.nextBoolean()) 0 else random.nextInt(1, 4)
            assertSlicesExactly(width, height, radius, stroke, "${width}x$height radius $radius stroke $stroke")
        }
    }

    @Test
    fun `a pill of odd pixel height uses one tile for every width`() {
        // 47 px tall: radius 23.5, corners of 24, so the height cannot slice; the width still does.
        val c = NineSlice.cornerSize(23.5f, 0)
        assertEquals(47, NineSlice.tileSize(47, c))
        for (width in listOf(48, 96, 133, 348)) assertEquals(2 * c + 1, NineSlice.tileSize(width, c))
        for (width in listOf(48, 96, 133)) assertSlicesExactly(width, 47, 23.5f, 0, "pill $width x 47")
    }

    /** Shapes of the art direction in dp: width, height, radius, stroke. */
    private val kit = listOf(
        listOf(232f, 40f, 10f, 0f), listOf(232f, 40f, 10f, 1f), listOf(232f, 64f, 14f, 0f), listOf(232f, 64f, 14f, 1f),
        listOf(88f, 32f, 999f, 0f), listOf(104f, 32f, 999f, 1f), listOf(88f, 32f, 999f, 2f), listOf(36f, 20f, 999f, 0f),
        listOf(48f, 48f, 48f, 0f), listOf(32f, 32f, 32f, 0f), listOf(28f, 28f, 28f, 0f), listOf(16f, 16f, 16f, 0f),
        listOf(6f, 6f, 6f, 0f), listOf(48f, 3f, 999f, 0f),
    )

    @Test
    fun `the kit's shapes draw exactly at every GUI scale from 2 to 9, at even and odd positions`() {
        for (scale in 2..9) {
            val grid = PixelGrid(scale)
            for (at in listOf(12f, 13f)) {
                for ((w, h, radiusDp, strokeDp) in kit) {
                    val pw = grid.snap(at + w) - grid.snap(at)
                    val ph = grid.snap(at + h) - grid.snap(at)
                    val radius = min(grid.toPx(radiusDp), min(pw, ph) / 2f)
                    val stroke = if (strokeDp > 0f) grid.strokePx(strokeDp) else 0
                    val context = "scale $scale at $at: ${w}x$h dp, radius $radiusDp, stroke $strokeDp"
                    assertSlicesExactly(pw, ph, radius, stroke, context)
                    // Tiles stay small: a corner tile along every axis that slices, the true size otherwise.
                    val c = NineSlice.cornerSize(radius, stroke)
                    assertTrue(NineSlice.tileSize(pw, c) <= 2 * c + 1 && NineSlice.tileSize(ph, c) <= 2 * c + 1, context)
                }
            }
        }
    }

    @Test
    fun `atlas pages grow above GUI scale 4 and hold the largest tile`() {
        assertEquals(1024, SmoothResources.atlasPageSize(4))
        assertEquals(2048, SmoothResources.atlasPageSize(5))
        // The largest kit tile at scale 9: a 48 dp disc, 216 px, or the 2c + 1 tile of a 24 dp radius, 217 px.
        assertTrue(MaskAtlas(SmoothResources.atlasPageSize(9)).fits(217, 217))
    }
}
