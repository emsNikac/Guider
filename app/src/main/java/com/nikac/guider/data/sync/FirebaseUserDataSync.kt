package com.nikac.guider.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.nikac.guider.data.database.GuiderDatabase
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
    private val localDataOwnerStore = LocalDataOwnerStore(database)

    override suspend fun activateGuest() = activationMutex.withLock {
        awaitDatabaseReady()
        engine?.close()
        engine = null
        workManager.cancelUniqueWork(CloudSyncWorker.UNIQUE_WORK_NAME)
        mutableOwner.value = DataOwner.local(localDataOwnerStore.resolve())
        mutableStatus.value = CloudSyncStatus.LOCAL_ONLY
    }

    override suspend fun activateAccount(firebaseUid: String) = activationMutex.withLock {
        require(firebaseUid.isNotBlank())
        awaitDatabaseReady()
        if (mutableOwner.value.firebaseUid == firebaseUid && engine != null) return@withLock
        engine?.close()
        engine = null
        val localOwnerId = localDataOwnerStore.resolve(firebaseUid)
        localDataOwnerStore.bindToAccount(localOwnerId, firebaseUid)
        val accountOwner = DataOwner.account(firebaseUid, localOwnerId)
        mutableOwner.value = accountOwner

        val firebaseApp = FirebaseApp.getInstance()
        engine = FirestoreSyncEngine(
            database = database,
            firestore = FirebaseFirestore.getInstance(firebaseApp),
            firebaseUid = firebaseUid,
            localOwnerId = localOwnerId,
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

}
