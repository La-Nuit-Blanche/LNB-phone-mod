package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle

/** A screen that opens another one on each click, to try the phone's navigation by hand. Drawn without the kit. */
class HelloScreen(private val depth: Int) : AppScreen() {
    override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
        canvas.fillRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), Colors.BG)
        canvas.drawTextCentered("Hello $depth", canvas.width / 2f, 170f, TextStyle.TITLE_L, Colors.TEXT_1)
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        phone.push(HelloScreen(depth + 1))
        return true
    }

    // A demo notification for this depth needs no banner here: the player is looking at it.
    override fun showsTarget(target: String) = target == "depth:$depth"

    override fun toString() = "HelloScreen($depth)"
}
