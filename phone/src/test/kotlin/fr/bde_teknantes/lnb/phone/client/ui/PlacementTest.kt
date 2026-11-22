package fr.bde_teknantes.lnb.phone.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Overlays placed near every edge and corner of the 256 x 370 dp viewport (art direction 8.7). */
class PlacementTest {
    private fun inside(box: Box) =
        box.x >= 0f && box.y >= 0f && box.right <= Placement.VIEWPORT_W && box.bottom <= Placement.VIEWPORT_H

    @Test
    fun `a menu opens with its top-right 4 dp under the button's bottom-right`() {
        val anchor = Box(218f, 6f, 32f, 32f)
        assertEquals(Box(82f, 42f, 168f, 128f), Placement.menu(anchor, 3))
    }

    @Test
    fun `a menu opens upward when its bottom would pass y 362`() {
        // 2 items: 88 dp. Under a button ending at 270 the bottom would be 362: still below.
        assertEquals(274f, Placement.menu(Box(100f, 242f, 28f, 28f), 2).y)
        // One dp lower and it passes 362: above, its bottom 4 dp over the button.
        val up = Placement.menu(Box(100f, 243f, 28f, 28f), 2)
        assertEquals(243f - 4f, up.bottom)
    }

    @Test
    fun `a menu opened from the left edge stays inside`() {
        val box = Placement.menu(Box(0f, 100f, 28f, 28f), 1)
        assertEquals(0f, box.x)
    }

    @Test
    fun `a tooltip sits centred 6 dp above its element`() {
        assertEquals(Box(98f, 70f, 60f, 24f), Placement.tooltip(Box(118f, 100f, 20f, 20f), 60f, 24f))
    }

    @Test
    fun `a tooltip goes below when less than 8 dp would remain above`() {
        // Above would start at 38 - 6 - 24 = 8: enough.
        assertEquals(8f, Placement.tooltip(Box(100f, 38f, 20f, 20f), 60f, 24f).y)
        // One dp higher and it would start at 7: below.
        assertEquals(37f + 20f + 6f, Placement.tooltip(Box(100f, 37f, 20f, 20f), 60f, 24f).y)
    }

    @Test
    fun `tooltips near every edge and corner stay inside`() {
        val targets = listOf(
            Box(0f, 0f, 28f, 28f), Box(228f, 0f, 28f, 28f), Box(0f, 342f, 28f, 28f), Box(228f, 342f, 28f, 28f),
            Box(0f, 170f, 16f, 16f), Box(240f, 170f, 16f, 16f), Box(110f, 0f, 36f, 44f), Box(110f, 322f, 36f, 48f),
            Box(0f, 0f, 256f, 44f), Box(4f, 300f, 248f, 56f),
        )
        for (target in targets) {
            for ((w, h) in listOf(40f to 24f, 120f to 24f, 200f to 40f)) {
                val box = Placement.tooltip(target, w, h)
                assertTrue(inside(box), "$w x $h tooltip for $target: $box")
            }
        }
    }

    @Test
    fun `a tooltip at the side keeps 4 dp from the edge`() {
        assertEquals(4f, Placement.tooltip(Box(0f, 100f, 16f, 16f), 120f, 24f).x)
        assertEquals(256f - 4f, Placement.tooltip(Box(240f, 100f, 16f, 16f), 120f, 24f).right)
    }

    @Test
    fun `dialogs follow the art direction's height sum and stay centred on even dp`() {
        // 16 + 18 + 32 + 16 + 16 = 98 without text; each text line adds 18, and 8 for the gap.
        assertEquals(98f, Placement.dialogHeight(1, 0))
        assertEquals(124f, Placement.dialogHeight(1, 1))
        assertEquals(178f, Placement.dialogHeight(2, 3))
        for (lines in 0..3) {
            val h = Placement.dialogHeight(2, lines)
            val top = Placement.dialogTop(h)
            assertEquals(0f, top % 2f)
            assertTrue(top >= 0f && top + h <= Placement.VIEWPORT_H)
            assertTrue(kotlin.math.abs((top + h / 2f) - Placement.VIEWPORT_H / 2f) <= 1f)
        }
    }

    @Test
    fun `the snackbar is 240 x 40 at (8, 322)`() {
        assertEquals(Box(8f, 322f, 240f, 40f), Placement.SNACKBAR)
        assertTrue(inside(Placement.SNACKBAR))
    }
}
