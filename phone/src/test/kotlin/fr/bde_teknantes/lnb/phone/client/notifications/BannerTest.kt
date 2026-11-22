package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerTest {
    /** Every code point 0.5 em wide: 6.5 dp at the body size, so 25 characters per 164 dp line. */
    private val mono = object : FontMetrics {
        override fun advance(codePoint: Int) = 0.5f
        override fun kerning(left: Int, right: Int) = 0f
        override val ascender = 0.8f
        override val descender = 0.2f
    }

    @Test
    fun `wraps the text on two lines and ends the second with an ellipsis`() {
        assertEquals(listOf("short"), BannerLayout.textLines("short", mono))
        val lines = BannerLayout.textLines("aaaa bbbb cccc dddd eeee ffff gggg hhhh iiii jjjj kkkk llll mmmm", mono)
        assertEquals(listOf("aaaa bbbb cccc dddd eeee", "ffff gggg hhhh iiii jjjj…"), lines)
        assertEquals(emptyList<String>(), BannerLayout.textLines("", mono))
    }

    @Test
    fun `slides in, holds, slides out, and restarts its hold`() {
        val timeline = BannerTimeline(BannerMotion.IN_PHONE, 0.0)
        assertEquals(0f, timeline.progress(0.0))
        assertEquals(0.75f, timeline.progress(75.0))
        assertEquals(1f, timeline.progress(150.0))
        assertTrue(timeline.isHolding(2000.0))
        assertEquals(0.75f, timeline.progress(4150.0 + 75.0))
        timeline.restart(4150.0 + 75.0)
        assertEquals(0.75f, timeline.progress(4150.0 + 75.0), 1e-4f)
        assertEquals(1f, timeline.progress(4300.0))
        assertTrue(timeline.isFinished(4225.0 + 150.0 + 4000.0 + 150.0))
        assertEquals(-36f, BannerLayout.inPhoneY(0.5f))
    }

    @Test
    fun `drops emoji and marks what the font lacks`() {
        val latin = { cp: Int -> cp < 0x2000 }
        assertEquals("Party tonight", BannerText.clean("🎉 Party\ntonight 👍🏽", latin))
        assertEquals("? : salut", BannerText.clean("田中 : salut", latin))
        assertEquals("Mika : ok", BannerText.clean("  Mika :\tok ", latin))
    }
}
