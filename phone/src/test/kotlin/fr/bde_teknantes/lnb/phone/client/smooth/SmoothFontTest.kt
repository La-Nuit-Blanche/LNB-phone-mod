package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.system.MemoryUtil

/** Runs FreeType for real (Minecraft's LWJGL build), on the bundled Inter and on broken files. */
class SmoothFontTest {
    private fun memory(bytes: ByteArray) = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()

    @Test
    fun `opens the bundled Inter and rasterises gray glyphs`() {
        SmoothFont.fromMemory(memory(InterMetrics.bytes("inter_regular.ttf")), "Inter").use { font ->
            assertEquals(InterMetrics.REGULAR.advance('A'.code), font.advance('A'.code), 1e-6f)
            val glyph = font.rasterize('A'.code, 19.5f, 0f, SmoothFont.Hinting.LIGHT)
            assertNotNull(glyph)
            glyph!!
            assertTrue(glyph.width in 8..16 && glyph.height in 10..18, "${glyph.width}x${glyph.height}")
            assertTrue(glyph.coverage.any { (it.toInt() and 0xFF) == 255 }, "solid stem pixels")
            assertNull(font.rasterize(' '.code, 19.5f, 0f, SmoothFont.Hinting.LIGHT), "a space has no pixels")
        }
    }

    @Test
    fun `a glyph Inter lacks is the font's missing-glyph box, glyph 0`() {
        SmoothFont.fromMemory(memory(InterMetrics.bytes("inter_regular.ttf")), "Inter").use { font ->
            assertEquals(0, font.glyphId(0x1F600)) // an emoji
            assertEquals(0, font.glyphId(0x4E2D)) // a CJK ideograph
        }
    }

    @Test
    fun `a file that is not a font fails cleanly, every time`() {
        repeat(3) {
            assertThrows(IllegalStateException::class.java) { SmoothFont.fromMemory(memory(ByteArray(1000) { 7 }), "garbage") }
        }
        // A real font header cut short.
        val cut = InterMetrics.bytes("inter_regular.ttf").copyOf(300)
        assertThrows(Exception::class.java) { SmoothFont.fromMemory(memory(cut), "cut Inter") }
    }
}
