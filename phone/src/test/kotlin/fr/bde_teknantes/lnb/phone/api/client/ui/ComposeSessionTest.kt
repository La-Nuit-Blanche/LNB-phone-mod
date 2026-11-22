package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_END
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_KEY_V

/**
 * A player writing a post, played against a real [KitScreen] laid out as Compose (art direction 9.8): the text area at
 * (12, 96), 232 x 196, with its counter, and Publier (88 dp) right-aligned to x 244 in the header.
 */
class ComposeSessionTest {
    private lateinit var env: FakeTextEnvironment

    private class Compose(draft: TextState) : TestScreen() {
        val posted = ArrayList<String>()
        val area = add(TextArea(draft, placeholder = "Quoi de neuf sur le serveur ?", limit = 280).at(12f, 96f, 232f, 196f))
        val publish = add(Button("Publier", fixedWidth = 88f) { posted += area.text }.atRight(244f, 6f))

        init {
            publish.enabled = CharacterCounter.canSend(draft.text, 280)
            area.onChange = { publish.enabled = CharacterCounter.canSend(it.text, 280) }
            area.onSubmit = { publish.click() }
        }

        /** The counter as drawn in the last frame: its text and colour (the fake canvas records widget-local positions). */
        fun counter(): Pair<String, Int> {
            val drawn = canvas.texts.single { it.style == TextStyle.CAPTION && it.y == 196f - 22f }
            return drawn.text to drawn.color
        }

        /** The point over character boundary [col] of visible line [line] of the text area. */
        fun point(line: Int, col: Float) = (12f + 12f + col * 6.5f) to (96f + 12f + line * 18f + 9f)
    }

    @BeforeEach
    fun setUp() {
        env = KitFakes.install()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `write, fix a word, overflow, trim back, tab to Publier and press Enter`() {
        val s = Compose(TextState())
        s.frame()
        assertFalse(s.publish.enabled)
        assertTrue(s.canvas.drew("Quoi de neuf sur le serveur ?"))
        assertEquals("0/280" to Colors.TEXT_2, s.counter())

        // Focus the text area with a click, as F-11 step 3 shows it focused.
        val (x0, y0) = s.point(0, 0f)
        s.click(x0, y0)
        assertSame(s.area, s.focused)
        assertTrue(s.hasTextFocus)

        // Type a sentence; the ê comes from a dead key: the key itself types nothing, the composed letter follows.
        s.type("Rendez-vous à l'ar")
        assertFalse(s.key(GLFW_KEY_LEFT_BRACKET))
        assertEquals("Rendez-vous à l'ar", s.area.text)
        s.type("ène demain")
        assertEquals("Rendez-vous à l'arène demain", s.area.text)
        assertTrue(s.publish.enabled)
        s.frame()
        assertEquals("28/280" to Colors.TEXT_2, s.counter())

        // Double-click "arène" (column 18 is inside it): the whole word, its article and apostrophe included.
        val (wx, wy) = s.point(0, 18f)
        s.advance(500f)
        s.click(wx, wy)
        s.click(wx, wy)
        assertEquals("l'arène", s.area.state.selectedText)

        // Paste over it.
        env.clipboard = "la place du spawn"
        s.key(GLFW_KEY_V, CTRL)
        assertEquals("Rendez-vous à la place du spawn demain", s.area.text)

        // Paste a long text at the end: over the limit, the counter turns red and Publier goes off.
        s.key(GLFW_KEY_END, CTRL)
        env.clipboard = " " + "Inscriptions au panneau du spawn, venez nombreux ! ".repeat(5)
        s.key(GLFW_KEY_V, CTRL)
        val length = s.area.state.length
        assertTrue(length > 280)
        assertFalse(s.publish.enabled)
        s.frame()
        assertEquals("-${length - 280}" to Colors.DANGER, s.counter())
        assertTrue(s.canvas.fills.contains(Colors.DANGER_SOFT), "the characters past 280 are marked")

        // Trim back with Backspace until 270 characters: amber, and Publier comes back.
        repeat(length - 270) { s.key(GLFW_KEY_BACKSPACE) }
        assertEquals(270, s.area.state.length)
        assertTrue(s.publish.enabled)
        s.frame()
        assertEquals("270/280" to Colors.WARNING, s.counter())
        assertFalse(s.canvas.fills.contains(Colors.DANGER_SOFT))

        // Tab to Publier: the field lets go of the keyboard, the button shows its ring, Enter publishes.
        s.key(GLFW_KEY_TAB)
        assertSame(s.publish, s.focused)
        assertFalse(s.hasTextFocus)
        assertTrue(s.publish.showsFocusRing)
        s.key(GLFW_KEY_ENTER)
        assertEquals(1, s.posted.size)
        assertEquals(270, CharacterCounter.count(s.posted.single()))
        assertTrue(s.posted.single().startsWith("Rendez-vous à la place du spawn demain Inscriptions"))
    }

    @Test
    fun `ctrl+enter publishes from the text area, and the draft outlives the screen`() {
        val draft = TextState()
        val first = Compose(draft)
        first.frame()
        first.focus(first.area)
        first.type("J'arrive dans 5 min")
        // The player closes Compose: a new screen is built later with the same draft.
        val second = Compose(draft)
        second.frame()
        assertEquals("J'arrive dans 5 min", second.area.text)
        assertTrue(second.publish.enabled)
        assertEquals("19/280" to Colors.TEXT_2, second.counter())
        second.focus(second.area)
        second.key(GLFW_KEY_LEFT)
        second.type("!")
        assertEquals("J'arrive dans 5 mi!n", draft.text)
        second.key(GLFW_KEY_ENTER, CTRL)
        assertEquals(listOf("J'arrive dans 5 mi!n"), second.posted)
    }

    @Test
    fun `a text of spaces can't be published, by button or by ctrl+enter`() {
        val s = Compose(TextState())
        s.frame()
        s.focus(s.area)
        s.type("   ")
        s.key(GLFW_KEY_ENTER)
        assertFalse(s.publish.enabled)
        s.key(GLFW_KEY_ENTER, CTRL)
        val (bx, by) = 244f - 44f to 22f
        s.click(bx, by)
        assertTrue(s.posted.isEmpty())
    }

    @Test
    fun `at exactly 280 characters Publier is on and nothing is marked, at 281 it is off`() {
        val s = Compose(TextState("a".repeat(279)))
        s.frame()
        s.focus(s.area)
        s.type("😀")
        assertEquals(280, s.area.state.length)
        assertTrue(s.publish.enabled)
        s.frame()
        assertEquals("280/280" to Colors.WARNING, s.counter())
        assertFalse(s.canvas.fills.contains(Colors.DANGER_SOFT))
        s.type("b")
        assertFalse(s.publish.enabled)
        s.frame()
        assertEquals("-1" to Colors.DANGER, s.counter())
        assertTrue(s.canvas.fills.contains(Colors.DANGER_SOFT))
    }
}
