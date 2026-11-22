package fr.bde_teknantes.lnb.phone.sprites

import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D

// Section 16.2: the smooth set, white shapes and line icons tinted at draw time, and two black shadows. Sizes are in
// dp; SmoothFormat turns them into pixels and metadata, so the set can be re-exported in another format.

/**
 * How the smooth set is exported. The art direction asks for 2 px per dp in `textures/gui/smooth/`, drawn with
 * linear filtering and no mipmaps; the rendering spike may settle on something else, which is a change here only.
 */
data class SmoothFormat(
    val pxPerDp: Double = 2.0,
    val dir: String = "smooth",
    /** Writes `"texture": {"blur": true, "clamp": true}`: vanilla's switch for linear filtering of a texture. */
    val linearFiltering: Boolean = true,
    /** Writes the nine-slice border in vanilla's `gui.scaling` format, with width, height and border in dp. */
    val nineSliceMeta: Boolean = true,
) {
    fun px(dp: Number): Int {
        val px = dp.toDouble() * pxPerDp
        require(px == Math.rint(px)) { "$dp dp is not a whole number of pixels at $pxPerDp px per dp" }
        return px.toInt()
    }

    fun mcmeta(sizeDp: Int, nineSliceBorderDp: Int?): String? {
        val sections = mutableListOf<String>()
        if (linearFiltering) sections += """"texture": {"blur": true, "clamp": true}"""
        if (nineSliceMeta && nineSliceBorderDp != null) {
            sections += """"gui": {"scaling": {"type": "nine_slice", "width": $sizeDp, "height": $sizeDp, """ +
                """"border": $nineSliceBorderDp}}"""
        }
        return if (sections.isEmpty()) null else sections.joinToString(", ", "{", "}\n")
    }
}

private val WHITE = rgb("#FFFFFF")
private val BLACK = rgb("#000000")

fun smoothSprites(format: SmoothFormat = SmoothFormat()): List<Sprite> =
    shapes(format) + shadows(format) + icons(format)

// --- Shapes ----------------------------------------------------------------------------------------------------------

/** A filled rounded rect of [size] dp, or the ring of [stroke] dp just inside it. */
private fun roundedSquare(size: Double, radius: Double, stroke: Double?): Shape {
    val outer = RoundRectangle2D.Double(0.0, 0.0, size, size, 2 * radius, 2 * radius)
    if (stroke == null) return outer
    val inner = RoundRectangle2D.Double(
        stroke, stroke, size - 2 * stroke, size - 2 * stroke, 2 * (radius - stroke), 2 * (radius - stroke),
    )
    return Area(outer).apply { subtract(Area(inner)) }
}

private fun circle(cx: Double, cy: Double, r: Double) = Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r)

private fun disc(size: Double, stroke: Double?): Shape {
    val outer = circle(size / 2, size / 2, size / 2)
    if (stroke == null) return outer
    return Area(outer).apply { subtract(Area(circle(size / 2, size / 2, size / 2 - stroke))) }
}

private class ShapeSpec(val name: String, val sizeDp: Int, val nineSliceDp: Int?, val shape: Shape)

private val SHAPES = listOf(
    ShapeSpec("r10", 24, 10, roundedSquare(24.0, 10.0, null)),
    ShapeSpec("r10_stroke1", 24, 10, roundedSquare(24.0, 10.0, 1.0)),
    ShapeSpec("r10_stroke2", 24, 10, roundedSquare(24.0, 10.0, 2.0)),
    ShapeSpec("r14", 32, 14, roundedSquare(32.0, 14.0, null)),
    ShapeSpec("r14_stroke1", 32, 14, roundedSquare(32.0, 14.0, 1.0)),
    ShapeSpec("pill", 48, 24, disc(48.0, null)),
    ShapeSpec("pill_stroke1", 48, 24, disc(48.0, 1.0)),
    ShapeSpec("pill_stroke2", 48, 24, disc(48.0, 2.0)),
    // Scaled to 16, 32 or 48 dp by the code, never nine-sliced.
    ShapeSpec("avatar_mask", 48, null, disc(48.0, null)),
)

private fun shapes(format: SmoothFormat): List<Sprite> = SHAPES.map { spec ->
    val px = format.px(spec.sizeDp)
    val alpha = coverage(px, px) { g ->
        g.scale(format.pxPerDp, format.pxPerDp)
        g.fill(spec.shape)
    }
    Sprite("${format.dir}/shape/${spec.name}", maskImage(px, px, alpha, WHITE), format.mcmeta(spec.sizeDp, spec.nineSliceDp))
}

// --- Shadows ---------------------------------------------------------------------------------------------------------

/** CSS's `blur` is twice the standard deviation of the Gaussian, and the mockups drew these as CSS box-shadows. */
private const val SHADOW_BLUR_DP = 8.0
private const val SHADOW_OPACITY = 0.45

/** The sheet shadow serves radii 10 to 14 (banner, snackbar, menu, dialog); one radius in between does for all. */
private const val SHEET_RADIUS_DP = 12.0

/** How far the sheet sprite reaches past its element on each side; the code draws it this much larger. */
private const val SHEET_MARGIN_DP = 8.0
private const val SHEET_OFFSET_DP = 2.0

private fun shadows(format: SmoothFormat): List<Sprite> {
    // FAB: a 48 dp circle in the middle of 72 dp. The 2 dp drop is applied when drawing ("offset 2 dp down", 8.7).
    val fab = shadow(format, 72) { circle(36.0, 36.0, 24.0) }
    // Sheet: an element drawn 8 dp inside the sprite on each side, its shadow 2 dp lower, baked in so the sprite
    // can be drawn exactly 8 dp larger than the element (16.2).
    val sheet = shadow(format, 48) {
        val side = 48 - 2 * SHEET_MARGIN_DP
        RoundRectangle2D.Double(
            SHEET_MARGIN_DP, SHEET_MARGIN_DP + SHEET_OFFSET_DP, side, side, 2 * SHEET_RADIUS_DP, 2 * SHEET_RADIUS_DP,
        )
    }
    return listOf(
        Sprite("${format.dir}/shadow/fab", fab, format.mcmeta(72, null)),
        Sprite("${format.dir}/shadow/sheet", sheet, format.mcmeta(48, 22)),
    )
}

private fun shadow(format: SmoothFormat, sizeDp: Int, shape: () -> Shape) = format.px(sizeDp).let { px ->
    val solid = coverage(px, px) { g ->
        g.scale(format.pxPerDp, format.pxPerDp)
        g.fill(shape())
    }
    val sigmaPx = SHADOW_BLUR_DP / 2 * format.pxPerDp
    maskImage(px, px, gaussianBlur(solid, px, px, sigmaPx), BLACK, SHADOW_OPACITY)
}

// --- Line icons (8.6) ------------------------------------------------------------------------------------------------

/** Something an icon paints, in the units of its view box. */
private sealed interface Part {
    data class Stroke(val shape: Shape) : Part
    data class Fill(val shape: Shape) : Part
}

private fun stroke(path: String) = Part.Stroke(svgPath(path))
private fun fill(path: String) = Part.Fill(svgPath(path))
private fun strokeCircle(cx: Double, cy: Double, r: Double) = Part.Stroke(circle(cx, cy, r))
private fun fillCircle(cx: Double, cy: Double, r: Double) = Part.Fill(circle(cx, cy, r))

/**
 * One icon: [gridDp] is its size (and folder, `icon16/...`), [viewBox] the units its paths are written in, and
 * [strokeDp] the stroke width once drawn at [gridDp]. Paths are the mockups' (Kit.dc.html), so the icons are the
 * ones the user approved.
 */
private class IconSpec(
    val name: String,
    val gridDp: Int,
    val strokeDp: Double,
    val parts: List<Part>,
    val viewBox: Double = gridDp.toDouble(),
)

// 20-unit drawings used at 20 dp and again at 40 dp for the empty states.
private val GLOBE = listOf(
    strokeCircle(10.0, 10.0, 7.5),
    stroke("M2.5 10h15 M10 2.5c2.2 2.2 3.2 4.7 3.2 7.5s-1 5.3-3.2 7.5c-2.2-2.2-3.2-4.7-3.2-7.5s1-5.3 3.2-7.5z"),
)
private val MEGAPHONE = listOf(stroke("M3 8.5v3a1 1 0 0 0 1 1h2l6 3.5v-12L6 7.5H4a1 1 0 0 0-1 1z M15 7.5a3.5 3.5 0 0 1 0 5 M6 12.5l1 4"))
private val INBOX = listOf(stroke("M3 11l2-6.5h10L17 11v4.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z M3 11h4l1 2h4l1-2h4"))
private const val HEART = "M8 13.2C5 11.2 2.5 9 2.5 6.3A2.8 2.8 0 0 1 8 5a2.8 2.8 0 0 1 5.5 1.3c0 2.7-2.5 4.9-5.5 6.9z"

private val ICONS = listOf(
    IconSpec("megaphone_filled", 12, 0.0, listOf(fill("M1.5 4.6v2.8a.6.6 0 0 0 .6.6h1.4l1 2.6h1.2l-.6-2.6L9.5 10V2L5.1 4H2.1a.6.6 0 0 0-.6.6z"))),

    IconSpec("reply", 16, 1.5, listOf(stroke("M4.5 3h7A2.5 2.5 0 0 1 14 5.5v4A2.5 2.5 0 0 1 11.5 12H8.5L5.5 14v-2h-1A2.5 2.5 0 0 1 2 9.5v-4A2.5 2.5 0 0 1 4.5 3z"))),
    IconSpec("heart", 16, 1.5, listOf(stroke(HEART))),
    // Filled and stroked, so the heart keeps its outline's size when it fills (the mockup filled it without the
    // stroke, 0.75 dp smaller on each side).
    IconSpec("heart_filled", 16, 1.5, listOf(fill(HEART), stroke(HEART))),
    IconSpec("pin", 16, 1.5, listOf(stroke("M6 2.5h4 M7 2.5v4L4.5 9h7L9 6.5v-4 M8 9v4.5"))),
    IconSpec("pin_filled", 16, 1.5, listOf(fill("M6.5 2h3v4.6L12 9.5H4L6.5 6.6z"), stroke("M8 9.5v4"))),
    IconSpec(
        "lock", 16, 1.5,
        listOf(Part.Stroke(RoundRectangle2D.Double(3.5, 7.0, 9.0, 6.5, 4.0, 4.0)), stroke("M5.5 7V5a2.5 2.5 0 0 1 5 0v2")),
    ),
    IconSpec("more", 16, 1.5, listOf(fillCircle(3.5, 8.0, 1.3), fillCircle(8.0, 8.0, 1.3), fillCircle(12.5, 8.0, 1.3))),
    IconSpec("trash", 16, 1.5, listOf(stroke("M3 4.5h10 M6.5 4.5V3h3v1.5 M4.5 4.5l.6 8.5a1 1 0 0 0 1 .9h3.8a1 1 0 0 0 1-.9l.6-8.5"))),
    IconSpec("alert", 16, 1.5, listOf(strokeCircle(8.0, 8.0, 6.0), stroke("M8 5v3.5 M8 11h.01"))),
    IconSpec("arrow_up", 16, 1.5, listOf(stroke("M8 13V3 M3.5 7.5L8 3l4.5 4.5"))),

    IconSpec("back", 20, 2.0, listOf(stroke("M12.5 4.5L7 10l5.5 5.5"))),
    IconSpec("close", 20, 2.0, listOf(stroke("M5 5l10 10 M15 5L5 15"))),
    IconSpec("person", 20, 2.0, listOf(strokeCircle(10.0, 7.0, 3.2), stroke("M3.8 16.8c.9-2.9 3.3-4.6 6.2-4.6s5.3 1.7 6.2 4.6"))),
    IconSpec("globe", 20, 2.0, GLOBE),
    IconSpec("megaphone", 20, 2.0, MEGAPHONE),
    IconSpec("inbox", 20, 2.0, INBOX),

    IconSpec("compose", 24, 2.0, listOf(stroke("M15 4.5l4.5 4.5L10 18.5H5.5V14z M13 6.5l4.5 4.5 M5 21h14"))),

    IconSpec("inbox", 40, 2.5, INBOX, viewBox = 20.0),
    IconSpec("megaphone", 40, 2.5, MEGAPHONE, viewBox = 20.0),
    IconSpec("globe", 40, 2.5, GLOBE, viewBox = 20.0),
)

private fun icons(format: SmoothFormat): List<Sprite> = ICONS.map { icon ->
    val px = format.px(icon.gridDp)
    val units = icon.viewBox / icon.gridDp
    val alpha = coverage(px, px) { g ->
        g.scale(format.pxPerDp / units, format.pxPerDp / units)
        g.stroke = roundStroke(icon.strokeDp * units)
        icon.parts.forEach {
            when (it) {
                is Part.Stroke -> g.draw(it.shape)
                is Part.Fill -> g.fill(it.shape)
            }
        }
    }
    Sprite("${format.dir}/icon${icon.gridDp}/${icon.name}", maskImage(px, px, alpha, WHITE), format.mcmeta(icon.gridDp, null))
} + spinner(format)

/** 20 dp: a 90 degree arc from the top, clockwise, over its full ring at 25%; 2 dp stroke. Turned by the code. */
private fun spinner(format: SmoothFormat): Sprite {
    val px = format.px(20)
    fun layer(part: Part.Stroke) = coverage(px, px) { g ->
        g.scale(format.pxPerDp, format.pxPerDp)
        g.stroke = roundStroke(2.0)
        g.draw(part.shape)
    }
    val ring = layer(strokeCircle(10.0, 10.0, 8.0)).map { it * 0.25 }.toDoubleArray()
    val arc = layer(stroke("M10 2a8 8 0 0 1 8 8"))
    return Sprite("${format.dir}/spinner", maskImage(px, px, over(arc, ring), WHITE), format.mcmeta(20, null))
}
