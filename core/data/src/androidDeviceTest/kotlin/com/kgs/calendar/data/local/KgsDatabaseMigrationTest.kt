package com.kgs.calendar.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.domain.trash.TrashRetention
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun migrate20To21AddsEmptyTrashTable() {
        helper.createDatabase(TEST_DB, 20).use { db ->
            insertAccountAndPendingMutation(db)
        }

        helper.runMigrationsAndValidate(TEST_DB, 21, true, *KgsDatabaseMigrations.ALL).use { db ->
            assertPendingMutationSurvived(db)
            db.query("SELECT COUNT(*) FROM trashed_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            insertTrashedItem(db)
        }
    }

    @Test
    fun migrate19To21ChainsBothMigrations() {
        helper.createDatabase(TEST_DB, 19).use { db ->
            insertAccountAndPendingMutation(db, withOccurrenceScope = false)
        }

        helper.runMigrationsAndValidate(TEST_DB, 21, true, *KgsDatabaseMigrations.ALL).use { db ->
            assertPendingMutationSurvived(db)
            insertTrashedItem(db)
        }
    }

    @Test
    fun migrate21To22MarksExistingTrashAsLocalSnapshots() {
        helper.createDatabase(TEST_DB, 21).use { db ->
            insertAccountAndPendingMutation(db)
            insertTrashedItem(db)
        }

        helper.runMigrationsAndValidate(TEST_DB, 22, true, *KgsDatabaseMigrations.ALL).use { db ->
            assertPendingMutationSurvived(db)
            assertMigratedLocalSnapshot(db)
            insertServerTrashItem(db)
        }
    }

    @Test
    fun migrate19To22ChainsAllMigrations() {
        helper.createDatabase(TEST_DB, 19).use { db ->
            insertAccountAndPendingMutation(db, withOccurrenceScope = false)
        }

        helper.runMigrationsAndValidate(TEST_DB, 22, true, *KgsDatabaseMigrations.ALL).use { db ->
            assertPendingMutationSurvived(db)
            insertTrashedItem(db)
            insertServerTrashItem(db)
        }
    }

    /** A snapshot written by DB 21 is a local one that expires 30 days after its delete. */
    private fun assertMigratedLocalSnapshot(db: SupportSQLiteDatabase) {
        db.query("SELECT title, origin, serverHref, deletedAtMillis, expiresAtMillis FROM trashed_items WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Weekly", cursor.getString(0))
            assertEquals("local", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(2000L, cursor.getLong(3))
            assertEquals(2000L + TrashRetention.MILLIS, cursor.getLong(4))
        }
    }

    /** DB 22 caches Nextcloud trash bin objects, at most once per account and trash bin href. */
    private fun insertServerTrashItem(db: SupportSQLiteDatabase) {
        val insert =
            """
            INSERT INTO trashed_items (componentType, uid, collectionHref, accountId, sourceType, resourceHref,
                providerEventId, rawIcs, title, startMillis, hasTime, collectionName, collectionColor, manualColor, deletedAtMillis,
                origin, serverHref, expiresAtMillis)
            VALUES ('VTODO', 'todo', '/cal/work/', 'primary', 'caldav', '/cal/work/todo.ics',
                NULL, 'BEGIN:VCALENDAR', 'Todo', NULL, 0, 'Work', -16777216, NULL, 3000,
                'server', '/cal/trashbin/objects/12.ics', 5000)
            """.trimIndent()
        db.execSQL(insert)
        db.query("SELECT origin, serverHref, expiresAtMillis FROM trashed_items WHERE uid = 'todo'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("server", cursor.getString(0))
            assertEquals("/cal/trashbin/objects/12.ics", cursor.getString(1))
            assertEquals(5000L, cursor.getLong(2))
        }
        assertThrows(SQLiteConstraintException::class.java) { db.execSQL(insert) }
    }

    private fun insertAccountAndPendingMutation(db: SupportSQLiteDatabase, withOccurrenceScope: Boolean = true) {
        db.execSQL(
            """
            INSERT INTO accounts (id, serverUrl, username, displayName, lastSyncAtMillis, syncState, sourceType)
            VALUES ('primary', 'https://dav.example.test', 'alice', 'Alice', NULL, 'idle', 'caldav')
            """.trimIndent(),
        )
        val scopeColumn = if (withOccurrenceScope) ", occurrenceScope" else ""
        val scopeValue = if (withOccurrenceScope) ", NULL" else ""
        db.execSQL(
            """
            INSERT INTO pending_mutations
                (id, accountId, collectionHref, resourceHref, componentType, action, payloadIcs, baseEtag, createdAtMillis$scopeColumn)
            VALUES (7, 'primary', '/cal/work/', '/cal/work/weekly.ics', 'VEVENT', 'DELETE', NULL, '"etag-1"', 1234$scopeValue)
            """.trimIndent(),
        )
    }

    private fun assertPendingMutationSurvived(db: SupportSQLiteDatabase) {
        db.query("SELECT resourceHref, action, baseEtag, occurrenceScope FROM pending_mutations WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("/cal/work/weekly.ics", cursor.getString(0))
            assertEquals("DELETE", cursor.getString(1))
            assertEquals("\"etag-1\"", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
    }

    /** The migrated table accepts a row with every column the entity writes. */
    private fun insertTrashedItem(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO trashed_items (componentType, uid, collectionHref, accountId, sourceType, resourceHref,
                providerEventId, rawIcs, title, startMillis, hasTime, collectionName, collectionColor, manualColor, deletedAtMillis)
            VALUES ('VEVENT', 'weekly', '/cal/work/', 'primary', 'caldav', '/cal/work/weekly.ics',
                NULL, 'BEGIN:VCALENDAR', 'Weekly', 1000, 1, 'Work', -16777216, NULL, 2000)
            """.trimIndent(),
        )
        db.query("SELECT id, title, deletedAtMillis FROM trashed_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("Weekly", cursor.getString(1))
            assertEquals(2000L, cursor.getLong(2))
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
        const val CURRENT_VERSION = 22
    }
}
