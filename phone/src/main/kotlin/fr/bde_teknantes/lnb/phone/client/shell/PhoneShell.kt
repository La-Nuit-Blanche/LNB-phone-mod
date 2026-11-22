package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.client.apps.NavigationListener
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.apps.Transition
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettings
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import kotlin.math.floor

/** The phone in the game. */
internal object PhoneShell : PhoneShellCore(MinecraftShellHost) {
    /**
     * Draws the phone if it is out. [interactive] when the phone's screen has the mouse: hover states follow
     * ([mouseX], [mouseY]), window GUI px.
     */
    fun render(graphics: GuiGraphics, interactive: Boolean, mouseX: Double, mouseY: Double) {
        val now = now()
        val pose = currentPose(now) ?: return
        ShellRenderer.render(graphics, pose, now, if (interactive && state == PhoneShellCore.State.OPEN) Pointer(mouseX, mouseY) else null)
    }
}

/**
 * The phone as the player sees it: put away, sliding up, open, sliding down; where the device stands; what the
 * viewport is animating; what the pointer is pressing. It drives the [PhoneNavigator] and listens to it. What it needs
 * from the game comes through [host], so that tests can run it without one.
 *
 * The device only takes the mouse once it has finished sliding up (art direction 17, P-1): while it moves, the game
 * keeps the mouse and the device is drawn over the HUD; once it stands, the phone's screen opens, frees the cursor and
 * stops the player. Closing gives the mouse back at once and the device slides down over the HUD.
 *
 * Client thread only.
 */
internal open class PhoneShellCore(private val host: ShellHost) : NavigationListener {
    enum class State { CLOSED, OPENING, OPEN, CLOSING }

    private val navigator: PhoneNavigator get() = host.navigator

    var state = State.CLOSED
        private set

    /** Client ticks since the game started: the clock of every shell animation, with the partial tick. */
    var ticks = 0L
        private set

    // The device's movement, or null while it stands still (open) or is away (closed).
    private var slide: DeviceSlide? = null

    // The viewport's slide between two screens, and the screen it involves besides the current one: the incoming
    // screen covers [slideOutgoing] going forward (null: the home screen), the outgoing one leaves going back.
    internal var screenSlide: ScreenSlide? = null
        private set
    internal var slideOutgoing: AppScreen? = null
        private set

    // The screen to put back when the phone closes (the inventory, when the phone was opened from it).
    private var returnTo: Screen? = null

    /** Home page shown, kept while the phone is put away. */
    internal var page = 0

    // Home screen pointer state.
    internal var pressedApp: Int? = null
        private set
    private var pressedAppStart = 0f
    private var pressedAppHeld = false
    private var homeButtonStart = 0f
    private var homeButtonHeld = false
    private var homeButtonShown = false

    // Mouse buttons pressed on the app viewport, whose drags and release go to the app wherever the pointer goes.
    private val viewportButtons = HashSet<Int>()

    /** Unseen counts by app, read once a tick, and when each badge last started to pulse. */
    internal val counts = HashMap<ResourceLocation, Int>()
    internal val pulseStart = HashMap<ResourceLocation, Long>()

    /**
     * For the screenshot harness: holds every animation at this eased progress (0 to 1) and keeps them from ending,
     * so a capture shows an exact mid-slide frame.
     */
    internal var heldProgress: Float? = null

    fun init() {
        navigator.addListener(this)
        host.settings.addListener(::onSettingsChanged)
    }

    // ---- Opening and closing ----

    val isOut: Boolean get() = state != State.CLOSED

    /**
     * Brings the phone out, on the screen it was put away on. [returnTo] is the screen to show again when the phone
     * closes; any screen open now is closed so that the phone slides up over the game.
     */
    fun open(returnTo: Screen? = null) {
        if (state == State.OPEN || state == State.OPENING) return
        if (!host.inWorld) return
        this.returnTo = returnTo
        if (host.screen != null) host.setScreen(null)
        val now = now()
        val from = if (state == State.CLOSING) currentPose(now) else null
        val target = restingPose()
        slide = DeviceSlide.open(from ?: DevicePose(target.x, guiHeight(), 0f), now)
        state = State.OPENING
        navigator.open()
        host.play(ShellSound.OPEN)
    }

    /** Puts the phone away: it slides down, and the next [open] shows the same screen. */
    fun close() {
        if (state == State.CLOSED || state == State.CLOSING) return
        startClosing()
        navigator.close()
    }

    /** The phone key pressed while the game has the mouse. */
    fun toggle() {
        if (state == State.CLOSED || state == State.CLOSING) open() else close()
    }

    /** Disconnection: everything forgotten at once, with no animation. */
    fun reset() {
        state = State.CLOSED
        slide = null
        returnTo = null
        page = 0
        clearSlide()
        clearPointer()
    }

    private fun startClosing() {
        val now = now()
        slide = DeviceSlide.close(currentPose(now) ?: restingPose().let { DevicePose(it.x, guiHeight(), 0f) }, now)
        state = State.CLOSING
        clearPointer()
        host.play(ShellSound.CLOSE)
        val screen = host.screen
        val back = returnTo
        returnTo = null
        // State first: the phone screen's removed() then knows the shell closed it. Closed while still rising, the
        // phone has no screen yet, but the one it was opened from comes back all the same.
        if (host.isPhoneScreen(screen) || (screen == null && back != null)) host.setScreen(back)
    }

    /** The phone's screen was replaced by another one (death screen, another mod's screen): put away at once. */
    fun onScreenRemoved() {
        if (state != State.OPEN) return
        state = State.CLOSED
        slide = null
        returnTo = null
        clearSlide()
        clearPointer()
        navigator.close()
    }

    // ---- Ticks ----

    fun tick() {
        ticks++
        if (state != State.CLOSED && !host.inWorld) reset()
        while (host.consumePhoneKeyClick()) {
            if (host.screen == null && host.inWorld) toggle()
        }
        updateCounts()
        if (heldProgress == null) finishAnimations()
        if (navigator.isOpen && appsSupported()) navigator.tick()
    }

    private fun finishAnimations() {
        val now = ticks.toFloat()
        if (screenSlide?.isDone(now) == true) clearSlide()
        val current = slide ?: return
        if (!current.isDone(now)) return
        slide = null
        when (state) {
            State.OPENING -> if (host.screen == null) {
                state = State.OPEN
                host.setScreen(host.createPhoneScreen())
            } else {
                // Something else took the screen while the phone rose (the pause menu, the inventory): stand down.
                state = State.CLOSED
                returnTo = null
                navigator.close()
            }
            State.CLOSING -> state = State.CLOSED
            else -> {}
        }
    }

    private fun updateCounts() {
        for (app in host.apps.apps) {
            val count = PhoneBadges.count(app.id)
            val before = counts.put(app.id, count) ?: 0
            if (count > before) pulseStart[app.id] = ticks
        }
    }

    // ---- Where the device is ----

    fun now(): Float = ticks + host.partialTick

    internal fun guiWidth() = host.guiWidth

    internal fun guiHeight() = host.guiHeight

    /** Where the device stands when open, in the current placement and window. */
    private fun restingPose(placement: Placement = host.settings.current.placement): DevicePose {
        val position = PhoneLayout.devicePosition(placement, guiWidth(), guiHeight())
        return DevicePose(position.x, position.y, PhoneLayout.scrimAmount(placement))
    }

    /**
     * The device's pose at [now], or null while it is away. Rising or gliding, it moves towards where [placement] puts
     * it: the current setting, except while that setting is being changed.
     */
    fun currentPose(now: Float = now(), placement: Placement = host.settings.current.placement): DevicePose? {
        if (state == State.CLOSED) return null
        val moving = slide ?: return restingPose(placement)
        val progress = heldProgress ?: moving.progress(now)
        return if (state == State.CLOSING) {
            moving.pose(progress, Motion.snap(moving.fromX), guiHeight(), 0f)
        } else {
            val target = restingPose(placement)
            moving.pose(progress, target.x, target.y, target.scrim)
        }
    }

    /** Eased progress of the viewport's slide at [now]. */
    internal fun screenSlideProgress(slide: ScreenSlide, now: Float): Float = heldProgress ?: slide.progress(now)

    private fun onSettingsChanged(old: PhoneSettings, new: PhoneSettings) {
        if (old.placement == new.placement || (state != State.OPEN && state != State.OPENING)) return
        val now = now()
        // From where it is now: the pose under the old placement, or mid-way if it was already moving. The store holds
        // the new placement already, so a move under way is measured towards the old one it was heading for.
        val from = slide?.let { currentPose(now, old.placement) } ?: restingPose(old.placement)
        slide = DeviceSlide.glide(from, now)
    }

    // ---- Navigator ----

    override fun onTransition(transition: Transition, outgoing: AppScreen?) {
        viewportButtons.clear()
        // Heard while the phone is put away too (an app opened for a notification): nothing to animate then.
        if (!navigator.isOpen || transition == Transition.INSTANT) return clearSlide()
        screenSlide = ScreenSlide(transition == Transition.FORWARD, now())
        slideOutgoing = outgoing
    }

    override fun onPhoneClosed() {
        // An app put the phone away (Phone.close). Not from inside the navigator's callback: on the next frame.
        if (state == State.OPEN || state == State.OPENING) {
            host.later {
                if ((state == State.OPEN || state == State.OPENING) && !navigator.isOpen) startClosing()
            }
        }
    }

    private fun clearSlide() {
        screenSlide = null
        slideOutgoing = null
    }

    // ---- Input from the phone's screen ----

    /** Whether apps can be shown: not at GUI scale 1, where 1 dp would be half a pixel (art direction 8.1). */
    fun appsSupported(): Boolean = host.guiScale >= 2.0

    private fun showsApp() = !navigator.isHome && appsSupported()

    fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
        val pose = currentPose() ?: return false
        if (host.isPhoneMouseButton(button)) {
            close()
            return true
        }
        val dx = x - pose.x
        val dy = y - pose.y
        val px = floor(dx).toInt()
        val py = floor(dy).toInt()
        val now = now()
        when {
            // A click anywhere outside the device puts it away, in both placements (2.3).
            !Device.inDevice(px, py) -> close()
            Device.inHomeButton(px, py) -> if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                homeButtonHeld = true
                homeButtonShown = true
                homeButtonStart = now
            }
            Device.inViewport(dx, dy) -> if (navigator.isHome) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    val app = HomeGrid.appAt(dx - Device.VIEWPORT_X, dy - Device.VIEWPORT_Y, page(), appCount())
                    if (app != null) {
                        pressedApp = app
                        pressedAppHeld = true
                        pressedAppStart = now
                    }
                }
            } else if (showsApp()) {
                viewportButtons += button
                navigator.mousePressed(dpX(x, pose), dpY(y, pose), button)
            }
            // The rest of the bezel and the status bar: nothing.
        }
        return true
    }

    fun mouseReleased(x: Double, y: Double, button: Int): Boolean {
        val pose = currentPose() ?: return false
        val dx = x - pose.x
        val dy = y - pose.y
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && homeButtonHeld) {
            homeButtonHeld = false
            if (Device.inHomeButton(floor(dx).toInt(), floor(dy).toInt())) {
                host.play(ShellSound.TAP)
                navigator.home()
            }
        }
        val pressed = pressedApp
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressed != null && pressedAppHeld) {
            pressedAppHeld = false
            val under = if (Device.inViewport(dx, dy)) {
                HomeGrid.appAt(dx - Device.VIEWPORT_X, dy - Device.VIEWPORT_Y, page(), appCount())
            } else {
                null
            }
            val app = host.apps.apps.getOrNull(pressed)
            if (under == pressed && app != null && navigator.isHome) {
                host.play(ShellSound.TAP)
                navigator.openApp(app)
            }
        }
        if (viewportButtons.remove(button) && showsApp()) navigator.mouseReleased(dpX(x, pose), dpY(y, pose), button)
        return true
    }

    fun mouseDragged(x: Double, y: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val pose = currentPose() ?: return false
        if (button in viewportButtons && showsApp()) {
            navigator.mouseDragged(dpX(x, pose), dpY(y, pose), button, (dragX * 2).toFloat(), (dragY * 2).toFloat())
        }
        return true
    }

    fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val pose = currentPose() ?: return false
        if (!Device.inViewport(x - pose.x, y - pose.y)) return true
        if (navigator.isHome) {
            page = HomeGrid.pageAfterScroll(page(), scrollY, appCount())
        } else if (showsApp()) {
            navigator.mouseScrolled(dpX(x, pose), dpY(y, pose), scrollX.toFloat(), scrollY.toFloat())
        }
        return true
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (state != State.OPEN) return true
        val app = showsApp()
        // Read before the app sees the key: the key may move the focus.
        val textFocus = app && navigator.hasTextFocus
        val used = app && navigator.keyPressed(keyCode, scanCode, modifiers)
        when {
            // The app closes its menu or dialog on Esc and keeps the key; otherwise Esc puts the phone away (2.3).
            keyCode == GLFW.GLFW_KEY_ESCAPE -> if (!used) close()
            // While a text field has the focus, the phone key and Backspace belong to it.
            host.isPhoneKey(keyCode, scanCode) -> if (!used && !textFocus) close()
            keyCode == GLFW.GLFW_KEY_BACKSPACE -> if (!used && !textFocus) navigator.back()
            else -> return used
        }
        // Always true for the phone's own keys, so the game doesn't act on them as well (pause on Esc, the key mapping).
        return true
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean = showsApp() && navigator.charTyped(codePoint, modifiers)

    private fun clearPointer() {
        pressedApp = null
        pressedAppHeld = false
        homeButtonHeld = false
        homeButtonShown = false
        viewportButtons.clear()
    }

    // Viewport dp from window GUI px: 2 dp per GUI px, from the viewport's top-left.
    private fun dpX(x: Double, pose: DevicePose) = ((x - pose.x - Device.VIEWPORT_X) * 2).toFloat()

    private fun dpY(y: Double, pose: DevicePose) = ((y - pose.y - Device.VIEWPORT_Y) * 2).toFloat()

    internal fun appCount() = host.apps.apps.size

    /** The home page shown, kept inside the pages there are. */
    internal fun page(): Int = page.coerceIn(0, HomeGrid.pageCount(appCount()) - 1)

    /** Whether the pressed look of [pressedApp] shows at [now]: while held, and at least 100 ms. */
    internal fun pressedAppShown(now: Float): Boolean =
        pressedApp != null && (pressedAppHeld || now < pressedAppStart + Motion.PRESS_MIN_TICKS)

    internal fun homeButtonPressedShown(now: Float): Boolean {
        if (homeButtonShown && !homeButtonHeld && now >= homeButtonStart + Motion.PRESS_MIN_TICKS) homeButtonShown = false
        return homeButtonShown
    }
}

/** The mouse pointer over the open phone, window GUI px. */
internal data class Pointer(val x: Double, val y: Double)
