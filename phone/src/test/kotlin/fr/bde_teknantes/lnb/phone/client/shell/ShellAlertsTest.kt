package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.RecordingScreen
import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Notifications arriving at the phone: the peek, the banner, the chime (art direction 10, P-8 to P-11, P-14). */
internal class ShellAlertsTest : ShellTestBase() {
    private var nextId = 1L

    private fun registerFeed() = register(
        testApp("feed", targets = { listOf(TargetScreen(it)) }, root = { RecordingScreen("feed", log) }),
        testApp("mail"),
    )

    /** A screen that shows one target. */
    private inner class TargetScreen(private val target: String) : RecordingScreen(target, log) {
        override fun showsTarget(target: String) = target == this.target
    }

    private fun notify(app: String = "feed", target: String? = "thread:1"): PhoneNotification =
        PhoneNotification(nextId++, appId(app), Component.literal("title"), Component.literal("text"), target, 0, true)
            .also { shell.notificationArrived(it) }

    private val alerts get() = shell.alerts

    @Test
    fun `closed, a notification peeks from the corner for 4 s with the chime, then sinks (P-8)`() {
        registerFeed()
        notify()
        assertEquals(listOf(ShellSound.NOTIFICATION), host.sounds)
        assertNotNull(alerts.peek)
        tick(2)
        // 100 ms into the 200 ms rise, eased out: three quarters of the 72 px.
        assertEquals(DevicePose(334, 216, 0f), alerts.peekPose())
        tick(2)
        assertEquals(DevicePose(334, 198, 0f), alerts.peekPose())
        tick(80)
        assertEquals(DevicePose(334, 198, 0f), alerts.peekPose())
        tick(4)
        assertNull(alerts.peekPose())
        tick()
        assertNull(alerts.peek)
        assertEquals(State.CLOSED, shell.state)
    }

    @Test
    fun `narrow windows rise 112 px`() {
        registerFeed()
        host.guiWidth = 427
        notify()
        tick(4)
        assertEquals(DevicePose(427 - 146, 270 - 112, 0f), alerts.peekPose())
    }

    @Test
    fun `the phone key during a peek opens the phone on the notification's target, rising from the peek (P-9)`() {
        registerFeed()
        notify(target = "thread:4")
        tick(10)
        pressPhoneKeyInGame()
        assertEquals(State.OPENING, shell.state)
        assertNull(alerts.peek)
        assertEquals("thread:4", host.navigator.currentScreen.toString())
        // From the peek position, not from the window's edge.
        assertEquals(DevicePose(334, 198, 0f), pose)
        // No screen slide: the app was opened before the phone.
        assertNull(shell.screenSlide)
    }

    @Test
    fun `preview off, no peek but the chime still plays (P-14)`() {
        registerFeed()
        host.settings.update { it.copy(preview = false) }
        notify()
        assertNull(alerts.peek)
        assertEquals(listOf(ShellSound.NOTIFICATION), host.sounds)
    }

    @Test
    fun `no peek behind another screen, and a screen opening during a peek sends it down`() {
        registerFeed()
        host.openOtherScreen("chat")
        notify()
        assertNull(alerts.peek)
        assertEquals(listOf(ShellSound.NOTIFICATION), host.sounds)
        host.setScreen(null)
        notify()
        tick(10)
        host.openOtherScreen("inventory")
        tick(5)
        assertNull(alerts.peekPose())
    }

    @Test
    fun `a notification during the hold swaps the peek's content and restarts the hold`() {
        registerFeed()
        val first = notify()
        tick(60)
        val second = notify(app = "mail", target = null)
        assertSame(second, alerts.peek!!.notification)
        tick(60)
        assertEquals(DevicePose(334, 198, 0f), alerts.peekPose())
        assertTrue(first !== alerts.peek!!.notification)
    }

    @Test
    fun `several notifications in one tick chime once`() {
        registerFeed()
        notify()
        notify(app = "mail")
        assertEquals(listOf(ShellSound.NOTIFICATION), host.sounds)
        tick()
        notify()
        assertEquals(listOf(ShellSound.NOTIFICATION, ShellSound.NOTIFICATION), host.sounds)
    }

    @Test
    fun `while the device slides down, the peek waits for it to be away`() {
        registerFeed()
        openFully()
        host.pressKey(ESC)
        assertEquals(State.CLOSING, shell.state)
        notify()
        assertNull(alerts.peek)
        closeFully()
        tick()
        assertNotNull(alerts.peek)
    }

    @Test
    fun `open on home, the banner slides down, holds and slides up (P-10)`() {
        registerFeed()
        openFully()
        host.sounds.clear()
        notify()
        assertEquals(listOf(ShellSound.NOTIFICATION), host.sounds)
        assertNull(alerts.peek)
        assertNotNull(alerts.banner)
        tick(3)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
        tick(80)
        assertEquals(1f, alerts.bannerProgress(shell.now()))
        tick(3)
        assertNull(alerts.banner)
    }

    @Test
    fun `held half-way, the peek and the banner sit where the flows measure them (P-8_2, P-10_2)`() {
        registerFeed()
        shell.heldProgress = 0.5f
        notify()
        tick(100)
        assertEquals(DevicePose(334, 234, 0f), alerts.peekPose())
    }

    @Test
    fun `a click on the banner opens its target with the forward slide (P-10)`() {
        registerFeed()
        openFully()
        notify(target = "thread:8")
        tick(3)
        // App-local (128, 42) dp, on the resting banner.
        val (x, y) = device(Device.VIEWPORT_X + 64.0, Device.VIEWPORT_Y + 21.0)
        press(x, y)
        assertTrue(alerts.bannerPressed)
        release(x, y)
        assertNull(alerts.banner)
        assertEquals("thread:8", host.navigator.currentScreen.toString())
        assertNotNull(shell.screenSlide)
        assertTrue(shell.screenSlide!!.forward)
    }

    @Test
    fun `target already on screen, no banner but the chime, and the notification is read`() {
        registerFeed()
        openFully()
        host.navigator.openApp(appId("feed"), "thread:1")
        host.sounds.clear()
        val n = notify(target = "thread:1")
        assertNull(alerts.banner)
        assertEquals(listOf(ShellSound.NOTIFICATION), host.sounds)
        assertEquals(listOf(n), host.dismissed)
    }

    @Test
    fun `at GUI scale 1 there is neither banner nor peek, only the chime`() {
        registerFeed()
        host.guiScale = 1.0
        notify()
        assertNull(alerts.peek)
        openFully()
        notify()
        assertNull(alerts.banner)
        assertEquals(2, host.sounds.count { it == ShellSound.NOTIFICATION })
    }

    @Test
    fun `the inventory button opens on home when there is news, and the inventory comes back (P-4, P-5)`() {
        registerFeed()
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        val inventory = host.openOtherScreen("inventory")
        val before = PhoneBadges.source
        try {
            PhoneBadges.source = BadgeSource { if (it == appId("feed")) 3 else 0 }
            tick()
            shell.openFromInventory(inventory)
            assertTrue(host.navigator.isHome)
            tick(OPEN_TICKS)
            assertEquals(State.OPEN, shell.state)
            host.pressKey(ESC)
            assertSame(inventory, host.screen)
        } finally {
            PhoneBadges.source = before
        }
    }
}
