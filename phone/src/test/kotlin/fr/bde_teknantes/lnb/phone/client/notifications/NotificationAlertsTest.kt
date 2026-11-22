package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.apps.RecordingScreen
import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationAlertsTest {
    private val on = AlertSettings(sounds = true, preview = true)

    private fun decide(view: PhoneView, onScreen: Boolean = false, settings: AlertSettings = on, installed: Boolean = true) =
        NotificationAlerts.decide(installed, PhoneSituation(view, onScreen), settings)

    @Test
    fun `follows art direction 10 and the flows`() {
        assertEquals(Alert(AlertVisual.PEEK, chime = true), decide(PhoneView.CLOSED))
        assertEquals(Alert(AlertVisual.NONE, chime = true), decide(PhoneView.CLOSED, settings = on.copy(preview = false)))
        assertEquals(Alert(AlertVisual.BANNER, chime = true), decide(PhoneView.HOME))
        assertEquals(Alert(AlertVisual.BANNER, chime = true), decide(PhoneView.OTHER_APP, settings = on.copy(preview = false)))
        assertEquals(Alert(AlertVisual.BANNER, chime = true), decide(PhoneView.SAME_APP))
        assertEquals(Alert(AlertVisual.NONE, chime = true, keep = false), decide(PhoneView.SAME_APP, onScreen = true))
        assertEquals(Alert(AlertVisual.PEEK, chime = false), decide(PhoneView.CLOSED, settings = on.copy(sounds = false)))
        assertEquals(Alert.QUIET, decide(PhoneView.HOME, installed = false))
    }

    @Test
    fun `reads the situation from the navigator and the shown screen`() {
        val registry = AppRegistry()
        val log = mutableListOf<String>()
        val feed = testApp("feed", root = { object : RecordingScreen("feed", log) {
            override fun showsTarget(target: String) = target == "thread:1"
        } })
        registry.register(feed, "test")
        registry.register(testApp("mail"), "test")
        val navigator = PhoneNavigator(registry)
        fun notification(target: String?) =
            PhoneNotification(1, appId("feed"), Component.empty(), Component.empty(), target, 0, true)

        assertEquals(PhoneSituation(PhoneView.CLOSED), NotificationAlerts.situation(navigator, notification(null)))
        navigator.open()
        assertEquals(PhoneSituation(PhoneView.HOME), NotificationAlerts.situation(navigator, notification(null)))
        navigator.openApp(appId("mail"))
        assertEquals(PhoneSituation(PhoneView.OTHER_APP), NotificationAlerts.situation(navigator, notification(null)))
        navigator.openApp(appId("feed"))
        assertEquals(PhoneSituation(PhoneView.SAME_APP, true), NotificationAlerts.situation(navigator, notification("thread:1")))
        assertEquals(PhoneSituation(PhoneView.SAME_APP, false), NotificationAlerts.situation(navigator, notification("thread:2")))
        assertEquals(PhoneSituation(PhoneView.SAME_APP, false), NotificationAlerts.situation(navigator, notification(null)))
    }

    @Test
    fun `opening a notification opens its app and then the phone`() {
        val registry = AppRegistry()
        registry.register(testApp("feed"), "test")
        val navigator = PhoneNavigator(registry)
        val notification = PhoneNotification(1, appId("feed"), Component.empty(), Component.empty(), null, 0, true)
        assertTrue(NotificationAlerts.open(navigator, notification))
        assertTrue(navigator.isOpen)
        assertEquals(appId("feed"), navigator.currentApp?.id)
        assertFalse(NotificationAlerts.open(navigator, notification.copy(appId = appId("gone"))))
    }

    @Test
    fun `the client store counts installed apps only and clears on read`() {
        val store = ClientNotifications { it != appId("gone") }
        val arrived = mutableListOf<Long>()
        store.addListener(object : ClientNotifications.Listener {
            override fun onArrived(notification: PhoneNotification) {
                arrived += notification.id
            }
        })
        fun n(id: Long, app: String, delivered: Boolean = true) =
            PhoneNotification(id, appId(app), Component.empty(), Component.empty(), null, 0, delivered)

        store.sync(listOf(n(1, "feed", delivered = false), n(2, "gone")))
        assertEquals(listOf(1L), store.missedWhileAway.map { it.id })
        store.arrived(n(3, "feed"))
        store.arrived(n(3, "feed"))
        assertEquals(listOf(3L), arrived)
        assertEquals(2, store.unreadCount(appId("feed")))
        assertEquals(0, store.unreadCount(appId("gone")))
        assertEquals(2, store.totalUnread)
        assertEquals(3L, store.read(appId("feed")))
        assertFalse(store.hasUnread)
        assertEquals(1, store.all.size)
    }
}
