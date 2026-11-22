package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
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

/** How the phone's state is read for a new notification, and how a notification is opened. */
class NotificationSituationTest {
    private val log = mutableListOf<String>()
    private val asked = mutableListOf<String>()

    /** Feed's root screen shows "thread:1" and nothing else. */
    private inner class FeedRoot : RecordingScreen("feed", log) {
        override fun showsTarget(target: String): Boolean {
            asked += target
            return target == "thread:1"
        }
    }

    private val registry = AppRegistry().apply {
        register(testApp("feed", targets = { listOf(RecordingScreen("thread:$it", log)) }, root = { FeedRoot() }), "test")
        register(testApp("mail"), "test")
        register(testApp("broken", root = { throw IllegalStateException("no screens") }), "test")
    }
    private val navigator = PhoneNavigator(registry)

    private fun notification(target: String?, app: String = "feed") =
        PhoneNotification(1, appId(app), Component.empty(), Component.empty(), target, 0, true)

    private fun situation(target: String?, app: String = "feed") = NotificationAlerts.situation(navigator, notification(target, app))

    @Test
    fun `a phone put away on the notification's app and target is closed`() {
        navigator.open()
        navigator.openApp(appId("feed"))
        navigator.close()
        assertEquals(PhoneSituation(PhoneView.CLOSED), situation("thread:1"))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `back on home after an app`() {
        navigator.open()
        navigator.openApp(appId("feed"))
        navigator.home()
        assertEquals(PhoneSituation(PhoneView.HOME), situation("thread:1"))
    }

    @Test
    fun `another app open`() {
        navigator.open()
        navigator.openApp(appId("mail"))
        assertEquals(PhoneSituation(PhoneView.OTHER_APP), situation("thread:1"))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `the same app is asked with the exact target`() {
        navigator.open()
        navigator.openApp(appId("feed"))
        assertEquals(PhoneSituation(PhoneView.SAME_APP, true), situation("thread:1"))
        assertEquals(PhoneSituation(PhoneView.SAME_APP, false), situation("thread:10"))
        assertEquals(listOf("thread:1", "thread:10"), asked)
    }

    @Test
    fun `a notification without a target is never on screen, and the screen isn't asked`() {
        navigator.open()
        navigator.openApp(appId("feed"))
        assertEquals(PhoneSituation(PhoneView.SAME_APP, false), situation(null))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `only the shown screen answers`() {
        navigator.open()
        navigator.openApp(appId("feed"), "2")
        // The stack the target gave has a screen that doesn't override showsTarget.
        assertEquals(PhoneSituation(PhoneView.SAME_APP, false), situation("thread:1"))
    }

    @Test
    fun `a screen that throws while asked lets the banner show and doesn't escape`() {
        val failing = AppRegistry().apply {
            register(testApp("feed", root = { object : AppScreen() {
                override fun render(canvas: fr.bde_teknantes.lnb.phone.api.client.AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {}
                override fun showsTarget(target: String): Boolean = throw IllegalStateException("boom")
            } }), "test")
        }
        val nav = PhoneNavigator(failing)
        nav.open()
        nav.openApp(appId("feed"))
        val result = NotificationAlerts.situation(nav, notification("thread:1"))
        assertFalse(result.targetOnScreen)
    }

    @Test
    fun `opening a notification while the phone is put away opens its app at its target, then the phone`() {
        assertTrue(NotificationAlerts.open(navigator, notification("7")))
        assertTrue(navigator.isOpen)
        assertEquals(appId("feed"), navigator.currentApp?.id)
        assertEquals("thread:7", navigator.shownScreen.toString())
    }

    @Test
    fun `opening a notification over another app switches to its app`() {
        navigator.open()
        navigator.openApp(appId("mail"))
        assertTrue(NotificationAlerts.open(navigator, notification(null)))
        assertEquals(appId("feed"), navigator.currentApp?.id)
        assertTrue(navigator.isOpen)
    }

    @Test
    fun `a notification whose app fails to start changes nothing`() {
        navigator.open()
        navigator.openApp(appId("mail"))
        navigator.close()
        assertFalse(NotificationAlerts.open(navigator, notification(null, "broken")))
        assertFalse(navigator.isOpen)
        assertEquals(appId("mail"), navigator.currentApp?.id)
    }

    @Test
    fun `a notification of an app that isn't installed changes nothing`() {
        assertFalse(NotificationAlerts.open(navigator, notification(null, "gone")))
        assertFalse(navigator.isOpen)
        assertTrue(navigator.isHome)
    }
}
