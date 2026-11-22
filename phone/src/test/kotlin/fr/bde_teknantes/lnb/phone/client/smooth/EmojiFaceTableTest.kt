package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.drawn
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.emojiGlyph
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.inter
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.noto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Which face draws what, as a table over the rules in [EmojiShaping]'s documentation, with the expectations worked out
 * from the two bundled fonts' character maps (not from the code's own lists): for every code point, alone and with
 * each variation selector.
 */
class EmojiFaceTableTest {
    private fun cp(vararg c: Int) = String(c, 0, c.size)

    private val VS15 = 0xFE0E
    private val VS16 = 0xFE0F

    private enum class Face { INTER, EMOJI }

    private fun faceOf(text: String): Face {
        val d = drawn(text)
        assertEquals(1, d.size, "one glyph for ${text.map { "%04X".format(it.code) }}")
        return if (EmojiShaping.isEmojiGlyph(d[0])) Face.EMOJI else Face.INTER
    }

    /** Whether the character takes part in the sweep: a plain, drawable base (not space, control, mark, joiner). */
    private fun plain(c: Int): Boolean {
        if (c <= 0x20 || Character.isWhitespace(c) || Character.isSpaceChar(c) || TextLayout.isInvisible(c)) return false
        if (Character.isSurrogate(c.toChar()) && c < 0x10000) return false
        if (!Character.isDefined(c)) return false
        return when (Character.getType(c)) {
            Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
            Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.PRIVATE_USE.toInt(), Character.UNASSIGNED.toInt() -> false
            else -> true
        } && !(c in '0'.code..'9'.code || c == '#'.code || c == '*'.code) && c !in 0x1F1E6..0x1F1FF && c !in 0x1F3FB..0x1F3FF
    }

    private fun interHas(c: Int) = inter.glyphId(c) != 0
    private fun notoHas(c: Int) = noto.glyph(c) != 0

    @Test
    fun `the fonts this table is worked out from are the ones the rules assume`() {
        // Symbols Inter has, the ones it lacks, and what the emoji face has of each.
        for (c in listOf(0xA9, 0xAE, 0x2122, 0x2190, 0x2192, 0x2605, 0x2665)) assertTrue(interHas(c), "Inter has %04X".format(c))
        for (c in listOf(0x263A, 0x2714, 0x1F600, 0x1F44D)) assertFalse(interHas(c), "Inter lacks %04X".format(c))
        for (c in listOf(0xA9, 0xAE, 0x2122, 0x263A, 0x2714, 0x2764, 0x1F600, 0x1F44D)) assertTrue(notoHas(c), "Noto has %04X".format(c))
        assertFalse(notoHas(0x1FAE9), "the font is older than Emoji 16")
    }

    @Test
    fun `every code point alone goes to the face the rules give it`() {
        val bad = ArrayList<String>()
        var tested = 0
        var emojiCount = 0
        for (c in 0x21..0x2FFFF) {
            if (!plain(c)) continue
            tested++
            // Rules: emoji by default, or Inter lacks it, and the emoji face has it: emoji. Else Inter (its glyph, or
            // its missing-glyph box).
            val expected = if (notoHas(c) && (Character.isEmojiPresentation(c) || !interHas(c))) Face.EMOJI else Face.INTER
            val d = drawn(String(Character.toChars(c)))
            if (d.size != 1) { bad += "%04X: ${d.size} glyphs".format(c); continue }
            val actual = if (EmojiShaping.isEmojiGlyph(d[0])) Face.EMOJI else Face.INTER
            if (actual == Face.EMOJI) emojiCount++
            if (actual != expected) bad += "%04X: expected $expected, got $actual".format(c)
            if (actual == Face.INTER && d[0] != c) bad += "%04X drawn as %04X".format(c, d[0])
            if (actual == Face.EMOJI && d[0] != emojiGlyph(c)) bad += "%04X not its own glyph".format(c)
        }
        assertEquals(emptyList<String>(), bad.take(30), "${bad.size} code points")
        // The sweep really covered the repertoire (about 150k assigned characters, about 1,300 of them emoji).
        assertTrue(tested > 100_000, "tested $tested")
        assertTrue(emojiCount in 1000..2500, "emoji $emojiCount")
    }

    @Test
    fun `with the emoji selector, whatever the emoji face has is emoji, what it lacks stays as alone`() {
        val bad = ArrayList<String>()
        for (c in 0x21..0x2FFFF) {
            if (!plain(c)) continue
            val text = cp(c, VS16)
            val d = drawn(text)
            if (d.size != 1) { bad += "%04X VS16: ${d.size} glyphs".format(c); continue }
            val actual = if (EmojiShaping.isEmojiGlyph(d[0])) Face.EMOJI else Face.INTER
            val expected = if (notoHas(c)) Face.EMOJI else Face.INTER
            if (actual != expected) bad += "%04X VS16: expected $expected, got $actual".format(c)
        }
        assertEquals(emptyList<String>(), bad.take(30), "${bad.size} code points")
    }

    @Test
    fun `with the text selector, Inter's glyph where Inter has one, else the emoji face`() {
        val bad = ArrayList<String>()
        for (c in 0x21..0x2FFFF) {
            if (!plain(c)) continue
            val d = drawn(cp(c, VS15))
            if (d.size != 1) { bad += "%04X VS15: ${d.size} glyphs".format(c); continue }
            val actual = if (EmojiShaping.isEmojiGlyph(d[0])) Face.EMOJI else Face.INTER
            val expected = if (!interHas(c) && notoHas(c)) Face.EMOJI else Face.INTER
            if (actual != expected) bad += "%04X VS15: expected $expected, got $actual".format(c)
        }
        assertEquals(emptyList<String>(), bad.take(30), "${bad.size} code points")
    }

    @Test
    fun `the symbols of the brief, with and without a selector`() {
        // ©, ® and ™ stay Inter in a sentence and become emoji with the emoji selector.
        for (c in listOf(0xA9, 0xAE, 0x2122)) {
            assertEquals(Face.INTER, faceOf(cp(c)))
            assertEquals(Face.EMOJI, faceOf(cp(c, VS16)))
            assertEquals(Face.INTER, faceOf(cp(c, VS15)))
        }
        // Arrows, a star and a heart: Inter's, until the selector asks.
        for (c in listOf(0x2192, 0x2605, 0x2665, 0x2764)) {
            assertEquals(Face.INTER, faceOf(cp(c)), "%04X".format(c))
        }
        assertEquals(Face.EMOJI, faceOf(cp(0x2764, VS16)))
        assertEquals(Face.INTER, faceOf(cp(0x2764, VS15)))
        // Emoji by default, and what Inter lacks.
        for (c in listOf(0x1F600, 0x1F389, 0x263A, 0x2714)) assertEquals(Face.EMOJI, faceOf(cp(c)), "%04X".format(c))
        // The text selector on a character Inter lacks still draws something: the emoji.
        assertEquals(Face.EMOJI, faceOf(cp(0x263A, VS15)))
    }

    @Test
    fun `keycap bases go to the emoji face only with the keycap mark`() {
        for (base in "0123456789#*") {
            assertEquals(listOf(base.code), drawn("$base"), "$base alone")
            assertEquals(listOf(base.code), drawn(cp(base.code, VS16)), "$base with the emoji selector only")
            assertEquals(listOf(base.code), drawn(cp(base.code, VS15)), "$base with the text selector")
            assertEquals(Face.EMOJI, faceOf(cp(base.code, VS16, 0x20E3)), "$base keycap")
            assertEquals(Face.EMOJI, faceOf(cp(base.code, 0x20E3)), "$base keycap without selector")
        }
    }

    @Test
    fun `the cases a rule list forgets stay Inter, laid out exactly as without the emoji face`() {
        val texts = listOf(
            "Rendez-vous #12 à 14h", "06 12 34 56 78", "+33 6 12 34 56 78", "01.23.45.67.89", "C#", "F# et C++", "1. Premier\n2. Deuxième",
            "- *gras* et **plus gras**", "* un\n* deux", "© 2024 LNB", "® et ™", "100 % sûr", "a1b2c3", "#lnb #bde", "12/05/2025 à 08:30",
            "Tu viens ? Oui !", "« Bonjour » : dit-il", "3 × 4 = 12", "€ 5", "→ suite", "★ ★ ★", "♥", "N° 5", "(1)", "1)", "#", "*", "0",
            "H₂O", "x²", "½ litre", "a b", "Été", "été",
        )
        for (text in texts) {
            val plain = drawn(text, RealFaces.INTER_ONLY)
            val withFace = drawn(text, RealFaces.REGULAR)
            assertEquals(plain, withFace, "glyphs of $text")
            assertEquals(
                TextLayout.width(text, RealFaces.INTER_ONLY, 13f), TextLayout.width(text, RealFaces.REGULAR, 13f), 1e-4f, "width of $text",
            )
            assertTrue(withFace.none { EmojiShaping.isEmojiGlyph(it) }, text)
        }
    }

    @Test
    fun `random Latin text is unchanged by the emoji face`() {
        val random = Random(8)
        val alphabet = ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 \n.,;:!?'\"()-_/*#@&%+=<>[]{}€éèêàçùôîïÉÀ«»…–—©®™").toList()
        repeat(500) {
            val text = buildString { repeat(random.nextInt(0, 40)) { append(alphabet[random.nextInt(alphabet.size)]) } }
            assertEquals(drawn(text, RealFaces.INTER_ONLY), drawn(text, RealFaces.REGULAR), text)
        }
    }

    @Test
    fun `a lone tone after a space draws nothing, a lone joiner or selector draws nothing, a lone keycap mark draws nothing`() {
        val space = TextLayout.width(" ", RealFaces.REGULAR, 13f)
        assertEquals(space, TextLayout.width(cp(0x20, 0x1F3FD), RealFaces.REGULAR, 13f), 1e-4f)
        assertEquals(listOf(0x20), drawn(cp(0x20, 0x1F3FD)))
        assertEquals(listOf(0x20), drawn(cp(0x20, 0x200D)))
        assertEquals(listOf(0x20), drawn(cp(0x20, VS16)))
        assertEquals(emptyList<Int>(), drawn(cp(0x200D)))
        assertEquals(emptyList<Int>(), drawn(cp(VS16)))
        assertEquals(emptyList<Int>(), drawn(cp(0x20E3)).filter { EmojiShaping.isEmojiGlyph(it) })
    }

    @Test
    fun `a lone skin tone at the start of a text`() {
        // Not decided: the tone is an emoji by default, so the face draws its swatch when the text starts with it.
        // Recorded here so a change shows; the rule list only says "after a space draws nothing".
        val d = drawn(cp(0x1F3FD))
        assertEquals(listOf(emojiGlyph(0x1F3FD)), d)
    }

    @Test
    fun `a ZWJ sequence the font lacks is its emoji side by side, a part the font lacks is left out`() {
        // Man and dinosaur: not a sequence the font has.
        assertEquals(listOf(emojiGlyph(0x1F468), emojiGlyph(0x1F996)), drawn(cp(0x1F468, 0x200D, 0x1F996)))
        // A newer emoji in the middle or at the end is left out; the rest stays.
        assertEquals(listOf(emojiGlyph(0x1F468), emojiGlyph(0x1F469)), drawn(cp(0x1F468, 0x200D, 0x1FAE9, 0x200D, 0x1F469)))
        assertEquals(listOf(emojiGlyph(0x1F468)), drawn(cp(0x1F468, 0x200D, 0x1FAE9)))
        // The base itself lacking: the font is not asked, and the grapheme is one box.
        assertEquals(listOf(0x1FAE9), drawn(cp(0x1FAE9, 0x200D, 0x1F468)))
    }

    @Test
    fun `flags, known and unknown, subdivision flags known and unknown`() {
        val fr = cp(0x1F1EB, 0x1F1F7)
        val d = drawn(fr)
        assertEquals(1, d.size)
        assertTrue(EmojiShaping.isEmojiGlyph(d[0]))
        // Unknown pair: its two letters, as emoji glyphs of the indicators.
        assertEquals(listOf(emojiGlyph(0x1F1FD), emojiGlyph(0x1F1FD)), drawn(cp(0x1F1FD, 0x1F1FD)))
        // Three indicators: a pair and a lone one.
        val three = drawn(cp(0x1F1EB, 0x1F1F7, 0x1F1E9))
        assertEquals(2, three.size)
        assertEquals(emojiGlyph(0x1F1E9), three[1])
        // England, Scotland, Wales compose; a made-up subdivision leaves the black flag.
        for (tail in listOf(intArrayOf(0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067), intArrayOf(0xE0067, 0xE0062, 0xE0073, 0xE0063, 0xE0074), intArrayOf(0xE0067, 0xE0062, 0xE0077, 0xE006C, 0xE0073))) {
            val text = cp(0x1F3F4, *tail, 0xE007F)
            val g = drawn(text)
            assertEquals(1, g.size)
            assertTrue(g[0] != emojiGlyph(0x1F3F4), "composed, not the plain flag")
        }
        assertEquals(listOf(emojiGlyph(0x1F3F4)), drawn(cp(0x1F3F4, 0xE0061, 0xE0062, 0xE007F)))
        // Tags with no terminator or no base: nothing visible beyond the flag.
        assertEquals(listOf(emojiGlyph(0x1F3F4)), drawn(cp(0x1F3F4, 0xE0067, 0xE0062)))
        assertEquals(emptyList<Int>(), drawn(cp(0xE0067, 0xE0062, 0xE007F)))
    }

    @Test
    fun `semibold text draws the same emoji, with the same advances`() {
        for (text in listOf("😀", "👍🏽", "🇫🇷", "1️⃣", "❤️")) {
            assertEquals(drawn(text, RealFaces.REGULAR), drawn(text, RealFaces.SEMIBOLD), text)
        }
    }
}
