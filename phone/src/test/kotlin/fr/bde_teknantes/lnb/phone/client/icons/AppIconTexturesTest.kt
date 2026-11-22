package fr.bde_teknantes.lnb.phone.client.icons

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The one icon implementation left after the merge (art direction 7): what each icon is composed from, which icons
 * share a texture, and how a glyph image is read. Covers what the shell's own icon code and its tests used to.
 */
class AppIconTexturesTest {
    private val notes = ResourceLocation.fromNamespaceAndPath("test", "textures/gui/notes.png")
    private val other = ResourceLocation.fromNamespaceAndPath("test", "textures/gui/other.png")
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `an item icon is the bare tile, framed, the item drawn over it without a shade`() {
        for (tile in TileColor.entries) {
            val icon = AppIcon.Item(tile, ItemStack(Items.DIAMOND))
            val pixels = AppIconTextures.pixels(icon) { error("an item icon reads no glyph") }
            assertArrayEquals(IconComposer.compose(tile, null), pixels, "$tile")
        }
    }

    @Test
    fun `a glyph icon is composed from its tile and the glyph read from its texture`() {
        val glyph = IntArray(256).also { it[3 * 16 + 4] = white }
        val read = ArrayList<ResourceLocation>()
        val pixels = AppIconTextures.pixels(AppIcon.Glyph(TileColor.PINK, notes)) { read += it; glyph }
        assertEquals(listOf(notes), read)
        assertArrayEquals(IconComposer.compose(TileColor.PINK, glyph), pixels)
    }

    @Test
    fun `a glyph that can't be read leaves the bare tile`() {
        val pixels = AppIconTextures.pixels(AppIcon.Glyph(TileColor.TEAL, notes)) { null }
        assertArrayEquals(IconComposer.compose(TileColor.TEAL, null), pixels)
    }

    @Test
    fun `icons share a texture by tile and glyph, whatever the instance`() {
        val key = AppIconTextures.cacheKey(AppIcon.Glyph(TileColor.AZURE, notes))
        assertEquals(key, AppIconTextures.cacheKey(AppIcon.Glyph(TileColor.AZURE, notes)))
        assertNotEquals(key, AppIconTextures.cacheKey(AppIcon.Glyph(TileColor.RED, notes)))
        assertNotEquals(key, AppIconTextures.cacheKey(AppIcon.Glyph(TileColor.AZURE, other)))
    }

    @Test
    fun `item icons of one tile share the bare tile, whatever the item, and never a glyph icon's texture`() {
        val diamond = AppIconTextures.cacheKey(AppIcon.Item(TileColor.TEAL, ItemStack(Items.DIAMOND)))
        assertEquals(diamond, AppIconTextures.cacheKey(AppIcon.Item(TileColor.TEAL, ItemStack(Items.EMERALD, 3))))
        assertNotEquals(diamond, AppIconTextures.cacheKey(AppIcon.Item(TileColor.GREEN, ItemStack(Items.DIAMOND))))
        assertNotEquals(diamond, AppIconTextures.cacheKey(AppIcon.Glyph(TileColor.TEAL, notes)))
    }

    @Test
    fun `a glyph image is read row by row, from NativeImage's ABGR into ARGB`() {
        // Opaque red at (3, 0), half-transparent blue at (0, 5), as NativeImage stores them (ABGR).
        val abgr = mapOf((3 to 0) to 0xFF0000FF.toInt(), (0 to 5) to 0x80FF0000.toInt())
        val glyph = AppIconTextures.glyphFromImage(16, 16) { x, y -> abgr[x to y] ?: 0 }!!
        assertEquals(256, glyph.size)
        assertEquals(0xFFFF0000.toInt(), glyph[3])
        assertEquals(0x800000FF.toInt(), glyph[5 * 16])
        assertEquals(0, glyph[1])
    }

    @Test
    fun `a glyph image of no usable size is refused`() {
        for ((w, h) in listOf(15 to 16, 16 to 15, 17 to 16, 16 to 17, 8 to 8, 0 to 0, 20 to 20, 32 to 16, 32 to 64, 24 to 24, 16 to 24)) {
            assertNull(AppIconTextures.glyphFromImage(w, h) { _, _ -> white }, "$w x $h")
            assertEquals(AppIconTextures.GlyphFit.REFUSED, AppIconTextures.glyphFit(w, h), "$w x $h")
        }
    }

    @Test
    fun `a square multiple of 16 is sampled down with nearest neighbour`() {
        // 48 x 48: each glyph pixel is the top-left of its 3 x 3 block.
        val glyph = AppIconTextures.glyphFromImage(48, 48) { x, y -> if (x % 3 == 0 && y % 3 == 0) (0xFF000000.toInt() or x or (y shl 8)) else 0 }!!
        assertEquals(AppIconTextures.GlyphFit.SAMPLED, AppIconTextures.glyphFit(48, 48))
        for (gy in 0 until 16) for (gx in 0 until 16) {
            // ABGR in, ARGB out: x lands in the blue channel's place swapped to red.
            assertEquals(0xFF000000.toInt() or ((3 * gx) shl 16) or ((3 * gy) shl 8), glyph[gy * 16 + gx], "($gx, $gy)")
        }
    }

    @Test
    fun `an animated strip 16 wide shows its first frame`() {
        assertEquals(AppIconTextures.GlyphFit.FIRST_FRAME, AppIconTextures.glyphFit(16, 64))
        val glyph = AppIconTextures.glyphFromImage(16, 64) { _, y -> if (y < 16) white else 0 }!!
        assertEquals(true, glyph.all { it == white })
    }

    @Test
    fun `the composer refuses a glyph array one pixel short or long`() {
        assertThrows(IllegalArgumentException::class.java) { IconComposer.compose(TileColor.AZURE, IntArray(255)) }
        assertThrows(IllegalArgumentException::class.java) { IconComposer.compose(TileColor.AZURE, IntArray(257)) }
    }

    @Test
    fun `a soft glyph still gives an opaque icon wherever the tile is`() {
        val soft = IntArray(256) { 0x40FFFFFF }
        val icon = IconComposer.compose(TileColor.ORANGE, soft)
        for (y in 1..18) for (x in 1..18) assertEquals(0xFF, icon[y * 20 + x] ushr 24, "($x, $y)")
    }
}
