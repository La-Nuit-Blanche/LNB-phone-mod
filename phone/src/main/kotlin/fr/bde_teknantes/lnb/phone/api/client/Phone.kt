package fr.bde_teknantes.lnb.phone.api.client

/**
 * The phone, as seen by one of your screens: how a screen moves to another one. Reach it with [AppScreen.phone].
 *
 * An app is a stack of screens. Its first screen (the root) comes from [PhoneApp]'s factory; [push] opens another one
 * on top, [pop] goes back to the one underneath. The phone draws the back transition and offers the usual ways back
 * (Backspace when no text field has focus); drawing a back button in your header is up to you (on every screen but
 * the root), and it should call [pop].
 *
 * The handle belongs to the screen it was given to. Once that screen is closed (the player went back past it, went
 * home, disconnected, or the app failed), every call on it does nothing. So a late answer from the server that calls
 * [push] can't drag the player back into an app they have left.
 *
 * Call it from the client thread only (the one that runs screens and ticks): from anywhere else, it throws. To act on
 * an answer that arrives on another thread, hop over first with `Minecraft.getInstance().execute { ... }`.
 */
interface Phone {
    /**
     * Opens [screen] on top of this app's stack, sliding in from the right. The current screen stays underneath with
     * its state, and comes back when the new one is popped.
     *
     * [screen] must be a new instance: a screen that is already on the phone, or was closed, can't be pushed again
     * (it throws [IllegalArgumentException]).
     */
    fun push(screen: AppScreen)

    /**
     * Closes this screen and goes back to the one underneath, sliding out to the right. Screens pushed above this one
     * close with it. Does nothing on the app's root screen: to leave the app, use [home].
     */
    fun pop()

    /** Leaves the app for the home screen, instantly. Every screen of the app is closed. */
    fun home()

    /**
     * Puts the phone away. The app keeps its screens: the phone reopens on this one, with its state, the next time the
     * player opens it (until they disconnect).
     */
    fun close()
}
