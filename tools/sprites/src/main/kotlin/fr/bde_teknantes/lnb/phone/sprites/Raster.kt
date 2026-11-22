package fr.bde_teknantes.lnb.phone.sprites

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.roundToInt

// Anti-aliased rasterising for the smooth set. Shapes are drawn by Java2D at SUPERSAMPLE times the size and averaged
// down, which evens out the renderer's own coverage steps. Everything here is plain arithmetic on the same inputs, so
// two runs give the same pixels.

private const val SUPERSAMPLE = 4

/** A round-capped, round-joined stroke, the only kind the line icons use (8.6). */
fun roundStroke(width: Double) = BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

/**
 * The coverage, 0 to 1, of each pixel of a [width] x [height] image by what [draw] paints. [draw] works in pixels of
 * the final image, with white as the current colour.
 */
fun coverage(width: Int, height: Int, draw: (Graphics2D) -> Unit): DoubleArray {
    val big = BufferedImage(width * SUPERSAMPLE, height * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB)
    val g = big.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    // PURE: no stroke normalisation, so a stroke lands exactly where its numbers say.
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.scale(SUPERSAMPLE.toDouble(), SUPERSAMPLE.toDouble())
    g.color = Color.WHITE
    draw(g)
    g.dispose()

    val pixels = big.getRGB(0, 0, big.width, big.height, null, 0, big.width)
    val result = DoubleArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        var sum = 0
        for (sy in 0 until SUPERSAMPLE) for (sx in 0 until SUPERSAMPLE) {
            sum += pixels[(y * SUPERSAMPLE + sy) * big.width + x * SUPERSAMPLE + sx] ushr 24
        }
        result[y * width + x] = sum / (255.0 * SUPERSAMPLE * SUPERSAMPLE)
    }
    return result
}

/** Paints [top] over [bottom], both coverages, as one colour: the alpha of the result. */
fun over(top: DoubleArray, bottom: DoubleArray): DoubleArray =
    DoubleArray(top.size) { top[it] + bottom[it] * (1 - top[it]) }

/** Separable Gaussian blur with standard deviation [sigma] pixels; outside the image counts as empty. */
fun gaussianBlur(values: DoubleArray, width: Int, height: Int, sigma: Double): DoubleArray {
    val radius = ceil(4 * sigma).toInt()
    val kernel = DoubleArray(2 * radius + 1) { StrictMath.exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma)) }
    val total = kernel.sum()
    for (i in kernel.indices) kernel[i] /= total

    fun pass(source: DoubleArray, horizontal: Boolean): DoubleArray = DoubleArray(source.size) { index ->
        val x = index % width
        val y = index / width
        var sum = 0.0
        for (k in -radius..radius) {
            val sx = if (horizontal) x + k else x
            val sy = if (horizontal) y else y + k
            if (sx in 0 until width && sy in 0 until height) sum += source[sy * width + sx] * kernel[k + radius]
        }
        sum
    }
    return pass(pass(values, horizontal = true), horizontal = false)
}

/**
 * An image of one colour whose alpha is [alpha] times [opacity]. Transparent pixels keep the colour too, so linear
 * filtering never blends a dark fringe into the edge of a white shape.
 */
fun maskImage(width: Int, height: Int, alpha: DoubleArray, rgb: Int, opacity: Double = 1.0): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val pixels = IntArray(width * height) {
        val a = (alpha[it].coerceIn(0.0, 1.0) * opacity * 255).roundToInt()
        (a shl 24) or (rgb and 0xFFFFFF)
    }
    image.setRGB(0, 0, width, height, pixels, 0, width)
    return image
}
