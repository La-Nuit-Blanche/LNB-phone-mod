package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.RecordingScreen
import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach

/**
 * A shell with three apps and notifications to send it: `feed` (whose target screens say which target they show),
 * `mail` (a plain app) and `broken` (which can't start), plus [extraApps] plain ones for the home grid.
 *
 * Times: a peek lives 200 + 4000 + 200 ms (4 + 80 + 4 ticks), the in-phone banner 150 + 4000 + 150 ms (3 + 80 + 3).
 */
internal abstract class AlertsTestBase : ShellTestBase() {
    private var nextId = 1L
    private val badgesBefore = PhoneBadges.source

    val alerts: ShellAlerts get() = shell.alerts

    /** A screen of `feed` that shows one target. */
    inner class TargetScreen(private val target: String) : RecordingScreen(target, log) {
        override fun showsTarget(target: String) = target == this.target
    }

    fun registerAll(extraApps: Int = 0) = register(
        testApp("feed", targets = { listOf(TargetScreen(it)) }, root = { RecordingScreen("feed", log) }),
        testApp("mail", root = { RecordingScreen("mail", log) }),
        testApp("broken", targets = { error("can't start") }, root = { error("can't start") }),
        *Array(extraApps) { testApp("app$it", root = { RecordingScreen("app$it", log) }) },
    )

    fun notification(app: String = "feed", target: String? = "thread:1"): PhoneNotification =
        PhoneNotification(nextId++, appId(app), Component.literal("title"), Component.literal("text"), target, 0, true)

    /** A notification arriving while the player is connected. */
    fun notify(app: String = "feed", target: String? = "thread:1"): PhoneNotification =
        notification(app, target).also { shell.notificationArrived(it) }

    val chimes: Int get() = host.sounds.count { it == ShellSound.NOTIFICATION }

    /** The badge counts the shell reads, by app path, taken in at the next tick. */
    fun badges(vararg counts: Pair<String, Int>) {
        val map = counts.associate { appId(it.first) to it.second }
        PhoneBadges.source = BadgeSource { map[it] ?: 0 }
    }

    @AfterEach
    fun restoreBadges() {
        PhoneBadges.source = badgesBefore
    }

    /** Window GUI px of viewport-local ([vx], [vy]) GUI px where the device stands now. */
    fun viewport(vx: Double, vy: Double): Pair<Double, Double> = device(Device.VIEWPORT_X + vx, Device.VIEWPORT_Y + vy)

    /** Window GUI px of app-local ([x], [y]) dp. */
    fun dp(x: Double, y: Double): Pair<Double, Double> = viewport(x / 2, y / 2)

    fun pressAt(at: Pair<Double, Double>, button: Int = LEFT) = press(at.first, at.second, button)

    fun releaseAt(at: Pair<Double, Double>, button: Int = LEFT) = release(at.first, at.second, button)

    fun clickAt(at: Pair<Double, Double>, button: Int = LEFT) = click(at.first, at.second, button)

    /** Where the banner rests: app-local (8, 4) dp, 240 x 76 (10.1, 10.3). Its middle is (128, 42), as in P-10.4. */
    val bannerMiddle: Pair<Double, Double> get() = dp(128.0, 42.0)

    /** The peeking device at [ticksAfter] ticks past the shell's clock, or null. */
    fun peekAt(ticksAfter: Float = 0f): DevicePose? = alerts.peekPose(shell.now() + ticksAfter)

    companion object {
        const val LEFT = 0
        const val RIGHT = 1

        /** The peek's and the banner's lives in ticks (14). */
        const val PEEK_RISE_TICKS = 4
        const val PEEK_LIFE_TICKS = 88
        const val BANNER_IN_TICKS = 3
        const val BANNER_LIFE_TICKS = 86
        const val HOLD_TICKS = 80
    }
}
