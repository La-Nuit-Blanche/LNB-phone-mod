package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkinReadinessTest {
    private val steve = IntArray(64) { 0xFF000000.toInt() or (it * 3) }
    private val alex = IntArray(64) { 0xFF000000.toInt() or (0x100000 + it) }
    private val second = 1_000_000_000L

    @Test
    fun `a real skin is ready at once`() {
        assertTrue(SkinReadiness.isReady(alex, steve, 0))
    }

    @Test
    fun `pixels not uploaded yet are not ready`() {
        assertFalse(SkinReadiness.isReady(IntArray(64), steve, 0), "an empty texture reads as transparent")
        assertFalse(SkinReadiness.isReady(alex.copyOf().also { it[10] = it[10] and 0x00FFFFFF }, steve, 0))
        assertFalse(SkinReadiness.isReady(null, steve, 0), "not skin-sized")
    }

    @Test
    fun `Steve's face for another skin is the placeholder until patience runs out`() {
        assertFalse(SkinReadiness.isReady(steve.copyOf(), steve, 0))
        assertFalse(SkinReadiness.isReady(steve.copyOf(), steve, 9 * second))
        assertTrue(SkinReadiness.isReady(steve.copyOf(), steve, 11 * second), "a player who really wears Steve's face")
        assertTrue(SkinReadiness.isReady(steve.copyOf(), null, 0), "Steve's own skin")
    }

    @Test
    fun `the hat goes over the face and the face keeps its alpha`() {
        val base = intArrayOf(0xFF102030.toInt(), 0x00102030, 0xFF000000.toInt())
        val hat = intArrayOf(0x00FFFFFF, 0xFFAABBCC.toInt(), 0x80FFFFFF.toInt())
        val out = SkinReadiness.composite(base, hat)
        assertArrayEquals(intArrayOf(0xFF102030.toInt(), 0x00AABBCC, 0xFF808080.toInt()), out)
        // An unloaded face stays transparent whatever the hat, so it is not mistaken for a loaded one.
        assertFalse(SkinReadiness.isReady(SkinReadiness.composite(IntArray(64), IntArray(64) { -1 }), steve, 0))
    }
}
