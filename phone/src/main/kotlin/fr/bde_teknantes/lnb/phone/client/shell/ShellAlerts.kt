package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.notifications.AlertSettings
import fr.bde_teknantes.lnb.phone.client.notifications.AlertVisual
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLayout
import fr.bde_teknantes.lnb.phone.client.notifications.BannerMotion
import fr.bde_teknantes.lnb.phone.client.notifications.BannerTimeline
import fr.bde_teknantes.lnb.phone.client.notifications.NotificationAlerts
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import org.lwjgl.glfw.GLFW

/**
 * What the phone does when a notification arrives while the player is connected (art direction 10, flows P-8 to P-11
 * and P-14): the banner over the open phone, the peek of the closed one, the chime. [NotificationAlerts.decide] says
 * what a notification deserves; this says what can be shown right now, runs it over time and answers the player's
 * hand on it. Part of [PhoneShellCore], which drives it; plain logic over the shell's [host], tested through its fake.
 *
 * Rules the art direction leaves open, settled here:
 * - At GUI scale 1 there is no smooth layer to draw a banner on: no banner and no peek; the chime plays and the badge
 *   waits, as with the preview off. A banner or a peek showing when the scale falls to 1 goes at once.
 * - The peek rises only over the game itself. With another screen open (chat, a container, the pause menu, the death
 *   screen) there is none: the chime plays and the badge waits. A screen that opens during a peek sends it back down.
 * - While the device slides down, a peek waits for it to be away, then rises.
 * - While it slides up, the phone counts as open: the banner comes down over the rising screen.
 * - Several notifications at once (an announcement and a reply in the same tick): the last one shows, each swapping
 *   the content in place and restarting the hold as 10.2's queue says, and the chime plays once.
 * - A click on the banner removes it at once, as the notification's screen slides in.
 *
 * Client thread only.
 */
internal class ShellAlerts(private val shell: PhoneShellCore, private val host: ShellHost) {
    /** A notification on screen and its life: coming out, holding, going back. */
    class Shown(notification: PhoneNotification, val timeline: BannerTimeline) {
        /** What it shows; a newer notification takes its place during its life (10.2 "Queue"). */
        var notification: PhoneNotification = notification
            internal set
    }

    /** The banner over the open phone (10.3), or null. */
    var banner: Shown? = null
        private set

    /** The peek of the closed phone (10.2), or null. */
    var peek: Shown? = null
        private set

    // A peek decided while the device was still sliding down: it rises once the device is away.
    private var pendingPeek: PhoneNotification? = null

    /** Whether the left button went down on the banner and hasn't come up yet: its pressed look. */
    var bannerPressed = false
        private set

    // The tick the chime last played on: notifications of one tick chime once.
    private var lastChime = Long.MIN_VALUE

    /** [notification] arrived, after the store took it. */
    fun arrived(notification: PhoneNotification) {
        val installed = host.apps[notification.appId] != null
        val drawable = shell.appsSupported()
        var situation = NotificationAlerts.situation(host.navigator, notification)
        // At GUI scale 1 no app screen is drawn: nothing the player sees shows the target, so it isn't read in place.
        if (!drawable) situation = situation.copy(targetOnScreen = false)
        val settings = host.settings.current
        val alert = NotificationAlerts.decide(installed, situation, AlertSettings(settings.sounds, settings.preview))
        if (alert.chime && lastChime != shell.ticks) {
            lastChime = shell.ticks
            host.play(ShellSound.NOTIFICATION)
        }
        if (!alert.keep) host.dismissNotification(notification)
        if (!drawable) return
        when (alert.visual) {
            AlertVisual.BANNER -> showBanner(notification)
            AlertVisual.PEEK -> showPeek(notification)
            AlertVisual.NONE -> {}
        }
    }

    /**
     * Shows [notification], already waiting as a badge, as the banner over the phone that is opening: no chime, it
     * isn't news that just arrived. For the inventory button when the phone opens on a screen it keeps (P-5).
     */
    fun showUnread(notification: PhoneNotification) {
        if (shell.appsSupported()) showBanner(notification)
    }

    private fun showBanner(notification: PhoneNotification) {
        val now = nowMs()
        val current = banner
        if (current != null && !current.timeline.isFinished(now)) {
            current.notification = notification
            current.timeline.restart(now)
        } else {
            banner = Shown(notification, BannerTimeline(BannerMotion.IN_PHONE, now))
            bannerPressed = false
        }
    }

    private fun showPeek(notification: PhoneNotification) {
        // Behind another screen a peek would only show blurred, and the phone key couldn't reach it.
        if (host.screen != null) return
        val now = nowMs()
        val current = peek
        when {
            current != null && !current.timeline.isFinished(now) -> {
                current.notification = notification
                current.timeline.restart(now)
            }
            shell.state == PhoneShellCore.State.CLOSING -> pendingPeek = notification
            shell.state == PhoneShellCore.State.CLOSED -> peek = Shown(notification, BannerTimeline(BannerMotion.PEEK, now))
            else -> {}
        }
    }

    /** Every client tick, after the shell's own animations. */
    fun tick() {
        // The GUI scale fell to 1 (a window made small under the automatic scale): nothing smooth can be drawn there.
        if (!shell.appsSupported()) return forgetShown()
        val now = nowMs()
        // Held by the screenshot harness: nothing ends.
        val held = shell.heldProgress != null
        peek?.let {
            if (!held && it.timeline.isFinished(now)) {
                peek = null
            } else if (host.screen != null) {
                // The player turned to a screen (chat, inventory, pause menu): the peek goes back down.
                it.timeline.dismiss(now)
            }
        }
        pendingPeek?.let { notification ->
            when (shell.state) {
                PhoneShellCore.State.CLOSING -> {}
                PhoneShellCore.State.CLOSED -> {
                    pendingPeek = null
                    if (host.screen == null) peek = Shown(notification, BannerTimeline(BannerMotion.PEEK, now))
                }
                // Opened again before it was away: the badge is on the screen now.
                else -> pendingPeek = null
            }
        }
        banner?.let {
            if (!held && it.timeline.isFinished(now)) {
                banner = null
                bannerPressed = false
            }
        }
    }

    // ---- The peek (10.2) ----

    /** Where the peeking device is at [now], or null while no peek shows. */
    fun peekPose(now: Float = shell.now()): DevicePose? {
        val shown = peek ?: return null
        // Until the next tick drops it, a peek whose GUI scale fell to 1 is not there.
        if (!shell.appsSupported()) return null
        val progress = shell.heldProgress ?: shown.timeline.progress(now * BannerMotion.TICK_MS)
        if (progress <= 0f) return null
        val at = PhoneLayout.peekPosition(host.guiWidth, host.guiHeight, progress)
        return DevicePose(at.x, at.y, 0f)
    }

    /**
     * The phone key pressed in game (2.3 "Up arrow during a peek", P-9): if a peek shows, the phone opens on its
     * notification's target, rising from where the peek is, and true is returned. False when there is no peek.
     */
    fun phoneKeyDuringPeek(): Boolean {
        val shown = peek ?: return false
        if (shell.state != PhoneShellCore.State.CLOSED || peekPose() == null) return false
        val notification = shown.notification
        // The app first, while the phone is put away, so its screens don't slide in: the device rises onto them. An
        // app that fails to start leaves the phone where it was, and it opens there all the same.
        host.navigator.openApp(notification.appId, notification.target)
        shell.open()
        return true
    }

    /**
     * The phone starts opening, by any route: the peek gives way. Returns where the peeking device stood, for the
     * device to rise from there (14 "Open from a peek"), or null if no peek showed.
     */
    fun onOpening(now: Float): DevicePose? {
        val pose = peekPose(now)
        peek = null
        pendingPeek = null
        return pose
    }

    // ---- The banner over the open phone (10.3) ----

    /** How far out the banner is at [now], 0 (hidden) to 1 (resting), or 0 without a banner. */
    fun bannerProgress(now: Float): Float {
        val shown = banner ?: return 0f
        return shell.heldProgress ?: shown.timeline.progress(now * BannerMotion.TICK_MS)
    }

    /** Whether the app-local point ([x], [y]) dp is on the banner as it is drawn at [now], clipped to the viewport. */
    fun bannerContains(x: Float, y: Float, now: Float): Boolean {
        // Not drawn at GUI scale 1, so not there to click or to hide the screen under it from the pointer.
        if (banner == null || !shell.appsSupported()) return false
        val progress = bannerProgress(now)
        if (progress <= 0f || y < 0f) return false
        return BannerLayout.contains(x - BannerLayout.IN_PHONE_X, y - BannerLayout.inPhoneY(progress))
    }

    /** A mouse button went down on the viewport at app-local dp. Returns true when the banner took it. */
    fun mousePressed(x: Float, y: Float, button: Int, now: Float): Boolean {
        if (!bannerContains(x, y, now)) return false
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) bannerPressed = true
        return true
    }

    /**
     * A mouse button came up at app-local dp. A left click that went down and comes up on the banner opens its
     * notification (P-10.5): its app at its target, sliding in, which reads the app's notifications.
     */
    fun mouseReleased(x: Float, y: Float, button: Int, now: Float, onViewport: Boolean) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !bannerPressed) return
        bannerPressed = false
        val shown = banner ?: return
        if (!onViewport || !bannerContains(x, y, now)) return
        banner = null
        host.play(ShellSound.TAP)
        NotificationAlerts.open(host.navigator, shown.notification)
    }

    /** The phone is put away: the banner belongs to the open phone and goes with it. */
    fun onPutAway() {
        banner = null
        bannerPressed = false
    }

    /** Disconnection: everything forgotten. */
    fun reset() {
        forgetShown()
        lastChime = Long.MIN_VALUE
    }

    private fun forgetShown() {
        banner = null
        peek = null
        pendingPeek = null
        bannerPressed = false
    }

    private fun nowMs(): Double = shell.now() * BannerMotion.TICK_MS
}
