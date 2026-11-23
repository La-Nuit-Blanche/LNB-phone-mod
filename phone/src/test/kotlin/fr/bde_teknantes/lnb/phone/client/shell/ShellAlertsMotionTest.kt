package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.notifications.BannerLayout
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Where the peek and the banner are, and for how long (art direction 10.2, 10.3, 14; P-8, P-9, P-10): at every window
 * size the document gives a rule for, at the flows' measured frames, and across the hand-over from a peek to the open
 * phone.
 */
internal class ShellAlertsMotionTest : AlertsTestBase() {
    @ParameterizedTest(name = "{0} x {1}: peeks at ({2}, {3})")
    @CsvSource(
        // The flows' window: 72 px up, at (334, 198) (10.2).
        "480, 270, 334, 198",
        // Narrow from under 476 px: 112 px up (10.2), so the banner clears the hotbar and the food row.
        "476, 270, 330, 198",
        "475, 270, 329, 158",
        "427, 240, 281, 128",
        // The smallest window Minecraft allows above GUI scale 1 (2.4).
        "320, 240, 174, 128",
        // 1920 x 1080 at GUI scale 2, and 2560 x 1440 at GUI scale 3.
        "960, 540, 814, 468",
        "853, 480, 707, 408",
    )
    fun `the risen peek sits in the bottom-right corner, 72 px up or 112 in a narrow window`(width: Int, height: Int, x: Int, y: Int) {
        registerAll()
        host.guiWidth = width
        host.guiHeight = height
        notify()
        tick(PEEK_RISE_TICKS)
        assertEquals(DevicePose(x, y, 0f), peekAt())
        tick(HOLD_TICKS - 1)
        assertEquals(DevicePose(x, y, 0f), peekAt())
    }

    @Test
    fun `the peek rises in 200 ms ease-out, holds 4000 ms and sinks in 200 ms ease-in`() {
        registerAll()
        notify()
        val start = shell.now()
        fun at(ms: Double) = alerts.peekPose(start + (ms / 50).toFloat())?.y
        // Hidden at the very start, under the window's edge.
        assertNull(at(0.0))
        // Ease-out: 1 - (1 - t)^2 of 72 px.
        assertEquals(270 - 32, at(50.0)) // t 0.25: 0.4375 * 72 = 31.5, to the even 32
        assertEquals(216, at(100.0)) // P-8.2's mid-rise at 0.75 of the way
        assertEquals(198, at(200.0))
        assertEquals(198, at(4200.0))
        // Ease-in: 1 - t^2.
        assertEquals(270 - 68, at(4250.0)) // 0.9375 * 72 = 67.5, to the even 68
        assertEquals(216, at(4300.0))
        assertEquals(270 - 32, at(4350.0)) // t 0.75: 1 - 0.5625 = 0.4375, as on the way up
        assertNull(at(4400.0))
    }

    @Test
    fun `the peek's life is 4400 ms, then it is dropped`() {
        registerAll()
        notify()
        tick(PEEK_LIFE_TICKS - 1)
        assertNotNull(peekAt())
        assertNotNull(peekAt(0.99f))
        assertNull(peekAt(1f))
        tick()
        assertNull(alerts.peek)
    }

    @Test
    fun `frames between ticks follow the partial tick`() {
        registerAll()
        notify()
        tick()
        host.partialTick = 0.5f
        // 75 ms: 1 - 0.625^2 = 0.609375 of 72 px = 43.875, so 44 up.
        assertEquals(DevicePose(334, 270 - 44, 0f), alerts.peekPose())
    }

    @Test
    fun `the banner slides down in 150 ms ease-out, holds 4000 ms and slides up in 150 ms ease-in (14)`() {
        registerAll()
        openFully()
        notify()
        val start = shell.now()
        fun at(ms: Double) = alerts.bannerProgress(start + (ms / 50).toFloat())
        assertEquals(0f, at(0.0))
        assertEquals(0.75f, at(75.0), 1e-5f)
        assertEquals(1f, at(150.0))
        assertEquals(1f, at(4150.0))
        assertEquals(0.75f, at(4225.0), 1e-5f)
        assertEquals(0f, at(4300.0))
        tick(BANNER_LIFE_TICKS - 1)
        assertNotNull(alerts.banner)
        tick()
        assertNull(alerts.banner)
    }

    @Test
    fun `half-way down the banner's top is at y -36, and it rests at (8, 4) dp (P-10_2, P-10_3)`() {
        assertEquals(-36f, BannerLayout.inPhoneY(0.5f))
        assertEquals(4f, BannerLayout.inPhoneY(1f))
        assertEquals(-76f, BannerLayout.inPhoneY(0f))
        registerAll()
        openFully()
        shell.heldProgress = 0.5f
        notify()
        assertEquals(0.5f, alerts.bannerProgress(shell.now()))
    }

    @Test
    fun `the banner keeps its place on the device in the centred placement and whatever the window`() {
        registerAll()
        host.settings.update { it.copy(placement = Placement.CENTRE) }
        host.guiWidth = 320
        host.guiHeight = 240
        openFully()
        notify()
        tick(BANNER_IN_TICKS)
        // Device at (90, 6): the banner's middle (128, 42) dp is viewport (64, 21) GUI px.
        assertEquals(DevicePose(90, 6, 1f), pose)
        assertEquals(90.0 + 6 + 64 to 6.0 + 23 + 21, bannerMiddle)
        clickAt(bannerMiddle)
        assertEquals("thread:1", host.navigator.currentScreen.toString())
    }

    // ---- From the peek to the open phone (14 "Open from a peek", P-9) ----

    @Test
    fun `the phone key on a risen peek, no jump, then up to the open position in 250 ms, through P-9_2's frame`() {
        registerAll()
        notify()
        tick(10)
        val peek = peekAt()
        pressPhoneKeyInGame()
        assertEquals(peek, pose)
        shell.heldProgress = 0.5f
        // Half-way from 198 to 36.
        assertEquals(DevicePose(334, 117, 0f), pose)
        shell.heldProgress = null
        tick(OPEN_TICKS - 1)
        assertEquals(State.OPENING, shell.state)
        tick()
        assertEquals(State.OPEN, shell.state)
        assertEquals(DevicePose(334, 36, 0f), pose)
    }

    @Test
    fun `from a peek still rising or already sinking, the phone rises from where the peek is`() {
        registerAll()
        notify()
        tick(2)
        val rising = peekAt()
        assertEquals(DevicePose(334, 216, 0f), rising)
        pressPhoneKeyInGame()
        assertEquals(rising, pose)

        shell.close()
        closeFully()
        notify()
        tick(PEEK_LIFE_TICKS - 2)
        val sinking = peekAt()
        assertEquals(DevicePose(334, 216, 0f), sinking)
        pressPhoneKeyInGame()
        assertEquals(sinking, pose)
    }

    @Test
    fun `in the centred placement the phone still peeks from the corner, and opens from there to the centre`() {
        registerAll()
        host.settings.update { it.copy(placement = Placement.CENTRE) }
        notify()
        tick(10)
        assertEquals(DevicePose(334, 198, 0f), peekAt())
        pressPhoneKeyInGame()
        assertEquals(DevicePose(334, 198, 0f), pose)
        tick(OPEN_TICKS)
        assertEquals(DevicePose(170, 21, 1f), pose)
    }

    @Test
    fun `in a narrow window the phone opens from the 112 px peek`() {
        registerAll()
        host.guiWidth = 320
        host.guiHeight = 240
        notify()
        tick(10)
        pressPhoneKeyInGame()
        assertEquals(DevicePose(174, 128, 0f), pose)
        tick(OPEN_TICKS)
        assertEquals(DevicePose(174, 6, 0f), pose)
    }

    @Test
    fun `the inventory button during a sinking peek opens the phone from where the peek is`() {
        registerAll()
        notify()
        tick(10)
        val inventory = host.openOtherScreen("inventory")
        tick(2)
        // The inventory sent the peek down at the next tick; it is on its way, 50 ms in: 0.9375 of 72 px up.
        val sinking = peekAt()
        assertEquals(DevicePose(334, 202, 0f), sinking)
        shell.openFromInventory(inventory)
        assertEquals(sinking, pose)
        assertNull(alerts.peek)
    }
}
