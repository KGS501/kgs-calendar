package com.kgs.calendar.data

import com.kgs.calendar.data.account.SourceImageStore
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.platform.OkHttpTransport
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.time.ZoneId
import okhttp3.OkHttpClient
import java.io.File

/** The Android wiring: OkHttp for read-only feeds and PNG files in [sourceImageDirectory]. */
fun CalendarDataComponents(
    database: KgsDatabase,
    credentialsStore: CredentialsStore,
    loginFlowClient: NextcloudLoginFlowClient,
    calDavClient: CalDavHttpClient,
    androidCalendarProviderClient: AndroidCalendarProviderClient,
    icalCodec: IcalCodec,
    readOnlyHttpClient: OkHttpClient,
    zoneId: ZoneId? = null,
    recurrenceExpander: RecurrenceExpander = RecurrenceExpander(zoneId),
    sourceImageDirectory: File? = null,
): CalendarDataComponents = CalendarDataComponents(
    database = database,
    credentialsStore = credentialsStore,
    loginFlowClient = loginFlowClient,
    calDavClient = calDavClient,
    androidCalendarProviderClient = androidCalendarProviderClient,
    icalCodec = icalCodec,
    readOnlyHttpClient = OkHttpTransport(readOnlyHttpClient),
    zoneId = zoneId,
    recurrenceExpander = recurrenceExpander,
    sourceImages = SourceImageStore(sourceImageDirectory),
)
