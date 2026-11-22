package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationHandler
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettingsStore
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
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

    /**
     * Closes the open screen as its own close does (Esc): a container screen tells the server, which puts the crafting
     * grid and the item on the cursor back. Back to the game afterwards, whatever the screen did.
     */
    fun closeScreen()

    /** The unread notifications of the apps this client has, oldest first. */
    val unread: List<PhoneNotification>

    fun createPhoneScreen(): Screen

    fun isPhoneScreen(screen: Screen?): Boolean

    /** Runs [task] on the client thread after the current call has returned (on the next frame). */
    fun later(task: () -> Unit)

    /**
     * Whether [keyCode] is the phone key, as a screen receives it. [withModifier]: only if the key's modifier (Ctrl,
     * Shift, Alt) is held too, as for a press; a release is recognised without it.
     */
    fun isPhoneKey(keyCode: Int, scanCode: Int, withModifier: Boolean = true): Boolean

    fun isPhoneMouseButton(button: Int): Boolean

    /** Whether the phone key is physically held down right now. */
    fun isPhoneKeyDown(): Boolean

    fun play(sound: ShellSound)

    /** [notification] was read in place, its target on screen: removed here and on the server. */
    fun dismissNotification(notification: PhoneNotification)

    /** Prepares the smooth layer (fonts, glyphs, shapes) for the current GUI scale, so opening doesn't hitch. */
    fun prewarm() {}
}

internal enum class ShellSound { OPEN, CLOSE, TAP, NOTIFICATION }

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

    override fun closeScreen() {
        val open = mc.screen ?: return
        open.onClose()
        if (mc.screen != null) mc.setScreen(null)
    }

    override val unread: List<PhoneNotification>
        get() = LnbPhoneClient.notifications.all.filter { LnbPhoneClient.apps[it.appId] != null }

    override fun createPhoneScreen(): Screen = PhoneScreen()

    override fun isPhoneScreen(screen: Screen?) = screen is PhoneScreen

    // tell, not execute: execute would run it at once on the client thread, inside the navigator's callback.
    override fun later(task: () -> Unit) = mc.tell { task() }

    override fun isPhoneKey(keyCode: Int, scanCode: Int, withModifier: Boolean) =
        PhoneKeys.OPEN.matches(keyCode, scanCode) && (!withModifier || PhoneKeys.modifierHeld())

    override fun isPhoneMouseButton(button: Int) = PhoneKeys.OPEN.matchesMouse(button) && PhoneKeys.modifierHeld()

    override fun isPhoneKeyDown(): Boolean = PhoneKeys.isPhysicallyDown()

    override fun prewarm() {
        SmoothCanvas.prewarm()
    }

    override fun play(sound: ShellSound) = when (sound) {
        ShellSound.OPEN -> PhoneSounds.open()
        ShellSound.CLOSE -> PhoneSounds.close()
        ShellSound.TAP -> PhoneSounds.tap()
        ShellSound.NOTIFICATION -> PhoneSounds.notification()
    }

    override fun dismissNotification(notification: PhoneNotification) = ClientNotificationHandler.dismiss(notification)
}
