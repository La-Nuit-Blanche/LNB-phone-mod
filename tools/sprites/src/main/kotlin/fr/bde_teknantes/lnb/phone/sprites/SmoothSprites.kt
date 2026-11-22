package fr.bde_teknantes.lnb.phone.sprites

import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D

// The smooth layer's images (16.2, as changed by the rendering spike). Rounded rects, pills and circles are no
// longer images: the drawing surface computes them at the screen's real resolution. What remains:
//
//   smooth/<density>/icon<grid>/<name>.png   line icons, white, one set per density, drawn 1:1 at the matching
//   smooth/<density>/spinner.png             GUI scale (1x at scale 2, 1_5x at scale 3, 2x at scale 4)
//   smooth/shadow/<name>.png                 black soft shadows, 2 px per dp, stretched with linear filtering
//
// Every PNG has a .png.mcmeta that turns linear filtering on.

/** The folder of the smooth set under `textures/gui/`. */
const val SMOOTH_DIR = "smooth"

/** A density of the icon set: its folder under [SMOOTH_DIR] and its pixels per dp. */
data class Density(val folder: String, val pxPerDp: Double) {
    fun px(dp: Number): Int {
        val px = dp.toDouble() * pxPerDp
        require(px == Math.rint(px)) { "$dp dp is not a whole number of pixels at $pxPerDp px per dp" }
        return px.toInt()
    }
}

/** What the smooth set is exported as. The defaults are the single source of truth for the committed assets. */
data class SmoothFormat(
    /** One per GUI scale that runs apps: a 16 dp icon is 16, 24 and 32 px. */
    val iconDensities: List<Density> = listOf(Density("1x", 1.0), Density("1_5x", 1.5), Density("2x", 2.0)),
    /** Shadows are blurred anyway, so one density stretched with linear filtering serves every scale. */
    val shadowDensity: Density = Density("shadow", 2.0),
)

/** vanilla's switch for linear filtering (no mipmaps) of a standalone texture. */
private const val LINEAR_FILTERING = """{"texture": {"blur": true, "clamp": true}}""" + "\n"

private val WHITE = rgb("#FFFFFF")
private val BLACK = rgb("#000000")

fun smoothSprites(format: SmoothFormat = SmoothFormat()): List<Sprite> =
    shadows(format.shadowDensity) + format.iconDensities.flatMap { icons(it) + spinner(it) }

private fun smoothSprite(path: String, image: java.awt.image.BufferedImage) =
    Sprite("$SMOOTH_DIR/$path", image, LINEAR_FILTERING, antiAliased = true)

private fun circle(cx: Double, cy: Double, r: Double) = Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r)

// --- Shadows ---------------------------------------------------------------------------------------------------------

/** CSS's `blur` is twice the standard deviation of the Gaussian, and the mockups drew these as CSS box-shadows. */
private const val SHADOW_BLUR_DP = 8.0
private const val SHADOW_OPACITY = 0.45

/** The sheet shadow serves radii 10 to 14 (banner, snackbar, menu, dialog); one radius in between does for all. */
private const val SHEET_RADIUS_DP = 12.0

/**
 * How far the sheet sprite reaches past its element on each side; the code draws it this much larger. The UI kit
 * nine-slices it with a 22 dp border (16.2), from UV coordinates.
 */
private const val SHEET_MARGIN_DP = 8.0
private const val SHEET_OFFSET_DP = 2.0

private fun shadows(density: Density): List<Sprite> {
    // FAB: a 48 dp circle in the middle of 72 dp. The 2 dp drop is applied when drawing ("offset 2 dp down", 8.7).
    val fab = shadow(density, 72) { circle(36.0, 36.0, 24.0) }
    // Sheet: an element drawn 8 dp inside the sprite on each side, its shadow 2 dp lower, baked in so the sprite
    // can be drawn exactly 8 dp larger than the element (16.2).
    val sheet = shadow(density, 48) {
        val side = 48 - 2 * SHEET_MARGIN_DP
        RoundRectangle2D.Double(
            SHEET_MARGIN_DP, SHEET_MARGIN_DP + SHEET_OFFSET_DP, side, side, 2 * SHEET_RADIUS_DP, 2 * SHEET_RADIUS_DP,
        )
    }
    return listOf(smoothSprite("${density.folder}/fab", fab), smoothSprite("${density.folder}/sheet", sheet))
}

private fun shadow(density: Density, sizeDp: Int, shape: () -> Shape) = density.px(sizeDp).let { px ->
    val solid = coverage(px, px) { g ->
        g.scale(density.pxPerDp, density.pxPerDp)
        g.fill(shape())
    }
    val sigmaPx = SHADOW_BLUR_DP / 2 * density.pxPerDp
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
 * [strokeDp] the stroke width once drawn at [gridDp] (8.6: 1.5 dp at 16, 2 dp at 20 and 24, 2.5 dp at 40). Paths are
 * the mockups' (Kit.dc.html), so the icons are the ones the user approved.
 */
private class IconSpec(
    val name: String,
    val gridDp: Int,
    val strokeDp: Double,
    val parts: List<Part>,
    val viewBox: Double = gridDp.toDouble(),
)

// 20-unit drawings used at several sizes.
private val GLOBE = listOf(
    strokeCircle(10.0, 10.0, 7.5),
    stroke("M2.5 10h15 M10 2.5c2.2 2.2 3.2 4.7 3.2 7.5s-1 5.3-3.2 7.5c-2.2-2.2-3.2-4.7-3.2-7.5s1-5.3 3.2-7.5z"),
)
private val MEGAPHONE = listOf(stroke("M3 8.5v3a1 1 0 0 0 1 1h2l6 3.5v-12L6 7.5H4a1 1 0 0 0-1 1z M15 7.5a3.5 3.5 0 0 1 0 5 M6 12.5l1 4"))
private val INBOX = listOf(stroke("M3 11l2-6.5h10L17 11v4.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z M3 11h4l1 2h4l1-2h4"))
private val PERSON = listOf(strokeCircle(10.0, 7.0, 3.2), stroke("M3.8 16.8c.9-2.9 3.3-4.6 6.2-4.6s5.3 1.7 6.2 4.6"))
private const val HEART = "M8 13.2C5 11.2 2.5 9 2.5 6.3A2.8 2.8 0 0 1 8 5a2.8 2.8 0 0 1 5.5 1.3c0 2.7-2.5 4.9-5.5 6.9z"

private val ICONS = listOf(
    IconSpec("megaphone_filled", 12, 0.0, listOf(fill("M1.5 4.6v2.8a.6.6 0 0 0 .6.6h1.4l1 2.6h1.2l-.6-2.6L9.5 10V2L5.1 4H2.1a.6.6 0 0 0-.6.6z"))),
    // The anonymous avatar's icon on a 16 dp avatar (8.7). 8.6 gives no stroke for 12 dp; the nearest stroked grid
    // is 16 dp, 1.5 dp.
    IconSpec("person", 12, 1.5, PERSON, viewBox = 20.0),

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
    IconSpec("person", 20, 2.0, PERSON),
    IconSpec("globe", 20, 2.0, GLOBE),
    IconSpec("megaphone", 20, 2.0, MEGAPHONE),
    IconSpec("inbox", 20, 2.0, INBOX),

    IconSpec("compose", 24, 2.0, listOf(stroke("M15 4.5l4.5 4.5L10 18.5H5.5V14z M13 6.5l4.5 4.5 M5 21h14"))),

    // The anonymous avatar's icon on a 48 dp avatar (8.7). 8.6 gives no 28 dp grid; the nearest is 24 dp, 2 dp.
    IconSpec("person", 28, 2.0, PERSON, viewBox = 20.0),

    IconSpec("inbox", 40, 2.5, INBOX, viewBox = 20.0),
    IconSpec("megaphone", 40, 2.5, MEGAPHONE, viewBox = 20.0),
    IconSpec("globe", 40, 2.5, GLOBE, viewBox = 20.0),
)

private fun icons(density: Density): List<Sprite> = ICONS.map { icon ->
    val px = density.px(icon.gridDp)
    val units = icon.viewBox / icon.gridDp
    val alpha = coverage(px, px) { g ->
        g.scale(density.pxPerDp / units, density.pxPerDp / units)
        g.stroke = roundStroke(icon.strokeDp * units)
        icon.parts.forEach {
            when (it) {
                is Part.Stroke -> g.draw(it.shape)
                is Part.Fill -> g.fill(it.shape)
            }
        }
    }
    smoothSprite("${density.folder}/icon${icon.gridDp}/${icon.name}", maskImage(px, px, alpha, WHITE))
}

/** 20 dp: a 90 degree arc from the top, clockwise, over its full ring at 25%; 2 dp stroke. Turned by the code. */
private fun spinner(density: Density): Sprite {
    val px = density.px(20)
    fun layer(part: Part.Stroke) = coverage(px, px) { g ->
        g.scale(density.pxPerDp, density.pxPerDp)
        g.stroke = roundStroke(2.0)
        g.draw(part.shape)
    }
    val ring = layer(strokeCircle(10.0, 10.0, 8.0)).map { it * 0.25 }.toDoubleArray()
    val arc = layer(stroke("M10 2a8 8 0 0 1 8 8"))
    return smoothSprite("${density.folder}/spinner", maskImage(px, px, over(arc, ring), WHITE))
}
