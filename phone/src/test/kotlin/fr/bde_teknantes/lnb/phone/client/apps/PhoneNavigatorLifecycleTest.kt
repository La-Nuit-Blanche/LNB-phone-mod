package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The exact onShown / onHidden / onClosed sequence of every navigation, which app authors rely on (rule 7). */
class PhoneNavigatorLifecycleTest {
    private val log = mutableListOf<String>()
    private val heard = mutableListOf<String>()
    private val navigator = PhoneNavigator(AppRegistry()).apply { addListener(RecordingListener(heard)) }

    private fun screen(name: String) = RecordingScreen(name, log)

    /** Opens the phone on an app made of [stack] (root first, each pushed from the one under it), then clears the logs. */
    private fun openStack(vararg stack: RecordingScreen) {
        navigator.open()
        assertTrue(navigator.openApp(testApp("app") { stack[0] }))
        stack.toList().zipWithNext { below, above -> below.handle.push(above) }
        assertEquals(stack.toList(), navigator.screens)
        log.clear()
        heard.clear()
    }

    @Test
    fun `an app opened while the phone is put away is shown only when the phone comes out`() {
        val root = screen("root")
        assertTrue(navigator.openApp(testApp("app") { root }))
        assertSame(root, navigator.currentScreen)
        assertNull(navigator.shownScreen)
        assertEquals(emptyList(), log)

        navigator.open()
        assertSame(root, navigator.shownScreen)
        assertEquals(listOf("root:shown"), log)
    }

    @Test
    fun `opening another app closes the open one top first, then shows the new one`() {
        val root = screen("root")
        val a = screen("a")
        val b = screen("b")
        openStack(root, a, b)
        val other = screen("other")

        assertTrue(navigator.openApp(testApp("other") { other }))

        assertEquals(listOf("b:hidden", "b:closed", "a:closed", "root:closed", "other:shown"), log)
        assertEquals(listOf(other), navigator.screens)
        assertEquals("other", navigator.currentApp?.id?.path)
        root.handle.push(screen("late"))
        b.handle.pop()
        assertEquals(listOf(other), navigator.screens)
    }

    @Test
    fun `opening the open app again starts it on a new root screen`() {
        var made = 0
        val app = testApp("app") { screen("root${++made}") }
        navigator.open()
        navigator.openApp(app)
        val first = navigator.currentScreen as RecordingScreen
        first.handle.push(screen("a"))
        log.clear()

        assertTrue(navigator.openApp(app))

        val second = navigator.currentScreen
        assertNotSame(first, second)
        assertEquals(listOf(second), navigator.screens)
        assertEquals(listOf("a:hidden", "a:closed", "root1:closed", "root2:shown"), log)
    }

    @Test
    fun `going home closes every screen top first and hides only the shown one`() {
        openStack(screen("root"), screen("a"), screen("b"))

        navigator.home()

        assertEquals(listOf("b:hidden", "b:closed", "a:closed", "root:closed"), log)
        assertEquals(listOf("@INSTANT from b"), heard)
        assertTrue(navigator.isHome)
        assertNull(navigator.currentScreen)
        assertNull(navigator.shownScreen)
        assertFalse(navigator.canGoBack)
        assertTrue(navigator.isOpen)
    }

    @Test
    fun `going home with the phone put away closes the screens without hiding them again`() {
        openStack(screen("root"), screen("a"))
        navigator.close()
        assertEquals(listOf("a:hidden"), log)
        log.clear()

        navigator.home()
        assertEquals(listOf("a:closed", "root:closed"), log)

        navigator.open()
        assertTrue(navigator.isHome)
        assertEquals(listOf("a:closed", "root:closed"), log)
    }

    @Test
    fun `going home at home does nothing`() {
        navigator.open()
        heard.clear()
        navigator.home()
        assertTrue(navigator.isHome)
        assertEquals(emptyList(), heard)
    }

    @Test
    fun `pop from a lower screen closes it and every screen above it, top first`() {
        val root = screen("root")
        val a = screen("a")
        openStack(root, a, screen("b"), screen("c"))

        a.handle.pop()

        assertEquals(listOf("c:hidden", "c:closed", "b:closed", "a:closed", "root:shown"), log)
        assertEquals(listOf(root), navigator.screens)
        assertEquals(listOf("@BACK from c"), heard)
    }

    @Test
    fun `pop on the root screen does nothing, even with screens above it`() {
        val root = screen("root")
        val a = screen("a")
        openStack(root, a)

        root.handle.pop()

        assertEquals(listOf(root, a), navigator.screens)
        assertSame(a, navigator.shownScreen)
        assertEquals(emptyList(), log)
        assertEquals(emptyList(), heard)
    }

    @Test
    fun `back goes down one screen at a time and stops at the root`() {
        val root = screen("root")
        openStack(root, screen("a"), screen("b"))

        assertTrue(navigator.back())
        assertTrue(navigator.back())
        assertFalse(navigator.back())

        assertEquals(listOf("b:hidden", "b:closed", "a:shown", "a:hidden", "a:closed", "root:shown"), log)
        assertEquals(listOf(root), navigator.screens)
    }

    @Test
    fun `back at home does nothing`() {
        navigator.open()
        assertFalse(navigator.canGoBack)
        assertFalse(navigator.back())
        assertTrue(navigator.isHome)
    }

    @Test
    fun `push from a screen under the top opens the new screen above the top`() {
        val root = screen("root")
        val a = screen("a")
        openStack(root, a)
        val b = screen("b")

        root.handle.push(b)

        assertEquals(listOf(root, a, b), navigator.screens)
        assertEquals(listOf("a:hidden", "b:shown"), log)
        assertEquals(listOf("@FORWARD from a"), heard)
    }

    @Test
    fun `replace from a lower screen closes the screens above it and takes its place`() {
        val root = screen("root")
        val a = screen("a")
        openStack(root, a, screen("b"))
        val replacement = screen("new")

        a.handle.replace(replacement)

        assertEquals(listOf("b:hidden", "b:closed", "a:closed", "new:shown"), log)
        assertEquals(listOf(root, replacement), navigator.screens)
        assertEquals(listOf("@FORWARD from b"), heard)
        log.clear()
        assertTrue(navigator.back())
        assertEquals(listOf("new:hidden", "new:closed", "root:shown"), log)
    }

    @Test
    fun `replace on the root screen with screens above leaves the new screen alone`() {
        val root = screen("root")
        openStack(root, screen("a"))
        val replacement = screen("new")

        root.handle.replace(replacement)

        assertEquals(listOf("a:hidden", "a:closed", "root:closed", "new:shown"), log)
        assertEquals(listOf(replacement), navigator.screens)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `the screens under a target's top close without ever being shown when the player leaves`() {
        val feed = screen("feed")
        val thread = screen("thread")
        val reply = screen("reply")
        navigator.open()
        assertTrue(navigator.openApp(testApp("app", targets = { listOf(feed, thread, reply) }), "reply:1"))
        assertEquals(listOf(feed, thread, reply), navigator.screens)

        navigator.home()

        assertEquals(listOf("reply:shown", "reply:hidden", "reply:closed", "thread:closed", "feed:closed"), log)
    }

    @Test
    fun `navigating while the phone is put away shows nothing until it comes out`() {
        val root = screen("root")
        openStack(root)
        navigator.close()
        log.clear()

        val a = screen("a")
        root.handle.push(a)
        assertEquals(emptyList(), log)
        assertNull(navigator.shownScreen)
        a.handle.pop()
        assertEquals(listOf("a:closed"), log)

        navigator.open()
        assertEquals(listOf("a:closed", "root:shown"), log)
    }

    @Test
    fun `closing and opening the phone keeps the whole stack`() {
        val root = screen("root")
        openStack(root, screen("a"), screen("b"))

        navigator.close()
        navigator.open()
        assertTrue(navigator.back())
        assertTrue(navigator.back())

        assertEquals(
            listOf("b:hidden", "b:shown", "b:hidden", "b:closed", "a:shown", "a:hidden", "a:closed", "root:shown"),
            log,
        )
        assertEquals(listOf(root), navigator.screens)
    }

    @Test
    fun `opening or closing the phone a second time does nothing`() {
        openStack(screen("root"))

        navigator.open()
        assertEquals(emptyList(), log)
        assertEquals(emptyList(), heard)

        navigator.close()
        navigator.close()
        assertEquals(listOf("root:hidden"), log)
        assertEquals(listOf("@phone closed"), heard)
    }

    @Test
    fun `listeners hear of a change once the navigator shows it`() {
        val seen = mutableListOf<String>()
        navigator.addListener(object : NavigationListener {
            override fun onTransition(transition: Transition, outgoing: AppScreen?) {
                seen += "$transition: app ${navigator.currentApp?.id?.path}, top ${navigator.currentScreen}"
            }

            override fun onAppOpened(app: PhoneApp) {
                seen += "opened: app ${navigator.currentApp?.id?.path}"
            }

            override fun onPhoneOpened() {
                seen += "phone opened: ${navigator.isOpen}"
            }

            override fun onPhoneClosed() {
                seen += "phone closed: ${navigator.isOpen}"
            }
        })
        val root = screen("root")
        val a = screen("a")

        navigator.open()
        navigator.openApp(testApp("app") { root })
        root.handle.push(a)
        navigator.back()
        navigator.close()
        navigator.home()

        assertEquals(
            listOf(
                "phone opened: true",
                "FORWARD: app app, top root",
                "opened: app app",
                "FORWARD: app app, top a",
                "BACK: app app, top root",
                "phone closed: false",
                "INSTANT: app null, top null",
            ),
            seen,
        )
    }

    @Test
    fun `reset puts the phone away, closes the app and opens on home next time`() {
        val root = screen("root")
        val a = screen("a")
        openStack(root, a)

        navigator.reset()

        assertEquals(listOf("a:hidden", "a:closed", "root:closed"), log)
        assertEquals(listOf("@phone closed", "@INSTANT from a"), heard)
        assertFalse(navigator.isOpen)
        assertTrue(navigator.isHome)

        root.handle.push(screen("late"))
        a.handle.close()
        navigator.open()
        assertTrue(navigator.isHome)
        assertNull(navigator.shownScreen)
        assertEquals(listOf("a:hidden", "a:closed", "root:closed"), log)
    }

    @Test
    fun `reset with the phone put away closes the screens without hiding them again`() {
        openStack(screen("root"), screen("a"))
        navigator.close()
        log.clear()
        heard.clear()

        navigator.reset()

        assertEquals(listOf("a:closed", "root:closed"), log)
        assertEquals(listOf("@INSTANT from a"), heard)
    }

    @Test
    fun `reset at home with the phone put away tells no one`() {
        navigator.reset()
        assertEquals(emptyList(), heard)
        assertTrue(navigator.isHome)
        assertFalse(navigator.isOpen)
    }

    @Test
    fun `the phone works as new after a reset`() {
        openStack(screen("root"))
        navigator.reset()
        log.clear()

        val fresh = screen("fresh")
        navigator.open()
        assertTrue(navigator.openApp(testApp("app") { fresh }))

        assertSame(fresh, navigator.shownScreen)
        assertEquals(listOf("fresh:shown"), log)
    }
}
