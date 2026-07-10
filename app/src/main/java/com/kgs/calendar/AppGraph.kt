package com.kgs.calendar

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.SourceType
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.reminder.ReminderRegistry
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.widget.KgsWidgetUpdateScheduler
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext

    val database: KgsDatabase = Room.databaseBuilder(appContext, KgsDatabase::class.java, "kgs-calendar.db")
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
        )
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

    val taskMutationCoordinator = TaskMutationCoordinator(
        persistStatus = repository::setTaskStatus,
        pushPendingChanges = repository::pushPendingChangesCreatedSince,
        notificationReconciler = reminderRegistry,
        rescheduleReminders = { ReminderScheduler.reschedule(appContext) },
        updateWidgets = { KgsWidgetUpdateScheduler.updateAll(appContext) },
    )

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueHasTime INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tasks ADD COLUMN startHasTime INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    """
                    UPDATE tasks
                    SET dueHasTime = CASE
                        WHEN dueAtMillis IS NULL THEN 0
                        WHEN EXISTS (
                            SELECT 1 FROM calendar_resources
                            WHERE calendar_resources.href = tasks.resourceHref
                              AND calendar_resources.rawIcs LIKE '%DUE%VALUE=DATE%'
                        ) THEN 0
                        ELSE 1
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE tasks
                    SET startHasTime = CASE
                        WHEN startAtMillis IS NULL THEN 0
                        WHEN EXISTS (
                            SELECT 1 FROM calendar_resources
                            WHERE calendar_resources.href = tasks.resourceHref
                              AND calendar_resources.rawIcs LIKE '%DTSTART%VALUE=DATE%'
                        ) THEN 0
                        ELSE 1
                    END
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN location TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN url TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN categories TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN percentComplete INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN locationMapVerified INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN locationMapVerified INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN manualColor INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN manualColor INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN exDatesCsv TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN status TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN remindersCsv TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindersCsv TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN status TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN classification TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN transparency TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN categories TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN organizerJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN attendeesJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN readOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE collections
                    SET readOnly = 1
                    WHERE href LIKE 'readonly-%'
                       OR LOWER(displayName) LIKE '%geburtstag%'
                       OR LOWER(displayName) LIKE '%birthday%'
                       OR LOWER(href) LIKE '%birthday%'
                       OR LOWER(href) LIKE '%geburtstag%'
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN exDatesCsv TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN remoteDisplayName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE collections ADD COLUMN customDisplayName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN automaticColor INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE collections ADD COLUMN sourceColor INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE collections ADD COLUMN customColor INTEGER DEFAULT NULL")
                db.execSQL("UPDATE collections SET automaticColor = color")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS events_new (
                        uid TEXT NOT NULL,
                        collectionHref TEXT NOT NULL,
                        resourceHref TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        location TEXT,
                        locationMapVerified INTEGER,
                        startsAtMillis INTEGER NOT NULL,
                        endsAtMillis INTEGER NOT NULL,
                        allDay INTEGER NOT NULL,
                        recurrenceRule TEXT,
                        isRecurring INTEGER NOT NULL,
                        exDatesCsv TEXT,
                        remindersCsv TEXT,
                        status TEXT,
                        classification TEXT,
                        transparency TEXT,
                        categories TEXT,
                        organizerJson TEXT,
                        attendeesJson TEXT,
                        color INTEGER NOT NULL,
                        manualColor INTEGER,
                        syncError TEXT,
                        PRIMARY KEY(resourceHref),
                        FOREIGN KEY(collectionHref) REFERENCES collections(href) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO events_new (
                        uid, collectionHref, resourceHref, title, description, location, locationMapVerified,
                        startsAtMillis, endsAtMillis, allDay, recurrenceRule, isRecurring, exDatesCsv,
                        remindersCsv, status, classification, transparency, categories, organizerJson,
                        attendeesJson, color, manualColor, syncError
                    )
                    SELECT
                        uid, collectionHref, resourceHref, title, description, location, locationMapVerified,
                        startsAtMillis, endsAtMillis, allDay, recurrenceRule, isRecurring, exDatesCsv,
                        remindersCsv, status, classification, transparency, categories, organizerJson,
                        attendeesJson, color, manualColor, syncError
                    FROM events
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE events")
                db.execSQL("ALTER TABLE events_new RENAME TO events")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_collectionHref ON events(collectionHref)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startsAtMillis ON events(startsAtMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_endsAtMillis ON events(endsAtMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_uid ON events(uid)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tasks_new (
                        uid TEXT NOT NULL,
                        collectionHref TEXT NOT NULL,
                        resourceHref TEXT NOT NULL,
                        title TEXT NOT NULL,
                        notes TEXT,
                        location TEXT,
                        locationMapVerified INTEGER,
                        url TEXT,
                        categories TEXT,
                        dueAtMillis INTEGER,
                        dueHasTime INTEGER NOT NULL,
                        startAtMillis INTEGER,
                        startHasTime INTEGER NOT NULL,
                        completedAtMillis INTEGER,
                        isCompleted INTEGER NOT NULL,
                        status TEXT,
                        priority INTEGER,
                        percentComplete INTEGER,
                        recurrenceRule TEXT,
                        exDatesCsv TEXT,
                        remindersCsv TEXT,
                        color INTEGER NOT NULL,
                        manualColor INTEGER,
                        syncError TEXT,
                        PRIMARY KEY(resourceHref),
                        FOREIGN KEY(collectionHref) REFERENCES collections(href) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO tasks_new (
                        uid, collectionHref, resourceHref, title, notes, location, locationMapVerified,
                        url, categories, dueAtMillis, dueHasTime, startAtMillis, startHasTime,
                        completedAtMillis, isCompleted, status, priority, percentComplete, recurrenceRule,
                        exDatesCsv, remindersCsv, color, manualColor, syncError
                    )
                    SELECT
                        uid, collectionHref, resourceHref, title, notes, location, locationMapVerified,
                        url, categories, dueAtMillis, dueHasTime, startAtMillis, startHasTime,
                        completedAtMillis, isCompleted, status, priority, percentComplete, recurrenceRule,
                        exDatesCsv, remindersCsv, color, manualColor, syncError
                    FROM tasks
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE tasks")
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_collectionHref ON tasks(collectionHref)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_uid ON tasks(uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueAtMillis ON tasks(dueAtMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_startAtMillis ON tasks(startAtMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_isCompleted ON tasks(isCompleted)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN sourceType TEXT NOT NULL DEFAULT '${SourceType.CalDav}'")
                db.execSQL("ALTER TABLE collections ADD COLUMN sourceType TEXT NOT NULL DEFAULT '${SourceType.CalDav}'")
                db.execSQL("ALTER TABLE collections ADD COLUMN externalId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE collections ADD COLUMN capabilitiesJson TEXT DEFAULT NULL")
                db.execSQL("UPDATE accounts SET sourceType = '${SourceType.Local}' WHERE id = 'local' OR serverUrl LIKE 'local://%'")
                db.execSQL("UPDATE accounts SET sourceType = '${SourceType.ReadOnlyUrl}' WHERE id LIKE 'readonly-%' OR username = 'Read-only URL'")
                db.execSQL("UPDATE collections SET sourceType = '${SourceType.Local}' WHERE href LIKE 'local://%'")
                db.execSQL("UPDATE collections SET sourceType = '${SourceType.ReadOnlyUrl}' WHERE href LIKE 'readonly-%'")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN principalUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN calendarHomeUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN capabilitiesJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN rDatesCsv TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN timezoneId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceOverridesJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN rDatesCsv TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN timezoneId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceOverridesJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN parentUid TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_parentUid ON tasks(parentUid)")
            }
        }
    }
}
