package com.nikac.guider.data.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.nikac.guider.data.database.GUEST_OWNER_ID
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.AppMetadataEntity
import com.nikac.guider.domain.settings.ThemeMode
import com.nikac.guider.domain.sync.CloudSyncStatus
import com.nikac.guider.domain.sync.DataOwner
import com.nikac.guider.domain.sync.UserDataSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class FirebaseUserDataSync(
    context: Context,
    private val database: GuiderDatabase,
    private val applicationScope: CoroutineScope,
    private val awaitDatabaseReady: suspend () -> Unit,
) : UserDataSync {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val mutableOwner = MutableStateFlow(DataOwner.Guest)
    override val owner = mutableOwner.asStateFlow()
    private val mutableStatus = MutableStateFlow(CloudSyncStatus.LOCAL_ONLY)
    override val status = mutableStatus.asStateFlow()
    private val mutableRestoredThemes = MutableSharedFlow<ThemeMode>(extraBufferCapacity = 1)
    override val restoredThemes = mutableRestoredThemes.asSharedFlow()
    private var engine: FirestoreSyncEngine? = null
    private val activationMutex = Mutex()

    override suspend fun activateGuest() = activationMutex.withLock {
        awaitDatabaseReady()
        engine?.close()
        engine = null
        workManager.cancelUniqueWork(CloudSyncWorker.UNIQUE_WORK_NAME)
        mutableOwner.value = DataOwner.Guest
        mutableStatus.value = CloudSyncStatus.LOCAL_ONLY
    }

    override suspend fun activateAccount(
        firebaseUid: String,
        migrateGuestData: Boolean,
    ) = activationMutex.withLock {
        require(firebaseUid.isNotBlank())
        awaitDatabaseReady()
        engine?.close()
        engine = null
        val accountOwner = DataOwner.account(firebaseUid)
        val prePartitionDataPending =
            database.appMetadataDao().getValue(PRE_PARTITION_DATA_PENDING) == "1"
        if (migrateGuestData || prePartitionDataPending) {
            migrateGuestData(accountOwner.localId)
            database.appMetadataDao().set(
                AppMetadataEntity(PRE_PARTITION_DATA_PENDING, "migrated:$firebaseUid"),
            )
        }
        mutableOwner.value = accountOwner

        val firebaseApp = FirebaseApp.getInstance()
        engine = FirestoreSyncEngine(
            database = database,
            firestore = FirebaseFirestore.getInstance(firebaseApp),
            firebaseUid = firebaseUid,
            localOwnerId = accountOwner.localId,
            applicationScope = applicationScope,
            onStatusChanged = { mutableStatus.value = it },
            onThemeRestored = { mutableRestoredThemes.emit(it) },
        ).also(FirestoreSyncEngine::start)
        scheduleBackgroundSync()
    }

    override fun requestUpload() {
        engine?.requestUpload()
    }

    override suspend fun syncNow() {
        engine?.synchronizeNow()
    }

    override fun saveTheme(mode: ThemeMode) {
        engine?.saveTheme(mode)
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            CloudSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private suspend fun migrateGuestData(accountOwnerId: String) {
        if (accountOwnerId == GUEST_OWNER_ID) return
        database.withTransaction {
            val ownership = database.ownershipDao()
            val sleep = database.sleepDao()
            val money = database.moneyDao()
            val now = System.currentTimeMillis()

            val guestSession = sleep.getActiveSession(GUEST_OWNER_ID)
            val accountSession = sleep.getActiveSession(accountOwnerId)
            when {
                guestSession == null -> Unit
                accountSession == null -> ownership.moveActiveSleep(GUEST_OWNER_ID, accountOwnerId, now)
                guestSession.activatedAtEpochMillis > accountSession.activatedAtEpochMillis -> {
                    ownership.hardDeleteActiveSleep(accountOwnerId)
                    ownership.moveActiveSleep(GUEST_OWNER_ID, accountOwnerId, now)
                }
                else -> ownership.hardDeleteActiveSleep(GUEST_OWNER_ID)
            }

            val guestMoneyState = money.getState(GUEST_OWNER_ID)
            val accountMoneyState = money.getState(accountOwnerId)
            ownership.moveMoneyPeriods(GUEST_OWNER_ID, accountOwnerId, now)
            ownership.moveSpendings(GUEST_OWNER_ID, accountOwnerId, now)
            when {
                guestMoneyState == null -> Unit
                accountMoneyState == null -> ownership.moveMoneyState(GUEST_OWNER_ID, accountOwnerId, now)
                else -> ownership.hardDeleteMoneyState(GUEST_OWNER_ID)
            }

            ownership.moveGoals(GUEST_OWNER_ID, accountOwnerId, now)
            ownership.moveTasks(GUEST_OWNER_ID, accountOwnerId, now)
            ownership.moveHabits(GUEST_OWNER_ID, accountOwnerId, now)
            ownership.markMovedCompletions(accountOwnerId, now)
            ownership.moveSleepRecords(GUEST_OWNER_ID, accountOwnerId, now)
        }
    }

    private companion object {
        const val PRE_PARTITION_DATA_PENDING = "pre_partition_data_pending"
    }
}
