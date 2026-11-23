package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.api.client.FakeCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.FAMILY
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.FR
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.KEYCAP
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.SKIN
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.THUMB
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.TONE
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.VS16
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.ZWJ
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStateGraphemeTest.Companion.cp
import fr.bde_teknantes.lnb.phone.text.Graphemes
import fr.bde_teknantes.lnb.phone.text.GraphemesTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The length limits against graphemes: what [TextLimits] counts (code points after NFC, as documented), what a state
 * with a maximum length takes when the room is smaller than the next grapheme, and what a text area marks and counts
 * around a sequence that straddles its limit.
 */
class EmojiLimitTest {
    private lateinit var screen: TestScreen

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    // ---- TextLimits, exactly as documented ----

    @Test
    fun `an emoji counts one, a flag two, a family with two joiners five, a keycap three`() {
        assertEquals(1, TextLimits.count(cp(0x1F600)))
        assertEquals(2, TextLimits.count(FR))
        assertEquals(5, TextLimits.count(FAMILY))
        assertEquals(3, TextLimits.count(cp('1'.code, VS16, KEYCAP)))
        assertEquals(2, TextLimits.count(SKIN))
        assertEquals(7, TextLimits.count(cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F)))
        assertEquals(10, TextLimits.count(cp(0x1F9D1, 0x1F3FB, ZWJ, 0x2764, VS16, ZWJ, 0x1F48B, ZWJ, 0x1F9D1, 0x1F3FC)))
    }

    @Test
    fun `accents count after composition, and what does not compose counts each mark`() {
        assertEquals(1, TextLimits.count("é"))
        assertEquals(1, TextLimits.count("é"))
        assertEquals(2, TextLimits.count(cp('x'.code, 0x301)))
        // Hangul jamo compose too.
        assertEquals(1, TextLimits.count("한"))
    }

    @Test
    fun `a lone surrogate counts one, spaces and line feeds count, and the limit is inclusive`() {
        assertEquals(1, TextLimits.count("\uD83D"))
        assertEquals(3, TextLimits.count(" \n "))
        assertTrue(TextLimits.fits("😀".repeat(280), 280))
        assertFalse(TextLimits.fits("😀".repeat(281), 280))
        assertTrue(TextLimits.canSend("a", 280))
        assertFalse(TextLimits.canSend(" \n ", 280))
    }

    @Test
    fun `the client's count and the server's agree on whatever the state holds, including after editing`() {
        val random = Random(77)
        val fragments = GraphemesTest.PIECES + listOf(SKIN, FAMILY, "é")
        repeat(300) {
            val s = TextState()
            repeat(30) {
                when (random.nextInt(5)) {
                    0 -> s.deleteBackward()
                    1 -> s.deleteForward()
                    2 -> s.moveTo(random.nextInt(s.text.length + 1))
                    else -> s.insert(fragments[random.nextInt(fragments.size)])
                }
                assertEquals(TextLimits.count(s.text), s.length)
                // The counter's overflow mark agrees with the count.
                val limit = random.nextInt(1, 20)
                val mark = CharacterCounter.overflowStart(s.text, limit)
                if (s.length <= limit) assertNull(mark) else assertEquals(limit, s.text.codePointCount(0, mark!!))
            }
        }
    }

    // ---- A state with a maximum length, when the next grapheme is longer than the room ----

    @Test
    fun `pasting a family with room for two characters takes the whole family or nothing, never part of it`() {
        // The cut used to fall between code points, which left a man and a dangling joiner at the end of the text.
        val s = TextState("", false, 5)
        s.insert("abc")
        s.insert(FAMILY)
        assertEquals("abc", s.text)
        val t = TextState("", false, 7)
        t.insert("ab")
        t.insert(FAMILY)
        assertEquals("ab$FAMILY", t.text)
        assertEquals(7, t.length)
    }

    @Test
    fun `a paste cut at the limit ends on a grapheme, for every kind of sequence`() {
        val pieces = listOf(SKIN, FAMILY, FR, cp('1'.code, VS16, KEYCAP), cp(0x2764, VS16, ZWJ, 0x1F525), cp('x'.code, 0x301, 0x302))
        for (piece in pieces) {
            for (room in 0..piece.codePointCount(0, piece.length) + 1) {
                val s = TextState("", true, 3 + room)
                s.insert("abc")
                s.insert("$piece!")
                val taken = s.text.substring(3)
                assertTrue(Graphemes.isBoundary(s.text, s.text.length), "room $room")
                // What went in is whole graphemes of the pasted text.
                assertTrue("$piece!".startsWith(taken), "room $room: $taken")
                assertTrue(Graphemes.isBoundary("$piece!", taken.length), "room $room of ${piece.length}")
                assertTrue(s.length <= s.maxLength)
            }
        }
    }

    @Test
    fun `typing a family one code point at a time at the limit leaves what fits, and reports no error`() {
        // An emoji panel sends the sequence one code point at a time; the state can't know a family is coming. What
        // fits is kept (a man and a joiner at room 2): undecided, documented in the report.
        val s = TextState("", false, 5)
        s.insert("abc")
        for (c in FAMILY.codePoints().toArray()) s.insert(cp(c))
        assertEquals(5, s.length)
        assertEquals("abc" + cp(0x1F468, ZWJ), s.text)
        assertTrue(Graphemes.isBoundary(s.text, s.caret))
    }

    @Test
    fun `replacing a selection counts what the selection frees, with whole sequences`() {
        val s = TextState("", false, 6)
        s.insert("ab$FAMILY")
        assertEquals("ab", s.text) // 2 + 5 > 6 and the family is not cut
        s.selectAll()
        s.insert(FAMILY + "x")
        assertEquals(FAMILY + "x", s.text)
        assertEquals(6, s.length)
    }

    // ---- The text area: marking and counting around a straddling sequence ----

    private class RectCanvas : FakeCanvas() {
        class Rect(val x: Float, val w: Float, val color: Int)

        val rects = ArrayList<Rect>()

        override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
            rects += Rect(x, w, color)
            super.fillRect(x, y, w, h, color)
        }
    }

    private fun highlight(text: String, limit: Int): Float? {
        val screen = TestScreen()
        screen.add(TextArea(TextState(text)).apply { this.limit = limit }.at(0f, 0f))
        val canvas = RectCanvas()
        screen.frame()
        screen.render(canvas, 0f, 0f, 0f)
        return canvas.rects.firstOrNull { it.color == Colors.DANGER_SOFT }?.x
    }

    @Test
    fun `a family straddling the limit is marked whole from its first character`() {
        // Limit 5: "abc" and the man with his joiner are within it, the rest past it; the mark starts at the family.
        val text = "abc$FAMILY"
        assertEquals(8, TextLimits.count(text))
        assertEquals("-3", CharacterCounter.label(TextLimits.count(text), 5))
        assertEquals(12f + 3 * 6.5f, highlight(text, 5))
    }

    @Test
    fun `a sequence that ends exactly at the limit is not marked, one that starts there is marked from its start`() {
        assertNull(highlight("abc" + SKIN, 5))
        assertEquals(12f + 5 * 6.5f, highlight("abcde" + SKIN, 5))
        // A flag's two letters straddle: marked from the flag's start, the count says 1 over.
        assertEquals(12f + 4 * 6.5f, highlight("abcd" + FR, 5))
        assertEquals("-1", CharacterCounter.label(TextLimits.count("abcd$FR"), 5))
    }

    @Test
    fun `a keycap, a skin tone and a flag straddling the limit by one are all marked from their start`() {
        val keycap = cp('1'.code, VS16, KEYCAP)
        for (piece in listOf(keycap, SKIN, FR, cp(THUMB, TONE, ZWJ, 0x1F4BB))) {
            for (before in 3..5) {
                val text = "a".repeat(before) + piece
                if (TextLimits.count(text) <= 5) continue
                val mark = highlight(text, 5)!!
                // The mark starts at a grapheme start at or before the 6th code point, never inside a sequence.
                val startIndexInCodePoints = Math.round((mark - 12f) / 6.5f)
                assertTrue(startIndexInCodePoints <= 5, "$before + ${piece.length}")
                assertTrue(startIndexInCodePoints >= before, "$before + ${piece.length}")
            }
        }
    }

    @Test
    fun `sending is refused past the limit whatever the sequence, and allowed exactly at it`() {
        assertFalse(CharacterCounter.canSend("a".repeat(278) + FR + "b", 280)) // 281
        assertTrue(CharacterCounter.canSend("a".repeat(278) + FR, 280)) // 280
        assertFalse(CharacterCounter.canSend("a".repeat(276) + FAMILY, 280)) // 281
        assertTrue(CharacterCounter.canSend("a".repeat(275) + FAMILY, 280))
    }
}
