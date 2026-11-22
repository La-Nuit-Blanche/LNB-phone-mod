package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One player's phone settings (art direction 12). Defaults: corner placement, sounds on, notification preview on.
 *
 * @property sounds every phone sound, the notification chime included (15).
 * @property preview the peek that rises when a notification arrives with the phone put away (10.2); banners inside
 * the open phone, badges, LED and chime don't depend on it.
 */
internal data class PhoneSettings(
    val placement: Placement = Placement.CORNER,
    val sounds: Boolean = true,
    val preview: Boolean = true,
)

/**
 * The phone settings of each player who plays on this computer, kept in one JSON file, by Minecraft account: the
 * settings are per player and client-side (art direction 2.4 and 12). Changes apply at once: [update] saves the file
 * and tells the listeners, which is how the open phone glides to its new placement.
 *
 * The one place the shell, the notification banner and the Settings app read and write settings. Client thread only.
 *
 * @param file where the settings live; read on first use.
 * @param player the account whose settings [current] shows (its id as a string).
 */
internal class PhoneSettingsStore(private val file: Path, private val player: () -> String) {
    private var players: MutableMap<String, PhoneSettings>? = null
    private val listeners = CopyOnWriteArrayList<(old: PhoneSettings, new: PhoneSettings) -> Unit>()

    /** The current player's settings. */
    val current: PhoneSettings get() = loaded()[player()] ?: PhoneSettings()

    /** Changes the current player's settings, saves them and tells the listeners if anything changed. */
    fun update(change: (PhoneSettings) -> PhoneSettings) {
        val old = current
        val new = change(old)
        if (new == old) return
        loaded()[player()] = new
        save()
        for (listener in listeners) {
            try {
                listener(old, new)
            } catch (e: Exception) {
                LnbPhone.LOGGER.error("Phone settings listener failed", e)
            }
        }
    }

    /** [listener] hears every change from now on, with the settings before and after. */
    fun addListener(listener: (old: PhoneSettings, new: PhoneSettings) -> Unit) {
        listeners += listener
    }

    private fun loaded(): MutableMap<String, PhoneSettings> = players ?: read().also { players = it }

    // A missing or broken file costs the settings, never the game: the defaults stand in and the next change rewrites it.
    private fun read(): MutableMap<String, PhoneSettings> {
        val result = LinkedHashMap<String, PhoneSettings>()
        if (!Files.exists(file)) return result
        try {
            val root = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
            val entries = root.getAsJsonObject(PLAYERS) ?: return result
            for ((id, value) in entries.entrySet()) {
                if (value.isJsonObject) result[id] = fromJson(value.asJsonObject)
            }
        } catch (e: Exception) {
            LnbPhone.LOGGER.warn("Couldn't read the phone settings in {}; using the defaults", file, e)
        }
        return result
    }

    private fun save() {
        val root = JsonObject()
        root.addProperty(VERSION, 1)
        val entries = JsonObject()
        for ((id, settings) in loaded()) entries.add(id, toJson(settings))
        root.add(PLAYERS, entries)
        try {
            Files.createDirectories(file.toAbsolutePath().parent)
            // Written next to the file, then moved over it: a crash mid-write leaves the old settings, not half a file.
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temp, GSON.toJson(root))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            LnbPhone.LOGGER.error("Couldn't save the phone settings to {}", file, e)
        }
    }

    private companion object {
        const val VERSION = "version"
        const val PLAYERS = "players"
        const val PLACEMENT = "placement"
        const val SOUNDS = "sounds"
        const val PREVIEW = "preview"
        val GSON = GsonBuilder().setPrettyPrinting().create()

        fun toJson(settings: PhoneSettings) = JsonObject().apply {
            addProperty(PLACEMENT, settings.placement.name.lowercase())
            addProperty(SOUNDS, settings.sounds)
            addProperty(PREVIEW, settings.preview)
        }

        // Field by field, each falling back to its default: a value from a newer or hand-edited file can't break the rest.
        fun fromJson(json: JsonObject): PhoneSettings {
            val defaults = PhoneSettings()
            fun bool(name: String, default: Boolean) =
                json.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default
            val placement = json.get(PLACEMENT)?.takeIf { it.isJsonPrimitive }?.asString
                ?.let { name -> Placement.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            return PhoneSettings(
                placement = placement ?: defaults.placement,
                sounds = bool(SOUNDS, defaults.sounds),
                preview = bool(PREVIEW, defaults.preview),
            )
        }
    }
}
