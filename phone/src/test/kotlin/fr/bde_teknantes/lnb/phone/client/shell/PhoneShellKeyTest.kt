package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.lwjgl.glfw.GLFW

/**
 * Which key does what (art direction 2.3 and the `AppScreen.keyPressed` / `hasTextFocus` KDoc): Esc, Backspace and
 * the phone key in every state of the phone, with and without a focused text field, kept or not by the app.
 */
internal class PhoneShellKeyTest : ShellTestBase() {
    enum class Where { HOME, ROOT, DEEP }

    enum class Outcome { CLOSES, STAYS, BACK }

    private val root = ScriptedScreen("root", log)
    private val deep = ScriptedScreen("deep", log)

    private fun keyCode(name: String) = when (name) {
        "ESC" -> ESC
        "BACKSPACE" -> BACKSPACE
        "PHONE" -> host.phoneKey
        else -> error(name)
    }

    private fun openOn(where: Where) {
        register(testApp("app") { root }, testApp("other"))
        openFully()
        if (where != Where.HOME) clickIcon(0)
        if (where == Where.DEEP) root.handle.push(deep)
        tick(3)
        log.clear()
    }

    private fun top(where: Where) = when (where) {
        Where.HOME -> null
        Where.ROOT -> root
        Where.DEEP -> deep
    }

    @ParameterizedTest(name = "{1} on {0}, text focus {2}, kept {3}: {4}")
    @CsvSource(
        "HOME, ESC, false, false, CLOSES",
        "HOME, PHONE, false, false, CLOSES",
        "HOME, BACKSPACE, false, false, STAYS",

        "ROOT, ESC, false, false, CLOSES",
        "ROOT, PHONE, false, false, CLOSES",
        "ROOT, BACKSPACE, false, false, STAYS",
        "DEEP, ESC, false, false, CLOSES",
        "DEEP, PHONE, false, false, CLOSES",
        "DEEP, BACKSPACE, false, false, BACK",

        // The app keeps the key (closes a menu on Esc...): the phone does nothing more.
        "ROOT, ESC, false, true, STAYS",
        "ROOT, PHONE, false, true, STAYS",
        "ROOT, BACKSPACE, false, true, STAYS",
        "DEEP, ESC, false, true, STAYS",
        "DEEP, PHONE, false, true, STAYS",
        "DEEP, BACKSPACE, false, true, STAYS",

        // A text field has the focus: the phone key and Backspace are its own, whatever the app answers; Esc isn't.
        "ROOT, ESC, true, false, CLOSES",
        "ROOT, PHONE, true, false, STAYS",
        "ROOT, BACKSPACE, true, false, STAYS",
        "DEEP, ESC, true, false, CLOSES",
        "DEEP, PHONE, true, false, STAYS",
        "DEEP, BACKSPACE, true, false, STAYS",
        "ROOT, ESC, true, true, STAYS",
        "ROOT, PHONE, true, true, STAYS",
        "ROOT, BACKSPACE, true, true, STAYS",
        "DEEP, ESC, true, true, STAYS",
        "DEEP, PHONE, true, true, STAYS",
        "DEEP, BACKSPACE, true, true, STAYS",
    )
    fun `the phone's keys while it stands open`(where: Where, key: String, focus: Boolean, kept: Boolean, outcome: Outcome) {
        openOn(where)
        val code = keyCode(key)
        val top = top(where)
        top?.focus = focus
        top?.keeps = if (kept) setOf(code) else emptySet()

        host.pressKey(code)

        // The app hears the key first, whatever happens next.
        if (top != null) assertEquals("$top:key $code", log.firstOrNull())
        when (outcome) {
            Outcome.CLOSES -> {
                assertEquals(State.CLOSING, shell.state)
                assertFalse(host.navigator.isOpen)
                assertFalse(phoneScreenShown)
                // The screen is kept for the next opening.
                assertSame(top, host.navigator.currentScreen)
            }
            Outcome.STAYS -> {
                assertEquals(State.OPEN, shell.state)
                assertTrue(phoneScreenShown)
                assertSame(top, host.navigator.currentScreen)
                assertNull(shell.screenSlide)
            }
            Outcome.BACK -> {
                assertEquals(State.OPEN, shell.state)
                assertSame(root, host.navigator.currentScreen)
                assertEquals(false, shell.screenSlide?.forward)
            }
        }
        // Never heard by the game as well: no pause menu, and the phone key doesn't count again for the next tick.
        tick(20)
        assertTrue(host.screensShown.none { (it as? TestScreen)?.name == FakeShellHost.PAUSE })
        assertEquals(if (outcome == Outcome.CLOSES) State.CLOSED else State.OPEN, shell.state)
    }

    @Test
    fun `the focus is read before the app sees the key`() {
        openOn(Where.DEEP)
        // The field loses the focus as it takes Backspace (its last character erased, say): no back all the same.
        deep.focus = true
        deep.onKey = { deep.focus = false }
        host.pressKey(BACKSPACE)
        assertSame(deep, host.navigator.currentScreen)
        // The next Backspace, without focus, goes back.
        deep.onKey = {}
        host.pressKey(BACKSPACE)
        assertSame(root, host.navigator.currentScreen)
    }

    @Test
    fun `a phone key bound to a letter types that letter in a focused field`() {
        host.phoneKey = GLFW.GLFW_KEY_P
        openOn(Where.ROOT)
        root.focus = true
        host.pressKey(GLFW.GLFW_KEY_P)
        assertTrue(shell.charTyped('p', 0))
        assertEquals(listOf("root:key ${GLFW.GLFW_KEY_P}", "root:char p"), log)
        assertEquals(State.OPEN, shell.state)
        // Without the focus the same key puts the phone away.
        root.focus = false
        host.pressKey(GLFW.GLFW_KEY_P)
        assertEquals(State.CLOSING, shell.state)
    }

    @Test
    fun `other keys go to the app, and back to the game's screen handling when the app doesn't use them`() {
        openOn(Where.ROOT)
        root.keeps = setOf(GLFW.GLFW_KEY_ENTER)
        assertTrue(shell.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0))
        assertFalse(shell.keyPressed(GLFW.GLFW_KEY_A, 0, 0))
        assertEquals(listOf("root:key ${GLFW.GLFW_KEY_ENTER}", "root:key ${GLFW.GLFW_KEY_A}"), log)
        // On home no app hears anything.
        clickHomeButton()
        log.clear()
        assertFalse(shell.keyPressed(GLFW.GLFW_KEY_A, 0, 0))
        assertFalse(shell.charTyped('a', 0))
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `the app learns which presses are repeats, also on a screen pushed while the key is held`() {
        val heard = ArrayList<String>()
        val deeper = object : ScriptedScreen("deeper", log) {
            override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
                heard += "deeper repeat=$isKeyRepeat"
                return true
            }
        }
        val first = object : ScriptedScreen("first", log) {
            override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
                heard += "first repeat=$isKeyRepeat"
                // Enter opens the next screen, as a row does.
                if (!isKeyRepeat) handle.push(deeper)
                return true
            }
        }
        register(testApp("app") { first })
        openFully()
        clickIcon(0)
        tick(3)
        shell.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)
        shell.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)
        shell.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)
        shell.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0)
        shell.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)
        assertEquals(listOf("first repeat=false", "deeper repeat=true", "deeper repeat=true", "deeper repeat=false"), heard)
    }

    @Test
    fun `while a field has the focus, no key goes on to the game, even one the app doesn't use`() {
        openOn(Where.ROOT)
        root.focus = true
        assertTrue(shell.keyPressed(GLFW.GLFW_KEY_A, 0, 0))
        root.focus = false
        assertFalse(shell.keyPressed(GLFW.GLFW_KEY_A, 0, 0))
    }

    @Test
    fun `opening the phone prepares the smooth layer first`() {
        openOn(Where.ROOT)
        assertTrue(host.prewarms >= 1)
    }

    @ParameterizedTest
    @ValueSource(ints = [GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_UP])
    fun `at GUI scale 1 the app hears no keys, and Esc and the phone key still put the phone away (P-15)`(key: Int) {
        openOn(Where.ROOT)
        host.guiScale = 1.0
        root.keeps = setOf(ESC, UP)
        root.focus = true
        assertFalse(shell.charTyped('a', 0))
        host.pressKey(key)
        // Put away, the screen is hidden; it never heard a key or a character.
        assertEquals(listOf("root:hidden"), log)
        assertEquals(State.CLOSING, shell.state)
    }

    // ---- While the device moves or is away, keys reach the game, not the phone's screen ----

    @Test
    fun `closed, the phone key opens, Esc is the game's, Backspace does nothing`() {
        register(testApp("app"))
        host.pressKey(BACKSPACE)
        tick()
        assertEquals(State.CLOSED, shell.state)
        host.pressKey(ESC)
        tick()
        assertEquals(State.CLOSED, shell.state)
        assertEquals(FakeShellHost.PAUSE, (host.screen as TestScreen).name)
        host.setScreen(null)
        pressPhoneKeyInGame()
        assertEquals(State.OPENING, shell.state)
    }

    @Test
    fun `rising, Backspace changes nothing and the phone still opens`() {
        openOn(Where.DEEP)
        host.pressKey(ESC)
        closeFully()
        pressPhoneKeyInGame()
        host.pressKey(BACKSPACE)
        tick(OPEN_TICKS)
        assertEquals(State.OPEN, shell.state)
        assertSame(deep, host.navigator.currentScreen)
    }

    @Test
    fun `rising, Esc opens the pause menu and the phone stands down when the slide ends`() {
        register(testApp("app"))
        pressPhoneKeyInGame()
        host.pressKey(ESC)
        assertEquals(FakeShellHost.PAUSE, (host.screen as TestScreen).name)
        assertEquals(State.OPENING, shell.state)
        tick(OPEN_TICKS)
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.navigator.isOpen)
    }

    @Test
    fun `falling, Esc opens the pause menu and the slide carries on, Backspace does nothing`() {
        register(testApp("app"))
        openFully()
        host.pressKey(ESC)
        host.pressKey(BACKSPACE)
        assertEquals(State.CLOSING, shell.state)
        assertNull(host.screen)
        host.pressKey(ESC)
        assertEquals(FakeShellHost.PAUSE, (host.screen as TestScreen).name)
        assertEquals(State.CLOSING, shell.state)
        closeFully()
    }

    @Test
    fun `keys sent to the shell while it isn't standing open are left to the game`() {
        openOn(Where.ROOT)
        host.pressKey(ESC)
        log.clear()
        assertFalse(shell.keyPressed(GLFW.GLFW_KEY_A, 0, 0))
        assertFalse(shell.keyPressed(ESC, 0, 0))
        assertEquals(emptyList<String>(), log)
        assertEquals(State.CLOSING, shell.state)
    }
}
