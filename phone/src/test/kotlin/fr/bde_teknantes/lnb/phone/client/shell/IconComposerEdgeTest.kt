package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.TileColor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Art direction 7.1, beyond the developer's cases: every tile, the frame left clear, glyph edge cases. */
class IconComposerEdgeTest {
    private fun IntArray.at(x: Int, y: Int) = this[y * IconComposer.SIZE + x]

    @Test
    fun `every tile colour fills, bands and leaves the outline ring to the frame sprite`() {
        for (tile in TileColor.entries) {
            val icon = IconComposer.compose(tile, null)
            for (y in 0 until 20) for (x in 0 until 20) {
                val expected = when {
                    x == 0 || y == 0 || x == 19 || y == 19 -> 0
                    y == 1 && x in 2..17 -> tile.hi
                    y == 18 && x in 2..17 -> tile.lo
                    else -> tile.base
                }
                assertEquals(expected, icon.at(x, y), "$tile at ($x, $y)")
            }
        }
    }

    @Test
    fun `a fully transparent glyph leaves the bare tile`() {
        for (tile in TileColor.entries) {
            assertArrayEquals(IconComposer.compose(tile, null), IconComposer.compose(tile, IntArray(256)))
        }
    }

    @Test
    fun `a fully opaque glyph covers its 16 x 16 and shades nothing but the bottom band`() {
        val tile = TileColor.AZURE
        val ink = 0xFF1B1E23.toInt()
        val icon = IconComposer.compose(tile, IntArray(256) { ink })
        for (y in 2..17) for (x in 2..17) assertEquals(ink, icon.at(x, y))
        for (x in 2..17) assertEquals(tile.lo, icon.at(x, 18))
        assertEquals(tile.base, icon.at(1, 10))
        assertEquals(tile.base, icon.at(18, 10))
    }

    @Test
    fun `half-way alpha decides what casts a shade, and soft pixels blend over the tile`() {
        val tile = TileColor.AZURE
        val glyph = IntArray(256)
        glyph[5 * 16 + 3] = 0x7FFFFFFF // alpha 127: no shade under it
        glyph[5 * 16 + 8] = 0x80FFFFFF.toInt() // alpha 128: shade under it
        val icon = IconComposer.compose(tile, glyph)
        assertEquals(tile.base, icon.at(2 + 3, 2 + 6))
        assertEquals(tile.lo, icon.at(2 + 8, 2 + 6))
        // White over the tile at 127 / 255, rounded.
        fun blend(top: Int, bottom: Int, alpha: Int) = (top * alpha + bottom * (255 - alpha) + 127) / 255
        val base = tile.base
        val expected = (0xFF shl 24) or
            (blend(0xFF, (base shr 16) and 0xFF, 127) shl 16) or
            (blend(0xFF, (base shr 8) and 0xFF, 127) shl 8) or
            blend(0xFF, base and 0xFF, 127)
        assertEquals(expected, icon.at(2 + 3, 2 + 5))
        // The composed icon is opaque wherever the tile is.
        for (y in 1..18) for (x in 1..18) assertEquals(0xFF, icon.at(x, y) ushr 24, "($x, $y)")
    }

    @Test
    fun `a glyph that isn't 16 x 16 is refused`() {
        assertThrows(IllegalArgumentException::class.java) { IconComposer.compose(TileColor.AZURE, IntArray(255)) }
        assertThrows(IllegalArgumentException::class.java) { IconComposer.compose(TileColor.AZURE, IntArray(400)) }
    }
}
