package com.nikac.guider.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDataIsolationTest {
    private lateinit var database: GuiderDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GuiderDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ownersOnlyObserveTheirOwnProgressAndArchivedRowsRemainStored() = runBlocking {
        val dao = database.dailyTaskDao()
        val guestTask = DailyTaskEntity(
            ownerId = GUEST_OWNER_ID,
            title = "Guest task",
            category = "HEALTH",
            isFinished = true,
            createdDayKey = 20260901,
            completedDayKey = 20260901,
            linkedGoalId = null,
        )
        dao.insert(guestTask)
        dao.insert(
            DailyTaskEntity(
                ownerId = ACCOUNT_OWNER,
                title = "Account task",
                category = "WORK",
                isFinished = false,
                createdDayKey = 20260905,
                completedDayKey = null,
                linkedGoalId = null,
            ),
        )

        assertEquals(listOf("Guest task"), dao.observeAll(GUEST_OWNER_ID).first().map { it.title })
        assertEquals(listOf("Account task"), dao.observeAll(ACCOUNT_OWNER).first().map { it.title })

        dao.archiveCompletedBefore(
            ownerId = GUEST_OWNER_ID,
            dayKey = 20260905,
            updatedAtEpochMillis = 1234L,
            syncPending = false,
        )

        assertEquals(emptyList<DailyTaskEntity>(), dao.observeAll(GUEST_OWNER_ID).first())
        val archived = dao.getByRemoteId(GUEST_OWNER_ID, guestTask.remoteId)
        assertNotNull(archived)
        assertEquals(1234L, archived?.archivedAtEpochMillis)
    }

    private companion object {
        const val ACCOUNT_OWNER = "firebase:test-account"
    }
}
