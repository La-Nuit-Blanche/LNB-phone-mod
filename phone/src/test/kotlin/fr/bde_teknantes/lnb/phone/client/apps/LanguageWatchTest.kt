package fr.bde_teknantes.lnb.phone.client.apps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A language change sends the open app home, so that a phone left open in an app never shows the old language. */
class LanguageWatchTest {
    private val log = ArrayList<String>()
    private val registry = AppRegistry().apply {
        register(testApp("settings") { RecordingScreen("settings", log) }, "test")
        freeze()
    }
    private val navigator = PhoneNavigator(registry)
    private val watch = LanguageWatch(navigator)

    @Test
    fun `the phone left in an app, put away, opens home after the language changed`() {
        watch.loaded("en_us")
        navigator.open()
        navigator.openApp(appId("settings"))
        navigator.close()
        log.clear()

        watch.loaded("fr_fr")
        assertTrue(navigator.isHome)
        assertEquals(listOf("settings:closed"), log)
        navigator.open()
        assertTrue(navigator.isHome, "reopened, the phone shows home; the app builds new screens when opened")
    }

    @Test
    fun `the first load and a reload in the same language change nothing`() {
        navigator.open()
        navigator.openApp(appId("settings"))
        watch.loaded("fr_fr")
        watch.loaded("fr_fr")
        assertFalse(navigator.isHome)
    }

    @Test
    fun `an open phone goes home at once`() {
        watch.loaded("en_us")
        navigator.open()
        navigator.openApp(appId("settings"))
        watch.loaded("fr_fr")
        assertTrue(navigator.isHome)
        assertTrue(navigator.isOpen)
    }
}
