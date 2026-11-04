package com.kgs.calendar.desktop

import java.io.File
import java.io.RandomAccessFile

/**
 * Where the calendar keeps its data: `%LOCALAPPDATA%\KGS501\KGS Calendar`.
 *
 * The installer puts the program into `%LOCALAPPDATA%\KGS Calendar` and, like every jpackage installer,
 * deletes that whole folder when the program is uninstalled or replaced by an update. The data must
 * therefore never be there. The previews kept it there; [adoptPreviewData] brings it over.
 */
internal object DataLocation {
    private val localAppData: File =
        System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")

    val default: File get() = File(localAppData, "KGS501/KGS Calendar")

    /** Where the previews kept the data, which is also where the installer puts the program. */
    private val preview: File get() = File(localAppData, "KGS Calendar")

    /**
     * Copies the previews' data into [target] when [target] has none yet, so that a portable copy (or an
     * installed preview not yet updated) carries on where it was. The old files stay where they are.
     */
    fun adoptPreviewData(target: File) {
        runCatching {
            val source = preview
            if (File(target, DATABASE).exists() || !File(source, DATABASE).isFile) return
            // A preview still running would be changing the files while they are copied.
            if (isLocked(File(source, "instance.lock"))) return
            target.mkdirs()
            source.listFiles()
                ?.filter { it.name !in ProgramFiles && !it.name.endsWith(".lck") }
                ?.forEach { file -> file.copyRecursively(File(target, file.name), overwrite = false) }
        }.onFailure { println("KGS preview data not adopted: $it") }
    }

    private fun isLocked(lockFile: File): Boolean {
        if (!lockFile.exists()) return false
        return runCatching {
            RandomAccessFile(lockFile, "rw").channel.use { channel -> channel.tryLock()?.release() == null }
        }.getOrDefault(true)
    }

    private const val DATABASE = "kgs-calendar.db"

    /** The program's own files in the previews' folder, and the running app's markers. */
    private val ProgramFiles = setOf("app", "runtime", "KGS Calendar.exe", "KGS Calendar.ico", "instance.lock", "instance.properties")
}
