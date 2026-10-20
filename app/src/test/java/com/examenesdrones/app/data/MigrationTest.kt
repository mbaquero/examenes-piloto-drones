package com.examenesdrones.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.examenesdrones.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Validates the exported Room schemas. When the schema version increases, add a test that creates the
 * previous version, inserts history rows and runs [AppDatabase.MIGRATIONS] to the current version.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun currentSchemaMatchesExportedSchemaAndAllMigrationsValidate() {
        val db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO exam_attempt (startedAt, submittedAt, status, configJson, questionCount, correct, incorrect, unanswered, " +
                "score, maxScore, percent, passed, timeLimitSec, elapsedSec, topicKeys, currentIndex, documentHash) " +
                "VALUES (1, 2, 'SUBMITTED', '{}', 20, 16, 4, 0, 16.0, 20.0, 80.0, 1, 1200, 600, 'T1', 0, 'h')"
        )
        db.close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS)
        migrated.query("SELECT correct, status FROM exam_attempt").use { c ->
            c.moveToFirst()
            assertEquals(16, c.getInt(0))
            assertEquals("SUBMITTED", c.getString(1))
        }
        migrated.close()
    }

    @Test
    fun noDestructiveFallbackIsConfigured() {
        // Every version step up to the current one must be covered by an explicit migration.
        val covered = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until AppDatabase.VERSION) assertEquals(true, (v to v + 1) in covered)
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
