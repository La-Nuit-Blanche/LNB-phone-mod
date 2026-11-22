package fr.bde_teknantes.lnb.phone.api.client

import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMeasurer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import net.minecraft.resources.ResourceLocation

/**
 * What an [AppScreen] draws on: the app viewport, the part of the phone's screen under the status bar.
 *
 * Most screens are built from the UI kit's widgets (see [KitScreen][fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen]),
 * which draw themselves; the methods here are for what the kit doesn't have.
 *
 * **Coordinates** are in **dp**, with (0, 0) at the top-left corner of the viewport (or of the current offset, see
 * [pushOffset]), x to the right and y downwards. The viewport is always [WIDTH] x [HEIGHT] dp, whatever the window
 * size or GUI scale (2 dp make one GUI pixel; one dp is 1, 1.5 or 2 real pixels at GUI scale 2, 3 or 4). Anything
 * drawn outside it is cut off. Every edge is snapped to the nearest real pixel on its own, so shapes that touch in dp
 * touch on screen. Put edges on even dp values: they then fall on whole pixels at every GUI scale and stay sharp.
 *
 * **Colours** are ARGB ints, `0xAARRGGBB`: take them from [Colors]. **Text** comes in the [TextStyle]s only.
 *
 * **Order**: what is drawn later is drawn on top. A canvas is only valid during the [AppScreen.render] call that
 * received it; don't keep it.
 *
 * The phone provides the canvas; don't implement this interface yourself (for tests, draw through a
 * [fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen] and check the widgets' state instead). Later versions of the
 * phone will add methods to it, which would break a class of yours.
 */
interface AppCanvas : TextMeasurer {
    /** Width of the viewport in dp: [WIDTH]. */
    val width: Int

    /** Height of the viewport in dp: [HEIGHT]. */
    val height: Int

    /** Real screen pixels per dp right now: 1, 1.5 or 2 at GUI scale 2, 3 or 4. Rarely needed: everything is in dp. */
    val pixelsPerDp: Float

    // ---- Shapes ----

    /** Fills a rectangle whose top-left corner is ([x], [y]), [w] x [h] dp. */
    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int)

    /** Fills a rectangle with rounded corners of [radius] dp. [Radius.FULL][fr.bde_teknantes.lnb.phone.api.client.ui.Radius.FULL] gives a pill. */
    fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int)

    /**
     * Draws a border [thickness] dp thick along the inside of a rounded rectangle (it doesn't grow the shape). The
     * thickness is rounded to whole pixels and never thinner than one.
     */
    fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, color: Int)

    /** Fills a circle of [diameter] dp whose bounding square's top-left corner is ([x], [y]). */
    fun fillCircle(x: Float, y: Float, diameter: Float, color: Int)

    /**
     * A horizontal divider from [x0] to [x1], occupying the dp row [y]..[y]+1: one pixel at GUI scale 2, two at 3 and
     * 4. It always ends at the bottom of that row, so a line drawn on the last dp of an area (a header's at y 43, a
     * row's at its bottom) never spills onto what follows.
     */
    fun hairline(x0: Float, x1: Float, y: Float, color: Int)

    /** [hairline] in [Colors.HAIRLINE]. */
    fun hairline(x0: Float, x1: Float, y: Float) = hairline(x0, x1, y, Colors.HAIRLINE)

    /** A vertical divider from [y0] to [y1], occupying the dp column [x]..[x]+1 (see [hairline]). */
    fun verticalHairline(x: Float, y0: Float, y1: Float, color: Int)

    /** [verticalHairline] in [Colors.HAIRLINE]. */
    fun verticalHairline(x: Float, y0: Float, y1: Float) = verticalHairline(x, y0, y1, Colors.HAIRLINE)

    // ---- Text ----

    /**
     * Draws one line of [text] in [style], in the line box whose top-left corner is ([x], [y]). Line feeds are not
     * interpreted. Returns the width drawn, in dp.
     */
    fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float

    /** Draws [text] on one line, cut with an ellipsis (…) so that it fits in [maxWidth] dp. Returns the width drawn. */
    fun drawTextTruncated(text: String, x: Float, y: Float, maxWidth: Float, style: TextStyle, color: Int): Float =
        drawText(truncate(text, style, maxWidth), x, y, style, color)

    /**
     * Draws [text] wrapped at [maxWidth] dp, one line box of [style] under the other. Returns the number of lines drawn.
     */
    fun drawTextWrapped(text: String, x: Float, y: Float, maxWidth: Float, style: TextStyle, color: Int): Int =
        drawTextWrapped(text, x, y, maxWidth, style, color, Int.MAX_VALUE)

    /** [drawTextWrapped] on at most [maxLines] lines, the last one ending with an ellipsis when text remains. */
    fun drawTextWrapped(text: String, x: Float, y: Float, maxWidth: Float, style: TextStyle, color: Int, maxLines: Int): Int {
        val lines = wrap(text, style, maxWidth, maxLines)
        lines.forEachIndexed { i, line -> drawText(line, x, y + i * style.lineHeight, style, color) }
        return lines.size
    }

    /** Draws one line of [text] centred horizontally on [centerX]. Returns the width drawn. */
    fun drawTextCentered(text: String, centerX: Float, y: Float, style: TextStyle, color: Int): Float =
        drawText(text, centerX - measure(text, style) / 2f, y, style, color)

    // ---- Images ----

    /**
     * Draws [icon] as a [size] dp square tinted with [color], its top-left corner at ([x], [y]). Use one of the icon's
     * [sizes][Icon.sizes]: the icon is then drawn pixel for pixel.
     */
    fun drawIcon(icon: Icon, x: Float, y: Float, color: Int, size: Float)

    /** [drawIcon] at the icon's smallest size. */
    fun drawIcon(icon: Icon, x: Float, y: Float, color: Int) = drawIcon(icon, x, y, color, icon.sizes.first().toFloat())

    /**
     * Draws the loading spinner, a quarter arc on a faint ring, as a [size] dp square (20 dp, or 16 inside buttons),
     * turned by [turns] (0 to 1, a full turn; the phone's spinner turns once per 800 ms). The
     * [Spinner][fr.bde_teknantes.lnb.phone.api.client.ui.Spinner] widget animates it for you.
     */
    fun drawSpinner(x: Float, y: Float, size: Float, color: Int, turns: Float)

    /**
     * Draws part [u0]..[u1] x [v0]..[v1] (0 to 1) of [texture], stretched over a rectangle and multiplied by [color]
     * (white leaves it as it is). [linear] smooths it when stretched; pass false for pixel art.
     */
    fun drawTexture(
        texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float,
        color: Int, u0: Float, v0: Float, u1: Float, v1: Float, linear: Boolean,
    )

    /** The whole of [texture], as it is, smoothed when stretched. */
    fun drawTexture(texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float) =
        drawTexture(texture, x, y, w, h, -1, 0f, 0f, 1f, 1f, true)

    /**
     * Draws a player's face (the 8 x 8 face of [skin] with its hat layer, in sharp pixels) in a circle of [size] dp.
     * Use 16, 32 or 48 dp: other sizes don't scale the face by a whole number of pixels. While the skin is loading, a
     * grey disc stands in. The [Avatar][fr.bde_teknantes.lnb.phone.api.client.ui.Avatar] widget does this, and
     * the anonymous variant.
     */
    fun drawPlayerFace(skin: ResourceLocation, x: Float, y: Float, size: Float)

    /**
     * Draws the soft shadow that goes under an element occupying the rectangle ([x], [y], [w], [h]); draw the element
     * itself afterwards. The shadow reaches past the rectangle: see [Shadow]. Only sheets (menus, dialogs, snackbars,
     * banners) and the floating action button have shadows on the phone.
     */
    fun drawShadow(shadow: Shadow, x: Float, y: Float, w: Float, h: Float)

    // ---- State ----

    /**
     * Cuts what follows to a rectangle (also inside the current one) until the matching [popClip]. For scrolling
     * areas and text that must not overflow its box.
     */
    fun pushClip(x: Float, y: Float, w: Float, h: Float)

    /** Ends the clip of the last [pushClip]. */
    fun popClip()

    /**
     * Moves the origin by ([dx], [dy]) dp until the matching [popOffset]: a widget draws at (0, 0) and its parent
     * places it. Offsets add up. Fractions are fine (a list scrolled by 10.5 dp): edges still land on whole pixels.
     */
    fun pushOffset(dx: Float, dy: Float)

    /** Ends the offset of the last [pushOffset]. */
    fun popOffset()

    /**
     * Multiplies the opacity of what follows by [alpha] (0 to 1) until the matching [popAlpha], for fades. Opacities
     * multiply: half inside half is a quarter.
     */
    fun pushAlpha(alpha: Float)

    /** Ends the opacity of the last [pushAlpha]. */
    fun popAlpha()

    companion object {
        /** Width of the app viewport in dp. */
        const val WIDTH = 256

        /** Height of the app viewport in dp. */
        const val HEIGHT = 370
    }
}

/** Runs [block] with what it draws cut to a rectangle (see [AppCanvas.pushClip]). */
inline fun AppCanvas.clipped(x: Float, y: Float, w: Float, h: Float, block: () -> Unit) {
    pushClip(x, y, w, h)
    try {
        block()
    } finally {
        popClip()
    }
}

/** Runs [block] with the origin moved by ([dx], [dy]) dp (see [AppCanvas.pushOffset]). */
inline fun AppCanvas.offset(dx: Float, dy: Float, block: () -> Unit) {
    pushOffset(dx, dy)
    try {
        block()
    } finally {
        popOffset()
    }
}

/** Runs [block] with what it draws faded by [alpha] (see [AppCanvas.pushAlpha]). */
inline fun AppCanvas.faded(alpha: Float, block: () -> Unit) {
    pushAlpha(alpha)
    try {
        block()
    } finally {
        popAlpha()
    }
}

/** The shadows of the phone, pre-blurred (the phone draws no other effect). More may be added: `when` needs an `else`. */
enum class Shadow {
    /**
     * Under rounded sheets with a radius of 10 to 14 dp: menus, dialogs, snackbars. Reaches 8 dp past the element on
     * every side and sits 2 dp low.
     */
    SHEET,

    /** Under the 48 dp floating action button: a 72 dp soft disc centred on it, 2 dp low. */
    FAB,
}
