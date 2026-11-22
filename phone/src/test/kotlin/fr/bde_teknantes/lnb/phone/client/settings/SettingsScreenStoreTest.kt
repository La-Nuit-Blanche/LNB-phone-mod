package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.api.client.ui.KitFakes
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.CENTRE_X
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.CENTRE_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.KNOB_OFF
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.KNOB_ON
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PILL_CENTRE
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PILL_CORNER
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PREVIEW_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.SOUNDS_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.TOGGLE_X
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The settings changing under the open Settings screen: from elsewhere in this game, from a second game sharing the
 * folder, and a settings file that turns unreadable or unwritable while the screen is up.
 */
class SettingsScreenStoreTest {
    @TempDir
    lateinit var dir: Path

    private val file get() = dir.resolve("lnbphone-client.json")

    private fun store(player: String = "alice") = PhoneSettingsStore(file) { player }

    private fun open(store: PhoneSettingsStore) = SettingsPlayer(store).apply { frame() }

    @BeforeEach
    fun setUp() {
        KitFakes.install()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `a change made elsewhere in this game shows at the next tick, slides like a click, and is not written back`() {
        val store = store()
        val heard = ArrayList<Pair<PhoneSettings, PhoneSettings>>()
        store.addListener { old, new -> heard += old to new }
        val s = open(store)

        // The phone's own code (the capture harness's lever, a future placement rule) changes the store.
        store.update { it.copy(placement = Placement.CENTRE, sounds = false) }
        assertEquals(Triple(0, true, true), s.shown, "not before the tick")

        val pills = ArrayList<Float>()
        val knobs = ArrayList<Float>()
        repeat(4) {
            s.tick()
            pills += s.pillX(s.position)
            knobs += s.knobX(s.sounds)
        }
        assertEquals(Triple(1, false, true), s.shown)
        // One slide each way to the new value, never back: the same motion as the player's own click.
        assertEquals(pills.sorted(), pills)
        assertEquals(knobs.sortedDescending(), knobs)
        assertTrue(pills.first() > PILL_CORNER && pills.first() < PILL_CENTRE)
        assertEquals(PILL_CENTRE, pills.last())
        assertEquals(KNOB_OFF, knobs.last())

        s.tick(40)
        assertEquals(listOf(PhoneSettings() to PhoneSettings(Placement.CENTRE, sounds = false)), heard)
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), store().current)
    }

    @Test
    fun `a change and its undo between two ticks leave the controls still`() {
        val store = store()
        val s = open(store)
        store.update { it.copy(sounds = false) }
        store.update { it.copy(sounds = true) }
        repeat(3) {
            s.tick()
            assertEquals(KNOB_ON, s.knobX(s.sounds))
        }
    }

    @Test
    fun `a second game's change shows only once this game writes, and is then kept, not undone`() {
        val here = store()
        val heard = ArrayList<Pair<PhoneSettings, PhoneSettings>>()
        here.addListener { old, new -> heard += old to new }
        val s = open(here)

        // Another game on the same folder and account centres its phone.
        store().update { it.copy(placement = Placement.CENTRE) }
        // This game read the file once: its store, and so the screen, still say Corner.
        s.tick(20)
        assertEquals(Triple(0, true, true), s.shown)

        // The player turns the sounds off here: the store reads the file again before writing, keeps the other game's
        // placement, and the screen follows it at the next tick.
        s.click(TOGGLE_X, SOUNDS_Y)
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), store().current)
        assertEquals(listOf(PhoneSettings() to PhoneSettings(Placement.CENTRE, sounds = false)), heard)
        s.tick()
        assertEquals(Triple(1, false, true), s.shown)
        s.tick(20)
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), store().current)
        assertEquals(1, heard.size)
    }

    @Test
    fun `a file turned unreadable under the open screen costs no crash, and the next change sets it aside whole`() {
        store("bob").update { it.copy(sounds = false) }
        val here = store()
        here.update { it.copy(preview = false) }
        val s = open(here)

        // A stray edit truncates the file, bob's settings with it.
        val garbage = Files.readString(file).dropLast(12)
        Files.writeString(file, garbage)
        s.tick(20)
        assertEquals(Triple(0, true, false), s.shown, "the screen keeps showing what the store read")

        s.click(CENTRE_X, CENTRE_Y)
        s.tick(4)
        assertEquals(Triple(1, true, false), s.shown)
        assertEquals(PhoneSettings(Placement.CENTRE, preview = false), store().current)
        // Bob's entry isn't overwritten in silence: the broken file, his settings in it, is kept beside the new one.
        val aside = dir.listDirectoryEntries().filter { it.name.startsWith("lnbphone-client.json.broken-") }
        assertEquals(1, aside.size)
        assertEquals(garbage, Files.readString(aside.single()))
        assertEquals(PhoneSettings(), store("bob").current)
        assertEquals(setOf(file, aside.single()), dir.listDirectoryEntries().toSet(), "no temporary file left")
    }

    @Test
    fun `a file that can't be written under the open screen costs no crash, the changes hold and reach it later`() {
        store("bob").update { it.copy(placement = Placement.CENTRE) }
        val disk = RefusingDisk()
        val here = PhoneSettingsStore(file, disk) { "alice" }
        here.update { it.copy(preview = false) }
        val before = Files.readAllBytes(file)
        val heard = ArrayList<PhoneSettings>()
        here.addListener { _, new -> heard += new }
        val s = open(here)

        disk.refuseWrites = true
        s.click(CENTRE_X, CENTRE_Y)
        s.click(TOGGLE_X, SOUNDS_Y)
        s.tick(4)
        assertEquals(Triple(1, false, false), s.shown)
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false, preview = false), here.current)
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals(listOf(file), dir.listDirectoryEntries(), "no temporary file left")

        // Writable again: the next change saves the earlier ones with it, and the screen never goes back to Coin.
        disk.refuseWrites = false
        s.click(TOGGLE_X, PREVIEW_Y)
        repeat(4) {
            s.tick()
            assertEquals(Triple(1, false, true), s.shown)
        }
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), store().current)
        assertEquals(PhoneSettings(Placement.CENTRE), store("bob").current)
        assertEquals(
            listOf(
                PhoneSettings(Placement.CENTRE, preview = false),
                PhoneSettings(Placement.CENTRE, sounds = false, preview = false),
                PhoneSettings(Placement.CENTRE, sounds = false),
            ),
            heard,
        )
    }
}
