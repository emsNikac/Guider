package com.example.guider.data.sleep

import androidx.room.withTransaction
import com.example.guider.data.database.ActiveSleepSessionEntity
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.SleepRecordEntity
import com.example.guider.data.database.toModel
import com.example.guider.data.stateInWhileSubscribed
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepCycleCalculator
import com.example.guider.domain.sleep.SleepRecord
import com.example.guider.domain.sleep.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoomSleepRepository private constructor(
    private val database: GuiderDatabase,
    override val activeSession: StateFlow<ActiveSleepSession?>,
    override val history: StateFlow<List<SleepRecord>>,
) : SleepRepository {
    private val dao = database.sleepDao()

    override suspend fun startHibernation(activatedAtEpochMillis: Long): ActiveSleepSession =
        database.withTransaction {
            dao.getActiveSession()?.toModel()?.let { return@withTransaction it }
            val session = ActiveSleepSession(
                activatedAtEpochMillis = activatedAtEpochMillis,
                sleepStartsAtEpochMillis = SleepCycleCalculator.effectiveSleepStart(
                    activatedAtEpochMillis,
                ),
            )
            dao.setActiveSession(
                ActiveSleepSessionEntity(
                    activatedAtEpochMillis = session.activatedAtEpochMillis,
                    sleepStartsAtEpochMillis = session.sleepStartsAtEpochMillis,
                ),
            )
            session
        }

    override suspend fun finishHibernation(endedAtEpochMillis: Long): SleepRecord? =
        database.withTransaction {
            val session = dao.getActiveSession()?.toModel() ?: return@withTransaction null
            dao.clearActiveSession()
            val entity = SleepRecordEntity(
                activatedAtEpochMillis = session.activatedAtEpochMillis,
                sleepStartsAtEpochMillis = session.sleepStartsAtEpochMillis,
                endedAtEpochMillis = endedAtEpochMillis,
            )
            val record = entity.copy(id = dao.insertRecord(entity)).toModel()
            dao.trimHistory(MAX_HISTORY_RECORDS)
            record
        }

    companion object {
        private const val MAX_HISTORY_RECORDS = 365

        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomSleepRepository {
            val dao = database.sleepDao()
            // Kept eager intentionally: notification and worker paths read activeSession.value
            // even while no Compose screen is collecting the flow.
            val activeSession = dao.observeActiveSession()
                .map { it?.toModel() }
                .distinctUntilChanged()
                .stateIn(scope)
            val history = dao.observeHistory(MAX_HISTORY_RECORDS)
                .map { records -> records.map(SleepRecordEntity::toModel) }
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomSleepRepository(
                database = database,
                activeSession = activeSession,
                history = history,
            )
        }
    }
}
