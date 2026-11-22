package fr.bde_teknantes.lnb.phone.api.client

import fr.bde_teknantes.lnb.phone.client.apps.ScreenHost

/**
 * One screen of an app: it draws the app viewport and reacts to the player. Extend it and override what you need;
 * only [render] is required.
 *
 * ```kotlin
 * class NotesScreen : AppScreen() {
 *     override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
 *         // draw the list of notes
 *     }
 *
 *     override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
 *         phone.push(NoteScreen(noteAt(y) ?: return false))
 *         return true
 *     }
 * }
 * ```
 *
 * **Coordinates** are in dp, local to the app viewport: (0, 0) is its top-left corner and it measures
 * [AppCanvas.WIDTH] x [AppCanvas.HEIGHT] dp (see [AppCanvas]).
 *
 * **Lifecycle.** A screen is created by your code, then given to the phone (by [PhoneApp]'s factories,
 * [Phone.push] or [Phone.replace]). From then on:
 * - [onShown] when it becomes the screen the player sees: when it opens, when the screen above it is popped, when
 *   the phone is opened again on it;
 * - [onHidden] when it stops being seen but stays alive: a screen is pushed above it, or the phone is put away;
 * - [onClosed], once, when it leaves the phone for good: popped or replaced, the player went home or disconnected, the
 *   app was opened again (from home or a notification), or the app failed. A closed screen is never shown again.
 *
 * [onShown] and [onHidden] alternate, starting with [onShown]; a screen that is shown when it closes gets [onHidden]
 * before [onClosed]. A screen may also close without ever being shown: one placed under the top of a stack opened for a
 * notification ([AppTargetScreens]), when the player leaves before going back to it. While a screen is shown, it gets
 * [tick] and the player's input.
 *
 * **Errors.** If any of these methods throws, the phone writes the error to the log, closes every screen of your app
 * and returns to the home screen; the rest of the phone and the game carry on, and the player can open the app again.
 * A screen that had already left the stack (popped or replaced, still drawn during its slide-out, or in its own
 * [onClosed]) only takes itself down: the error is logged and the app carries on. Either way, after throwing once, a
 * screen is never called again.
 *
 * Everything here runs on the client thread.
 */
abstract class AppScreen {
    // Set once, when the phone takes the screen. Never cleared: a closed screen keeps a handle that ignores calls.
    internal var host: ScreenHost? = null

    // Set when one of the screen's methods threw: the phone doesn't call it again.
    internal var failed = false

    /**
     * The phone this screen is on, to open another screen, go back, go home or put the phone away. Available once
     * the phone has the screen (from [onShown] on, and in every input method), not in the constructor.
     *
     * @throws IllegalStateException when read before the screen was given to the phone.
     */
    protected val phone: Phone
        get() = host ?: throw IllegalStateException(
            "${javaClass.name} isn't on the phone yet: use `phone` from onShown or later, not in the constructor",
        )

    /** The screen became the one the player sees. Also the place to start what needs [phone] (it isn't set yet in the constructor). */
    open fun onShown() {}

    /** The screen is no longer seen, but stays alive with its state: another screen is above it, or the phone was put away. */
    open fun onHidden() {}

    /** The screen left the phone for good. Stop what it started (listeners, requests); it won't be shown again. */
    open fun onClosed() {}

    /** Called 20 times a second while the screen is shown. */
    open fun tick() {}

    /**
     * Draws the screen, every frame while it is shown.
     *
     * The phone may also call it briefly while the screen is not the one shown, to draw it under or beside another
     * one during a slide (150 ms), including just after [onClosed]: when the screen is popped or replaced, and when
     * the app is opened again from a notification while this screen was on top. Draw from your state as it is, and
     * don't free in [onClosed] what drawing needs. Conversely, the phone may skip it while the screen is shown, when
     * it can't draw apps (at GUI scale 1 it shows a message instead).
     *
     * @param mouseX where the pointer is, in viewport dp; it can lie outside the viewport.
     * @param mouseY see [mouseX].
     * @param partialTick how far the game is between the last tick and the next one (0 to 1), to make animations
     * that advance in [tick] smooth.
     */
    abstract fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float)

    /**
     * A mouse button was pressed over the viewport.
     *
     * @param button 0 for the left button, 1 for the right one, 2 for the middle one.
     * @return true if the screen used the click.
     */
    open fun mousePressed(x: Float, y: Float, button: Int): Boolean = false

    /**
     * A mouse button was released. The pointer may have left the viewport since the press.
     *
     * @return true if the screen used it.
     */
    open fun mouseReleased(x: Float, y: Float, button: Int): Boolean = false

    /**
     * The mouse moved with a button held down, after a press on the viewport. The pointer may be outside the viewport.
     *
     * @param dragX how far the pointer moved since the last call, in dp.
     * @param dragY see [dragX].
     * @return true if the screen used it.
     */
    open fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean = false

    /**
     * The mouse wheel turned over the viewport.
     *
     * @param scrollY notches turned, positive away from the player (towards the top of a list). Lists on the phone
     * scroll by 36 dp per notch.
     * @param scrollX the same for horizontal scrolling, rare.
     * @return true if the screen used it.
     */
    open fun mouseScrolled(x: Float, y: Float, scrollX: Float, scrollY: Float): Boolean = false

    /**
     * A key was pressed (or repeats while held). Codes are GLFW's, as everywhere in Minecraft (`GLFW.GLFW_KEY_ENTER`);
     * `Screen.hasControlDown()` and its siblings read the modifiers.
     *
     * The phone's own keys reach the screen first: Esc, Backspace and the key that opens the phone. Return true to
     * keep them, otherwise the phone does what they normally do (Esc and the phone key put the phone away, Backspace
     * goes back one screen). So a screen with a menu or dialog open closes it on Esc and returns true. While
     * [hasTextFocus] is true, the phone key and Backspace never do their usual thing, whatever this returns: they
     * belong to the text field.
     *
     * @return true if the screen used the key.
     */
    open fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false

    /**
     * Whether a text field of this screen has the keyboard focus right now. Read by the phone at each key press, while
     * the screen is shown.
     *
     * While it is true, the phone key and Backspace no longer put the phone away or go back: they are left to the
     * screen, which gets them in [keyPressed] (and letters in [charTyped]) as usual. So a player can type a sentence
     * with the letter they bound the phone key to, or erase it with Backspace, without leaving. Esc keeps its meaning:
     * if [keyPressed] doesn't use it, the phone is put away.
     *
     * Override it if the screen has a text field; the default, false, suits every other screen.
     */
    open val hasTextFocus: Boolean get() = false

    /**
     * A character was typed, for text input. Comes in addition to [keyPressed], for the keys that produce text.
     *
     * @return true if the screen used it.
     */
    open fun charTyped(codePoint: Char, modifiers: Int): Boolean = false

    /**
     * Whether this screen already shows what [target] points to, as the player sees it now: the thread a reply
     * notification leads to, the tab where a new announcement simply arrives. [target] is a notification's
     * [fr.bde_teknantes.lnb.phone.api.AppTarget] for this screen's app.
     *
     * The phone asks the shown screen when a notification for its app arrives (art direction 10.3): if true, no banner
     * slides over the screen (the chime still plays) and the notification counts as read, since the player is looking
     * at its content. If false, the banner shows as usual. Answer from your state, quickly and without side effects;
     * the default, false, always lets the banner show.
     */
    open fun showsTarget(target: String): Boolean = false
}
