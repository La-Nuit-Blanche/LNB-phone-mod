package fr.bde_teknantes.lnb.phone.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import net.minecraft.client.gui.GuiGraphics

/**
 * Where app screens appear on the game's screen: the one place a host (the phone's `Screen`, or a test screen) turns
 * its GUI pixels into an app's dp, both to draw and to pass input on. Not for apps.
 *
 * ```kotlin
 * // render(), with the pose already translated for the slide:
 * viewport.moveTo(screenLeft, screenTop + 11)
 * viewport.render(graphics) { canvas ->
 *     navigator.render(screen, canvas, viewport.toDpX(mouseX), viewport.toDpY(mouseY), partialTick)
 * }
 * // input, in GUI px from Minecraft:
 * override fun mouseClicked(x: Double, y: Double, button: Int) =
 *     viewport.contains(x, y) && navigator.mousePressed(viewport.toDpX(x), viewport.toDpY(y), button)
 * override fun mouseReleased(x: Double, y: Double, button: Int) =   // wherever the pointer is now
 *     navigator.mouseReleased(viewport.toDpX(x), viewport.toDpY(y), button)
 * override fun mouseDragged(x: Double, y: Double, button: Int, dx: Double, dy: Double) =
 *     navigator.mouseDragged(viewport.toDpX(x), viewport.toDpY(y), button, viewport.toDp(dx), viewport.toDp(dy))
 * override fun mouseScrolled(x: Double, y: Double, sx: Double, sy: Double) =
 *     viewport.contains(x, y) && navigator.mouseScrolled(viewport.toDpX(x), viewport.toDpY(y), sx.toFloat(), sy.toFloat())
 * // keyPressed and charTyped go to the navigator as they come; read navigator.hasTextFocus before acting on the
 * // phone key or Backspace.
 * ```
 *
 * Drags are only sent after a press inside the viewport; releases are always sent, so a widget that was pressed
 * learns the press is over even when the pointer left. Render thread only; one viewport draws at a time.
 */
class AppViewport {
    private val smooth = SmoothCanvas()
    private val canvas = SmoothAppCanvas(smooth)

    /** Left edge of the viewport in GUI px. */
    var left = 0
        private set

    /** Top edge of the viewport in GUI px. */
    var top = 0
        private set

    /** Places the viewport's top-left corner at ([left], [top]) GUI px. */
    fun moveTo(left: Int, top: Int) {
        this.left = left
        this.top = top
    }

    /** Whether the point ([guiX], [guiY]), in GUI px, is over the viewport. */
    fun contains(guiX: Double, guiY: Double): Boolean =
        guiX >= left && guiX < left + WIDTH_GUI && guiY >= top && guiY < top + HEIGHT_GUI

    /** A horizontal position in GUI px as app-local dp. */
    fun toDpX(guiX: Double): Float = ((guiX - left) * DP_PER_GUI).toFloat()

    /** A vertical position in GUI px as app-local dp. */
    fun toDpY(guiY: Double): Float = ((guiY - top) * DP_PER_GUI).toFloat()

    /** A distance in GUI px (a drag) in dp. */
    fun toDp(gui: Double): Float = (gui * DP_PER_GUI).toFloat()

    /**
     * Opens the canvas over the viewport, runs [draw] with it, and closes it, restoring the render state. What [draw]
     * leaves pushed (clips, offsets, opacity) is undone.
     */
    fun render(graphics: GuiGraphics, draw: (AppCanvas) -> Unit) {
        // draw { } ends the smooth canvas (scissor, render state) even when the app or the tidying up throws.
        smooth.draw(graphics, left, top, WIDTH_GUI, HEIGHT_GUI) {
            canvas.reset()
            try {
                draw(canvas)
            } finally {
                canvas.finish()
            }
        }
    }

    companion object {
        /** 2 dp per GUI pixel (art direction, "Units and coordinates"). */
        const val DP_PER_GUI = 2.0

        /** The viewport in GUI px: 256 x 370 dp. */
        const val WIDTH_GUI = AppCanvas.WIDTH / 2
        const val HEIGHT_GUI = AppCanvas.HEIGHT / 2
    }
}
