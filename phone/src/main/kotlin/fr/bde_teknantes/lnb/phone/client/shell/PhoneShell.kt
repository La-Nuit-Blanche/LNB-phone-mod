package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.client.apps.NavigationListener
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.apps.Transition
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettings
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
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
        if (state == PhoneShellCore.State.CLOSED) return
        val now = now()
        val pose = currentPose(now) ?: return
        ShellRenderer.render(graphics, pose, now, if (interactive && state == PhoneShellCore.State.OPEN) Pointer(mouseX, mouseY) else null)
    }

    /** Draws the peeking device (art direction 10.2), if a peek shows: the HUD layer under the hotbar. */
    fun renderPeekDevice(graphics: GuiGraphics) {
        val now = now()
        val pose = alerts.peekPose(now) ?: return
        guardPeek { ShellRenderer.renderPeekDevice(graphics, pose, now) }
    }

    /** Draws the peek's banner, if a peek shows: its own HUD layer, above the chat, which would cover it. */
    fun renderPeekBanner(graphics: GuiGraphics) {
        val pose = alerts.peekPose() ?: return
        val notification = alerts.peek?.notification ?: return
        guardPeek { ShellRenderer.renderPeekBanner(graphics, pose, notification) }
    }

    private var peekFailed = false

    // The peek draws in the middle of play: an error in it costs the peek, never the game. Logged once.
    private inline fun guardPeek(draw: () -> Unit) {
        try {
            draw()
        } catch (e: Exception) {
            alerts.dropPeek()
            if (!peekFailed) {
                peekFailed = true
                LnbPhone.LOGGER.error("The phone's notification peek failed to draw; it is dropped", e)
            }
        }
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

    // Makes the screen to show when the phone closes (a fresh inventory, when the phone was opened from it). A maker
    // rather than the screen: a container screen put away is closed for good, and must not be shown again.
    private var returnTo: (() -> Screen?)? = null

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

    // Keys pressed in the phone's screen whose release it hasn't seen: a second press of one is a key repeat, which
    // screens get like a press. The phone acts on the first press only.
    private val keysHeld = HashSet<Int>()

    // The phone key, still held from the press that brought the phone out when its screen opened: its repeats (and
    // the characters they type) are for neither the phone nor the app, until it is released.
    private var phoneKeyLatched = false

    // Mouse wheel turned on the home screen and not yet turned into a page: a trackpad sends fractions of a notch.
    private var homeScroll = 0.0

    /** Unseen counts by app, read once a tick, and when each badge last started to pulse. */
    internal val counts = HashMap<ResourceLocation, Int>()
    internal val pulseStart = HashMap<ResourceLocation, Long>()

    /**
     * For the screenshot harness: holds every animation at this eased progress (0 to 1) and keeps them from ending,
     * so a capture shows an exact mid-slide frame.
     */
    internal var heldProgress: Float? = null

    /** The banner, the peek and the chime of notifications that arrive (art direction 10). */
    internal val alerts = ShellAlerts(this, host)

    fun init() {
        navigator.addListener(this)
        host.settings.addListener(::onSettingsChanged)
    }

    // ---- Opening and closing ----

    val isOut: Boolean get() = state != State.CLOSED

    /**
     * Brings the phone out, on the screen it was put away on. [returnTo] makes the screen to show when the phone
     * closes; any screen open now is closed so that the phone slides up over the game.
     */
    fun open(returnTo: (() -> Screen?)? = null) {
        if (state == State.OPEN || state == State.OPENING) return
        if (!host.inWorld) return
        this.returnTo = returnTo
        // Fonts and masks for this GUI scale, before the slide's first frames rather than during them.
        host.prewarm()
        if (host.screen != null) host.setScreen(null)
        val now = now()
        // A peeking device rises from where it peeks (14 "Open from a peek").
        val peeking = alerts.onOpening(now)
        val from = (if (state == State.CLOSING) currentPose(now) else null) ?: peeking
        val target = restingPose()
        slide = DeviceSlide.open(from ?: DevicePose(target.x, guiHeight(), 0f), now)
        state = State.OPENING
        navigator.open()
        readInPlace()
        host.play(ShellSound.OPEN)
    }

    /**
     * The phone reopens onto an app left open: its badge stays (it clears when the app is opened), but notifications
     * whose target the shown screen reports as on screen are read in place, as one arriving there would be (10.3).
     */
    private fun readInPlace() {
        val app = navigator.currentApp ?: return
        for (notification in host.unread) {
            val target = notification.target ?: continue
            if (notification.appId == app.id && navigator.showsTarget(target)) host.dismissNotification(notification)
        }
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

    /**
     * The phone key went down while no screen was open: a real press, never a key repeat (holding the key must not
     * open and close the phone over and over). The host calls it straight from the input event. During a peek it opens
     * the phone on the notification's target (2.3, P-9).
     */
    fun phoneKeyPressedInGame() {
        if (host.screen != null || !host.inWorld) return
        val peeked = alerts.peekedNotification()
        if (peeked == null) return toggle()
        // The app first, while the phone is put away, so its screens don't slide in: the device rises onto them, from
        // where the peek is. An app that fails to start leaves the phone where it was, and it opens there all the same.
        navigator.openApp(peeked.appId, peeked.target)
        open()
    }

    /**
     * The inventory's phone button (art direction 11). The inventory is closed as its own close would (the server
     * hears it: the crafting grid and the cursor's item go back to the inventory), the phone opens over the game, and
     * [reopen] makes a fresh inventory when it closes.
     *
     * With unread notifications (P-5) the phone opens on the home screen, so the badges show where the news is, but
     * only if it was put away on home or on an app's root screen: leaving a deeper screen would end the app's session
     * and lose what the player was writing there (2.3 keeps it until disconnection). Then it opens where it was left,
     * with the newest unread notification as the banner, so the news still shows.
     */
    fun openFromInventory(reopen: () -> Screen?) {
        if (state == State.OPEN || state == State.OPENING || !host.inWorld) return
        val news = unreadNow() > 0
        val keepsScreen = news && navigator.canGoBack
        if (news && !keepsScreen) navigator.home()
        host.closeScreen()
        open(returnTo = reopen)
        if (keepsScreen) host.unread.lastOrNull()?.let(alerts::showUnread)
    }

    /** Unread notifications of every app, read from the store now rather than from last tick's [counts]. */
    internal fun unreadNow(): Int = host.apps.apps.sumOf { PhoneBadges.count(it.id) }

    /** A notification arrived while the player is connected (not one of the login sync). */
    fun notificationArrived(notification: PhoneNotification) = alerts.arrived(notification)

    /** The notification store changed in any way: a banner or peek whose notification is gone goes away. */
    fun notificationsChanged() = alerts.notificationsChanged()

    /** Disconnection: everything forgotten at once, with no animation. */
    fun reset() {
        state = State.CLOSED
        slide = null
        returnTo = null
        page = 0
        clearSlide()
        clearPointer()
        alerts.reset()
        warmedScale = 0.0
    }

    private fun startClosing() {
        val screen = host.screen
        val back = returnTo
        slideDown()
        // State first: the phone screen's removed() then knows the shell closed it. Closed while still rising, the
        // phone has no screen yet, but the one it was opened from comes back all the same.
        if (host.isPhoneScreen(screen) || (screen == null && back != null)) {
            host.setScreen(back?.invoke())
            // A mod refused the screen it came from (ScreenEvent.Opening): give the game back rather than leave the
            // phone's screen up with nothing behind it. Going back to the game can't be refused.
            if (host.isPhoneScreen(host.screen)) host.setScreen(null)
        }
    }

    /**
     * Another screen took over while the phone rose, or the phone's own screen was refused or replaced: the device
     * slides down behind whatever is shown, which is left alone.
     */
    private fun standDown() {
        slideDown()
        navigator.close()
    }

    private fun slideDown() {
        val now = now()
        slide = DeviceSlide.close(currentPose(now) ?: restingPose().let { DevicePose(it.x, guiHeight(), 0f) }, now)
        state = State.CLOSING
        returnTo = null
        clearPointer()
        alerts.onPutAway()
        host.play(ShellSound.CLOSE)
    }

    /** The phone's screen was replaced by another one (death screen, another mod's screen): put away at once. */
    fun onScreenRemoved() {
        if (state != State.OPEN) return
        state = State.CLOSED
        slide = null
        returnTo = null
        clearSlide()
        clearPointer()
        alerts.onPutAway()
        navigator.close()
    }

    /**
     * The phone's screen became the game's screen: opened by the shell, or shown again by someone else. A vanilla
     * dialog an app opens (a link to confirm, another mod's settings) has the phone's screen as its parent and shows
     * that same screen again when it closes: the phone is then taken back as open, standing, without a slide, so the
     * player never sits in a phone screen that draws nothing.
     */
    fun onScreenShown() {
        keysHeld.clear()
        phoneKeyLatched = host.isPhoneKeyDown()
        homeScroll = 0.0
        if (state == State.OPEN) return
        state = State.OPEN
        slide = null
        returnTo = null
        clearPointer()
        if (!navigator.isOpen) navigator.open()
    }

    // ---- Ticks ----

    fun tick() {
        ticks++
        if (state != State.CLOSED && !host.inWorld) {
            navigator.close()
            reset()
        }
        updateCounts()
        finishAnimations(now())
        alerts.tick()
        if (navigator.isOpen && appsSupported()) navigator.tick()
        warmUp()
    }

    // The GUI scale the smooth layer was last prepared for, 0 for none since the last world.
    private var warmedScale = 0.0

    /**
     * Prepares the smooth layer when the player arrives in a world and when the GUI scale changes: moments where the
     * tens of milliseconds it costs go unnoticed (the world just loaded, the window is being resized), rather than on
     * the first peek, which comes in the middle of play.
     */
    private fun warmUp() {
        if (!host.inWorld || !appsSupported() || host.guiScale == warmedScale) return
        warmedScale = host.guiScale
        host.prewarm()
    }

    /** At the start of each frame: a slide that has just ended opens the phone's screen at once, not a tick later. */
    fun frame() = finishAnimations(now())

    private fun finishAnimations(now: Float) {
        // Any screen that opens while the device rises (pause menu, inventory, chat, a container the server opens,
        // the death screen) sends it back down at once, behind that screen.
        val shown = host.screen
        if (state == State.OPENING && shown != null && !host.isPhoneScreen(shown)) standDown()
        if (heldProgress != null) return
        if (screenSlide?.isDone(now) == true) clearSlide()
        val current = slide ?: return
        if (!current.isDone(now)) return
        slide = null
        when (state) {
            State.OPENING -> {
                state = State.OPEN
                host.setScreen(host.createPhoneScreen())
                // A mod may refuse or replace the phone's screen (ScreenEvent.Opening): then the phone isn't open.
                if (!host.isPhoneScreen(host.screen)) standDown()
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
    fun currentPose(now: Float = now(), placement: Placement? = null): DevicePose? {
        // Away, nothing to place: the settings aren't read (the HUD asks every frame).
        if (state == State.CLOSED) return null
        val target = placement ?: host.settings.current.placement
        val moving = slide ?: return restingPose(target)
        val progress = heldProgress ?: moving.progress(now)
        return if (state == State.CLOSING) {
            moving.pose(progress, Motion.snap(moving.fromX), guiHeight(), 0f)
        } else {
            val resting = restingPose(target)
            moving.pose(progress, resting.x, resting.y, resting.scrim)
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
        if (state != State.OPEN) return false
        val pose = currentPose() ?: return false
        val dx = x - pose.x
        val dy = y - pose.y
        val px = floor(dx).toInt()
        val py = floor(dy).toInt()
        // A phone key on a mouse button closes the phone, except the left button on the device, which works the phone.
        if (host.isPhoneMouseButton(button) && !(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Device.inDevice(px, py))) {
            close()
            return true
        }
        val now = now()
        when {
            // A click anywhere outside the device puts it away, in both placements (2.3).
            !Device.inDevice(px, py) -> close()
            Device.inHomeButton(px, py) -> if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                homeButtonHeld = true
                homeButtonShown = true
                homeButtonStart = now
            }
            Device.inViewport(dx, dy) -> if (alerts.mousePressed(dpX(x, pose), dpY(y, pose), button, now)) {
                // The notification banner lies over the viewport: the click is its, not the screen's under it.
            } else if (navigator.isHome) {
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
        alerts.mouseReleased(dpX(x, pose), dpY(y, pose), button, now(), Device.inViewport(dx, dy))
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
            // Released on the banner, which came down over the icon since: the press is cancelled.
            val onBanner = alerts.bannerContains(dpX(x, pose), dpY(y, pose), now())
            if (under == pressed && app != null && navigator.isHome && !onBanner) {
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
        if (state != State.OPEN) return false
        val pose = currentPose() ?: return false
        if (!Device.inViewport(x - pose.x, y - pose.y)) return true
        // Over the banner the wheel is the banner's: the screen under it doesn't scroll.
        if (alerts.mouseScrolled(dpX(x, pose), dpY(y, pose), now())) return true
        if (navigator.isHome) {
            // One page per whole notch, however finely the wheel or trackpad reports it; turning back starts over.
            homeScroll = if (homeScroll != 0.0 && Math.signum(scrollY) != Math.signum(homeScroll)) scrollY else homeScroll + scrollY
            // A hair under a notch counts: ten tenths don't add up to exactly 1 in floating point.
            while (homeScroll >= 1.0 - NOTCH_SLACK) {
                page = HomeGrid.pageAfterScroll(page(), 1.0, appCount())
                homeScroll -= 1.0
            }
            while (homeScroll <= -1.0 + NOTCH_SLACK) {
                page = HomeGrid.pageAfterScroll(page(), -1.0, appCount())
                homeScroll += 1.0
            }
        } else if (showsApp()) {
            navigator.mouseScrolled(dpX(x, pose), dpY(y, pose), scrollX.toFloat(), scrollY.toFloat())
        }
        return true
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val repeat = !keysHeld.add(keyCode)
        if (state != State.OPEN) {
            // The phone's screen up while the phone isn't standing open must never trap the player: Esc puts the
            // screen away, every other key is left to the game.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && host.isPhoneScreen(host.screen)) {
                host.setScreen(null)
                return true
            }
            return false
        }
        val phoneKey = host.isPhoneKey(keyCode, scanCode)
        if (phoneKey && phoneKeyLatched) return true
        val app = showsApp()
        // Read before the app sees the key: the key may move the focus.
        val textFocus = app && navigator.hasTextFocus
        val used = app && navigator.keyPressed(keyCode, scanCode, modifiers)
        // The phone's own actions answer the first press only: a held key repeats, and the app gets the repeats.
        when {
            // The app closes its menu or dialog on Esc and keeps the key; otherwise Esc puts the phone away (2.3).
            keyCode == GLFW.GLFW_KEY_ESCAPE -> if (!used && !repeat) close()
            // While a text field has the focus, the phone key and Backspace belong to it.
            phoneKey -> if (!used && !textFocus && !repeat) close()
            keyCode == GLFW.GLFW_KEY_BACKSPACE -> if (!used && !textFocus && !repeat) back()
            // While the player types, no key goes on to the game or to other mods' key handlers.
            else -> return used || textFocus
        }
        // Always true for the phone's own keys, so the game doesn't act on them as well (pause on Esc, the key mapping).
        return true
    }

    fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        keysHeld.remove(keyCode)
        // Without its modifier: Ctrl may come up before the key itself.
        if (host.isPhoneKey(keyCode, scanCode, withModifier = false)) phoneKeyLatched = false
        return false
    }

    /** Backspace: back one screen; at GUI scale 1, where app screens can't be seen, the only back there is: home. */
    private fun back() {
        if (appsSupported()) navigator.back() else navigator.home()
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        state == State.OPEN && !phoneKeyLatched && showsApp() && navigator.charTyped(codePoint, modifiers)

    private fun clearPointer() {
        pressedApp = null
        pressedAppHeld = false
        homeButtonHeld = false
        homeButtonShown = false
        viewportButtons.clear()
        homeScroll = 0.0
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

private const val NOTCH_SLACK = 1e-6

/** The mouse pointer over the open phone, window GUI px. */
internal data class Pointer(val x: Double, val y: Double)
