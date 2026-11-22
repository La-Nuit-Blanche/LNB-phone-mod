package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Screens and listeners that navigate from inside the navigator's own callbacks. */
class PhoneNavigatorReentrancyTest {
    private val log = mutableListOf<String>()
    private val heard = mutableListOf<String>()
    private val navigator = PhoneNavigator(AppRegistry()).apply { addListener(RecordingListener(heard)) }

    private fun screen(name: String, onEvent: RecordingScreen.(String) -> Unit = {}) = RecordingScreen(name, log, onEvent)

    private fun openStack(vararg stack: RecordingScreen) {
        navigator.open()
        assertTrue(navigator.openApp(testApp("app") { stack[0] }))
        stack.toList().zipWithNext { below, above -> below.handle.push(above) }
        log.clear()
        heard.clear()
    }

    @Test
    fun `a screen that puts the phone away from onShown is hidden again at once`() {
        val root = screen("root") { if (it == "shown") handle.close() }
        navigator.openApp(testApp("app") { root })

        navigator.open()

        assertEquals(listOf("root:shown", "root:hidden"), log)
        assertFalse(navigator.isOpen)
        assertNull(navigator.shownScreen)
        assertSame(root, navigator.currentScreen)
        assertEquals(listOf("@FORWARD from null", "@opened app", "@phone opened", "@phone closed"), heard)
    }

    @Test
    fun `a screen that goes home from onShown leaves the phone on home`() {
        navigator.open()
        navigator.openApp(testApp("app") { screen("root") { if (it == "shown") handle.home() } })

        assertEquals(listOf("root:shown", "root:hidden", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertNull(navigator.shownScreen)
        assertTrue(navigator.isOpen)
    }

    @Test
    fun `a screen that replaces itself from onShown hands over to the new screen`() {
        val loaded = screen("loaded")
        navigator.open()
        navigator.openApp(testApp("app") { screen("loading") { if (it == "shown") handle.replace(loaded) } })

        assertEquals(listOf("loading:shown", "loading:hidden", "loading:closed", "loaded:shown"), log)
        assertEquals(listOf(loaded), navigator.screens)
        assertSame(loaded, navigator.shownScreen)
    }

    @Test
    fun `a target's top screen that pops itself from onShown reveals the screen under it`() {
        val feed = screen("feed")
        val gone = screen("gone") { if (it == "shown") handle.pop() }
        navigator.open()
        navigator.openApp(testApp("app", targets = { listOf(feed, gone) }), "post:1")

        assertEquals(listOf("gone:shown", "gone:hidden", "gone:closed", "feed:shown"), log)
        assertEquals(listOf(feed), navigator.screens)
    }

    @Test
    fun `a screen that pops itself from onHidden as the phone is put away is gone on the next open`() {
        val root = screen("root")
        openStack(root, screen("a") { if (it == "hidden") handle.pop() })

        navigator.close()
        assertEquals(listOf("a:hidden", "a:closed"), log)
        assertEquals(listOf(root), navigator.screens)

        navigator.open()
        assertEquals(listOf("a:hidden", "a:closed", "root:shown"), log)
    }

    @Test
    fun `a screen that pops itself from onHidden under a push takes the pushed screen with it`() {
        val root = screen("root")
        val a = screen("a") { if (it == "hidden") handle.pop() }
        openStack(root, a)

        a.handle.push(screen("b"))

        assertEquals(listOf("a:hidden", "b:closed", "a:closed", "root:shown"), log)
        assertEquals(listOf(root), navigator.screens)
    }

    @Test
    fun `a screen popped off the stack can't navigate from its onHidden or onClosed`() {
        val root = screen("root")
        val a = screen("a") { event ->
            when (event) {
                "hidden" -> handle.push(screen("from hidden"))
                "closed" -> handle.home()
            }
        }
        openStack(root, a)

        assertTrue(navigator.back())

        assertEquals(listOf("a:hidden", "a:closed", "root:shown"), log)
        assertEquals(listOf(root), navigator.screens)
    }

    @Test
    fun `a screen closed by going home can't bring the app back from its callbacks`() {
        val root = screen("root") { if (it == "closed") handle.replace(screen("from closed")) }
        openStack(root, screen("a") { if (it == "hidden") handle.push(screen("from hidden")) })

        navigator.home()

        assertEquals(listOf("a:hidden", "a:closed", "root:closed"), log)
        assertTrue(navigator.isHome)
    }

    @Test
    fun `a screen closed by a reset can't open the phone again from its callbacks`() {
        openStack(screen("root") { if (it == "closed") handle.push(screen("late")) })

        navigator.reset()

        assertFalse(navigator.isOpen)
        assertTrue(navigator.isHome)
    }

    @Test
    fun `navigation from an input handler happens before the handler returns`() {
        val root = screen("root")
        val b = screen("b")
        openStack(root, screen("a") { event ->
            if (event == "pressed") {
                handle.push(b)
                assertSame(b, navigator.shownScreen)
                handle.pop()
            }
        })

        assertTrue(navigator.mousePressed(0f, 0f, 0))

        assertEquals(listOf("a:pressed", "a:hidden", "b:shown", "b:hidden", "b:closed", "a:closed", "root:shown"), log)
        assertEquals(listOf(root), navigator.screens)
    }

    @Test
    fun `a screen that goes home from an input handler gets no more input`() {
        openStack(screen("root") { if (it == "pressed") handle.home() })

        assertTrue(navigator.mousePressed(0f, 0f, 0))
        assertFalse(navigator.mousePressed(0f, 0f, 0))
        navigator.tick()

        assertEquals(listOf("root:pressed", "root:hidden", "root:closed"), log)
    }

    @Test
    fun `a screen that puts the phone away from a key press gets input again when it comes back`() {
        val root = screen("root") { if (it == "key") handle.close() }
        openStack(root)

        assertTrue(navigator.keyPressed(265, 0, 0))
        assertFalse(navigator.keyPressed(265, 0, 0))
        assertFalse(navigator.charTyped('a', 0))
        navigator.tick()
        assertEquals(listOf("root:key", "root:hidden"), log)

        navigator.open()
        navigator.tick()
        assertTrue(navigator.charTyped('a', 0))
        assertEquals(listOf("root:key", "root:hidden", "root:shown", "root:tick", "root:char"), log)
    }

    @Test
    fun `screens that keep opening one another from onShown fail the app instead of hanging`() {
        var made = 0
        fun chain(): RecordingScreen = screen("s${made++}") { event ->
            // Bounded so that a navigator without its own limit fails the test instead of looping for ever.
            if (event == "shown" && made < 10_000) handle.push(chain())
        }
        navigator.open()

        navigator.openApp(testApp("app") { chain() })

        assertTrue(made < 10_000, "the navigator never stopped the chain")
        assertTrue(navigator.isHome)
        assertNull(navigator.shownScreen)
        assertEquals(1, heard.count { it == "@failed app" })
        assertConsistentLifecycles(log)
    }

    @Test
    fun `a screen and the screens it pushes popping back fail the app instead of hanging`() {
        var bounces = 0
        val root = screen("root") { event ->
            if (event == "shown" && bounces++ < 10_000) handle.push(screen("bouncer$bounces") { if (it == "shown") handle.pop() })
        }
        navigator.open()

        navigator.openApp(testApp("app") { root })

        assertTrue(bounces < 10_000, "the navigator never stopped the bouncing")
        assertTrue(navigator.isHome)
        assertEquals(1, heard.count { it == "@failed app" })
        assertConsistentLifecycles(log)
    }

    @Test
    fun `a listener that goes home when the phone comes out closes the app unseen`() {
        openStack(screen("root"), screen("a"))
        navigator.close()
        log.clear()
        navigator.addListener(object : NavigationListener {
            override fun onPhoneOpened() = navigator.home()
        })

        navigator.open()

        assertEquals(listOf("a:closed", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertTrue(navigator.isOpen)
        assertNull(navigator.shownScreen)
    }

    @Test
    fun `a listener that opens another app as one opens leaves only the second open`() {
        val first = screen("first")
        val second = screen("second")
        val secondApp = testApp("second") { second }
        navigator.addListener(object : NavigationListener {
            override fun onAppOpened(app: PhoneApp) {
                if (app.id.path == "first") navigator.openApp(secondApp)
            }
        })
        navigator.open()

        navigator.openApp(testApp("first") { first })

        assertSame(secondApp, navigator.currentApp)
        assertEquals(listOf(second), navigator.screens)
        assertSame(second, navigator.shownScreen)
        assertEquals(listOf("first:closed", "second:shown"), log)
        first.handle.push(screen("late"))
        assertEquals(listOf(second), navigator.screens)
    }

    @Test
    fun `a listener that opens another app on a failure leaves that app working`() {
        val fallback = screen("fallback")
        val fallbackApp = testApp("fallback") { fallback }
        navigator.addListener(object : NavigationListener {
            override fun onAppFailed(app: PhoneApp, error: Throwable) {
                if (app.id.path == "app") navigator.openApp(fallbackApp)
            }
        })
        val root = screen("root")
        openStack(root, screen("broken") { if (it == "pressed") error("boom") })

        navigator.mousePressed(0f, 0f, 0)

        assertSame(fallbackApp, navigator.currentApp)
        assertSame(fallback, navigator.shownScreen)
        assertEquals(listOf(fallback), navigator.screens)
        assertTrue(log.contains("root:closed"))
        assertConsistentLifecycles(log)
        root.handle.push(screen("late"))
        assertEquals(listOf(fallback), navigator.screens)
    }
}
