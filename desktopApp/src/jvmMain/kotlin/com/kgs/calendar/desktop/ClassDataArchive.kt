package com.kgs.calendar.desktop

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Faster starts from the second one on, by class data sharing: the JVM maps an archive of the app's
 * classes, ready to use, instead of reading and checking each of them from the jars.
 *
 * Such an archive only fits the folder the app is installed in, so it is made here, on the user's
 * computer: the packaged app lists the classes it loads (`-XX:DumpLoadedClassList`, which costs nothing),
 * and a while after the start, if there is no archive for this version yet, [buildLater] has a separate
 * JVM build one from that list, at low priority, so the app itself never waits for it. Then the JVM
 * options file the app starts with (`-XX:VMOptionsFile`, empty as installed and after every update) is
 * switched to the archive. A missing or outdated archive is no problem: the JVM then starts without it.
 */
internal object ClassDataArchive {
    private const val OPTIONS_FILE = "kgs-jvm.options"
    private const val CLASS_LIST = "kgs-classes.lst"
    private const val ARCHIVE_PREFIX = "kgs-classes-"
    private const val ARCHIVE_SUFFIX = ".jsa"
    private const val KEY_FILE = "kgs-classes.key"

    /** The archive is somewhat larger than the jars; it is only made with plenty of room left. */
    private const val MIN_FREE_BYTES = 1_000_000_000L

    @Volatile private var started = false

    /** Builds the archive in the background if there is none yet; blocks the calling thread while it runs. */
    fun build() {
        if (started) return
        started = true
        runCatching { buildArchive() }.onFailure { println("KGS class archive not built: $it") }
    }

    private fun buildArchive() {
        val resources = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return
        val options = File(resources, OPTIONS_FILE)
        val classList = File(resources, CLASS_LIST)
        val javaw = File(System.getProperty("java.home"), "bin/javaw.exe")
        if (!options.isFile || !classList.isFile || !javaw.isFile) return
        val classPath = System.getProperty("java.class.path").orEmpty()
        // Once per version: the archive fits this class path (with its jars' versions) and this JVM.
        val key = "${System.getProperty("java.vm.version")}|$classPath"
        val keyFile = File(resources, KEY_FILE)
        val current = options.readText().isNotBlank() && runCatching { keyFile.readText() }.getOrNull() == key
        if (current) return
        if (resources.usableSpace < MIN_FREE_BYTES) return

        // The JVM reads its options and argument files in the system's code page.
        val charset = runCatching { Charset.forName(System.getProperty("sun.jnu.encoding")) }.getOrDefault(Charsets.UTF_8)
        val archive = File(resources, "$ARCHIVE_PREFIX${System.currentTimeMillis()}$ARCHIVE_SUFFIX")
        val optionsLine = "\"-XX:SharedArchiveFile=${archive.absolutePath}\"\n"
        if (classPath.isEmpty() || !charset.newEncoder().canEncode(optionsLine + classPath)) return

        // This run still adds to the list; the archive is built from what it holds now.
        val listCopy = File(resources, "$CLASS_LIST.build")
        Files.copy(classList.toPath(), listCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val argFile = File(resources, "kgs-classes.args")
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\") + "\""
        argFile.writeText(
            listOf(
                "-Xshare:dump",
                "-XX:ActiveProcessorCount=1",
                quoted("-XX:SharedClassListFile=${listCopy.absolutePath}"),
                quoted("-XX:SharedArchiveFile=${archive.absolutePath}"),
                "-cp",
                // Exactly the class path this app runs with: the archive only fits that one.
                quoted(classPath),
            ).joinToString("\n", postfix = "\n"),
            charset,
        )
        try {
            val process = ProcessBuilder(javaw.absolutePath, "@${argFile.absolutePath}")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            lowerPriority(process)
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                archive.delete()
                return
            }
            if (process.exitValue() != 0 || !archive.isFile || archive.length() == 0L) {
                archive.delete()
                return
            }
        } finally {
            listCopy.delete()
            argFile.delete()
        }

        val pending = File(resources, "$OPTIONS_FILE.tmp")
        pending.writeText(optionsLine, charset)
        Files.move(pending.toPath(), options.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        keyFile.writeText(key)
        // Archives of earlier versions or attempts; this run started without any of them.
        resources.listFiles { file -> file.name.startsWith(ARCHIVE_PREFIX) && file.name.endsWith(ARCHIVE_SUFFIX) && file != archive }
            ?.forEach { old -> old.setWritable(true); old.delete() }
        println("KGS class archive built: ${archive.name} (${archive.length() / 1_000_000} MB)")
    }

    /** Below normal, so that the build never competes with the app or anything else the user does. */
    private fun lowerPriority(process: Process) {
        runCatching {
            val handle: WinNT.HANDLE = Kernel32.INSTANCE.OpenProcess(PROCESS_SET_INFORMATION, false, process.pid().toInt()) ?: return
            try {
                Kernel32.INSTANCE.SetPriorityClass(handle, WinDef.DWORD(BELOW_NORMAL_PRIORITY_CLASS))
            } finally {
                Kernel32.INSTANCE.CloseHandle(handle)
            }
        }
    }

    private const val PROCESS_SET_INFORMATION = 0x0200
    private const val BELOW_NORMAL_PRIORITY_CLASS = 0x00004000L
}
