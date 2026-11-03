package com.kgs.calendar.data.secure

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.util.Base64
import java.util.Properties

/**
 * Credentials kept in [file], each value encrypted with the Windows data protection API (DPAPI),
 * so only the signed-in Windows user can read them. Off Windows nothing is written to disk and
 * the credentials last only as long as the process.
 */
class WindowsCredentialsStore(private val file: File) : CredentialsStore {
    private val lock = Any()
    private val protects = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val values: Properties = load()

    override fun save(credentials: StoredCredentials) = save(CredentialsStore.PRIMARY_ID, credentials)

    override fun save(accountId: String, credentials: StoredCredentials) = edit {
        setProperty(key(accountId, KEY_SERVER), seal(credentials.serverUrl))
        setProperty(key(accountId, KEY_USERNAME), seal(credentials.username))
        setProperty(key(accountId, KEY_APP_PASSWORD), seal(credentials.appPassword))
    }

    override fun get(accountId: String): StoredCredentials? = synchronized(lock) {
        val server = open(values.getProperty(key(accountId, KEY_SERVER))) ?: return null
        val username = open(values.getProperty(key(accountId, KEY_USERNAME))) ?: return null
        val password = open(values.getProperty(key(accountId, KEY_APP_PASSWORD))) ?: return null
        StoredCredentials(server, username, password)
    }

    override fun clear() = edit { clear() }

    override fun clear(accountId: String) = edit {
        remove(key(accountId, KEY_SERVER))
        remove(key(accountId, KEY_USERNAME))
        remove(key(accountId, KEY_APP_PASSWORD))
    }

    private fun edit(change: Properties.() -> Unit) = synchronized(lock) {
        values.change()
        if (!protects) return@synchronized
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { values.store(it, null) }
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    private fun load(): Properties = Properties().apply {
        if (protects && file.isFile) runCatching { file.inputStream().use(::load) }
    }

    private fun seal(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(if (protects) Crypt32Util.cryptProtectData(bytes) else bytes)
    }

    /** A value that can no longer be decrypted (another user, a restored profile) counts as missing. */
    private fun open(sealed: String?): String? = sealed?.let {
        runCatching {
            val bytes = Base64.getDecoder().decode(it)
            String(if (protects) Crypt32Util.cryptUnprotectData(bytes) else bytes, Charsets.UTF_8)
        }.getOrNull()
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_USERNAME = "username"
        const val KEY_APP_PASSWORD = "app_password"

        fun key(accountId: String, name: String): String = "account.$accountId.$name"
    }
}
