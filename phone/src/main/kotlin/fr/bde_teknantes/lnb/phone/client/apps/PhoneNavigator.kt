package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import net.minecraft.resources.ResourceLocation

/**
 * What the phone shows, independently of how it is drawn: the home screen, or one app with its stack of screens; and
 * whether the phone is open. Plain logic, so it can be tested without a game window.
 *
 * Rules (art direction 2.3):
 * - Opening an app starts it afresh from its root screen; the app that was open, if any, is closed.
 * - [back] (Backspace, or the header back button through [fr.bde_teknantes.lnb.phone.api.client.Phone.pop]) goes
 *   back one screen; on an app's root screen it does nothing.
 * - [home] closes the app instantly.
 * - [close] puts the phone away and keeps everything: [open] shows the same screen again.
 * - [reset] (disconnection) forgets everything.
 *
 * Every call into an app's code goes through a guard: if it throws, the error is logged, the whole app is closed, the
 * phone goes home and listeners hear [NavigationListener.onAppFailed]. One broken app never takes the phone down.
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
    private val listeners = ArrayList<NavigationListener>()

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
        listeners.forEach { it.onPhoneOpened() }
        syncVisibility()
    }

    /** The phone is put away; the app and its screens stay as they are. */
    fun close() {
        checkThread()
        if (!isOpen) return
        isOpen = false
        listeners.forEach { it.onPhoneClosed() }
        syncVisibility()
    }

    /** Opens the registered app [id], for a notification. Returns false if there is no such app or it failed to start. */
    fun openApp(id: ResourceLocation): Boolean {
        val app = registry[id] ?: run {
            LnbPhone.LOGGER.warn("There is no phone app {} to open", id)
            return false
        }
        return openApp(app)
    }

    /**
     * Starts [app] from its root screen, closing the app that was open (even if it is [app] itself). If the app can't
     * give a root screen, nothing changes and false is returned.
     */
    fun openApp(app: PhoneApp): Boolean {
        checkThread()
        val root = try {
            app.createRootScreen()
        } catch (e: Throwable) {
            rethrowFatal(e)
            appFailed(app, "creating its first screen", e)
            return false
        }
        if (root.host != null) {
            appFailed(app, "creating its first screen", IllegalStateException(REUSED_SCREEN))
            return false
        }
        val previous = session
        val outgoing = previous?.screens?.lastOrNull()
        val closing = previous?.let(::endSession).orEmpty()
        val opened = AppSession(app)
        session = opened
        attach(root, opened)
        listeners.forEach { it.onTransition(Transition.FORWARD, outgoing) }
        listeners.forEach { it.onAppOpened(app) }
        closeScreens(closing)
        syncVisibility()
        return true
    }

    /** Back one screen in the open app. Returns false, doing nothing, on an app's root screen or at home. */
    fun back(): Boolean {
        val top = currentScreen ?: return false
        return pop(top.host!!)
    }

    /** Closes the open app and shows the home screen, at once. */
    fun home() {
        checkThread()
        val ending = session ?: return
        val outgoing = ending.screens.lastOrNull()
        val closing = endSession(ending)
        listeners.forEach { it.onTransition(Transition.INSTANT, outgoing) }
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
        listeners.forEach { it.onTransition(Transition.FORWARD, outgoing) }
        syncVisibility()
    }

    internal fun pop(from: ScreenHost): Boolean {
        checkThread()
        if (ignored(from, "pop")) return false
        val stack = from.session.screens
        val index = stack.indexOf(from.screen)
        if (index <= 0) return false
        val outgoing = stack.last()
        val removed = stack.subList(index, stack.size)
        val closing = removed.reversed()
        removed.clear()
        closing.forEach { it.host!!.closed = true }
        listeners.forEach { it.onTransition(Transition.BACK, outgoing) }
        closeScreens(closing)
        syncVisibility()
        return true
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

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        dispatch("keyPressed") { it.keyPressed(keyCode, scanCode, modifiers) }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        dispatch("charTyped") { it.charTyped(codePoint, modifiers) }

    private inline fun dispatch(what: String, action: (AppScreen) -> Boolean): Boolean {
        val screen = shown ?: return false
        return guard(screen, what, false) { action(screen) }
    }

    private fun attach(screen: AppScreen, session: AppSession) {
        screen.host = ScreenHost(this, session, screen)
        session.screens += screen
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
            rethrowFatal(e)
            screenFailed(screen, what, e)
            fallback
        }
    }

    private fun screenFailed(screen: AppScreen, what: String, error: Throwable) {
        screen.failed = true
        val host = screen.host
        LnbPhone.LOGGER.error(
            "Phone app {} failed in {}.{}; the phone closes the app",
            host?.session?.app?.id, screen.javaClass.name, what, error,
        )
        val failing = host?.session ?: return
        // A screen drawn during its slide-out belongs to a session that is already over: nothing left to close.
        if (failing.ended || failing !== session) return
        val outgoing = failing.screens.lastOrNull()
        val closing = endSession(failing)
        listeners.forEach { it.onTransition(Transition.INSTANT, outgoing) }
        listeners.forEach { it.onAppFailed(failing.app, error) }
        closeScreens(closing)
        syncVisibility()
    }

    private fun appFailed(app: PhoneApp, what: String, error: Throwable) {
        LnbPhone.LOGGER.error("Phone app {} failed {}; it stays closed", app.id, what, error)
        listeners.forEach { it.onAppFailed(app, error) }
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

    // Out of memory and the like are the game's problem, not the app's: let them through. A stack overflow is the app's.
    private fun rethrowFatal(e: Throwable) {
        if (e is VirtualMachineError && e !is StackOverflowError) throw e
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

/** Hears what the navigator does: the phone's screen, for drawing; notifications, to clear an app's badge. */
internal interface NavigationListener {
    /** What the phone shows changed. [outgoing] was shown before, null for the home screen; it may be closed already. */
    fun onTransition(transition: Transition, outgoing: AppScreen?) {}

    /** [app] was opened, by any route (home icon, banner, peek). */
    fun onAppOpened(app: PhoneApp) {}

    fun onPhoneOpened() {}

    /** The phone was put away, possibly by an app ([fr.bde_teknantes.lnb.phone.api.client.Phone.close]). */
    fun onPhoneClosed() {}

    /** [app] threw and was closed (or couldn't start). */
    fun onAppFailed(app: PhoneApp, error: Throwable) {}
}
