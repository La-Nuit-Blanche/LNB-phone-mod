package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PhoneSettingsStoreTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `defaults, then changes saved per player and read back`() {
        val file = dir.resolve("settings.json")
        var player = "alice"
        val store = PhoneSettingsStore(file) { player }
        assertEquals(PhoneSettings(Placement.CORNER, sounds = true, preview = true), store.current)

        val heard = ArrayList<Pair<PhoneSettings, PhoneSettings>>()
        store.addListener { old, new -> heard += old to new }
        store.update { it.copy(placement = Placement.CENTRE) }
        store.update { it.copy(placement = Placement.CENTRE) }
        assertEquals(1, heard.size)
        assertEquals(Placement.CENTRE, heard[0].second.placement)

        player = "bob"
        assertEquals(PhoneSettings(), store.current)
        store.update { it.copy(sounds = false) }

        val reread = PhoneSettingsStore(file) { "alice" }
        assertEquals(PhoneSettings(Placement.CENTRE), reread.current)
        assertEquals(PhoneSettings(sounds = false), PhoneSettingsStore(file) { "bob" }.current)
    }

    @Test
    fun `a broken file falls back to the defaults, field by field`() {
        val file = dir.resolve("settings.json")
        Files.writeString(file, """{"players": {"alice": {"placement": "sideways", "sounds": false, "preview": "yes"}}}""")
        assertEquals(PhoneSettings(sounds = false), PhoneSettingsStore(file) { "alice" }.current)
        Files.writeString(file, "not json")
        assertEquals(PhoneSettings(), PhoneSettingsStore(file) { "alice" }.current)
    }
}
