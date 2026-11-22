package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

/**
 * The phone's sounds (art direction 15): vanilla sounds played for this player only, all silent while the player
 * turns sounds off in Settings. The kit's widgets already play [tap] when clicked and [error] for a click on something
 * disabled or an error snackbar; call the others when your app does what they stand for. Client thread.
 */
object PhoneSounds {
    // The phone's own sound events (assets/lnbphone/sounds.json), each playing the vanilla sound art direction 15
    // names, at the same volume and pitch. Its own so that subtitles say what they are: the notification chime has a
    // subtitle, the interface sounds none, as vanilla's button click. Client side only, so never registered.
    private fun event(path: String) = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, path))

    private val OPEN = event("phone.open")
    private val CLOSE = event("phone.close")
    private val TAP = event("ui.tap")
    private val LIKE = event("ui.like")
    private val SENT = event("ui.sent")
    private val ERROR = event("ui.error")
    private val NOTIFICATION = event("notification")

    // Notification's second chime, 100 ms (2 ticks) after the first.
    private const val CHIME_GAP_TICKS = 2
    private var secondChimeIn = -1

    /** Set by tests to play nothing (or to pretend sounds are on) without the settings file. */
    @Volatile
    internal var forcedEnabled: Boolean? = null

    /** Whether sounds play: the player's Settings switch. */
    val enabled: Boolean get() = forcedEnabled ?: LnbPhoneClient.settings.current.sounds

    /** A tap on an icon, button, row, toggle, tab, pin or menu item. */
    fun tap() = play(TAP, 0.35f, 2.0f)

    /** Something was liked. */
    fun like() = play(LIKE, 0.3f, 2.0f)

    /** A post, reply or message was sent. */
    fun sent() = play(SENT, 0.4f, 1.2f)

    /** Something failed, or a disabled control was clicked. */
    fun error() = play(ERROR, 0.3f, 0.7f)

    /** The phone opens. */
    internal fun open() = play(OPEN, 0.6f, 1.4f)

    /** The phone closes. */
    internal fun close() = play(CLOSE, 0.6f, 1.4f)

    /** A notification arrived: two chimes, a fifth apart, 100 ms apart. */
    internal fun notification() {
        if (!enabled) return
        play(NOTIFICATION, 0.6f, 1.26f)
        secondChimeIn = CHIME_GAP_TICKS
    }

    /** Called every client tick, for the delayed second chime. */
    internal fun tick() {
        if (secondChimeIn < 0) return
        if (--secondChimeIn == 0) {
            secondChimeIn = -1
            play(NOTIFICATION, 0.6f, 1.68f)
        }
    }

    private fun play(event: SoundEvent, volume: Float, pitch: Float) {
        if (!enabled) return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, pitch, volume))
    }
}
