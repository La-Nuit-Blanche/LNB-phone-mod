package fr.bde_teknantes.lnb.phone.client.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Text made drawable by the banner's font, whatever players put in it. */
class BannerTextTest {
    /**
     * Roughly Inter's coverage: Basic Latin, Latin-1, Latin Extended A and B, combining accents, Greek, Cyrillic, the
     * usual punctuation (dashes, quotes, ellipsis, narrow no-break space), the euro and trademark signs, arrows.
     */
    private val inter = { cp: Int ->
        cp in 0x20..0x7E || cp in 0xA0..0x24F || cp in 0x300..0x36F || cp in 0x370..0x3FF || cp in 0x400..0x4FF ||
            cp in 0x2010..0x2027 || cp == 0x202F || cp in 0x2030..0x205E || cp == 0x20AC || cp == 0x2122 || cp in 0x2190..0x2199
    }

    /** A font without combining accents nor the no-break spaces. */
    private val bare = { cp: Int -> cp in 0x21..0x7E || cp in 0xA1..0x24F }

    private fun clean(text: String) = BannerText.clean(text, inter)

    // ---- Emoji ----

    @Test
    fun `plain emoji are dropped with the space they leave`() {
        assertEquals("Party tonight", clean("🎉 Party tonight"))
        assertEquals("Party tonight", clean("Party 🎉 tonight"))
        assertEquals("Party tonight", clean("Party tonight 🎉🎊"))
        assertEquals("gg", clean("gg😀"))
    }

    @Test
    fun `skin tones go with their emoji`() {
        assertEquals("Bravo", clean("Bravo 👍🏽"))
        assertEquals("Bravo", clean("Bravo 👋🏿👋🏻"))
        assertEquals("a", clean("a🏽")) // a lone modifier
    }

    @Test
    fun `joined sequences vanish entirely`() {
        assertEquals("Family", clean("Family 👨‍👩‍👧‍👦"))
        assertEquals("ok", clean("ok 🏳️‍🌈"))
        assertEquals("ok", clean("👩🏽‍💻 ok"))
        assertEquals("ok", clean("ok ❤️‍🔥"))
    }

    @Test
    fun `flags vanish, including tag sequences`() {
        assertEquals("Allez la France", clean("Allez la France 🇫🇷"))
        assertEquals("GB", clean("🏴󠁧󠁢󠁥󠁮󠁧󠁿 GB"))
        assertEquals("", clean("🇫🇷🇧🇪🇨🇦"))
    }

    @Test
    fun `a keycap keeps its digit or sign`() {
        assertEquals("1 place", clean("1️⃣ place"))
        assertEquals("#tag", clean("#️⃣tag"))
    }

    @Test
    fun `text-style symbols and variation selectors`() {
        assertEquals("love", clean("❤︎ love"))
        assertEquals("love", clean("❤️ love"))
        assertEquals("star", clean("⭐ star"))
        assertEquals("sun", clean("☀ sun"))
        // A symbol the font has stays, its variation selector goes.
        assertEquals("LNB™", clean("LNB™️"))
        assertEquals("© 2026", clean("© 2026"))
        assertEquals("→ ici", clean("→ ici"))
    }

    // ---- Other scripts ----

    @Test
    fun `a run of characters the font lacks becomes one question mark`() {
        assertEquals("? : salut", clean("田中 : salut"))
        assertEquals("Mika: ?!", clean("Mika: こんにちは!"))
        assertEquals("? Mika", clean("مرحبا Mika"))
        assertEquals("? ?", clean("田 中"))
        assertEquals("?a?", clean("田a中"))
    }

    @Test
    fun `marks inside a missing run don't split it`() {
        assertEquals("? x", clean("مَرْحَبًا x"))
        assertEquals("? x", clean("हिंदी x"))
        assertEquals("? x", clean("ภาษาไทย x"))
    }

    @Test
    fun `an emoji inside a missing run doesn't split it`() {
        assertEquals("?", clean("田🎉中"))
    }

    @Test
    fun `scripts the font has are kept`() {
        assertEquals("Привет, Αθήνα, Łódź, Ærøskøbing", clean("Привет, Αθήνα, Łódź, Ærøskøbing"))
        assertEquals("Événement à l'arène — « ouvert » …", clean("Événement à l'arène — « ouvert » …"))
    }

    @Test
    fun `combining accents stay when the font has them, and go alone otherwise`() {
        assertEquals("été", clean("été"))
        assertEquals("ete", BannerText.clean("été", bare))
    }

    @Test
    fun `characters that can't be decoded become a question mark`() {
        assertEquals("a?b", clean("a\uD83Db"))
        assertEquals("a?", clean("a\uDE00"))
        assertEquals("?", clean("")) // private use
    }

    // ---- Spaces and controls ----

    @Test
    fun `line breaks, tabs and controls read as one space`() {
        assertEquals("a b c d e f", clean("a\nb\r\nc\td\u0000e\u0085f"))
        assertEquals("a b", clean("a b"))
        assertEquals("a b", clean("a\u0007\u007F b"))
    }

    @Test
    fun `spaces are collapsed and trimmed`() {
        assertEquals("Mika : ok", clean("  Mika :\tok "))
        assertEquals("a b", clean("a     b"))
        assertEquals("a b", clean("a 　 b"))
    }

    @Test
    fun `no-break spaces the font has stay, the others become spaces`() {
        assertEquals("Mika : ok", clean("Mika : ok"))
        assertEquals("Mika : ok", clean("Mika : ok"))
        assertEquals("Mika : ok", BannerText.clean("Mika : ok", bare))
        assertEquals("Mika : ok", BannerText.clean("Mika : ok", bare))
    }

    @Test
    fun `invisible formatting characters are dropped`() {
        assertEquals("abc", clean("a​b‍c"))
        assertEquals("abc", clean("‎a‏b‮c⁦"))
        assertEquals("abc", clean("﻿abc"))
        assertEquals("abc", clean("a­bc")) // a soft hyphen
    }

    // ---- Nothing left ----

    @Test
    fun `text that is all emoji, spaces or controls becomes empty`() {
        for (text in listOf("", "   ", "\n\t\r", "🎉🎉", " 👍🏽 ", "​‍", "🇫🇷 🇧🇪", "️")) {
            assertEquals("", clean(text), "for ${text.codePoints().toArray().joinToString { Integer.toHexString(it) }}")
        }
    }

    @Test
    fun `text the font can't draw at all becomes a single question mark`() {
        assertEquals("?", clean("こんにちは"))
        assertEquals("?", clean("\uD83D"))
    }

    @Test
    fun `cleaning twice changes nothing`() {
        for (text in listOf("🎉 Party\ntonight 👍🏽", "田中 : salut", "  a   b ", "Événement à l'arène", "é")) {
            val once = clean(text)
            assertEquals(once, clean(once), text)
        }
    }

    @Test
    fun `a long mixed message stays in order`() {
        assertEquals(
            "Lune Garnier : La saison 2 commence ? samedi à 18:00 ! L'End sera réinitialisé",
            clean("Lune Garnier : La saison 2 commence 🎉 季节 samedi à 18:00 ! 🇫🇷\nL'End sera réinitialisé 👍🏽"),
        )
    }
}
