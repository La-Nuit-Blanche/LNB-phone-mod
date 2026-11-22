package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhoneNavigatorTest {
    private val log = mutableListOf<String>()
    private val heard = mutableListOf<String>()
    private val registry = AppRegistry()

    private val navigator = PhoneNavigator(registry).apply {
        addListener(object : NavigationListener {
            override fun onTransition(transition: Transition, outgoing: AppScreen?) {
                heard += "$transition from $outgoing"
            }

            override fun onAppOpened(app: PhoneApp) {
                heard += "opened ${app.id.path}"
            }

            override fun onAppFailed(app: PhoneApp, error: Throwable) {
                heard += "failed ${app.id.path}"
            }

            override fun onPhoneClosed() {
                heard += "phone closed"
            }
        })
    }

    private fun screen(name: String, onEvent: RecordingScreen.(String) -> Unit = {}) = RecordingScreen(name, log, onEvent)

    private fun openOn(root: RecordingScreen): RecordingScreen {
        navigator.open()
        assertTrue(navigator.openApp(testApp("app") { root }))
        return root
    }

    @Test
    fun `push, pop and home call the lifecycle in order`() {
        val root = openOn(screen("root"))
        val second = screen("second")
        root.handle.push(second)
        assertSame(second, navigator.currentScreen)
        assertTrue(navigator.canGoBack)

        assertTrue(navigator.back())
        assertSame(root, navigator.currentScreen)
        navigator.home()

        assertTrue(navigator.isHome)
        assertEquals(
            listOf(
                "root:shown",
                "root:hidden", "second:shown",
                "second:hidden", "second:closed", "root:shown",
                "root:hidden", "root:closed",
            ),
            log,
        )
        assertEquals(listOf("FORWARD from null", "opened app", "FORWARD from root", "BACK from second", "INSTANT from root"), heard)
    }

    @Test
    fun `closing the phone keeps the app for the next open`() {
        val root = openOn(screen("root"))
        navigator.close()
        assertNull(navigator.shownScreen)
        navigator.open()
        assertSame(root, navigator.shownScreen)
        assertEquals(listOf("root:shown", "root:hidden", "root:shown"), log)
    }

    @Test
    fun `back on the root screen does nothing`() {
        val root = openOn(screen("root"))
        assertFalse(navigator.canGoBack)
        assertFalse(navigator.back())
        root.handle.pop()
        assertSame(root, navigator.currentScreen)
        assertEquals(listOf("root:shown"), log)
    }

    @Test
    fun `a factory that throws leaves the phone where it was`() {
        navigator.open()
        assertFalse(navigator.openApp(testApp("broken") { error("no screen") }))
        assertTrue(navigator.isHome)
        assertEquals(listOf("failed broken"), heard)
    }

    @Test
    fun `a screen that throws closes its whole app`() {
        val root = openOn(screen("root"))
        val broken = object : RecordingScreen("broken", log) {
            override fun mousePressed(x: Float, y: Float, button: Int): Boolean = error("boom")
        }
        root.handle.push(broken)
        log.clear()

        assertFalse(navigator.mousePressed(10f, 10f, 0))

        assertTrue(navigator.isHome)
        // The broken screen is never called again; the others are closed normally.
        assertEquals(listOf("root:closed"), log)
        assertTrue(heard.contains("failed app"))
        navigator.render(broken, TestCanvas, 0f, 0f, 0f)
        assertEquals(listOf("root:closed"), log)
    }

    @Test
    fun `a closed screen's handle does nothing`() {
        val root = openOn(screen("root"))
        val second = screen("second")
        root.handle.push(second)
        navigator.back()

        second.handle.push(screen("late"))
        second.handle.close()

        assertSame(root, navigator.currentScreen)
        assertTrue(navigator.isOpen)
    }

    @Test
    fun `a screen may navigate from its own callbacks`() {
        val target = screen("target")
        openOn(screen("root") { event -> if (event == "shown" && log.count { it == "root:shown" } == 1) handle.push(target) })

        assertSame(target, navigator.shownScreen)
        assertEquals(listOf("root:shown", "root:hidden", "target:shown"), log)
    }

    @Test
    fun `pushing a screen twice fails the app`() {
        val root = openOn(screen("root"))
        val reused = screen("reused") { event -> if (event == "pressed") handle.push(this) }
        root.handle.push(reused)

        navigator.mousePressed(0f, 0f, 0)

        assertTrue(navigator.isHome)
        assertTrue(heard.contains("failed app"))
    }

    @Test
    fun `a screen's close puts the phone away`() {
        val root = openOn(screen("root"))
        root.handle.close()
        assertFalse(navigator.isOpen)
        assertSame(root, navigator.currentScreen)
        assertTrue(heard.contains("phone closed"))
    }

    @Test
    fun `calls from another thread throw`() {
        val offThread = PhoneNavigator(registry) { false }
        assertThrows(IllegalStateException::class.java) { offThread.open() }
    }
}
