package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.client.ui.PressTracker
import org.lwjgl.glfw.GLFW

/** The look of something clickable at a given moment. */
enum class Interaction { NORMAL, HOVER, PRESSED, DISABLED }

/**
 * A widget that does something when clicked: the base of buttons, rows, tabs and cards, and of your own clickable
 * widgets. It handles the pointer and the keyboard the phone's way, and tells [render] which look to draw through
 * [interaction]:
 *
 * - hover while the pointer is on it; pressed while the left button is held on it, and for at least 100 ms in all;
 * - a click is a press and a release on it; dragging off before releasing cancels;
 * - focused from the keyboard, Enter or Space click it;
 * - disabled, it draws [Interaction.DISABLED], ignores clicks and answers them with the "can't" sound.
 *
 * Every click plays the tap sound unless [silent]. On its own (no drawing) it makes an invisible clickable area, for
 * a row whose whole width should react.
 *
 * @param onClick What a click does.
 */
open class Clickable(var onClick: (() -> Unit)? = null) : Widget() {
    private val press = PressTracker()

    /** No tap sound on click (the widget plays its own, like a heart's chime). */
    var silent = false

    /**
     * Forces the look, whatever the pointer does: for galleries and screenshots that show every state side by side.
     * Leave null in apps.
     */
    var showAs: Interaction? = null

    /** Forces the focus ring on, for galleries like [showAs]. */
    var showFocus = false

    override val catchesPointer get() = true

    override val focusable get() = enabled

    /** Whether the pressed look is due now. */
    val isPressed: Boolean get() = press.isPressed(now, isPointerOver)

    /** The look to draw now. */
    val interaction: Interaction
        get() = showAs ?: when {
            !enabled -> Interaction.DISABLED
            isPressed -> Interaction.PRESSED
            isHovered && screen?.capturing == null -> Interaction.HOVER
            else -> Interaction.NORMAL
        }

    override val showsFocusRing get() = showFocus || (enabled && super.showsFocusRing)

    // Over the widget, even while it holds the press (when the screen reports no other hover).
    private val isPointerOver: Boolean
        get() {
            val s = screen ?: return false
            val px = s.mouseX - toViewportX(0f)
            val py = s.mouseY - toViewportY(0f)
            return px >= 0f && py >= 0f && px < width && py < height && isShowing
        }

    /** Clicks the widget as the player would (sound and pressed look included), if enabled. */
    fun click() {
        if (!enabled) return
        press.flash(now)
        perform()
    }

    /** Runs the click: plays the tap and calls [onClick]. Override to act before or instead. */
    protected open fun perform() {
        if (!silent) PhoneSounds.tap()
        onClick?.invoke()
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        if (!enabled) {
            PhoneSounds.error()
            return true
        }
        press.press(now)
        return true
    }

    override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !press.holding) return false
        val over = x >= 0f && y >= 0f && x < width && y < height
        if (press.release(now, over) && enabled) perform()
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            click()
            return enabled
        }
        return false
    }

    override fun pointerLost() {
        press.cancel()
        super.pointerLost()
    }

    override var enabled: Boolean
        get() = super.enabled
        set(value) {
            if (!value) press.cancel()
            super.enabled = value
        }
}
