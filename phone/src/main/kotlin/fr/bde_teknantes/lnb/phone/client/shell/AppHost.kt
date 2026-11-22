package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles
import net.minecraft.client.gui.GuiGraphics
import net.neoforged.fml.loading.FMLEnvironment

/**
 * Where app screens are drawn: the one place that makes a canvas for the viewport and hands it to an app, through the
 * navigator. Each screen gets the app background (`bg`, the same colour as the status bar inside apps, 8.2) under it,
 * so a screen sliding in hides what it covers.
 *
 * Render thread only.
 */
internal object AppHost {
    private val smooth = SmoothCanvas()

    // What apps draw on. It only knows its size for now: the UI kit gives it its drawing methods over [smooth].
    private val canvas = object : AppCanvas {
        override val width = AppCanvas.WIDTH
        override val height = AppCanvas.HEIGHT
    }

    /**
     * Draws [screen] with its left edge [offset] GUI px right of the viewport's ([left], [top]), clipped to the
     * viewport. [mouseX] and [mouseY] are the pointer in window GUI px; the screen gets them in its own dp.
     */
    fun draw(graphics: GuiGraphics, screen: AppScreen, left: Int, top: Int, offset: Int, mouseX: Double, mouseY: Double, partialTick: Float) {
        graphics.enableScissor(left, top, left + Device.VIEWPORT_WIDTH, top + Device.VIEWPORT_HEIGHT)
        try {
            smooth.begin(graphics, left + offset, top, Device.VIEWPORT_WIDTH, Device.VIEWPORT_HEIGHT)
            try {
                smooth.fillRect(0f, 0f, AppCanvas.WIDTH.toFloat(), AppCanvas.HEIGHT.toFloat(), ShellColors.APP_BG)
                val x = ((mouseX - left - offset) * 2).toFloat()
                val y = ((mouseY - top) * 2).toFloat()
                LnbPhoneClient.navigator.render(screen, canvas, x, y, partialTick)
                // Until apps can draw (the UI kit), development builds name the screen so its slides can be seen.
                if (!FMLEnvironment.production) smooth.drawText(screen.toString(), 12f, 12f, TextStyles.BODY, PLACEHOLDER)
            } finally {
                smooth.end()
            }
        } finally {
            graphics.disableScissor()
        }
    }

    private const val PLACEHOLDER = 0xFF5C6575.toInt()
}
