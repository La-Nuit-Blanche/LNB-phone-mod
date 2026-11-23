package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Which screen render, tick and input reach, and the client-thread guard on navigation. */
class PhoneNavigatorDispatchTest {
    private val log = mutableListOf<String>()
    private var onClientThread = true
    private val navigator = PhoneNavigator(AppRegistry()) { onClientThread }

    private fun screen(name: String) = RecordingScreen(name, log)

    private fun openStack(vararg stack: RecordingScreen) {
        navigator.open()
        assertTrue(navigator.openApp(testApp("app") { stack[0] }))
        stack.toList().zipWithNext { below, above -> below.handle.push(above) }
        log.clear()
    }

    /** Every input the navigator passes on, as `name(arguments)`, answering [answer]. */
    private class ArgumentScreen(private val calls: MutableList<String>, var answer: Boolean) : AppScreen() {
        override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
            calls += "render(${canvas.width}, ${canvas.height}, $mouseX, $mouseY, $partialTick)"
        }

        override fun mousePressed(x: Float, y: Float, button: Int) = answer.also { calls += "pressed($x, $y, $button)" }
        override fun mouseReleased(x: Float, y: Float, button: Int) = answer.also { calls += "released($x, $y, $button)" }
        override fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float) =
            answer.also { calls += "dragged($x, $y, $button, $dragX, $dragY)" }

        override fun mouseScrolled(x: Float, y: Float, scrollX: Float, scrollY: Float) =
            answer.also { calls += "scrolled($x, $y, $scrollX, $scrollY)" }

        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int) =
            answer.also { calls += "key($keyCode, $scanCode, $modifiers)" }

        override fun charTyped(codePoint: Char, modifiers: Int) = answer.also { calls += "char($codePoint, $modifiers)" }
    }

    private fun sendEveryInput(): List<Boolean> = listOf(
        navigator.mousePressed(1.5f, 2.5f, 1),
        navigator.mouseReleased(3.5f, -4f, 2),
        navigator.mouseDragged(5f, 6f, 0, 0.5f, -0.5f),
        navigator.mouseScrolled(7f, 8f, -1f, 2f),
        navigator.keyPressed(257, 28, 2),
        navigator.charTyped('é', 1),
    )

    @Test
    fun `input reaches the shown screen as given and returns its answer`() {
        val calls = mutableListOf<String>()
        val screen = ArgumentScreen(calls, answer = true)
        navigator.open()
        navigator.openApp(testApp("app") { screen })

        assertEquals(List(6) { true }, sendEveryInput())
        screen.answer = false
        assertEquals(List(6) { false }, sendEveryInput())
        navigator.render(screen, TestCanvas, 10f, -20f, 0.25f)

        val once = listOf(
            "pressed(1.5, 2.5, 1)",
            "released(3.5, -4.0, 2)",
            "dragged(5.0, 6.0, 0, 0.5, -0.5)",
            "scrolled(7.0, 8.0, -1.0, 2.0)",
            "key(257, 28, 2)",
            "char(é, 1)",
        )
        assertEquals(once + once + "render(256, 370, 10.0, -20.0, 0.25)", calls)
    }

    @Test
    fun `input and ticks reach only the top screen`() {
        openStack(screen("root"), screen("middle"), screen("top"))

        sendEveryInput()
        navigator.tick()

        assertEquals(listOf("top:pressed", "top:released", "top:dragged", "top:scrolled", "top:key", "top:char", "top:tick"), log)
    }

    @Test
    fun `at home nothing gets input or ticks`() {
        val root = screen("root")
        openStack(root)
        navigator.home()
        log.clear()

        assertEquals(List(6) { false }, sendEveryInput())
        navigator.tick()

        assertEquals(emptyList(), log)
    }

    @Test
    fun `with the phone put away the open app gets no input or ticks`() {
        openStack(screen("root"))
        navigator.close()
        log.clear()

        assertEquals(List(6) { false }, sendEveryInput())
        navigator.tick()

        assertEquals(emptyList(), log)
    }

    @Test
    fun `a popped screen gets no more input or ticks but can still be drawn sliding out`() {
        val top = screen("top")
        openStack(screen("root"), top)
        navigator.back()
        log.clear()

        navigator.mousePressed(0f, 0f, 0)
        navigator.tick()
        navigator.render(top, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("root:pressed", "root:tick", "top:render"), log)
    }

    @Test
    fun `render draws whichever screen it is given, such as the one under a slide`() {
        val root = screen("root")
        openStack(root, screen("top"))

        navigator.render(root, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("root:render"), log)
    }

    @Test
    fun `screens of an app that was left can still be drawn sliding out`() {
        val old = screen("old")
        openStack(old)
        navigator.home()
        log.clear()

        navigator.render(old, TestCanvas, 0f, 0f, 0f)

        assertEquals(listOf("old:render"), log)
    }

    /** A screen whose text field focus a test sets. */
    private class TypingScreen(name: String, log: MutableList<String>) : RecordingScreen(name, log) {
        var typing = false
        override val hasTextFocus get() = typing
    }

    @Test
    fun `text focus is the shown screen's, and nobody's at home or with the phone away`() {
        val root = TypingScreen("root", log)
        openStack(root)
        assertFalse(navigator.hasTextFocus)
        root.typing = true
        assertTrue(navigator.hasTextFocus)

        root.handle.push(screen("top"))
        assertFalse(navigator.hasTextFocus)
        navigator.back()
        assertTrue(navigator.hasTextFocus)
        navigator.close()
        assertFalse(navigator.hasTextFocus)
        navigator.open()
        navigator.home()
        assertFalse(navigator.hasTextFocus)
    }

    @Test
    fun `a text focus that throws counts as an app failure`() {
        val broken = object : RecordingScreen("broken", log) {
            override val hasTextFocus: Boolean get() = error("boom")
        }
        openStack(screen("root"), broken)

        assertFalse(navigator.hasTextFocus)
        assertTrue(navigator.isHome)
    }

    @Test
    fun `every navigation from another thread throws and changes nothing`() {
        val registry = AppRegistry()
        val app = testApp("app") { screen("unused") }
        registry.register(app, "test")
        val offThread = PhoneNavigator(registry) { onClientThread }
        val root = screen("root")
        val top = screen("top")
        val popped = screen("popped")
        offThread.open()
        offThread.openApp(testApp("open") { root })
        root.handle.push(top)
        top.handle.push(popped)
        popped.handle.pop()
        log.clear()

        onClientThread = false
        fun call(name: String, block: () -> Unit) = name to block
        val calls = listOf(
            call("open") { offThread.open() },
            call("close") { offThread.close() },
            call("openApp") { offThread.openApp(app) },
            call("openApp by id") { offThread.openApp(appId("app")) },
            call("back") { offThread.back() },
            call("home") { offThread.home() },
            call("reset") { offThread.reset() },
            call("push") { top.handle.push(screen("new")) },
            call("pop") { top.handle.pop() },
            call("replace") { top.handle.replace(screen("new")) },
            call("handle home") { top.handle.home() },
            call("handle close") { top.handle.close() },
            call("closed handle") { popped.handle.push(screen("new")) },
        )
        for ((name, block) in calls) {
            assertThrows(IllegalStateException::class.java, { block() }, name)
        }
        onClientThread = true

        assertEquals(listOf(root, top), offThread.screens)
        assertSame(top, offThread.shownScreen)
        assertTrue(offThread.isOpen)
        assertEquals(emptyList(), log)
    }

    @Test
    fun `navigation that would do nothing still throws from another thread`() {
        onClientThread = false
        assertThrows(IllegalStateException::class.java) { navigator.close() }
        assertThrows(IllegalStateException::class.java) { navigator.home() }
        assertThrows(IllegalStateException::class.java) { navigator.back() }
        assertThrows(IllegalStateException::class.java) { navigator.openApp(appId("unknown")) }
        assertFalse(navigator.isOpen)
    }
}
