package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.client.ui.PressTracker
import org.lwjgl.glfw.GLFW

/**
 * The look of something clickable at a given moment. Complete for good: no value will be added, so a `when` over it
 * may list the four without an `else`. Keyboard focus is separate ([Widget.showsFocusRing]).
 */
enum class Interaction { NORMAL, HOVER, PRESSED, DISABLED }

/**
 * A widget that does something when clicked: the base of buttons, rows, tabs and cards, and of your own clickable
 * widgets. It handles the pointer and the keyboard the phone's way, and tells [render] which look to draw through
 * [interaction]:
 *
 * - hover while the pointer is on it; pressed while the left button is held on it, and for at least 100 ms in all;
 * - a click is a press and a release on the visible part of it; dragging off before releasing cancels;
 * - focused from the keyboard, Enter or Space click it;
 * - disabled, it draws [Interaction.DISABLED], ignores clicks and answers them with the "can't" sound.
 *
 * Every click plays the tap sound unless [silent]. On its own (no drawing) it makes an invisible clickable area, for
 * a row whose whole width should react.
 */
open class Clickable() : Widget() {
    /** A clickable that runs [onClick] when clicked. */
    constructor(onClick: () -> Unit) : this() {
        this.onClick = onClick
    }

    private val press = PressTracker()

    /** What a click does. */
    var onClick: (() -> Unit)? = null

    /** No tap sound on click (the widget plays its own, like a heart's chime). */
    var silent = false

    /** Forces the look, whatever the pointer does: for galleries and screenshots that show every state side by side. */
    @KitPreview
    var showAs: Interaction? = null

    /** Forces the focus ring on, for galleries like [showAs]. */
    @KitPreview
    var showFocus = false

    override val catchesPointer get() = true

    override val focusable get() = isEnabled

    /** Whether the pressed look is due now. */
    val isPressed: Boolean get() = isEnabled && press.isPressed(now, isPointerOver)

    /** The look to draw now. */
    @OptIn(KitPreview::class)
    val interaction: Interaction
        get() = showAs ?: when {
            !isEnabled -> Interaction.DISABLED
            isPressed -> Interaction.PRESSED
            hoverLook && screen?.capturing == null -> Interaction.HOVER
            else -> Interaction.NORMAL
        }

    /** Whether to draw the hover look (the pointer on it); a widget inside a row may follow the row's. */
    protected open val hoverLook: Boolean get() = isHovered

    @OptIn(KitPreview::class)
    override val showsFocusRing get() = showFocus || (isEnabled && super.showsFocusRing)

    /** Clicks the widget as the player would (sound and pressed look included), if enabled. */
    fun click() {
        if (!isEnabled) return
        press.flash(now)
        perform()
    }

    /** Hook: runs the click, playing the tap and calling [onClick]. Override to act before or instead. */
    protected open fun perform() {
        if (!silent) PhoneSounds.tap()
        onClick?.invoke()
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        if (!isEnabled) {
            PhoneSounds.error()
            return true
        }
        press.press(now)
        return true
    }

    override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !press.holding) return false
        // A click only on what the player can see of it: not hidden since the press, not scrolled away or covered.
        val over = isShowing && isPointerOver
        if (press.release(now, over) && isEnabled) perform()
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            click()
            return isEnabled
        }
        return false
    }

    override fun kitPointerLost() = press.cancel()
}
