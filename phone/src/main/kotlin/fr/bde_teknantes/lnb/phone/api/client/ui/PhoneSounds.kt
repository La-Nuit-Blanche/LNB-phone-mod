package fr.bde_teknantes.lnb.phone.api.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/**
 * The phone's interface sounds (art direction 15), muted when the player turns sounds off in Settings. The kit's
 * widgets already play [tap] when clicked and [error] for a click on something disabled or an error snackbar; call the
 * others when your app does what they stand for.
 */
object PhoneSounds {
    /**
     * Whether sounds play: the Settings app's sounds switch. Set by the phone; apps only read it.
     */
    @Volatile
    var enabled: () -> Boolean = { true }
        internal set

    /** A tap on an icon, button, row, toggle, tab or menu item. */
    fun tap() = play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35f, 2.0f)

    /** Something was liked. */
    fun like() = play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.3f, 2.0f)

    /** A post, reply or message was sent. */
    fun sent() = play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f)

    /** Something failed, or a disabled control was clicked. */
    fun error() = play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.3f, 0.7f)

    private fun play(sound: SoundEvent, volume: Float, pitch: Float) {
        if (!enabled()) return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
    }
}
