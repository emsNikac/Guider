package com.nikac.guider.data.sync

import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.nikac.guider.data.database.ActiveSleepSessionEntity
import com.nikac.guider.data.database.DailyTaskEntity
import com.nikac.guider.data.database.GoalEntity
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.HabitCompletionEntity
import com.nikac.guider.data.database.HabitEntity
import com.nikac.guider.data.database.HabitWeekdayEntity
import com.nikac.guider.data.database.MoneyPeriodEntity
import com.nikac.guider.data.database.MoneyStateEntity
import com.nikac.guider.data.database.SleepRecordEntity
import com.nikac.guider.data.database.SpendingEntity
import com.nikac.guider.domain.goals.GoalType
import com.nikac.guider.domain.habits.HabitWeekday
import com.nikac.guider.domain.settings.ThemeMode
import com.nikac.guider.models.TaskCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

internal data class RemotePullResult(
    val themeMode: ThemeMode?,
    val fromCache: Boolean,
)

internal class FirestoreRemoteMerge(
    private val database: GuiderDatabase,
    private val userDocument: DocumentReference,
    private val localOwnerId: String,
) {
    suspend fun pullAndApply(): RemotePullResult {
        val bundle = fetchRemoteBundle()
        database.withTransaction { apply(bundle) }
        return RemotePullResult(
            themeMode = bundle.preferences.takeIf(DocumentSnapshot::exists)
                ?.getString("themeMode")
                ?.let(ThemeMode::fromStoredValue),
            fromCache = bundle.isFromCache,
        )
    }

    private suspend fun fetchRemoteBundle(): RemoteBundle = coroutineScope {
        val goals = async { collection(FirestoreSyncEngine.GOALS) }
        val tasks = async { collection(FirestoreSyncEngine.DAILY_TASKS) }
        val habits = async { collection(FirestoreSyncEngine.HABITS) }
        val completions = async { collection(FirestoreSyncEngine.HABIT_COMPLETIONS) }
        val sleepRecords = async { collection(FirestoreSyncEngine.SLEEP_RECORDS) }
        val moneyPeriods = async { collection(FirestoreSyncEngine.MONEY_PERIODS) }
        val spendings = async { collection(FirestoreSyncEngine.SPENDINGS) }
        val activeSleep = async {
            userDocument.collection(FirestoreSyncEngine.STATE)
                .document(FirestoreSyncEngine.ACTIVE_SLEEP).get().await()
        }
        val moneyState = async {
            userDocument.collection(FirestoreSyncEngine.STATE)
                .document(FirestoreSyncEngine.MONEY_STATE).get().await()
        }
        val preferences = async {
            userDocument.collection(FirestoreSyncEngine.PREFERENCES)
                .document(FirestoreSyncEngine.APP_PREFERENCES).get().await()
        }
        RemoteBundle(
            goals = goals.await(),
            tasks = tasks.await(),
            habits = habits.await(),
            completions = completions.await(),
            sleepRecords = sleepRecords.await(),
            moneyPeriods = moneyPeriods.await(),
            spendings = spendings.await(),
            activeSleep = activeSleep.await(),
            moneyState = moneyState.await(),
            preferences = preferences.await(),
        )
    }

    private suspend fun apply(bundle: RemoteBundle) {
        val goalDao = database.goalDao()
        val taskDao = database.dailyTaskDao()
        val habitDao = database.habitDao()
        val sleepDao = database.sleepDao()
        val moneyDao = database.moneyDao()

        bundle.goals.documents.forEach { document ->
            decodeGoal(document)?.let { remote ->
                val local = goalDao.getByRemoteId(localOwnerId, remote.remoteId)
                if (shouldApply(remote.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    goalDao.upsert(remote.copy(id = local?.id ?: 0L))
                }
            }
        }

        bundle.habits.documents.forEach { document ->
            decodeHabit(document)?.let { decoded ->
                val local = habitDao.getByRemoteId(localOwnerId, decoded.entity.remoteId)
                if (shouldApply(decoded.entity.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    val linkedGoalId = decoded.linkedGoalRemoteId?.let {
                        goalDao.getByRemoteId(localOwnerId, it)?.id
                    }
                    val entity = decoded.entity.copy(id = local?.id ?: 0L, linkedGoalId = linkedGoalId)
                    habitDao.upsertHabit(entity)
                    val localId = local?.id ?: habitDao.getByRemoteId(
                        localOwnerId,
                        entity.remoteId,
                    )?.id ?: return@let
                    habitDao.deleteWeekdays(localId)
                    habitDao.insertWeekdays(
                        decoded.weekdays.map { weekday -> HabitWeekdayEntity(localId, weekday.name) },
                    )
                }
            }
        }

        bundle.tasks.documents.forEach { document ->
            decodeTask(document)?.let { decoded ->
                val local = taskDao.getByRemoteId(localOwnerId, decoded.entity.remoteId)
                if (shouldApply(decoded.entity.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    val linkedGoalId = decoded.linkedGoalRemoteId?.let {
                        goalDao.getByRemoteId(localOwnerId, it)?.id
                    }
                    taskDao.upsert(
                        decoded.entity.copy(id = local?.id ?: 0L, linkedGoalId = linkedGoalId),
                    )
                }
            }
        }

        bundle.completions.documents.forEach { document ->
            decodeCompletion(document)?.let { decoded ->
                val habit = habitDao.getByRemoteId(localOwnerId, decoded.habitRemoteId)
                    ?: return@let
                val local = habitDao.getCompletion(habit.id, decoded.dayKey)
                if (shouldApply(decoded.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    habitDao.upsertCompletion(
                        HabitCompletionEntity(
                            habitId = habit.id,
                            dayKey = decoded.dayKey,
                            weekday = decoded.weekday.name,
                            remoteId = document.id,
                            updatedAtEpochMillis = decoded.updatedAtEpochMillis,
                            deletedAtEpochMillis = decoded.deletedAtEpochMillis,
                            syncPending = false,
                        ),
                    )
                }
            }
        }

        if (bundle.activeSleep.exists() && bundle.activeSleep.isSupported()) {
            decodeActiveSleep(bundle.activeSleep)?.let { remote ->
                val local = sleepDao.getActiveSession(localOwnerId)
                if (shouldApply(remote.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    sleepDao.setActiveSession(remote)
                }
            }
        }

        bundle.sleepRecords.documents.forEach { document ->
            decodeSleepRecord(document)?.let { remote ->
                val local = sleepDao.getRecordByRemoteId(localOwnerId, remote.remoteId)
                if (shouldApply(remote.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    sleepDao.upsertRecord(remote.copy(id = local?.id ?: 0L))
                }
            }
        }

        bundle.moneyPeriods.documents.forEach { document ->
            decodeMoneyPeriod(document)?.let { remote ->
                val local = moneyDao.getPeriod(localOwnerId, remote.remoteId)
                if (shouldApply(remote.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    moneyDao.upsertPeriod(remote)
                }
            }
        }

        bundle.spendings.documents.forEach { document ->
            decodeSpending(document)?.let { remote ->
                val local = moneyDao.getSpending(localOwnerId, remote.remoteId)
                if (shouldApply(remote.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    moneyDao.upsertSpending(remote.copy(id = local?.id ?: 0L))
                }
            }
        }

        if (bundle.moneyState.exists() && bundle.moneyState.isSupported()) {
            decodeMoneyState(bundle.moneyState)?.let { remote ->
                val local = moneyDao.getState(localOwnerId)
                if (shouldApply(remote.updatedAtEpochMillis, local?.updatedAtEpochMillis, local?.syncPending)) {
                    moneyDao.setState(remote)
                }
            }
        }
    }

    private suspend fun collection(name: String): QuerySnapshot =
        userDocument.collection(name).get().await()

    private fun decodeGoal(document: DocumentSnapshot): GoalEntity? = document.decode {
        val type = requiredString("type").also(GoalType::valueOf)
        GoalEntity(
            ownerId = localOwnerId,
            remoteId = id,
            title = requiredString("title"),
            type = type,
            createdDayKey = requiredInt("createdDayKey"),
            achievedDayKey = optionalInt("achievedDayKey"),
            startDayKey = optionalInt("startDayKey"),
            endDayKey = optionalInt("endDayKey"),
            updatedAtEpochMillis = updateTime(),
            archivedAtEpochMillis = getLong("archivedAtEpochMillis"),
            deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
            syncPending = false,
        )
    }

    private fun decodeTask(document: DocumentSnapshot): DecodedTask? = document.decode {
        val category = requiredString("category").also(TaskCategory::valueOf)
        DecodedTask(
            entity = DailyTaskEntity(
                ownerId = localOwnerId,
                remoteId = id,
                category = category,
                title = requiredString("title"),
                isFinished = getBoolean("isFinished") ?: false,
                createdDayKey = requiredInt("createdDayKey"),
                completedDayKey = optionalInt("completedDayKey"),
                linkedGoalId = null,
                updatedAtEpochMillis = updateTime(),
                archivedAtEpochMillis = getLong("archivedAtEpochMillis"),
                deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
                syncPending = false,
            ),
            linkedGoalRemoteId = getString("linkedGoalRemoteId"),
        )
    }

    private fun decodeHabit(document: DocumentSnapshot): DecodedHabit? = document.decode {
        val weekdayNames = get("scheduledWeekdays") as? List<*> ?: emptyList<Any>()
        val weekdays = weekdayNames.mapNotNull { value ->
            (value as? String)?.let { runCatching { HabitWeekday.valueOf(it) }.getOrNull() }
        }.toSet().ifEmpty { HabitWeekday.entries.toSet() }
        DecodedHabit(
            entity = HabitEntity(
                ownerId = localOwnerId,
                remoteId = id,
                name = requiredString("name"),
                colorHue = (getDouble("colorHue") ?: 210.0).toFloat(),
                linkedGoalId = null,
                activeStartDayKey = optionalInt("activeStartDayKey"),
                activeEndDayKey = optionalInt("activeEndDayKey"),
                updatedAtEpochMillis = updateTime(),
                deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
                syncPending = false,
            ),
            linkedGoalRemoteId = getString("linkedGoalRemoteId"),
            weekdays = weekdays,
        )
    }

    private fun decodeCompletion(document: DocumentSnapshot): DecodedCompletion? = document.decode {
        DecodedCompletion(
            habitRemoteId = requiredString("habitRemoteId"),
            dayKey = requiredInt("dayKey"),
            weekday = HabitWeekday.valueOf(requiredString("weekday")),
            updatedAtEpochMillis = updateTime(),
            deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
        )
    }

    private fun decodeActiveSleep(document: DocumentSnapshot): ActiveSleepSessionEntity? =
        document.decode {
            ActiveSleepSessionEntity(
                ownerId = localOwnerId,
                activatedAtEpochMillis = requiredLong("activatedAtEpochMillis"),
                sleepStartsAtEpochMillis = requiredLong("sleepStartsAtEpochMillis"),
                updatedAtEpochMillis = updateTime(),
                deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
                syncPending = false,
            )
        }

    private fun decodeSleepRecord(document: DocumentSnapshot): SleepRecordEntity? = document.decode {
        SleepRecordEntity(
            ownerId = localOwnerId,
            remoteId = id,
            activatedAtEpochMillis = requiredLong("activatedAtEpochMillis"),
            sleepStartsAtEpochMillis = requiredLong("sleepStartsAtEpochMillis"),
            endedAtEpochMillis = requiredLong("endedAtEpochMillis"),
            updatedAtEpochMillis = updateTime(),
            deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
            syncPending = false,
        )
    }

    private fun decodeMoneyPeriod(document: DocumentSnapshot): MoneyPeriodEntity? = document.decode {
        MoneyPeriodEntity(
            remoteId = id,
            ownerId = localOwnerId,
            startDayKey = optionalInt("startDayKey"),
            endDayKey = optionalInt("endDayKey"),
            updatedAtEpochMillis = updateTime(),
            deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
            syncPending = false,
        )
    }

    private fun decodeSpending(document: DocumentSnapshot): SpendingEntity? = document.decode {
        SpendingEntity(
            ownerId = localOwnerId,
            remoteId = id,
            periodRemoteId = requiredString("periodRemoteId"),
            title = requiredString("title"),
            amountMinor = requiredLong("amountMinor"),
            createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
            updatedAtEpochMillis = updateTime(),
            deletedAtEpochMillis = getLong("deletedAtEpochMillis"),
            syncPending = false,
        )
    }

    private fun decodeMoneyState(document: DocumentSnapshot): MoneyStateEntity? = document.decode {
        MoneyStateEntity(
            ownerId = localOwnerId,
            currentPeriodRemoteId = requiredString("currentPeriodRemoteId"),
            periodStartDayKey = optionalInt("periodStartDayKey"),
            updatedAtEpochMillis = updateTime(),
            syncPending = false,
        )
    }

    private data class DecodedTask(
        val entity: DailyTaskEntity,
        val linkedGoalRemoteId: String?,
    )

    private data class DecodedHabit(
        val entity: HabitEntity,
        val linkedGoalRemoteId: String?,
        val weekdays: Set<HabitWeekday>,
    )

    private data class DecodedCompletion(
        val habitRemoteId: String,
        val dayKey: Int,
        val weekday: HabitWeekday,
        val updatedAtEpochMillis: Long,
        val deletedAtEpochMillis: Long?,
    )

    private data class RemoteBundle(
        val goals: QuerySnapshot,
        val tasks: QuerySnapshot,
        val habits: QuerySnapshot,
        val completions: QuerySnapshot,
        val sleepRecords: QuerySnapshot,
        val moneyPeriods: QuerySnapshot,
        val spendings: QuerySnapshot,
        val activeSleep: DocumentSnapshot,
        val moneyState: DocumentSnapshot,
        val preferences: DocumentSnapshot,
    ) {
        val isFromCache: Boolean
            get() = listOf(
                goals.metadata.isFromCache,
                tasks.metadata.isFromCache,
                habits.metadata.isFromCache,
                completions.metadata.isFromCache,
                sleepRecords.metadata.isFromCache,
                moneyPeriods.metadata.isFromCache,
                spendings.metadata.isFromCache,
                activeSleep.metadata.isFromCache,
                moneyState.metadata.isFromCache,
                preferences.metadata.isFromCache,
            ).any { it }
    }
}

private inline fun <T> DocumentSnapshot.decode(block: DocumentSnapshot.() -> T): T? {
    if (!exists() || !isSupported()) return null
    return runCatching(block).getOrNull()
}

private fun DocumentSnapshot.isSupported(): Boolean =
    (getLong("schemaVersion") ?: 1L) <= FirestoreSyncEngine.CLOUD_SCHEMA_VERSION

private fun DocumentSnapshot.requiredString(field: String): String =
    requireNotNull(getString(field)).also { require(it.isNotBlank()) }

private fun DocumentSnapshot.requiredLong(field: String): Long = requireNotNull(getLong(field))
private fun DocumentSnapshot.requiredInt(field: String): Int = Math.toIntExact(requiredLong(field))
private fun DocumentSnapshot.optionalInt(field: String): Int? = getLong(field)?.let(Math::toIntExact)
private fun DocumentSnapshot.updateTime(): Long = getLong("updatedAtEpochMillis") ?: 0L

private fun shouldApply(remote: Long, local: Long?, localPending: Boolean?): Boolean =
    local == null || remote > local || (remote == local && localPending != true)
