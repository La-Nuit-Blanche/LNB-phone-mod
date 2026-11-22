package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

/**
 * A settings file that can't be written or read for a moment: the player's changes hold for the session and reach the
 * disk at the next save that works; nothing is undone, and no good file is set aside.
 */
class PhoneSettingsStoreFailureTest {
    @TempDir
    lateinit var dir: Path

    private val file: Path get() = dir.resolve("lnbphone-client.json")

    private val disk = RefusingDisk()

    private fun store(player: String = "alice") = PhoneSettingsStore(file, disk) { player }

    /** A fresh game reading the file as it is on disk. */
    private fun onDisk(player: String = "alice") = PhoneSettingsStore(file) { player }.current

    private fun heard(store: PhoneSettingsStore) = ArrayList<Pair<PhoneSettings, PhoneSettings>>().also { list ->
        store.addListener { old, new -> list += old to new }
    }

    @Test
    fun `a change whose save failed is not undone by the next save that works, it is saved with it`() {
        val store = store()
        store.update { it.copy(preview = false) }
        val heard = heard(store)

        disk.refuseWrites = true
        store.update { it.copy(placement = Placement.CENTRE) }
        assertEquals(PhoneSettings(Placement.CENTRE, preview = false), store.current)
        assertEquals(PhoneSettings(preview = false), onDisk())

        disk.refuseWrites = false
        store.update { it.copy(sounds = false) }

        val all = PhoneSettings(Placement.CENTRE, sounds = false, preview = false)
        assertEquals(all, store.current)
        assertEquals(all, onDisk())
        // The phone never heard of a glide back to the corner: only the player's two changes.
        assertEquals(
            listOf(
                PhoneSettings(preview = false) to PhoneSettings(Placement.CENTRE, preview = false),
                PhoneSettings(Placement.CENTRE, preview = false) to all,
            ),
            heard,
        )
    }

    @Test
    fun `several failed saves in a row, one undoing another, leave the last value to be saved`() {
        val store = store()
        store.update { it.copy(preview = false) }
        disk.refuseWrites = true
        store.update { it.copy(placement = Placement.CENTRE) }
        store.update { it.copy(sounds = false) }
        store.update { it.copy(sounds = true) }
        store.update { it.copy(preview = true) }
        assertEquals(PhoneSettings(Placement.CENTRE), store.current)
        assertEquals(PhoneSettings(preview = false), onDisk())

        disk.refuseWrites = false
        store.update { it.copy(placement = Placement.CORNER) }
        store.update { it.copy(placement = Placement.CENTRE) }
        assertEquals(PhoneSettings(Placement.CENTRE), onDisk())
    }

    @Test
    fun `once saved, a change is no longer written over another game's later change`() {
        val here = store()
        here.update { it.copy(preview = false) }
        disk.refuseWrites = true
        here.update { it.copy(placement = Placement.CENTRE) }
        disk.refuseWrites = false
        here.update { it.copy(sounds = false) }
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false, preview = false), onDisk())

        // Another game on the same account puts its phone back in the corner, then this one turns the preview on.
        PhoneSettingsStore(file) { "alice" }.update { it.copy(placement = Placement.CORNER) }
        here.update { it.copy(preview = true) }
        assertEquals(PhoneSettings(Placement.CORNER, sounds = false, preview = true), onDisk())
    }

    @Test
    fun `an unsaved change of one account is saved by the next change of another`() {
        var player = "alice"
        val store = PhoneSettingsStore(file, disk) { player }
        disk.refuseWrites = true
        store.update { it.copy(placement = Placement.CENTRE) }
        disk.refuseWrites = false
        player = "bob"
        store.update { it.copy(sounds = false) }
        assertEquals(PhoneSettings(Placement.CENTRE), onDisk("alice"))
        assertEquals(PhoneSettings(sounds = false), onDisk("bob"))
    }

    @Test
    fun `nothing is written while nothing changes, even with a change waiting`() {
        val store = store()
        disk.refuseWrites = true
        store.update { it.copy(sounds = false) }
        disk.refuseWrites = false
        store.update { it }
        store.update { it.copy(sounds = false) }
        assertEquals(0, disk.writes)
        assertEquals(PhoneSettings(sounds = false), store.current)
    }
}
