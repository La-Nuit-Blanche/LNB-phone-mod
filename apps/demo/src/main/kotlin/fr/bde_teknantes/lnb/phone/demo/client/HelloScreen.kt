package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * A screen that opens another one on each click, to try the phone's navigation by hand. Drawn without the kit.
 *
 * A right click opens a vanilla dialog over the phone, as an app asking to confirm a link would, to check that the
 * phone comes back as it was when the dialog closes (`TESTING.md`).
 */
class HelloScreen(private val depth: Int) : AppScreen() {
    override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
        canvas.fillRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), Colors.BG)
        canvas.drawTextCentered("Hello $depth", canvas.width / 2f, 170f, TextStyle.TITLE_L, Colors.TEXT_1)
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            val mc = Minecraft.getInstance()
            val phoneScreen = mc.screen
            mc.setScreen(ConfirmScreen({ mc.setScreen(phoneScreen) }, Component.translatable("lnbphone_demo.hello.dialog.title"), Component.translatable("lnbphone_demo.hello.dialog.text")))
            return true
        }
        phone.push(HelloScreen(depth + 1))
        return true
    }

    // A demo notification for this depth needs no banner here: the player is looking at it.
    override fun showsTarget(target: String) = target == "depth:$depth"

    override fun toString() = "HelloScreen($depth)"
}
