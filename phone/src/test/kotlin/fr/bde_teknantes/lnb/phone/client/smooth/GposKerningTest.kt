package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.smooth.GposFontBuilder.Classes
import fr.bde_teknantes.lnb.phone.client.smooth.GposFontBuilder.Lookup
import fr.bde_teknantes.lnb.phone.client.smooth.GposFontBuilder.Pairs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/** Expected values computed independently with fontTools from the same Inter 4.1 files. */
class GposKerningTest {
    private fun load(name: String): GposKerning {
        val bytes = javaClass.getResourceAsStream("/assets/lnbphone/font/$name")!!.use { it.readBytes() }
        return GposKerning(ByteBuffer.wrap(bytes))
    }

    private fun kerning(bytes: ByteArray) = GposKerning(ByteBuffer.wrap(bytes))

    @Test
    fun `reads Inter Regular pair kerning, including the lookup wrapped in an extension`() {
        val kerning = load("inter_regular.ttf")
        assertFalse(kerning.isEmpty)
        assertEquals(-160, kerning.kerning(411, 790)) // T o
        assertEquals(-140, kerning.kerning(2, 456)) // A V
        assertEquals(-104, kerning.kerning(456, 507)) // V a
        assertEquals(-128, kerning.kerning(852, 1502)) // r .
        assertEquals(-197, kerning.kerning(281, 411)) // L T
        assertEquals(-157, kerning.kerning(472, 790)) // Y o
        assertEquals(0, kerning.kerning(507, 578)) // a b
    }

    @Test
    fun `reads Inter SemiBold pair kerning`() {
        val kerning = load("inter_semibold.ttf")
        assertEquals(-154, kerning.kerning(2, 456)) // A V
        assertEquals(-202, kerning.kerning(472, 790)) // Y o
        assertEquals(-49, kerning.kerning(647, 618)) // f é
    }

    // ---- Against HarfBuzz ----

    /**
     * Kerning by character, as HarfBuzz 0.57 (uharfbuzz) shapes each pair with only `kern` on: the first glyph's
     * shaped advance minus its hmtx advance. Includes French pairs (accents, guillemets).
     */
    private val harfBuzzRegular = mapOf(
        "To" to -160, "AV" to -140, "Va" to -104, "r." to -128, "LT" to -197, "Yo" to -157, "Te" to -160,
        "Vo" to -104, "fé" to -60, "Ty" to -128, "P," to -81, "W." to -197, "y." to -90, "Av" to -139, "ab" to 0,
        "«T" to -104, "T»" to -104, "LV" to -139, "rô" to -34,
    )
    private val harfBuzzSemiBold = mapOf(
        "To" to -160, "AV" to -154, "Va" to -116, "r." to -128, "LT" to -190, "Yo" to -202, "Te" to -160,
        "Vo" to -118, "fé" to -49, "Ty" to -128, "P," to -81, "W." to -197, "y." to -83, "Av" to -139, "ab" to 0,
        "«T" to -104, "T»" to -104, "LV" to -152, "rô" to -45,
    )

    @Test
    fun `matches HarfBuzz on known pairs in both weights`() {
        for ((font, expected) in listOf(InterMetrics.REGULAR to harfBuzzRegular, InterMetrics.SEMIBOLD to harfBuzzSemiBold)) {
            for ((pair, value) in expected) {
                val left = font.glyphId(pair.codePointAt(0))
                val right = font.glyphId(pair.codePointAt(1))
                assertEquals(value, font.gpos.kerning(left, right), "kerning of \"$pair\"")
            }
        }
    }

    @Test
    fun `unknown glyphs and glyph ids past the font kern by nothing`() {
        val kerning = load("inter_regular.ttf")
        assertEquals(0, kerning.kerning(0, 0))
        assertEquals(0, kerning.kerning(65535, 65535))
        assertEquals(0, kerning.kerning(-1, 411))
        assertEquals(0, kerning.kerning(411, Int.MAX_VALUE))
    }

    // ---- Hand-made tables ----

    @Test
    fun `reads explicit pairs`() {
        val k = kerning(GposFontBuilder.font(Lookup(listOf(Pairs(mapOf(3 to listOf(5 to -50, 7 to 20), 9 to listOf(3 to -1)))))))
        assertFalse(k.isEmpty)
        assertEquals(-50, k.kerning(3, 5))
        assertEquals(20, k.kerning(3, 7))
        assertEquals(-1, k.kerning(9, 3))
        assertEquals(0, k.kerning(3, 6))
        assertEquals(0, k.kerning(5, 3))
    }

    @Test
    fun `reads the X advance among other value record fields`() {
        // X placement and Y placement before the X advance, Y advance after it, and a second-glyph record too.
        val pairs = Pairs(mapOf(3 to listOf(5 to -50, 8 to 12)), valueFormat1 = 0x1 or 0x2 or 0x4 or 0x8, valueFormat2 = 0x1 or 0x4)
        val k = kerning(GposFontBuilder.font(Lookup(listOf(pairs))))
        assertEquals(-50, k.kerning(3, 5))
        assertEquals(12, k.kerning(3, 8))
    }

    @Test
    fun `a value record without X advance kerns by nothing`() {
        val k = kerning(GposFontBuilder.font(Lookup(listOf(Pairs(mapOf(3 to listOf(5 to -50)), valueFormat1 = 0x1)))))
        assertEquals(0, k.kerning(3, 5))
    }

    @Test
    fun `reads class pairs`() {
        val classes = Classes(
            coverage = listOf(10, 11, 12),
            firstClasses = mapOf(10 to 1, 11 to 1), // 12 stays in class 0
            secondClasses = mapOf(20 to 1, 21 to 2),
            values = arrayOf(intArrayOf(0, -5, -6), intArrayOf(0, -30, 40)),
        )
        val k = kerning(GposFontBuilder.font(Lookup(listOf(classes))))
        assertEquals(-30, k.kerning(10, 20))
        assertEquals(40, k.kerning(11, 21))
        assertEquals(-5, k.kerning(12, 20))
        assertEquals(0, k.kerning(10, 22)) // second glyph in class 0
        assertEquals(0, k.kerning(13, 20)) // first glyph not covered
    }

    @Test
    fun `reads lookups wrapped in extensions`() {
        val k = kerning(GposFontBuilder.font(Lookup(listOf(Pairs(mapOf(3 to listOf(5 to -77)))), extension = true)))
        assertEquals(-77, k.kerning(3, 5))
    }

    @Test
    fun `within a lookup the first subtable that applies wins, and lookups add up`() {
        val first = Pairs(mapOf(3 to listOf(5 to -10)))
        val second = Pairs(mapOf(3 to listOf(5 to -99, 6 to -20)))
        val k = kerning(GposFontBuilder.font(Lookup(listOf(first, second)), Lookup(listOf(Pairs(mapOf(3 to listOf(5 to -1)))))))
        assertEquals(-11, k.kerning(3, 5))
        // The first subtable covers 3 but has no pair with 6: the second subtable applies.
        assertEquals(-20, k.kerning(3, 6))
    }

    @Test
    fun `a class subtable that covers the first glyph ends its lookup even at zero`() {
        // OpenType: a format 2 subtable applies to every covered pair, so the explicit pair after it is never reached.
        val classes = Classes(listOf(3), mapOf(3 to 0), mapOf(5 to 1), arrayOf(intArrayOf(0, 0)))
        val k = kerning(GposFontBuilder.font(Lookup(listOf(classes, Pairs(mapOf(3 to listOf(5 to -40)))))))
        assertEquals(0, k.kerning(3, 5))
    }

    @Test
    fun `ignores lookups of other features and fonts without GPOS`() {
        val pairs = Lookup(listOf(Pairs(mapOf(3 to listOf(5 to -50)))))
        val other = kerning(GposFontBuilder.font(listOf(pairs), featureTag = "dist"))
        assertTrue(other.isEmpty)
        assertEquals(0, other.kerning(3, 5))
        val none = kerning(GposFontBuilder.fontWithoutGpos())
        assertTrue(none.isEmpty)
        assertEquals(0, none.kerning(3, 5))
    }

    @Test
    fun `reads a font buffer from its start whatever its position`() {
        val bytes = GposFontBuilder.font(Lookup(listOf(Pairs(mapOf(3 to listOf(5 to -50))))))
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(bytes.size / 2)
        assertEquals(-50, GposKerning(buffer).kerning(3, 5))
        assertEquals(bytes.size / 2, buffer.position(), "the caller's buffer is left as it was")
    }
}
