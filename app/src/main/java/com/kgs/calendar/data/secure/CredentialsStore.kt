package com.kgs.calendar.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(credentials: StoredCredentials) {
        save(PRIMARY_ID, credentials)
        prefs.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_APP_PASSWORD, credentials.appPassword)
            .apply()
    }

    fun save(accountId: String, credentials: StoredCredentials) {
        prefs.edit()
            .putString(accountKey(accountId, KEY_SERVER), credentials.serverUrl)
            .putString(accountKey(accountId, KEY_USERNAME), credentials.username)
            .putString(accountKey(accountId, KEY_APP_PASSWORD), credentials.appPassword)
            .apply()
    }

    fun get(accountId: String = PRIMARY_ID): StoredCredentials? {
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

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clear(accountId: String) {
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
        private const val PRIMARY_ID = "primary"
        private const val KEY_SERVER = "server"
        private const val KEY_USERNAME = "username"
        private const val KEY_APP_PASSWORD = "app_password"

        private fun accountKey(accountId: String, key: String): String =
            "account.$accountId.$key"
    }
}
