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

    // A demo notification for this depth needs no banner here: the player is looking at it.
    override fun showsTarget(target: String) = target == "depth:$depth"

    override fun toString() = "HelloScreen($depth)"
}
