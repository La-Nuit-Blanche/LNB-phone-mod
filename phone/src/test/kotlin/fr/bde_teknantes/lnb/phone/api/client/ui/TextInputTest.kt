package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_C
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_END
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_HOME
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_UP
import org.lwjgl.glfw.GLFW.GLFW_KEY_V
import org.lwjgl.glfw.GLFW.GLFW_KEY_X

/**
 * Text fields driven through a real [KitScreen]: clicks, drags, keys and typing, with the clipboard faked. Text is
 * measured at 6.5 dp per character, so a 232 dp text area holds 32 characters per line and shows 4 lines over its
 * counter.
 */
class TextInputTest {
    private lateinit var env: FakeTextEnvironment
    private lateinit var screen: TestScreen

    @BeforeEach
    fun setUp() {
        env = KitFakes.install()
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    /** The point of a text area at (0, 0) over the character boundary [col] of visible line [line]. */
    private fun point(line: Int, col: Float) = (12f + col * 6.5f) to (12f + line * 18f + 9f)

    private fun TestScreen.clickAt(line: Int, col: Float) {
        val (x, y) = point(line, col)
        click(x, y)
    }

    @Nested
    inner class TextAreaCaret {
        // Three words of ten fill the first line exactly (32 characters); the fourth wraps.
        private val text = "abcdefghij klmnopqrst uvwxyzabcd efghijklmn"
        private lateinit var area: TextArea

        @BeforeEach
        fun place() {
            area = screen.add(TextArea(TextState(text), limit = 280).at(0f, 0f))
            screen.frame()
        }

        @Test
        fun `a click focuses the area and puts the caret where it lands`() {
            screen.clickAt(1, 7f)
            assertSame(area, screen.focused)
            assertTrue(screen.hasTextFocus)
            assertEquals(40, area.state.caret)
        }

        @Test
        fun `up and down keep the column between wrapped lines and stop at the text's ends`() {
            screen.clickAt(1, 7f)
            screen.key(GLFW_KEY_UP)
            assertEquals(7, area.state.caret)
            screen.key(GLFW_KEY_DOWN)
            assertEquals(40, area.state.caret)
            screen.key(GLFW_KEY_DOWN)
            assertEquals(text.length, area.state.caret)
            screen.clickAt(0, 5f)
            screen.key(GLFW_KEY_UP)
            assertEquals(0, area.state.caret)
        }

        @Test
        fun `shift with up and down selects whole lines' worth`() {
            screen.clickAt(0, 3f)
            screen.key(GLFW_KEY_DOWN, SHIFT)
            assertEquals(3, area.state.anchor)
            assertEquals(36, area.state.caret)
            screen.key(GLFW_KEY_UP, SHIFT)
            assertFalse(area.state.hasSelection)
        }

        @Test
        fun `home and end go to the ends of the wrapped line, with ctrl to the text's`() {
            screen.clickAt(1, 7f)
            screen.key(GLFW_KEY_HOME)
            assertEquals(33, area.state.caret)
            screen.key(GLFW_KEY_END)
            assertEquals(43, area.state.caret)
            screen.clickAt(0, 10f)
            screen.key(GLFW_KEY_END)
            // Before the space the line broke at: the caret stays on the first line.
            assertEquals(32, area.state.caret)
            screen.key(GLFW_KEY_HOME)
            assertEquals(0, area.state.caret)
            screen.key(GLFW_KEY_END, CTRL)
            assertEquals(43, area.state.caret)
            screen.key(GLFW_KEY_HOME, CTRL or SHIFT)
            assertEquals(0, area.state.caret)
            assertEquals(text, area.state.selectedText)
        }

        @Test
        fun `a click past the end of a line lands at its end, below the last line on the last line`() {
            screen.clickAt(0, 31.9f)
            assertEquals(32, area.state.caret)
            // Past the double-click delay, so the second click on the same spot is a click of its own.
            screen.advance(500f)
            screen.click(225f, 12f + 9f)
            assertEquals(32, area.state.caret)
            screen.click(225f, 12f + 18f + 9f)
            assertEquals(43, area.state.caret)
            // Under the last line (in the counter's footer): the last line, at the column clicked.
            screen.click(12f + 4 * 6.5f, 90f)
            assertEquals(37, area.state.caret)
        }

        @Test
        fun `a click above the first line lands on the first line`() {
            screen.click(12f + 2 * 6.5f, 3f)
            assertEquals(2, area.state.caret)
        }

        @Test
        fun `dragging selects from the press to the pointer, in both directions`() {
            val (x0, y0) = point(0, 2f)
            val (x1, y1) = point(0, 8f)
            screen.press(x0, y0)
            screen.drag(x1, y1)
            assertEquals("cdefgh", area.state.selectedText)
            val (x2, y2) = point(1, 1f)
            screen.drag(x2, y2)
            assertEquals(2, area.state.anchor)
            assertEquals(34, area.state.caret)
            screen.drag(12f, y0)
            assertEquals("ab", area.state.selectedText)
            screen.release()
            // The release ends the drag: moving afterwards changes nothing.
            screen.mouseDragged(x1, y1, 0, 0f, 0f)
            assertEquals("ab", area.state.selectedText)
        }

        @Test
        fun `shift+click extends the selection from the caret`() {
            screen.clickAt(0, 2f)
            env.shiftDown = true
            screen.clickAt(0, 6f)
            assertEquals("cdef", area.state.selectedText)
        }

        @Test
        fun `a double click selects the word, a slow second click doesn't`() {
            screen.clickAt(0, 14.2f)
            screen.clickAt(0, 14.2f)
            assertEquals("klmnopqrst", area.state.selectedText)
            screen.advance(500f)
            screen.clickAt(0, 14.2f)
            screen.advance(450f)
            screen.clickAt(0, 14.2f)
            assertFalse(area.state.hasSelection)
        }

        @Test
        fun `a double click on the right half of a word's last letter selects that word`() {
            // Column 9.8 is on the right half of "j", the last letter of the first word: the nearest boundary is
            // the one before the space.
            screen.clickAt(0, 9.8f)
            screen.clickAt(0, 9.8f)
            assertEquals("abcdefghij", area.state.selectedText)
        }

        @Test
        fun `two quick clicks at different places are two clicks`() {
            screen.clickAt(0, 2f)
            screen.clickAt(0, 14f)
            assertFalse(area.state.hasSelection)
            assertEquals(14, area.state.caret)
        }
    }

    @Nested
    inner class TextAreaLines {
        @Test
        fun `a click in the empty line after a final line feed puts the caret there`() {
            val area = screen.add(TextArea(TextState("abc\n"), limit = 280).at(0f, 0f))
            screen.frame()
            screen.clickAt(1, 5f)
            assertEquals(4, area.state.caret)
            screen.clickAt(0, 5f)
            assertEquals(3, area.state.caret)
            screen.key(GLFW_KEY_DOWN)
            assertEquals(4, area.state.caret)
        }

        @Test
        fun `up and down don't remember the column across a short line`() {
            // Undecided by the art direction; editors usually keep the column. Here the short line takes it.
            val area = screen.add(TextArea(TextState("a".repeat(20) + "\nbb\n" + "c".repeat(20)), limit = 280).at(0f, 0f))
            screen.frame()
            screen.clickAt(0, 15f)
            screen.key(GLFW_KEY_DOWN)
            assertEquals(23, area.state.caret)
            screen.key(GLFW_KEY_DOWN)
            assertEquals(26, area.state.caret)
        }

        @Test
        fun `end on a line cut inside a long word draws the caret at the next line's start`() {
            // Reported: a caret at a mid-word cut belongs to the line below, so End on the first line jumps there.
            val area = screen.add(TextArea(TextState("x".repeat(40)), limit = 280).at(0f, 0f))
            screen.frame()
            screen.clickAt(0, 3f)
            screen.key(GLFW_KEY_END)
            assertEquals(32, area.state.caret)
            screen.key(GLFW_KEY_END)
            assertEquals(40, area.state.caret)
        }

        @Test
        fun `a long text scrolls to the caret, and the wheel scrolls it two lines a notch`() {
            val text = (0..9).joinToString("\n") { "l$it" }
            val area = screen.add(TextArea(TextState(text), limit = 280).at(0f, 0f))
            screen.frame()
            // The caret is at the end: line 9 shows last, so line 6 is at the top of the 4 visible.
            screen.clickAt(0, 0f)
            assertEquals(18, area.state.caret)
            assertTrue(screen.wheel(50f, 50f, 1f))
            screen.clickAt(0, 0f)
            assertEquals(12, area.state.caret)
            // At the top the wheel stays with the area.
            assertTrue(screen.wheel(50f, 50f, 10f))
            screen.clickAt(0, 0f)
            assertEquals(0, area.state.caret)
        }

        @Test
        fun `a short text leaves the wheel to what is under it`() {
            screen.add(TextArea(TextState("court"), limit = 280).at(0f, 0f))
            screen.frame()
            assertFalse(screen.wheel(50f, 50f, -1f))
        }

        @Test
        fun `moving the caret with the keys scrolls it into view`() {
            val text = (0..9).joinToString("\n") { "l$it" }
            val area = screen.add(TextArea(TextState(text), limit = 280).at(0f, 0f))
            screen.frame()
            screen.focus(area)
            screen.key(GLFW_KEY_HOME, CTRL)
            screen.frame()
            screen.clickAt(0, 0f)
            assertEquals(0, area.state.caret)
        }
    }

    @Nested
    inner class Editing {
        private lateinit var area: TextArea
        private val changes = ArrayList<String>()
        private var submits = 0

        @BeforeEach
        fun place() {
            area = screen.add(TextArea(limit = 280).at(0f, 0f))
            area.onChange = { changes += it.text }
            area.onSubmit = { submits++ }
            screen.frame()
            screen.click(50f, 30f)
        }

        @Test
        fun `typed characters go in, each change heard once`() {
            screen.type("Salut")
            assertEquals("Salut", area.text)
            assertEquals(listOf("S", "Sa", "Sal", "Salu", "Salut"), changes)
        }

        @Test
        fun `accents arrive composed and an emoji arrives whole from its two halves`() {
            screen.type("é")
            screen.type("à")
            screen.type("😀")
            assertEquals("éà😀", area.text)
            assertEquals(3, area.state.length)
            // Three changes, not four: the emoji's first half alone changes nothing.
            assertEquals(3, changes.size)
        }

        @Test
        fun `a lone low half is ignored, a high half followed by a letter is dropped`() {
            screen.charTyped('\uDE00', 0)
            assertEquals("", area.text)
            screen.charTyped('\uD83D', 0)
            screen.charTyped('a', 0)
            assertEquals("a", area.text)
        }

        @Test
        fun `enter starts a line, ctrl+enter submits without one`() {
            screen.type("un")
            screen.key(GLFW_KEY_ENTER)
            screen.type("deux")
            assertEquals("un\ndeux", area.text)
            assertEquals(0, submits)
            screen.key(GLFW_KEY_ENTER, CTRL)
            assertEquals(1, submits)
            assertEquals("un\ndeux", area.text)
        }

        @Test
        fun `copy, cut and paste use the clipboard`() {
            screen.type("Rendez-vous demain")
            screen.key(GLFW_KEY_LEFT, CTRL or SHIFT)
            screen.key(GLFW_KEY_C, CTRL)
            assertEquals("demain", env.clipboard)
            assertEquals("Rendez-vous demain", area.text)
            screen.key(GLFW_KEY_X, CTRL)
            assertEquals("Rendez-vous ", area.text)
            env.clipboard = "ce soir"
            screen.key(GLFW_KEY_V, CTRL)
            assertEquals("Rendez-vous ce soir", area.text)
            assertEquals(19, area.state.caret)
        }

        @Test
        fun `copy and cut with nothing selected leave the clipboard alone`() {
            env.clipboard = "avant"
            screen.type("abc")
            screen.key(GLFW_KEY_C, CTRL)
            screen.key(GLFW_KEY_X, CTRL)
            assertEquals("avant", env.clipboard)
            assertEquals("abc", area.text)
        }

        @Test
        fun `a paste with Windows line ends keeps one line feed each`() {
            env.clipboard = "un\r\ndeux\r\n"
            screen.key(GLFW_KEY_V, CTRL)
            assertEquals("un\ndeux\n", area.text)
        }

        @Test
        fun `select all then typing replaces everything`() {
            screen.type("brouillon")
            screen.key(GLFW_KEY_A, CTRL)
            screen.type("N")
            assertEquals("N", area.text)
        }

        @Test
        fun `ctrl+backspace deletes a word`() {
            screen.type("J'arrive dans 5 min")
            screen.key(GLFW_KEY_BACKSPACE, CTRL)
            assertEquals("J'arrive dans 5 ", area.text)
        }

        @Test
        fun `while sending, nothing can be typed and the screen gets the keys`() {
            screen.type("Envoi")
            area.sending = true
            screen.type("x")
            assertEquals("Envoi", area.text)
            // The field keeps the focus but no longer takes text, so the phone may act on Backspace (it goes back).
            assertSame(area, screen.focused)
            assertFalse(screen.hasTextFocus)
            val before = screen.keysToScreen
            screen.key(GLFW_KEY_BACKSPACE)
            assertEquals(before + 1, screen.keysToScreen)
            assertEquals("Envoi", area.text)
            area.sending = false
            assertTrue(screen.hasTextFocus)
            screen.type("!")
            assertEquals("Envoi!", area.text)
        }

        @Test
        fun `a click elsewhere takes the focus away, and typing then goes nowhere`() {
            screen.click(128f, 300f)
            assertNull(screen.focused)
            assertFalse(screen.hasTextFocus)
            screen.type("x")
            assertEquals("", area.text)
        }
    }

    @Nested
    inner class SingleLine {
        private lateinit var field: TextField
        private var submits = 0

        @BeforeEach
        fun place() {
            field = screen.add(TextField().at(0f, 0f))
            field.onSubmit = { submits++ }
            screen.frame()
            screen.click(50f, 20f)
        }

        @Test
        fun `enter submits and adds nothing`() {
            screen.type("Camille")
            screen.key(GLFW_KEY_ENTER)
            assertEquals(1, submits)
            assertEquals("Camille", field.text)
        }

        @Test
        fun `up, down, home and end go to the ends`() {
            screen.type("abcdef")
            screen.key(GLFW_KEY_UP)
            assertEquals(0, field.state.caret)
            screen.key(GLFW_KEY_DOWN)
            assertEquals(6, field.state.caret)
            screen.key(GLFW_KEY_HOME)
            assertEquals(0, field.state.caret)
            screen.key(GLFW_KEY_END, SHIFT)
            assertEquals("abcdef", field.state.selectedText)
        }

        @Test
        fun `a pasted paragraph becomes one line`() {
            env.clipboard = "Première ligne\r\nDeuxième\nTroisième"
            screen.key(GLFW_KEY_V, CTRL)
            assertEquals("Première ligne Deuxième Troisième", field.text)
        }

        @Test
        fun `a text longer than the field scrolls, and clicks follow the scroll`() {
            screen.type("a".repeat(50))
            screen.frame()
            // The caret at 50 (325 dp) is kept in the 206 dp room: the text is scrolled by 119 dp.
            screen.click(12f, 20f)
            assertEquals(18, field.state.caret)
            screen.key(GLFW_KEY_HOME)
            screen.frame()
            screen.click(12f + 6.5f * 3, 20f)
            assertEquals(3, field.state.caret)
        }

        @Test
        fun `a field with a maximum length takes no more`() {
            val short = screen.add(TextField(TextState(multiline = false, maxLength = 4)).at(0f, 60f))
            screen.frame()
            screen.click(50f, 80f)
            screen.type("123456")
            assertEquals("1234", short.text)
        }
    }

    @Test
    fun `a disabled field takes neither the focus nor text, and loses the focus it had`() {
        val field = screen.add(TextField().at(0f, 0f))
        screen.frame()
        screen.click(50f, 20f)
        assertTrue(screen.hasTextFocus)
        field.enabled = false
        assertNull(screen.focused)
        assertFalse(screen.hasTextFocus)
        screen.click(50f, 20f)
        assertNull(screen.focused)
        screen.type("x")
        assertEquals("", field.text)
    }
}
