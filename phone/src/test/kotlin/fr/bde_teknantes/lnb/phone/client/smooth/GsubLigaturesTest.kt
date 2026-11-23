package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Duration
import kotlin.random.Random

/** The GSUB ligature reader on the bundled emoji font, and on broken copies of it. */
class GsubLigaturesTest {
    private val bytes = InterMetrics.bytes("noto_emoji.ttf")

    @Test
    fun `reads the emoji font's ligatures, longest sequences included`() {
        val gsub = GsubLigatures(ByteBuffer.wrap(bytes))
        assertTrue(gsub.size > 3000)
        val noto = NotoEmojiMetrics.NOTO
        // A kiss with two skin tones: 10 components, all composed into one glyph.
        val kiss = "🧑🏻‍❤️‍💋‍🧑🏼".codePoints().filter { it != 0xFE0F }.map { noto.glyph(it) }.toArray()
        val found = gsub.ligature(kiss, 0, kiss.size)
        assertEquals(kiss.size.toLong(), found and 0xFFFF)
        // Out of range, or nothing there.
        assertEquals(0L, gsub.ligature(kiss, kiss.size, kiss.size))
        assertEquals(0L, gsub.ligature(intArrayOf(noto.glyph(0x1F600)), 0, 1))
    }

    @Test
    fun `a font without GSUB, or a corrupted one, has no ligatures and never throws`() {
        assertTrue(GsubLigatures(ByteBuffer.wrap(InterMetrics.bytes("inter_regular.ttf").copyOf(64))).isEmpty)
        val (offset, length) = GposKerning.findTable(ByteBuffer.wrap(bytes), "GSUB")!!
        val random = Random(13)
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            repeat(300) {
                val copy = bytes.copyOf()
                repeat(1 + random.nextInt(40)) { copy[offset + random.nextInt(length)] = random.nextInt(256).toByte() }
                val gsub = GsubLigatures(ByteBuffer.wrap(copy))
                assertTrue(gsub.workUsed <= gsub.workAllowed)
                gsub.ligature(intArrayOf(1, 2, 3, 4), 0, 4)
            }
        }
    }
}
