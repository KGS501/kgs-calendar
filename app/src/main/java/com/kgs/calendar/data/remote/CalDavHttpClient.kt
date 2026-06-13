package com.kgs.calendar.data.remote

import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Small CalDAV transport focused on interoperable RFC 4791/5397/6578 behavior.
 *
 * Basic authentication is intentionally the generic credential mechanism here. Providers
 * which require OAuth still need a provider-specific authorization flow, but the discovered
 * DAV URLs and all subsequent requests remain standards based.
 */
class CalDavHttpClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun discoverAccount(
        serverUrl: String,
        username: String,
        appPassword: String,
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
                discoverFromEndpoint(candidate, username, appPassword)?.let { return@withContext it }
            } catch (error: Throwable) {
                lastError = error
            }
        }

        // Compatibility fallback for older Nextcloud installations which don't expose
        // complete principal discovery at the URL entered by the user.
        val userId = resolveNextcloudUserId(normalized, username, appPassword)
        val nextcloudBase = normalized.trimEnd('/')
        val home = "$nextcloudBase/remote.php/dav/calendars/${userId.urlPathSegment()}/"
        runCatching {
            propfind(home, username, appPassword, "0", HOME_PROPERTIES)
        }.getOrElse { throw lastError ?: it }
        CalDavAccountDiscovery(
            serviceUrl = normalized,
            principalUrl = "$nextcloudBase/remote.php/dav/principals/users/${userId.urlPathSegment()}/",
            calendarHomeUrl = home,
        )
    }

    suspend fun discoverCollections(
        discovery: CalDavAccountDiscovery,
        username: String,
        appPassword: String,
    ): List<RemoteCollection> = withContext(Dispatchers.IO) {
        val responses = propfind(
            discovery.calendarHomeUrl,
            username,
            appPassword,
            depth = "1",
            body = COLLECTION_PROPERTIES,
        )
        responses
            .filterNot { it.href.isSameDavHref(discovery.calendarHomeUrl) }
            .mapNotNull { response ->
                val isCalendar = "calendar" in response.resourceTypes
                val isScheduleInbox = "schedule-inbox" in response.resourceTypes ||
                    discovery.scheduleInboxUrl?.let { response.href.isSameDavHref(it) } == true
                val supported = response.supportedComponents
                if (!isCalendar && !isScheduleInbox && supported.isEmpty()) return@mapNotNull null
                val components = when {
                    supported.isNotEmpty() -> supported
                    isScheduleInbox -> setOf("VTODO")
                    else -> setOf("VEVENT")
                }
                val capabilities = CalDavCollectionCapabilities(
                    privileges = response.privileges,
                    supportedReports = response.supportedReports,
                    resourceTypes = response.resourceTypes,
                    maxResourceSize = response.maxResourceSize,
                    maxAttendeesPerInstance = response.maxAttendeesPerInstance,
                )
                RemoteCollection(
                    href = stableHref(discovery.calendarHomeUrl, response.href),
                    displayName = response.displayName ?: response.href.trim('/').substringAfterLast('/'),
                    color = response.color,
                    supportsEvents = !isScheduleInbox && "VEVENT" in components,
                    supportsTasks = isScheduleInbox || "VTODO" in components,
                    syncToken = response.syncToken,
                    ctag = response.ctag,
                    readOnly = if (isScheduleInbox) {
                        true
                    } else if (capabilities.privilegesKnown) {
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
            resolveNextcloudUserId(normalizeServer(serverUrl), username, appPassword)
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
    ): List<RemoteResource> = withContext(Dispatchers.IO) {
        propfind(
            absoluteUrl(serverUrl, collectionHref),
            username,
            appPassword,
            depth = "1",
            body = ETAG_PROPERTIES,
        )
            .filterNot { it.href.isSameDavHref(collectionHref) }
            .filterNot { it.href.endsWith("/") }
            .filter { it.successfulPropertyStatus }
            .map { RemoteResource(stableHref(absoluteUrl(serverUrl, collectionHref), it.href), it.etag) }
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
    ): SyncCollectionResult? = withContext(Dispatchers.IO) {
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
            appendLine("""<d:sync-collection xmlns:d="DAV:">""")
            appendLine("""  <d:sync-token>${previousSyncToken.orEmpty().xmlEscaped()}</d:sync-token>""")
            appendLine("""  <d:sync-level>1</d:sync-level>""")
            appendLine("""  <d:prop><d:getetag /></d:prop>""")
            appendLine("""</d:sync-collection>""")
        }
        val request = authenticatedRequest(
            absoluteUrl(serverUrl, collectionHref),
            username,
            appPassword,
        )
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code in setOf(400, 403, 405, 409, 501)) return@withContext null
            if (!response.isSuccessful) {
                error("REPORT sync-collection $collectionHref failed: HTTP ${response.code}")
            }
            val xml = response.body?.string().orEmpty()
            val token = parseDocumentFirstText(xml, "sync-token") ?: return@withContext null
            val davResponses = parseDavResponses(xml)
            val deleted = davResponses
                .filter { it.statusCodes.any { code -> code == 404 } }
                .map { stableHref(absoluteUrl(serverUrl, collectionHref), it.href) }
                .toSet()
            val changed = davResponses
                .filterNot { it.href in deleted }
                .filterNot { it.href.isSameDavHref(collectionHref) }
                .filterNot { it.href.endsWith("/") }
                .map {
                    RemoteResource(
                        stableHref(absoluteUrl(serverUrl, collectionHref), it.href),
                        it.etag,
                    )
                }
            SyncCollectionResult(changed, deleted, token)
        }
    }

    suspend fun getResource(serverUrl: String, href: String, username: String, appPassword: String): String =
        withContext(Dispatchers.IO) {
            val request = authenticatedRequest(absoluteUrl(serverUrl, href), username, appPassword)
                .header("Accept", "text/calendar")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GET $href failed: HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }

    suspend fun getResourceEtag(serverUrl: String, href: String, username: String, appPassword: String): String? =
        withContext(Dispatchers.IO) {
            propfind(
                absoluteUrl(serverUrl, href),
                username,
                appPassword,
                depth = "0",
                body = ETAG_PROPERTIES,
            ).firstOrNull { it.successfulPropertyStatus }?.etag
        }

    suspend fun multigetResources(
        serverUrl: String,
        collectionHref: String,
        hrefs: List<String>,
        username: String,
        appPassword: String,
    ): List<RemoteResourceData> = withContext(Dispatchers.IO) {
        if (hrefs.isEmpty()) return@withContext emptyList()
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8" ?>""")
            appendLine("""<cal:calendar-multiget xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">""")
            appendLine("""  <d:prop><d:getetag /><cal:calendar-data /></d:prop>""")
            hrefs.forEach { appendLine("""  <d:href>${it.xmlEscaped()}</d:href>""") }
            appendLine("""</cal:calendar-multiget>""")
        }
        val request = authenticatedRequest(
            absoluteUrl(serverUrl, collectionHref),
            username,
            appPassword,
        )
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("REPORT calendar-multiget $collectionHref failed: HTTP ${response.code}")
            parseDavResponses(response.body?.string().orEmpty()).mapNotNull { dav ->
                val data = dav.calendarData ?: return@mapNotNull null
                RemoteResourceData(
                    stableHref(absoluteUrl(serverUrl, collectionHref), dav.href),
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
    ): List<RemoteResourceData> = withContext(Dispatchers.IO) {
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
        val request = authenticatedRequest(
            absoluteUrl(serverUrl, collectionHref),
            username,
            appPassword,
        )
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("REPORT calendar-query $collectionHref failed: HTTP ${response.code}")
            parseDavResponses(response.body?.string().orEmpty()).mapNotNull { dav ->
                val data = dav.calendarData ?: return@mapNotNull null
                RemoteResourceData(
                    stableHref(absoluteUrl(serverUrl, collectionHref), dav.href),
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
    ): PutResult = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(absoluteUrl(serverUrl, href), username, appPassword)
            .put(rawIcs.toRequestBody(CALENDAR_MEDIA_TYPE))
            .header("Content-Type", CALENDAR_MEDIA_TYPE.toString())
            .apply {
                if (baseEtag != null) header("If-Match", baseEtag) else header("If-None-Match", "*")
            }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 412) error("Conflict while uploading $href")
            if (!response.isSuccessful) error("PUT $href failed: HTTP ${response.code}")
            PutResult(href, response.header("ETag"))
        }
    }

    suspend fun deleteResource(
        serverUrl: String,
        href: String,
        username: String,
        appPassword: String,
        baseEtag: String?,
    ) = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(absoluteUrl(serverUrl, href), username, appPassword)
            .delete()
            .apply { baseEtag?.let { header("If-Match", it) } }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@use
            if (response.code == 412) error("Conflict while deleting $href")
            if (!response.isSuccessful) error("DELETE $href failed: HTTP ${response.code}")
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
    ): String = withContext(Dispatchers.IO) {
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
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) return@withContext href
                if (response.code == 405) {
                    error("The server does not allow creating a calendar at $href (HTTP 405).")
                }
                if (response.code != 409) {
                    error("MKCALENDAR failed: HTTP ${response.code}")
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
        val request = authenticatedRequest(
            absoluteUrl(serverUrl, collectionHref),
            username,
            appPassword,
        )
            .method("PROPPATCH", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("PROPPATCH $collectionHref failed: HTTP ${response.code}")
        }
    }

    suspend fun deleteCalendar(
        serverUrl: String,
        collectionHref: String,
        username: String,
        appPassword: String,
    ) = deleteResource(serverUrl, collectionHref, username, appPassword, null)

    private fun discoverFromEndpoint(
        endpoint: String,
        username: String,
        appPassword: String,
    ): CalDavAccountDiscovery? {
        val endpointResponses = propfind(endpoint, username, appPassword, "0", DISCOVERY_PROPERTIES)
        val endpointResponse = endpointResponses.firstOrNull() ?: return null
        val effectiveUrl = endpointResponse.requestUrl
        val principalUrl = endpointResponse.currentUserPrincipal?.let { resolveUrl(effectiveUrl, it) }
        val directHome = endpointResponse.calendarHomeSet?.let { resolveUrl(effectiveUrl, it) }

        val principalResponse = principalUrl?.let { principal ->
            propfind(principal, username, appPassword, "0", PRINCIPAL_PROPERTIES).firstOrNull()
        }
        val home = principalResponse?.calendarHomeSet?.let {
            resolveUrl(principalUrl ?: effectiveUrl, it)
        } ?: directHome ?: if ("calendar" in endpointResponse.resourceTypes) {
            effectiveUrl
        } else {
            null
        } ?: return null

        val options = options(effectiveUrl, username, appPassword)
        return CalDavAccountDiscovery(
            serviceUrl = effectiveUrl,
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

    private fun options(url: String, username: String, appPassword: String): Set<String> {
        val request = authenticatedRequest(url, username, appPassword)
            .method("OPTIONS", null)
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) emptySet()
                else response.headers("DAV")
                    .flatMap { it.split(',') }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    private fun resolveNextcloudUserId(
        normalizedServer: String,
        username: String,
        appPassword: String,
    ): String {
        val url = "${normalizedServer.trimEnd('/')}/ocs/v1.php/cloud/user"
        val request = authenticatedRequest(url, username, appPassword)
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/xml")
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use username
                val document = parseXml(response.body?.string().orEmpty())
                document.getElementsByTagName("id").item(0)?.textContent?.ifBlank { username } ?: username
            }
        }.getOrDefault(username)
    }

    private fun propfind(
        url: String,
        username: String,
        appPassword: String,
        depth: String,
        body: String,
    ): List<DavResponse> {
        val request = authenticatedRequest(url, username, appPassword)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .header("Content-Type", XML_MEDIA_TYPE.toString())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("PROPFIND $url failed: HTTP ${response.code}")
            val effectiveUrl = response.request.url.toString()
            return parseDavResponses(response.body?.string().orEmpty())
                .map { it.copy(requestUrl = effectiveUrl) }
        }
    }

    private fun authenticatedRequest(url: String, username: String, appPassword: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, appPassword))

    private fun parseDavResponses(xml: String): List<DavResponse> {
        if (xml.isBlank()) return emptyList()
        val document = parseXml(xml)
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
                statusCodes = response.statusCodes(),
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
            element.getAttribute("name").takeIf { it.isNotBlank() }?.let { names += it.uppercase() }
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

    private fun Element.directElementChildren(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) (child as? Element)?.let(result::add)
        }
        return result
    }

    private fun parseDocumentFirstText(xml: String, localName: String): String? {
        if (xml.isBlank()) return null
        val document = parseXml(xml)
        val nodes = document.getElementsByTagNameNS("*", localName)
        return if (nodes.length == 0) null else nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseXml(xml: String) = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            secureFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            secureFeature("http://xml.org/sax/features/external-general-entities", false)
            secureFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray()))

    private fun DocumentBuilderFactory.secureFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun parseColor(value: String): Int? =
        runCatching { Color.parseColor(value.trim().substringBefore(';')) }.getOrNull()

    private fun absoluteUrl(serverUrl: String, href: String): String =
        if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
            href
        } else {
            resolveUrl(normalizeServer(serverUrl), href)
        }

    private fun stableHref(baseUrl: String, href: String): String =
        if (
            href.startsWith("http://", true) ||
            href.startsWith("https://", true) ||
            href.startsWith("/")
        ) {
            href
        } else {
            resolveUrl(ensureTrailingSlash(baseUrl), href)
        }

    private fun resolveUrl(baseUrl: String, href: String): String =
        URI(baseUrl).resolve(href).toString()

    private fun resolveMailOrUrl(baseUrl: String, value: String): String =
        if (value.startsWith("mailto:", true)) value else resolveUrl(baseUrl, value)

    private fun originOf(url: String): String {
        val uri = URI(normalizeServer(url))
        return "${uri.scheme}://${uri.authority}"
    }

    private fun normalizeServer(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
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
        val statusCodes: Set<Int> = emptySet(),
    ) {
        val successfulPropertyStatus: Boolean
            get() = statusCodes.isEmpty() || statusCodes.any { it in 200..299 }
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
                <a:calendar-color />
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private val ETAG_PROPERTIES = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:getetag /></d:prop></d:propfind>
        """.trimIndent()
    }
}

private fun String.xmlEscaped(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private fun String.urlPathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String.isSameDavHref(other: String): Boolean =
    davPathKey() == other.davPathKey()

private fun String.davPathKey(): String =
    runCatching {
        val uri = URI(this)
        (uri.path ?: uri.rawPath ?: this).trimEnd('/')
    }.getOrDefault(trimEnd('/'))

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
    "#%06X".format(this and 0x00FFFFFF)
