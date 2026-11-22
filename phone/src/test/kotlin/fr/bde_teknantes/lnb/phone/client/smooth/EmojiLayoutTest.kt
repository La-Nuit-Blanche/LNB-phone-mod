package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.notifications.BannerText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Emoji laid out on the two real fonts, Inter and Noto Emoji: which face draws what, sequences, cuts. */
class EmojiLayoutTest {
    private val noto = NotoEmojiMetrics.NOTO

    /** Inter Regular as the game sees it (glyph 0 is missing), with the emoji face. */
    private val font = object : FontMetrics by InterMetrics.REGULAR {
        override fun hasGlyph(codePoint: Int) = InterMetrics.REGULAR.glyphId(codePoint) != 0
        override val emoji: EmojiMetrics get() = noto
    }

    private fun drawn(text: String): List<Int> {
        val out = ArrayList<Int>()
        TextLayout.forEachGlyph(text, font, 13f) { d, _ -> out += d }
        return out
    }

    private fun emoji(cp: Int) = EmojiShaping.EMOJI_GLYPH or noto.glyph(cp)

    private fun isEmoji(text: String) = drawn(text).let { it.size == 1 && EmojiShaping.isEmojiGlyph(it[0]) }

    @Test
    fun `the bundled font is the one these rules were written for`() {
        assertTrue(noto.ligatures.size > 3000, "ligatures: ${noto.ligatures.size}")
        assertTrue(noto.ligatures.workUsed < noto.ligatures.workAllowed / 4)
        assertNotEquals(0, noto.glyph(0x1F600))
    }

    @Test
    fun `plain text stays Inter, keycap bases included`() {
        for (text in listOf("1", "#", "*", "a", "€", "©", "®", "™", "→", "★", "❤")) {
            val d = drawn(text)
            assertEquals(listOf(text.codePointAt(0)), d, text)
        }
    }

    @Test
    fun `emoji, and symbols asking for emoji, go to the emoji face`() {
        for (text in listOf("😀", "🎉", "☺", "✔", "©️", "❤️", "™️", "1️⃣", "#️⃣", "*⃣")) assertTrue(isEmoji(text), text)
        // The text selector keeps Inter's heart; the digit with the emoji selector alone stays a digit.
        assertEquals(listOf(0x2764), drawn("❤︎"))
        assertEquals(listOf('1'.code), drawn("1️"))
    }

    @Test
    fun `sequences compose to one glyph`() {
        // Monochrome: a toned emoji is its base.
        assertEquals(listOf(emoji(0x1F44D)), drawn("👍🏽"))
        for (text in listOf("👨‍👩‍👧", "🇫🇷", "1️⃣", "❤️‍🔥", "🏳️‍🌈", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "👩🏽‍💻")) {
            val d = drawn(text)
            assertEquals(1, d.size, text)
            assertTrue(EmojiShaping.isEmojiGlyph(d[0]), text)
            assertNotEquals(emoji(text.codePointAt(0)), d[0], "$text composed, not its first part")
        }
    }

    @Test
    fun `what can't be composed degrades to the base or its parts`() {
        // A skin tone on an emoji that takes none: dropped, no swatch.
        assertEquals(listOf(emoji(0x1F600)), drawn("😀🏽"))
        // Two letters that make no flag: the two letters.
        assertEquals(listOf(emoji(0x1F1FD), emoji(0x1F1FD)), drawn("🇽🇽"))
        // A ZWJ sequence the font doesn't have: its emoji side by side.
        assertEquals(listOf(emoji(0x1F468), emoji(0x1F996)), drawn("👨‍🦖"))
        // Tags that make no known flag: the black flag.
        assertEquals(listOf(emoji(0x1F3F4)), drawn("🏴󠁡󠁢󠁿"))
    }

    @Test
    fun `an emoji neither font has is one box per grapheme`() {
        // Face with bags under the eyes (Emoji 16), newer than the font.
        assertEquals(listOf(0x1FAE9), drawn("🫩"))
        assertEquals(listOf(0x1FAE9), drawn("🫩🏽"))
    }

    @Test
    fun `widths add up, scale linearly, and never kern across faces`() {
        val a = TextLayout.width("a", font, 13f)
        val b = TextLayout.width("b", font, 13f)
        val smile = noto.advance(noto.glyph(0x1F600)) * 13f
        assertEquals(a + smile + b, TextLayout.width("a😀b", font, 13f), 1e-4f)
        for (k in listOf(1.5f, 2f, 4.5f)) {
            assertEquals(TextLayout.width("Salut 👋🏽 ça va ? 🇫🇷", font, 13f) * k, TextLayout.width("Salut 👋🏽 ça va ? 🇫🇷", font, 13f * k), 1e-3f)
        }
    }

    @Test
    fun `wrapping and truncating cut between graphemes only`() {
        val family = "👨‍👩‍👧"
        val text = family.repeat(12)
        val boundaries = Graphemes.boundaries(text)
        for (width in listOf(20f, 40f, 61f, 100f)) {
            for (line in TextLayout.lines(text, font, 13f, width)) {
                assertTrue(boundaries[line.start] && boundaries[line.end], "line $line at $width")
            }
            val cut = TextLayout.truncate("Salut $text", font, 13f, width)
            assertTrue(cut.isEmpty() || Graphemes.isBoundary("Salut $text", cut.length - 1), "\"$cut\" at $width")
        }
        val line = TextLayout.Line(0, text.length, text.length, 0f)
        for (x in 0..200 step 3) assertTrue(boundaries[TextLayout.indexAt(text, line, x.toFloat(), font, 13f)])
    }

    @Test
    fun `the banner keeps what the faces draw and drops the rest`() {
        assertEquals("🎉 Party tonight", BannerText.clean("🎉 Party\ntonight", font))
        assertEquals("Bravo 👍🏽 1️⃣ 🇫🇷", BannerText.clean("Bravo 👍🏽 1️⃣ 🇫🇷", font))
        assertEquals("? ok", BannerText.clean("田中 🫩 ok", font))
        assertEquals("LNB™️", BannerText.clean("LNB™️", font))
        assertFalse(BannerText.clean("a‍b", font).contains('‍'))
    }
}
