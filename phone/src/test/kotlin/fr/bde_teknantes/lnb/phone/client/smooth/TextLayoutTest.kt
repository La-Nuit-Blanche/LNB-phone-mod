package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

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

    private fun wrap(text: String, maxWidth: Float) = TextLayout.wrap(text, mono, 10f, maxWidth)

    private fun truncate(text: String, maxWidth: Float) = TextLayout.truncate(text, mono, 10f, maxWidth)

    private fun codePoints(text: String) = text.codePointCount(0, text.length)

    @Test
    fun `measures with kerning`() {
        assertEquals(15f, width("abc"))
        assertEquals(7.5f, width("AV"))
        assertEquals(0f, width(""))
    }

    @Test
    fun `walks glyphs at their kerned pen positions`() {
        val pens = ArrayList<Pair<Int, Float>>()
        val total = TextLayout.forEachGlyph("AVA", mono, 10f) { cp, x -> pens += cp to x }
        assertEquals(listOf('A'.code to 0f, 'V'.code to 2.5f, 'A'.code to 7.5f), pens)
        assertEquals(12.5f, total)
    }

    @Test
    fun `counts code points, not chars`() {
        val smiley = "😀" // one code point, two chars
        assertEquals(10f, width("${smiley}a"))
        assertEquals("$smiley$smiley…", truncate(smiley.repeat(4), 15f))
        assertEquals(listOf(smiley.repeat(2), smiley.repeat(2)), wrap(smiley.repeat(4), 10f))
    }

    // ---- Truncation ----

    @Test
    fun `keeps text that fits and cuts the rest with an ellipsis`() {
        assertEquals("abcd", truncate("abcd", 20f))
        // 20 dp: three characters plus the ellipsis.
        assertEquals("abc…", truncate("abcdef", 20f))
        // The space before the ellipsis goes.
        assertEquals("ab…", truncate("ab cdef", 20f))
        assertEquals("…", truncate("abcdef", 5f))
        assertEquals("", truncate("abcdef", 4f))
    }

    @Test
    fun `text that fits exactly is kept, float noise included`() {
        assertEquals("abcd", truncate("abcd", 19.9995f))
        assertEquals("ab…", truncate("abcd", 19.99f))
    }

    @Test
    fun `nothing fits in no width`() {
        assertEquals("", truncate("abc", 0f))
        assertEquals("", truncate("abc", -10f))
        assertEquals("", truncate("", 0f))
        assertEquals("", truncate("", -10f))
    }

    @Test
    fun `the kerning before the ellipsis counts`() {
        val loose = object : FontMetrics by mono {
            override fun kerning(left: Int, right: Int) = if (right == 0x2026) 0.5f else 0f
        }
        // "abc…" would be 15 + 5 (kerning) + 5 = 25 dp.
        assertEquals("ab…", TextLayout.truncate("abcdef", loose, 10f, 20f))
    }

    @Test
    fun `spaces never end up before the ellipsis`() {
        assertEquals("a…", truncate("a      bcdef", 20f))
        assertEquals("…", truncate("   abcdef", 20f))
    }

    @Test
    fun `truncated text is never wider than its slot`() {
        val random = Random(42)
        val alphabet = "abcAVé è«» !?:;.,'-ÉçÀ ".codePoints().toArray()
        repeat(3000) {
            val text = buildString { repeat(random.nextInt(0, 20)) { appendCodePoint(alphabet[random.nextInt(alphabet.size)]) } }
            val maxWidth = random.nextInt(-10, 120) / 2f
            val result = truncate(text, maxWidth)
            assertTrue(width(result) <= maxWidth + 1e-3f || result.isEmpty(), "\"$result\" from \"$text\" in $maxWidth dp")
            when {
                text.isEmpty() -> assertEquals("", result)
                result == text -> assertTrue(width(text) <= maxWidth + 1e-3f)
                result.isEmpty() -> assertTrue(5f > maxWidth + 1e-3f, "the ellipsis alone fits $maxWidth dp")
                else -> {
                    assertTrue(result.endsWith("…"), "\"$result\" from \"$text\"")
                    assertTrue(text.startsWith(result.dropLast(1)), "\"$result\" is a start of \"$text\"")
                    // The longest start that fits: one more code point would not.
                    val kept = result.dropLast(1)
                    val next = text.offsetByCodePoints(0, codePoints(kept)).let { text.offsetByCodePoints(it, 1) }
                    val longer = text.substring(0, next).trimEnd()
                    if (longer.length > kept.length) assertTrue(width("$longer…") > maxWidth + 1e-3f, "\"$longer…\" would fit too")
                }
            }
        }
    }

    // ---- Wrapping ----

    @Test
    fun `wraps at spaces and line feeds`() {
        // 30 dp: six characters per line.
        assertEquals(listOf("aa bb", "cccc", "dd"), wrap("aa bb cccc dd", 30f))
        assertEquals(listOf("aa bb", "cccc d"), wrap("aa bb cccc d", 30f))
        assertEquals(listOf("aa", "", "bb"), wrap("aa\n\nbb", 30f))
    }

    @Test
    fun `a line feed starts a new line whatever room is left`() {
        assertEquals(listOf("aaaa", "bb cc"), wrap("aaaa\nbb cc", 30f))
        assertEquals(listOf("", "ab"), wrap("\nab", 30f))
    }

    @Test
    fun `text that fits exactly stays on one line`() {
        assertEquals(listOf("abcdef"), wrap("abcdef", 30f))
        assertEquals(listOf("abc def"), wrap("abc def", 35f))
        assertEquals(listOf("abc def"), wrap("abc def", 34.9995f))
        assertEquals(listOf("abc", "def"), wrap("abc def", 34.99f))
    }

    @Test
    fun `breaks a word longer than a line`() {
        assertEquals(listOf("abcdef", "ghij", "kl"), wrap("abcdefghij kl", 30f))
    }

    @Test
    fun `a character wider than the line still gets a line of its own`() {
        assertEquals(listOf("W"), wrap("W", 3f))
        assertEquals(listOf("a", "W", "b"), wrap("aWb", 3f))
    }

    @Test
    fun `no width puts one character per line and never loops`() {
        assertEquals(listOf("a", "b", "c", "d"), wrap("ab cd", 0f))
        assertEquals(listOf("a", "b", "c"), wrap("abc", -5f))
    }

    @Test
    fun `never breaks before French high punctuation`() {
        // "servi !" stays together even though "servi" alone would end the line.
        assertEquals(listOf("ab", "servi !"), wrap("ab servi !", 35f))
        assertEquals(listOf("« oui »"), wrap("« oui »", 35f))
        // 60 dp: "Quoi de neuf" fills the line; the question mark goes down with "neuf".
        assertEquals(listOf("Quoi de", "neuf ?"), wrap("Quoi de neuf ?", 60f))
        assertEquals(listOf("Note", "bien : oui"), wrap("Note bien : oui", 50f))
        assertEquals(listOf("un ;", "deux"), wrap("un ; deux", 25f))
    }

    @Test
    fun `never breaks inside guillemets`() {
        // "«" never ends a line and "»" never starts one.
        assertEquals(listOf("il a dit", "« salut »"), wrap("il a dit « salut »", 45f))
        assertEquals(listOf("« salut »", "et voilà"), wrap("« salut » et voilà", 45f))
    }

    @Test
    fun `never breaks at a non-breaking space`() {
        assertEquals(listOf("aa bb", "cc"), wrap("aa bb cc", 25f))
        assertEquals(listOf("aa !", "cc"), wrap("aa ! cc", 20f))
    }

    @Test
    fun `random text always fits and keeps every character`() {
        val random = Random(7)
        val alphabet = "aaaabbcAVé è  «» !?:;.,\n".codePoints().toArray()
        repeat(3000) {
            val text = buildString { repeat(random.nextInt(0, 40)) { appendCodePoint(alphabet[random.nextInt(alphabet.size)]) } }
            val maxWidth = random.nextInt(-2, 80) / 2f
            val lines = wrap(text, maxWidth)
            for (line in lines) {
                assertTrue(width(line) <= maxWidth + 1e-3f || codePoints(line) <= 1, "\"$line\" of \"$text\" in $maxWidth dp")
                assertTrue('\n' !in line)
            }
            // Only spaces and line feeds can disappear, and nothing is reordered.
            val kept = lines.joinToString("").filter { it != ' ' }
            assertEquals(text.filter { it != ' ' && it != '\n' }, kept, "\"$text\" in $maxWidth dp")
        }
    }

    @Test
    fun `wrapped prose fills each line greedily and keeps French punctuation attached`() {
        val words = listOf("le", "serveur", "est", "ouvert", "ce", "soir", "!", "venez", "nombreux", "?", "«", "Oui", "»", ":", "à", "très", "vite", ";", "merci")
        val random = Random(3)
        repeat(500) {
            val text = (1..random.nextInt(1, 30)).joinToString(" ") { words[random.nextInt(words.size)] }
            val maxWidth = random.nextInt(9, 40) * 5f
            // Marks glue to their word; keep to texts whose glued units each fit a line.
            if (TextLayout.words(text).any { width(it) > maxWidth }) return@repeat
            val lines = wrap(text, maxWidth)
            assertEquals(text, lines.joinToString(" "), "only the spaces lines break at are dropped")
            for ((i, line) in lines.withIndex()) {
                assertTrue(width(line) <= maxWidth + 1e-3f)
                if (i > 0) assertTrue(line.first() !in "!?:;»", "line \"$line\" of \"$text\" starts with a mark")
                if (i < lines.size - 1) {
                    assertTrue(line.last() != '«', "line \"$line\" of \"$text\" ends with «")
                    // The next unit would not have fitted.
                    val next = TextLayout.words(lines[i + 1]).first()
                    assertTrue(width("$line $next") > maxWidth + 1e-3f, "\"$next\" fitted after \"$line\" in $maxWidth dp")
                }
            }
        }
    }

    @Test
    fun `centres the font's line in the line box`() {
        // Content 10 dp in an 18 dp box: 4 dp of leading above, then the 8 dp ascender.
        assertEquals(12f, TextLayout.baseline(TextStyle(FontWeight.REGULAR, 10f, 18f), mono))
    }
}
