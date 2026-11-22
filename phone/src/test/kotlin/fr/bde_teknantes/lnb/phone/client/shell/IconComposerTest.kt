package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.TileColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IconComposerTest {
    private val tile = TileColor.AZURE
    private val white = 0xFFFFFFFF.toInt()

    private fun IntArray.at(x: Int, y: Int) = this[y * IconComposer.SIZE + x]

    @Test
    fun `tile, bands and corners`() {
        val icon = IconComposer.compose(tile, null)
        assertEquals(0, icon.at(0, 0))
        assertEquals(tile.base, icon.at(1, 1))
        assertEquals(tile.hi, icon.at(2, 1))
        assertEquals(tile.hi, icon.at(17, 1))
        assertEquals(tile.base, icon.at(18, 1))
        assertEquals(tile.lo, icon.at(2, 18))
        assertEquals(tile.base, icon.at(10, 10))
        assertEquals(0, icon.at(19, 10))
    }

    @Test
    fun `glyph at (2, 2) with its shade one row lower where it is transparent`() {
        val glyph = IntArray(16 * 16)
        // A 2-pixel-tall column at glyph (5, 3..4).
        glyph[3 * 16 + 5] = white
        glyph[4 * 16 + 5] = white
        val icon = IconComposer.compose(tile, glyph)
        assertEquals(white, icon.at(7, 5))
        assertEquals(white, icon.at(7, 6))
        // Shade under the bottom pixel only, not under the top one (covered by the glyph itself).
        assertEquals(tile.lo, icon.at(7, 7))
        assertEquals(tile.base, icon.at(7, 4))
        assertEquals(tile.base, icon.at(8, 7))
    }

    @Test
    fun `the shade of the glyph's last row lands on the tile's shade row`() {
        val glyph = IntArray(16 * 16)
        glyph[15 * 16 + 0] = white
        val icon = IconComposer.compose(tile, glyph)
        assertEquals(white, icon.at(2, 17))
        assertEquals(tile.lo, icon.at(2, 18))
    }
}
