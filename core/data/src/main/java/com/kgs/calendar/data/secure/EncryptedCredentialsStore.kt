package com.kgs.calendar.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kgs.calendar.data.remote.DavOrigins
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/** How [EncryptedCredentialsStore] reaches its encrypted file; replaceable so tests can make it fail. */
internal interface CredentialsFile {
    fun open(): SharedPreferences

    /** Deletes the file and the key that encrypts it. */
    fun delete()
}

/**
 * Saved logins in encrypted preferences. Failing to open them proves nothing about the data: a
 * Keystore hiccup looks the same as a key that is gone for good, and deleting on a guess would lose
 * every account's login. So the file and key stay untouched and the store just reports itself
 * unreadable, retrying on later use, until the user chooses [resetUnreadable] after being told what
 * it does. Calendars and queued changes remain usable on the device in the meantime.
 */
class EncryptedCredentialsStore internal constructor(private val file: CredentialsFile) : CredentialsStore {
    constructor(context: Context) : this(AndroidCredentialsFile(context.applicationContext))

    private val lock = Any()
    private var opened: SharedPreferences? = null

    /** The open preferences, or null when they can't be opened right now. */
    private fun prefsOrNull(): SharedPreferences? = synchronized(lock) {
        opened?.let { return it }
        var failure: Exception? = null
        // A second attempt rules out a one-off Keystore hiccup before reporting anything.
        repeat(OPEN_ATTEMPTS) {
            try {
                return file.open().also { opened = it }
            } catch (error: GeneralSecurityException) {
                failure = error
            } catch (error: IOException) {
                failure = error
            }
        }
        Log.w(TAG, "Stored credentials can't be opened right now.", failure)
        null
    }

    private fun prefs(): SharedPreferences = prefsOrNull()
        ?: throw CredentialsUnavailableException("Saved logins can't be read right now. They were not changed; try again, or reset them in the source's settings.")

    override fun isReadable(): Boolean = prefsOrNull() != null

    override fun resetUnreadable() {
        synchronized(lock) {
            opened = null
            file.delete()
        }
    }

    override fun save(credentials: StoredCredentials) {
        save(PRIMARY_ID, credentials)
        prefs().edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_APP_PASSWORD, credentials.appPassword)
            .apply()
    }

    override fun save(accountId: String, credentials: StoredCredentials) {
        prefs().edit()
            .putString(accountKey(accountId, KEY_SERVER), credentials.serverUrl)
            .putString(accountKey(accountId, KEY_USERNAME), credentials.username)
            .putString(accountKey(accountId, KEY_APP_PASSWORD), credentials.appPassword)
            .putStringSet(accountKey(accountId, KEY_APPROVED_ORIGINS), credentials.origins.approved)
            .putString(accountKey(accountId, KEY_DAV_ORIGIN), credentials.origins.davOrigin)
            .apply()
    }

    override fun get(accountId: String): StoredCredentials? {
        val prefs = prefs()
        val server = prefs.getString(accountKey(accountId, KEY_SERVER), null)
            ?: (if (accountId == PRIMARY_ID) prefs.getString(KEY_SERVER, null) else null)
            ?: return null
        val username = prefs.getString(accountKey(accountId, KEY_USERNAME), null)
            ?: (if (accountId == PRIMARY_ID) prefs.getString(KEY_USERNAME, null) else null)
            ?: return null
        val password = prefs.getString(accountKey(accountId, KEY_APP_PASSWORD), null)
            ?: (if (accountId == PRIMARY_ID) prefs.getString(KEY_APP_PASSWORD, null) else null)
            ?: return null
        val origins = DavOrigins(
            approved = prefs.getStringSet(accountKey(accountId, KEY_APPROVED_ORIGINS), null).orEmpty().toSet(),
            davOrigin = prefs.getString(accountKey(accountId, KEY_DAV_ORIGIN), null),
        )
        return StoredCredentials(server, username, password, origins)
    }

    override fun clear() {
        prefsOrNull()?.edit()?.clear()?.apply()
    }

    /** With the store unreadable there is nothing to remove yet; a later reset covers these entries. */
    override fun clear(accountId: String) {
        val editor = (prefsOrNull() ?: return).edit()
            .remove(accountKey(accountId, KEY_SERVER))
            .remove(accountKey(accountId, KEY_USERNAME))
            .remove(accountKey(accountId, KEY_APP_PASSWORD))
            .remove(accountKey(accountId, KEY_APPROVED_ORIGINS))
            .remove(accountKey(accountId, KEY_DAV_ORIGIN))
        if (accountId == PRIMARY_ID) {
            editor
                .remove(KEY_SERVER)
                .remove(KEY_USERNAME)
                .remove(KEY_APP_PASSWORD)
        }
        editor.apply()
    }

    private companion object {
        const val PRIMARY_ID = CredentialsStore.PRIMARY_ID
        const val KEY_SERVER = "server"
        const val KEY_USERNAME = "username"
        const val KEY_APP_PASSWORD = "app_password"
        const val KEY_APPROVED_ORIGINS = "approved_origins"
        const val KEY_DAV_ORIGIN = "dav_origin"
        const val TAG = "KgsCredentials"
        const val OPEN_ATTEMPTS = 2

        fun accountKey(accountId: String, key: String): String = "account.$accountId.$key"
    }
}

private class AndroidCredentialsFile(private val context: Context) : CredentialsFile {
    override fun open(): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    override fun delete() {
        context.deleteSharedPreferences(PREF_NAME)
        // This store is the master key's only user; a broken key must be replaced as well.
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    private companion object {
        const val PREF_NAME = "kgs_secure_credentials"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
