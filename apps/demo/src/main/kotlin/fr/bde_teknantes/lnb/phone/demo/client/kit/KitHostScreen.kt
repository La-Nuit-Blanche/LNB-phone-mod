package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.client.ui.AppViewport
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * One app screen to show in [KitHostScreen], with a caption and, for screenshots, a pointer position held still (in
 * viewport dp) so that hover states and tooltips show without a mouse.
 */
class HostedPage(val caption: String, val pointer: ((AppScreen) -> Pair<Float, Float>)? = null, val create: () -> AppScreen)

/**
 * Test host for app screens until the phone's own screen exists: draws a few viewports side by side, each through an
 * [AppViewport] at the real size (256 x 370 dp), and feeds the real mouse and keyboard to the page under the pointer.
 * The screens are not on a phone, so navigation (back, push) does nothing here.
 */
class KitHostScreen(private val pages: List<HostedPage>) : Screen(Component.literal("UI kit")) {
    private val screens = pages.map { it.create() }
    private val viewports = pages.map { AppViewport() }
    private var active = -1
    private var ticks = 0

    override fun isPauseScreen() = false

    override fun init() {
        val gap = 24
        val total = pages.size * AppViewport.WIDTH_GUI + (pages.size - 1) * gap
        val left = (width - total) / 2
        val top = (height - AppViewport.HEIGHT_GUI) / 2 + 5
        viewports.forEachIndexed { i, v -> v.moveTo(left + i * (AppViewport.WIDTH_GUI + gap), top) }
        if (ticks == 0) screens.forEach { it.onShown() }
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xFF2B2F36.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        val mx = minecraft!!.mouseHandler.xpos() * width / minecraft!!.window.screenWidth
        val my = minecraft!!.mouseHandler.ypos() * height / minecraft!!.window.screenHeight
        pages.forEachIndexed { i, page ->
            val v = viewports[i]
            graphics.drawString(font, page.caption, v.left, v.top - 10, 0xFFC8CDD5.toInt(), false)
            v.render(graphics) { canvas: AppCanvas ->
                val (px, py) = page.pointer?.invoke(screens[i]) ?: (v.toDpX(mx) to v.toDpY(my))
                screens[i].render(canvas, px, py, partialTick)
            }
        }
    }

    override fun tick() {
        ticks++
        screens.forEach { it.tick() }
    }

    private fun pageAt(x: Double, y: Double): Int = viewports.indexOfFirst { it.contains(x, y) }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        active = pageAt(mouseX, mouseY)
        val i = active.takeIf { it >= 0 } ?: return false
        return screens[i].mousePressed(viewports[i].toDpX(mouseX), viewports[i].toDpY(mouseY), button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val i = active.takeIf { it >= 0 } ?: return false
        return screens[i].mouseReleased(viewports[i].toDpX(mouseX), viewports[i].toDpY(mouseY), button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val i = active.takeIf { it >= 0 } ?: return false
        val v = viewports[i]
        return screens[i].mouseDragged(v.toDpX(mouseX), v.toDpY(mouseY), button, v.toDp(dragX), v.toDp(dragY))
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val i = pageAt(mouseX, mouseY).takeIf { it >= 0 } ?: return false
        return screens[i].mouseScrolled(viewports[i].toDpX(mouseX), viewports[i].toDpY(mouseY), scrollX.toFloat(), scrollY.toFloat())
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val screen = screens.getOrNull(active)
        if (screen != null && screen.keyPressed(keyCode, scanCode, modifiers)) return true
        // As the phone does: with a text field focused, only Esc reaches the host.
        if (screen?.hasTextFocus == true && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        screens.getOrNull(active)?.charTyped(codePoint, modifiers) ?: false

    override fun removed() {
        screens.forEach {
            it.onHidden()
            it.onClosed()
        }
    }

    /** Calls [action] on the hosted screen at [index], for capture scripts that set a state up. */
    fun <T : AppScreen> withScreen(index: Int, action: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        action(screens[index] as T)
    }
}
