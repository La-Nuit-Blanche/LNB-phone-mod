package fr.bde_teknantes.lnb.phone.api.client

import fr.bde_teknantes.lnb.phone.api.client.ui.Header

/**
 * The phone, as seen by one of your screens: how a screen moves to another one. Reach it with [AppScreen.phone].
 *
 * An app is a stack of screens. Its first screen (the root) comes from [PhoneApp]'s factory; [push] opens another one
 * on top, [pop] goes back to the one underneath. The phone draws the back transition and offers the usual ways back:
 * Backspace when no text field has focus, and the back arrow a kit [Header] shows by itself on every screen but the
 * root. A screen drawn without the kit draws its own arrow when [canGoBack] is true, and calls [pop].
 *
 * The handle belongs to the screen it was given to. Once that screen is closed (the player went back past it, went
 * home, disconnected, or the app failed), every call on it does nothing. So a late answer from the server that calls
 * [push] can't drag the player back into an app they have left.
 *
 * Call it from the client thread only (the one that runs screens and ticks): from anywhere else, it throws. To act on
 * an answer that arrives on another thread, hop over first with `Minecraft.getInstance().execute { ... }`.
 *
 * Only the phone makes these: later versions add to it without breaking your code. To test navigation, play your app
 * with the phone's test support (`fr.bde_teknantes.lnb.phone.testing.PhoneTester`), whose phone is the real one.
 */
abstract class Phone internal constructor() {
    /**
     * Whether [pop] would lead somewhere: there is a screen under this one. A [Header] shows its back arrow then, and a
     * screen drawn without the kit draws one. False on the app's root screen, and once this screen is closed.
     */
    abstract val canGoBack: Boolean

    /**
     * Opens [screen] on top of this app's stack, sliding in from the right. The current screen stays underneath with
     * its state, and comes back when the new one is popped.
     *
     * [screen] must be a new instance: a screen that is already on the phone, or was closed, can't be pushed again
     * (it throws [IllegalArgumentException]).
     */
    abstract fun push(screen: AppScreen)

    /**
     * Closes this screen and goes back to the one underneath, sliding out to the right. Screens pushed above this one
     * close with it. Does nothing on the app's root screen: to leave the app, use [home].
     */
    abstract fun pop()

    /**
     * Swaps this screen for [screen], which slides in from the right and takes its place in the stack: back from
     * [screen] leads to the screen that was under this one. For a compose screen that gives way to the post it just
     * created, or a loading screen to what it loaded. Screens pushed above this one close too; on the root screen,
     * [screen] becomes the new root.
     *
     * This screen is closed, so its handle does nothing afterwards. [screen] must be a new instance, as for [push].
     */
    abstract fun replace(screen: AppScreen)

    /** Leaves the app for the home screen, instantly. Every screen of the app is closed. */
    abstract fun home()

    /**
     * Puts the phone away. The app keeps its screens: the next time the player opens the phone, it reopens on the
     * app's top screen, with its state; unless meanwhile they disconnected or changed the game's language, which
     * close the app (the phone then opens on home).
     */
    abstract fun close()
}
