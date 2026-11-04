package com.kgs.calendar.data.secure

import com.kgs.calendar.data.remote.DavOrigins
import com.kgs.calendar.json.JSONObject
import com.kgs.calendar.platform.toByteArray
import com.kgs.calendar.platform.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * App passwords in the iOS Keychain, one generic-password item per account. Readable after the
 * first unlock, so background refresh can sync.
 */
class KeychainCredentialsStore(
    private val service: String = "com.kgs501.kgscalendar.credentials",
) : CredentialsStore {
    private val knownAccountsKey = "__accounts__"

    override fun save(credentials: StoredCredentials) = save(CredentialsStore.PRIMARY_ID, credentials)

    override fun save(accountId: String, credentials: StoredCredentials) {
        val json = JSONObject()
            .put("server", credentials.serverUrl)
            .put("username", credentials.username)
            .put("appPassword", credentials.appPassword)
            .put("approvedOrigins", credentials.origins.approved.joinToString(" "))
            .put("davOrigin", credentials.origins.davOrigin.orEmpty())
            .toString()
        write(accountId, json.encodeToByteArray())
        val accounts = knownAccounts() + accountId
        write(knownAccountsKey, accounts.joinToString("\n").encodeToByteArray())
    }

    override fun get(accountId: String): StoredCredentials? {
        val json = read(accountId)?.decodeToString() ?: return null
        return runCatching {
            val o = JSONObject(json)
            StoredCredentials(
                o.getString("server"),
                o.getString("username"),
                o.getString("appPassword"),
                DavOrigins(
                    approved = o.optString("approvedOrigins").split(' ').filter { it.isNotBlank() }.toSet(),
                    davOrigin = o.optString("davOrigin").takeIf { it.isNotBlank() },
                ),
            )
        }.getOrNull()
    }

    override fun clear() {
        knownAccounts().forEach(::delete)
        delete(knownAccountsKey)
    }

    override fun clear(accountId: String) {
        delete(accountId)
        write(knownAccountsKey, (knownAccounts() - accountId).joinToString("\n").encodeToByteArray())
    }

    private fun knownAccounts(): Set<String> =
        read(knownAccountsKey)?.decodeToString()?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private fun query(account: String): CFMutableDictionaryRef {
        val dict = CFDictionaryCreateMutable(null, 0, null, null)!!
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFBridgingRetain(service))
        CFDictionaryAddValue(dict, kSecAttrAccount, CFBridgingRetain(account))
        return dict
    }

    private fun write(account: String, bytes: ByteArray) {
        delete(account)
        val dict = query(account)
        CFDictionaryAddValue(dict, kSecValueData, CFBridgingRetain(bytes.toNSData()))
        CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val status = SecItemAdd(dict as CFDictionaryRef, null)
        check(status == errSecSuccess) { "Keychain write failed ($status)" }
    }

    @OptIn(BetaInteropApi::class)
    private fun read(account: String): ByteArray? = memScoped {
        val dict = query(account)
        CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(dict as CFDictionaryRef, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        data.toByteArray()
    }

    private fun delete(account: String) {
        SecItemDelete(query(account) as CFDictionaryRef)
    }
}
