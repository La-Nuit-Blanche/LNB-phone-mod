package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.drawn
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.emojiGlyph
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.noto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** What the GSUB reader finds in the real bundled emoji font, for sequences whose composition is known. */
class GsubRealFontTest {
    private fun cp(vararg c: Int) = String(c, 0, c.size)

    /** The glyphs [EmojiShaping] hands to the reader for [text]: its code points, variation selectors left out. */
    private fun glyphs(text: String): IntArray =
        text.codePoints().filter { it !in 0xFE00..0xFE0F }.map { noto.glyph(it) }.toArray()

    private fun found(text: String): Long = glyphs(text).let { noto.ligatures.ligature(it, 0, it.size) }

    private fun components(packed: Long) = (packed and 0xFFFF).toInt()

    @Test
    fun `known sequences compose into one glyph that uses all their components`() {
        val sequences = mapOf(
            "flag of France" to cp(0x1F1EB, 0x1F1F7),
            "family of three" to cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467),
            "family of four" to cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466),
            "heart on fire" to cp(0x2764, 0xFE0F, 0x200D, 0x1F525),
            "rainbow flag" to cp(0x1F3F3, 0xFE0F, 0x200D, 0x1F308),
            "pirate flag" to cp(0x1F3F4, 0x200D, 0x2620, 0xFE0F),
            "keycap 1" to cp('1'.code, 0xFE0F, 0x20E3),
            "keycap hash" to cp('#'.code, 0xFE0F, 0x20E3),
            "keycap star" to cp('*'.code, 0xFE0F, 0x20E3),
            "England" to cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F),
            "Scotland" to cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0073, 0xE0063, 0xE0074, 0xE007F),
            "Wales" to cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0077, 0xE006C, 0xE0073, 0xE007F),
            "woman technologist" to cp(0x1F469, 0x200D, 0x1F4BB),
            "couple with heart" to cp(0x1F469, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F468),
            "kiss with two tones" to cp(0x1F9D1, 0x1F3FB, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F48B, 0x200D, 0x1F9D1, 0x1F3FC),
        )
        for ((name, text) in sequences) {
            val glyphs = glyphs(text)
            assertTrue(glyphs.all { it != 0 }, "$name: the font has every part")
            val packed = found(text)
            assertNotEquals(0L, packed, name)
            assertEquals(glyphs.size, components(packed), "$name uses every component")
            val ligature = (packed ushr 32).toInt()
            assertTrue(ligature != 0 && glyphs.none { it == ligature }, name)
        }
    }

    @Test
    fun `every ISO country's flag composes`() {
        val missing = ArrayList<String>()
        for (country in Locale.getISOCountries()) {
            val text = String(country.map { 0x1F1E6 + (it - 'A') }.toIntArray(), 0, 2)
            val packed = found(text)
            if (packed == 0L || components(packed) != 2) missing += country
        }
        // Noto Emoji 3.002 has the flag of every region Unicode lists; a missing one would draw its two letters.
        assertTrue(missing.size <= 3, "flags without a glyph: $missing")
    }

    @Test
    fun `an unknown pair of indicators, a lone part, and plain emoji compose into nothing`() {
        assertEquals(0L, found(cp(0x1F1FD, 0x1F1FD)))
        assertEquals(0L, found(cp(0x1F1EB)))
        assertEquals(0L, found(cp(0x1F600)))
        assertEquals(0L, found(cp(0x1F468, 0x200D, 0x1F996)))
        assertEquals(0L, found(cp(0x1F3F4, 0xE0061, 0xE0062, 0xE007F)))
    }

    @Test
    fun `the glyph a ligature gives is stable whatever follows the sequence`() {
        val family = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)
        val alone = found(family)
        val followed = glyphs(family + cp('a'.code, 0x1F600))
        assertEquals(alone, noto.ligatures.ligature(followed, 0, followed.size))
        // And from the middle of a longer run.
        val run = glyphs(cp(0x1F600) + family)
        assertEquals(alone, noto.ligatures.ligature(run, 1, run.size))
    }

    @Test
    fun `a skin tone on an emoji that takes one draws one glyph, never the swatch, on every emoji modifier base`() {
        val tones = intArrayOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)
        var checked = 0
        for (base in 0x1F000..0x1FAFF) {
            if (!Character.isEmojiModifierBase(base) || noto.glyph(base) == 0) continue
            for (tone in tones) {
                val d = drawn(cp(base, tone))
                assertEquals(1, d.size, "%04X + %04X".format(base, tone))
                assertTrue(EmojiShaping.isEmojiGlyph(d[0]))
                assertNotEquals(emojiGlyph(tone), d[0], "%04X shows the swatch".format(base))
            }
            checked++
        }
        assertTrue(checked > 100, "$checked modifier bases")
        // A tone on an emoji that takes none is dropped, whatever the tone.
        for (tone in tones) assertEquals(listOf(emojiGlyph(0x1F600)), drawn(cp(0x1F600, tone)))
    }

    @Test
    fun `the emoji text selector or emoji selector between a base and its tone changes nothing`() {
        assertEquals(drawn(cp(0x1F44D, 0x1F3FD)), drawn(cp(0x1F44D, 0xFE0F, 0x1F3FD)))
    }
}
