package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.FAMILY
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.FR
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.SKIN
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.cp
import fr.bde_teknantes.lnb.phone.client.smooth.Graphemes
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_C
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_END
import org.lwjgl.glfw.GLFW.GLFW_KEY_HOME
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
import org.lwjgl.glfw.GLFW.GLFW_KEY_UP
import org.lwjgl.glfw.GLFW.GLFW_KEY_V
import org.lwjgl.glfw.GLFW.GLFW_KEY_X

/**
 * Text fields driven through a real [KitScreen] with the real fonts (Inter and the emoji face) measuring: the keys,
 * the clicks and the typing a player does around emoji, as the game delivers them.
 */
class EmojiTextInputTest {
    private lateinit var env: FakeTextEnvironment
    private lateinit var screen: TestScreen
    private lateinit var area: TextArea

    @BeforeEach
    fun setUp() {
        env = KitFakes.install()
        // The real layout: Inter and the emoji face, measured as the game measures.
        TextMetrics.fonts = { if (it == fr.bde_teknantes.lnb.phone.client.smooth.FontWeight.SEMIBOLD) RealFaces.SEMIBOLD else RealFaces.REGULAR }
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    private fun focus(text: String): TextArea {
        area = screen.add(TextArea(TextState(text)).apply { limit = 280 }.at(0f, 0f))
        screen.frame()
        screen.click(50f, 30f)
        screen.advance(500f)
        return area
    }

    private val state get() = area.state
    private fun check() {
        val t = state.text
        assertTrue(Graphemes.isBoundary(t, state.caret) && Graphemes.isBoundary(t, state.anchor), "$state")
    }

    /** x in the area's coordinates of the text boundary [index] of the first line. */
    private fun xAt(index: Int) = 12f + TextMetrics.measure(state.text.substring(0, index), TextStyle.BODY)

    @Test
    fun `typed unit by unit, as the game delivers it, each sequence arrives whole and Backspace removes it whole`() {
        focus("")
        val sequences = listOf(
            FAMILY, SKIN, FR, cp('1'.code, 0xFE0F, 0x20E3), cp(0x2764, 0xFE0F, 0x200D, 0x1F525), cp(0x1F3F3, 0xFE0F, 0x200D, 0x1F308),
            cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F),
        )
        for (sequence in sequences) {
            screen.type("é")
            screen.type(sequence)
            screen.type("!")
            assertEquals("é$sequence!", state.text)
            assertEquals(state.text.length, state.caret)
            screen.key(GLFW_KEY_LEFT)
            screen.key(GLFW_KEY_BACKSPACE)
            assertEquals("é!", state.text, sequence)
            screen.key(GLFW_KEY_BACKSPACE)
            assertEquals("!", state.text)
            screen.key(GLFW_KEY_DELETE)
            assertEquals("", state.text)
            check()
        }
    }

    @Test
    fun `arrows, Shift and Delete step over each sequence, in a line of mixed text`() {
        focus("été $FR $SKIN!")
        screen.key(GLFW_KEY_HOME)
        assertEquals(0, state.caret)
        val stops = ArrayList<Int>()
        repeat(12) {
            screen.key(GLFW_KEY_RIGHT)
            check()
            stops += state.caret
        }
        assertEquals(state.text.length, state.caret)
        assertEquals(8, stops.distinct().size)
        // Shift+Left takes the toned thumb and the mark after it as two steps, the thumb whole.
        screen.key(GLFW_KEY_LEFT)
        screen.key(GLFW_KEY_LEFT, SHIFT)
        assertEquals(SKIN, state.selectedText)
        screen.key(GLFW_KEY_DELETE)
        assertEquals("été $FR !", state.text)
        screen.key(GLFW_KEY_HOME, SHIFT)
        screen.key(GLFW_KEY_BACKSPACE)
        assertEquals("!", state.text)
    }

    @Test
    fun `clicks along a line of mixed text always land on a boundary, and in order`() {
        val line = "été $FR $SKIN! ${FAMILY}ok"
        focus(line)
        var previous = -1
        var x = 0f
        while (x < 260f) {
            screen.click(x, 20f)
            screen.advance(500f) // a slow second click is not a double click
            check()
            assertTrue(state.caret >= previous, "x $x gave ${state.caret} after $previous")
            previous = state.caret
            x += 1.5f
        }
        assertEquals(line.length, previous)
        // A click on the middle of a flag goes before or after it, never between its letters.
        val flagStart = line.indexOf(FR)
        screen.click((xAt(flagStart) + xAt(flagStart + FR.length)) / 2f + 1f, 20f)
        assertTrue(state.caret == flagStart || state.caret == flagStart + FR.length)
    }

    @Test
    fun `a double click on an emoji between spaces selects it, and Shift+click extends over whole sequences`() {
        focus("salut $FAMILY ok")
        val start = "salut ".length
        val middle = (xAt(start) + xAt(start + FAMILY.length)) / 2f
        screen.click(middle, 20f)
        screen.click(middle, 20f)
        assertEquals(FAMILY, state.selectedText)
        screen.key(GLFW_KEY_HOME)
        env.shiftDown = true
        screen.click(middle, 20f)
        env.shiftDown = false
        screen.frame()
        assertTrue(state.selectedText == "salut " || state.selectedText == "salut $FAMILY", "\"${state.selectedText}\"")
        check()
    }

    @Test
    fun `Home, End and Shift with them land on boundaries on wrapped lines of emoji`() {
        // Emoji only: no spaces to wrap at, so the lines are cut between sequences.
        focus(FAMILY.repeat(30))
        screen.key(GLFW_KEY_HOME, CTRL)
        screen.key(GLFW_KEY_END)
        check()
        val firstLineEnd = state.caret
        assertTrue(firstLineEnd in 1 until state.text.length, "the first line is cut inside the text")
        assertEquals(0, firstLineEnd % FAMILY.length, "after a whole family")
        screen.key(GLFW_KEY_DOWN)
        check()
        screen.key(GLFW_KEY_HOME, SHIFT)
        check()
        assertEquals(0, state.selectedText.length % FAMILY.length)
        screen.key(GLFW_KEY_UP)
        check()
        screen.key(GLFW_KEY_END, CTRL or SHIFT)
        assertEquals(state.text.length, state.caret)
        check()
    }

    @Test
    fun `up and down keep the column and land on boundaries across lines with emoji of different widths`() {
        focus("une ligne avec 😀😀😀😀 et $FR$FR\n$FAMILY$FAMILY$FAMILY encore du texte\n$SKIN$SKIN ok")
        screen.key(GLFW_KEY_HOME, CTRL)
        repeat(8) { screen.key(GLFW_KEY_RIGHT) }
        val lines = TextMetrics.lines(state.text, TextStyle.BODY, 232f - 24f).size
        assertTrue(lines >= 4, "$lines lines")
        repeat(lines - 1) { screen.key(GLFW_KEY_DOWN); check() }
        repeat(lines - 1) { screen.key(GLFW_KEY_UP); check() }
        assertEquals(8, state.caret)
    }

    @Test
    fun `cut, copy and paste with the keys round-trip sequences through the clipboard`() {
        focus("a${FAMILY}b")
        screen.key(GLFW_KEY_HOME, CTRL)
        screen.key(GLFW_KEY_RIGHT)
        screen.key(GLFW_KEY_RIGHT, SHIFT)
        screen.key(GLFW_KEY_C, CTRL)
        assertEquals(FAMILY, env.clipboard)
        screen.key(GLFW_KEY_X, CTRL)
        assertEquals("ab", state.text)
        screen.key(GLFW_KEY_V, CTRL)
        screen.key(GLFW_KEY_V, CTRL)
        assertEquals("a$FAMILY${FAMILY}b", state.text)
        assertEquals(1 + 2 * FAMILY.length, state.caret)
        // A paste with a lone joiner at each end, a lone surrogate and a tone: filtered or kept as the state does.
        env.clipboard = cp(0x200D) + "x\uD83D" + cp(0x1F3FD) + cp(0x200D)
        screen.key(GLFW_KEY_V, CTRL)
        check()
    }

    @Test
    fun `a text field of one line scrolls to the caret after an emoji and clicks follow the scroll`() {
        val field = screen.add(TextField().at(0f, 60f))
        screen.frame()
        screen.click(50f, 80f)
        screen.advance(500f)
        screen.type("${FAMILY}${SKIN}${FR}".repeat(8))
        screen.frame()
        assertEquals(field.state.text.length, field.state.caret)
        screen.key(GLFW_KEY_HOME)
        screen.frame()
        screen.click(40f, 80f)
        assertTrue(Graphemes.isBoundary(field.text, field.state.caret))
        screen.key(GLFW_KEY_END)
        assertEquals(field.state.text.length, field.state.caret)
    }
}
