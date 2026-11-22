package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettingsStore
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.nio.file.Path

/** A game screen for tests: the phone's own, or another one (inventory, pause menu, death screen). */
internal class TestScreen(val name: String) : Screen(Component.literal(name)) {
    override fun toString() = name
}

/**
 * The game as the shell sees it, without a game: a window, a screen slot, the phone key, a queue of deferred tasks.
 * [pressKey] routes a key the way Minecraft's keyboard handler does, so a key the phone's screen doesn't keep can
 * still reach the game (the pause menu, the phone key's mapping).
 */
internal class FakeShellHost(settingsFile: Path) : ShellHost {
    override val apps = AppRegistry()
    override val navigator = PhoneNavigator(apps)
    override val settings = PhoneSettingsStore(settingsFile) { "player" }
    override var inWorld = true
    override var guiWidth = 480
    override var guiHeight = 270
    override var guiScale = 4.0
    override var partialTick = 0f
    override var screen: Screen? = null
        private set

    lateinit var shell: PhoneShellCore

    /** Every screen shown, in order (null: back to the game). */
    val screensShown = ArrayList<Screen?>()
    val sounds = ArrayList<ShellSound>()
    var prewarms = 0
    val deferred = ArrayList<() -> Unit>()
    var phoneKey = GLFW.GLFW_KEY_UP
    var phoneMouseButton: Int? = null

    /** Keys physically held down. */
    val keysDown = HashSet<Int>()

    /**
     * What a mod listening to `ScreenEvent.Opening` does to the next screen opened (not to going back to the game,
     * which can't be refused): [refuseNextScreen] cancels it, [replaceNextScreen] swaps it for another.
     */
    var refuseNextScreen = false
    var replaceNextScreen: Screen? = null

    override fun setScreen(screen: Screen?) {
        var next = screen
        if (next != null) {
            if (refuseNextScreen) {
                refuseNextScreen = false
                return
            }
            replaceNextScreen?.let {
                replaceNextScreen = null
                next = it
            }
        }
        val old = this.screen
        // Minecraft calls removed() on the old screen before it takes the new one, then added() on the new one.
        if (old != null && old !== next && isPhoneScreen(old)) shell.onScreenRemoved()
        this.screen = next
        screensShown += next
        if (next != null && isPhoneScreen(next)) shell.onScreenShown()
    }

    /** Screens closed through [closeScreen] (as their own close would, telling the server for a container). */
    val screensClosed = ArrayList<Screen>()

    override fun closeScreen() {
        val open = screen ?: return
        screensClosed += open
        setScreen(null)
    }

    /** The store's unread notifications, oldest first; those dismissed since are left out of [unread]. */
    var unreadList: List<PhoneNotification> = emptyList()

    override val unread: List<PhoneNotification> get() = unreadList.filter { it !in dismissed }

    override fun createPhoneScreen(): Screen = TestScreen(PHONE)

    override fun isPhoneScreen(screen: Screen?) = (screen as? TestScreen)?.name == PHONE

    override fun later(task: () -> Unit) {
        deferred += task
    }

    override fun isPhoneKey(keyCode: Int, scanCode: Int, withModifier: Boolean) = keyCode == phoneKey

    override fun isPhoneMouseButton(button: Int) = button == phoneMouseButton

    override fun isPhoneKeyDown() = phoneKey in keysDown

    override fun heldKeys(): Set<Int> = keysDown.toSet()

    override fun prewarm() {
        prewarms++
    }

    override fun play(sound: ShellSound) {
        sounds += sound
    }

    /** Notifications read in place, in order. */
    val dismissed = ArrayList<PhoneNotification>()

    /** What reading in place does besides being recorded (a test wires the client's notification store here). */
    var onDismiss: (PhoneNotification) -> Unit = {}

    override fun dismissNotification(notification: PhoneNotification) {
        dismissed += notification
        onDismiss(notification)
    }

    /** Runs the tasks queued with [later], as the client does between frames. */
    fun runDeferred() {
        while (deferred.isNotEmpty()) deferred.removeAt(0)()
    }

    /** A quick tap of a key: pressed, then released. */
    fun pressKey(keyCode: Int) {
        keyDown(keyCode)
        releaseKey(keyCode)
    }

    /** A key pressed and held long enough to send [repeats] key repeats, not released. */
    fun holdKey(keyCode: Int, repeats: Int) {
        keyDown(keyCode)
        repeatKey(keyCode, repeats)
    }

    /** [times] key repeats of a key still held (about 30 a second in Minecraft). */
    fun repeatKey(keyCode: Int, times: Int) {
        check(keyCode in keysDown) { "A key repeats only while held" }
        repeat(times) { keyEvent(keyCode, repeat = true) }
    }

    fun keyDown(keyCode: Int) {
        keysDown += keyCode
        keyEvent(keyCode, repeat = false)
    }

    /** The key comes up; the phone's screen hears it if it is the open screen, as screens do. */
    fun releaseKey(keyCode: Int) {
        keysDown -= keyCode
        if (isPhoneScreen(screen)) shell.keyReleased(keyCode, 0, 0)
    }

    /**
     * A key press or repeat as Minecraft's keyboard handler routes it: to the open screen first, which can't tell a
     * repeat from a press (only the phone's screen reacts here); if no screen kept it and the game has the mouse
     * afterwards, the key reaches the game: Esc opens the pause menu (repeats too, as in Minecraft), and a press of the
     * phone key, never a repeat, reaches the shell through the input event.
     */
    private fun keyEvent(keyCode: Int, repeat: Boolean) {
        val open = screen
        if (open != null) {
            val kept = isPhoneScreen(open) && shell.keyPressed(keyCode, 0, 0)
            if (kept) return
        }
        if (screen != null) return
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> setScreen(TestScreen(PAUSE))
            phoneKey -> if (!repeat) shell.phoneKeyPressedInGame()
        }
    }

    /** Some other screen opens (the inventory, a death screen, another mod's screen). */
    fun openOtherScreen(name: String): Screen = TestScreen(name).also { setScreen(it) }

    companion object {
        const val PHONE = "phone"
        const val PAUSE = "pause"
    }
}
