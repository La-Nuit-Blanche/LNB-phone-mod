package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationSync
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotifications
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The shell wired to the client's notification store as the game wires them (LnbPhoneClient): the store tells the
 * shell of arrivals, the badges count the store, opening an app reads it, reading in place dismisses. The flows end to
 * end: P-5, P-8, P-9, P-10, P-11, P-14, the login sync, and the badge kept by decision 2.
 */
internal class ShellNotificationFlowTest : AlertsTestBase() {
    private lateinit var store: ClientNotifications
    private val sent = ArrayList<CustomPacketPayload>()

    private val feed = appId("feed")

    @BeforeEach
    fun wire() {
        store = ClientNotifications { host.apps[it] != null }
        val sync = ClientNotificationSync(store) { sent += it }
        host.navigator.addListener(sync)
        host.onDismiss = sync::dismiss
        PhoneBadges.source = BadgeSource(store::unreadCount)
        store.addListener(object : ClientNotifications.Listener {
            override fun onArrived(notification: PhoneNotification) = shell.notificationArrived(notification)
        })
        registerAll()
    }

    /** A notification the server sends while the player is connected. */
    private fun arrive(app: String = "feed", target: String? = "thread:1"): PhoneNotification =
        notification(app, target).also { store.arrived(it) }

    private val badge: Int get() = shell.counts[feed] ?: 0

    private val ledLit: Boolean get() = shell.counts.values.any { it > 0 }

    @Test
    fun `notifications waiting at login never peek or chime, and wait as badges`() {
        store.sync(listOf(notification(), notification(), notification(app = "mail")), login = true)
        tick()
        assertNull(alerts.peek)
        assertEquals(0, chimes)
        assertEquals(2, badge)
        assertTrue(ledLit)
        // Nor a resync later, nor the same notifications sent again.
        store.sync(store.all, login = false)
        store.all.forEach(store::arrived)
        tick()
        assertNull(alerts.peek)
        assertEquals(0, chimes)
    }

    @Test
    fun `notifications waiting at login don't show a banner over the open phone either`() {
        openFully()
        host.sounds.clear()
        store.sync(listOf(notification()), login = true)
        assertNull(alerts.banner)
        assertEquals(0, chimes)
    }

    @Test
    fun `P-8, a peek, then the badge and the LED until the app is opened`() {
        val n = arrive()
        assertEquals(n, alerts.peek?.notification)
        assertEquals(1, chimes)
        tick(PEEK_LIFE_TICKS)
        assertNull(alerts.peek)
        openFully()
        assertEquals(1, badge)
        assertTrue(ledLit)
        clickIcon(0)
        tick()
        assertEquals(0, badge)
        assertTrue(!ledLit)
        assertEquals(listOf(ClearAppNotificationsPayload(feed, n.id)), sent)
    }

    @Test
    fun `P-9, the phone key during the peek lands on the target and clears the badge`() {
        arrive(target = "thread:8")
        tick(10)
        pressPhoneKeyInGame()
        tick()
        assertEquals("thread:8", host.navigator.currentScreen.toString())
        assertEquals(0, badge)
    }

    @Test
    fun `P-10, the banner over home, its badge under it, and a click that opens the thread and clears it`() {
        openFully()
        arrive(target = "thread:8")
        tick(BANNER_IN_TICKS)
        assertEquals(1, badge)
        clickAt(bannerMiddle)
        tick()
        assertEquals("thread:8", host.navigator.currentScreen.toString())
        assertEquals(0, badge)
    }

    @Test
    fun `P-11, a banner over another app that isn't clicked leaves the badge waiting on home`() {
        openFully()
        host.navigator.openApp(appId("mail"))
        arrive()
        tick(BANNER_LIFE_TICKS)
        assertNull(alerts.banner)
        clickHomeButton()
        assertEquals(1, badge)
    }

    @Test
    fun `P-14, preview off, nothing rises, the chime plays, the badge waits, and banners still show inside the phone`() {
        host.settings.update { it.copy(preview = false) }
        arrive()
        assertNull(alerts.peek)
        assertEquals(1, chimes)
        openFully()
        assertEquals(1, badge)
        arrive(app = "mail")
        assertNotNull(alerts.banner)
    }

    @Test
    fun `a notification whose target is on screen is read in place, no badge, and the server is told`() {
        openFully()
        host.navigator.openApp(feed, "thread:1")
        sent.clear()
        val n = arrive(target = "thread:1")
        tick()
        assertEquals(0, badge)
        assertEquals(1, sent.size)
        assertEquals(n.id, assertInstanceOf(DismissNotificationPayload::class.java, sent.single()).id)
    }

    @Test
    fun `at GUI scale 1 a target "on screen" is not read in place (decision 1)`() {
        host.guiScale = 1.0
        openFully()
        host.navigator.openApp(feed, "thread:1")
        sent.clear()
        arrive(target = "thread:1")
        tick()
        assertEquals(1, badge)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `reopening the phone onto the app left open keeps its badge, which clears when the app is opened (decision 2)`() {
        openFully()
        host.navigator.openApp(feed)
        shell.close()
        closeFully()
        arrive(target = null)
        tick(PEEK_LIFE_TICKS)
        openFully()
        assertEquals("feed", host.navigator.currentScreen.toString())
        assertEquals(1, badge)
        clickHomeButton()
        clickIcon(0)
        tick()
        assertEquals(0, badge)
    }

    @Test
    fun `P-5, the inventory button with news opens on home, with the badge, and the inventory comes back`() {
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        val inventory = host.openOtherScreen("inventory")
        arrive()
        tick()
        assertNull(alerts.peek)
        shell.openFromInventory(inventory)
        tick(OPEN_TICKS)
        assertTrue(host.navigator.isHome)
        assertEquals(1, badge)
        host.pressKey(ESC)
        assertEquals(inventory, host.screen)
    }

    @Test
    fun `a notification for an app this client doesn't have shows nothing and lights nothing`() {
        arrive(app = "gone")
        tick()
        assertNull(alerts.peek)
        assertEquals(0, chimes)
        assertTrue(!ledLit)
    }
}
