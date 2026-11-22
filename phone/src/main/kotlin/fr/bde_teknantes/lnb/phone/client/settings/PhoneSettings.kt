package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.EnumSet
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
 * The file is read once, on first use. Each change reads it again just before writing and writes only the fields this
 * game changed, leaving the rest as found: two games sharing one game folder don't undo each other's changes, even on
 * the same account, and what a newer version of the phone wrote (its version number, fields this one doesn't know) is
 * kept.
 *
 * A change the player made stays in effect for the session until it reaches the disk, and reaches it at the next save
 * that succeeds: a file that can't be written or read for a moment (a sync client holding it, a read-only copy, a full
 * disk) is left exactly as it is meanwhile. (If it can't be read on first use, the defaults show until the next change
 * reads it.) A file that can be read but not understood is moved aside before it is replaced, so nobody's settings
 * are lost to a stray edit.
 *
 * The one place the shell, the notification banner and the Settings app read and write settings. Client thread only.
 *
 * @param file where the settings live.
 * @param disk how the file is read and written; tests make it refuse.
 * @param player the account whose settings [current] shows (its id as a string).
 */
internal class PhoneSettingsStore(
    private val file: Path,
    private val disk: SettingsDisk = SettingsDisk.Real,
    private val player: () -> String,
) {
    // What this game holds, by account: the file as last read, with this session's changes on top.
    private var players: Map<String, PhoneSettings>? = null

    // The fields changed this session that the file doesn't hold yet, by account. Cleared only by a write that worked.
    private val unsaved = HashMap<String, MutableSet<Field>>()

    private val listeners = CopyOnWriteArrayList<(old: PhoneSettings, new: PhoneSettings) -> Unit>()

    /** The current player's settings. */
    val current: PhoneSettings get() = loaded()[player()] ?: PhoneSettings()

    /** Changes the current player's settings, saves them and tells the listeners if anything changed. */
    fun update(change: (PhoneSettings) -> PhoneSettings) {
        val id = player()
        val old = current
        val wanted = change(old)
        if (wanted == old) return
        unsaved.getOrPut(id) { EnumSet.noneOf(Field::class.java) } += Field.entries.filter { it.differs(old, wanted) }
        players = loaded() + (id to wanted)
        save()
        val new = current
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
     * Writes every unsaved field into the file as it is now and takes the result as what this game holds: the file's
     * other entries and fields, which another game may have changed, with this session's changes on top. When the file
     * can't be read right now, nothing is written and the changes wait for the next save.
     */
    private fun save() {
        val root = when (val found = read()) {
            is Read.Found -> found.root
            Read.Missing -> JsonObject()
            Read.Unavailable -> return
            Read.Broken -> {
                moveAside()
                JsonObject()
            }
        }
        if (!root.has(VERSION)) root.addProperty(VERSION, 1)
        val entries = root.get(PLAYERS)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { root.add(PLAYERS, it) }
        val session = loaded()
        for ((id, fields) in unsaved) {
            val wanted = session[id] ?: continue
            val entry = entries.get(id)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { entries.add(id, it) }
            // What changed and isn't saved yet, and what the entry lacks; the rest stays as found.
            for (field in Field.entries) {
                if (field in fields || !entry.has(field.key)) field.write(entry, wanted)
            }
        }
        players = parse(root)
        if (write(root)) unsaved.clear()
    }

    private sealed interface Read {
        class Found(val root: JsonObject) : Read

        /** No file yet. */
        data object Missing : Read

        /** The file is there but couldn't be read just now: leave it alone. */
        data object Unavailable : Read

        /** The file was read but isn't settings: it may be set aside and replaced. */
        data object Broken : Read
    }

    // A missing or broken file costs the settings, never the game: the defaults stand in.
    private fun read(): Read {
        val text = try {
            disk.read(file)
        } catch (e: NoSuchFileException) {
            return Read.Missing
        } catch (e: CharacterCodingException) {
            // Read in full, but not text: that is the content's fault, not the disk's.
            LnbPhone.LOGGER.warn("The phone settings in {} aren't UTF-8 text; using the defaults", file, e)
            return Read.Broken
        } catch (e: IOException) {
            LnbPhone.LOGGER.warn("Couldn't read the phone settings in {} just now; leaving the file as it is", file, e)
            return Read.Unavailable
        }
        return try {
            val root = JsonParser.parseString(text)
            if (root.isJsonObject) Read.Found(root.asJsonObject) else Read.Broken
        } catch (e: Exception) {
            LnbPhone.LOGGER.warn("Couldn't understand the phone settings in {}; using the defaults", file, e)
            Read.Broken
        }
    }

    private fun moveAside() {
        val aside = file.resolveSibling("${file.fileName}.broken-${System.currentTimeMillis()}")
        try {
            disk.move(file, aside)
            LnbPhone.LOGGER.warn("The unreadable phone settings file was moved to {}", aside)
        } catch (e: Exception) {
            LnbPhone.LOGGER.error("Couldn't move the unreadable phone settings file {} aside", file, e)
        }
    }

    /** Whether [root] reached the disk. */
    private fun write(root: JsonObject): Boolean = try {
        disk.write(file, GSON.toJson(root))
        true
    } catch (e: Exception) {
        LnbPhone.LOGGER.error("Couldn't save the phone settings to {}; they hold for this session and will be saved with the next change", file, e)
        false
    }

    private enum class Field(val key: String) {
        PLACEMENT("placement"),
        SOUNDS("sounds"),
        PREVIEW("preview"),
        ;

        fun differs(a: PhoneSettings, b: PhoneSettings): Boolean = when (this) {
            PLACEMENT -> a.placement != b.placement
            SOUNDS -> a.sounds != b.sounds
            PREVIEW -> a.preview != b.preview
        }

        fun write(entry: JsonObject, settings: PhoneSettings) = when (this) {
            PLACEMENT -> entry.addProperty(key, settings.placement.name.lowercase())
            SOUNDS -> entry.addProperty(key, settings.sounds)
            PREVIEW -> entry.addProperty(key, settings.preview)
        }
    }

    private companion object {
        const val VERSION = "version"
        const val PLAYERS = "players"
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
            fun bool(field: Field, default: Boolean) =
                json.get(field.key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default
            val placement = json.get(Field.PLACEMENT.key)?.takeIf(JsonElement::isJsonPrimitive)?.asString
                ?.let { name -> Placement.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            return PhoneSettings(
                placement = placement ?: defaults.placement,
                sounds = bool(Field.SOUNDS, defaults.sounds),
                preview = bool(Field.PREVIEW, defaults.preview),
            )
        }
    }
}
