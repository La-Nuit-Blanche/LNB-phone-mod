package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout.Line
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The mapping between caret positions and wrapped lines: [TextLayout.lines], [TextLayout.lineOf], [TextLayout.indexAt]. */
class TextLayoutCaretTest {
    /** Every code point 5 dp at size 10, no kerning: a 30 dp line holds six. */
    private val mono = object : FontMetrics {
        override fun advance(codePoint: Int) = 0.5f
        override fun kerning(left: Int, right: Int) = 0f
        override val ascender = 0.8f
        override val descender = 0.2f
    }

    private fun lines(text: String, width: Float = 30f) = TextLayout.lines(text, mono, 10f, width)
    private fun indexAt(text: String, line: Line, x: Float) = TextLayout.indexAt(text, line, x, mono, 10f)

    @Test
    fun `an empty text is one empty line`() {
        assertEquals(listOf(Line(0, 0, 0, 0f)), lines(""))
        assertEquals(0, TextLayout.lineOf(lines(""), 0))
    }

    @Test
    fun `a final line feed opens an empty last line the caret can reach`() {
        val text = "abc\n"
        val l = lines(text)
        assertEquals(listOf(Line(0, 3, 4, 15f), Line(4, 4, 4, 0f)), l)
        assertEquals(0, TextLayout.lineOf(l, 3))
        assertEquals(1, TextLayout.lineOf(l, 4))
        assertEquals(4, indexAt(text, l[1], 50f))
    }

    @Test
    fun `line feeds alone make empty lines, each reachable`() {
        val text = "\n\n"
        val l = lines(text)
        assertEquals(listOf(Line(0, 0, 1, 0f), Line(1, 1, 2, 0f), Line(2, 2, 2, 0f)), l)
        assertEquals(listOf(0, 1, 2), (0..2).map { TextLayout.lineOf(l, it) })
    }

    @Test
    fun `the caret before a break space is on the first line, after it on the second`() {
        val text = "aa bb cccc"
        val l = lines(text)
        assertEquals(Line(0, 5, 6, 25f), l[0])
        assertEquals(0, TextLayout.lineOf(l, 5))
        assertEquals(1, TextLayout.lineOf(l, 6))
    }

    @Test
    fun `the end of a word cut in two is the start of the next line`() {
        // A caret at the cut can't be told from one at the start of the next line: End on the first line lands
        // there and is drawn at the start of the second. Reported; no affinity is kept.
        val text = "abcdefghij"
        val l = lines(text)
        assertEquals(listOf(Line(0, 6, 6, 30f), Line(6, 10, 10, 20f)), l)
        assertEquals(1, TextLayout.lineOf(l, l[0].end))
    }

    @Test
    fun `a click past the end of a line lands at its end, before its start at its start`() {
        val text = "aa bb cccc"
        val l = lines(text)
        assertEquals(5, indexAt(text, l[0], 29f))
        assertEquals(5, indexAt(text, l[0], 1000f))
        assertEquals(10, indexAt(text, l[1], 1000f))
        assertEquals(6, indexAt(text, l[1], -40f))
    }

    @Test
    fun `a click goes to the nearest boundary, the earlier one on a tie`() {
        val text = "abcdef"
        val l = lines(text)
        assertEquals(1, indexAt(text, l[0], 7f))
        assertEquals(2, indexAt(text, l[0], 8f))
        assertEquals(1, indexAt(text, l[0], 7.5f))
    }

    @Test
    fun `a click never lands inside a surrogate pair`() {
        val text = "a😀b"
        val l = lines(text, 100f)
        // Boundaries at 0, 1, 3 and 4 (x 0, 5, 10, 15 dp).
        for (x in listOf(0f, 3f, 6f, 9f, 11f, 14f, 20f)) {
            val index = indexAt(text, l[0], x)
            assertTrue(index != 2, "x $x gave $index")
        }
        assertEquals(3, indexAt(text, l[0], 9f))
    }

    @Test
    fun `a long run of emoji is cut between pairs`() {
        val text = "😀".repeat(8)
        val l = lines(text)
        assertEquals(listOf(Line(0, 12, 12, 30f), Line(12, 16, 16, 10f)), l)
    }

    @Test
    fun `spaces past the width wrap to a line of their own`() {
        val text = "aaaaa   "
        val l = lines(text)
        assertEquals(listOf(Line(0, 6, 7, 30f), Line(7, 8, 8, 5f)), l)
        // Every caret position belongs to a line.
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1, 1), (0..8).map { TextLayout.lineOf(l, it) })
    }

    @Test
    fun `wrapped lines cover the text, each starting where the previous one said`() {
        val text = "Rendez-vous à l'arène dimanche à 15:00, inscriptions au panneau du spawn !\nÀ demain."
        val l = lines(text, 60f)
        assertEquals(0, l.first().start)
        assertEquals(text.length, l.last().end)
        for (i in 1 until l.size) assertEquals(l[i - 1].next, l[i].start)
        for (line in l) assertTrue(line.width <= 60f + 1e-3f, "$line too wide")
    }

    @Test
    fun `a limited wrap keeps whole lines and cuts the last with an ellipsis`() {
        assertEquals(emptyList<String>(), TextLayout.wrap("abc", mono, 10f, 30f, 0))
        assertEquals(listOf("aa bb", "cccc"), TextLayout.wrap("aa bb cccc", mono, 10f, 30f, 2))
        // A full last line loses its end to make room for the ellipsis.
        assertEquals(listOf("abcde…"), TextLayout.wrap("abcdef ghi", mono, 10f, 30f, 1))
    }
}
