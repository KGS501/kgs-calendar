package com.kgs.calendar.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KgsDatabaseMigrationsTest {
    @Test
    fun migrationsFormContiguousChainFromVersionOneToCurrentSchema() {
        val migrations = KgsDatabaseMigrations.ALL

        migrations.forEachIndexed { index, migration ->
            assertEquals("start of migration #$index", index + 1, migration.startVersion)
            assertEquals("end of migration #$index", migration.startVersion + 1, migration.endVersion)
        }
        assertEquals(currentSchemaVersion(), migrations.last().endVersion)
    }

    /**
     * Room exports the schema of the current [KgsDatabase] version on every build, so the newest
     * exported file is the version a fresh install opens with.
     */
    private fun currentSchemaVersion(): Int {
        val schemaDir = listOf("schemas", "app/schemas")
            .map { File(it, KgsDatabase::class.java.name) }
            .firstOrNull { it.isDirectory }
            ?: error("Exported Room schemas not found from ${File("").absolutePath}")
        val latest = schemaDir.listFiles().orEmpty()
            .mapNotNull { file -> file.name.removeSuffix(".json").toIntOrNull()?.let { it to file } }
            .maxBy { it.first }
        val declared = Regex(""""version"\s*:\s*(\d+)""").find(latest.second.readText())?.groupValues?.get(1)?.toInt()
        assertTrue("${latest.second} declares version $declared", declared == latest.first)
        return latest.first
    }
}
