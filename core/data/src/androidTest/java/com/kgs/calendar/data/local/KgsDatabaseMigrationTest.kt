package com.kgs.calendar.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates migrations against the schemas Room exports to app/schemas. Only version 19 onwards is
 * exported, so older migrations are covered by [KgsDatabaseMigrations] review alone.
 *
 * When bumping [KgsDatabase] to a new version, add the migration to [KgsDatabaseMigrations.ALL],
 * commit the generated schema JSON, raise CURRENT_VERSION and add a test like [migrate19To20].
 */
@RunWith(AndroidJUnit4::class)
class KgsDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KgsDatabase::class.java,
    )

    @Test
    fun exportedCurrentSchemaValidates() {
        helper.createDatabase(TEST_DB, CURRENT_VERSION).close()

        helper.runMigrationsAndValidate(TEST_DB, CURRENT_VERSION, true, *KgsDatabaseMigrations.ALL).close()
    }

    @Test
    fun migrate19To20() {
        helper.createDatabase(TEST_DB, 19).use { db ->
            db.execSQL(
                """
                INSERT INTO accounts (id, serverUrl, username, displayName, lastSyncAtMillis, syncState, sourceType)
                VALUES ('primary', 'https://dav.example.test', 'alice', 'Alice', NULL, 'idle', 'caldav')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO pending_mutations
                    (id, accountId, collectionHref, resourceHref, componentType, action, payloadIcs, baseEtag, createdAtMillis)
                VALUES (7, 'primary', '/cal/work/', '/cal/work/weekly.ics', 'VEVENT', 'PUT', 'BEGIN:VCALENDAR', '"etag-1"', 1234)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 20, true, *KgsDatabaseMigrations.ALL).use { db ->
            db.query(
                """
                SELECT accountId, collectionHref, resourceHref, componentType, action, payloadIcs, baseEtag,
                    createdAtMillis, occurrenceScope
                FROM pending_mutations WHERE id = 7
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("primary", cursor.getString(0))
                assertEquals("/cal/work/", cursor.getString(1))
                assertEquals("/cal/work/weekly.ics", cursor.getString(2))
                assertEquals("VEVENT", cursor.getString(3))
                assertEquals("PUT", cursor.getString(4))
                assertEquals("BEGIN:VCALENDAR", cursor.getString(5))
                assertEquals("\"etag-1\"", cursor.getString(6))
                assertEquals(1234L, cursor.getLong(7))
                // Changes queued before the upgrade mark the whole resource.
                assertTrue(cursor.isNull(8))
            }
        }
    }

    @Test
    fun roomOpensDatabaseCreatedFromExportedSchema() {
        helper.createDatabase(TEST_DB, CURRENT_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO accounts (id, serverUrl, username, displayName, lastSyncAtMillis, syncState, sourceType)
                VALUES ('primary', 'https://dav.example.test', 'alice', 'Alice', NULL, 'idle', 'caldav')
                """.trimIndent(),
            )
        }

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KgsDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(*KgsDatabaseMigrations.ALL)
            .build()
        try {
            val accounts = runBlocking { database.accountDao().getAll() }
            assertEquals(listOf("alice"), accounts.map { it.username })
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "kgs-migration-test.db"
        const val CURRENT_VERSION = 20
    }
}
