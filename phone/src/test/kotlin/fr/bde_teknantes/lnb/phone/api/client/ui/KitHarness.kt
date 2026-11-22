package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.FakeCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.client.ui.TextEnvironment
import org.lwjgl.glfw.GLFW

/**
 * Every code point half an em wide, no kerning: 6.5 dp in [TextStyle.BODY], like [FakeCanvas] measures. A text area
 * 232 dp wide (208 inside its padding) then holds exactly 32 characters per line.
 */
internal object MonoFont : FontMetrics {
    override fun advance(codePoint: Int) = 0.5f
    override fun kerning(left: Int, right: Int) = 0f
    override val ascender = 0.8f
    override val descender = 0.2f
}

/** The clipboard, Shift and platform, as a test sets them. */
internal class FakeTextEnvironment : TextEnvironment {
    override var clipboard = ""
    override var shiftDown = false
    override var mac = false
}

/** Puts the fakes in place of the game for a widget test, and takes them away. */
internal object KitFakes {
    fun install(): FakeTextEnvironment {
        TextMetrics.fonts = { MonoFont }
        PhoneSounds.enabled = { false }
        return FakeTextEnvironment().also { TextEnvironment.current = it }
    }

    fun uninstall() {
        TextMetrics.fonts = { SmoothResources.font(it) }
        PhoneSounds.enabled = { true }
        TextEnvironment.current = TextEnvironment.Game
    }
}

/** A [FakeCanvas] that remembers the text drawn and the rectangles filled, with their colours. */
internal class RecordingCanvas : FakeCanvas() {
    data class Text(val text: String, val x: Float, val y: Float, val style: TextStyle, val color: Int)

    val texts = ArrayList<Text>()
    val fills = ArrayList<Int>()

    fun clear() {
        texts.clear()
        fills.clear()
    }

    fun drew(text: String) = texts.any { it.text == text }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float {
        texts += Text(text, x, y, style, color)
        return super.drawText(text, x, y, style, color)
    }

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fills += color
        super.fillRect(x, y, w, h, color)
    }

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        fills += color
        super.fillRoundRect(x, y, w, h, radius, color)
    }
}

/**
 * A [KitScreen] driven like the phone drives one: frames (which move the pointer and the clock), ticks of 50 ms, the
 * mouse and the keyboard. The clock only advances through [advance], as in the game, where it moves with ticks.
 */
internal open class TestScreen : KitScreen() {
    val canvas = RecordingCanvas()
    var keysToScreen = 0
        private set

    override fun onKey(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        keysToScreen++
        return false
    }

    /** Draws a frame with the pointer at ([x], [y]). */
    fun frame(x: Float = mouseX, y: Float = mouseY) {
        canvas.clear()
        render(canvas, x, y, 0f)
    }

    /** Lets [ms] pass (whole ticks), then draws a frame. */
    fun advance(ms: Float) {
        repeat((ms / 50f).toInt()) { tick() }
        frame()
    }

    fun moveTo(x: Float, y: Float) = frame(x, y)

    fun press(x: Float, y: Float, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT): Boolean {
        frame(x, y)
        return mousePressed(x, y, button)
    }

    fun drag(x: Float, y: Float, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT): Boolean {
        val dx = x - mouseX
        val dy = y - mouseY
        val used = mouseDragged(x, y, button, dx, dy)
        frame(x, y)
        return used
    }

    fun release(x: Float = mouseX, y: Float = mouseY, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT): Boolean {
        val used = mouseReleased(x, y, button)
        frame(x, y)
        return used
    }

    fun click(x: Float, y: Float) {
        press(x, y)
        release(x, y)
    }

    fun wheel(x: Float, y: Float, notches: Float): Boolean {
        frame(x, y)
        return mouseScrolled(x, y, 0f, notches)
    }

    fun key(keyCode: Int, modifiers: Int = 0): Boolean = keyPressed(keyCode, 0, modifiers)

    /** Types [text] as Minecraft hands it over: one UTF-16 unit at a time, a pair's halves one after the other. */
    fun type(text: String) {
        for (c in text) charTyped(c, 0)
    }
}

/** Shortcuts for modifier masks. */
internal const val SHIFT = GLFW.GLFW_MOD_SHIFT
internal const val CTRL = GLFW.GLFW_MOD_CONTROL
