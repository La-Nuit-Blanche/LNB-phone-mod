package fr.bde_teknantes.lnb.phone.client.icons

import fr.bde_teknantes.lnb.phone.api.client.TileColor

/**
 * Composes an app icon the way art direction 7.1 says the phone does, as plain pixel maths: a 20 x 20 ARGB image
 * from a tile colour and a 16 x 16 glyph. Kept apart from textures so it can be tested pixel by pixel.
 */
object IconComposer {
    const val SIZE = 20
    const val GLYPH_SIZE = 16
    private const val GLYPH_AT = 2

    /** `outline` of the shell palette (3). */
    const val OUTLINE = 0xFF0A0C10.toInt()

    /**
     * The icon for [tile] and [glyph] (16 x 16 ARGB, row by row), or the bare tile when [glyph] is null (an app whose
     * glyph is an item drawn on top, or whose picture couldn't be read). Returns 20 x 20 ARGB, row by row; the three
     * pixels outside each 2-step corner are transparent.
     */
    fun compose(tile: TileColor, glyph: IntArray?): IntArray {
        require(glyph == null || glyph.size == GLYPH_SIZE * GLYPH_SIZE) { "A glyph is 16 x 16 pixels" }
        val out = IntArray(SIZE * SIZE)
        // 1. Tile body, then 2. its highlight and shade rows.
        for (y in 1..18) for (x in 1..18) out[y * SIZE + x] = tile.base
        for (x in 2..17) {
            out[1 * SIZE + x] = tile.hi
            out[18 * SIZE + x] = tile.lo
        }
        // 3. Frame: a 1 px ring whose corners step in twice.
        for (x in 2..17) {
            out[x] = OUTLINE
            out[19 * SIZE + x] = OUTLINE
        }
        for (y in 2..17) {
            out[y * SIZE] = OUTLINE
            out[y * SIZE + 19] = OUTLINE
        }
        for ((x, y) in listOf(1 to 1, 18 to 1, 1 to 18, 18 to 18)) out[y * SIZE + x] = OUTLINE
        if (glyph == null) return out

        // 4. Auto-shade: each opaque glyph pixel casts tile lo one pixel down, where the glyph itself isn't opaque.
        for (gy in 0 until GLYPH_SIZE) for (gx in 0 until GLYPH_SIZE) {
            if (!opaque(glyph, gx, gy) || (gy + 1 < GLYPH_SIZE && opaque(glyph, gx, gy + 1))) continue
            out[(GLYPH_AT + gy + 1) * SIZE + GLYPH_AT + gx] = tile.lo
        }
        // 5. The glyph over all of it.
        for (gy in 0 until GLYPH_SIZE) for (gx in 0 until GLYPH_SIZE) {
            val i = (GLYPH_AT + gy) * SIZE + GLYPH_AT + gx
            out[i] = over(glyph[gy * GLYPH_SIZE + gx], out[i])
        }
        return out
    }

    // Glyphs should have hard pixels only; for one that doesn't, the half-way mark decides what casts a shade, so a
    // faint fringe left by an export doesn't drag a shade line under the whole shape.
    private fun opaque(glyph: IntArray, x: Int, y: Int) = (glyph[y * GLYPH_SIZE + x] ushr 24) >= 128

    /** [top] over the opaque [bottom] (glyphs should be hard pixels; a soft one is blended rather than dropped). */
    private fun over(top: Int, bottom: Int): Int {
        val a = top ushr 24
        if (a == 255) return top
        if (a == 0) return bottom
        fun channel(shift: Int) = (((top shr shift) and 0xFF) * a + ((bottom shr shift) and 0xFF) * (255 - a) + 127) / 255
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
