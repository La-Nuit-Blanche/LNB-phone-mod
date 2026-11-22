package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.ui.AppViewport
import net.minecraft.client.gui.GuiGraphics

/**
 * Where app screens are drawn: the one place that opens the public canvas over the viewport and hands it to an app,
 * through the navigator. Each screen gets the app background (`bg`, the same colour as the status bar inside apps, 8.2)
 * under it, so a screen sliding in hides what it covers.
 *
 * Render thread only.
 */
internal object AppHost {
    private val viewport = AppViewport()

    /**
     * Draws [screen] with its left edge [offset] GUI px right of the viewport's ([left], [top]), clipped to the
     * viewport. [mouseX] and [mouseY] are the pointer in window GUI px; the screen gets them in its own dp.
     */
    fun draw(graphics: GuiGraphics, screen: AppScreen, left: Int, top: Int, offset: Int, mouseX: Double, mouseY: Double, partialTick: Float) {
        // The shell's own clip keeps a screen sliding in or out inside the viewport; the canvas clips to its own
        // (shifted) rectangle within it.
        graphics.enableScissor(left, top, left + Device.VIEWPORT_WIDTH, top + Device.VIEWPORT_HEIGHT)
        try {
            viewport.moveTo(left + offset, top)
            viewport.render(graphics) { canvas ->
                canvas.fillRect(0f, 0f, AppCanvas.WIDTH.toFloat(), AppCanvas.HEIGHT.toFloat(), ShellColors.APP_BG)
                // Converted here rather than by the viewport: two screens are drawn in one frame during a slide.
                val x = ((mouseX - left - offset) * 2).toFloat()
                val y = ((mouseY - top) * 2).toFloat()
                LnbPhoneClient.navigator.render(screen, canvas, x, y, partialTick)
            }
        } finally {
            graphics.disableScissor()
        }
    }
}
