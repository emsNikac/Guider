package com.nikac.guider.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nikac.guider.data.database.DailyTaskEntity
import com.nikac.guider.data.database.GUEST_OWNER_ID
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.domain.sync.DataOwner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataOwnerStoreTest {
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
    fun legacyAccountDataBecomesThePersistentDeviceDataset() = runBlocking {
        val legacyOwner = DataOwner.legacyAccountLocalId(TEST_UID)
        val task = task(ownerId = legacyOwner, remoteId = "legacy-task")
        database.dailyTaskDao().insert(task)
        val store = LocalDataOwnerStore(database)

        val localOwner = store.resolve(preferredFirebaseUid = TEST_UID)
        store.bindToAccount(localOwner, TEST_UID)

        assertEquals(legacyOwner, localOwner)
        assertTrue(
            database.dailyTaskDao().getByRemoteId(legacyOwner, task.remoteId)?.syncPending == true,
        )
        assertEquals(legacyOwner, store.resolve())
    }

    @Test
    fun reconnectingTheSameAccountDoesNotMarkSyncedRowsPendingAgain() = runBlocking {
        val task = task(ownerId = GUEST_OWNER_ID, remoteId = "local-task")
        database.dailyTaskDao().insert(task)
        val store = LocalDataOwnerStore(database)
        val localOwner = store.resolve()
        store.bindToAccount(localOwner, TEST_UID)
        database.dailyTaskDao().markSynced(localOwner, task.remoteId, task.updatedAtEpochMillis)

        store.bindToAccount(localOwner, TEST_UID)

        assertFalse(
            database.dailyTaskDao().getByRemoteId(localOwner, task.remoteId)?.syncPending == true,
        )
    }

    private fun task(ownerId: String, remoteId: String) = DailyTaskEntity(
        ownerId = ownerId,
        remoteId = remoteId,
        category = "WORK",
        title = "User task",
        isFinished = false,
        createdDayKey = 20260905,
        completedDayKey = null,
        linkedGoalId = null,
    )

    private companion object {
        const val TEST_UID = "test-account"
    }
}
