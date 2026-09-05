package com.nikac.guider.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyPreferencesImporterTest {
    private lateinit var context: Context
    private lateinit var database: GuiderDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LEGACY_PREFERENCES.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        database = Room.inMemoryDatabaseBuilder(context, GuiderDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
        LEGACY_PREFERENCES.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test
    fun freshInstallDoesNotCreateStarterTasksOrHabits() = runBlocking {
        LegacyPreferencesImporter(context).importIfNeeded(database)

        assertEquals(emptyList<DailyTaskEntity>(), database.dailyTaskDao().observeAll(GUEST_OWNER_ID).first())
        assertEquals(emptyList<HabitRecord>(), database.habitDao().observeAll(GUEST_OWNER_ID).first())
    }

    @Test
    fun previouslySeededRowsAreHiddenAndKeptAsCloudTombstones() = runBlocking {
        database.appMetadataDao().set(
            AppMetadataEntity("legacy_shared_preferences_imported", "1"),
        )
        database.dailyTaskDao().insert(
            DailyTaskEntity(
                id = 1L,
                remoteId = "starter-task",
                category = "HEALTH",
                title = "Drink water",
                isFinished = false,
                createdDayKey = 20260905,
                completedDayKey = null,
                linkedGoalId = null,
            ),
        )
        database.habitDao().insertHabit(
            HabitEntity(
                id = 1L,
                remoteId = "starter-habit",
                name = "Drink water",
                colorHue = 210f,
                linkedGoalId = null,
                activeStartDayKey = null,
                activeEndDayKey = null,
            ),
        )

        LegacyPreferencesImporter(context).importIfNeeded(database)

        assertEquals(emptyList<DailyTaskEntity>(), database.dailyTaskDao().observeAll(GUEST_OWNER_ID).first())
        assertEquals(emptyList<HabitRecord>(), database.habitDao().observeAll(GUEST_OWNER_ID).first())
        val removedTask = database.dailyTaskDao().getByRemoteId(GUEST_OWNER_ID, "starter-task")
        val removedHabit = database.habitDao().getByRemoteId(GUEST_OWNER_ID, "starter-habit")
        assertNotNull(removedTask?.deletedAtEpochMillis)
        assertNotNull(removedHabit?.deletedAtEpochMillis)
        assertTrue(removedTask?.syncPending == true)
        assertTrue(removedHabit?.syncPending == true)
    }

    private companion object {
        val LEGACY_PREFERENCES = listOf(
            "daily_task_tracking",
            "goal_tracking",
            "habit_tracking",
            "sleep_tracking",
            "money_tracking",
        )
    }
}
