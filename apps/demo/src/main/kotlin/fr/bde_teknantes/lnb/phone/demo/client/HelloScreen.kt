package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen

/** A screen that opens another one on each click, to try the phone's navigation by hand. */
class HelloScreen(private val depth: Int) : AppScreen() {
    override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
        // Nothing to draw with yet: the canvas gets its drawing methods with the UI kit.
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        phone.push(HelloScreen(depth + 1))
        return true
    }

    override fun toString() = "HelloScreen($depth)"
}
