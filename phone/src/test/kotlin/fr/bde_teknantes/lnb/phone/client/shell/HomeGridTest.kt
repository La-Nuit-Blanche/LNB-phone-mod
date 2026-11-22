package fr.bde_teknantes.lnb.phone.client.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Section 6 of the art direction: the grid, hit testing, pages, badges. Viewport-local GUI px. */
class HomeGridTest {
    // The app whose 24 x 24 hover ring covers pixel (vx, vy), from the document's positions: icon at
    // (6 + 24c, 8 + 24r), ring at (icon - 2), 24 wide.
    private fun expectedApp(vx: Int, vy: Int, page: Int, apps: Int): Int? {
        for (r in 0 until 6) for (c in 0 until 5) {
            val left = 6 + 24 * c - 2
            val top = 8 + 24 * r - 2
            if (vx in left until left + 24 && vy in top until top + 24) {
                return (page * 30 + r * 5 + c).takeIf { it < apps }
            }
        }
        return null
    }

    @Test
    fun `icon positions`() {
        for (slot in 0 until 30) {
            assertEquals(6 + 24 * (slot % 5), HomeGrid.iconX(slot))
            assertEquals(8 + 24 * (slot / 5), HomeGrid.iconY(slot))
        }
        // 6: the grid spans vx 6..121, vy 8..147.
        assertEquals(121, HomeGrid.iconX(29) + HomeGrid.ICON_SIZE - 1)
        assertEquals(147, HomeGrid.iconY(29) + HomeGrid.ICON_SIZE - 1)
    }

    @Test
    fun `hit testing on every pixel of the viewport, full page and partial pages`() {
        for (apps in listOf(0, 1, 4, 5, 6, 29, 30)) {
            for (vx in 0 until Device.VIEWPORT_WIDTH) for (vy in 0 until Device.VIEWPORT_HEIGHT) {
                val expected = expectedApp(vx, vy, 0, apps)
                // Anywhere inside the pixel answers the same, its first and last fraction alike.
                assertEquals(expected, HomeGrid.appAt(vx.toDouble(), vy.toDouble(), 0, apps), "($vx, $vy) of $apps apps")
                assertEquals(expected, HomeGrid.appAt(vx + 0.999, vy + 0.999, 0, apps), "($vx.999, $vy.999) of $apps apps")
            }
        }
    }

    @Test
    fun `neighbouring icons touch at the boundary pixels between them`() {
        // Between columns c and c + 1 the rings meet at vx 27 | 28 (and every 24 px after).
        for (c in 0 until 4) {
            val last = 27 + 24 * c
            assertEquals(c, HomeGrid.appAt(last.toDouble(), 18.0, 0, 30))
            assertEquals(c + 1, HomeGrid.appAt(last + 1.0, 18.0, 0, 30))
        }
        // Between rows r and r + 1 at vy 29 | 30.
        for (r in 0 until 5) {
            val last = 29 + 24 * r
            assertEquals(r * 5, HomeGrid.appAt(16.0, last.toDouble(), 0, 30))
            assertEquals((r + 1) * 5, HomeGrid.appAt(16.0, last + 1.0, 0, 30))
        }
        // The four rings meeting at a corner.
        assertEquals(0, HomeGrid.appAt(27.0, 29.0, 0, 30))
        assertEquals(1, HomeGrid.appAt(28.0, 29.0, 0, 30))
        assertEquals(5, HomeGrid.appAt(27.0, 30.0, 0, 30))
        assertEquals(6, HomeGrid.appAt(28.0, 30.0, 0, 30))
    }

    @Test
    fun `the grid's outer edges`() {
        // Left: the ring starts at vx 4.
        assertNull(HomeGrid.appAt(3.999, 18.0, 0, 30))
        assertEquals(0, HomeGrid.appAt(4.0, 18.0, 0, 30))
        // Right: the last ring ends at vx 123.
        assertEquals(4, HomeGrid.appAt(123.999, 18.0, 0, 30))
        assertNull(HomeGrid.appAt(124.0, 18.0, 0, 30))
        // Top: the ring starts at vy 6.
        assertNull(HomeGrid.appAt(16.0, 5.999, 0, 30))
        assertEquals(0, HomeGrid.appAt(16.0, 6.0, 0, 30))
        // Bottom: the last ring ends at vy 149, above the label line.
        assertEquals(25, HomeGrid.appAt(16.0, 149.999, 0, 30))
        assertNull(HomeGrid.appAt(16.0, 150.0, 0, 30))
        // Outside the viewport altogether.
        assertNull(HomeGrid.appAt(-0.5, 18.0, 0, 30))
        assertNull(HomeGrid.appAt(16.0, -0.5, 0, 30))
        assertNull(HomeGrid.appAt(-30.0, -30.0, 0, 30))
        assertNull(HomeGrid.appAt(500.0, 18.0, 0, 30))
    }

    @Test
    fun `the flows' pointer positions`() {
        // P-6.2: screen-local (16, 29) is viewport (16, 18), the centre of Feed (first app).
        assertEquals(0, HomeGrid.appAt(16.0, 18.0, 0, 5))
        // P-6.1: screen-local (70, 120), past the last of 5 apps.
        assertNull(HomeGrid.appAt(70.0, 109.0, 0, 5))
        // P-12.1: screen-local (112, 29), Réglages as the fifth and last app (column 4, row 0).
        assertEquals(4, HomeGrid.appAt(112.0, 18.0, 0, 5))
    }

    @Test
    fun `hit testing on later pages`() {
        assertEquals(30, HomeGrid.appAt(16.0, 18.0, 1, 31))
        assertNull(HomeGrid.appAt(40.0, 18.0, 1, 31))
        assertEquals(59, HomeGrid.appAt(112.0, 138.0, 1, 60))
        assertEquals(65, HomeGrid.appAt(16.0, 42.0, 2, 100))
        // A page with no apps at all.
        assertNull(HomeGrid.appAt(16.0, 18.0, 3, 61))
    }

    @Test
    fun `page counts`() {
        val expected = mapOf(0 to 1, 1 to 1, 29 to 1, 30 to 1, 31 to 2, 59 to 2, 60 to 2, 61 to 3, 300 to 10, 301 to 11)
        for ((apps, pages) in expected) assertEquals(pages, HomeGrid.pageCount(apps), "$apps apps")
        assertEquals(0, HomeGrid.pageOf(0))
        assertEquals(0, HomeGrid.pageOf(29))
        assertEquals(1, HomeGrid.pageOf(30))
        assertEquals(3, HomeGrid.pageOf(119))
    }

    @Test
    fun `apps on each page`() {
        assertEquals(0 until 0, HomeGrid.appsOnPage(0, 0))
        assertEquals(0..0, HomeGrid.appsOnPage(0, 1))
        assertEquals(0..29, HomeGrid.appsOnPage(0, 30))
        assertEquals(0..29, HomeGrid.appsOnPage(0, 31))
        assertEquals(30..30, HomeGrid.appsOnPage(1, 31))
        assertEquals(30..59, HomeGrid.appsOnPage(1, 100))
        assertEquals(90..99, HomeGrid.appsOnPage(3, 100))
        assertEquals(0, HomeGrid.appsOnPage(4, 100).count())
    }

    @Test
    fun `the wheel turns pages within the pages there are`() {
        // Away from the player (positive): back a page; towards: forward.
        assertEquals(0, HomeGrid.pageAfterScroll(1, 1.0, 31))
        assertEquals(1, HomeGrid.pageAfterScroll(0, -1.0, 31))
        assertEquals(0, HomeGrid.pageAfterScroll(0, 1.0, 31))
        assertEquals(1, HomeGrid.pageAfterScroll(1, -1.0, 31))
        assertEquals(1, HomeGrid.pageAfterScroll(1, 0.0, 31))
        // One page or none: nowhere to go.
        for (apps in listOf(0, 1, 30)) {
            assertEquals(0, HomeGrid.pageAfterScroll(0, 1.0, apps))
            assertEquals(0, HomeGrid.pageAfterScroll(0, -1.0, apps))
        }
        // Several notches still move one page.
        assertEquals(1, HomeGrid.pageAfterScroll(0, -3.0, 100))
        // Many pages, all the way through and back.
        var page = 0
        repeat(20) { page = HomeGrid.pageAfterScroll(page, -1.0, 301) }
        assertEquals(10, page)
        repeat(20) { page = HomeGrid.pageAfterScroll(page, 1.0, 301) }
        assertEquals(0, page)
    }

    @Test
    fun `badge text for each count`() {
        assertNull(HomeGrid.badgeText(0))
        assertNull(HomeGrid.badgeText(-1))
        assertNull(HomeGrid.badgeText(Int.MIN_VALUE))
        for (count in 1..9) assertEquals(count.toString(), HomeGrid.badgeText(count))
        for (count in listOf(10, 11, 99, 100, 1000, Int.MAX_VALUE)) assertEquals("9+", HomeGrid.badgeText(count))
    }

    @Test
    fun `badge size and position over every icon`() {
        assertEquals(9, HomeGrid.badgeWidth("1"))
        assertEquals(9, HomeGrid.badgeWidth("9"))
        assertEquals(15, HomeGrid.badgeWidth("9+"))
        for (slot in 0 until 30) {
            // 6.1: 9 x 9 at (icon.x + 14, icon.y - 3); 15 x 9 at (icon.x + 8, icon.y - 3).
            assertEquals(HomeGrid.iconX(slot) + 14, HomeGrid.badgeX(slot, 9))
            assertEquals(HomeGrid.iconX(slot) + 8, HomeGrid.badgeX(slot, 15))
            assertEquals(HomeGrid.iconY(slot) - 3, HomeGrid.badgeY(slot))
        }
    }

    @Test
    fun `a full page of icons, rings and badges covers screen sx 4 to 124, sy 16 to 160`() {
        // Section 5: what the wallpaper's moon must stay clear of.
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        fun cover(x: Int, y: Int, w: Int, h: Int) {
            left = minOf(left, x)
            right = maxOf(right, x + w - 1)
            top = minOf(top, y + Device.STATUS_HEIGHT)
            bottom = maxOf(bottom, y + h - 1 + Device.STATUS_HEIGHT)
        }
        for (slot in 0 until 30) {
            cover(HomeGrid.iconX(slot) - HomeGrid.RING_INSET, HomeGrid.iconY(slot) - HomeGrid.RING_INSET, 24, 24)
            for (width in listOf(9, 15)) cover(HomeGrid.badgeX(slot, width), HomeGrid.badgeY(slot), width, HomeGrid.BADGE_HEIGHT)
        }
        assertEquals(listOf(4, 124, 16, 160), listOf(left, right, top, bottom))
    }

    @Test
    fun `page dots and the label line are centred`() {
        // Two pages: 9 px of dots, centred in 128.
        assertEquals(59, HomeGrid.firstDotX(2))
        assertEquals(56, HomeGrid.firstDotX(3))
        assertEquals(53, HomeGrid.firstDotX(4))
        for (pages in 2..10) {
            val first = HomeGrid.firstDotX(pages)
            val last = first + 6 * (pages - 1) + 2
            val leftGap = first
            val rightGap = Device.VIEWPORT_WIDTH - 1 - last
            assertTrue(rightGap - leftGap in 0..1, "$pages pages: $leftGap | $rightGap")
        }
        assertEquals(4, HomeGrid.centredX(120))
        assertEquals(64, HomeGrid.centredX(0))
        assertEquals(176, HomeGrid.DOT_Y)
        assertEquals(160, HomeGrid.LABEL_Y)
    }
}
