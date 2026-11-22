package fr.bde_teknantes.lnb.phone.client.ui

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.Shadow
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMeasurer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import net.minecraft.resources.ResourceLocation
import kotlin.math.PI

/**
 * The public [AppCanvas] over the phone's [SmoothCanvas]: adds the offset and opacity stacks, the icon and shadow
 * textures, and keeps apps away from the canvas's own settings. Valid between [AppViewport]'s begin and end.
 */
internal class SmoothAppCanvas(private val smooth: SmoothCanvas) : AppCanvas, TextMeasurer by TextMetrics {
    private var ox = 0f
    private var oy = 0f
    private var alpha = 1f
    private val offsets = ArrayList<FloatArray>()
    private val alphas = ArrayList<Float>()
    private var clipDepth = 0

    override val width get() = AppCanvas.WIDTH
    override val height get() = AppCanvas.HEIGHT
    override val pixelsPerDp get() = smooth.grid.pxPerDp

    /** Called after [SmoothCanvas.begin]. */
    fun reset() {
        ox = 0f
        oy = 0f
        alpha = 1f
        offsets.clear()
        alphas.clear()
        clipDepth = 0
    }

    /** Called before [SmoothCanvas.end]: undoes what an app left pushed, so the next frame starts clean. */
    fun finish() {
        if (offsets.isNotEmpty() || alphas.isNotEmpty() || clipDepth > 0) {
            warnUnbalanced()
            while (clipDepth > 0) popClip()
        }
        reset()
    }

    private fun tint(color: Int) = Colors.withAlpha(color, alpha)

    // ---- Shapes ----

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) = smooth.fillRect(ox + x, oy + y, w, h, tint(color))

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) =
        smooth.fillRoundRect(ox + x, oy + y, w, h, radius, tint(color))

    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, color: Int) =
        smooth.strokeRoundRect(ox + x, oy + y, w, h, radius, thickness, tint(color))

    override fun fillCircle(x: Float, y: Float, diameter: Float, color: Int) = smooth.fillCircle(ox + x, oy + y, diameter, tint(color))

    override fun hairline(x0: Float, x1: Float, y: Float, color: Int) {
        // Anchored on the row's bottom edge: at GUI scale 3 a 1 dp row is 1.5 px, drawn 2 px, and the extra pixel must
        // go up into the row's own area rather than down into the next one.
        val grid = smooth.grid
        val bottom = grid.snap(oy + y + 1f)
        val top = bottom - grid.strokePx(1f)
        smooth.fillRect(ox + x0, grid.toDp(top.toFloat()), x1 - x0, grid.toDp((bottom - top).toFloat()), tint(color))
    }

    override fun verticalHairline(x: Float, y0: Float, y1: Float, color: Int) {
        val grid = smooth.grid
        val right = grid.snap(ox + x + 1f)
        val left = right - grid.strokePx(1f)
        smooth.fillRect(grid.toDp(left.toFloat()), oy + y0, grid.toDp((right - left).toFloat()), y1 - y0, tint(color))
    }

    // ---- Text ----

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float =
        smooth.drawText(text, ox + x, oy + y, style.smooth, tint(color))

    // ---- Images ----

    override fun drawIcon(icon: Icon, x: Float, y: Float, color: Int, size: Float) {
        val texture = icon.texture(icon.sourceSize(size), smooth.grid.guiScale)
        val (u0, u1) = if (icon.mirrored) 1f to 0f else 0f to 1f
        smooth.image(texture, ox + x, oy + y, size, size, u0, 0f, u1, 1f, tint(color), linear = true)
    }

    override fun drawSpinner(x: Float, y: Float, size: Float, color: Int, turns: Float) {
        val texture = Icon.spinnerTexture(smooth.grid.guiScale)
        val radians = (turns - kotlin.math.floor(turns)) * 2f * PI.toFloat()
        smooth.imageRotated(texture, ox + x + size / 2f, oy + y + size / 2f, size, radians, tint(color))
    }

    override fun drawTexture(
        texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float,
        color: Int, u0: Float, v0: Float, u1: Float, v1: Float, linear: Boolean,
    ) = smooth.image(texture, ox + x, oy + y, w, h, u0, v0, u1, v1, tint(color), linear)

    override fun drawPlayerFace(skin: ResourceLocation, x: Float, y: Float, size: Float) =
        smooth.playerFace(skin, ox + x, oy + y, size, tint(Colors.ANON_BG), tint(-1))

    override fun drawShadow(shadow: Shadow, x: Float, y: Float, w: Float, h: Float) {
        when (shadow) {
            Shadow.FAB -> {
                // 72 dp for a 48 dp circle: 12 dp around it, plus the 2 dp drop (8.7).
                val grow = (FAB_SHADOW_DP - w) / 2f
                drawTexture(FAB_SHADOW, x - grow, y - grow + 2f, w + 2 * grow, h + 2 * grow)
            }
            Shadow.SHEET -> sheetShadow(x - SHEET_MARGIN, y - SHEET_MARGIN, w + 2 * SHEET_MARGIN, h + 2 * SHEET_MARGIN)
        }
    }

    /** The sheet sprite nine-sliced over the grown rectangle: its corners keep their 22 dp (16.2). */
    private fun sheetShadow(x: Float, y: Float, w: Float, h: Float) {
        val border = minOf(SHEET_BORDER, w / 2f, h / 2f)
        val xs = floatArrayOf(x, x + border, x + w - border, x + w)
        val ys = floatArrayOf(y, y + border, y + h - border, y + h)
        val b = border / SHEET_SPRITE_DP
        val us = floatArrayOf(0f, b, 1f - b, 1f)
        for (row in 0..2) for (col in 0..2) {
            if (xs[col + 1] <= xs[col] || ys[row + 1] <= ys[row]) continue
            drawTexture(SHEET_SHADOW, xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row], -1, us[col], us[row], us[col + 1], us[row + 1])
        }
    }

    // ---- State ----

    override fun pushClip(x: Float, y: Float, w: Float, h: Float) {
        smooth.pushClip(ox + x, oy + y, w, h)
        clipDepth++
    }

    override fun popClip() {
        check(clipDepth > 0) { "popClip() without pushClip()" }
        clipDepth--
        smooth.popClip()
    }

    override fun pushOffset(dx: Float, dy: Float) {
        offsets.add(floatArrayOf(ox, oy))
        ox += dx
        oy += dy
    }

    override fun popOffset() {
        check(offsets.isNotEmpty()) { "popOffset() without pushOffset()" }
        val previous = offsets.removeAt(offsets.size - 1)
        ox = previous[0]
        oy = previous[1]
    }

    override fun pushAlpha(alpha: Float) {
        alphas.add(this.alpha)
        this.alpha *= alpha.coerceIn(0f, 1f)
    }

    override fun popAlpha() {
        check(alphas.isNotEmpty()) { "popAlpha() without pushAlpha()" }
        alpha = alphas.removeAt(alphas.size - 1)
    }

    private companion object {
        val FAB_SHADOW: ResourceLocation = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/smooth/shadow/fab.png")
        val SHEET_SHADOW: ResourceLocation = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/smooth/shadow/sheet.png")
        const val FAB_SHADOW_DP = 72f
        const val SHEET_SPRITE_DP = 48f
        const val SHEET_BORDER = 22f
        const val SHEET_MARGIN = 8f
        var warned = false

        fun warnUnbalanced() {
            if (warned) return
            warned = true
            LnbPhone.LOGGER.warn("An app screen left a clip, offset or opacity pushed at the end of its frame")
        }
    }
}
