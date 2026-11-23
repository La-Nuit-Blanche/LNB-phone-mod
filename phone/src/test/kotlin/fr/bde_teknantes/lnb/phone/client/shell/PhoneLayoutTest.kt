package fr.bde_teknantes.lnb.phone.client.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Placements (art direction 2.4). Expected positions are worked out from the document's formulas by hand:
 * corner `(W - 146, H - 234)`, centre `(floor((W - 140) / 2), floor((H - 228) / 2))`.
 */
class PhoneLayoutTest {
    @ParameterizedTest(name = "{0} x {1}: corner ({2}, {3}), centre ({4}, {5})")
    @CsvSource(
        // The document's own examples (2.4, 17.0).
        "320, 240, 174, 6, 90, 6",
        "480, 270, 334, 36, 170, 21",
        // Odd sizes: the centre floors the half pixel.
        "321, 241, 175, 7, 90, 6",
        "481, 271, 335, 37, 170, 21",
        "683, 384, 537, 150, 271, 78", // 1366 x 768 at GUI scale 2
        // Usual windows: 1920 x 1080 at GUI scale 2 and 3, 2560 x 1440 at 4.
        "960, 540, 814, 306, 410, 156",
        "640, 360, 494, 126, 250, 66",
        // GUI scale 1, 854 x 480 (the default window) and 1920 x 1080.
        "854, 480, 708, 246, 357, 126",
        "1920, 1080, 1774, 846, 890, 426",
    )
    fun `placements in windows of every size`(w: Int, h: Int, cx: Int, cy: Int, mx: Int, my: Int) {
        assertEquals(Point(cx, cy), PhoneLayout.devicePosition(Placement.CORNER, w, h))
        assertEquals(Point(mx, my), PhoneLayout.devicePosition(Placement.CENTRE, w, h))
    }

    @Test
    fun `the device is fully on screen in every window it fits in`() {
        for (w in Device.WIDTH..700) for (h in Device.HEIGHT..500 step 7) for (placement in Placement.entries) {
            val at = PhoneLayout.devicePosition(placement, w, h)
            val where = "$placement in $w x $h: $at"
            assertTrue(at.x >= 0 && at.x + Device.WIDTH <= w, where)
            assertTrue(at.y >= 0 && at.y + Device.HEIGHT <= h, where)
        }
    }

    @Test
    fun `the corner keeps 6 px to the right and bottom edges whenever there is room`() {
        for (w in 146..700) for (h in 234..500 step 5) {
            val at = PhoneLayout.devicePosition(Placement.CORNER, w, h)
            assertEquals(6, w - (at.x + Device.WIDTH))
            assertEquals(6, h - (at.y + Device.HEIGHT))
        }
    }

    @Test
    fun `the centre leaves equal margins, the extra odd pixel on the right and bottom`() {
        for (w in Device.WIDTH..700) for (h in Device.HEIGHT..500 step 3) {
            val at = PhoneLayout.devicePosition(Placement.CENTRE, w, h)
            val right = w - at.x - Device.WIDTH
            val bottom = h - at.y - Device.HEIGHT
            assertEquals((w - Device.WIDTH) % 2, right - at.x, "$w x $h")
            assertEquals((h - Device.HEIGHT) % 2, bottom - at.y, "$w x $h")
        }
    }

    @Test
    fun `a window smaller than the device pins it to the top left, axis by axis`() {
        // Both axes too small, in both placements.
        for (placement in Placement.entries) {
            assertEquals(Point(0, 0), PhoneLayout.devicePosition(placement, 139, 227))
            assertEquals(Point(0, 0), PhoneLayout.devicePosition(placement, 1, 1))
            assertEquals(Point(0, 0), PhoneLayout.devicePosition(placement, 0, 0))
        }
        // Only the height too small: x is still placed.
        assertEquals(Point(334, 0), PhoneLayout.devicePosition(Placement.CORNER, 480, 200))
        assertEquals(Point(170, 0), PhoneLayout.devicePosition(Placement.CENTRE, 480, 200))
        // Only the width too small: y is still placed.
        assertEquals(Point(0, 36), PhoneLayout.devicePosition(Placement.CORNER, 100, 270))
        assertEquals(Point(0, 21), PhoneLayout.devicePosition(Placement.CENTRE, 100, 270))
        // The device fits but the corner's margin doesn't: flush left, still whole.
        assertEquals(Point(0, 0), PhoneLayout.devicePosition(Placement.CORNER, 143, 230))
    }

    @Test
    fun `the scrim is full in the centre, none in the corner, half at half way`() {
        assertEquals(1f, PhoneLayout.scrimAmount(Placement.CENTRE))
        assertEquals(0f, PhoneLayout.scrimAmount(Placement.CORNER))
        assertEquals(0x4D000000, PhoneLayout.scrimColor(1f))
        assertEquals(0, PhoneLayout.scrimColor(0f))
        // P-3.2 and P-12.4: #00000026 at half opacity.
        assertEquals(0x26000000, PhoneLayout.scrimColor(0.5f))
        // Out of range amounts are clamped.
        assertEquals(0x4D000000, PhoneLayout.scrimColor(1.5f))
        assertEquals(0, PhoneLayout.scrimColor(-0.5f))
    }

    @Test
    fun `the scrim darkens steadily with its amount and is always pure black`() {
        var previous = -1
        for (i in 0..1000) {
            val color = PhoneLayout.scrimColor(i / 1000f)
            assertEquals(0, color and 0xFFFFFF)
            val alpha = color ushr 24
            assertTrue(alpha >= previous, "alpha went down at ${i / 1000f}")
            previous = alpha
        }
        assertEquals(0x4D, previous)
    }
}
