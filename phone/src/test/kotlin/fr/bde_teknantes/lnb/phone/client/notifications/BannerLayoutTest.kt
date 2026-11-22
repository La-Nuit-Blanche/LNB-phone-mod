package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** The banner's geometry (art direction 10.1 to 10.3) and how its title and text fit. */
class BannerLayoutTest {
    /** Every code point 0.5 em: 6.5 dp in `body` (25 per 164 dp line), 7 dp in `title-m` (23 per line). */
    private val mono = object : FontMetrics {
        override fun advance(codePoint: Int) = 0.5f
        override fun kerning(left: Int, right: Int) = 0f
        override val ascender = 0.8f
        override val descender = 0.2f
    }

    private fun lines(text: String, font: FontMetrics = mono) = BannerLayout.textLines(text, font)

    private fun bodyWidth(line: String, font: FontMetrics) = TextLayout.width(line, font, TextStyles.BODY.size)

    // ---- Geometry against the document ----

    @Test
    fun `the card, icon and text slots are those of 10_1`() {
        assertEquals(240f, BannerLayout.WIDTH)
        assertEquals(76f, BannerLayout.HEIGHT)
        assertEquals(14f, BannerLayout.RADIUS)
        assertEquals(1f, BannerLayout.EDGE)
        assertEquals(12f, BannerLayout.ICON_X)
        assertEquals(18f, BannerLayout.ICON_Y)
        assertEquals(40f, BannerLayout.ICON_SIZE)
        assertEquals(64f, BannerLayout.TEXT_X)
        assertEquals(10f, BannerLayout.TITLE_Y)
        assertEquals(28f, BannerLayout.TEXT_Y)
        assertEquals(228f, BannerLayout.TEXT_X + BannerLayout.TEXT_WIDTH)
        // Two body lines (36 dp) end inside the card.
        assertTrue(BannerLayout.TEXT_Y + BannerLayout.MAX_TEXT_LINES * TextStyles.BODY.lineHeight <= BannerLayout.HEIGHT)
        assertEquals(36f, BannerLayout.MAX_TEXT_LINES * TextStyles.BODY.lineHeight)
    }

    @Test
    fun `the shadow is drawn 8 dp larger on each side, from a 48 dp sprite with a 22 dp border (16_2)`() {
        assertEquals(8f, BannerLayout.SHADOW_MARGIN)
        assertEquals(48f, BannerLayout.SHADOW_SPRITE)
        assertEquals(22f, BannerLayout.SHADOW_BORDER)
    }

    @Test
    fun `the peek banner covers screen sx 4 to 123 and sy 14 to 51 (10_2)`() {
        val gui = { dp: Float -> (dp / 2f).toInt() }
        assertEquals(4, BannerLayout.PEEK_SCREEN_X_GUI)
        assertEquals(123, BannerLayout.PEEK_SCREEN_X_GUI + gui(BannerLayout.WIDTH) - 1)
        assertEquals(14, BannerLayout.PEEK_SCREEN_Y_GUI)
        assertEquals(51, BannerLayout.PEEK_SCREEN_Y_GUI + gui(BannerLayout.HEIGHT) - 1)
    }

    @Test
    fun `over the open phone it rests at app-local (8, 4) and slides from above the viewport (10_3, P-10_2)`() {
        assertEquals(8f, BannerLayout.IN_PHONE_X)
        assertEquals(4f, BannerLayout.IN_PHONE_Y)
        assertEquals(-76f, BannerLayout.inPhoneY(0f))
        assertEquals(-36f, BannerLayout.inPhoneY(0.5f))
        assertEquals(4f, BannerLayout.inPhoneY(1f))
        // Hidden means wholly above the viewport.
        assertTrue(BannerLayout.inPhoneY(0f) + BannerLayout.HEIGHT <= 0f)
    }

    @Test
    fun `a click hits the card, edges included at the top left only`() {
        assertTrue(BannerLayout.contains(0f, 0f))
        assertTrue(BannerLayout.contains(239.9f, 75.9f))
        assertTrue(BannerLayout.contains(120f, 38f))
        assertFalse(BannerLayout.contains(240f, 10f))
        assertFalse(BannerLayout.contains(10f, 76f))
        assertFalse(BannerLayout.contains(-0.1f, 10f))
        assertFalse(BannerLayout.contains(10f, -0.1f))
    }

    // ---- Title ----

    @Test
    fun `a title that fits is kept, a longer one ends with an ellipsis`() {
        assertEquals("a".repeat(23), BannerLayout.title("a".repeat(23), mono))
        assertEquals("a".repeat(22) + "…", BannerLayout.title("a".repeat(24), mono))
        assertEquals("", BannerLayout.title("", mono))
    }

    @Test
    fun `no space is left before the title's ellipsis`() {
        assertEquals("a".repeat(21) + "…", BannerLayout.title("a".repeat(21) + " bbbbb", mono))
    }

    // ---- Text ----

    @Test
    fun `empty text gives no line, short text one`() {
        assertEquals(emptyList<String>(), lines(""))
        assertEquals(listOf("Mika : ok"), lines("Mika : ok"))
    }

    @Test
    fun `text filling exactly two lines has no ellipsis`() {
        val text = "x".repeat(25) + " " + "y".repeat(25)
        assertEquals(listOf("x".repeat(25), "y".repeat(25)), lines(text))
    }

    @Test
    fun `a full second line is shortened to make room for the ellipsis`() {
        val text = "x".repeat(25) + " " + "y".repeat(25) + " z"
        assertEquals(listOf("x".repeat(25), "y".repeat(24) + "…"), lines(text))
    }

    @Test
    fun `a short second line takes the ellipsis as it is`() {
        val text = "x".repeat(25) + " yy " + "z".repeat(25)
        assertEquals(listOf("x".repeat(25), "yy…"), lines(text))
    }

    @Test
    fun `a long unbroken word is cut across lines`() {
        assertEquals(listOf("a".repeat(25), "a".repeat(24) + "…"), lines("a".repeat(80)))
        val url = "https://example.com/" + "segment/".repeat(10)
        val cut = lines("voir $url")
        assertEquals(2, cut.size)
        assertTrue(cut[1].endsWith("…"))
        assertTrue(cut.all { bodyWidth(it, mono) <= BannerLayout.TEXT_WIDTH })
    }

    @Test
    fun `a line never starts with French high punctuation or ends with an opening guillemet`() {
        assertEquals(listOf("x".repeat(20), "yyy : zzz"), lines("x".repeat(20) + " yyy : zzz"))
        assertEquals(listOf("x".repeat(22), "ok !"), lines("x".repeat(22) + " ok !"))
        assertEquals(listOf("x".repeat(20), "« oui » non"), lines("x".repeat(20) + " « oui » non"))
    }

    @Test
    fun `whatever the text, at most two lines, each within 164 dp, cut ones ending with an ellipsis`() {
        val random = Random(1122)
        val words = listOf("a", "Mika", "Laurent", ":", "!", "réinitialisé", "18:00.", "«", "»", "x".repeat(40), "L'End", "à")
        for (font in listOf(mono, InterMetrics.REGULAR)) {
            repeat(300) {
                val text = List(random.nextInt(1, 40)) { words.random(random) }.joinToString(" ")
                val result = lines(text, font)
                assertTrue(result.size in 1..2, text)
                assertTrue(result.all { bodyWidth(it, font) <= BannerLayout.TEXT_WIDTH + 1e-3f }, text)
                val whole = TextLayout.wrap(text, font, TextStyles.BODY.size, BannerLayout.TEXT_WIDTH)
                if (whole.size > 2) assertTrue(result.last().endsWith("…"), text) else assertEquals(whole, result)
            }
        }
    }

    // ---- With Inter's real metrics, against the flows ----

    @Test
    fun `P-10_3's reply fits whole on two lines`() {
        val text = "Mika Laurent : Je l'ai vu près du moulin, côté est !"
        val result = lines(text, InterMetrics.REGULAR)
        assertEquals(2, result.size)
        assertEquals(text, result.joinToString(" "))
    }

    @Test
    fun `P-8_3's and P-11_2's announcements are clamped with an ellipsis`() {
        // The captions show more text than two 164 dp lines hold (see the report); only the start and the clamp are
        // checked here.
        val season = lines("Lune Garnier : La saison 2 commence samedi à 18:00. L'End sera réinitialisé à 17:00.", InterMetrics.REGULAR)
        assertEquals(2, season.size)
        assertTrue(season[0].startsWith("Lune Garnier : La saison"))
        assertTrue(season[1].endsWith("…"))
        val pvp = lines("Kael_Builder : Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn.", InterMetrics.REGULAR)
        assertEquals(2, pvp.size)
        assertTrue(pvp[0].startsWith("Kael_Builder : Événement"))
        assertTrue(pvp[1].endsWith("…"))
    }

    @Test
    fun `the banner titles of 9_10 fit in both languages (13_3)`() {
        for (title in listOf("Nouvelle annonce", "New announcement", "Nouvelle réponse", "New reply")) {
            assertEquals(title, BannerLayout.title(title, InterMetrics.SEMIBOLD))
        }
    }

    @Test
    fun `a 32-character real name still leaves a clamped text (9_2)`() {
        val name = "Marie-Charlotte de La Villemarqu"
        assertEquals(32, name.length)
        val result = lines("$name : Je l'ai vu près du moulin, côté est !", InterMetrics.REGULAR)
        assertEquals(2, result.size)
        assertTrue(result.all { bodyWidth(it, InterMetrics.REGULAR) <= BannerLayout.TEXT_WIDTH + 1e-3f })
    }
}
