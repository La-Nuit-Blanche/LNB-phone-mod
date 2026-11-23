package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

/**
 * The phone's sounds (art direction 15): vanilla sounds played for this player only, all silent while the player
 * turns sounds off in Settings. The kit's widgets already play [tap] when clicked (after the click has done its work)
 * and [error] for a click on something disabled or an error snackbar; call the others when your app does what they
 * stand for. Client thread.
 *
 * In an app's unit tests, the phone's test support (`fr.bde_teknantes.lnb.phone.testing.PhoneTester`) records the
 * sounds instead of playing them.
 */
object PhoneSounds {
    /** One sound of the phone, with the volume and pitch art direction 15 gives it. */
    internal enum class Sound(val path: String, val volume: Float, val pitch: Float) {
        OPEN("phone.open", 0.6f, 1.4f),
        CLOSE("phone.close", 0.6f, 1.4f),
        TAP("ui.tap", 0.35f, 2.0f),
        LIKE("ui.like", 0.3f, 2.0f),
        SENT("ui.sent", 0.4f, 1.2f),
        ERROR("ui.error", 0.3f, 0.7f),
        NOTIFICATION("notification", 0.6f, 1.26f),

        /** The notification's second chime, a fifth above the first. */
        NOTIFICATION_SECOND("notification", 0.6f, 1.68f),
    }

    /** The game's sound manager, where sounds go outside tests. */
    internal val gameOutput: (Sound) -> Unit = { GameOutput.play(it) }

    /** The player's Sounds switch in Settings, outside tests. */
    internal val playerSetting: () -> Boolean = { LnbPhoneClient.settings.current.sounds }

    /** Where sounds go once the player's setting lets them through: the game, or a test's recorder. */
    @Volatile
    internal var output: (Sound) -> Unit = gameOutput

    /** The player's Sounds switch, read at the moment a sound plays; tests put their own. */
    @Volatile
    internal var setting: () -> Boolean = playerSetting

    // Notification's second chime, 100 ms (2 ticks) after the first.
    private const val CHIME_GAP_TICKS = 2
    private var secondChimeIn = -1

    /** Whether sounds play: the player's Settings switch. */
    val enabled: Boolean get() = setting()

    /** A tap on an icon, button, row, toggle, tab, pin or menu item. */
    fun tap() = play(Sound.TAP)

    /** Something was liked. */
    fun like() = play(Sound.LIKE)

    /** A post, reply or message was sent. */
    fun sent() = play(Sound.SENT)

    /** Something failed, or a disabled control was clicked. */
    fun error() = play(Sound.ERROR)

    /** The phone opens. */
    internal fun open() = play(Sound.OPEN)

    /** The phone closes. */
    internal fun close() = play(Sound.CLOSE)

    /** A notification arrived: two chimes, a fifth apart, 100 ms apart. */
    internal fun notification() {
        if (!enabled) return
        output(Sound.NOTIFICATION)
        secondChimeIn = CHIME_GAP_TICKS
    }

    /** Called every client tick, for the delayed second chime. */
    internal fun tick() {
        if (secondChimeIn < 0) return
        if (--secondChimeIn == 0) {
            secondChimeIn = -1
            play(Sound.NOTIFICATION_SECOND)
        }
    }

    private fun play(sound: Sound) {
        if (enabled) output(sound)
    }

    // The phone's own sound events (assets/lnbphone/sounds.json), each playing the vanilla sound art direction 15
    // names, at the same volume and pitch. Its own so that subtitles say what they are: the notification chime has a
    // subtitle, the interface sounds none, as vanilla's button click. Client side only, so never registered.
    private object GameOutput {
        private val events = Sound.entries.associateWith {
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, it.path))
        }

        fun play(sound: Sound) {
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(events.getValue(sound), sound.pitch, sound.volume))
        }
    }
}
