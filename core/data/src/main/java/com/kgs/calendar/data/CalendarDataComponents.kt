package com.kgs.calendar.data

import com.kgs.calendar.data.account.CalendarSourceManager
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.mutation.EventMutations
import com.kgs.calendar.data.mutation.TaskMutations
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.provider.AndroidProviderWriteShield
import com.kgs.calendar.data.query.CalendarQueries
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.sync.AndroidProviderSyncEngine
import com.kgs.calendar.data.sync.CalDavSyncEngine
import com.kgs.calendar.data.sync.PendingMutationUploader
import com.kgs.calendar.data.sync.ReadOnlyUrlSyncEngine
import com.kgs.calendar.data.sync.SyncOrchestrator
import com.kgs.calendar.data.sync.SyncRepairs
import okhttp3.OkHttpClient
import java.time.ZoneId

/**
 * Wires the calendar data components. The shared in-memory state (the Android provider write
 * shield and the sync locks) exists once here and is handed to the components that need it.
 */
class CalendarDataComponents(
    database: KgsDatabase,
    credentialsStore: CredentialsStore,
    loginFlowClient: NextcloudLoginFlowClient,
    calDavClient: CalDavHttpClient,
    androidCalendarProviderClient: AndroidCalendarProviderClient,
    icalCodec: IcalCodec,
    readOnlyHttpClient: OkHttpClient,
    zoneId: ZoneId = ZoneId.systemDefault(),
    recurrenceExpander: RecurrenceExpander = RecurrenceExpander(zoneId),
) {
    private val localWrites = LocalWriteSupport(database, icalCodec)
    private val androidWriteShield = AndroidProviderWriteShield()

    val queries = CalendarQueries(database, recurrenceExpander, zoneId)
    val eventMutations = EventMutations(database, localWrites, androidCalendarProviderClient, icalCodec, androidWriteShield, zoneId)
    val taskMutations = TaskMutations(database, localWrites, icalCodec, zoneId)

    val repairs = SyncRepairs(database, localWrites, icalCodec)
    val uploader = PendingMutationUploader(database, credentialsStore, calDavClient, localWrites)
    val calDavSyncEngine = CalDavSyncEngine(database, credentialsStore, calDavClient, icalCodec, localWrites, uploader, zoneId)
    val readOnlyUrlSyncEngine = ReadOnlyUrlSyncEngine(database, localWrites, icalCodec, readOnlyHttpClient)
    val androidProviderSyncEngine = AndroidProviderSyncEngine(database, localWrites, androidCalendarProviderClient, androidWriteShield)

    // CalDAV claims every account the others don't, so it has to stay last.
    val syncOrchestrator = SyncOrchestrator(
        database = database,
        repairs = repairs,
        uploader = uploader,
        engines = listOf(androidProviderSyncEngine, readOnlyUrlSyncEngine, calDavSyncEngine),
    )

    val sources = CalendarSourceManager(
        database = database,
        credentialsStore = credentialsStore,
        loginFlowClient = loginFlowClient,
        calDavClient = calDavClient,
        androidCalendarProviderClient = androidCalendarProviderClient,
        localWrites = localWrites,
        readOnlyUrlSyncEngine = readOnlyUrlSyncEngine,
        androidProviderSyncEngine = androidProviderSyncEngine,
    )
}
