package com.kgs.calendar.domain.source

import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.model.SourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/**
 * The user's changes to how calendars are grouped in the sidebar and the source lists.
 * [assignments] moves calendars (by href) into another group; [names] renames groups by key.
 * A custom group ([CalendarSourceGroups.CUSTOM_PREFIX]) exists while a calendar is assigned to it.
 */
data class CalendarGroupSettings(
    val assignments: Map<String, String> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
) {
    fun encode(): String = JSONObject()
        .put(KEY_ASSIGNMENTS, JSONObject(assignments))
        .put(KEY_NAMES, JSONObject(names))
        .toString()

    companion object {
        private const val KEY_ASSIGNMENTS = "assignments"
        private const val KEY_NAMES = "names"

        fun decode(value: String?): CalendarGroupSettings {
            val json = value?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return CalendarGroupSettings()
            return CalendarGroupSettings(
                assignments = json.optJSONObject(KEY_ASSIGNMENTS).toStringMap(),
                names = json.optJSONObject(KEY_NAMES).toStringMap(),
            )
        }

        private fun JSONObject?.toStringMap(): Map<String, String> {
            val json = this ?: return emptyMap()
            return json.keys().asSequence()
                .mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() }?.let { key to it } }
                .toMap()
        }
    }
}

enum class CalendarSourceGroupKind { Local, AndroidDevice, CalDav, ReadOnlyUrl, Custom }

/**
 * One top-level group of calendars. [defaultName] is the name the group gets without a user
 * rename ([customName]); null means the UI's localized name for [kind] (local, device calendars).
 * [accounts] are the sources whose calendars are shown here, in source order.
 */
data class CalendarSourceGroup(
    val key: String,
    val kind: CalendarSourceGroupKind,
    val defaultName: String?,
    val customName: String?,
    val accounts: List<AccountEntity>,
    val collections: List<CollectionEntity>,
) {
    val name: String? get() = customName ?: defaultName
}

/**
 * Groups calendars by where they come from. Every source is its own group, except read-only
 * iCalendar URLs: those are grouped by registrable domain (so `a.example.com/x.ics` and
 * `b.example.com/y.ics` share "example.com"), and join the CalDAV account of that domain when
 * there is one. [CalendarGroupSettings] then moves single calendars and renames groups.
 */
object CalendarSourceGroups {
    const val LOCAL_KEY = "local"
    const val ANDROID_KEY = "android"
    const val CUSTOM_PREFIX = "custom:"
    private const val ACCOUNT_PREFIX = "account:"
    private const val DOMAIN_PREFIX = "domain:"

    fun group(
        accounts: List<AccountEntity>,
        collections: List<CollectionEntity>,
        settings: CalendarGroupSettings = CalendarGroupSettings(),
    ): List<CalendarSourceGroup> {
        val accountsById = accounts.associateBy { it.id }
        val calDavByDomain = accounts
            .filter { it.groupKind() == CalendarSourceGroupKind.CalDav }
            .mapNotNull { account -> registrableDomain(account.serverUrl)?.let { it to account } }
            .distinctBy { it.first }
            .toMap()
        val automaticKeys = accounts.associate { account -> account.id to automaticKey(account, calDavByDomain) }

        // Group keys in source order, so the sidebar keeps the order of the sources.
        val order = LinkedHashMap<String, MutableList<CollectionEntity>>()
        accounts.forEach { account -> order.getOrPut(automaticKeys.getValue(account.id)) { mutableListOf() } }
        collections.forEach { collection ->
            val automatic = automaticKeys[collection.accountId] ?: return@forEach
            val assigned = settings.assignments[collection.href]?.takeIf { it.isKnownKey(automaticKeys.values) }
            order.getOrPut(assigned ?: automatic) { mutableListOf() } += collection
        }
        return order.map { (key, members) ->
            val memberAccounts = members.mapNotNull { accountsById[it.accountId] }.distinctBy { it.id }
                .ifEmpty { accounts.filter { automaticKeys[it.id] == key } }
                .sortedBy { accounts.indexOf(it) }
            CalendarSourceGroup(
                key = key,
                kind = kindOf(key, memberAccounts),
                defaultName = defaultName(key, memberAccounts, accounts, automaticKeys),
                customName = settings.names[key]?.trim()?.takeIf { it.isNotEmpty() },
                accounts = memberAccounts,
                collections = members,
            )
        }
    }

    /** The group [account]'s calendars belong to without a user assignment. */
    fun automaticKey(account: AccountEntity, accounts: List<AccountEntity>): String =
        automaticKey(
            account,
            accounts.filter { it.groupKind() == CalendarSourceGroupKind.CalDav }
                .mapNotNull { other -> registrableDomain(other.serverUrl)?.let { it to other } }
                .distinctBy { it.first }
                .toMap(),
        )

    fun isCustomKey(key: String): Boolean = key.startsWith(CUSTOM_PREFIX)

    /** `foo.bar.example.co.uk` → `example.co.uk`; the host itself for IPs and unknown suffixes. */
    fun registrableDomain(url: String): String? {
        val httpUrl = transportUrl(url).toHttpUrlOrNull() ?: return null
        return httpUrl.topPrivateDomain() ?: httpUrl.host
    }

    private fun automaticKey(account: AccountEntity, calDavByDomain: Map<String, AccountEntity>): String =
        when (account.groupKind()) {
            CalendarSourceGroupKind.Local -> LOCAL_KEY
            CalendarSourceGroupKind.AndroidDevice -> ANDROID_KEY
            CalendarSourceGroupKind.ReadOnlyUrl -> {
                val domain = registrableDomain(account.serverUrl)
                calDavByDomain[domain]?.let { ACCOUNT_PREFIX + it.id }
                    ?: domain?.let { DOMAIN_PREFIX + it }
                    ?: ACCOUNT_PREFIX + account.id
            }
            else -> ACCOUNT_PREFIX + account.id
        }

    private fun String.isKnownKey(automaticKeys: Collection<String>): Boolean =
        isCustomKey(this) || this in automaticKeys

    private fun kindOf(key: String, accounts: List<AccountEntity>): CalendarSourceGroupKind = when {
        isCustomKey(key) -> CalendarSourceGroupKind.Custom
        key == LOCAL_KEY -> CalendarSourceGroupKind.Local
        key == ANDROID_KEY -> CalendarSourceGroupKind.AndroidDevice
        key.startsWith(DOMAIN_PREFIX) -> CalendarSourceGroupKind.ReadOnlyUrl
        else -> accounts.firstOrNull { ACCOUNT_PREFIX + it.id == key }?.groupKind()
            ?: accounts.firstOrNull()?.groupKind()
            ?: CalendarSourceGroupKind.CalDav
    }

    private fun defaultName(
        key: String,
        members: List<AccountEntity>,
        accounts: List<AccountEntity>,
        automaticKeys: Map<String, String>,
    ): String? = when {
        isCustomKey(key) -> null
        key.startsWith(DOMAIN_PREFIX) -> {
            // A single feed keeps its source name; several feeds are named after their domain.
            val feeds = accounts.filter { automaticKeys[it.id] == key }
            feeds.singleOrNull()?.sourceName() ?: key.removePrefix(DOMAIN_PREFIX)
        }
        else -> (accounts.firstOrNull { ACCOUNT_PREFIX + it.id == key } ?: members.firstOrNull())?.sourceName()
    }

    private fun AccountEntity.sourceName(): String = displayName?.takeIf { it.isNotBlank() } ?: username

    private fun transportUrl(url: String): String = when {
        url.startsWith("webcal://", ignoreCase = true) -> "http://" + url.substringAfter("://")
        url.startsWith("webcals://", ignoreCase = true) -> "https://" + url.substringAfter("://")
        else -> url
    }
}

fun AccountEntity.groupKind(): CalendarSourceGroupKind = when {
    isLocalAccount() -> CalendarSourceGroupKind.Local
    isAndroidProviderAccount() -> CalendarSourceGroupKind.AndroidDevice
    sourceType == SourceType.ReadOnlyUrl || id.startsWith(READ_ONLY_PREFIX) -> CalendarSourceGroupKind.ReadOnlyUrl
    else -> CalendarSourceGroupKind.CalDav
}
