package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SettingsChoicesTest {
    @TempDir
    lateinit var dir: Path

    private fun store() = PhoneSettingsStore(dir.resolve("settings.json")) { "alice" }

    @Test
    fun `shows the defaults, corner with sounds and preview on`() {
        val choices = SettingsChoices(store())
        assertEquals(0, choices.position)
        assertTrue(choices.sounds)
        assertTrue(choices.preview)
    }

    @Test
    fun `each choice is saved at once and read back by a new store`() {
        val store = store()
        val choices = SettingsChoices(store)
        val heard = ArrayList<PhoneSettings>()
        store.addListener { _, new -> heard += new }

        choices.choosePosition(1)
        assertEquals(Placement.CENTRE, store.current.placement)
        assertEquals(1, choices.position)
        choices.setSounds(false)
        choices.setPreview(false)
        assertEquals(3, heard.size)

        val again = SettingsChoices(store())
        assertEquals(1, again.position)
        assertFalse(again.sounds)
        assertFalse(again.preview)

        choices.choosePosition(0)
        assertEquals(Placement.CORNER, store.current.placement)
    }

    @Test
    fun `an unknown segment changes nothing`() {
        val store = store()
        val choices = SettingsChoices(store)
        var heard = 0
        store.addListener { _, _ -> heard++ }
        choices.choosePosition(2)
        choices.choosePosition(-1)
        assertEquals(0, heard)
        assertEquals(Placement.CORNER, store.current.placement)
    }

    @Test
    fun `reads the store each time, never a copy of its own`() {
        val store = store()
        val choices = SettingsChoices(store)
        store.update { it.copy(placement = Placement.CENTRE, sounds = false) }
        assertEquals(1, choices.position)
        assertFalse(choices.sounds)
    }
}
