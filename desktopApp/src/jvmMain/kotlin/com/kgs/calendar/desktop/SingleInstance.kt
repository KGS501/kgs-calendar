package com.kgs.calendar.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.util.Properties
import java.util.UUID
import kotlin.concurrent.thread

/**
 * One app per profile. The first start holds a lock in the profile's folder and listens on the local
 * machine; a later start (the Start menu, a shortcut) finds it there, asks it to show its window and
 * ends, instead of opening a second copy of the calendar next to it.
 */
class SingleInstance private constructor(
    // Held for as long as the app runs; the system releases it when the app ends, however it ends.
    @Suppress("unused") private val lock: FileLock?,
    private val server: ServerSocket?,
    private val token: String,
) {
    /** Calls [onShow] (on its own thread) whenever another start asks this app to show itself. */
    fun listen(onShow: () -> Unit) {
        val server = server ?: return
        thread(name = "kgs-instance", isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching {
                    socket.use {
                        it.soTimeout = 2_000
                        val request = it.getInputStream().bufferedReader().readLine()
                        if (request == "$token show") {
                            it.getOutputStream().write("ok\n".toByteArray())
                            onShow()
                        }
                    }
                }
            }
        }
    }

    companion object {
        /**
         * This start's claim to the profile in [dataDir], or null when another app already has it and
         * was asked to show itself, so this one should end. Without a lock or a port to listen on, or
         * when the other app doesn't answer, this one starts anyway rather than not at all.
         */
        fun claim(dataDir: File): SingleInstance? {
            val lockFile = File(dataDir, "instance.lock")
            val infoFile = File(dataDir, "instance.properties")
            runCatching { dataDir.mkdirs() }
            val channel = runCatching { RandomAccessFile(lockFile, "rw").channel }.getOrNull() ?: return unguarded()
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock != null) {
                val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull()
                    ?: return SingleInstance(lock, null, "")
                val token = UUID.randomUUID().toString()
                val info = Properties().apply {
                    this["port"] = server.localPort.toString()
                    this["token"] = token
                    this["pid"] = ProcessHandle.current().pid().toString()
                }
                runCatching { infoFile.outputStream().use { info.store(it, null) } }
                return SingleInstance(lock, server, token)
            }
            runCatching { channel.close() }
            // Another app holds the profile. It says how to reach it right after taking the lock, so a
            // moment's patience covers two starts in quick succession.
            repeat(HANDOVER_ATTEMPTS) {
                if (askToShow(infoFile)) return null
                Thread.sleep(HANDOVER_RETRY_MS)
            }
            return unguarded()
        }

        private fun unguarded() = SingleInstance(null, null, "")

        private fun askToShow(infoFile: File): Boolean = runCatching {
            val info = Properties().apply { infoFile.inputStream().use(::load) }
            val port = info.getProperty("port")?.toIntOrNull() ?: return false
            val token = info.getProperty("token") ?: return false
            // The running app may then bring its window to the front: Windows lets only the program
            // the user just started (this one) hand the foreground on.
            info.getProperty("pid")?.toIntOrNull()?.let(::allowForeground)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000)
                socket.soTimeout = 3_000
                socket.getOutputStream().write("$token show\n".toByteArray())
                socket.getInputStream().bufferedReader().readLine() == "ok"
            }
        }.getOrDefault(false)

        @Suppress("FunctionName")
        private interface User32 : Library {
            fun AllowSetForegroundWindow(processId: Int): Boolean
        }

        private fun allowForeground(pid: Int) {
            runCatching { Native.load("user32", User32::class.java).AllowSetForegroundWindow(pid) }
        }

        private const val HANDOVER_ATTEMPTS = 10
        private const val HANDOVER_RETRY_MS = 200L
    }
}
