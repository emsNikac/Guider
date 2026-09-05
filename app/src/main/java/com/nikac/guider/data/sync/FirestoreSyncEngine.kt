package com.nikac.guider.data.sync

import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.domain.settings.ThemeMode
import com.nikac.guider.domain.sync.CloudSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

internal class FirestoreSyncEngine(
    private val database: GuiderDatabase,
    private val firestore: FirebaseFirestore,
    private val firebaseUid: String,
    private val localOwnerId: String,
    private val applicationScope: CoroutineScope,
    private val onStatusChanged: (CloudSyncStatus) -> Unit,
    private val onThemeRestored: suspend (ThemeMode) -> Unit,
) {
    private val engineJob = SupervisorJob(applicationScope.coroutineContext[Job])
    private val engineScope = CoroutineScope(applicationScope.coroutineContext + engineJob)
    private val uploadRequests = Channel<Unit>(Channel.CONFLATED)
    private val synchronizationMutex = Mutex()
    private var sessionJob: Job? = null
    private var listener: ListenerRegistration? = null
    private val userDocument = firestore.collection(USERS).document(firebaseUid)
    private val syncDocument = userDocument.collection(STATE).document(SYNC_STATE)

    fun start() {
        if (sessionJob != null) return
        onStatusChanged(CloudSyncStatus.SYNCING)
        sessionJob = engineScope.launch {
            attachRemoteListener()
            synchronize()
            for (request in uploadRequests) {
                uploadPending()
            }
        }
    }

    fun requestUpload() {
        uploadRequests.trySend(Unit)
    }

    suspend fun synchronizeNow() {
        synchronize()
    }

    fun saveTheme(mode: ThemeMode) {
        if (sessionJob == null) return
        engineScope.launch {
            runSyncOperation {
                val now = System.currentTimeMillis()
                val batch = firestore.batch()
                batch.set(
                    userDocument.collection(PREFERENCES).document(APP_PREFERENCES),
                    mapOf(
                        "themeMode" to mode.storedValue,
                        "updatedAtEpochMillis" to now,
                        "schemaVersion" to CLOUD_SCHEMA_VERSION,
                    ),
                )
                touchSyncState(batch, now)
                batch.commit().await()
                onStatusChanged(CloudSyncStatus.SYNCED)
            }
        }
    }

    fun close() {
        listener?.remove()
        listener = null
        sessionJob?.cancel()
        sessionJob = null
        engineJob.cancel()
    }

    private fun attachRemoteListener() {
        var deliveredInitialSnapshot = false
        listener = syncDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onStatusChanged(if (error.isOffline()) CloudSyncStatus.OFFLINE else CloudSyncStatus.FAILED)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (!deliveredInitialSnapshot) {
                deliveredInitialSnapshot = true
                return@addSnapshotListener
            }
            engineScope.launch { synchronizeFromCloud() }
        }
    }

    private suspend fun synchronize() = synchronizationMutex.withLock {
        runSyncOperation {
            val result = FirestoreRemoteMerge(
                database = database,
                userDocument = userDocument,
                localOwnerId = localOwnerId,
            ).pullAndApply()
            result.themeMode?.let { onThemeRestored(it) }
            uploadPendingLocked()
            onStatusChanged(if (result.fromCache) CloudSyncStatus.OFFLINE else CloudSyncStatus.SYNCED)
        }
    }

    private suspend fun synchronizeFromCloud() = synchronizationMutex.withLock {
        runSyncOperation {
            val result = FirestoreRemoteMerge(
                database = database,
                userDocument = userDocument,
                localOwnerId = localOwnerId,
            ).pullAndApply()
            result.themeMode?.let { onThemeRestored(it) }
            onStatusChanged(if (result.fromCache) CloudSyncStatus.OFFLINE else CloudSyncStatus.SYNCED)
        }
    }

    private suspend fun uploadPending() = synchronizationMutex.withLock {
        runSyncOperation { uploadPendingLocked() }
    }

    private suspend fun uploadPendingLocked() {
        val writes = collectPendingWrites()
        if (writes.isEmpty()) {
            onStatusChanged(CloudSyncStatus.SYNCED)
            return
        }

        writes.chunked(MAX_DATA_WRITES_PER_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { write -> batch.set(write.reference, write.data) }
            touchSyncState(batch, System.currentTimeMillis())
            batch.commit().await()
            database.withTransaction {
                chunk.forEach { write -> write.markSynced() }
            }
        }
        onStatusChanged(CloudSyncStatus.SYNCED)
    }

    private suspend fun collectPendingWrites(): List<CloudWrite> {
        val writes = ArrayList<CloudWrite>()
        val goalDao = database.goalDao()
        val taskDao = database.dailyTaskDao()
        val habitDao = database.habitDao()
        val sleepDao = database.sleepDao()
        val moneyDao = database.moneyDao()

        goalDao.getPending(localOwnerId).forEach { goal ->
            writes += CloudWrite(
                reference = userDocument.collection(GOALS).document(goal.remoteId),
                data = mapOf(
                    "title" to goal.title,
                    "type" to goal.type,
                    "createdDayKey" to goal.createdDayKey,
                    "achievedDayKey" to goal.achievedDayKey,
                    "startDayKey" to goal.startDayKey,
                    "endDayKey" to goal.endDayKey,
                    "updatedAtEpochMillis" to goal.updatedAtEpochMillis,
                    "archivedAtEpochMillis" to goal.archivedAtEpochMillis,
                    "deletedAtEpochMillis" to goal.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    goalDao.markSynced(localOwnerId, goal.remoteId, goal.updatedAtEpochMillis)
                },
            )
        }

        taskDao.getPending(localOwnerId).forEach { task ->
            val linkedGoalRemoteId = task.linkedGoalId?.let { goalDao.getById(localOwnerId, it)?.remoteId }
            writes += CloudWrite(
                reference = userDocument.collection(DAILY_TASKS).document(task.remoteId),
                data = mapOf(
                    "category" to task.category,
                    "title" to task.title,
                    "isFinished" to task.isFinished,
                    "createdDayKey" to task.createdDayKey,
                    "completedDayKey" to task.completedDayKey,
                    "linkedGoalRemoteId" to linkedGoalRemoteId,
                    "updatedAtEpochMillis" to task.updatedAtEpochMillis,
                    "archivedAtEpochMillis" to task.archivedAtEpochMillis,
                    "deletedAtEpochMillis" to task.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    taskDao.markSynced(localOwnerId, task.remoteId, task.updatedAtEpochMillis)
                },
            )
        }

        habitDao.getPendingHabits(localOwnerId).forEach { habit ->
            val linkedGoalRemoteId = habit.linkedGoalId?.let { goalDao.getById(localOwnerId, it)?.remoteId }
            writes += CloudWrite(
                reference = userDocument.collection(HABITS).document(habit.remoteId),
                data = mapOf(
                    "name" to habit.name,
                    "colorHue" to habit.colorHue.toDouble(),
                    "linkedGoalRemoteId" to linkedGoalRemoteId,
                    "activeStartDayKey" to habit.activeStartDayKey,
                    "activeEndDayKey" to habit.activeEndDayKey,
                    "scheduledWeekdays" to habitDao.getWeekdays(habit.id),
                    "updatedAtEpochMillis" to habit.updatedAtEpochMillis,
                    "deletedAtEpochMillis" to habit.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    habitDao.markHabitSynced(localOwnerId, habit.remoteId, habit.updatedAtEpochMillis)
                },
            )
        }

        habitDao.getPendingCompletions(localOwnerId).forEach { completion ->
            val habitRemoteId = habitDao.getById(localOwnerId, completion.habitId)?.remoteId
                ?: return@forEach
            writes += CloudWrite(
                reference = userDocument.collection(HABIT_COMPLETIONS).document(completion.remoteId),
                data = mapOf(
                    "habitRemoteId" to habitRemoteId,
                    "dayKey" to completion.dayKey,
                    "weekday" to completion.weekday,
                    "updatedAtEpochMillis" to completion.updatedAtEpochMillis,
                    "deletedAtEpochMillis" to completion.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    habitDao.markCompletionSynced(completion.remoteId, completion.updatedAtEpochMillis)
                },
            )
        }

        sleepDao.getPendingSession(localOwnerId)?.let { session ->
            writes += CloudWrite(
                reference = userDocument.collection(STATE).document(ACTIVE_SLEEP),
                data = mapOf(
                    "activatedAtEpochMillis" to session.activatedAtEpochMillis,
                    "sleepStartsAtEpochMillis" to session.sleepStartsAtEpochMillis,
                    "updatedAtEpochMillis" to session.updatedAtEpochMillis,
                    "deletedAtEpochMillis" to session.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = { sleepDao.markSessionSynced(localOwnerId, session.updatedAtEpochMillis) },
            )
        }

        sleepDao.getPendingRecords(localOwnerId).forEach { record ->
            writes += CloudWrite(
                reference = userDocument.collection(SLEEP_RECORDS).document(record.remoteId),
                data = mapOf(
                    "activatedAtEpochMillis" to record.activatedAtEpochMillis,
                    "sleepStartsAtEpochMillis" to record.sleepStartsAtEpochMillis,
                    "endedAtEpochMillis" to record.endedAtEpochMillis,
                    "updatedAtEpochMillis" to record.updatedAtEpochMillis,
                    "deletedAtEpochMillis" to record.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    sleepDao.markRecordSynced(localOwnerId, record.remoteId, record.updatedAtEpochMillis)
                },
            )
        }

        moneyDao.getPendingPeriods(localOwnerId).forEach { period ->
            writes += CloudWrite(
                reference = userDocument.collection(MONEY_PERIODS).document(period.remoteId),
                data = mapOf(
                    "startDayKey" to period.startDayKey,
                    "endDayKey" to period.endDayKey,
                    "updatedAtEpochMillis" to period.updatedAtEpochMillis,
                    "deletedAtEpochMillis" to period.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    moneyDao.markPeriodSynced(localOwnerId, period.remoteId, period.updatedAtEpochMillis)
                },
            )
        }

        moneyDao.getPendingSpendings(localOwnerId).forEach { spending ->
            writes += CloudWrite(
                reference = userDocument.collection(SPENDINGS).document(spending.remoteId),
                data = mapOf(
                    "periodRemoteId" to spending.periodRemoteId,
                    "title" to spending.title,
                    "amountMinor" to spending.amountMinor,
                    "createdAtEpochMillis" to spending.createdAtEpochMillis,
                    "updatedAtEpochMillis" to spending.updatedAtEpochMillis,
                    "deletedAtEpochMillis" to spending.deletedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = {
                    moneyDao.markSpendingSynced(localOwnerId, spending.remoteId, spending.updatedAtEpochMillis)
                },
            )
        }

        moneyDao.getPendingState(localOwnerId)?.let { state ->
            writes += CloudWrite(
                reference = userDocument.collection(STATE).document(MONEY_STATE),
                data = mapOf(
                    "currentPeriodRemoteId" to state.currentPeriodRemoteId,
                    "periodStartDayKey" to state.periodStartDayKey,
                    "updatedAtEpochMillis" to state.updatedAtEpochMillis,
                    "schemaVersion" to CLOUD_SCHEMA_VERSION,
                ),
                markSynced = { moneyDao.markStateSynced(localOwnerId, state.updatedAtEpochMillis) },
            )
        }
        return writes
    }

    private fun touchSyncState(batch: com.google.firebase.firestore.WriteBatch, now: Long) {
        batch.set(
            syncDocument,
            mapOf(
                "updatedAt" to FieldValue.serverTimestamp(),
                "clientUpdatedAtEpochMillis" to now,
                "schemaVersion" to CLOUD_SCHEMA_VERSION,
            ),
            SetOptions.merge(),
        )
    }

    private suspend fun runSyncOperation(block: suspend () -> Unit) {
        onStatusChanged(CloudSyncStatus.SYNCING)
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onStatusChanged(if (error.isOffline()) CloudSyncStatus.OFFLINE else CloudSyncStatus.FAILED)
        }
    }

    private data class CloudWrite(
        val reference: DocumentReference,
        val data: Map<String, Any?>,
        val markSynced: suspend () -> Unit,
    )

    companion object {
        const val CLOUD_SCHEMA_VERSION = 1
        const val USERS = "users"
        const val DAILY_TASKS = "dailyTasks"
        const val GOALS = "goals"
        const val HABITS = "habits"
        const val HABIT_COMPLETIONS = "habitCompletions"
        const val SLEEP_RECORDS = "sleepRecords"
        const val MONEY_PERIODS = "moneyPeriods"
        const val SPENDINGS = "spendings"
        const val STATE = "state"
        const val ACTIVE_SLEEP = "activeSleep"
        const val MONEY_STATE = "money"
        const val SYNC_STATE = "sync"
        const val PREFERENCES = "preferences"
        const val APP_PREFERENCES = "app"
        const val MAX_DATA_WRITES_PER_BATCH = 400
    }
}

private fun Exception.isOffline(): Boolean =
    this is FirebaseFirestoreException &&
        (code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
