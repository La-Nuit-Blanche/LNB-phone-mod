package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PixelGridTest {
    private val grids = listOf(PixelGrid(2), PixelGrid(3), PixelGrid(4))

    /** Quarter-dp positions from -200 to 200 dp: exact in float, and finer than anything a layout produces. */
    private val positions = (-800..800).map { it / 4f }

    @Test
    fun `snaps dp edges to whole pixels at GUI scale 3`() {
        val grid = PixelGrid(3)
        assertEquals(1.5f, grid.pxPerDp)
        assertEquals(66, grid.snap(44f))
        // Odd dp land on half pixels and round up.
        assertEquals(65, grid.snap(43f))
        assertEquals(2, grid.strokePx(1f))
    }

    @Test
    fun `hairlines are never thinner than a pixel`() {
        assertEquals(1, PixelGrid(2).strokePx(1f))
        assertEquals(1, PixelGrid(2).strokePx(0.25f))
        assertEquals(2, PixelGrid(4).strokePx(1f))
    }

    @Test
    fun `one dp is 1, 1,5 and 2 pixels`() {
        assertEquals(listOf(1f, 1.5f, 2f), grids.map { it.pxPerDp })
        // The viewport, 256 x 370 dp, is 128 x 185 GUI px times the scale.
        for (grid in grids) {
            assertEquals(128 * grid.guiScale, grid.snap(256f))
            assertEquals(185 * grid.guiScale, grid.snap(370f))
        }
    }

    @Test
    fun `even dp are whole pixels at every scale`() {
        for (grid in grids) {
            for (dp in -200..200 step 2) assertEquals(dp * grid.guiScale / 2, grid.snap(dp.toFloat()), "scale ${grid.guiScale}, $dp dp")
        }
    }

    @Test
    fun `halves round up, negative coordinates included`() {
        val grid = PixelGrid(3)
        assertEquals(2, grid.snap(1f)) // 1.5 px
        assertEquals(5, grid.snap(3f)) // 4.5 px
        assertEquals(-1, grid.snap(-1f)) // -1.5 px
        assertEquals(-4, grid.snap(-3f)) // -4.5 px
        assertEquals(0, grid.snap(-0.25f)) // -0.375 px
        assertEquals(-1, grid.snap(-0.5f)) // -0.75 px
        assertEquals(1, PixelGrid(2).snap(0.5f))
        assertEquals(0, PixelGrid(2).snap(-0.5f))
    }

    @Test
    fun `snapping never reorders edges, so touching shapes never gap or overlap`() {
        for (grid in grids) {
            for (i in 1 until positions.size) {
                assertTrue(grid.snap(positions[i - 1]) <= grid.snap(positions[i]), "scale ${grid.guiScale} at ${positions[i]}")
            }
        }
        // A row of cells cut at arbitrary dp: each cell ends exactly where the next starts, and together they cover
        // the row's snapped width with no pixel lost or doubled.
        val cuts = listOf(0f, 13f, 27.5f, 44f, 56f, 81.25f, 120f, 187f, 188f, 244f)
        for (grid in grids) {
            val widths = cuts.zipWithNext { a, b -> grid.snap(b) - grid.snap(a) }
            assertTrue(widths.all { it >= 0 })
            assertEquals(grid.snap(cuts.last()) - grid.snap(cuts.first()), widths.sum())
        }
    }

    @Test
    fun `a shape's snapped size is within a pixel of its exact size, and exact when that is whole`() {
        for (grid in grids) {
            for (x in positions.filterIndexed { i, _ -> i % 7 == 0 }) {
                for (w in listOf(0.25f, 0.5f, 1f, 3f, 6f, 13f, 18f, 32f, 44.75f, 188f)) {
                    val snapped = grid.snap(x + w) - grid.snap(x)
                    val exact = grid.toPx(w)
                    assertTrue(abs(snapped - exact) < 1f, "scale ${grid.guiScale}: $w dp at $x is $snapped px")
                    if (exact == exact.toInt().toFloat()) {
                        assertEquals(exact.toInt(), snapped, "scale ${grid.guiScale}: $w dp at $x")
                    }
                }
            }
        }
    }

    @Test
    fun `a shape on even dp keeps its size wherever it moves`() {
        for (grid in grids) {
            for (w in listOf(2f, 6f, 18f, 28f, 32f, 232f)) {
                val sizes = (-50..50).map { grid.snap(it * 2f + w) - grid.snap(it * 2f) }.toSet()
                assertEquals(setOf(grid.snap(w)), sizes, "scale ${grid.guiScale}, $w dp")
            }
        }
    }

    @Test
    fun `moving by whole pixels moves every edge by the same pixels`() {
        for (grid in grids) {
            // 2 dp is a whole number of pixels at every scale.
            for (dp in positions) {
                for (shift in listOf(-6f, -2f, 2f, 10f)) {
                    assertEquals(grid.snap(dp) + grid.snap(shift), grid.snap(dp + shift), "scale ${grid.guiScale}: $dp + $shift")
                }
            }
        }
    }

    @Test
    fun `odd dp sizes alternate at GUI scale 3, hairlines do not`() {
        val grid = PixelGrid(3)
        // A 1 dp wide rectangle is 2 or 1 px depending on where it starts: why edges belong on even dp (8.1).
        assertEquals(listOf(2, 1, 2, 1), (0..3).map { grid.snap(it + 1f) - grid.snap(it.toFloat()) })
        // A hairline is a thickness, not two edges: always 2 px.
        assertEquals(2, grid.strokePx(1f))
    }

    @Test
    fun `strokes round to the nearest whole pixel`() {
        assertEquals(listOf(1, 2, 2), grids.map { it.strokePx(1f) })
        assertEquals(listOf(2, 3, 4), grids.map { it.strokePx(2f) })
        assertEquals(1, PixelGrid(3).strokePx(0.5f)) // 0.75 px
        assertEquals(2, PixelGrid(3).strokePx(1.5f)) // 2.25 px
        assertEquals(3, PixelGrid(2).strokePx(2.5f)) // 2.5 px, a half: up
        assertEquals(1, PixelGrid(2).strokePx(0f))
        assertEquals(1, PixelGrid(2).strokePx(-3f))
    }

    @Test
    fun `converts between dp and pixels`() {
        for (grid in grids) {
            for (dp in positions) assertEquals(dp, grid.toDp(grid.toPx(dp)), 1e-4f)
        }
        assertEquals(19.5f, PixelGrid(3).toPx(13f), "13 dp body text is 19.5 px at GUI scale 3")
    }

    @Test
    fun `GUI scales below 1 are refused, scales above 4 work`() {
        assertThrows(IllegalArgumentException::class.java) { PixelGrid(0) }
        assertThrows(IllegalArgumentException::class.java) { PixelGrid(-2) }
        assertEquals(0.5f, PixelGrid(1).pxPerDp)
        assertEquals(15, PixelGrid(6).snap(5f))
    }

    @Test
    fun `lines of every text style are evenly spaced at every scale`() {
        // drawTextWrapped puts line i at y + i x lineHeight and snaps each baseline on its own: with a line height
        // that is not a whole number of pixels, lines would alternate between two spacings.
        val styles = listOf(
            TextStyles.TITLE_L, TextStyles.TITLE_M, TextStyles.BODY, TextStyles.BODY_STRONG,
            TextStyles.LABEL, TextStyles.CAPTION, TextStyles.CAPTION_STRONG, TextStyles.MICRO,
        )
        for (grid in grids) {
            for (style in styles) {
                for (top in listOf(0f, 13f, 44.5f, 101f)) {
                    val baseline = top + 13.73f // any baseline offset
                    val spacings = (0..10).map { grid.snap(baseline + (it + 1) * style.lineHeight) - grid.snap(baseline + it * style.lineHeight) }.toSet()
                    assertEquals(setOf(grid.snap(style.lineHeight)), spacings, "scale ${grid.guiScale}, $style from $top")
                }
            }
        }
    }
}
