package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ShelfPackerTest {
    private data class Placed(val x: Int, val y: Int, val w: Int, val h: Int)

    /** Every rectangle inside the page with its padding, and no two rectangles (padding included) overlapping. */
    private fun assertPackedCleanly(placed: List<Placed>, width: Int, height: Int, padding: Int) {
        for (p in placed) {
            assertTrue(p.x >= padding && p.y >= padding, "$p keeps the page's top and left padding")
            assertTrue(p.x + p.w + padding <= width && p.y + p.h + padding <= height, "$p keeps its padding inside the page")
        }
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                val a = placed[i]
                val b = placed[j]
                // Grown by the padding on the right and bottom, the rectangles must still not meet.
                val apart = a.x + a.w + padding <= b.x || b.x + b.w + padding <= a.x ||
                    a.y + a.h + padding <= b.y || b.y + b.h + padding <= a.y
                assertTrue(apart, "$a and $b overlap or touch")
            }
        }
    }

    @Test
    fun `starts at the padding`() {
        assertEquals(1 to 1, ShelfPacker(64, 64).insert(10, 10))
        assertEquals(3 to 3, ShelfPacker(64, 64, padding = 3).insert(10, 10))
        assertEquals(0 to 0, ShelfPacker(64, 64, padding = 0).insert(10, 10))
    }

    @Test
    fun `fills a page with equal glyphs and then reports it full`() {
        val packer = ShelfPacker(64, 64)
        val placed = ArrayList<Placed>()
        while (true) {
            val (x, y) = packer.insert(10, 10) ?: break
            placed += Placed(x, y, 10, 10)
        }
        // 1 + 5 x (10 + 1) = 56 <= 64 each way, a sixth would need 67.
        assertEquals(25, placed.size)
        assertPackedCleanly(placed, 64, 64, 1)
        assertNull(packer.insert(10, 10))
        // Room is left at the right and bottom edges for smaller items.
        assertNotNull(packer.insert(5, 5))
    }

    @Test
    fun `refuses items larger than a page`() {
        val packer = ShelfPacker(64, 32)
        assertNull(packer.insert(63, 10), "wider than the page minus its padding")
        assertNull(packer.insert(10, 31), "taller than the page minus its padding")
        assertNull(packer.insert(1000, 1000))
        // A refused item takes no room.
        assertEquals(1 to 1, packer.insert(62, 30))
        assertNull(packer.insert(1, 1), "the largest item fills the page")
    }

    @Test
    fun `rejects empty sizes`() {
        assertThrows(IllegalArgumentException::class.java) { ShelfPacker(64, 64).insert(0, 5) }
        assertThrows(IllegalArgumentException::class.java) { ShelfPacker(64, 64).insert(5, -1) }
    }

    @Test
    fun `random glyphs and tiles never overlap`() {
        for (seed in 0 until 20) {
            val random = Random(seed)
            val size = listOf(64, 128, 256, 1024)[seed % 4]
            val packer = ShelfPacker(size, size)
            val placed = ArrayList<Placed>()
            var refused = 0
            while (refused < 50) {
                // Mostly glyph-like sizes, some shape tiles and avatars, a few as large as a page.
                val w = when (random.nextInt(10)) {
                    0 -> random.nextInt(1, size + 2)
                    1, 2 -> random.nextInt(1, 100)
                    else -> random.nextInt(1, 30)
                }
                val h = if (random.nextInt(4) == 0) w else random.nextInt(1, 36)
                val spot = packer.insert(w, h)
                if (spot == null) refused++ else placed += Placed(spot.first, spot.second, w, h)
            }
            assertPackedCleanly(placed, size, size, 1)
        }
    }

    @Test
    fun `shorter items share a shelf up to half its height`() {
        val packer = ShelfPacker(256, 256)
        assertEquals(1 to 1, packer.insert(10, 20))
        assertEquals(12 to 1, packer.insert(10, 10), "10 tall fits the 20 tall shelf")
        assertEquals(1 to 22, packer.insert(10, 9), "9 tall would waste more than half: a new shelf")
    }
}
