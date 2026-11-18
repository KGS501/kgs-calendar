package com.kgs.calendar.ui.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference

/**
 * The Microsoft Store package the app runs from, if any. A Store app is installed in a read-only
 * folder, updated by the Store, and its data goes when it is uninstalled.
 */
object WindowsPackage {
    /** The app's identity in Windows while it runs from a package; null otherwise. */
    val appUserModelId: String? by lazy {
        runCatching {
            val length = IntByReference(MAX_ID_LENGTH)
            val id = CharArray(MAX_ID_LENGTH)
            if (Kernel32App.INSTANCE.GetCurrentApplicationUserModelId(length, id) != 0) return@runCatching null
            String(id, 0, length.value - 1)
        }.getOrNull()
    }

    val isStoreApp: Boolean get() = appUserModelId != null

    private const val MAX_ID_LENGTH = 130

    @Suppress("FunctionName")
    private interface Kernel32App : Library {
        fun GetCurrentApplicationUserModelId(length: IntByReference, id: CharArray): Int

        companion object {
            val INSTANCE: Kernel32App = Native.load("kernel32", Kernel32App::class.java)
        }
    }
}
