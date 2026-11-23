package fr.bde_teknantes.lnb.phone.client.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The three things [PhoneSettingsStore] asks of the disk. A seam: tests put a disk that refuses in its place, which
 * works the same on every system, where file permissions don't (Windows' read-only flag, POSIX modes ignored by root).
 */
internal interface SettingsDisk {
    /**
     * The whole of [file] as UTF-8 text.
     *
     * @throws java.nio.file.NoSuchFileException when there is no such file.
     * @throws java.nio.charset.CharacterCodingException when the file was read but isn't UTF-8.
     * @throws java.io.IOException when the file can't be read right now.
     */
    fun read(file: Path): String

    /** Replaces [file] with [text] in one step, creating its folder. @throws java.io.IOException when it can't. */
    fun write(file: Path, text: String)

    /** Renames [file] to [target]. @throws java.io.IOException when it can't. */
    fun move(file: Path, target: Path)

    /** The real disk. */
    object Real : SettingsDisk {
        override fun read(file: Path): String = Files.readString(file)

        override fun write(file: Path, text: String) {
            val folder = file.toAbsolutePath().parent
            Files.createDirectories(folder)
            // A temporary file of its own, then moved over the settings: a crash mid-write leaves the old settings, and
            // two games writing at once never share a half-written file.
            val temp = Files.createTempFile(folder, file.fileName.toString(), ".tmp")
            try {
                Files.writeString(temp, text)
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
        }

        override fun move(file: Path, target: Path) {
            Files.move(file, target)
        }
    }
}
