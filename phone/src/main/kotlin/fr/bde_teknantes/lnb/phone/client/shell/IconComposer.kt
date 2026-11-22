package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.TileColor

/**
 * Composes an app icon's 20 x 20 pixels from its tile colour and 16 x 16 glyph (art direction 7.1), as ARGB, row by
 * row. The outline ring (step 3) is not part of it: the `home/icon_frame` sprite is drawn over the result, and its
 * pixels never meet the glyph's.
 */
internal object IconComposer {
    const val SIZE = 20
    const val GLYPH_SIZE = 16
    const val GLYPH_OFFSET = 2

    /**
     * @param glyph the glyph's 16 x 16 ARGB pixels, row by row, or null for a bare tile (an item is drawn over it, or
     * the glyph couldn't be read).
     */
    fun compose(tile: TileColor, glyph: IntArray?): IntArray {
        require(glyph == null || glyph.size == GLYPH_SIZE * GLYPH_SIZE) { "A glyph is 16 x 16 pixels" }
        val pixels = IntArray(SIZE * SIZE)
        // 1. The tile, inside the 1 px frame.
        for (y in 1..18) for (x in 1..18) pixels[y * SIZE + x] = tile.base
        // 2. Highlight row on top, shade row at the bottom.
        for (x in 2..17) {
            pixels[1 * SIZE + x] = tile.hi
            pixels[18 * SIZE + x] = tile.lo
        }
        if (glyph == null) return pixels
        // 4. Auto-shade: the glyph's opaque pixels one row lower, in the tile's shade, where the glyph is transparent.
        for (gy in 0 until GLYPH_SIZE) for (gx in 0 until GLYPH_SIZE) {
            if (!opaque(glyph, gx, gy)) continue
            val below = gy + 1
            if (below < GLYPH_SIZE && opaque(glyph, gx, below)) continue
            pixels[(GLYPH_OFFSET + below) * SIZE + GLYPH_OFFSET + gx] = tile.lo
        }
        // 5. The glyph at (2, 2), blended over the rest in case an author left soft edges.
        for (gy in 0 until GLYPH_SIZE) for (gx in 0 until GLYPH_SIZE) {
            val index = (GLYPH_OFFSET + gy) * SIZE + GLYPH_OFFSET + gx
            pixels[index] = over(glyph[gy * GLYPH_SIZE + gx], pixels[index])
        }
        return pixels
    }

    // Glyphs should have hard pixels only; for one that doesn't, the half-way mark decides what casts a shade.
    private fun opaque(glyph: IntArray, x: Int, y: Int) = (glyph[y * GLYPH_SIZE + x] ushr 24) >= 128

    /** [top] over the opaque [bottom], straight alpha. */
    private fun over(top: Int, bottom: Int): Int {
        val alpha = top ushr 24
        if (alpha == 255) return top
        if (alpha == 0) return bottom
        fun channel(shift: Int): Int {
            val t = (top shr shift) and 0xFF
            val b = (bottom shr shift) and 0xFF
            return (t * alpha + b * (255 - alpha) + 127) / 255
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
