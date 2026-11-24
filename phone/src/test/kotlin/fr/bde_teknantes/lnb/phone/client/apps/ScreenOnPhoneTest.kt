package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AppScreen.isOnPhone]: true from the moment the phone takes a screen until it closes or throws, whether it shows or
 * not; so an app finds out that a screen is gone even when its onClosed never comes (it threw, or its stack never
 * opened).
 */
class ScreenOnPhoneTest {
    private val log = mutableListOf<String>()
    private val navigator = PhoneNavigator(AppRegistry())

    private fun screen(name: String, onEvent: RecordingScreen.(String) -> Unit = {}) = RecordingScreen(name, log, onEvent)

    @Test
    fun `a screen is on the phone from the phone taking it until it is popped, shown or not, the phone away or not`() {
        val root = screen("root")
        val top = screen("top")
        assertFalse(root.isOnPhone)
        navigator.open()
        navigator.openApp(testApp("app") { root })
        assertTrue(root.isOnPhone)
        root.handle.push(top)
        // Under another screen, and with the phone put away: still on it.
        navigator.close()
        assertTrue(root.isOnPhone)
        assertTrue(top.isOnPhone)
        navigator.open()
        top.handle.pop()
        assertFalse(top.isOnPhone)
        assertTrue(root.isOnPhone)
        navigator.home()
        assertFalse(root.isOnPhone)
    }

    @Test
    fun `a screen that threw is off the phone, though its onClosed never came`() {
        val root = screen("root") { if (it == "tick") error("root fails in tick") }
        navigator.open()
        navigator.openApp(testApp("app") { root })
        navigator.tick()
        assertFalse(root.isOnPhone)
        assertFalse(log.contains("root:closed"))
    }

    @Test
    fun `a screen built for a stack that never opened was never on the phone`() {
        val root = screen("root")
        navigator.open()
        val app = testApp("app", targets = { listOf(root, error("the second screen fails")) }) { screen("other") }
        assertFalse(navigator.openApp(app, "target"))
        assertFalse(root.isOnPhone)
    }
}
