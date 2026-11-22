package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

class ShapeMaskTest {
    private fun ByteArray.at(width: Int, x: Int, y: Int) = this[y * width + x].toInt() and 0xFF

    /** Total coverage in pixels. */
    private fun ByteArray.area() = sumOf { it.toInt() and 0xFF } / 255.0

    private fun roundedArea(w: Double, h: Double, r: Double) = if (w <= 0 || h <= 0) 0.0 else w * h - (4 - PI) * r * r

    @Test
    fun `a rectangle without radius is fully covered`() {
        val mask = ShapeMask.roundedRect(7, 3, 0f)
        assertTrue(mask.all { (it.toInt() and 0xFF) == 255 })
        assertTrue(ShapeMask.roundedRect(5, 5, -3f).all { (it.toInt() and 0xFF) == 255 }, "a negative radius is no radius")
    }

    @Test
    fun `the smallest masks`() {
        assertArrayEquals(byteArrayOf(-1), ShapeMask.roundedRect(1, 1, 0f))
        // A 1 px disc covers pi/4 of its pixel (16 x 16 samples count a little more: 207 rather than 200).
        val dot = ShapeMask.roundedRect(1, 1, 0.5f).at(1, 0, 0)
        assertEquals(255 * PI / 4, dot.toDouble(), 10.0)
        // A 2 px circle: four quarter discs.
        val circle = ShapeMask.roundedRect(2, 2, 1f)
        for (i in 0 until 4) assertEquals(255 * PI / 4, (circle[i].toInt() and 0xFF).toDouble(), 4.0)
        // 1 px tall pill: round ends, full middle.
        val pill = ShapeMask.roundedRect(5, 1, 99f)
        assertEquals(255, pill.at(5, 2, 0))
        assertEquals(255 * (0.5 + PI / 8), pill.at(5, 0, 0).toDouble(), 10.0) // half a disc, half a square
        assertArrayEquals(pill, ShapeMask.roundedRect(5, 1, 0.5f), "the radius stops at half the height")
    }

    @Test
    fun `rejects empty masks`() {
        assertThrows(IllegalArgumentException::class.java) { ShapeMask.roundedRect(0, 4, 1f) }
        assertThrows(IllegalArgumentException::class.java) { ShapeMask.roundedRect(4, -1, 1f) }
    }

    @Test
    fun `a radius larger than half the size makes a pill or a circle`() {
        assertArrayEquals(ShapeMask.roundedRect(48, 20, 10f), ShapeMask.roundedRect(48, 20, 500f))
        assertArrayEquals(ShapeMask.roundedRect(24, 24, 12f), ShapeMask.roundedRect(24, 24, 24f))
    }

    @Test
    fun `masks are symmetric`() {
        val cases = listOf(
            listOf(48f, 48f, 24f, 0f), listOf(31f, 31f, 15f, 0f), listOf(132f, 48f, 24f, 0f), listOf(40f, 27f, 13.5f, 0f),
            listOf(31f, 31f, 15f, 2f), listOf(49f, 21f, 10.5f, 1f), listOf(9f, 9f, 4.5f, 0f), listOf(30f, 50f, 7.25f, 3f),
        )
        for ((wf, hf, r, stroke) in cases) {
            val w = wf.toInt()
            val h = hf.toInt()
            val mask = ShapeMask.roundedRect(w, h, r, stroke)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = mask.at(w, x, y)
                    assertEquals(v, mask.at(w, w - 1 - x, y), "mirror x of ${w}x$h r $r at ($x, $y)")
                    assertEquals(v, mask.at(w, x, h - 1 - y), "mirror y of ${w}x$h r $r at ($x, $y)")
                    if (w == h) assertEquals(v, mask.at(w, y, x), "diagonal of ${w}x$h r $r at ($x, $y)")
                }
            }
        }
    }

    @Test
    fun `coverage matches the shape's area`() {
        for ((w, h, r) in listOf(Triple(48, 48, 24f), Triple(232, 64, 21f), Triple(31, 31, 15f), Triple(40, 27, 13.5f), Triple(9, 9, 4.5f))) {
            val mask = ShapeMask.roundedRect(w, h, r)
            val expected = roundedArea(w.toDouble(), h.toDouble(), r.toDouble())
            assertEquals(expected, mask.area(), max(0.5, expected * 0.002), "${w}x$h r $r")
        }
    }

    @Test
    fun `coverage at known points of a circle`() {
        val mask = ShapeMask.roundedRect(48, 48, 24f)
        assertEquals(255, mask.at(48, 24, 24))
        assertEquals(255, mask.at(48, 0, 23), "the middle of the left edge")
        assertEquals(0, mask.at(48, 0, 0), "the corner, outside the circle")
        assertEquals(0, mask.at(48, 47, 47))
        // Coverage only falls from the centre to the corner, along the diagonal.
        for (i in 1 until 24) assertTrue(mask.at(48, 24 - i, 24 - i) <= mask.at(48, 25 - i, 25 - i))
        // Only the pixels the circle passes through are partly covered: about its circumference.
        val partial = mask.count { (it.toInt() and 0xFF) in 1..254 }
        assertTrue(partial in 120..220, "$partial partly covered pixels")
    }

    @Test
    fun `a stroke is its width in whole pixels along straight edges`() {
        for (stroke in 1..3) {
            val mask = ShapeMask.roundedRect(40, 40, 10f, stroke.toFloat())
            for (x in 0 until 40) {
                val expected = if (x < stroke || x >= 40 - stroke) 255 else 0
                assertEquals(expected, mask.at(40, x, 20), "stroke $stroke, middle row, x $x")
                assertEquals(expected, mask.at(40, 20, x), "stroke $stroke, middle column, y $x")
            }
        }
        // Without radius, the whole ring is solid.
        val ring = ShapeMask.roundedRect(6, 5, 0f, 1f)
        for (y in 0 until 5) for (x in 0 until 6) {
            assertEquals(if (x == 0 || x == 5 || y == 0 || y == 4) 255 else 0, ring.at(6, x, y), "($x, $y)")
        }
    }

    @Test
    fun `a stroke covers the area between its outer and inner edges`() {
        for ((size, r, stroke) in listOf(Triple(48, 24f, 2f), Triple(64, 21f, 1f), Triple(42, 15f, 3f), Triple(48, 1f, 2f))) {
            val outer = roundedArea(size.toDouble(), size.toDouble(), r.toDouble())
            val inner = roundedArea(size - 2.0 * stroke, size - 2.0 * stroke, max(r - stroke, 0f).toDouble())
            val mask = ShapeMask.roundedRect(size, size, r, stroke)
            assertEquals(outer - inner, mask.area(), max(0.5, (outer - inner) * 0.01), "size $size r $r stroke $stroke")
        }
    }

    @Test
    fun `a stroke as thick as half the shape fills it`() {
        assertArrayEquals(ShapeMask.roundedRect(8, 8, 3f), ShapeMask.roundedRect(8, 8, 3f, 4f))
        assertArrayEquals(ShapeMask.roundedRect(8, 6, 3f), ShapeMask.roundedRect(8, 6, 3f, 5f))
        assertArrayEquals(ShapeMask.roundedRect(1, 1, 0.5f), ShapeMask.roundedRect(1, 1, 0.5f, 1f))
    }

    @Test
    fun `a stroke's inner corner keeps the stroke's width`() {
        // Along the diagonal of a rounded corner, the ring is about as thick as the stroke: the inner radius is the
        // outer one minus the stroke, both centred on the same point.
        val size = 60
        val r = 20f
        val mask = ShapeMask.roundedRect(size, size, r, 2f)
        val ink = (0 until size / 2).sumOf { mask.at(size, it, it) } / 255.0
        // The diagonal crosses the ring over 2 px of distance, so about 2 / sqrt(2) pixels of diagonal steps.
        assertTrue(abs(ink - 2 / Math.sqrt(2.0)) < 0.5, "diagonal ink $ink")
    }
}
