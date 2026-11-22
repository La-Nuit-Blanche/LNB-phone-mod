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
    fun `a target opens the stack the app gives for it`() {
        val feed = screen("feed")
        val thread = screen("thread")
        val app = testApp("app", targets = { if (it == "thread:1") listOf(feed, thread) else emptyList() }) { screen("root") }
        registry.register(app, "test")
        navigator.open()

        assertTrue(navigator.openApp(appId("app"), "thread:1"))

        assertEquals(listOf(feed, thread), navigator.screens)
        assertEquals(listOf("thread:shown"), log)
        assertTrue(navigator.back())
        assertEquals(listOf("thread:shown", "thread:hidden", "thread:closed", "feed:shown"), log)
    }

    @Test
    fun `an unknown target or an app without targets opens the root screen`() {
        navigator.open()
        assertTrue(navigator.openApp(testApp("app", targets = { emptyList() }) { screen("root") }, "nope"))
        assertEquals("root", navigator.currentScreen.toString())
        assertTrue(navigator.openApp(testApp("plain") { screen("plain root") }, "thread:1"))
        assertEquals("plain root", navigator.currentScreen.toString())
    }

    @Test
    fun `opening a target while the app is open starts it afresh`() {
        val root = openOn(screen("root"))
        val thread = screen("thread")
        assertTrue(navigator.openApp(testApp("app", targets = { listOf(thread) }) { screen("unused") }, "thread:1"))
        assertEquals(listOf("root:shown", "root:hidden", "root:closed", "thread:shown"), log)
        root.handle.push(screen("late"))
        assertEquals(listOf(thread), navigator.screens)
    }

    @Test
    fun `a target stack that repeats a screen fails to open`() {
        navigator.open()
        val twice = screen("twice")
        assertFalse(navigator.openApp(testApp("app", targets = { listOf(twice, twice) }), "x"))
        assertTrue(navigator.isHome)
        assertEquals(listOf("failed app"), heard)
    }

    @Test
    fun `replace swaps the screen in place`() {
        val root = openOn(screen("root"))
        val compose = screen("compose")
        root.handle.push(compose)
        val thread = screen("thread")
        log.clear()

        compose.handle.replace(thread)

        assertEquals(listOf(root, thread), navigator.screens)
        assertEquals(listOf("compose:hidden", "compose:closed", "thread:shown"), log)
        compose.handle.push(screen("late"))
        assertEquals(listOf(root, thread), navigator.screens)
        navigator.back()
        assertSame(root, navigator.currentScreen)
    }

    @Test
    fun `replace on the root screen makes a new root`() {
        val loading = openOn(screen("loading"))
        val loaded = screen("loaded")
        loading.handle.replace(loaded)
        assertEquals(listOf(loaded), navigator.screens)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `a screen's handle tells whether back leads anywhere`() {
        val root = openOn(screen("root"))
        val second = screen("second")
        assertFalse(root.handle.canGoBack)
        root.handle.push(second)
        assertTrue(second.handle.canGoBack)
        assertFalse(root.handle.canGoBack)

        second.handle.pop()
        assertFalse(second.handle.canGoBack)
        val loaded = screen("loaded")
        root.handle.replace(loaded)
        assertFalse(loaded.handle.canGoBack)
        assertFalse(root.handle.canGoBack)
    }

    @Test
    fun `calls from another thread throw`() {
        val offThread = PhoneNavigator(registry) { false }
        assertThrows(IllegalStateException::class.java) { offThread.open() }
    }
}
