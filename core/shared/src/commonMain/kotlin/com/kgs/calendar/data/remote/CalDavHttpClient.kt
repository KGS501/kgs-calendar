package com.kgs.calendar.data.remote

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.normalizeServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kgs.calendar.platform.HttpRequest
import com.kgs.calendar.platform.HttpRequestBuilder
import com.kgs.calendar.platform.HttpResponse
import com.kgs.calendar.platform.HttpTransport
import com.kgs.calendar.platform.IOException
import com.kgs.calendar.platform.XmlElement
import com.kgs.calendar.platform.item
import com.kgs.calendar.platform.length
import com.kgs.calendar.platform.basicAuthorization
import com.kgs.calendar.platform.encodePathSegment
import com.kgs.calendar.platform.parseAndroidColor
import com.kgs.calendar.platform.parseUri
import com.kgs.calendar.platform.parseXmlDocument
import com.kgs.calendar.platform.resolveUri
import com.kgs.calendar.platform.toMediaType
import com.kgs.calendar.platform.toRequestBody
import com.kgs.calendar.platform.use
import kotlinx.coroutines.IO
import kotlin.coroutines.cancellation.CancellationException

/**
 * Small CalDAV transport focused on interoperable RFC 4791/5397/6578 behavior.
 *
 * Basic authentication is intentionally the generic credential mechanism here. Providers
 * which require OAuth still need a provider-specific authorization flow, but the discovered
 * DAV URLs and all subsequent requests remain standards based.
 */
class CalDavHttpClient(
    private val transport: HttpTransport,
) {
    suspend fun discoverAccount(
        serverUrl: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): CalDavAccountDiscovery = withContext(Dispatchers.IO) {
        val normalized = normalizeServer(serverUrl)
        val origin = originOf(normalized)
        val candidates = linkedSetOf(
            normalized,
            "$origin/.well-known/caldav",
        )

        var lastError: Throwable? = null
        candidates.forEach { candidate ->
            try {
                discoverFromEndpoint(candidate, normalized, username, appPassword, origins)?.let { return@withContext it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }

        // Compatibility fallback for older Nextcloud installations which don't expose
        // complete principal discovery at the URL entered by the user.
        val userId = resolveNextcloudUserId(normalized, username, appPassword, origins)
        val nextcloudBase = normalized.trimEnd('/')
        val home = "$nextcloudBase/remote.php/dav/calendars/${userId.urlPathSegment()}/"
        runCatching {
            propfind(home, username, appPassword, "0", HOME_PROPERTIES, normalized, origins)
        }.getOrElse { throw lastError ?: it }
        CalDavAccountDiscovery(
            serviceUrl = normalized,
            enteredServerUrl = normalized,
            principalUrl = "$nextcloudBase/remote.php/dav/principals/users/${userId.urlPathSegment()}/",
            calendarHomeUrl = home,
        )
    }

    suspend fun discoverCollections(
        discovery: CalDavAccountDiscovery,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): List<RemoteCollection> = withContext(Dispatchers.IO) {
        val serverUrl = discovery.enteredServerUrl ?: discovery.serviceUrl
        val homeOrigins = origins.withDavOriginOf(discovery.calendarHomeUrl, serverUrl)
        val responses = propfind(
            discovery.calendarHomeUrl,
            username,
            appPassword,
            depth = "1",
            body = COLLECTION_PROPERTIES,
            serverUrl = serverUrl,
            origins = homeOrigins,
        )
        responses
            .filterNot { it.href.isSameDavHref(discovery.calendarHomeUrl) }
            .mapNotNull { response ->
                // Nextcloud keeps a deleted calendar in its trash bin and still lists it here, typed
                // as a deleted calendar. Syncing it would bring the calendar back.
                if (DELETED_CALENDAR_TYPE in response.resourceTypes) return@mapNotNull null
                // The scheduling inbox and outbox only carry invitation messages between servers.
                // The server already files invitations into a calendar, and Nextcloud's own
                // calendar does not show either of them.
                if (response.isSchedulingMailbox(discovery)) return@mapNotNull null
                val isCalendar = "calendar" in response.resourceTypes
                val supported = response.supportedComponents
                if (!isCalendar && supported.isEmpty()) return@mapNotNull null
                val components = supported.ifEmpty { setOf("VEVENT") }
                val capabilities = CalDavCollectionCapabilities(
                    privileges = response.privileges,
                    supportedReports = response.supportedReports,
                    resourceTypes = response.resourceTypes,
                    maxResourceSize = response.maxResourceSize,
                    maxAttendeesPerInstance = response.maxAttendeesPerInstance,
                    publicUrl = response.publishUrl?.let { publicCalendarUrl(resolveUrl(discovery.calendarHomeUrl, it)) },
                )
                RemoteCollection(
                    href = stableHref(discovery.calendarHomeUrl, response.href, serverUrl, homeOrigins),
                    displayName = response.displayName ?: response.href.trim('/').substringAfterLast('/'),
                    color = response.color,
                    supportsEvents = "VEVENT" in components,
                    supportsTasks = "VTODO" in components,
                    syncToken = response.syncToken,
                    ctag = response.ctag,
                    readOnly = if (capabilities.privilegesKnown) {
                        !capabilities.canWriteContent
                    } else {
                        response.displayName.isLikelyReadOnlyCalendarName()
                            ?: response.href.isLikelyReadOnlyCalendarName()
                            ?: false
                    },
                    capabilities = capabilities,
                )
            }
    }

    /**
     * Backward-compatible entry point retained for callers/tests compiled against the
     * original Nextcloud-only client.
     */
    suspend fun resolveUserId(serverUrl: String, username: String, appPassword: String): String =
        withContext(Dispatchers.IO) {
            resolveNextcloudUserId(normalizeServer(serverUrl), username, appPassword, DavOrigins.None)
        }

    suspend fun discoverCollections(
        serverUrl: String,
        userId: String,
        username: String,
        appPassword: String,
    ): List<RemoteCollection> {
        val normalized = normalizeServer(serverUrl)
        return discoverCollections(
            CalDavAccountDiscovery(
                serviceUrl = normalized,
                principalUrl = null,
                calendarHomeUrl = "$normalized/remote.php/dav/calendars/${userId.urlPathSegment()}/",
            ),
            username,
            appPassword,
        )
    }

    suspend fun listResources(
        collectionHref: String,
        serverUrl: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): List<RemoteResource> = withContext(Dispatchers.IO) {
        val collectionUrl = absoluteUrl(serverUrl, collectionHref, origins)
        propfind(
            collectionUrl,
            username,
            appPassword,
            depth = "1",
            body = ETAG_PROPERTIES,
            serverUrl = serverUrl,
            origins = origins,
        )
            .filterNot { it.href.isSameDavHref(collectionHref) }
            .filterNot { it.href.endsWith("/") }
            .onEach {
                // A denied/failed member is not evidence that the cached resource was deleted.
                if (it.responseStatus != null && it.responseStatus !in 200..299 && it.responseStatus != 404) {
                    throw HttpStatusException(it.responseStatus, "Cannot list calendar member ${it.href}")
                }
            }
            .filterNot { it.responseStatus == 404 }
            .map { RemoteResource(stableHref(collectionUrl, it.href, serverUrl, origins), it.etag) }
    }

    /**
     * Returns null when the server doesn't support sync-collection or rejects an old token.
     * The caller should then perform one full ETag listing and replace its stored token with
     * the token advertised by discovery/the full response.
     */
    suspend fun syncCollection(
        serverUrl: String,
        collectionHref: String,
        previousSyncToken: String?,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): SyncCollectionResult? = withContext(Dispatchers.IO) {
        val collectionUrl = absoluteUrl(serverUrl, collectionHref, origins)
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
            appendLine("""<d:sync-collection xmlns:d="DAV:">""")
            appendLine("""  <d:sync-token>${previousSyncToken.orEmpty().xmlEscaped()}</d:sync-token>""")
            appendLine("""  <d:sync-level>1</d:sync-level>""")
            appendLine("""  <d:prop><d:getetag /></d:prop>""")
            appendLine("""</d:sync-collection>""")
        }
        val request = authenticatedRequest(collectionUrl, username, appPassword)
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        send(request, serverUrl, origins).use { response ->
            if (response.code in setOf(400, 403, 405, 409, 501)) return@withContext null
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "REPORT sync-collection $collectionHref failed: HTTP ${response.code}")
            }
            val xml = response.bodyText()
            val token = parseDocumentFirstText(xml, "sync-token") ?: return@withContext null
            val allResponses = parseDavResponses(xml)
            // RFC 6578 3.6: a 507 for the collection itself marks a truncated page, whose token
            // continues where it ended. A 507 on a member remains a failure like any other.
            val (selfResponses, davResponses) = allResponses.partition { it.href.isSameDavHref(collectionHref) }
            val truncated = selfResponses.any { it.responseStatus == 507 }
            // The server would hand out the same page forever; a full listing gets past it.
            if (truncated && token == previousSyncToken) return@withContext null
            davResponses.firstOrNull { it.responseStatus != null && it.responseStatus !in 200..299 && it.responseStatus != 404 }
                ?.let { throw HttpStatusException(it.responseStatus!!, "Incomplete sync response for ${it.href}") }
            val deleted = davResponses
                .filter { it.responseStatus == 404 }
                .map { stableHref(collectionUrl, it.href, serverUrl, origins) }
                .toSet()
            val changed = davResponses
                .filterNot { stableHref(collectionUrl, it.href, serverUrl, origins) in deleted }
                .filterNot { it.href.endsWith("/") }
                .map { RemoteResource(stableHref(collectionUrl, it.href, serverUrl, origins), it.etag) }
            SyncCollectionResult(changed, deleted, token, truncated)
        }
    }

    suspend fun getResource(
        serverUrl: String,
        href: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): String = getResourceWithEtag(serverUrl, href, username, appPassword, origins).calendarData

    /** GETs [href]; the ETag comes from the same response, so it always belongs to the returned body. */
    suspend fun getResourceWithEtag(
        serverUrl: String,
        href: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): RemoteResourceData =
        withContext(Dispatchers.IO) {
            val request = authenticatedRequest(absoluteUrl(serverUrl, href, origins), username, appPassword)
                .header("Accept", "text/calendar")
                .build()
            send(request, serverUrl, origins).use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code, "GET $href failed: HTTP ${response.code}")
                RemoteResourceData(href, response.header("ETag"), response.bodyText())
            }
        }

    suspend fun getResourceEtag(
        serverUrl: String,
        href: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): String? =
        withContext(Dispatchers.IO) {
            propfind(
                absoluteUrl(serverUrl, href, origins),
                username,
                appPassword,
                depth = "0",
                body = ETAG_PROPERTIES,
                serverUrl = serverUrl,
                origins = origins,
            ).firstOrNull { it.successfulPropertyStatus }?.etag
        }

    suspend fun multigetResources(
        serverUrl: String,
        collectionHref: String,
        hrefs: List<String>,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): List<RemoteResourceData> = withContext(Dispatchers.IO) {
        if (hrefs.isEmpty()) return@withContext emptyList()
        val collectionUrl = absoluteUrl(serverUrl, collectionHref, origins)
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
            appendLine("""<cal:calendar-multiget xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">""")
            appendLine("""  <d:prop><d:getetag /><cal:calendar-data /></d:prop>""")
            hrefs.forEach { appendLine("""  <d:href>${it.xmlEscaped()}</d:href>""") }
            appendLine("""</cal:calendar-multiget>""")
        }
        val request = authenticatedRequest(collectionUrl, username, appPassword)
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        send(request, serverUrl, origins).use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "REPORT calendar-multiget $collectionHref failed: HTTP ${response.code}")
            parseDavResponses(response.bodyText()).mapNotNull { dav ->
                val data = dav.calendarData ?: return@mapNotNull null
                RemoteResourceData(
                    stableHref(collectionUrl, dav.href, serverUrl, origins),
                    dav.etag,
                    data,
                )
            }
        }
    }

    suspend fun queryResources(
        serverUrl: String,
        collectionHref: String,
        componentName: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ): List<RemoteResourceData> = withContext(Dispatchers.IO) {
        val collectionUrl = absoluteUrl(serverUrl, collectionHref, origins)
        val component = componentName.trim().uppercase()
        require(component in setOf("VEVENT", "VTODO")) { "Unsupported CalDAV component query: $componentName" }
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
            appendLine("""<cal:calendar-query xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">""")
            appendLine("""  <d:prop><d:getetag /><cal:calendar-data /></d:prop>""")
            appendLine("""  <cal:filter>""")
            appendLine("""    <cal:comp-filter name="VCALENDAR">""")
            appendLine("""      <cal:comp-filter name="$component" />""")
            appendLine("""    </cal:comp-filter>""")
            appendLine("""  </cal:filter>""")
            appendLine("""</cal:calendar-query>""")
        }
        val request = authenticatedRequest(collectionUrl, username, appPassword)
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        send(request, serverUrl, origins).use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "REPORT calendar-query $collectionHref failed: HTTP ${response.code}")
            parseDavResponses(response.bodyText()).mapNotNull { dav ->
                val data = dav.calendarData ?: return@mapNotNull null
                RemoteResourceData(
                    stableHref(collectionUrl, dav.href, serverUrl, origins),
                    dav.etag,
                    data,
                )
            }
        }
    }

    suspend fun putResource(
        serverUrl: String,
        href: String,
        username: String,
        appPassword: String,
        rawIcs: String,
        baseEtag: String?,
        origins: DavOrigins = DavOrigins.None,
    ): PutResult = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(absoluteUrl(serverUrl, href, origins), username, appPassword)
            .put(rawIcs.toRequestBody(CALENDAR_MEDIA_TYPE))
            .header("Content-Type", CALENDAR_MEDIA_TYPE.toString())
            .apply {
                if (baseEtag != null) header("If-Match", baseEtag) else header("If-None-Match", "*")
            }
            .build()
        send(request, serverUrl, origins).use { response ->
            // Some servers answer a precondition on a target that no longer exists with 404.
            if (response.code == 412 || (response.code == 404 && baseEtag != null)) throw CalDavConflictException("PUT", href)
            if (!response.isSuccessful) throw HttpStatusException(response.code, "PUT $href failed: HTTP ${response.code}")
            PutResult(href, response.header("ETag"))
        }
    }

    suspend fun deleteResource(
        serverUrl: String,
        href: String,
        username: String,
        appPassword: String,
        baseEtag: String?,
        origins: DavOrigins = DavOrigins.None,
    ) = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(absoluteUrl(serverUrl, href, origins), username, appPassword)
            .delete()
            .apply { baseEtag?.let { header("If-Match", it) } }
            .build()
        send(request, serverUrl, origins).use { response ->
            if (response.code == 404) return@use
            if (response.code == 412) throw CalDavConflictException("DELETE", href)
            if (!response.isSuccessful) throw HttpStatusException(response.code, "DELETE $href failed: HTTP ${response.code}")
        }
    }

    suspend fun createCalendar(
        discovery: CalDavAccountDiscovery,
        username: String,
        appPassword: String,
        displayName: String,
        color: Int?,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
        origins: DavOrigins = DavOrigins.None,
    ): String = withContext(Dispatchers.IO) {
        val serverUrl = discovery.enteredServerUrl ?: discovery.serviceUrl
        val slug = displayName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "calendar" }
        var href = discovery.calendarHomeUrl.trimEnd('/') + "/$slug/"
        var suffix = 2
        while (true) {
            val body = buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
                appendLine("""<cal:mkcalendar xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/">""")
                appendLine("""  <d:set><d:prop>""")
                appendLine("""    <d:displayname>${displayName.xmlEscaped()}</d:displayname>""")
                appendLine("""    <cal:supported-calendar-component-set>""")
                if (supportsEvents) appendLine("""      <cal:comp name="VEVENT" />""")
                if (supportsTasks) appendLine("""      <cal:comp name="VTODO" />""")
                appendLine("""    </cal:supported-calendar-component-set>""")
                color?.let { appendLine("""    <a:calendar-color>${it.toColorText()}</a:calendar-color>""") }
                appendLine("""  </d:prop></d:set>""")
                appendLine("""</cal:mkcalendar>""")
            }
            val request = authenticatedRequest(href, username, appPassword)
                .method("MKCALENDAR", body.toRequestBody(XML_MEDIA_TYPE))
                .header("Content-Type", XML_MEDIA_TYPE.toString())
                .build()
            send(request, serverUrl, origins).use { response ->
                if (response.isSuccessful) return@withContext href
                if (response.code == 405) {
                    error("The server does not allow creating a calendar at $href (HTTP 405).")
                }
                if (response.code != 409) {
                    throw HttpStatusException(response.code, "MKCALENDAR failed: HTTP ${response.code}")
                }
            }
            href = discovery.calendarHomeUrl.trimEnd('/') + "/$slug-${suffix++}/"
            if (suffix > 20) error("Could not find an available calendar URL.")
        }
        error("Unreachable")
    }

    suspend fun updateCalendarProperties(
        serverUrl: String,
        collectionHref: String,
        username: String,
        appPassword: String,
        displayName: String,
        color: Int?,
        origins: DavOrigins = DavOrigins.None,
    ) = withContext(Dispatchers.IO) {
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
            appendLine("""<d:propertyupdate xmlns:d="DAV:" xmlns:a="http://apple.com/ns/ical/">""")
            appendLine("""  <d:set><d:prop>""")
            appendLine("""    <d:displayname>${displayName.xmlEscaped()}</d:displayname>""")
            color?.let { appendLine("""    <a:calendar-color>${it.toColorText()}</a:calendar-color>""") }
            appendLine("""  </d:prop></d:set>""")
            appendLine("""</d:propertyupdate>""")
        }
        val request = authenticatedRequest(absoluteUrl(serverUrl, collectionHref, origins), username, appPassword)
            .method("PROPPATCH", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        send(request, serverUrl, origins).use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "PROPPATCH $collectionHref failed: HTTP ${response.code}")
        }
    }

    suspend fun deleteCalendar(
        serverUrl: String,
        collectionHref: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ) = deleteResource(serverUrl, collectionHref, username, appPassword, null, origins)

    /**
     * Looks for Nextcloud's CalDAV trash bin (Nextcloud 22+) at `{calendar home}/trashbin/`. Returns
     * null when the server has none: it answers the PROPFIND with a client error, or the collection
     * exposes neither a trash-bin type nor Nextcloud's retention property on a DAV collection.
     * Network trouble and server errors are thrown.
     */
    suspend fun findTrashBin(
        calendarHomeUrl: String,
        username: String,
        appPassword: String,
        serverUrl: String = calendarHomeUrl,
        origins: DavOrigins = DavOrigins.None,
    ): RemoteTrashBin? = withContext(Dispatchers.IO) {
        val url = ensureTrailingSlash(calendarHomeUrl) + TRASH_BIN_NAME + "/"
        val responses = try {
            propfind(url, username, appPassword, "0", TRASH_BIN_PROPERTIES, serverUrl, origins)
        } catch (error: HttpStatusException) {
            if (error.statusCode in TRASH_BIN_ABSENT_STATUSES) return@withContext null
            throw error
        }
        // Nextcloud 34's actual PROPFIND response only contains DAV:collection: Sabre replaces
        // the custom resource type. The successful Nextcloud retention property still identifies
        // its trash bin. Do not mistake an arbitrary DAV collection for a supported trash bin.
        val trashBin = responses.firstOrNull {
            "trash-bin" in it.resourceTypes ||
                ("collection" in it.resourceTypes && it.trashBinRetentionSeconds != null)
        } ?: return@withContext null
        RemoteTrashBin(url = url, retentionSeconds = trashBin.trashBinRetentionSeconds)
    }

    /**
     * Lists the deleted calendar objects of [trashBin]. Nextcloud's `objects` collection only answers
     * a calendar-query REPORT (a Depth 1 PROPFIND is not implemented) and ignores its filter, so the
     * query matches every component. Objects of deleted calendars are not listed by the server.
     */
    suspend fun listTrashBinObjects(
        trashBin: RemoteTrashBin,
        username: String,
        appPassword: String,
        serverUrl: String = trashBin.url,
        origins: DavOrigins = DavOrigins.None,
    ): List<RemoteTrashedObject> = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(trashBin.objectsUrl, username, appPassword)
            .method("REPORT", TRASH_BIN_OBJECTS_QUERY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        send(request, serverUrl, origins).use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "REPORT calendar-query ${trashBin.objectsUrl} failed: HTTP ${response.code}")
            }
            parseDavResponses(response.bodyText())
                .filterNot { it.href.isSameDavHref(trashBin.objectsUrl) }
                .filterNot { it.href.endsWith("/") }
                .map { dav ->
                    RemoteTrashedObject(
                        href = stableHref(trashBin.objectsUrl, dav.href, serverUrl, origins),
                        etag = dav.etag,
                        calendarData = dav.calendarData,
                        deletedAtMillis = dav.deletedAt?.let(::parseDavTimestampMillis),
                        calendarUri = dav.calendarUri,
                    )
                }
        }
    }

    /**
     * Restores a trash bin object into the calendar it was deleted from: a MOVE onto the trash bin's
     * `restore` collection. Fails with [HttpStatusException], e.g. 404 once the object has expired or
     * 403 when its calendar already holds a resource with the same name.
     */
    suspend fun restoreTrashBinObject(
        serverUrl: String,
        objectHref: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ) = withContext(Dispatchers.IO) {
        val objectUrl = absoluteUrl(serverUrl, objectHref, origins)
        val request = authenticatedRequest(objectUrl, username, appPassword)
            .method("MOVE", null)
            .header("Destination", trashBinRestoreUrl(objectUrl))
            .build()
        send(request, serverUrl, origins).use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "MOVE $objectHref failed: HTTP ${response.code}")
        }
    }

    /** Deletes a trash bin object for good; one that is already gone counts as deleted. */
    suspend fun deleteTrashBinObject(
        serverUrl: String,
        objectHref: String,
        username: String,
        appPassword: String,
        origins: DavOrigins = DavOrigins.None,
    ) = deleteResource(serverUrl, objectHref, username, appPassword, null, origins)

    /**
     * The user's Nextcloud avatar from `{server}/index.php/avatar/{user id}/{size}`. Null when the
     * server has no such endpoint (not Nextcloud) or failed; [NextcloudAvatar.isCustom] is false for
     * the letter avatar Nextcloud generates when the user has not set a picture.
     */
    suspend fun fetchNextcloudAvatar(
        serverUrl: String,
        username: String,
        appPassword: String,
        sizePx: Int = 256,
        origins: DavOrigins = DavOrigins.None,
    ): NextcloudAvatar? = withContext(Dispatchers.IO) {
        val normalized = normalizeServer(serverUrl)
        val userId = resolveNextcloudUserId(normalized, username, appPassword, origins)
        val request = authenticatedRequest(
            "${normalized.trimEnd('/')}/index.php/avatar/${userId.urlPathSegment()}/$sizePx",
            username,
            appPassword,
        )
            .header("Accept", "image/*")
            .header("OCS-APIRequest", "true")
            .build()
        send(request, serverUrl, origins).use { response ->
            val contentType = response.header("Content-Type").orEmpty()
            if (!response.isSuccessful || !contentType.startsWith("image/", ignoreCase = true)) return@withContext null
            val bytes = response.body?.takeIf { it.isNotEmpty() && it.size <= MAX_AVATAR_BYTES } ?: return@withContext null
            NextcloudAvatar(bytes = bytes, isCustom = response.header("X-NC-IsCustomAvatar") != "0")
        }
    }

    private suspend fun discoverFromEndpoint(
        endpoint: String,
        serverUrl: String,
        username: String,
        appPassword: String,
        origins: DavOrigins,
    ): CalDavAccountDiscovery? {
        val endpointResponses = propfind(endpoint, username, appPassword, "0", DISCOVERY_PROPERTIES, serverUrl, origins)
        val endpointResponse = endpointResponses.firstOrNull() ?: return null
        val effectiveUrl = endpointResponse.requestUrl
        val principalUrl = endpointResponse.currentUserPrincipal?.let { resolveUrl(effectiveUrl, it) }
        val directHome = endpointResponse.calendarHomeSet?.let { resolveUrl(effectiveUrl, it) }

        val principalResponse = principalUrl?.let { principal ->
            propfind(principal, username, appPassword, "0", PRINCIPAL_PROPERTIES, serverUrl, origins).firstOrNull()
        }
        val home = principalResponse?.calendarHomeSet?.let {
            resolveUrl(principalUrl ?: effectiveUrl, it)
        } ?: directHome ?: if ("calendar" in endpointResponse.resourceTypes) {
            effectiveUrl
        } else {
            null
        } ?: return null

        // Fails here, with the origin to review, rather than on the first request of every sync.
        requireApprovedDavOrigin(home, serverUrl, origins)
        val options = options(effectiveUrl, username, appPassword, serverUrl, origins)
        return CalDavAccountDiscovery(
            serviceUrl = effectiveUrl,
            enteredServerUrl = serverUrl,
            principalUrl = principalUrl,
            calendarHomeUrl = ensureTrailingSlash(home),
            calendarUserAddresses = principalResponse?.calendarUserAddresses
                .orEmpty()
                .map { resolveMailOrUrl(principalUrl ?: effectiveUrl, it) }
                .toSet(),
            scheduleInboxUrl = principalResponse?.scheduleInboxUrl?.let {
                resolveUrl(principalUrl ?: effectiveUrl, it)
            },
            scheduleOutboxUrl = principalResponse?.scheduleOutboxUrl?.let {
                resolveUrl(principalUrl ?: effectiveUrl, it)
            },
            serverDavCapabilities = options,
            supportedReports = (endpointResponse.supportedReports + principalResponse?.supportedReports.orEmpty()),
        )
    }

    private suspend fun options(url: String, username: String, appPassword: String, serverUrl: String, origins: DavOrigins): Set<String> {
        val request = authenticatedRequest(url, username, appPassword)
            .method("OPTIONS", null)
            .build()
        return runCatching {
            send(request, serverUrl, origins).use { response ->
                if (!response.isSuccessful) emptySet()
                else response.headers("DAV")
                    .flatMap { it.split(',') }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    private suspend fun resolveNextcloudUserId(
        normalizedServer: String,
        username: String,
        appPassword: String,
        origins: DavOrigins,
    ): String {
        val url = "${normalizedServer.trimEnd('/')}/ocs/v1.php/cloud/user"
        val request = authenticatedRequest(url, username, appPassword)
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/xml")
            .build()
        return runCatching {
            send(request, normalizedServer, origins).use { response ->
                if (!response.isSuccessful) return@use username
                val document = parseXml(response.bodyText())
                document.getElementsByTagName("id").firstOrNull()?.textContent?.ifBlank { username } ?: username
            }
        }.getOrDefault(username)
    }

    private suspend fun propfind(
        url: String,
        username: String,
        appPassword: String,
        depth: String,
        body: String,
        serverUrl: String,
        origins: DavOrigins,
    ): List<DavResponse> {
        val request = authenticatedRequest(url, username, appPassword)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        send(request, serverUrl, origins).use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "PROPFIND $url failed: HTTP ${response.code}")
            val effectiveUrl = response.url
            return parseDavResponses(response.bodyText())
                .map { it.copy(requestUrl = effectiveUrl) }
        }
    }

    private fun authenticatedRequest(url: String, username: String, appPassword: String): HttpRequestBuilder =
        HttpRequestBuilder()
            .url(url)
            .followRedirects(false)
            .header("Authorization", basicAuthorization(username, appPassword))

    /**
     * Performs an authenticated request and follows its redirects here, because each hop gets the
     * credentials and the body back: every target has to pass the account's origin check first.
     * Reads keep their method; a write only follows the 307/308 that promise it, and any other
     * redirect comes back as the failed response it is.
     */
    private suspend fun send(request: HttpRequest, serverUrl: String, origins: DavOrigins): HttpResponse {
        var current = request
        repeat(MAX_REDIRECTS + 1) {
            requireApprovedDavOrigin(current.url, serverUrl, origins)
            val response = transport.execute(current)
            val location = response.header("Location")?.takeIf { it.isNotBlank() }
            if (response.code !in REDIRECT_STATUSES || location == null) return response
            val keepsMethod = current.method in REDIRECTABLE_READS || response.code == 307 || response.code == 308
            if (!keepsMethod) return response
            current = HttpRequest(
                method = current.method,
                url = resolveUrl(response.url, location),
                headers = current.headers,
                body = current.body,
                contentType = current.contentType,
                maxResponseBytes = current.maxResponseBytes,
                followRedirects = false,
                totalTimeoutMillis = current.totalTimeoutMillis,
            )
        }
        throw IOException("Too many redirects from ${request.url}")
    }

    /** [DavOrigins.davOrigin] for an account whose calendar home is [homeUrl], before that is stored. */
    private fun DavOrigins.withDavOriginOf(homeUrl: String, serverUrl: String): DavOrigins {
        val home = canonicalOrigin(homeUrl)
        return if (home == null || home == canonicalOrigin(serverUrl)) this else copy(davOrigin = home)
    }

    private fun parseDavResponses(xml: String): List<DavResponse> {
        require(xml.isNotBlank()) { "Empty DAV response; refusing to treat it as an empty calendar." }
        val document = parseXml(xml)
        require(document.documentElement.localName == "multistatus") { "Expected a DAV multistatus response." }
        val responses = document.getElementsByTagNameNS("*", "response")
        return (0 until responses.length).mapNotNull { index ->
            val response = responses.item(index) as? Element ?: return@mapNotNull null
            val href = response.firstText("href") ?: return@mapNotNull null
            DavResponse(
                href = href,
                displayName = response.firstText("displayname"),
                currentUserPrincipal = response.firstHrefInside("current-user-principal"),
                calendarHomeSet = response.firstHrefInside("calendar-home-set"),
                calendarUserAddresses = response.hrefsInside("calendar-user-address-set"),
                scheduleInboxUrl = response.firstHrefInside("schedule-inbox-URL"),
                scheduleOutboxUrl = response.firstHrefInside("schedule-outbox-URL"),
                syncToken = response.firstText("sync-token"),
                ctag = response.firstText("getctag"),
                etag = response.firstText("getetag"),
                calendarData = response.firstText("calendar-data"),
                color = response.firstText("calendar-color")?.let(::parseColor),
                supportedComponents = response.supportedComponentNames(),
                privileges = response.currentUserPrivileges(),
                supportedReports = response.supportedReportNames(),
                resourceTypes = response.resourceTypeNames(),
                maxResourceSize = response.firstText("max-resource-size")?.toLongOrNull(),
                maxAttendeesPerInstance = response.firstText("max-attendees-per-instance")?.toIntOrNull(),
                publishUrl = response.firstHrefInside("publish-url"),
                statusCodes = response.statusCodes(),
                responseStatus = response.directElementChildren().firstOrNull { it.localName == "status" }
                    ?.textContent?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull(),
                deletedAt = response.firstText("deleted-at"),
                calendarUri = response.firstText("calendar-uri"),
                trashBinRetentionSeconds = response.firstText("trash-bin-retention-duration")?.toLongOrNull(),
            )
        }
    }

    private fun Element.currentUserPrivileges(): Set<String> {
        val result = mutableSetOf<String>()
        val sets = getElementsByTagNameNS("*", "current-user-privilege-set")
        for (setIndex in 0 until sets.length) {
            val set = sets.item(setIndex) as? Element ?: continue
            val privileges = set.getElementsByTagNameNS("*", "privilege")
            for (index in 0 until privileges.length) {
                val privilege = privileges.item(index) as? Element ?: continue
                privilege.directElementChildren().forEach { child ->
                    child.localName?.takeIf { it.isNotBlank() }?.let(result::add)
                }
            }
        }
        return result
    }

    private fun Element.supportedReportNames(): Set<String> {
        val result = mutableSetOf<String>()
        val sets = getElementsByTagNameNS("*", "supported-report-set")
        for (setIndex in 0 until sets.length) {
            val set = sets.item(setIndex) as? Element ?: continue
            val reports = set.getElementsByTagNameNS("*", "report")
            for (index in 0 until reports.length) {
                val report = reports.item(index) as? Element ?: continue
                report.directElementChildren().forEach { child ->
                    child.localName?.takeIf { it.isNotBlank() }?.let(result::add)
                }
            }
        }
        return result
    }

    private fun Element.resourceTypeNames(): Set<String> {
        val result = mutableSetOf<String>()
        val sets = getElementsByTagNameNS("*", "resourcetype")
        for (index in 0 until sets.length) {
            val set = sets.item(index) as? Element ?: continue
            set.directElementChildren().forEach { child ->
                child.localName?.takeIf { it.isNotBlank() }?.let(result::add)
            }
        }
        return result
    }

    private fun Element.supportedComponentNames(): Set<String> {
        val names = mutableSetOf<String>()
        val nodes = getElementsByTagNameNS("*", "comp")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            element.attribute("name").orEmpty().takeIf { it.isNotBlank() }?.let { names += it.uppercase() }
        }
        return names
    }

    private fun Element.statusCodes(): Set<Int> {
        val nodes = getElementsByTagNameNS("*", "status")
        return (0 until nodes.length).mapNotNull { index ->
            Regex("""\s(\d{3})(?:\s|$)""").find(nodes.item(index)?.textContent.orEmpty())
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.toSet()
    }

    private fun Element.firstText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun Element.firstHrefInside(localName: String): String? =
        hrefsInside(localName).firstOrNull()

    private fun Element.hrefsInside(localName: String): List<String> {
        val containers = getElementsByTagNameNS("*", localName)
        if (containers.length == 0) return emptyList()
        val result = mutableListOf<String>()
        for (containerIndex in 0 until containers.length) {
            val container = containers.item(containerIndex) as? Element ?: continue
            val hrefs = container.getElementsByTagNameNS("*", "href")
            for (index in 0 until hrefs.length) {
                hrefs.item(index)?.textContent?.trim()?.takeIf { it.isNotBlank() }?.let(result::add)
            }
        }
        return result
    }

    private fun Element.directElementChildren(): List<Element> = childElements

    private fun parseDocumentFirstText(xml: String, localName: String): String? {
        if (xml.isBlank()) return null
        val document = parseXml(xml)
        val nodes = document.getElementsByTagNameNS("*", localName)
        return if (nodes.length == 0) null else nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseXml(xml: String) = parseXmlDocument(xml)

    private fun parseColor(value: String): Int? =
        runCatching { parseAndroidColor(value.trim().substringBefore(';')) }.getOrNull()

    private fun absoluteUrl(serverUrl: String, href: String, origins: DavOrigins): String =
        if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
            href
        } else {
            resolveUrl(origins.davOrigin ?: normalizeServer(serverUrl), href)
        }

    /**
     * The form an href is kept in. One on the account's DAV origin stays as the server wrote it, so
     * stored keys don't change; one elsewhere keeps its origin, or [absoluteUrl] would later send it
     * to the wrong host. It still has to pass the origin check before anything is sent there.
     */
    private fun stableHref(baseUrl: String, href: String, serverUrl: String, origins: DavOrigins): String {
        val isRooted = href.startsWith("/") && !href.startsWith("//")
        val isAbsolute = href.startsWith("http://", true) || href.startsWith("https://", true)
        if (!isRooted && !isAbsolute) return resolveUrl(ensureTrailingSlash(baseUrl), href)
        val resolved = resolveUrl(ensureTrailingSlash(baseUrl), href)
        val rootOrigin = canonicalOrigin(origins.davOrigin ?: normalizeServer(serverUrl))
        return if (canonicalOrigin(resolved) == rootOrigin) href else resolved
    }

    private fun resolveUrl(baseUrl: String, href: String): String =
        resolveUri(baseUrl, href)

    private fun resolveMailOrUrl(baseUrl: String, value: String): String =
        if (value.startsWith("mailto:", true)) value else resolveUrl(baseUrl, value)

    private fun originOf(url: String): String {
        val uri = parseUri(normalizeServer(url))
        return "${uri.scheme}://${uri.authority}"
    }

    private fun ensureTrailingSlash(value: String): String =
        if (value.endsWith('/')) value else "$value/"

    private data class DavResponse(
        val href: String,
        val requestUrl: String = "",
        val displayName: String? = null,
        val currentUserPrincipal: String? = null,
        val calendarHomeSet: String? = null,
        val calendarUserAddresses: List<String> = emptyList(),
        val scheduleInboxUrl: String? = null,
        val scheduleOutboxUrl: String? = null,
        val syncToken: String? = null,
        val ctag: String? = null,
        val etag: String? = null,
        val calendarData: String? = null,
        val color: Int? = null,
        val supportedComponents: Set<String> = emptySet(),
        val privileges: Set<String> = emptySet(),
        val supportedReports: Set<String> = emptySet(),
        val resourceTypes: Set<String> = emptySet(),
        val maxResourceSize: Long? = null,
        val maxAttendeesPerInstance: Int? = null,
        val publishUrl: String? = null,
        val statusCodes: Set<Int> = emptySet(),
        val responseStatus: Int? = null,
        val deletedAt: String? = null,
        val calendarUri: String? = null,
        val trashBinRetentionSeconds: Long? = null,
    ) {
        val successfulPropertyStatus: Boolean
            get() = statusCodes.isEmpty() || statusCodes.any { it in 200..299 }

        fun isSchedulingMailbox(discovery: CalDavAccountDiscovery): Boolean =
            "schedule-inbox" in resourceTypes || "schedule-outbox" in resourceTypes ||
                listOfNotNull(discovery.scheduleInboxUrl, discovery.scheduleOutboxUrl).any { href.isSameDavHref(it) }
    }

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val CALENDAR_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()

        private val DISCOVERY_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <d:current-user-principal />
                <cal:calendar-home-set />
                <d:resourcetype />
                <d:supported-report-set />
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private val PRINCIPAL_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <cal:calendar-home-set />
                <cal:calendar-user-address-set />
                <cal:schedule-inbox-URL />
                <cal:schedule-outbox-URL />
                <d:supported-report-set />
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private val HOME_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype /></d:prop></d:propfind>
        """.trimIndent()

        private val COLLECTION_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/">
              <d:prop>
                <d:displayname />
                <d:resourcetype />
                <d:current-user-privilege-set />
                <d:supported-report-set />
                <d:sync-token />
                <cs:getctag />
                <cal:supported-calendar-component-set />
                <cal:max-resource-size />
                <cal:max-attendees-per-instance />
                <cs:publish-url />
                <a:calendar-color />
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private const val TRASH_BIN_NAME = "trashbin"
        private const val DELETED_CALENDAR_TYPE = "deleted-calendar"
        private const val MAX_AVATAR_BYTES = 2 * 1024 * 1024
        private const val MAX_REDIRECTS = 10
        private val REDIRECT_STATUSES = setOf(300, 301, 302, 303, 307, 308)
        private val REDIRECTABLE_READS = setOf("GET", "HEAD", "OPTIONS", "PROPFIND", "REPORT")

        /** Answers of servers without a trash bin at the probed URL. */
        private val TRASH_BIN_ABSENT_STATUSES = setOf(400, 403, 404, 405, 409, 501)

        private val TRASH_BIN_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:nc="http://nextcloud.com/ns">
              <d:prop>
                <d:resourcetype />
                <nc:trash-bin-retention-duration />
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private val TRASH_BIN_OBJECTS_QUERY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <cal:calendar-query xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:nc="http://nextcloud.com/ns">
              <d:prop>
                <d:getetag />
                <cal:calendar-data />
                <nc:deleted-at />
                <nc:calendar-uri />
              </d:prop>
              <cal:filter>
                <cal:comp-filter name="VCALENDAR" />
              </cal:filter>
            </cal:calendar-query>
        """.trimIndent()

        private val ETAG_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:getetag /></d:prop></d:propfind>
        """.trimIndent()
    }
}

private typealias Element = XmlElement

private fun String.xmlEscaped(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private fun String.urlPathSegment(): String =
    encodePathSegment(this)

private fun String.isSameDavHref(other: String): Boolean =
    davPathKey() == other.davPathKey()

private fun String.davPathKey(): String =
    runCatching {
        val uri = parseUri(this)
        uri.path.trimEnd('/')
    }.getOrDefault(trimEnd('/'))

/** `…/trashbin/objects/<name>` restores by moving to `…/trashbin/restore/<name>`. */
internal fun trashBinRestoreUrl(objectUrl: String): String {
    val objectName = objectUrl.trimEnd('/').substringAfterLast('/')
    val trashBinUrl = objectUrl.trimEnd('/').substringBeforeLast('/').substringBeforeLast('/')
    return "$trashBinUrl/restore/$objectName"
}

/** Nextcloud writes `{http://nextcloud.com/ns}deleted-at` as ISO 8601 with an offset (PHP's ATOM format). */
internal fun parseDavTimestampMillis(value: String): Long? {
    val text = value.trim()
    return runCatching { kotlin.time.Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { kotlin.time.Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: parseRfc1123Millis(text)
        ?: text.toLongOrNull()?.let { if (it < 100_000_000_000L) it * 1000 else it }
}

/**
 * The link to hand out for a published calendar. Nextcloud reports its DAV address, while people get
 * the calendar app's public page, which its own "Copy public link" builds from the same token.
 */
private fun publicCalendarUrl(publishUrl: String): String {
    val marker = "/remote.php/dav/public-calendars/"
    val cut = publishUrl.indexOf(marker)
    if (cut < 0) return publishUrl
    val token = publishUrl.substring(cut + marker.length).substringBefore('?').trimEnd('/')
    return if (token.isEmpty()) publishUrl else publishUrl.substring(0, cut) + "/apps/calendar/p/" + token
}

private fun String?.isLikelyReadOnlyCalendarName(): Boolean? {
    val value = this?.lowercase() ?: return null
    return if (
        value.contains("geburtstag") ||
        value.contains("birthday") ||
        value.contains("contact_birthdays") ||
        value.contains("contact-birthdays")
    ) true else null
}

private fun Int.toColorText(): String =
    "#" + (this and 0x00FFFFFF).toString(16).uppercase().padStart(6, '0')
