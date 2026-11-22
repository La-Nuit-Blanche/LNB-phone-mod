package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The settings file shared with other games and other versions of the phone: nobody's settings get lost. */
class PhoneSettingsStoreSharingTest {
    @TempDir
    lateinit var dir: Path

    private val file: Path get() = dir.resolve("lnbphone-client.json")

    private fun store(player: String) = PhoneSettingsStore(file) { player }

    private fun json() = JsonParser.parseString(Files.readString(file)).asJsonObject

    @Test
    fun `two games on one folder keep each other's changes, for other players and for the same one`() {
        // Both games read the file before either writes.
        val first = store("alice")
        val second = store("alice")
        val secondBob = store("bob")
        first.current
        second.current
        secondBob.current

        first.update { it.copy(sounds = false) }
        secondBob.update { it.copy(preview = false) }
        second.update { it.copy(placement = Placement.CENTRE) }

        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), store("alice").current)
        assertEquals(PhoneSettings(preview = false), store("bob").current)
        // The second game now knows what the first one wrote.
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), second.current)
    }

    @Test
    fun `a newer version's file keeps its version and the fields this one doesn't know`() {
        Files.writeString(
            file,
            """
            {
              "version": 7,
              "theme": "light",
              "players": {
                "alice": {"placement": "corner", "vibrate": true},
                "bob": {"wallpaper": "night"}
              }
            }
            """.trimIndent(),
        )
        store("alice").update { it.copy(placement = Placement.CENTRE) }
        val root = json()
        assertEquals(7, root.get("version").asInt)
        assertEquals("light", root.get("theme").asString)
        val alice = root.getAsJsonObject("players").getAsJsonObject("alice")
        assertEquals("centre", alice.get("placement").asString)
        assertTrue(alice.get("vibrate").asBoolean)
        assertEquals("night", root.getAsJsonObject("players").getAsJsonObject("bob").get("wallpaper").asString)
    }

    @Test
    fun `an unreadable file is moved aside, not erased, before the next change replaces it`() {
        val broken = """{"players": {"alice": {"placement": "centre"}, "bob": """
        Files.writeString(file, broken)
        val store = store("alice")
        assertEquals(PhoneSettings(), store.current)
        store.update { it.copy(sounds = false) }

        assertEquals(PhoneSettings(sounds = false), store("alice").current)
        val aside = Files.list(dir).use { files -> files.filter { it.fileName.toString().startsWith("lnbphone-client.json.broken") }.toList() }
        assertEquals(1, aside.size)
        assertEquals(broken, Files.readString(aside.single()))
    }
}
