package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import net.minecraft.resources.ResourceLocation
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the phone shows, independently of how it is drawn: the home screen, or one app with its stack of screens; and
 * whether the phone is open. Plain logic, so it can be tested without a game window.
 *
 * Rules (art direction 2.3):
 * - Opening an app starts it afresh, from its root screen or, for a notification with a target, from the stack the
 *   app gives for it; the app that was open, if any, is closed, even when it is the same app.
 * - [back] (Backspace, or the header back button through [fr.bde_teknantes.lnb.phone.api.client.Phone.pop]) goes
 *   back one screen; on an app's root screen it does nothing.
 * - [home] closes the app instantly.
 * - [close] puts the phone away and keeps everything: [open] shows the same screen again.
 * - [reset] (disconnection) forgets everything.
 *
 * Every call into an app's code goes through a guard: if it throws, the error is logged, the whole app is closed, the
 * phone goes home and listeners hear [NavigationListener.onAppFailed]. A screen already off the stack is only logged.
 * Either way the screen is never called again. One broken app never takes the phone down.
 *
 * Client thread only; [isClientThread] enforces it for the calls that change the state.
 */
internal class PhoneNavigator(
    private val registry: AppRegistry,
    private val isClientThread: () -> Boolean = { true },
) {
    private var session: AppSession? = null
    private var shown: AppScreen? = null
    private var syncing = false

    // Keys held down in the phone's screen, and those of them held when the shown screen last changed: a held key
    // belongs to the screen it was pressed on, so its repeats and characters must not act on the next one (a held
    // Space that opens a conversation must not type spaces into its field). Swallowing characters: the last key event
    // was a dead key's, and the characters that follow it are its.
    private val heldKeys = HashSet<Int>()
    private val deadKeys = HashSet<Int>()
    private var swallowChars = false
    // Copy on write: a listener may add another from inside a callback.
    private val listeners = CopyOnWriteArrayList<NavigationListener>()

    /** Whether the phone is out (open), as opposed to put away. */
    var isOpen = false
        private set

    /** The open app, or null on the home screen. */
    val currentApp: PhoneApp? get() = session?.app

    /** The open app's screens, its root first; empty on the home screen. */
    val screens: List<AppScreen> get() = session?.screens?.toList().orEmpty()

    /** The top screen of the open app (kept while the phone is put away), or null on the home screen. */
    val currentScreen: AppScreen? get() = session?.screens?.lastOrNull()

    /** The screen that last got onShown and not onHidden since: [currentScreen] while the phone is open. */
    val shownScreen: AppScreen? get() = shown

    val isHome: Boolean get() = session == null

    /** Whether [back] would do something: there is a screen under the current one. */
    val canGoBack: Boolean get() = (session?.screens?.size ?: 0) > 1

    fun addListener(listener: NavigationListener) {
        listeners += listener
    }

    /** The phone comes out, on the screen it was put away on. */
    fun open() {
        checkThread()
        if (isOpen) return
        isOpen = true
        notifyListeners { it.onPhoneOpened() }
        syncVisibility()
    }

    /** The phone is put away; the app and its screens stay as they are. */
    fun close() {
        checkThread()
        if (!isOpen) return
        isOpen = false
        notifyListeners { it.onPhoneClosed() }
        syncVisibility()
    }

    /**
     * Opens the registered app [id], at [target] if given: the call for a notification the player opened. Returns
     * false if there is no such app or it failed to start; true as [openApp] does.
     */
    fun openApp(id: ResourceLocation, target: String? = null): Boolean {
        checkThread()
        val app = registry[id] ?: run {
            LnbPhone.LOGGER.warn("There is no phone app {} to open", id)
            return false
        }
        return openApp(app, target)
    }

    /**
     * Starts [app] afresh, closing the app that was open (even if it is [app] itself). With a [target] the app knows,
     * it starts on the stack of screens it gives for it ([PhoneApp.createTargetScreens]); otherwise, on its root
     * screen. If the app can't give its screens, nothing changes and false is returned.
     *
     * True means the request was accepted and the app's screens are in place, not that the app is on screen now: its
     * first onShown may still fail (closing it at once), and a screen or listener may navigate elsewhere meanwhile.
     * Read the state afterwards ([currentApp], [shownScreen]) when it matters.
     */
    fun openApp(app: PhoneApp, target: String? = null): Boolean {
        checkThread()
        val stack = try {
            startingScreens(app, target)
        } catch (e: Throwable) {
            rethrowIfFatal(e)
            appFailed(app, "creating its screens" + target?.let { " for target \"$it\"" }.orEmpty(), e)
            return false
        }
        val unique = Collections.newSetFromMap(IdentityHashMap<AppScreen, Boolean>())
        if (!stack.all { it.host == null && unique.add(it) }) {
            appFailed(app, "creating its screens", IllegalStateException(REUSED_SCREEN))
            return false
        }
        val previous = session
        val outgoing = previous?.screens?.lastOrNull()
        val closing = previous?.let(::endSession).orEmpty()
        val opened = AppSession(app)
        session = opened
        stack.forEach { attach(it, opened) }
        notifyListeners { it.onTransition(Transition.FORWARD, outgoing) }
        notifyListeners { it.onAppOpened(app) }
        closeScreens(closing)
        syncVisibility()
        return true
    }

    /** Back one screen in the open app. Returns false, doing nothing, on an app's root screen or at home. */
    fun back(): Boolean {
        checkThread()
        val top = currentScreen ?: return false
        return pop(top.host!!)
    }

    /** Closes the open app and shows the home screen, at once. */
    fun home() {
        checkThread()
        val ending = session ?: return
        val outgoing = ending.screens.lastOrNull()
        val closing = endSession(ending)
        notifyListeners { it.onTransition(Transition.INSTANT, outgoing) }
        closeScreens(closing)
        syncVisibility()
    }

    /** Forgets everything, as when the player disconnects: phone put away, app closed, home next time. */
    fun reset() {
        close()
        home()
    }

    // Called through a screen's Phone handle. A closed screen's handle is ignored, so a late callback can't navigate.

    internal fun push(from: ScreenHost, screen: AppScreen) {
        checkThread()
        if (ignored(from, "push")) return
        require(screen.host == null) { REUSED_SCREEN }
        val outgoing = from.session.screens.last()
        attach(screen, from.session)
        notifyListeners { it.onTransition(Transition.FORWARD, outgoing) }
        syncVisibility()
    }

    internal fun pop(from: ScreenHost): Boolean {
        checkThread()
        if (ignored(from, "pop")) return false
        val stack = from.session.screens
        val index = stack.indexOf(from.screen)
        if (index <= 0) return false
        val outgoing = stack.last()
        val closing = detachFrom(stack, index)
        notifyListeners { it.onTransition(Transition.BACK, outgoing) }
        closeScreens(closing)
        syncVisibility()
        return true
    }

    internal fun replace(from: ScreenHost, screen: AppScreen) {
        checkThread()
        if (ignored(from, "replace")) return
        require(screen.host == null) { REUSED_SCREEN }
        val stack = from.session.screens
        val outgoing = stack.last()
        val closing = detachFrom(stack, stack.indexOf(from.screen))
        attach(screen, from.session)
        notifyListeners { it.onTransition(Transition.FORWARD, outgoing) }
        closeScreens(closing)
        syncVisibility()
    }

    internal fun home(from: ScreenHost) {
        checkThread()
        if (!ignored(from, "home")) home()
    }

    internal fun close(from: ScreenHost) {
        checkThread()
        if (!ignored(from, "close")) close()
    }

    // Calls from the phone's screen into the app, each behind the guard.

    /**
     * Draws [screen], which may be a screen other than the shown one (one sliding in or out). A screen that already
     * failed is skipped.
     */
    fun render(screen: AppScreen, canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
        guard(screen, "render", Unit) { screen.render(canvas, mouseX, mouseY, partialTick) }
    }

    fun tick() {
        val screen = shown ?: return
        guard(screen, "tick", Unit) { screen.tick() }
    }

    fun mousePressed(x: Float, y: Float, button: Int): Boolean =
        dispatch("mousePressed") { it.mousePressed(x, y, button) }

    fun mouseReleased(x: Float, y: Float, button: Int): Boolean =
        dispatch("mouseReleased") { it.mouseReleased(x, y, button) }

    fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean =
        dispatch("mouseDragged") { it.mouseDragged(x, y, button, dragX, dragY) }

    fun mouseScrolled(x: Float, y: Float, scrollX: Float, scrollY: Float): Boolean =
        dispatch("mouseScrolled") { it.mouseScrolled(x, y, scrollX, scrollY) }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = keyPressed(keyCode, scanCode, modifiers, repeat = false)

    /**
     * A key press, or with [repeat] the key repeating as it is held: the screen reads it as [AppScreen.isKeyRepeat].
     * A key held since before the shown screen changed is dead: its repeats, and the characters they type, reach no
     * screen until it is released. Returns true for a dead key, which nothing else should act on either.
     */
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int, repeat: Boolean): Boolean {
        keyDown(keyCode)
        if (keyCode in deadKeys) {
            swallowChars = true
            return true
        }
        swallowChars = false
        val before = shown
        val used = dispatch("keyPressed") {
            it.keyRepeat = repeat
            try {
                it.keyPressed(keyCode, scanCode, modifiers)
            } finally {
                it.keyRepeat = false
            }
        }
        // The press opened another screen (a row pushed one): the character it types next is not for that screen.
        if (shown !== before) swallowChars = true
        return used
    }

    /** A character typed; dropped when it comes from a dead key (see [keyPressed]). */
    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (swallowChars) return true
        return dispatch("charTyped") { it.charTyped(codePoint, modifiers) }
    }

    /** [keyCode] went down in the phone's screen, wherever it goes next (home, an app, the phone's own keys). */
    fun keyDown(keyCode: Int) {
        heldKeys += keyCode
    }

    /** [keyCode] came up: pressed again, it is a new press, for whichever screen is shown. */
    fun keyUp(keyCode: Int) {
        heldKeys -= keyCode
        deadKeys -= keyCode
    }

    /**
     * The phone's screen came (back) while [keys] were held down, pressed elsewhere (in the game, in a vanilla dialog
     * an app opened): they are dead for the app until released, as keys held through a change of screen are.
     */
    fun keysHeldElsewhere(keys: Set<Int>) {
        heldKeys.clear()
        heldKeys += keys
        deadKeys.clear()
        deadKeys += keys
        swallowChars = false
    }

    /**
     * Whether the shown screen has a text field with the focus ([AppScreen.hasTextFocus]): then the phone key and
     * Backspace belong to it and must not close the phone or go back. False at home and with the phone put away.
     */
    val hasTextFocus: Boolean get() = dispatch("hasTextFocus") { it.hasTextFocus }

    /**
     * Whether the shown screen says it already shows [target] ([AppScreen.showsTarget]), for a notification of the
     * [currentApp]. False at home and with the phone put away; the caller checks that the notification is for the
     * current app.
     */
    fun showsTarget(target: String): Boolean = dispatch("showsTarget") { it.showsTarget(target) }

    private inline fun dispatch(what: String, action: (AppScreen) -> Boolean): Boolean {
        val screen = shown ?: return false
        return guard(screen, what, false) { action(screen) }
    }

    private fun startingScreens(app: PhoneApp, target: String?): List<AppScreen> {
        if (target != null) {
            val screens = app.createTargetScreens(target)
            if (screens.isNotEmpty()) return screens
        }
        return listOf(app.createRootScreen())
    }

    // Each listener behind its own guard: one that throws must neither escape into the game's input handling nor
    // leave the navigation half done, nor keep the other listeners from hearing.
    private inline fun notifyListeners(event: (NavigationListener) -> Unit) {
        for (listener in listeners) {
            try {
                event(listener)
            } catch (e: Throwable) {
                rethrowIfFatal(e)
                LnbPhone.LOGGER.error("Phone navigation listener {} failed", listener.javaClass.name, e)
            }
        }
    }

    private fun attach(screen: AppScreen, session: AppSession) {
        screen.host = ScreenHost(this, session, screen)
        session.screens += screen
    }

    /** Takes the screens from [index] up out of [stack], detached at once and returned top first for [closeScreens]. */
    private fun detachFrom(stack: MutableList<AppScreen>, index: Int): List<AppScreen> {
        val removed = stack.subList(index, stack.size)
        val closing = removed.reversed()
        removed.clear()
        closing.forEach { it.host!!.closed = true }
        return closing
    }

    /** Ends [ending]: its screens are detached at once and returned top first, for [closeScreens] to notify. */
    private fun endSession(ending: AppSession): List<AppScreen> {
        ending.ended = true
        if (session === ending) session = null
        val closing = ending.screens.reversed()
        ending.screens.clear()
        closing.forEach { it.host!!.closed = true }
        return closing
    }

    // State first, callbacks after: whatever a callback does, it sees a consistent phone and closed screens can't
    // navigate.
    private fun closeScreens(closing: List<AppScreen>) {
        for (screen in closing) {
            if (shown === screen) {
                shown = null
                guard(screen, "onHidden", Unit) { screen.onHidden() }
            }
            guard(screen, "onClosed", Unit) { screen.onClosed() }
        }
    }

    /**
     * Brings onShown / onHidden in line with the state: the shown screen is the top screen while the phone is open.
     * Callbacks may navigate again; the loop runs until nothing changes, and nested calls leave the work to it.
     */
    private fun syncVisibility() {
        if (syncing) return
        syncing = true
        try {
            var steps = 0
            while (true) {
                val wanted = if (isOpen) currentScreen else null
                val current = shown
                if (current === wanted) break
                if (++steps > MAX_SYNC_STEPS && wanted != null) {
                    steps = 0
                    screenFailed(wanted, "onShown", IllegalStateException("its screens keep opening one another"))
                    continue
                }
                if (current != null) {
                    shown = null
                    guard(current, "onHidden", Unit) { current.onHidden() }
                } else if (wanted != null) {
                    shown = wanted
                    // What is held now was pressed for another screen.
                    deadKeys += heldKeys
                    guard(wanted, "onShown", Unit) { wanted.onShown() }
                }
            }
        } finally {
            syncing = false
        }
    }

    private inline fun <T> guard(screen: AppScreen, what: String, fallback: T, block: () -> T): T {
        if (screen.failed) return fallback
        return try {
            block()
        } catch (e: Throwable) {
            rethrowIfFatal(e)
            screenFailed(screen, what, e)
            fallback
        }
    }

    private fun screenFailed(screen: AppScreen, what: String, error: Throwable) {
        screen.failed = true
        val host = screen.host
        // A screen already off the stack (popped, replaced, or from an app that was left) takes only itself down: the
        // likeliest bug there, onClosed freeing what the slide-out still draws, mustn't send the player home on every
        // back.
        val onStack = host != null && !host.closed && host.session === session
        LnbPhone.LOGGER.error(
            if (onStack) "Phone app {} failed in {}.{}; the phone closes the app"
            else "Phone app {} failed in {}.{}, a screen already closed; it won't be called again",
            host?.session?.app?.id, screen.javaClass.name, what, error,
        )
        if (!onStack) return
        val failing = host!!.session
        val outgoing = failing.screens.lastOrNull()
        val closing = endSession(failing)
        notifyListeners { it.onTransition(Transition.INSTANT, outgoing) }
        notifyListeners { it.onAppFailed(failing.app, error) }
        closeScreens(closing)
        syncVisibility()
    }

    private fun appFailed(app: PhoneApp, what: String, error: Throwable) {
        LnbPhone.LOGGER.error("Phone app {} failed {}; it stays closed", app.id, what, error)
        notifyListeners { it.onAppFailed(app, error) }
    }

    private fun ignored(from: ScreenHost, what: String): Boolean {
        if (!from.closed) return false
        LnbPhone.LOGGER.debug("{} from closed screen {} of {} ignored", what, from.screen.javaClass.name, from.session.app.id)
        return true
    }

    private fun checkThread() {
        check(isClientThread()) {
            "The phone can only be used from the client thread, not from ${Thread.currentThread().name}: " +
                "hop over with Minecraft.getInstance().execute { ... }"
        }
    }

    private companion object {
        const val REUSED_SCREEN =
            "This screen was already given to the phone: push and the app factory need a new AppScreen each time"

        // Far more onShown / onHidden rounds than any sane navigation needs in one go.
        const val MAX_SYNC_STEPS = 64
    }
}

/** One visit to an app, from opening it to leaving it. */
internal class AppSession(val app: PhoneApp) {
    val screens = ArrayList<AppScreen>()
    var ended = false
}

/** How what is on screen changed, for the phone's animations. */
internal enum class Transition {
    /** A screen slides in from the right over [NavigationListener.onTransition]'s outgoing screen (or home). */
    FORWARD,

    /** The outgoing screen slides out to the right, revealing the current one. */
    BACK,

    /** The change shows at once (home button, app failure). */
    INSTANT,
}

/**
 * Hears what the navigator does: the phone's screen, for drawing; notifications, to clear an app's badge.
 *
 * - Don't navigate synchronously from a callback (open, close, openApp, home...): the navigator is in the middle of
 *   a change, and other listeners have not heard it yet. If a reaction must navigate, defer it (to the next tick, or
 *   with `Minecraft.getInstance().execute`).
 * - Read the navigator's state when you act ([PhoneNavigator.currentScreen], [PhoneNavigator.shownScreen],
 *   [PhoneNavigator.isOpen]) rather than rebuilding it from the sequence of events: when screens navigate from their
 *   own callbacks, events can arrive in an order that only the final state makes sense of.
 * - A callback that throws is logged and skipped; it doesn't stop the navigation or the other listeners.
 */
internal interface NavigationListener {
    /**
     * What the phone shows changed. [outgoing] was shown before, null for the home screen; it may be closed already.
     * Also heard while the phone is put away (an app opened for a peek before [PhoneNavigator.open], a reset): there
     * is nothing to animate then.
     */
    fun onTransition(transition: Transition, outgoing: AppScreen?) {}

    /** [app] was opened, by any route (home icon, banner, peek). */
    fun onAppOpened(app: PhoneApp) {}

    fun onPhoneOpened() {}

    /** The phone was put away, possibly by an app ([fr.bde_teknantes.lnb.phone.api.client.Phone.close]). */
    fun onPhoneClosed() {}

    /** [app] threw and was closed (or couldn't start). */
    fun onAppFailed(app: PhoneApp, error: Throwable) {}
}
