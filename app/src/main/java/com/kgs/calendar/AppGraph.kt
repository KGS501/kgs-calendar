package com.kgs.calendar

import android.content.Context
import androidx.room.Room
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.KgsDatabaseMigrations
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.reminder.ReminderRegistry
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.widget.KgsWidgetUpdateScheduler
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext

    val database: KgsDatabase = Room.databaseBuilder(appContext, KgsDatabase::class.java, "kgs-calendar.db")
        .addMigrations(*KgsDatabaseMigrations.ALL)
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    val settingsStore = SettingsStore(appContext)
    private val credentialsStore = CredentialsStore(appContext)
    private val loginFlowClient = NextcloudLoginFlowClient(okHttpClient)
    private val calDavHttpClient = CalDavHttpClient(okHttpClient)
    private val androidCalendarProviderClient = AndroidCalendarProviderClient(appContext)
    private val icalCodec = IcalCodec()

    val repository = CalendarRepository(
        database = database,
        credentialsStore = credentialsStore,
        loginFlowClient = loginFlowClient,
        calDavClient = calDavHttpClient,
        androidCalendarProviderClient = androidCalendarProviderClient,
        icalCodec = icalCodec,
    )

    val sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
        includeDisabledProviderCalendars = { settingsStore.showDisabledAndroidProviderCalendars.first() },
        fullRefresh = { includeDisabledProviderCalendars ->
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledProviderCalendars,
                forceFullCalDavRefresh = true,
            )
        },
        reconcileLocalState = {
            ReminderScheduler.reschedule(appContext)
            KgsWidgetUpdateScheduler.updateAll(appContext)
        },
    )

    val reminderRegistry = ReminderRegistry.create(appContext)

    val calendarLaunchResolver = CalendarLaunchResolver(
        eventByResource = repository::eventByResource,
        taskByResource = repository::taskByResource,
        expandEvents = repository::expandEventReminderOccurrences,
        expandTasks = repository::expandTaskReminderOccurrences,
    )

    val taskMutationCoordinator = TaskMutationCoordinator(
        persistStatus = repository::setTaskStatus,
        pushPendingChanges = repository::pushPendingChangesCreatedSince,
        notificationReconciler = reminderRegistry,
        rescheduleReminders = { ReminderScheduler.reschedule(appContext) },
        updateWidgets = { KgsWidgetUpdateScheduler.updateAll(appContext) },
    )

}
