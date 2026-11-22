package fr.bde_teknantes.lnb.phone.client.icons

import fr.bde_teknantes.lnb.phone.api.client.TileColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

/** App icons composed as art direction 7.1 describes, pixel by pixel. */
class IconComposerTest {
    private val size = IconComposer.SIZE
    private val clear = 0
    private val white = 0xFFFFFFFF.toInt()

    private fun IntArray.at(x: Int, y: Int) = this[y * IconComposer.SIZE + x]

    /** A 16 x 16 glyph with [opaque] pixels (glyph coordinates) in [colour]. */
    private fun glyph(colour: Int = white, vararg opaque: Pair<Int, Int>) =
        IntArray(16 * 16).also { g -> for ((x, y) in opaque) g[y * 16 + x] = colour }

    @Test
    fun `the frame is the generated icon_frame sprite`() {
        // The sprite generator draws the same 2-step ring (2.2) for the home screen: the two must match.
        val image = javaClass.getResourceAsStream("/assets/lnbphone/textures/gui/sprites/home/icon_frame.png")!!.use(ImageIO::read)
        assertEquals(size, image.width)
        assertEquals(size, image.height)
        for (tile in TileColor.entries) {
            val icon = IconComposer.compose(tile, null)
            for (y in 0 until size) for (x in 0 until size) {
                val sprite = image.getRGB(x, y)
                if (sprite ushr 24 != 0) {
                    assertEquals(sprite, icon.at(x, y), "frame pixel ($x, $y) of $tile")
                    assertEquals(IconComposer.OUTLINE, icon.at(x, y))
                } else {
                    // Inside the ring there is tile; outside, nothing.
                    val inside = x in 1..18 && y in 1..18
                    assertEquals(inside, icon.at(x, y) ushr 24 == 0xFF, "pixel ($x, $y) of $tile")
                }
            }
        }
    }

    @Test
    fun `the three pixels outside each corner are transparent`() {
        val icon = IconComposer.compose(TileColor.AZURE, null)
        for ((x, y) in listOf(0 to 0, 1 to 0, 0 to 1, 19 to 0, 18 to 0, 19 to 1, 0 to 19, 1 to 19, 0 to 18, 19 to 19, 18 to 19, 19 to 18)) {
            assertEquals(clear, icon.at(x, y), "($x, $y)")
        }
    }

    @Test
    fun `a bare tile has its base, a hi row on top and a lo row at the bottom`() {
        for (tile in TileColor.entries) {
            val icon = IconComposer.compose(tile, null)
            for (x in 2..17) {
                assertEquals(tile.hi, icon.at(x, 1))
                assertEquals(tile.lo, icon.at(x, 18))
            }
            for (y in 2..17) for (x in 1..18) assertEquals(tile.base, icon.at(x, y), "($x, $y) of $tile")
        }
    }

    @Test
    fun `the tile palette is that of 7_3`() {
        assertEquals(listOf(0xFF7CC0FF.toInt(), 0xFF3A8DF0.toInt(), 0xFF1F5FB0.toInt()), TileColor.AZURE.let { listOf(it.hi, it.base, it.lo) })
        assertEquals(listOf(0xFF5C6372.toInt(), 0xFF3A404B.toInt(), 0xFF23272E.toInt()), TileColor.GRAPHITE.let { listOf(it.hi, it.base, it.lo) })
        assertEquals(0xFF0A0C10.toInt(), IconComposer.OUTLINE)
    }

    @Test
    fun `the glyph sits at (2, 2) and casts lo one pixel down where it is clear`() {
        val tile = TileColor.GREEN
        // A vertical bar of two pixels and a lone pixel.
        val icon = IconComposer.compose(tile, glyph(white, 5 to 5, 5 to 6, 10 to 3))
        assertEquals(white, icon.at(7, 7))
        assertEquals(white, icon.at(7, 8))
        assertEquals(tile.lo, icon.at(7, 9)) // under the bar's foot
        assertEquals(white, icon.at(12, 5))
        assertEquals(tile.lo, icon.at(12, 6))
        // No shade beside or above.
        assertEquals(tile.base, icon.at(8, 9))
        assertEquals(tile.base, icon.at(7, 6))
    }

    @Test
    fun `the shade never covers the glyph itself`() {
        val tile = TileColor.RED
        val ink = 0xFF1B1E23.toInt()
        val icon = IconComposer.compose(tile, glyph(ink, 0 to 0, 0 to 1, 0 to 2))
        assertEquals(ink, icon.at(2, 2))
        assertEquals(ink, icon.at(2, 3))
        assertEquals(ink, icon.at(2, 4))
        assertEquals(tile.lo, icon.at(2, 5))
    }

    @Test
    fun `a glyph's bottom row shades the tile's lo row, and edges stay inside the frame`() {
        val tile = TileColor.TEAL
        val icon = IconComposer.compose(tile, glyph(white, 15 to 15, 0 to 15, 15 to 0))
        assertEquals(white, icon.at(17, 17))
        assertEquals(tile.lo, icon.at(17, 18))
        assertEquals(white, icon.at(2, 17))
        assertEquals(white, icon.at(17, 2))
        assertEquals(tile.lo, icon.at(17, 3))
        // The frame is untouched.
        for (x in 2..17) assertEquals(IconComposer.OUTLINE, icon.at(x, 19))
        for (y in 2..17) assertEquals(IconComposer.OUTLINE, icon.at(19, y))
    }

    @Test
    fun `a full glyph covers the tile and shades only below it`() {
        val tile = TileColor.VIOLET
        val full = IntArray(256) { white }
        val icon = IconComposer.compose(tile, full)
        for (y in 2..17) for (x in 2..17) assertEquals(white, icon.at(x, y))
        for (x in 2..17) assertEquals(tile.lo, icon.at(x, 18))
        // Column 1 and 18 keep the tile.
        for (y in 2..17) {
            assertEquals(tile.base, icon.at(1, y))
            assertEquals(tile.base, icon.at(18, y))
        }
    }

    @Test
    fun `soft glyph pixels are blended over the tile`() {
        val tile = TileColor.AZURE
        val half = 0x80FFFFFF.toInt()
        val icon = IconComposer.compose(tile, glyph(half, 4 to 4))
        val blended = icon.at(6, 6)
        assertEquals(0xFF, blended ushr 24)
        val expected = { shift: Int -> (0xFF * 0x80 + ((tile.base shr shift) and 0xFF) * (255 - 0x80) + 127) / 255 }
        assertEquals(expected(16), (blended shr 16) and 0xFF)
        assertEquals(expected(8), (blended shr 8) and 0xFF)
        assertEquals(expected(0), blended and 0xFF)
    }

    @Test
    fun `half-way alpha decides what casts a shade`() {
        val tile = TileColor.AZURE
        val icon = IconComposer.compose(tile, glyph(0x7FFFFFFF, 3 to 5).also { it[5 * 16 + 8] = 0x80FFFFFF.toInt() })
        // Alpha 127 casts nothing; alpha 128 does.
        assertEquals(tile.base, icon.at(2 + 3, 2 + 6))
        assertEquals(tile.lo, icon.at(2 + 8, 2 + 6))
    }

    @Test
    fun `an empty glyph gives the bare tile`() {
        for (tile in TileColor.entries) {
            assertEquals(IconComposer.compose(tile, null).toList(), IconComposer.compose(tile, IntArray(256)).toList())
        }
    }

    @Test
    fun `a glyph of the wrong size is refused`() {
        assertThrows(IllegalArgumentException::class.java) { IconComposer.compose(TileColor.AZURE, IntArray(20 * 20)) }
        assertThrows(IllegalArgumentException::class.java) { IconComposer.compose(TileColor.AZURE, IntArray(0)) }
    }

    @Test
    fun `the generated glyphs compose without error and keep their pixels`() {
        for (name in listOf("feed", "messages", "quests", "settings", "wallet")) {
            val image = javaClass.getResourceAsStream("/assets/lnbphone/textures/gui/sprites/icon/$name.png")!!.use(ImageIO::read)
            val pixels = IntArray(256) { image.getRGB(it % 16, it / 16) }
            val icon = IconComposer.compose(TileColor.GRAPHITE, pixels)
            for (i in pixels.indices) {
                if (pixels[i] ushr 24 == 0xFF) assertEquals(pixels[i], icon.at(2 + i % 16, 2 + i / 16), "$name pixel $i")
            }
        }
    }
}
