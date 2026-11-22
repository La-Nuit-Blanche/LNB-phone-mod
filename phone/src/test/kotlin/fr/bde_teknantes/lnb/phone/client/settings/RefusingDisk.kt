package fr.bde_teknantes.lnb.phone.client.settings

import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Path

/**
 * The real disk, which can be told to refuse reads or writes as a busy or read-only file does: a sharing violation, a
 * sync client holding the file, a full disk. The same on every system, unlike file permissions.
 */
internal class RefusingDisk : SettingsDisk {
    var refuseReads = false
    var refuseWrites = false

    /** Writes that reached the disk. */
    var writes = 0
        private set

    override fun read(file: Path): String {
        if (refuseReads) throw FileSystemException(file.toString(), null, "The process cannot access the file because it is being used by another process")
        return SettingsDisk.Real.read(file)
    }

    override fun write(file: Path, text: String) {
        if (refuseWrites) throw IOException("Access is denied: $file")
        SettingsDisk.Real.write(file, text)
        writes++
    }

    override fun move(file: Path, target: Path) {
        if (refuseWrites) throw IOException("Access is denied: $file")
        SettingsDisk.Real.move(file, target)
    }
}
