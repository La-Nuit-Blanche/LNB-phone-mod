package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.api.TextLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How layout treats text that isn't plain letters: line ends, tabs, invisible characters, missing glyphs. */
class TextHygieneTest {
    /** Every code point 0.5 em (5 dp at size 10); the font lacks emoji and combining marks. */
    private val font = object : FontMetrics {
        override fun advance(codePoint: Int) = 0.5f
        override fun kerning(left: Int, right: Int) = 0f
        override fun hasGlyph(codePoint: Int) = codePoint < 0x300 || codePoint in 0x2000..0x2FFF
        override val ascender = 0.8f
        override val descender = 0.2f
    }

    private fun width(text: String) = TextLayout.width(text, font, 10f)

    private fun drawn(text: String): List<Int> {
        val out = ArrayList<Int>()
        TextLayout.forEachGlyph(text, font, 10f) { cp, _ -> out += cp }
        return out
    }

    @Test
    fun `every kind of line end breaks a line`() {
        assertEquals(listOf("a", "b", "c", "d"), TextLayout.wrap("a\r\nb\rc\nd", font, 10f, 100f))
        val lines = TextLayout.lines("a\r\nb", font, 10f, 100f)
        assertEquals(3, lines[0].next)
    }

    @Test
    fun `a tab is a space`() {
        assertEquals(width("a b"), width("a\tb"))
        assertEquals(listOf(' '.code), drawn("\t"))
    }

    @Test
    fun `invisible characters take no room and draw nothing`() {
        val invisible = "\u0007​‌‍‎‮⁦️﻿͏"
        assertEquals(width("ab"), width("a${invisible}b"))
        assertEquals(listOf('a'.code, 'b'.code), drawn("a${invisible}b"))
    }

    @Test
    fun `one box per missing grapheme`() {
        // A thumbs-up with a skin tone, a flag's two letters, a family joined with ZWJ: one box each.
        assertEquals(1, drawn("👍🏽").size)
        assertEquals(1, drawn("🇫🇷").size)
        assertEquals(1, drawn("👨‍👩‍👧").size)
        // Two flags are two graphemes.
        assertEquals(2, drawn("🇫🇷🇩🇪").size)
        // A letter with an accent the font lacks: the letter alone.
        assertEquals(listOf('e'.code), drawn("é"))
    }

    @Test
    fun `a broken word never starts a line with the space glued to its punctuation`() {
        val lines = TextLayout.wrap("abcdefghij !", font, 10f, 30f)
        assertFalse(lines.any { it.startsWith(" ") }, "lines: $lines")
    }

    @Test
    fun `truncating a long run of spaces stays fast and correct`() {
        val text = "a" + " ".repeat(20000) + "b"
        val start = System.nanoTime()
        assertEquals("a…", TextLayout.truncate(text, font, 10f, 12f))
        assertTrue(System.nanoTime() - start < 1_000_000_000L)
    }

    @Test
    fun `limits count normalised code points, the same on both sides`() {
        assertEquals(1, TextLimits.count("é"))
        assertEquals(1, TextLimits.count("😀"))
        assertTrue(TextLimits.canSend(" a ", 3))
        assertFalse(TextLimits.canSend(" \n ", 3))
        assertFalse(TextLimits.fits("abcd", 3))
    }
}
