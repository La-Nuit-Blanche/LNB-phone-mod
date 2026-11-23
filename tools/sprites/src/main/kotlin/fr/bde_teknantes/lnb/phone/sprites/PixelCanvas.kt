package fr.bde_teknantes.lnb.phone.sprites

import java.awt.image.BufferedImage

/** A grid of ARGB pixels for the pixel shell, where 1 pixel = 1 GUI px. Coordinates and ranges are inclusive. */
class PixelCanvas(val width: Int, val height: Int) {
    private val pixels = IntArray(width * height)

    operator fun get(x: Int, y: Int): Int = pixels[index(x, y)]

    operator fun set(x: Int, y: Int, colour: Int) {
        pixels[index(x, y)] = colour
    }

    /** Fills `x0..x1`, `y0..y1`, both inclusive, the way the art direction writes ranges. */
    fun fill(x0: Int, y0: Int, x1: Int, y1: Int, colour: Int) {
        for (y in y0..y1) for (x in x0..x1) this[x, y] = colour
    }

    fun fill(colour: Int) = pixels.fill(colour)

    /**
     * Paints an ASCII grid as the art direction prints it, its top-left at (x, y). Spaces are ignored, so grids can
     * be pasted with or without them; a character missing from [inks] leaves the pixel as it is.
     */
    fun grid(x: Int, y: Int, rows: List<String>, inks: Map<Char, Int>) {
        rows.forEachIndexed { dy, row ->
            row.filterNot { it == ' ' }.forEachIndexed { dx, char ->
                inks[char]?.let { this[x + dx, y + dy] = it }
            }
        }
    }

    fun toImage(): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return image
    }

    private fun index(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) { "($x, $y) is outside $width x $height" }
        return y * width + x
    }
}

/** Splits a grid pasted as one multi-line string into rows, dropping blank lines and indentation. */
fun rows(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
