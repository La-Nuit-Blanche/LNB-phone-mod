package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What happens to a peek or a banner when something else happens during its life: the phone key, another
 * notification, a screen, the phone closing, an app opening or failing, a disconnection, a setting, the window.
 */
internal class ShellAlertsInterruptionTest : AlertsTestBase() {
    // ---- The phone key during a peek (2.3, P-9) ----

    @Test
    fun `the phone key while the peek rises, holds or sinks opens the phone on the notification's target`() {
        registerAll()
        for (moment in listOf(1, 2, 40, PEEK_RISE_TICKS + HOLD_TICKS + 1, PEEK_LIFE_TICKS - 1)) {
            val n = notify(target = "thread:$moment")
            tick(moment)
            assertNotNull(peekAt(), "peek at tick $moment")
            pressPhoneKeyInGame()
            assertEquals(State.OPENING, shell.state, "tick $moment")
            assertEquals("thread:$moment", host.navigator.currentScreen.toString(), "tick $moment")
            assertEquals(n.appId, host.navigator.currentApp?.id)
            assertNull(alerts.peek)
            shell.close()
            closeFully()
        }
    }

    @Test
    fun `once the peek is gone the phone key opens the phone where it was put away`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        notify(target = "thread:9")
        tick(PEEK_LIFE_TICKS)
        assertNull(alerts.peek)
        pressPhoneKeyInGame()
        assertEquals("mail", host.navigator.currentScreen.toString())
        // From the window's edge.
        assertEquals(DevicePose(334, 270, 0f), pose)
    }

    @Test
    fun `the phone key on a peek whose app can't start opens the phone where it was, from the peek`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        notify(app = "broken")
        tick(10)
        pressPhoneKeyInGame()
        assertEquals(State.OPENING, shell.state)
        assertEquals("mail", host.navigator.currentScreen.toString())
        assertEquals(DevicePose(334, 198, 0f), pose)
    }

    @Test
    fun `the phone key on a peek whose notification has no target opens the app on its first screen`() {
        registerAll()
        notify(target = null)
        tick(10)
        pressPhoneKeyInGame()
        assertEquals("feed", host.navigator.currentScreen.toString())
    }

    @Test
    fun `the phone key on a peek held behind a screen is that screen's`() {
        registerAll()
        notify()
        tick(10)
        host.openOtherScreen("chat")
        pressPhoneKeyInGame()
        assertEquals(State.CLOSED, shell.state)
        assertEquals("chat", host.screen.toString())
    }

    // ---- A second notification during a peek (10.2 "Queue", decision 5) ----

    @Test
    fun `a second notification while the peek rises swaps the content, and the rise carries on`() {
        registerAll()
        notify()
        tick(2)
        val second = notify(app = "mail", target = null)
        assertSame(second, alerts.peek?.notification)
        assertEquals(DevicePose(334, 216, 0f), peekAt())
        tick(2)
        assertEquals(DevicePose(334, 198, 0f), peekAt())
        // A full hold after the rise, then down.
        tick(HOLD_TICKS - 1)
        assertEquals(DevicePose(334, 198, 0f), peekAt())
        tick(1 + PEEK_RISE_TICKS)
        assertNull(alerts.peek)
    }

    @Test
    fun `a second notification while the peek sinks brings it back up from where it is, for a full hold`() {
        registerAll()
        notify()
        tick(PEEK_LIFE_TICKS - 2)
        assertEquals(DevicePose(334, 216, 0f), peekAt())
        val second = notify(app = "mail")
        assertSame(second, alerts.peek?.notification)
        // No jump.
        assertEquals(DevicePose(334, 216, 0f), peekAt())
        tick(2)
        assertEquals(198, peekAt()!!.y)
        tick(HOLD_TICKS)
        assertEquals(198, peekAt()!!.y)
        tick(PEEK_RISE_TICKS + 1)
        assertNull(alerts.peek)
    }

    @Test
    fun `a notification each tick keeps the peek up, the last one showing, with a chime each tick`() {
        registerAll()
        var last = notify()
        repeat(100) {
            tick()
            last = notify(app = if (it % 2 == 0) "mail" else "feed")
        }
        assertSame(last, alerts.peek?.notification)
        assertEquals(DevicePose(334, 198, 0f), peekAt())
        assertEquals(101, chimes)
    }

    @Test
    fun `several notifications in one tick, the last one shows, one chime, whatever the mix of apps`() {
        registerAll()
        notify(app = "gone")
        notify()
        val last = notify(app = "mail")
        notify(app = "gone")
        assertSame(last, alerts.peek?.notification)
        assertEquals(1, chimes)
        tick()
        notify(app = "gone")
        assertEquals(1, chimes)
    }

    @Test
    fun `with sounds off no chime ever, however many arrive, peek or banner`() {
        registerAll()
        host.settings.update { it.copy(sounds = false) }
        notify()
        tick()
        notify()
        openFully()
        notify()
        tick()
        notify()
        assertEquals(0, chimes)
    }

    @Test
    fun `sounds turned off between two notifications silence the second`() {
        registerAll()
        notify()
        tick()
        host.settings.update { it.copy(sounds = false) }
        notify()
        assertEquals(1, chimes)
    }

    // ---- A second notification during a banner ----

    @Test
    fun `a second notification while the banner slides down swaps it and the slide carries on`() {
        registerAll()
        openFully()
        notify()
        tick(1)
        val before = alerts.bannerProgress(shell.now())
        val second = notify(app = "mail")
        assertSame(second, alerts.banner?.notification)
        assertEquals(before, alerts.bannerProgress(shell.now()))
        tick(BANNER_IN_TICKS - 1 + HOLD_TICKS - 1)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
    }

    @Test
    fun `a second notification during the banner's hold restarts the hold`() {
        registerAll()
        openFully()
        notify()
        tick(60)
        val second = notify(app = "mail")
        tick(HOLD_TICKS - 1)
        assertSame(second, alerts.banner?.notification)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
        tick(1 + BANNER_IN_TICKS)
        assertNull(alerts.banner)
    }

    @Test
    fun `a second notification while the banner slides up brings it back down from where it is`() {
        registerAll()
        openFully()
        notify()
        tick(BANNER_IN_TICKS + HOLD_TICKS + 1)
        val leaving = alerts.bannerProgress(shell.now())
        assertTrue(leaving > 0f && leaving < 1f)
        notify(app = "mail")
        assertEquals(leaving, alerts.bannerProgress(shell.now()), 1e-4f)
        tick(BANNER_IN_TICKS)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
    }

    @Test
    fun `a notification read in place during a banner leaves the banner alone`() {
        registerAll()
        openFully()
        val first = notify(app = "mail")
        host.navigator.openApp(appId("feed"), "thread:1")
        val read = notify(target = "thread:1")
        assertSame(first, alerts.banner?.notification)
        assertEquals(listOf(read), host.dismissed)
    }

    // ---- Screens, closing, opening ----

    @Test
    fun `a screen opening while the peek rises sends it down from where it is, and it doesn't come back`() {
        registerAll()
        notify()
        tick(2)
        host.openOtherScreen("chat")
        tick()
        // Sent down at that tick, 150 ms into the rise (0.9375 of the way), from where it was.
        val down = peekAt()!!.y
        assertTrue(down in 199..216, "y $down")
        host.setScreen(null)
        tick(PEEK_RISE_TICKS)
        assertNull(alerts.peek)
    }

    @Test
    fun `while a screen is open a new notification doesn't bring back a sinking peek`() {
        registerAll()
        notify()
        tick(10)
        host.openOtherScreen("chat")
        tick()
        notify()
        tick(PEEK_RISE_TICKS + 1)
        assertNull(alerts.peek)
        assertEquals(2, chimes)
    }

    @Test
    fun `a peek waiting for the device to be away is dropped if a screen opens first`() {
        registerAll()
        openFully()
        host.pressKey(ESC)
        notify()
        host.openOtherScreen("chat")
        closeFully()
        tick()
        assertNull(alerts.peek)
    }

    @Test
    fun `a peek waiting for the device to be away is dropped if the phone opens again first (decision 3)`() {
        registerAll()
        openFully()
        host.pressKey(ESC)
        notify()
        tick()
        pressPhoneKeyInGame()
        assertEquals(State.OPENING, shell.state)
        tick(OPEN_TICKS)
        shell.close()
        closeFully()
        tick()
        assertNull(alerts.peek)
        assertNull(alerts.banner)
    }

    @Test
    fun `two notifications while the device slides down, the last one peeks once it is away`() {
        registerAll()
        openFully()
        host.pressKey(ESC)
        notify()
        tick()
        val last = notify(app = "mail")
        closeFully()
        tick()
        assertSame(last, alerts.peek?.notification)
        assertEquals(2, chimes)
    }

    @Test
    fun `closing the phone takes the banner away, keeps the notification and peeks nothing after`() {
        registerAll()
        openFully()
        notify()
        tick(BANNER_IN_TICKS)
        host.pressKey(ESC)
        assertNull(alerts.banner)
        assertFalse(alerts.bannerPressed)
        closeFully()
        tick()
        assertNull(alerts.peek)
        assertTrue(host.dismissed.isEmpty())
    }

    @Test
    fun `the phone standing down during its rise takes the banner with it`() {
        registerAll()
        pressPhoneKeyInGame()
        tick(2)
        notify()
        assertNotNull(alerts.banner)
        host.openOtherScreen("inventory")
        tick()
        assertEquals(State.CLOSING, shell.state)
        assertNull(alerts.banner)
    }

    @Test
    fun `the phone's screen replaced by another (the death screen) takes the banner away`() {
        registerAll()
        openFully()
        notify()
        host.openOtherScreen("death")
        assertEquals(State.CLOSED, shell.state)
        assertNull(alerts.banner)
    }

    @Test
    fun `an app opened from home under a banner keeps the banner where it is, over the app`() {
        registerAll(extraApps = 6)
        openFully()
        notify()
        tick(BANNER_IN_TICKS + 10)
        // Slot 7 (app4), in the second row, its middle below the banner.
        clickAt(viewport(HomeGrid.iconX(7) + 10.0, HomeGrid.iconY(7) + 10.0))
        assertEquals("app4", host.navigator.currentScreen.toString())
        assertNotNull(alerts.banner)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
        tick(HOLD_TICKS - 10 + BANNER_IN_TICKS)
        assertNull(alerts.banner)
    }

    @Test
    fun `an app failing under a banner sends the phone home and leaves the banner`() {
        registerAll()
        openFully()
        notify()
        assertFalse(host.navigator.openApp(appId("broken")))
        assertTrue(host.navigator.isHome)
        assertNotNull(alerts.banner)
    }

    // ---- Disconnection, settings, the window ----

    @Test
    fun `disconnecting forgets the peek, the banner and a peek still waiting`() {
        registerAll()
        notify()
        tick(10)
        shell.reset()
        assertNull(alerts.peek)

        openFully()
        notify()
        // The game drops the phone's screen as it leaves the server.
        host.setScreen(null)
        shell.reset()
        assertNull(alerts.banner)

        openFully()
        host.pressKey(ESC)
        notify()
        shell.reset()
        tick(CLOSE_TICKS + 1)
        assertNull(alerts.peek)
    }

    @Test
    fun `leaving the world with the phone open forgets the banner`() {
        registerAll()
        openFully()
        notify()
        host.inWorld = false
        tick()
        assertNull(alerts.banner)
        assertEquals(State.CLOSED, shell.state)
    }

    @Test
    fun `changing the placement during a peek leaves it in the corner (2_4)`() {
        registerAll()
        notify()
        tick(10)
        host.settings.update { it.copy(placement = Placement.CENTRE) }
        tick()
        assertEquals(DevicePose(334, 198, 0f), peekAt())
    }

    @Test
    fun `a window resized during a peek takes the peek with its corner`() {
        registerAll()
        notify()
        tick(10)
        host.guiWidth = 640
        host.guiHeight = 360
        assertEquals(DevicePose(494, 288, 0f), peekAt())
        host.guiWidth = 400
        assertEquals(DevicePose(254, 248, 0f), peekAt())
    }

}
