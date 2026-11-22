package fr.bde_teknantes.lnb.phone.client.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The art direction's own worked examples (2.4, 4, 6, 14, 17), checked against the layout maths. */
class ShellLayoutTest {
    @Test
    fun `placements match the art direction's examples`() {
        assertEquals(Point(174, 6), PhoneLayout.devicePosition(Placement.CORNER, 320, 240))
        assertEquals(Point(90, 6), PhoneLayout.devicePosition(Placement.CENTRE, 320, 240))
        assertEquals(Point(334, 36), PhoneLayout.devicePosition(Placement.CORNER, 480, 270))
        assertEquals(Point(170, 21), PhoneLayout.devicePosition(Placement.CENTRE, 480, 270))
    }

    @Test
    fun `a window smaller than the device keeps it at the top left`() {
        assertEquals(Point(0, 0), PhoneLayout.devicePosition(Placement.CORNER, 100, 200))
        assertEquals(Point(0, 0), PhoneLayout.devicePosition(Placement.CENTRE, 100, 200))
    }

    @Test
    fun `mid-slide frames of the flows`() {
        // P-1.2: half way up from the bottom edge to (334, 36) in a 480 x 270 window.
        val open = DeviceSlide.open(DevicePose(334, 270, 0f), 0f)
        assertEquals(DevicePose(334, 153, 0f), open.pose(0.5f, 334, 36, 0f))
        // P-3.2: centred, half way to (170, 21), scrim at half opacity (#00000026).
        val centred = DeviceSlide.open(DevicePose(170, 270, 0f), 0f).pose(0.5f, 170, 21, 1f)
        assertEquals(170, centred.x)
        assertEquals(146, centred.y)
        assertEquals(0x26000000, PhoneLayout.scrimColor(centred.scrim))
        // P-12.4: gliding from the corner to the centre, half way: (252, 28).
        val glide = DeviceSlide.glide(DevicePose(334, 36, 0f), 0f).pose(0.5f, 170, 21, 1f)
        assertEquals(252, glide.x)
        assertEquals(28, glide.y)
    }

    @Test
    fun `easing and durations`() {
        val open = DeviceSlide.open(DevicePose(0, 0, 0f), 10f)
        assertEquals(0f, open.progress(10f))
        assertEquals(0.75f, open.progress(12.5f), 1e-6f)
        assertEquals(1f, open.progress(15f))
        val close = DeviceSlide.close(DevicePose(0, 0, 0f), 0f)
        assertEquals(0.25f, close.progress(2f), 1e-6f)
        assertEquals(true, close.isDone(4f))
        // P-6.4: half way, the app is 128 dp (64 GUI px) from the left.
        assertEquals(64, ScreenSlide(true, 0f).offset(0.5f))
        assertEquals(128, ScreenSlide(true, 0f).offset(0f))
        assertEquals(0, ScreenSlide(false, 0f).offset(0f))
    }

    @Test
    fun `status bar`() {
        assertEquals("06:00", StatusBar.clock(0, false))
        assertEquals("12:00", StatusBar.clock(6000, false))
        assertEquals("00:00", StatusBar.clock(18000, false))
        assertEquals("05:59", StatusBar.clock(23999, false))
        assertEquals("06:00", StatusBar.clock(48000, false))
        assertEquals("--:--", StatusBar.clock(1234, true))
        assertEquals(51, StatusBar.clockX(25))
        assertEquals(listOf(4, 4, 3, 3, 2, 2, 1, 0, 0), listOf(0, 149, 150, 299, 300, 599, 600, null, 50).mapIndexed { i, ms ->
            StatusBar.signalBars(ms, singleplayer = i == 8)
        })
        assertEquals(StatusBar.Battery(8, StatusBar.FOOD_GREEN), StatusBar.battery(20, true))
        assertEquals(StatusBar.Battery(3, StatusBar.FOOD_GREEN), StatusBar.battery(7, true))
        assertEquals(StatusBar.Battery(2, StatusBar.FOOD_ORANGE), StatusBar.battery(6, true))
        assertEquals(StatusBar.Battery(2, StatusBar.FOOD_ORANGE), StatusBar.battery(4, true))
        assertEquals(StatusBar.Battery(1, StatusBar.FOOD_RED), StatusBar.battery(3, true))
        assertEquals(StatusBar.Battery(0, StatusBar.FOOD_RED), StatusBar.battery(0, true))
        assertEquals(StatusBar.Battery(8, StatusBar.FOOD_GREEN), StatusBar.battery(0, false))
    }

    @Test
    fun `home grid`() {
        assertEquals(6, HomeGrid.iconX(0))
        assertEquals(102, HomeGrid.iconX(4))
        assertEquals(128, HomeGrid.iconY(29))
        // P-6.2: screen-local (16, 29) is viewport (16, 18), on the first icon.
        assertEquals(0, HomeGrid.appAt(16.0, 18.0, 0, 5))
        // P-6.1: screen-local (70, 120), an empty part of the grid.
        assertNull(HomeGrid.appAt(70.0, 109.0, 0, 5))
        // Rings touch: the last pixel of one icon's ring, then the first of the next.
        assertEquals(0, HomeGrid.appAt(27.0, 18.0, 0, 5))
        assertEquals(1, HomeGrid.appAt(28.0, 18.0, 0, 5))
        assertNull(HomeGrid.appAt(3.0, 18.0, 0, 5))
        assertNull(HomeGrid.appAt(124.0, 18.0, 0, 5))
        assertEquals(31, HomeGrid.appAt(40.0, 18.0, 1, 35))
        assertEquals(2, HomeGrid.pageCount(31))
        assertEquals(1, HomeGrid.pageCount(30))
        assertEquals(116, HomeGrid.badgeX(4, 9))
        assertEquals(110, HomeGrid.badgeX(4, 15))
        assertEquals("9+", HomeGrid.badgeText(10))
        assertNull(HomeGrid.badgeText(0))
        assertEquals(59, HomeGrid.firstDotX(2))
    }
}
