package com.nikac.guider.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun migrate2To3PreservesLocalProgressAndCreatesSyncMetadata() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """INSERT INTO goals
                   (id, title, type, createdDayKey, achievedDayKey, startDayKey, endDayKey)
                   VALUES (1, 'Consistency', 'PERIODIC', 20260801, NULL, 20260801, 20260831)""",
            )
            execSQL(
                """INSERT INTO habits
                   (id, name, colorHue, linkedGoalId, activeStartDayKey, activeEndDayKey)
                   VALUES (10, 'Exercise', 210, 1, 20260801, 20260831)""",
            )
            execSQL("INSERT INTO habit_weekdays (habitId, weekday) VALUES (10, 'MONDAY')")
            execSQL(
                """INSERT INTO habit_completions (habitId, dayKey, weekday)
                   VALUES (10, 20260817, 'MONDAY')""",
            )
            execSQL(
                """INSERT INTO daily_tasks
                   (id, category, title, isFinished, createdDayKey, completedDayKey, linkedGoalId)
                   VALUES (20, 'HEALTH', 'Walk', 1, 20260817, 20260817, 1)""",
            )
            execSQL(
                """INSERT INTO active_sleep_session
                   (singletonId, activatedAtEpochMillis, sleepStartsAtEpochMillis)
                   VALUES (1, 1000, 2000)""",
            )
            execSQL(
                """INSERT INTO sleep_records
                   (id, activatedAtEpochMillis, sleepStartsAtEpochMillis, endedAtEpochMillis)
                   VALUES (30, 1000, 2000, 3000)""",
            )
            execSQL("INSERT INTO money_state (id, periodStartDayKey) VALUES (1, 20260801)")
            execSQL(
                """INSERT INTO spendings
                   (id, ledgerId, title, amountMinor, createdAtEpochMillis)
                   VALUES (40, 1, 'Lunch', 1250, 4000)""",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            GuiderDatabase.MIGRATION_2_3,
        )

        migrated.query("SELECT ownerId, remoteId, title FROM daily_tasks WHERE id = 20").use {
            check(it.moveToFirst())
            assertEquals(GUEST_OWNER_ID, it.getString(0))
            assertFalse(it.getString(1).isBlank())
            assertEquals("Walk", it.getString(2))
        }
        migrated.query(
            "SELECT currentPeriodRemoteId, periodStartDayKey FROM money_state WHERE ownerId = 'guest'",
        ).use {
            check(it.moveToFirst())
            assertEquals("guest-initial-period", it.getString(0))
            assertEquals(20260801, it.getInt(1))
        }
        migrated.query("SELECT periodRemoteId, amountMinor FROM spendings WHERE id = 40").use {
            check(it.moveToFirst())
            assertEquals("guest-initial-period", it.getString(0))
            assertEquals(1250L, it.getLong(1))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "guider-migration-test"
    }
}
