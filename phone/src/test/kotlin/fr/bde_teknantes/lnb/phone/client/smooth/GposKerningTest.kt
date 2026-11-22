package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/** Expected values computed independently with fontTools from the same Inter 4.1 files. */
class GposKerningTest {
    private fun load(name: String): GposKerning {
        val bytes = javaClass.getResourceAsStream("/assets/lnbphone/font/$name")!!.use { it.readBytes() }
        return GposKerning(ByteBuffer.wrap(bytes))
    }

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
}
