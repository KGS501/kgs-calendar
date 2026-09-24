package com.kgs.calendar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ServerSocketFactory

val TEST_ZONE: ZoneId = ZoneId.of("Europe/Berlin")

/** Local-calendar href created by [CalendarRepository.ensureLocalCalendar]. */
const val LOCAL_COLLECTION = "local://kgs-calendar/default"

class InMemoryCredentialsStore : CredentialsStore {
    private val entries = ConcurrentHashMap<String, StoredCredentials>()

    override fun save(credentials: StoredCredentials) {
        entries[CredentialsStore.PRIMARY_ID] = credentials
    }

    override fun save(accountId: String, credentials: StoredCredentials) {
        entries[accountId] = credentials
    }

    override fun get(accountId: String): StoredCredentials? = entries[accountId]

    override fun clear() = entries.clear()

    override fun clear(accountId: String) {
        entries.remove(accountId)
    }
}

/**
 * Builds the [CalendarDataComponents] and the [CalendarRepository] facade over an in-memory Room
 * database, an in-memory credential store and (lazily) a [FakeCalDavServer]. The inspection helpers
 * read the database directly so the tests only depend on the persisted state, not on how the
 * repository is split internally.
 */
class RepositoryHarness(val zone: ZoneId = TEST_ZONE) : Closeable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val database: KgsDatabase = Room.inMemoryDatabaseBuilder(context, KgsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val credentials = InMemoryCredentialsStore()
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    val components = CalendarDataComponents(
        database = database,
        credentialsStore = credentials,
        loginFlowClient = NextcloudLoginFlowClient(httpClient),
        calDavClient = CalDavHttpClient(httpClient),
        androidCalendarProviderClient = AndroidCalendarProviderClient(context, zone),
        icalCodec = IcalCodec(zone),
        readOnlyHttpClient = httpClient,
        zoneId = zone,
    )
    val repository = CalendarRepository(
        queries = components.queries,
        eventMutations = components.eventMutations,
        taskMutations = components.taskMutations,
        sources = components.sources,
        syncOrchestrator = components.syncOrchestrator,
        repairs = components.repairs,
    )

    private var startedServer: FakeCalDavServer? = null
    val server: FakeCalDavServer
        get() = startedServer ?: FakeCalDavServer().also {
            it.start()
            startedServer = it
        }

    /** Adds the fake server's account and performs the first full sync. */
    suspend fun addSyncedCalDavAccount() {
        repository.saveManualAccount(server.serverUrl, server.username, server.password)
        repository.syncNow()
    }

    suspend fun account(id: String) = database.accountDao().get(id)
    suspend fun collection(href: String) = database.collectionDao().get(href)
    suspend fun event(uidOrHref: String): EventEntity? = database.eventDao().get(uidOrHref)
    suspend fun task(uidOrHref: String): TaskEntity? = database.taskDao().get(uidOrHref)
    suspend fun resource(href: String): CalendarResourceEntity? = database.resourceDao().get(href)
    suspend fun pendingMutations(): List<PendingMutationEntity> = database.pendingMutationDao().all()

    suspend fun eventsIn(collectionHref: String): List<EventEntity> =
        database.resourceDao().forCollection(collectionHref)
            .filter { it.componentType == ComponentType.Event }
            .mapNotNull { database.eventDao().byResource(it.href) }

    suspend fun tasksIn(collectionHref: String): List<TaskEntity> =
        database.resourceDao().forCollection(collectionHref)
            .filter { it.componentType == ComponentType.Task }
            .mapNotNull { database.taskDao().byResource(it.href) }

    fun millis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    override fun close() {
        database.close()
        startedServer?.close()
    }
}

fun eventPayload(
    title: String,
    date: LocalDate,
    start: LocalTime? = LocalTime.of(10, 0),
    end: LocalTime? = LocalTime.of(11, 0),
    collectionHref: String? = null,
    allDay: Boolean = false,
    recurrenceRule: String? = null,
    description: String? = null,
) = EventEditPayload(
    title = title,
    collectionHref = collectionHref,
    date = date,
    endDate = date,
    startTime = start,
    endTime = end,
    allDay = allDay,
    description = description,
    location = null,
    locationMapVerified = null,
    manualColor = null,
    recurrenceRule = recurrenceRule,
)

fun taskPayload(
    title: String,
    collectionHref: String? = null,
    dueDate: LocalDate? = null,
    dueTime: LocalTime? = null,
    parentUid: String? = null,
    isCompleted: Boolean = false,
) = TaskEditPayload(
    title = title,
    collectionHref = collectionHref,
    notes = null,
    location = null,
    locationMapVerified = null,
    manualColor = null,
    url = null,
    categories = null,
    startDate = null,
    startTime = null,
    startHasTime = false,
    dueDate = dueDate,
    dueTime = dueTime,
    dueHasTime = dueTime != null,
    priority = null,
    percentComplete = null,
    isCompleted = isCompleted,
    recurrenceRule = null,
    parentUid = parentUid,
)

inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw error
    }
    throw AssertionError("Expected ${T::class.java.simpleName} to be thrown")
}

/** Undoes RFC 5545 line folding so assertions don't depend on where long lines wrap. */
fun String.unfoldedIcs(): String = replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")

object SampleIcs {
    /** [start]/[end] are UTC DATE-TIME values such as `20261005T080000Z`. */
    fun event(
        uid: String,
        summary: String,
        start: String = "20261005T080000Z",
        end: String = "20261005T090000Z",
        rrule: String? = null,
        sequence: Int = 0,
    ): String = calendar(
        "BEGIN:VEVENT",
        "UID:$uid",
        "DTSTAMP:20260901T000000Z",
        "DTSTART:$start",
        "DTEND:$end",
        "SUMMARY:$summary",
        rrule?.let { "RRULE:$it" },
        "SEQUENCE:$sequence",
        "END:VEVENT",
    )

    fun task(
        uid: String,
        summary: String,
        due: String? = "20261006T150000Z",
        status: String = "NEEDS-ACTION",
        parentUid: String? = null,
    ): String = calendar(
        "BEGIN:VTODO",
        "UID:$uid",
        "DTSTAMP:20260901T000000Z",
        "SUMMARY:$summary",
        due?.let { "DUE:$it" },
        "STATUS:$status",
        parentUid?.let { "RELATED-TO;RELTYPE=PARENT:$it" },
        "END:VTODO",
    )

    fun calendar(vararg lines: String?): String =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//KGS Calendar Tests//EN") +
            lines.filterNotNull() +
            "END:VCALENDAR")
            .joinToString("\r\n", postfix = "\r\n")
}

/** Canned multistatus bodies in the shapes [CalDavHttpClient] parses. */
object CalDavXml {
    private const val NAMESPACES =
        """xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:a="http://apple.com/ns/ical/""""
    private const val OK = "<d:status>HTTP/1.1 200 OK</d:status>"

    fun multistatus(vararg responses: String, syncToken: String? = null): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>""")
        append("<d:multistatus $NAMESPACES>")
        responses.forEach(::append)
        syncToken?.let { append("<d:sync-token>$it</d:sync-token>") }
        append("</d:multistatus>")
    }

    fun okResponse(href: String, props: String): String =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop>$OK</d:propstat></d:response>"

    fun notFoundResponse(href: String): String =
        "<d:response><d:href>$href</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>"

    fun serviceRoot(href: String, principal: String) = multistatus(
        okResponse(
            href,
            "<d:current-user-principal><d:href>$principal</d:href></d:current-user-principal>" +
                "<d:resourcetype><d:collection /></d:resourcetype>",
        ),
    )

    fun principal(href: String, home: String, email: String) = multistatus(
        okResponse(
            href,
            "<cal:calendar-home-set><d:href>$home</d:href></cal:calendar-home-set>" +
                "<cal:calendar-user-address-set><d:href>mailto:$email</d:href></cal:calendar-user-address-set>",
        ),
    )

    fun calendarCollection(
        href: String,
        displayName: String,
        components: Set<String>,
        readOnly: Boolean,
        supportsSyncCollection: Boolean,
        syncToken: String,
        ctag: String,
    ): String {
        val privileges = if (readOnly) {
            listOf("read")
        } else {
            listOf("read", "write-content", "write-properties", "bind", "unbind")
        }
        val reports = listOfNotNull("sync-collection".takeIf { supportsSyncCollection }, "calendar-multiget")
        return okResponse(
            href,
            "<d:displayname>$displayName</d:displayname>" +
                "<d:resourcetype><d:collection /><cal:calendar /></d:resourcetype>" +
                "<d:current-user-privilege-set>" +
                privileges.joinToString("") { "<d:privilege><d:$it /></d:privilege>" } +
                "</d:current-user-privilege-set>" +
                "<d:supported-report-set>" +
                reports.joinToString("") { name ->
                    val prefix = if (name == "sync-collection") "d" else "cal"
                    "<d:supported-report><d:report><$prefix:$name /></d:report></d:supported-report>"
                } +
                "</d:supported-report-set>" +
                "<cal:supported-calendar-component-set>" +
                components.joinToString("") { """<cal:comp name="$it" />""" } +
                "</cal:supported-calendar-component-set>" +
                (if (supportsSyncCollection) "<d:sync-token>$syncToken</d:sync-token>" else "") +
                "<cs:getctag>$ctag</cs:getctag>",
        )
    }

    fun etag(href: String, etag: String) = okResponse(href, "<d:getetag>$etag</d:getetag>")

    fun calendarData(href: String, etag: String, ics: String) = okResponse(
        href,
        "<d:getetag>$etag</d:getetag><cal:calendar-data><![CDATA[$ics]]></cal:calendar-data>",
    )
}

/**
 * Minimal stateful CalDAV server on top of [MockWebServer]. Requests are routed by method and path
 * (never by order), so the fixture keeps working if the repository reorders or retries requests.
 * Supports discovery, PROPFIND listings, calendar-multiget/-query, sync-collection with numeric
 * tokens, conditional PUT/DELETE (412 on ETag mismatch) and plain read-only ICS feeds.
 */
class FakeCalDavServer(
    val username: String = "alice",
    val password: String = "secret",
) : Closeable {
    data class CapturedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    ) {
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    data class StoredResource(val href: String, val etag: String, val ics: String)

    private class Collection(
        val href: String,
        val displayName: String,
        val components: Set<String>,
        val readOnly: Boolean,
    ) {
        val resources = LinkedHashMap<String, StoredResource>()
        val changeLog = mutableListOf<Pair<Int, String>>()
        var version = 1
    }

    private class Override(
        val method: String,
        val pathContains: String,
        val bodyContains: String?,
        val response: () -> MockResponse,
        var remaining: Int,
    )

    private val server = MockWebServer()
    private val collections = LinkedHashMap<String, Collection>()
    private val feeds = ConcurrentHashMap<String, () -> MockResponse>()
    private val overrides = CopyOnWriteArrayList<Override>()
    private val etagCounter = AtomicInteger(0)
    private val putsWithoutEtag = ConcurrentHashMap.newKeySet<String>()
    private val lock = Any()

    val requests = CopyOnWriteArrayList<CapturedRequest>()

    /** When false, collections stop advertising sync-collection so the client falls back to ctag. */
    @Volatile var supportsSyncCollection = true

    /** Runs on the server thread before each request is answered, e.g. to interleave local edits with a sync. */
    @Volatile var beforeResponse: ((CapturedRequest) -> Unit)? = null

    /** When true every `/dav` request answers 401, as after a revoked app password. */
    @Volatile var rejectCredentials = false

    val root = "/dav/"
    val principalHref = "/dav/principals/$username/"
    val homeHref = "/dav/calendars/$username/"
    val eventsHref = "${homeHref}events/"
    val tasksHref = "${homeHref}tasks/"

    val serverUrl: String get() = server.url(root).toString()

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        // MockWebServer flushes headers and body separately; without TCP_NODELAY every response
        // waits for the client's delayed ACK (~40 ms on Linux).
        server.serverSocketFactory = NoDelayServerSocketFactory
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        addCollection(eventsHref, "Events", setOf("VEVENT"))
        addCollection(tasksHref, "Tasks", setOf("VTODO"))
    }

    fun addCollection(href: String, displayName: String, components: Set<String>, readOnly: Boolean = false) {
        synchronized(lock) { collections[href] = Collection(href, displayName, components, readOnly) }
    }

    fun url(path: String): String = server.url(path).toString()

    /** Creates or replaces a resource as if another client had written it. Returns its href. */
    fun putRemote(collectionHref: String, fileName: String, ics: String): String = synchronized(lock) {
        val collection = collections.getValue(collectionHref)
        val href = collectionHref + fileName
        store(collection, href, ics)
        href
    }

    fun deleteRemote(href: String) = synchronized(lock) {
        val collection = collectionFor(href) ?: error("No collection for $href")
        collection.resources.remove(href)
        collection.version++
        collection.changeLog += collection.version to href
    }

    fun stored(href: String): StoredResource? = synchronized(lock) { collectionFor(href)?.resources?.get(href) }

    fun setFeed(path: String, body: String, contentType: String = "text/calendar; charset=utf-8") {
        feeds[path] = { MockResponse().setResponseCode(200).addHeader("Content-Type", contentType).setBody(body) }
    }

    /**
     * The next [times] requests whose method matches, whose path contains [pathContains] and (if given)
     * whose body contains [bodyContains] get [response].
     */
    fun respondNext(
        method: String,
        pathContains: String,
        times: Int = 1,
        bodyContains: String? = null,
        response: () -> MockResponse,
    ) {
        overrides += Override(method, pathContains, bodyContains, response, times)
    }

    /** The next PUT to [href] is stored as usual but answered without an ETag header. */
    fun answerNextPutWithoutEtag(href: String) {
        putsWithoutEtag += href
    }

    fun requests(method: String): List<CapturedRequest> = requests.filter { it.method == method }

    fun clearRequests() = requests.clear()

    override fun close() {
        server.shutdown()
    }

    private fun handle(request: RecordedRequest): MockResponse {
        val method = request.method.orEmpty()
        val path = request.path.orEmpty().substringBefore('?')
        val captured = CapturedRequest(
            method = method,
            path = path,
            headers = request.headers.associate { it.first to it.second },
            body = request.body.readUtf8(),
        )
        requests += captured
        beforeResponse?.invoke(captured)
        overrides.firstOrNull {
            it.remaining > 0 && it.method == method && captured.path.contains(it.pathContains) &&
                (it.bodyContains == null || captured.body.contains(it.bodyContains))
        }?.let {
            it.remaining--
            return it.response()
        }
        feeds[path]?.let { return it() }
        if (!path.startsWith(root.trimEnd('/')) && path != WELL_KNOWN) return MockResponse().setResponseCode(404)
        if (rejectCredentials || captured.header("Authorization") != Credentials.basic(username, password)) {
            return MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "Basic realm=\"fake\"")
        }
        return synchronized(lock) { route(captured) }
    }

    private fun route(request: CapturedRequest): MockResponse {
        val path = request.path
        val key = path.trimEnd('/')
        return when (request.method) {
            "OPTIONS" -> MockResponse().setResponseCode(200).addHeader("DAV", "1, 3, calendar-access")
            "PROPFIND" -> when {
                key == root.trimEnd('/') || path == WELL_KNOWN -> xml(CalDavXml.serviceRoot(root, principalHref))
                key == principalHref.trimEnd('/') -> xml(CalDavXml.principal(principalHref, homeHref, "$username@example.test"))
                key == homeHref.trimEnd('/') -> xml(homeListing())
                collectionAt(path) != null -> xml(etagListing(collectionAt(path)!!))
                else -> resourceAt(path)?.let { xml(CalDavXml.multistatus(CalDavXml.etag(it.href, it.etag))) }
                    ?: MockResponse().setResponseCode(404)
            }
            "REPORT" -> {
                val collection = collectionAt(path) ?: return MockResponse().setResponseCode(404)
                when {
                    "sync-collection" in request.body -> syncCollection(collection, request.body)
                    "calendar-multiget" in request.body -> {
                        val hrefs = Regex("<d:href>(.*?)</d:href>").findAll(request.body).map { it.groupValues[1] }.toList()
                        xml(
                            CalDavXml.multistatus(
                                *hrefs.map { href ->
                                    collection.resources[href]?.let { CalDavXml.calendarData(it.href, it.etag, it.ics) }
                                        ?: CalDavXml.notFoundResponse(href)
                                }.toTypedArray(),
                            ),
                        )
                    }
                    "calendar-query" in request.body -> {
                        val component = Regex("""comp-filter name="(VEVENT|VTODO)"""").find(request.body)?.groupValues?.get(1)
                        xml(
                            CalDavXml.multistatus(
                                *collection.resources.values
                                    .filter { component == null || "BEGIN:$component" in it.ics }
                                    .map { CalDavXml.calendarData(it.href, it.etag, it.ics) }
                                    .toTypedArray(),
                            ),
                        )
                    }
                    else -> MockResponse().setResponseCode(400)
                }
            }
            "GET" -> resourceAt(path)?.let {
                MockResponse().setResponseCode(200).addHeader("ETag", it.etag).addHeader("Content-Type", "text/calendar").setBody(it.ics)
            } ?: MockResponse().setResponseCode(404)
            "PUT" -> {
                val collection = collectionFor(path) ?: return MockResponse().setResponseCode(409)
                if (collection.readOnly) return MockResponse().setResponseCode(403)
                val existing = collection.resources[path]
                val ifMatch = request.header("If-Match")
                val ifNoneMatch = request.header("If-None-Match")
                if (ifNoneMatch == "*" && existing != null) return MockResponse().setResponseCode(412)
                if (ifMatch != null && ifMatch != existing?.etag) return MockResponse().setResponseCode(412)
                val stored = store(collection, path, request.body)
                MockResponse().setResponseCode(if (existing == null) 201 else 204).apply {
                    if (!putsWithoutEtag.remove(path)) addHeader("ETag", stored.etag)
                }
            }
            "DELETE" -> {
                val collection = collectionFor(path) ?: return MockResponse().setResponseCode(404)
                val existing = collection.resources[path] ?: return MockResponse().setResponseCode(404)
                val ifMatch = request.header("If-Match")
                if (ifMatch != null && ifMatch != existing.etag) return MockResponse().setResponseCode(412)
                collection.resources.remove(path)
                collection.version++
                collection.changeLog += collection.version to path
                MockResponse().setResponseCode(204)
            }
            else -> MockResponse().setResponseCode(405)
        }
    }

    private fun store(collection: Collection, href: String, ics: String): StoredResource {
        val stored = StoredResource(href, "\"etag-${etagCounter.incrementAndGet()}\"", ics)
        collection.resources[href] = stored
        collection.version++
        collection.changeLog += collection.version to href
        return stored
    }

    private fun homeListing(): String = CalDavXml.multistatus(
        CalDavXml.okResponse(homeHref, "<d:resourcetype><d:collection /></d:resourcetype>"),
        *collections.values.map {
            CalDavXml.calendarCollection(
                href = it.href,
                displayName = it.displayName,
                components = it.components,
                readOnly = it.readOnly,
                supportsSyncCollection = supportsSyncCollection,
                syncToken = syncToken(it),
                ctag = "ctag-${it.version}",
            )
        }.toTypedArray(),
    )

    private fun etagListing(collection: Collection): String = CalDavXml.multistatus(
        CalDavXml.okResponse(collection.href, "<d:resourcetype><d:collection /></d:resourcetype>"),
        *collection.resources.values.map { CalDavXml.etag(it.href, it.etag) }.toTypedArray(),
    )

    private fun syncCollection(collection: Collection, body: String): MockResponse {
        val token = Regex("<d:sync-token>(.*?)</d:sync-token>").find(body)?.groupValues?.get(1).orEmpty()
        val since = token.substringAfterLast('/').toIntOrNull()
        if (!token.startsWith(syncTokenPrefix(collection)) || since == null || since > collection.version) {
            return MockResponse().setResponseCode(403)
                .addHeader("Content-Type", "application/xml")
                .setBody("""<d:error xmlns:d="DAV:"><d:valid-sync-token /></d:error>""")
        }
        val changed = collection.changeLog.filter { it.first > since }.map { it.second }.distinct()
        return xml(
            CalDavXml.multistatus(
                *changed.map { href ->
                    collection.resources[href]?.let { CalDavXml.etag(it.href, it.etag) } ?: CalDavXml.notFoundResponse(href)
                }.toTypedArray(),
                syncToken = syncToken(collection),
            ),
        )
    }

    private fun syncTokenPrefix(collection: Collection) = "http://fake.test/sync${collection.href}"

    private fun syncToken(collection: Collection) = "${syncTokenPrefix(collection)}${collection.version}"

    private fun collectionAt(path: String): Collection? = collections[path] ?: collections["${path.trimEnd('/')}/"]

    private fun collectionFor(href: String): Collection? =
        collections.values.firstOrNull { href.startsWith(it.href) && href != it.href }

    private fun resourceAt(path: String): StoredResource? = collectionFor(path)?.resources?.get(path)

    private companion object {
        const val WELL_KNOWN = "/.well-known/caldav"
    }

    private fun xml(body: String) = MockResponse()
        .setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(body)
}

private object NoDelayServerSocketFactory : ServerSocketFactory() {
    private class NoDelayServerSocket : ServerSocket() {
        override fun accept(): Socket = super.accept().apply { tcpNoDelay = true }
    }

    override fun createServerSocket(): ServerSocket = NoDelayServerSocket()

    override fun createServerSocket(port: Int): ServerSocket =
        NoDelayServerSocket().apply { bind(InetSocketAddress(port)) }

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket =
        NoDelayServerSocket().apply { bind(InetSocketAddress(port), backlog) }

    override fun createServerSocket(port: Int, backlog: Int, ifAddress: InetAddress?): ServerSocket =
        NoDelayServerSocket().apply { bind(InetSocketAddress(ifAddress, port), backlog) }
}
