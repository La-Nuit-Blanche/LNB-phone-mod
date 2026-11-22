package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettingsStore
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Everything the shell's state machine ([PhoneShellCore]) needs from the game: the window, the screen, the phone key,
 * sounds, deferred work. [MinecraftShellHost] is the real one; tests drive the shell through a fake, without a game.
 */
internal interface ShellHost {
    val navigator: PhoneNavigator
    val apps: AppRegistry
    val settings: PhoneSettingsStore

    /** Whether the player is in a world. */
    val inWorld: Boolean

    /** The window in GUI px, and the GUI scale. */
    val guiWidth: Int
    val guiHeight: Int
    val guiScale: Double

    /** How far the game is between the last tick and the next one, 0 to 1. */
    val partialTick: Float

    /** The game screen open now, null while the game has the mouse. */
    val screen: Screen?

    /** Shows [screen] (null: back to the game), calling `removed()` on the one it replaces, as Minecraft does. */
    fun setScreen(screen: Screen?)

    fun createPhoneScreen(): Screen

    fun isPhoneScreen(screen: Screen?): Boolean

    /** Runs [task] on the client thread after the current call has returned (on the next frame). */
    fun later(task: () -> Unit)

    /** Takes one press of the phone key made while the game had the mouse; false once none are left. */
    fun consumePhoneKeyClick(): Boolean

    fun isPhoneKey(keyCode: Int, scanCode: Int): Boolean

    fun isPhoneMouseButton(button: Int): Boolean

    fun play(sound: ShellSound)
}

internal enum class ShellSound { OPEN, CLOSE, TAP }

/** The shell's host in the game. Client only. */
internal object MinecraftShellHost : ShellHost {
    private val mc: Minecraft get() = Minecraft.getInstance()

    override val navigator: PhoneNavigator get() = LnbPhoneClient.navigator
    override val apps: AppRegistry get() = LnbPhoneClient.apps
    override val settings: PhoneSettingsStore get() = LnbPhoneClient.settings
    override val inWorld: Boolean get() = mc.player != null
    override val guiWidth: Int get() = mc.window.guiScaledWidth
    override val guiHeight: Int get() = mc.window.guiScaledHeight
    override val guiScale: Double get() = mc.window.guiScale
    override val partialTick: Float get() = mc.getTimer().getGameTimeDeltaPartialTick(true)
    override val screen: Screen? get() = mc.screen

    override fun setScreen(screen: Screen?) = mc.setScreen(screen)

    override fun createPhoneScreen(): Screen = PhoneScreen()

    override fun isPhoneScreen(screen: Screen?) = screen is PhoneScreen

    // tell, not execute: execute would run it at once on the client thread, inside the navigator's callback.
    override fun later(task: () -> Unit) = mc.tell { task() }

    override fun consumePhoneKeyClick() = PhoneKeys.OPEN.consumeClick()

    override fun isPhoneKey(keyCode: Int, scanCode: Int) = PhoneKeys.OPEN.matches(keyCode, scanCode)

    override fun isPhoneMouseButton(button: Int) = PhoneKeys.OPEN.matchesMouse(button)

    override fun play(sound: ShellSound) = when (sound) {
        ShellSound.OPEN -> PhoneSounds.open()
        ShellSound.CLOSE -> PhoneSounds.close()
        ShellSound.TAP -> PhoneSounds.tap()
    }
}
