package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The mouse on the banner over the open phone (10.1, 10.3, P-10.4, P-10.5): where it is hit, what a click does, what
 * the screen under it gets. The banner rests at app-local (8, 4) dp, 240 x 76: x 8 to 248, y 4 to 80 dp, which is
 * viewport x 4 to 124, y 2 to 40 GUI px. Home icons: row 0 at viewport y 8..27 (all under the banner), row 1 at
 * y 32..51 (its top under the banner).
 */
internal class ShellAlertsClickTest : AlertsTestBase() {
    // feed, mail, broken, app0..app5: slot 5 is app2, the first icon of row 1.
    private fun openWithBanner(target: String? = "thread:8") {
        registerAll(extraApps = 6)
        openFully()
        notify(target = target)
        tick(BANNER_IN_TICKS)
        host.sounds.clear()
    }

    @ParameterizedTest(name = "app-local ({0}, {1}) dp: on the banner {2}")
    @CsvSource(
        "8, 42, true",
        "7.9, 42, false",
        "247.9, 42, true",
        "248, 42, false",
        "56, 4, true",
        "56, 3.9, false",
        "56, 79.9, true",
        "56, 80, false",
        "8, 4, true",
        "247.9, 79.9, true",
    )
    fun `the resting banner is hit on its 240 x 76 dp and nowhere else`(x: Double, y: Double, on: Boolean) {
        openWithBanner()
        pressAt(dp(x, y))
        assertEquals(on, alerts.bannerPressed, "pressed")
        releaseAt(dp(x, y))
        assertEquals(on, alerts.banner == null, "banner taken away")
        if (on) assertEquals("thread:8", host.navigator.currentScreen.toString())
    }

    @Test
    fun `a click on the banner opens its target with the forward slide and a tap, and the banner goes at once (P-10_5)`() {
        openWithBanner()
        clickAt(bannerMiddle)
        assertNull(alerts.banner)
        assertEquals("thread:8", host.navigator.currentScreen.toString())
        assertTrue(shell.screenSlide!!.forward)
        assertEquals(listOf(ShellSound.TAP), host.sounds)
    }

    @Test
    fun `a click on the banner of an untargeted notification opens its app's first screen`() {
        openWithBanner(target = null)
        clickAt(bannerMiddle)
        assertEquals("feed", host.navigator.currentScreen.toString())
    }

    @Test
    fun `a click on the banner over another app replaces that app`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        notify(target = "thread:3")
        tick(BANNER_IN_TICKS)
        clickAt(bannerMiddle)
        assertEquals("thread:3", host.navigator.currentScreen.toString())
        // The app's screens hear nothing of the click.
        assertFalse(log.any { it.startsWith("mail:pressed") || it.startsWith("mail:released") })
    }

    @Test
    fun `a click on the banner of an app that can't start removes the banner and leaves the phone where it was`() {
        registerAll()
        openFully()
        notify(app = "broken")
        tick(BANNER_IN_TICKS)
        clickAt(bannerMiddle)
        assertNull(alerts.banner)
        assertTrue(host.navigator.isHome)
        assertEquals(State.OPEN, shell.state)
    }

    @Test
    fun `the icons under the banner don't get the click, those below it do`() {
        openWithBanner()
        // Icon slot 0 (feed) lies under the banner.
        pressAt(viewport(HomeGrid.iconX(0) + 10.0, HomeGrid.iconY(0) + 10.0))
        assertNull(shell.pressedApp)
        assertTrue(alerts.bannerPressed)
        releaseAt(viewport(HomeGrid.iconX(0) + 10.0, HomeGrid.iconY(0) + 10.0))
        assertEquals("thread:8", host.navigator.currentScreen.toString())
    }

    @Test
    fun `row 1's icon is the banner's where the banner covers it, and the icon's just below`() {
        openWithBanner()
        val x = HomeGrid.iconX(5) + 10.0
        pressAt(viewport(x, 39.9))
        assertNull(shell.pressedApp)
        releaseAt(viewport(x, 39.9))
        assertEquals("thread:8", host.navigator.currentScreen.toString())

        shell.close()
        closeFully()
        host.navigator.home()
        openFully()
        notify()
        tick(BANNER_IN_TICKS)
        clickAt(viewport(x, 40.0))
        assertEquals("app2", host.navigator.currentScreen.toString())
        assertNotNull(alerts.banner)
    }

    @Test
    fun `while the banner moves, it is hit where it is drawn`() {
        registerAll(extraApps = 6)
        openFully()
        shell.heldProgress = 0.5f
        notify()
        // Half-way: y -36 to 40 dp, clipped to the viewport: viewport y 0 to 20 GUI px.
        val x = HomeGrid.iconX(0) + 10.0
        pressAt(viewport(x, 19.9))
        assertTrue(alerts.bannerPressed)
        releaseAt(viewport(x, 19.9))
        assertNull(alerts.banner)
        assertEquals("thread:1", host.navigator.currentScreen.toString())

        host.navigator.home()
        notify()
        // Below the moving banner, the icon under it: feed itself, on its first screen.
        clickAt(viewport(x, 20.0))
        assertNotNull(alerts.banner)
        assertEquals("feed", host.navigator.currentScreen.toString())
    }

    @Test
    fun `a press on the banner released elsewhere opens nothing, and the banner stays`() {
        openWithBanner()
        pressAt(bannerMiddle)
        assertTrue(alerts.bannerPressed)
        // Released on row 1's icon, below the banner.
        releaseAt(viewport(HomeGrid.iconX(5) + 10.0, 45.0))
        assertFalse(alerts.bannerPressed)
        assertNotNull(alerts.banner)
        assertTrue(host.navigator.isHome)

        // Released outside the viewport, on the bezel, or outside the device.
        pressAt(bannerMiddle)
        releaseAt(device(70.0, 5.0))
        assertNotNull(alerts.banner)
        pressAt(bannerMiddle)
        releaseAt(device(-20.0, 50.0))
        assertNotNull(alerts.banner)
        assertTrue(host.navigator.isHome)
        assertTrue(host.sounds.isEmpty())
    }

    @Test
    fun `a press elsewhere released on the banner opens nothing`() {
        openWithBanner()
        // Pressed on row 1's icon, below the banner, released on the banner over row 0.
        pressAt(viewport(HomeGrid.iconX(5) + 10.0, 45.0))
        assertEquals(5, shell.pressedApp)
        releaseAt(bannerMiddle)
        assertNotNull(alerts.banner)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `a press in an app released on the banner goes to the app, not to the banner`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        notify()
        tick(BANNER_IN_TICKS)
        pressAt(dp(128.0, 200.0))
        releaseAt(bannerMiddle)
        assertEquals(listOf("mail:pressed", "mail:released"), log.filter { it.startsWith("mail:p") || it.startsWith("mail:r") })
        assertNotNull(alerts.banner)
        assertEquals("mail", host.navigator.currentScreen.toString())
    }

    @Test
    fun `the right button on the banner is the banner's, and does nothing`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        notify()
        tick(BANNER_IN_TICKS)
        clickAt(bannerMiddle, RIGHT)
        assertFalse(alerts.bannerPressed)
        assertNotNull(alerts.banner)
        assertFalse(log.any { it == "mail:pressed" })
    }

    @Test
    fun `a pressed banner neither leaves nor changes, and opens what was pressed`() {
        openWithBanner()
        pressAt(bannerMiddle)
        tick(HOLD_TICKS * 2)
        assertNotNull(alerts.banner)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
        // A newer one arriving meanwhile doesn't take its place, and stays a badge.
        val newer = notify(app = "mail", target = null)
        assertEquals("thread:8", alerts.banner!!.notification.target)
        releaseAt(bannerMiddle)
        assertEquals("thread:8", host.navigator.currentScreen.toString())
        assertNull(alerts.banner)
        assertTrue(newer !in host.dismissed)
    }

    @Test
    fun `released off the banner, it resumes where it was, or shows what arrived meanwhile with a fresh hold`() {
        openWithBanner()
        tick(HOLD_TICKS - 10)
        pressAt(bannerMiddle)
        tick(100)
        releaseAt(dp(128.0, 300.0))
        // Resumed: 10 ticks of hold were left.
        tick(9)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
        tick(1 + BANNER_IN_TICKS)
        assertNull(alerts.banner)

        notify()
        tick(BANNER_IN_TICKS + HOLD_TICKS - 10)
        pressAt(bannerMiddle)
        val newer = notify(app = "mail", target = null)
        releaseAt(dp(128.0, 300.0))
        assertEquals(newer, alerts.banner!!.notification)
        tick(HOLD_TICKS - 1)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
    }

    @Test
    fun `a home icon pressed before the banner came down over it is cancelled by a release on the banner`() {
        registerAll(extraApps = 6)
        openFully()
        // Feed's icon, row 0, all under the resting banner.
        val icon = viewport(16.0, 18.0)
        pressAt(icon)
        notify()
        tick(BANNER_IN_TICKS)
        releaseAt(icon)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `the mouse wheel over the banner is the banner's`() {
        registerAll(extraApps = 40)
        openFully()
        notify()
        tick(BANNER_IN_TICKS)
        shell.mouseScrolled(bannerMiddle.first, bannerMiddle.second, 0.0, -1.0)
        assertEquals(0, shell.page())
        val below = dp(128.0, 300.0)
        shell.mouseScrolled(below.first, below.second, 0.0, -1.0)
        assertEquals(1, shell.page())
    }

    @Test
    fun `a notification cleared while its banner or peek shows sends it away early`() {
        registerAll()
        val n = notify()
        host.unreadList = listOf(n)
        tick(PEEK_RISE_TICKS)
        shell.notificationsChanged()
        assertNotNull(peekAt())
        host.unreadList = emptyList()
        shell.notificationsChanged()
        tick(PEEK_RISE_TICKS)
        assertNull(peekAt())

        openFully()
        val m = notify()
        host.unreadList = listOf(m)
        tick(BANNER_IN_TICKS)
        pressAt(bannerMiddle)
        host.unreadList = emptyList()
        shell.notificationsChanged()
        assertFalse(alerts.bannerPressed)
        tick(BANNER_IN_TICKS)
        assertNull(alerts.banner)
        // Nothing to open where it was.
        releaseAt(bannerMiddle)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `reopened onto an app left on a notification's target, that notification is read in place`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("feed"), "thread:2")
        shell.close()
        closeFully()
        val there = notification(target = "thread:2")
        val elsewhere = notification(target = "thread:3")
        host.unreadList = listOf(there, elsewhere)
        pressPhoneKeyInGame()
        assertEquals(listOf(there), host.dismissed)
    }

    @Test
    fun `a banner pressed while the phone is put away opens nothing`() {
        openWithBanner()
        pressAt(bannerMiddle)
        shell.close()
        assertFalse(alerts.bannerPressed)
        shell.mouseReleased(bannerMiddle.first, bannerMiddle.second, LEFT)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `a banner at GUI scale 1 is never there to click`() {
        registerAll(extraApps = 6)
        host.guiScale = 1.0
        openFully()
        notify()
        tick(BANNER_IN_TICKS)
        clickIcon(0)
        assertEquals("feed", host.navigator.currentScreen.toString())
    }
}
