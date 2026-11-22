package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** [TextMetrics]' cache: right after a font reload, bounded, and never mixing two widths or two kinds of answer. */
class TextMetricsTest {
    /** A fixed-width font that counts how often it is asked for an advance, i.e. how often text is really measured. */
    private class CountingFont(private val em: Float) : FontMetrics {
        var advances = 0
        override fun advance(codePoint: Int): Float {
            advances++
            return em
        }
        override fun kerning(left: Int, right: Int) = 0f
        override val ascender = 0.8f
        override val descender = 0.2f
    }

    @AfterEach
    fun restore() = KitFakes.uninstall()

    @Test
    fun `measures with the current font, and again with the font a reload brings`() {
        val before = CountingFont(0.5f)
        TextMetrics.fonts = { before }
        assertEquals(26f, TextMetrics.measure("abcd", TextStyle.BODY))
        // A resource pack replaces Inter: new font objects, new measurements, no stale entry.
        val after = CountingFont(1f)
        TextMetrics.fonts = { after }
        assertEquals(52f, TextMetrics.measure("abcd", TextStyle.BODY))
        assertEquals(listOf("abcd"), TextMetrics.wrap("abcd", TextStyle.BODY, 60f))
        assertEquals(listOf("ab", "cd"), TextMetrics.wrap("ab cd", TextStyle.BODY, 40f))
    }

    @Test
    fun `a measured text is measured once`() {
        val font = CountingFont(0.5f)
        TextMetrics.fonts = { font }
        TextMetrics.measure("Bonjour", TextStyle.BODY)
        val first = font.advances
        repeat(10) { TextMetrics.measure("Bonjour", TextStyle.BODY) }
        assertEquals(first, font.advances)
    }

    @Test
    fun `the same text at two widths wraps twice, each width keeping its own lines`() {
        TextMetrics.fonts = { CountingFont(0.5f) }
        val text = "aa bb cc dd"
        // 6.5 dp per character in BODY.
        val narrow = TextMetrics.wrap(text, TextStyle.BODY, 33f)
        val wide = TextMetrics.wrap(text, TextStyle.BODY, 72f)
        assertEquals(listOf("aa bb", "cc dd"), narrow)
        assertEquals(listOf("aa bb cc dd"), wide)
        assertEquals(narrow, TextMetrics.wrap(text, TextStyle.BODY, 33f))
        assertNotEquals(TextMetrics.lines(text, TextStyle.BODY, 33f), TextMetrics.lines(text, TextStyle.BODY, 72f))
    }

    @Test
    fun `a limited wrap and a full wrap of the same text are kept apart`() {
        TextMetrics.fonts = { CountingFont(0.5f) }
        val text = "aa bb cc dd"
        assertEquals(listOf("aa bb", "cc dd"), TextMetrics.wrap(text, TextStyle.BODY, 33f))
        assertEquals(listOf("aa bb…"), TextMetrics.wrap(text, TextStyle.BODY, 39f, 1))
        assertEquals(2, TextMetrics.wrap(text, TextStyle.BODY, 39f).size)
    }

    @Test
    fun `truncating, measuring and wrapping one text are three entries`() {
        TextMetrics.fonts = { CountingFont(0.5f) }
        val text = "abcdefgh"
        assertEquals(52f, TextMetrics.measure(text, TextStyle.BODY))
        assertEquals("abc…", TextMetrics.truncate(text, TextStyle.BODY, 26f))
        assertEquals(listOf("abcd", "efgh"), TextMetrics.wrap(text, TextStyle.BODY, 26f))
    }

    @Test
    fun `styles of the same size but different weights use their own font`() {
        val regular = CountingFont(0.5f)
        val semibold = CountingFont(0.6f)
        TextMetrics.fonts = { if (it == fr.bde_teknantes.lnb.phone.client.smooth.FontWeight.SEMIBOLD) semibold else regular }
        assertEquals(65f, TextMetrics.measure("aaaaaaaaaa", TextStyle.BODY))
        assertEquals(78f, TextMetrics.measure("aaaaaaaaaa", TextStyle.BODY_STRONG), 1e-4f)
    }

    @Test
    fun `the oldest entries leave once 2048 are kept, the recently used stay`() {
        val font = CountingFont(0.5f)
        TextMetrics.fonts = { font }
        // Fill the cache with this font's entries only, pushing out whatever earlier tests left.
        for (i in 0 until 2048) TextMetrics.measure("k$i", TextStyle.BODY)
        TextMetrics.measure("k0", TextStyle.BODY)
        TextMetrics.measure("k2048", TextStyle.BODY)

        var before = font.advances
        TextMetrics.measure("k0", TextStyle.BODY)
        assertEquals(before, font.advances, "k0 was used again just before: still cached")
        TextMetrics.measure("k2048", TextStyle.BODY)
        assertEquals(before, font.advances, "the newest entry is cached")

        before = font.advances
        TextMetrics.measure("k1", TextStyle.BODY)
        assertNotEquals(before, font.advances, "k1 was the least recently used: evicted, measured again")
    }
}
