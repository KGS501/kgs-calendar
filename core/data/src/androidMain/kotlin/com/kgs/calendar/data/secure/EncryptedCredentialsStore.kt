package com.kgs.calendar.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

class EncryptedCredentialsStore(context: Context) : CredentialsStore {
    private val prefs: SharedPreferences = openOrReset(context.applicationContext)

    override fun save(credentials: StoredCredentials) {
        save(PRIMARY_ID, credentials)
        prefs.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_APP_PASSWORD, credentials.appPassword)
            .apply()
    }

    override fun save(accountId: String, credentials: StoredCredentials) {
        prefs.edit()
            .putString(accountKey(accountId, KEY_SERVER), credentials.serverUrl)
            .putString(accountKey(accountId, KEY_USERNAME), credentials.username)
            .putString(accountKey(accountId, KEY_APP_PASSWORD), credentials.appPassword)
            .apply()
    }

    override fun get(accountId: String): StoredCredentials? {
        val server = prefs.getString(accountKey(accountId, KEY_SERVER), null)
            ?: (if (accountId == PRIMARY_ID) prefs.getString(KEY_SERVER, null) else null)
            ?: return null
        val username = prefs.getString(accountKey(accountId, KEY_USERNAME), null)
            ?: (if (accountId == PRIMARY_ID) prefs.getString(KEY_USERNAME, null) else null)
            ?: return null
        val password = prefs.getString(accountKey(accountId, KEY_APP_PASSWORD), null)
            ?: (if (accountId == PRIMARY_ID) prefs.getString(KEY_APP_PASSWORD, null) else null)
            ?: return null
        return StoredCredentials(server, username, password)
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun clear(accountId: String) {
        val editor = prefs.edit()
            .remove(accountKey(accountId, KEY_SERVER))
            .remove(accountKey(accountId, KEY_USERNAME))
            .remove(accountKey(accountId, KEY_APP_PASSWORD))
        if (accountId == PRIMARY_ID) {
            editor
                .remove(KEY_SERVER)
                .remove(KEY_USERNAME)
                .remove(KEY_APP_PASSWORD)
        }
        editor.apply()
    }

    companion object {
        private const val PREF_NAME = "kgs_secure_credentials"
        private const val PRIMARY_ID = CredentialsStore.PRIMARY_ID
        private const val KEY_SERVER = "server"
        private const val KEY_USERNAME = "username"
        private const val KEY_APP_PASSWORD = "app_password"

        private fun accountKey(accountId: String, key: String): String =
            "account.$accountId.$key"

        /**
         * A Keystore key that can no longer decrypt the stored keyset (seen after OS updates and
         * restores) makes opening throw on every start, and the app graph with it. The credentials
         * are lost either way, so start with an empty store and a new key: the accounts then ask
         * to be reconnected instead of the app crashing at launch.
         */
        private fun openOrReset(context: Context): SharedPreferences {
            var failure: Exception? = null
            // A second attempt rules out a one-off Keystore hiccup before anything is deleted.
            repeat(OPEN_ATTEMPTS) {
                try {
                    return open(context)
                } catch (error: GeneralSecurityException) {
                    failure = error
                } catch (error: IOException) {
                    failure = error
                }
            }
            Log.w(TAG, "Stored credentials are unreadable; resetting them.", failure)
            context.deleteSharedPreferences(PREF_NAME)
            // This store is the master key's only user; a broken key must be replaced as well.
            runCatching {
                KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            return open(context)
        }

        private fun open(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private const val TAG = "KgsCredentials"
        private const val OPEN_ATTEMPTS = 2
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
