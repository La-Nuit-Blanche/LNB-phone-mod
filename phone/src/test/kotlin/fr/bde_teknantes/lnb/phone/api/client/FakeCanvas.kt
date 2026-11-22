package fr.bde_teknantes.lnb.phone.api.client

import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import net.minecraft.resources.ResourceLocation

/**
 * An [AppCanvas] that draws nothing and measures text as if every character were half the font size wide, for tests
 * that call `render` without a game window. [calls] counts the drawing calls.
 */
open class FakeCanvas : AppCanvas() {
    var calls = 0
        private set

    override val width = AppCanvas.WIDTH
    override val height = AppCanvas.HEIGHT
    override val pixelsPerDp = 1.5f

    override fun measure(text: String, style: TextStyle) = text.codePointCount(0, text.length) * style.size / 2f
    override fun truncate(text: String, style: TextStyle, maxWidth: Float) = text
    override fun wrap(text: String, style: TextStyle, maxWidth: Float) = text.split('\n')
    override fun wrap(text: String, style: TextStyle, maxWidth: Float, maxLines: Int) = wrap(text, style, maxWidth).take(maxLines)

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) { calls++ }
    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) { calls++ }
    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, color: Int) { calls++ }
    override fun fillCircle(x: Float, y: Float, diameter: Float, color: Int) { calls++ }
    override fun hairline(x0: Float, x1: Float, y: Float, color: Int) { calls++ }
    override fun verticalHairline(x: Float, y0: Float, y1: Float, color: Int) { calls++ }
    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float {
        calls++
        return measure(text, style)
    }
    override fun drawIcon(icon: Icon, x: Float, y: Float, color: Int, size: Float) { calls++ }
    override fun drawSpinner(x: Float, y: Float, size: Float, color: Int, turns: Float) { calls++ }
    override fun drawTexture(
        texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float,
        color: Int, u0: Float, v0: Float, u1: Float, v1: Float, linear: Boolean,
    ) { calls++ }
    override fun drawPlayerFace(skin: ResourceLocation, x: Float, y: Float, size: Float) { calls++ }
    override fun drawShadow(shadow: Shadow, x: Float, y: Float, w: Float, h: Float) { calls++ }
    override fun pushClip(x: Float, y: Float, w: Float, h: Float) {}
    override fun popClip() {}
    override fun pushOffset(dx: Float, dy: Float) {}
    override fun popOffset() {}
    override fun pushAlpha(alpha: Float) {}
    override fun popAlpha() {}
}
