package fr.bde_teknantes.lnb.phone.client.shell

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * The open phone as a game screen: it frees the cursor and stops the player, without pausing the game or blurring
 * and darkening the world. Everything it receives goes to [PhoneShell], which owns the phone's state.
 */
internal class PhoneScreen : Screen(Component.translatable("gui.lnbphone.title")) {
    override fun isPauseScreen() = false

    // The shell handles Esc itself: an app may keep it to close a menu.
    override fun shouldCloseOnEsc() = false

    // No blur, no menu background: the world stays as it is (the centred scrim is the shell's).
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // The precise pointer, not the whole pixel the screen is given: apps work in dp, half a GUI px.
        val mc = Minecraft.getInstance()
        val window = mc.window
        val x = mc.mouseHandler.xpos() * window.guiScaledWidth / window.screenWidth
        val y = mc.mouseHandler.ypos() * window.guiScaledHeight / window.screenHeight
        PhoneShell.render(graphics, true, x, y)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) = PhoneShell.mouseClicked(mouseX, mouseY, button)

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) = PhoneShell.mouseReleased(mouseX, mouseY, button)

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double) =
        PhoneShell.mouseDragged(mouseX, mouseY, button, dragX, dragY)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double) =
        PhoneShell.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int) = PhoneShell.keyPressed(keyCode, scanCode, modifiers)

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int) = PhoneShell.keyReleased(keyCode, scanCode, modifiers)

    override fun charTyped(codePoint: Char, modifiers: Int) = PhoneShell.charTyped(codePoint, modifiers)

    // Every time the screen becomes the game's screen: when the phone opens it, and when a dialog it opened closes back
    // to it.
    override fun added() = PhoneShell.onScreenShown()

    override fun removed() = PhoneShell.onScreenRemoved()
}
