package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.client.shell.Placement

/**
 * What the Settings screen shows and changes (art direction 12), read from [store] and written to it on every call.
 * Nothing is kept here: the store is the one truth, and it already deals with a second game sharing the file and with
 * a broken file. Each change applies at once through the store's listeners (the open phone glides to its new place).
 */
internal class SettingsChoices(private val store: PhoneSettingsStore) {
    /** The selected segment of the position control: the index of the current placement in [POSITIONS]. */
    val position: Int get() = POSITIONS.indexOf(store.current.placement).coerceAtLeast(0)

    /** The player picked segment [index] of the position control. */
    fun choosePosition(index: Int) {
        val placement = POSITIONS.getOrNull(index) ?: return
        store.update { it.copy(placement = placement) }
    }

    /** Whether the phone makes sounds, the notification chime included. */
    val sounds: Boolean get() = store.current.sounds

    fun setSounds(on: Boolean) = store.update { it.copy(sounds = on) }

    /** Whether a notification raises the peek while the phone is put away. */
    val preview: Boolean get() = store.current.preview

    fun setPreview(on: Boolean) = store.update { it.copy(preview = on) }

    companion object {
        /** The position control's segments, left to right: `Coin`, then `Centre`. */
        val POSITIONS = listOf(Placement.CORNER, Placement.CENTRE)
    }
}
