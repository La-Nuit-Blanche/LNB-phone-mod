package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextLayoutTest {
    /** Every code point 0.5 em wide, "AV" kerned by -0.25 em. */
    private val mono = object : FontMetrics {
        override fun advance(codePoint: Int) = 0.5f
        override fun kerning(left: Int, right: Int) = if (left == 'A'.code && right == 'V'.code) -0.25f else 0f
        override val ascender = 0.8f
        override val descender = 0.2f
    }

    // At size 10 every character is 5 dp.
    private fun width(text: String) = TextLayout.width(text, mono, 10f)

    @Test
    fun `measures with kerning`() {
        assertEquals(15f, width("abc"))
        assertEquals(7.5f, width("AV"))
    }

    @Test
    fun `keeps text that fits and cuts the rest with an ellipsis`() {
        assertEquals("abcd", TextLayout.truncate("abcd", mono, 10f, 20f))
        // 20 dp: three characters plus the ellipsis.
        assertEquals("abc…", TextLayout.truncate("abcdef", mono, 10f, 20f))
        // The space before the ellipsis goes.
        assertEquals("ab…", TextLayout.truncate("ab cdef", mono, 10f, 20f))
        assertEquals("…", TextLayout.truncate("abcdef", mono, 10f, 5f))
        assertEquals("", TextLayout.truncate("abcdef", mono, 10f, 4f))
    }

    @Test
    fun `wraps at spaces and line feeds`() {
        // 30 dp: six characters per line.
        assertEquals(listOf("aa bb", "cccc", "dd"), TextLayout.wrap("aa bb cccc dd", mono, 10f, 30f))
        assertEquals(listOf("aa bb", "cccc d"), TextLayout.wrap("aa bb cccc d", mono, 10f, 30f))
        assertEquals(listOf("aa", "", "bb"), TextLayout.wrap("aa\n\nbb", mono, 10f, 30f))
    }

    @Test
    fun `breaks a word longer than a line`() {
        assertEquals(listOf("abcdef", "ghij", "kl"), TextLayout.wrap("abcdefghij kl", mono, 10f, 30f))
    }

    @Test
    fun `never breaks before French high punctuation`() {
        // "servi !" stays together even though "servi" alone would end the line.
        assertEquals(listOf("ab", "servi !"), TextLayout.wrap("ab servi !", mono, 10f, 35f))
        assertEquals(listOf("« oui »"), TextLayout.wrap("« oui »", mono, 10f, 35f))
    }

    @Test
    fun `centres the font's line in the line box`() {
        // Content 10 dp in an 18 dp box: 4 dp of leading above, then the 8 dp ascender.
        assertEquals(12f, TextLayout.baseline(TextStyle(FontWeight.REGULAR, 10f, 18f), mono))
    }

    @Test
    fun `wrapped lines know where they come from`() {
        val text = "aa bb cccc\ndd"
        val lines = TextLayout.lines(text, mono, 10f, 30f)
        assertEquals(listOf(TextLayout.Line(0, 5, 6, 25f), TextLayout.Line(6, 10, 11, 20f), TextLayout.Line(11, 13, 13, 10f)), lines)
        assertEquals(0, TextLayout.lineOf(lines, 5))
        assertEquals(1, TextLayout.lineOf(lines, 6))
        assertEquals(8, TextLayout.indexAt(text, lines[1], 11f, mono, 10f))
    }

    @Test
    fun `a limited wrap ends with an ellipsis`() {
        assertEquals(listOf("aa bb", "cccc…"), TextLayout.wrap("aa bb cccc dd ee", mono, 10f, 30f, 2))
        assertEquals(listOf("aa"), TextLayout.wrap("aa", mono, 10f, 30f, 2))
    }
}
