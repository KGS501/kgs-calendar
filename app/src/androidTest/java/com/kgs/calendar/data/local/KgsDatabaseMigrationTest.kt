package com.kgs.calendar.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates migrations against the schemas Room exports to app/schemas. Only version 19 onwards is
 * exported, so older migrations are covered by [KgsDatabaseMigrations] review alone.
 *
 * When bumping [KgsDatabase] to version 20, add MIGRATION_19_20 to [KgsDatabaseMigrations.ALL],
 * commit the generated 20.json and add a test following this pattern:
 *
 * ```
 * @Test
 * fun migrate19To20() {
 *     helper.createDatabase(TEST_DB, 19).use { db ->
 *         db.execSQL("INSERT INTO accounts (id, serverUrl, username, displayName, syncState, sourceType) VALUES (...)")
 *     }
 *     helper.runMigrationsAndValidate(TEST_DB, 20, true, *KgsDatabaseMigrations.ALL).use { db ->
 *         // Query db and assert that the rows inserted above survived with the expected new columns.
 *     }
 * }
 * ```
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
        const val CURRENT_VERSION = 19
    }
}
