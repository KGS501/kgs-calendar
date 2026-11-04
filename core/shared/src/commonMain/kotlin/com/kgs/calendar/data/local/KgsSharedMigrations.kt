package com.kgs.calendar.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Migrations of the schema versions iOS and desktop installs can be on; Android runs them after its older ones. */
object KgsSharedMigrations {
    val ALL: Array<Migration> get() = arrayOf(MIGRATION_22_23)

    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(connection: SQLiteConnection) {
            // A calendar move deletes its source only after the destination was acknowledged.
            connection.execSQL("ALTER TABLE pending_mutations ADD COLUMN dependsOnMutationId INTEGER")
            // The server's version of a resource this app could not import, kept for a later retry.
            connection.execSQL("ALTER TABLE calendar_resources ADD COLUMN quarantinedEtag TEXT")
            connection.execSQL("ALTER TABLE calendar_resources ADD COLUMN quarantinedRawIcs TEXT")
        }
    }
}
