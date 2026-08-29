package com.example.guider.data.sleep

import androidx.room.withTransaction
import com.example.guider.data.database.ActiveSleepSessionEntity
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.SleepRecordEntity
import com.example.guider.data.database.toModel
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepCycleCalculator
import com.example.guider.domain.sleep.SleepRecord
import com.example.guider.domain.sleep.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoomSleepRepository private constructor(
    private val database: GuiderDatabase,
    scope: CoroutineScope,
    initialActiveSession: ActiveSleepSession?,
    initialHistory: List<SleepRecord>,
) : SleepRepository {
    private val dao = database.sleepDao()

    override val activeSession: StateFlow<ActiveSleepSession?> = dao.observeActiveSession()
        .map { it?.toModel() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialActiveSession)

    override val history: StateFlow<List<SleepRecord>> = dao.observeHistory()
        .map { records -> records.map(SleepRecordEntity::toModel) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialHistory)

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

        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomSleepRepository =
            RoomSleepRepository(
                database = database,
                scope = scope,
                initialActiveSession = database.sleepDao().getActiveSession()?.toModel(),
                initialHistory = database.sleepDao().getHistory().map(SleepRecordEntity::toModel),
            )
    }
}
