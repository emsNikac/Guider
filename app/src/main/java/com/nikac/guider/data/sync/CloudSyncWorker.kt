package com.nikac.guider.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nikac.guider.GuiderApplication
import com.nikac.guider.domain.sync.CloudSyncStatus

class CloudSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val firebaseUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }
            .getOrNull()
            ?: return Result.success()
        val sync = (applicationContext as GuiderApplication).userDataSync
        return runCatching {
            sync.activateAccount(firebaseUid, migrateGuestData = false)
            sync.syncNow()
            when (sync.status.value) {
                CloudSyncStatus.SYNCED -> Result.success()
                CloudSyncStatus.LOCAL_ONLY -> Result.success()
                CloudSyncStatus.SYNCING,
                CloudSyncStatus.OFFLINE,
                CloudSyncStatus.FAILED,
                -> Result.retry()
            }
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "guider_cloud_progress_sync"
    }
}
