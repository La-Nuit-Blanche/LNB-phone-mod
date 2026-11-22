package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/**
 * The phone's sounds (art direction 15): vanilla events played for this player only, all silent while the Settings
 * `Sounds` toggle is off. Client thread only.
 */
internal object PhoneSounds {
    // Notification's second chime, 100 ms (2 ticks) after the first.
    private const val CHIME_GAP_TICKS = 2
    private var secondChimeIn = -1

    fun open() = play(SoundEvents.SPYGLASS_USE, 0.6f, 1.4f)

    fun close() = play(SoundEvents.SPYGLASS_STOP_USING, 0.6f, 1.4f)

    /** An icon, button, row, toggle, tab, pin or menu item was used. */
    fun tap() = play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35f, 2.0f)

    fun like() = play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.3f, 2.0f)

    /** A post or reply went out. */
    fun sent() = play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f)

    /** An error snackbar, or a tap on something disabled. */
    fun error() = play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.3f, 0.7f)

    /** A notification arrived: two chimes, a fifth apart, 100 ms apart. */
    fun notification() {
        if (!enabled()) return
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.26f)
        secondChimeIn = CHIME_GAP_TICKS
    }

    /** Called every client tick, for the delayed second chime. */
    fun tick() {
        if (secondChimeIn < 0) return
        if (--secondChimeIn == 0) {
            secondChimeIn = -1
            play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.68f)
        }
    }

    private fun enabled() = LnbPhoneClient.settings.current.sounds

    private fun play(event: SoundEvent, volume: Float, pitch: Float) {
        if (!enabled()) return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, pitch, volume))
    }
}
