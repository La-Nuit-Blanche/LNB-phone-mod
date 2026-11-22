package fr.bde_teknantes.lnb.phone.client.apps

import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** App code that throws (rule 2): the app closes as a whole, the phone survives, and the culprit is never called again. */
class PhoneNavigatorFailureTest {
    private val log = mutableListOf<String>()
    private val heard = mutableListOf<String>()
    private val navigator = PhoneNavigator(AppRegistry()).apply { addListener(RecordingListener(heard)) }

    private fun screen(name: String, onEvent: RecordingScreen.(String) -> Unit = {}) = RecordingScreen(name, log, onEvent)

    /** A screen that throws when it gets [event]. */
    private fun failingOn(name: String, event: String) = screen(name) { if (it == event) error("$name fails in $event") }

    private fun openStack(vararg stack: RecordingScreen) {
        navigator.open()
        assertTrue(navigator.openApp(testApp("app") { stack[0] }))
        stack.toList().zipWithNext { below, above -> below.handle.push(above) }
        log.clear()
        heard.clear()
    }

    private fun assertFailedOnce() = assertEquals(1, heard.count { it == "@failed app" }, "heard: $heard")

    @Test
    fun `any input or tick that throws closes the app and the screen is not called again`() {
        fun on(event: String, call: PhoneNavigator.() -> Unit) = event to call
        val calls = mapOf(
            on("pressed") { mousePressed(1f, 2f, 0) },
            on("released") { mouseReleased(1f, 2f, 0) },
            on("dragged") { mouseDragged(1f, 2f, 0, 3f, 4f) },
            on("scrolled") { mouseScrolled(1f, 2f, 0f, 1f) },
            on("key") { keyPressed(259, 0, 0) },
            on("char") { charTyped('x', 0) },
            on("tick") { tick() },
        )
        for ((event, call) in calls) {
            log.clear()
            val broken = failingOn("broken", event)
            openStack(screen("root"), broken)

            navigator.call()

            assertEquals(listOf("broken:$event", "root:closed"), log, "after $event")
            assertTrue(navigator.isHome, "after $event")
            assertTrue(navigator.isOpen, "after $event")
            assertEquals(listOf("@INSTANT from broken", "@failed app"), heard, "after $event")
            navigator.call()
            navigator.render(broken, TestCanvas, 0f, 0f, 0f)
            assertEquals(listOf("broken:$event", "root:closed"), log, "after $event")
        }
    }

    @Test
    fun `a failed input reports the input as not used`() {
        openStack(failingOn("root", "pressed"))
        assertFalse(navigator.mousePressed(0f, 0f, 0))
    }

    @Test
    fun `a screen that throws while drawn closes the app and is never drawn again`() {
        val broken = failingOn("broken", "render")
        openStack(screen("root"), broken)

        navigator.render(broken, TestCanvas, 0f, 0f, 0f)
        navigator.render(broken, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("broken:render", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertFailedOnce()
    }

    @Test
    fun `a screen that throws in onShown on a push closes the app`() {
        val root = screen("root")
        openStack(root)

        root.handle.push(failingOn("broken", "shown"))

        assertEquals(listOf("root:hidden", "broken:shown", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertNull(navigator.shownScreen)
        assertFailedOnce()
    }

    @Test
    fun `TODO and a stack overflow in app code count as app failures`() {
        openStack(screen("root") { if (it == "tick") TODO("not written yet") })
        navigator.tick()
        assertTrue(navigator.isHome)
        assertFailedOnce()

        openStack(screen("root") { if (it == "key") throw StackOverflowError() })
        navigator.keyPressed(0, 0, 0)
        assertTrue(navigator.isHome)
        assertFailedOnce()
    }

    @Test
    fun `an out-of-memory error is the game's, not the app's`() {
        openStack(screen("root") { if (it == "pressed") throw OutOfMemoryError("test") })
        assertThrows(OutOfMemoryError::class.java) { navigator.mousePressed(0f, 0f, 0) }
        assertThrows(OutOfMemoryError::class.java) {
            navigator.openApp(testApp("other") { throw OutOfMemoryError("test") })
        }
    }

    @Test
    fun `an app that fails to start leaves the open app exactly as it was`() {
        val root = screen("root")
        val top = screen("top")
        var rootCalls = 0
        val app = testApp("app") { if (rootCalls++ == 0) root else error("no second root") }
        navigator.open()
        navigator.openApp(app)
        root.handle.push(top)
        log.clear()
        heard.clear()

        // The factory throws, the target factory throws, the target gives a screen already on the phone, the target
        // repeats a screen, and the root screen reads the phone in its constructor.
        assertFalse(navigator.openApp(app))
        assertFalse(navigator.openApp(testApp("b", targets = { error("bad target") }), "x"))
        assertFalse(navigator.openApp(testApp("c", targets = { listOf(screen("fresh"), root) }), "x"))
        assertFalse(navigator.openApp(testApp("d", targets = { val s = screen("twice"); listOf(screen("fresh"), s, s) }), "x"))
        assertFalse(navigator.openApp(testApp("e") { screen("eager").also { it.handle } }))

        assertEquals(listOf("@failed app", "@failed b", "@failed c", "@failed d", "@failed e"), heard)
        assertEquals(emptyList(), log)
        assertSame(app, navigator.currentApp)
        assertEquals(listOf(root, top), navigator.screens)
        assertSame(top, navigator.shownScreen)
        top.handle.pop()
        assertSame(root, navigator.shownScreen)
    }

    @Test
    fun `a target stack whose top throws in onShown closes the app without showing the others`() {
        val s1 = screen("s1")
        val s2 = screen("s2")
        navigator.open()

        navigator.openApp(testApp("app", targets = { listOf(s1, s2, failingOn("s3", "shown")) }), "deep")

        assertEquals(listOf("s3:shown", "s2:closed", "s1:closed"), log)
        assertTrue(navigator.isHome)
        assertFailedOnce()
        s1.handle.push(screen("late"))
        assertTrue(navigator.isHome)
    }

    @Test
    fun `the outgoing screen throwing in onHidden during a push closes the app and the pushed screen`() {
        val root = failingOn("root", "hidden")
        openStack(root)
        val pushed = screen("pushed")

        root.handle.push(pushed)

        assertEquals(listOf("root:hidden", "pushed:closed"), log)
        assertTrue(navigator.isHome)
        assertFailedOnce()
        pushed.handle.push(screen("late"))
        assertTrue(navigator.isHome)
    }

    @Test
    fun `a popped screen that throws in onClosed takes only itself down`() {
        val root = screen("root")
        val a = failingOn("a", "closed")
        openStack(root, a)

        assertTrue(navigator.back())
        navigator.render(a, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("a:hidden", "a:closed", "root:shown"), log)
        assertSame(root, navigator.shownScreen)
        assertEquals(listOf("@BACK from a"), heard)
    }

    @Test
    fun `a popped screen that throws in onHidden gets no onClosed and the app carries on`() {
        val root = screen("root")
        openStack(root, failingOn("a", "hidden"))

        assertTrue(navigator.back())

        assertEquals(listOf("a:hidden", "root:shown"), log)
        assertSame(root, navigator.shownScreen)
    }

    @Test
    fun `a replaced screen that throws in onClosed leaves the screen that replaced it`() {
        val root = screen("root")
        val a = failingOn("a", "closed")
        val new = screen("new")
        openStack(root, a)

        a.handle.replace(new)

        assertEquals(listOf("a:hidden", "a:closed", "new:shown"), log)
        assertEquals(listOf(root, new), navigator.screens)
        assertSame(new, navigator.shownScreen)
        assertEquals(listOf("@FORWARD from a"), heard)
    }

    @Test
    fun `a screen throwing in onClosed while its app closes for another failure is reported once`() {
        openStack(failingOn("root", "closed"), screen("middle"), failingOn("top", "pressed"))

        navigator.mousePressed(0f, 0f, 0)

        assertEquals(listOf("top:pressed", "middle:closed", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertFailedOnce()
    }

    @Test
    fun `a screen throwing in onHidden on the way home gets no onClosed and the others still close`() {
        openStack(screen("root"), failingOn("a", "hidden"))

        navigator.home()

        assertEquals(listOf("a:hidden", "root:closed"), log)
        assertTrue(navigator.isHome)
    }

    @Test
    fun `an app throwing while it is closed for another app doesn't stop the new one`() {
        openStack(screen("root"), failingOn("a", "hidden"))
        val other = screen("other")

        assertTrue(navigator.openApp(testApp("other") { other }))

        assertEquals(listOf("a:hidden", "root:closed", "other:shown"), log)
        assertEquals("other", navigator.currentApp?.id?.path)
        assertSame(other, navigator.shownScreen)
    }

    @Test
    fun `a screen throwing in onHidden as the phone is put away closes the app`() {
        openStack(screen("root"), failingOn("a", "hidden"))

        navigator.close()

        assertEquals(listOf("a:hidden", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertFalse(navigator.isOpen)
        assertFailedOnce()
        navigator.open()
        assertTrue(navigator.isHome)
        assertEquals(listOf("a:hidden", "root:closed"), log)
    }

    @Test
    fun `a screen throwing in onShown as the phone comes out leaves the phone open on home`() {
        var armed = false
        openStack(screen("root"), screen("a") { if (it == "shown" && armed) error("boom") })
        navigator.close()
        armed = true
        log.clear()

        navigator.open()

        assertEquals(listOf("a:shown", "root:closed"), log)
        assertTrue(navigator.isOpen)
        assertTrue(navigator.isHome)
        assertFailedOnce()
    }

    @Test
    fun `a screen sliding out of an app already left can fail without touching the app now open`() {
        val old = failingOn("old", "render")
        openStack(old)
        val other = screen("other")
        navigator.openApp(testApp("other") { other })
        log.clear()

        navigator.render(old, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("old:render"), log)
        assertSame(other, navigator.shownScreen)
        assertEquals("other", navigator.currentApp?.id?.path)
    }

    @Test
    fun `a popped screen that throws while drawn sliding out is only dropped`() {
        val root = screen("root")
        val popped = failingOn("popped", "render")
        openStack(root, popped)
        navigator.back()
        log.clear()
        heard.clear()

        navigator.render(popped, TestCanvas, 0f, 0f, 0f)
        navigator.render(popped, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("popped:render"), log)
        assertSame(root, navigator.shownScreen)
        assertEquals(emptyList(), heard)
    }

    @Test
    fun `reusing a screen from outside app code throws and changes nothing`() {
        val root = screen("root")
        val a = screen("a")
        openStack(root, a)
        val popped = screen("popped")
        a.handle.push(popped)
        popped.handle.pop()
        log.clear()
        heard.clear()

        assertThrows(IllegalArgumentException::class.java) { a.handle.push(root) }
        assertThrows(IllegalArgumentException::class.java) { a.handle.push(a) }
        assertThrows(IllegalArgumentException::class.java) { a.handle.push(popped) }
        assertThrows(IllegalArgumentException::class.java) { a.handle.replace(root) }
        assertThrows(IllegalArgumentException::class.java) { a.handle.replace(popped) }

        assertEquals(listOf(root, a), navigator.screens)
        assertSame(a, navigator.shownScreen)
        assertEquals(emptyList(), log)
        assertEquals(emptyList(), heard)
    }

    @Test
    fun `replacing with a screen already in use from an input handler closes the app`() {
        val root = screen("root")
        openStack(root, screen("a") { if (it == "pressed") handle.replace(root) })

        navigator.mousePressed(0f, 0f, 0)

        assertEquals(listOf("a:pressed", "root:closed"), log)
        assertTrue(navigator.isHome)
        assertFailedOnce()
    }

    @Test
    fun `an app that failed opens again normally`() {
        var made = 0
        val app = testApp("app") { screen("root${++made}") { if (it == "pressed" && made == 1) error("boom") } }
        navigator.open()
        navigator.openApp(app)
        navigator.mousePressed(0f, 0f, 0)
        assertTrue(navigator.isHome)
        log.clear()

        assertTrue(navigator.openApp(app))

        assertEquals("root2", navigator.shownScreen.toString())
        assertTrue(navigator.mousePressed(0f, 0f, 0))
        assertEquals(listOf("root2:shown", "root2:pressed"), log)
    }
}
