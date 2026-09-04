package com.nikac.guider.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuiderDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = GuiderDatabase::class.java,
    )

    @Test
    fun migrate1To2BackfillsCompletionWeekdays() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO goals (
                    id, title, type, createdDayKey, achievedDayKey, startDayKey, endDayKey
                ) VALUES (1, 'Consistency', 'PERIODIC', 20260801, NULL, 20260801, 20260831)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO habits (
                    id, name, colorHue, linkedGoalId, activeStartDayKey, activeEndDayKey
                ) VALUES (10, 'Exercise', 210, 1, 20260801, 20260831)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO habit_completions (habitId, dayKey) VALUES (10, 20260817)",
            )
            execSQL(
                "INSERT INTO habit_completions (habitId, dayKey) VALUES (10, 20260821)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            GuiderDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT dayKey, weekday FROM habit_completions ORDER BY dayKey",
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(20260817, cursor.getInt(0))
            assertEquals("MONDAY", cursor.getString(1))
            check(cursor.moveToNext())
            assertEquals(20260821, cursor.getInt(0))
            assertEquals("FRIDAY", cursor.getString(1))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "guider-migration-test"
    }
}
