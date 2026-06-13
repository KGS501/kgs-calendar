package com.kgs.calendar.data.remote

import org.json.JSONArray
import org.json.JSONObject

data class CalDavAccountDiscovery(
    val serviceUrl: String,
    val principalUrl: String?,
    val calendarHomeUrl: String,
    val calendarUserAddresses: Set<String> = emptySet(),
    val scheduleInboxUrl: String? = null,
    val scheduleOutboxUrl: String? = null,
    val serverDavCapabilities: Set<String> = emptySet(),
    val supportedReports: Set<String> = emptySet(),
) {
    val supportsScheduling: Boolean
        get() = scheduleInboxUrl != null || scheduleOutboxUrl != null ||
            serverDavCapabilities.any { it.equals("calendar-schedule", ignoreCase = true) }

    fun toCapabilitiesJson(): String = JSONObject()
        .put("serviceUrl", serviceUrl)
        .put("principalUrl", principalUrl)
        .put("calendarHomeUrl", calendarHomeUrl)
        .put("calendarUserAddresses", JSONArray(calendarUserAddresses.sorted()))
        .put("scheduleInboxUrl", scheduleInboxUrl)
        .put("scheduleOutboxUrl", scheduleOutboxUrl)
        .put("serverDavCapabilities", JSONArray(serverDavCapabilities.sorted()))
        .put("supportedReports", JSONArray(supportedReports.sorted()))
        .put("supportsScheduling", supportsScheduling)
        .toString()
}

data class CalDavCollectionCapabilities(
    val privileges: Set<String> = emptySet(),
    val supportedReports: Set<String> = emptySet(),
    val resourceTypes: Set<String> = emptySet(),
    val maxResourceSize: Long? = null,
    val maxAttendeesPerInstance: Int? = null,
) {
    private fun hasPrivilege(name: String): Boolean =
        privileges.any { it.equals(name, ignoreCase = true) }

    val privilegesKnown: Boolean
        get() = privileges.isNotEmpty()

    val canRead: Boolean
        get() = !privilegesKnown || hasPrivilege("read") || hasPrivilege("all")

    val canWriteContent: Boolean
        get() = hasPrivilege("write") || hasPrivilege("write-content") || hasPrivilege("all")

    val canWriteProperties: Boolean
        get() = hasPrivilege("write") || hasPrivilege("write-properties") || hasPrivilege("all")

    val canCreateResources: Boolean
        get() = hasPrivilege("write") || hasPrivilege("bind") || hasPrivilege("all")

    val canDeleteResources: Boolean
        get() = hasPrivilege("write") || hasPrivilege("unbind") || hasPrivilege("all")

    val canReadFreeBusy: Boolean
        get() = hasPrivilege("read-free-busy") || canRead

    val supportsSyncCollection: Boolean
        get() = supportedReports.any { it.equals("sync-collection", ignoreCase = true) }

    val supportsCalendarMultiget: Boolean
        get() = supportedReports.any { it.equals("calendar-multiget", ignoreCase = true) }

    val supportsCalendarQuery: Boolean
        get() = supportedReports.any { it.equals("calendar-query", ignoreCase = true) }

    val isScheduleInbox: Boolean
        get() = resourceTypes.any { it.equals("schedule-inbox", ignoreCase = true) }

    fun toJson(): String = JSONObject()
        .put("privileges", JSONArray(privileges.sorted()))
        .put("supportedReports", JSONArray(supportedReports.sorted()))
        .put("resourceTypes", JSONArray(resourceTypes.sorted()))
        .put("maxResourceSize", maxResourceSize)
        .put("maxAttendeesPerInstance", maxAttendeesPerInstance)
        .put("canRead", canRead)
        .put("canWriteContent", canWriteContent)
        .put("canWriteProperties", canWriteProperties)
        .put("canCreateResources", canCreateResources)
        .put("canDeleteResources", canDeleteResources)
        .put("canReadFreeBusy", canReadFreeBusy)
        .put("supportsSyncCollection", supportsSyncCollection)
        .put("supportsCalendarMultiget", supportsCalendarMultiget)
        .put("supportsCalendarQuery", supportsCalendarQuery)
        .put("isScheduleInbox", isScheduleInbox)
        .toString()
}

data class RemoteCollection(
    val href: String,
    val displayName: String,
    val color: Int?,
    val supportsEvents: Boolean,
    val supportsTasks: Boolean,
    val syncToken: String?,
    val ctag: String?,
    val readOnly: Boolean,
    val capabilities: CalDavCollectionCapabilities = CalDavCollectionCapabilities(),
)

data class RemoteResource(
    val href: String,
    val etag: String?,
)

data class RemoteResourceData(
    val href: String,
    val etag: String?,
    val calendarData: String,
)

data class PutResult(
    val href: String,
    val etag: String?,
)

data class SyncCollectionResult(
    val changedResources: List<RemoteResource>,
    val deletedHrefs: Set<String>,
    val syncToken: String,
)
