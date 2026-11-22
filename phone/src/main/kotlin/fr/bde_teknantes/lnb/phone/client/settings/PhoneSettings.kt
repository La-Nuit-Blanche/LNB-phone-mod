package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
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
 * The file is read once, on first use. Each change reads it again just before writing and touches only what changed
 * in the current player's entry: two games sharing one game folder don't undo each other's changes, and what a newer
 * version of the phone wrote (its version number, fields this one doesn't know) is kept. A file that can't be read is
 * moved aside before it is replaced, so nobody's settings are lost to a stray edit.
 *
 * The one place the shell, the notification banner and the Settings app read and write settings. Client thread only.
 *
 * @param file where the settings live.
 * @param player the account whose settings [current] shows (its id as a string).
 */
internal class PhoneSettingsStore(private val file: Path, private val player: () -> String) {
    private var players: Map<String, PhoneSettings>? = null
    private val listeners = CopyOnWriteArrayList<(old: PhoneSettings, new: PhoneSettings) -> Unit>()

    /** The current player's settings. */
    val current: PhoneSettings get() = loaded()[player()] ?: PhoneSettings()

    /** Changes the current player's settings, saves them and tells the listeners if anything changed. */
    fun update(change: (PhoneSettings) -> PhoneSettings) {
        val old = current
        val wanted = change(old)
        if (wanted == old) return
        val new = save(player(), old, wanted)
        if (new == old) return
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

    private fun loaded(): Map<String, PhoneSettings> = players ?: parse((read() as? Read.Found)?.root).also { players = it }

    /**
     * Writes the fields that differ between [old] and [wanted] into [id]'s entry of the file as it is now, and returns
     * that player's settings as they then stand. The store keeps the result even when the file can't be written, so
     * the change holds for the session.
     */
    private fun save(id: String, old: PhoneSettings, wanted: PhoneSettings): PhoneSettings {
        val root = when (val found = read()) {
            is Read.Found -> found.root
            Read.Missing -> JsonObject()
            Read.Broken -> {
                moveAside()
                JsonObject()
            }
        }
        if (!root.has(VERSION)) root.addProperty(VERSION, 1)
        val entries = root.get(PLAYERS)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { root.add(PLAYERS, it) }
        val entry = entries.get(id)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { entries.add(id, it) }
        // What this change is about, and what the entry lacks; the rest stays as found: another game may have changed
        // it since this one read the file.
        if (wanted.placement != old.placement || !entry.has(PLACEMENT)) entry.addProperty(PLACEMENT, wanted.placement.name.lowercase())
        if (wanted.sounds != old.sounds || !entry.has(SOUNDS)) entry.addProperty(SOUNDS, wanted.sounds)
        if (wanted.preview != old.preview || !entry.has(PREVIEW)) entry.addProperty(PREVIEW, wanted.preview)
        write(root)
        val all = parse(root)
        players = all
        return all[id] ?: PhoneSettings()
    }

    private sealed interface Read {
        class Found(val root: JsonObject) : Read

        data object Missing : Read

        data object Broken : Read
    }

    // A missing or broken file costs the settings, never the game: the defaults stand in.
    private fun read(): Read {
        if (!Files.exists(file)) return Read.Missing
        return try {
            val root = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }
            if (root.isJsonObject) Read.Found(root.asJsonObject) else Read.Broken
        } catch (e: Exception) {
            LnbPhone.LOGGER.warn("Couldn't read the phone settings in {}; using the defaults", file, e)
            Read.Broken
        }
    }

    private fun moveAside() {
        val aside = file.resolveSibling("${file.fileName}.broken-${System.currentTimeMillis()}")
        try {
            Files.move(file, aside)
            LnbPhone.LOGGER.warn("The unreadable phone settings file was moved to {}", aside)
        } catch (e: Exception) {
            LnbPhone.LOGGER.error("Couldn't move the unreadable phone settings file {} aside", file, e)
        }
    }

    private fun write(root: JsonObject) {
        try {
            val folder = file.toAbsolutePath().parent
            Files.createDirectories(folder)
            // A temporary file of its own, then moved over the settings: a crash mid-write leaves the old settings,
            // and two games writing at once never share a half-written file.
            val temp = Files.createTempFile(folder, file.fileName.toString(), ".tmp")
            try {
                Files.writeString(temp, GSON.toJson(root))
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
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

        fun parse(root: JsonObject?): Map<String, PhoneSettings> {
            val entries = root?.get(PLAYERS)?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
            val result = LinkedHashMap<String, PhoneSettings>()
            for ((id, value) in entries.entrySet()) {
                if (value.isJsonObject) result[id] = fromJson(value.asJsonObject)
            }
            return result
        }

        // Field by field, each falling back to its default: a value from a newer or hand-edited file can't break the rest.
        fun fromJson(json: JsonObject): PhoneSettings {
            val defaults = PhoneSettings()
            fun bool(name: String, default: Boolean) =
                json.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default
            val placement = json.get(PLACEMENT)?.takeIf(JsonElement::isJsonPrimitive)?.asString
                ?.let { name -> Placement.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            return PhoneSettings(
                placement = placement ?: defaults.placement,
                sounds = bool(SOUNDS, defaults.sounds),
                preview = bool(PREVIEW, defaults.preview),
            )
        }
    }
}
