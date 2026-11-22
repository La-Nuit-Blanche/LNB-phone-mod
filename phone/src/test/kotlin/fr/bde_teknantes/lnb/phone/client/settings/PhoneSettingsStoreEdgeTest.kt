package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PhoneSettingsStoreEdgeTest {
    @TempDir
    lateinit var dir: Path

    private val file: Path get() = dir.resolve("lnbphone-client.json")

    private fun store(player: String = "alice") = PhoneSettingsStore(file) { player }

    private val everythingChanged = PhoneSettings(Placement.CENTRE, sounds = false, preview = false)

    @Test
    fun `defaults are corner, sounds on, preview on, and reading writes no file`() {
        assertEquals(PhoneSettings(Placement.CORNER, sounds = true, preview = true), store().current)
        assertFalse(Files.exists(file))
    }

    @Test
    fun `every field survives a round trip`() {
        store().update { everythingChanged }
        assertEquals(everythingChanged, store().current)
        // And back to the defaults.
        store().update { PhoneSettings() }
        assertEquals(PhoneSettings(), store().current)
    }

    @Test
    fun `the file names the placement in words and carries a version`() {
        store().update { it.copy(placement = Placement.CENTRE) }
        val root = JsonParser.parseString(Files.readString(file)).asJsonObject
        assertEquals(1, root.get("version").asInt)
        val alice = root.getAsJsonObject("players").getAsJsonObject("alice")
        assertEquals("centre", alice.get("placement").asString)
        assertEquals(true, alice.get("sounds").asBoolean)
        assertEquals(true, alice.get("preview").asBoolean)
        // Written whole: no temporary file left behind.
        assertFalse(Files.exists(dir.resolve("lnbphone-client.json.tmp")))
    }

    @Test
    fun `two accounts in one file keep their own settings`() {
        var player = "alice"
        val shared = PhoneSettingsStore(file) { player }
        shared.update { it.copy(placement = Placement.CENTRE) }
        player = "bob"
        shared.update { it.copy(preview = false) }
        player = "alice"
        assertEquals(PhoneSettings(Placement.CENTRE), shared.current)

        // A second store on the same file (another launch) sees both.
        assertEquals(PhoneSettings(Placement.CENTRE), store("alice").current)
        assertEquals(PhoneSettings(preview = false), store("bob").current)
        assertEquals(PhoneSettings(), store("carol").current)
        // Bob changing again leaves Alice alone.
        store("bob").update { it.copy(sounds = false) }
        assertEquals(PhoneSettings(Placement.CENTRE), store("alice").current)
        assertEquals(PhoneSettings(sounds = false, preview = false), store("bob").current)
    }

    @Test
    fun `a corrupt, truncated, empty or misshapen file gives the defaults`() {
        val broken = listOf(
            "",
            "   ",
            "not json",
            "{",
            """{"version": 1, "players": {"alice": {"placement": "centre", "sounds": fal""",
            "[]",
            "null",
            "42",
            """{"players": []}""",
            """{"players": "alice"}""",
            """{"players": {"alice": "centre"}}""",
            """{"players": {"alice": null}}""",
            """{"players": {"alice": {"placement": 3, "sounds": "no", "preview": null}}}""",
            """{"players": {"alice": {"placement": {"x": 1}, "sounds": [true]}}}""",
        )
        for (content in broken) {
            Files.writeString(file, content)
            assertEquals(PhoneSettings(), store().current, "file: $content")
        }
    }

    @Test
    fun `a broken entry costs only its own player`() {
        Files.writeString(file, """{"players": {"alice": 7, "bob": {"placement": "centre"}}}""")
        assertEquals(PhoneSettings(), store("alice").current)
        assertEquals(PhoneSettings(Placement.CENTRE), store("bob").current)
    }

    @Test
    fun `placement names are read whatever their case`() {
        for (name in listOf("centre", "CENTRE", "Centre")) {
            Files.writeString(file, """{"players": {"alice": {"placement": "$name"}}}""")
            assertEquals(Placement.CENTRE, store().current.placement, name)
        }
        Files.writeString(file, """{"players": {"alice": {"placement": "center"}}}""")
        assertEquals(Placement.CORNER, store().current.placement)
    }

    @Test
    fun `a file from a future version is read for what it knows`() {
        Files.writeString(
            file,
            """
            {
              "version": 7,
              "theme": "light",
              "players": {
                "alice": {"placement": "centre", "sounds": false, "preview": true, "vibrate": true, "placement2": "left"},
                "bob": {"wallpaper": "night"}
              }
            }
            """.trimIndent(),
        )
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), store("alice").current)
        assertEquals(PhoneSettings(), store("bob").current)
    }

    @Test
    fun `a broken file is replaced by the next change`() {
        Files.writeString(file, "{ truncated")
        val store = store()
        store.update { it.copy(sounds = false) }
        assertEquals(PhoneSettings(sounds = false), store().current)
    }

    @Test
    fun `an unwritable location keeps the change for the session and still tells the listeners`() {
        // A file where the settings' folder should be: the folder can't be created, so nothing can be written.
        val blocker = dir.resolve("config")
        Files.writeString(blocker, "")
        val store = PhoneSettingsStore(blocker.resolve("lnbphone-client.json")) { "alice" }
        val heard = ArrayList<PhoneSettings>()
        store.addListener { _, new -> heard += new }

        store.update { it.copy(placement = Placement.CENTRE) }

        assertEquals(PhoneSettings(Placement.CENTRE), store.current)
        assertEquals(listOf(PhoneSettings(Placement.CENTRE)), heard)
        assertTrue(Files.isRegularFile(blocker))
    }

    @Test
    fun `the folder is created when missing`() {
        val nested = dir.resolve("a").resolve("b").resolve("lnbphone-client.json")
        PhoneSettingsStore(nested) { "alice" }.update { it.copy(sounds = false) }
        assertEquals(PhoneSettings(sounds = false), PhoneSettingsStore(nested) { "alice" }.current)
    }

    @Test
    fun `listeners hear old and new, in order, once per real change`() {
        val store = store()
        val heard = ArrayList<String>()
        store.addListener { old, new -> heard += "first ${old.placement} -> ${new.placement}" }
        store.addListener { old, new -> heard += "second ${old.sounds} -> ${new.sounds}" }

        store.update { it }
        store.update { it.copy(placement = Placement.CORNER) }
        assertEquals(emptyList<String>(), heard)

        store.update { it.copy(placement = Placement.CENTRE) }
        assertEquals(listOf("first CORNER -> CENTRE", "second true -> true"), heard)
    }

    @Test
    fun `a listener that throws neither stops the others nor undoes the change`() {
        val store = store()
        val heard = ArrayList<String>()
        store.addListener { _, _ -> throw IllegalStateException("broken listener") }
        store.addListener { _, new -> heard += new.placement.name }

        store.update { it.copy(placement = Placement.CENTRE) }

        assertEquals(listOf("CENTRE"), heard)
        assertEquals(Placement.CENTRE, store.current.placement)
        assertEquals(Placement.CENTRE, store().current.placement)
    }

    @Test
    fun `listeners read the new settings from the store when they are told`() {
        val store = store()
        var seen: PhoneSettings? = null
        store.addListener { _, _ -> seen = store.current }
        store.update { it.copy(preview = false) }
        assertEquals(PhoneSettings(preview = false), seen)
    }

    @Test
    fun `the file is read once, so later edits by hand wait for the next launch`() {
        val store = store()
        assertEquals(PhoneSettings(), store.current)
        Files.writeString(file, """{"players": {"alice": {"placement": "centre"}}}""")
        assertEquals(PhoneSettings(), store.current)
        assertEquals(PhoneSettings(Placement.CENTRE), store().current)
    }
}
