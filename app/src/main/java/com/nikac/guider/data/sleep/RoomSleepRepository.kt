package com.nikac.guider.data.sleep

import androidx.room.withTransaction
import com.nikac.guider.data.database.ActiveSleepSessionEntity
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.SleepRecordEntity
import com.nikac.guider.data.database.toModel
import com.nikac.guider.data.stateInWhileSubscribed
import com.nikac.guider.domain.sleep.ActiveSleepSession
import com.nikac.guider.domain.sleep.SleepCycleCalculator
import com.nikac.guider.domain.sleep.SleepRecord
import com.nikac.guider.domain.sleep.SleepRepository
import com.nikac.guider.domain.sync.DataOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomSleepRepository private constructor(
    private val database: GuiderDatabase,
    private val owner: StateFlow<DataOwner>,
    private val onDataChanged: () -> Unit,
    override val activeSession: StateFlow<ActiveSleepSession?>,
    override val history: StateFlow<List<SleepRecord>>,
) : SleepRepository {
    private val dao = database.sleepDao()

    override suspend fun startHibernation(activatedAtEpochMillis: Long): ActiveSleepSession =
        database.withTransaction {
            val currentOwner = owner.value
            dao.getActiveSession(currentOwner.localId)
                ?.takeIf { it.deletedAtEpochMillis == null }
                ?.toModel()
                ?.let { return@withTransaction it }
            val session = ActiveSleepSession(
                activatedAtEpochMillis = activatedAtEpochMillis,
                sleepStartsAtEpochMillis = SleepCycleCalculator.effectiveSleepStart(
                    activatedAtEpochMillis,
                ),
            )
            dao.setActiveSession(
                ActiveSleepSessionEntity(
                    ownerId = currentOwner.localId,
                    activatedAtEpochMillis = session.activatedAtEpochMillis,
                    sleepStartsAtEpochMillis = session.sleepStartsAtEpochMillis,
                    deletedAtEpochMillis = null,
                    syncPending = currentOwner.usesCloud,
                ),
            )
            session
        }.also { notifyCloud(owner.value) }

    override suspend fun finishHibernation(endedAtEpochMillis: Long): SleepRecord? =
        database.withTransaction {
            val currentOwner = owner.value
            val session = dao.getActiveSession(currentOwner.localId)
                ?.takeIf { it.deletedAtEpochMillis == null }
                ?.toModel()
                ?: return@withTransaction null
            val now = System.currentTimeMillis()
            dao.clearActiveSession(currentOwner.localId, now, currentOwner.usesCloud)
            val entity = SleepRecordEntity(
                ownerId = currentOwner.localId,
                activatedAtEpochMillis = session.activatedAtEpochMillis,
                sleepStartsAtEpochMillis = session.sleepStartsAtEpochMillis,
                endedAtEpochMillis = endedAtEpochMillis,
                updatedAtEpochMillis = now,
                syncPending = currentOwner.usesCloud,
            )
            val record = entity.copy(id = dao.insertRecord(entity)).toModel()
            record
        }.also { record -> if (record != null) notifyCloud(owner.value) }

    private fun notifyCloud(changedOwner: DataOwner) {
        if (changedOwner == owner.value && changedOwner.usesCloud) onDataChanged()
    }

    companion object {
        private const val MAX_HISTORY_RECORDS = 365

        suspend fun create(
            database: GuiderDatabase,
            scope: CoroutineScope,
            owner: StateFlow<DataOwner>,
            onDataChanged: () -> Unit,
        ): RoomSleepRepository {
            val dao = database.sleepDao()
            // Kept eager intentionally: notification and worker paths read activeSession.value
            // even while no Compose screen is collecting the flow.
            val activeSession = owner.flatMapLatest { activeOwner ->
                dao.observeActiveSession(activeOwner.localId)
            }
                .map { it?.toModel() }
                .distinctUntilChanged()
                .stateIn(scope)
            val history = owner.flatMapLatest { activeOwner ->
                dao.observeHistory(activeOwner.localId, MAX_HISTORY_RECORDS)
            }
                .map { records -> records.map(SleepRecordEntity::toModel) }
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomSleepRepository(
                database = database,
                owner = owner,
                onDataChanged = onDataChanged,
                activeSession = activeSession,
                history = history,
            )
        }
    }
}
