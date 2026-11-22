package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettingsStore
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
    val deferred = ArrayList<() -> Unit>()
    var phoneKey = GLFW.GLFW_KEY_UP
    var phoneMouseButton: Int? = null
    private var pendingClicks = 0

    override fun setScreen(screen: Screen?) {
        val old = this.screen
        // Minecraft calls removed() on the old screen before it takes the new one.
        if (old != null && old !== screen && isPhoneScreen(old)) shell.onScreenRemoved()
        this.screen = screen
        screensShown += screen
    }

    override fun createPhoneScreen(): Screen = TestScreen(PHONE)

    override fun isPhoneScreen(screen: Screen?) = (screen as? TestScreen)?.name == PHONE

    override fun later(task: () -> Unit) {
        deferred += task
    }

    override fun consumePhoneKeyClick(): Boolean {
        if (pendingClicks == 0) return false
        pendingClicks--
        return true
    }

    override fun isPhoneKey(keyCode: Int, scanCode: Int) = keyCode == phoneKey

    override fun isPhoneMouseButton(button: Int) = button == phoneMouseButton

    override fun play(sound: ShellSound) {
        sounds += sound
    }

    /** Runs the tasks queued with [later], as the client does between frames. */
    fun runDeferred() {
        while (deferred.isNotEmpty()) deferred.removeAt(0)()
    }

    /**
     * A key press as Minecraft's keyboard handler routes it: to the open screen first (only the phone's screen reacts
     * here); if no screen kept it and the game has the mouse afterwards, the key reaches the game: Esc opens the pause
     * menu, the phone key counts a click for the next tick.
     */
    fun pressKey(keyCode: Int) {
        val open = screen
        if (open != null) {
            val kept = isPhoneScreen(open) && shell.keyPressed(keyCode, 0, 0)
            if (kept) return
        }
        if (screen != null) return
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> setScreen(TestScreen(PAUSE))
            phoneKey -> pendingClicks++
        }
    }

    /** Some other screen opens (the inventory, a death screen, another mod's screen). */
    fun openOtherScreen(name: String): Screen = TestScreen(name).also { setScreen(it) }

    companion object {
        const val PHONE = "phone"
        const val PAUSE = "pause"
    }
}
