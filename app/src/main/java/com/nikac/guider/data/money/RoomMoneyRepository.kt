package com.nikac.guider.data.money

import androidx.room.withTransaction
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.MoneyStateEntity
import com.nikac.guider.data.database.MoneyLedgerRow
import com.nikac.guider.data.database.MoneyPeriodEntity
import com.nikac.guider.data.database.SpendingEntity
import com.nikac.guider.data.database.toModel
import com.nikac.guider.data.stateInWhileSubscribed
import com.nikac.guider.domain.money.MoneyLedger
import com.nikac.guider.domain.money.MoneyRepository
import com.nikac.guider.domain.money.Spending
import com.nikac.guider.domain.collections.toImmutableSnapshot
import com.nikac.guider.domain.time.DayKeys
import com.nikac.guider.domain.sync.DataOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomMoneyRepository private constructor(
    private val database: GuiderDatabase,
    private val owner: StateFlow<DataOwner>,
    private val onDataChanged: () -> Unit,
    override val ledger: StateFlow<MoneyLedger>,
) : MoneyRepository {
    private val dao = database.moneyDao()

    override suspend fun addSpending(
        title: String,
        amountMinor: Long,
        createdAtEpochMillis: Long,
    ): Spending = database.withTransaction {
        require(title.isNotBlank())
        require(amountMinor > 0L)
        val currentOwner = owner.value
        val dayKey = DayKeys.today(createdAtEpochMillis)
        var state = ensureState(currentOwner)
        if (state.periodStartDayKey == null) {
            val now = System.currentTimeMillis()
            dao.upsertPeriod(
                MoneyPeriodEntity(
                    remoteId = state.currentPeriodRemoteId,
                    ownerId = currentOwner.localId,
                    startDayKey = dayKey,
                    endDayKey = null,
                    updatedAtEpochMillis = now,
                    syncPending = currentOwner.usesCloud,
                ),
            )
            state = state.copy(
                periodStartDayKey = dayKey,
                updatedAtEpochMillis = now,
                syncPending = currentOwner.usesCloud,
            )
            dao.setState(state)
        }
        val entity = SpendingEntity(
            ownerId = currentOwner.localId,
            periodRemoteId = state.currentPeriodRemoteId,
            title = title.trim(),
            amountMinor = amountMinor,
            createdAtEpochMillis = createdAtEpochMillis,
            syncPending = currentOwner.usesCloud,
        )
        entity.copy(id = dao.insertSpending(entity)).toModel()
    }.also { notifyCloud(owner.value) }

    override suspend fun editSpending(spendingId: Long, title: String, amountMinor: Long) {
        require(title.isNotBlank())
        require(amountMinor > 0L)
        val currentOwner = owner.value
        dao.editSpending(
            ownerId = currentOwner.localId,
            spendingId = spendingId,
            title = title.trim(),
            amountMinor = amountMinor,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = currentOwner.usesCloud,
        )
        notifyCloud(currentOwner)
    }

    override suspend fun deleteSpending(spendingId: Long) {
        val currentOwner = owner.value
        dao.softDeleteSpending(
            ownerId = currentOwner.localId,
            spendingId = spendingId,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = currentOwner.usesCloud,
        )
        notifyCloud(currentOwner)
    }

    override suspend fun restart(dayKey: Int) {
        val currentOwner = owner.value
        database.withTransaction {
            val previous = ensureState(currentOwner)
            val now = System.currentTimeMillis()
            dao.closePeriod(
                ownerId = currentOwner.localId,
                remoteId = previous.currentPeriodRemoteId,
                endDayKey = dayKey,
                updatedAtEpochMillis = now,
                syncPending = currentOwner.usesCloud,
            )
            val nextPeriod = MoneyPeriodEntity(
                ownerId = currentOwner.localId,
                startDayKey = dayKey,
                endDayKey = null,
                updatedAtEpochMillis = now,
                syncPending = currentOwner.usesCloud,
            )
            dao.upsertPeriod(nextPeriod)
            dao.setState(
                MoneyStateEntity(
                    ownerId = currentOwner.localId,
                    currentPeriodRemoteId = nextPeriod.remoteId,
                    periodStartDayKey = dayKey,
                    updatedAtEpochMillis = now,
                    syncPending = currentOwner.usesCloud,
                ),
            )
        }
        notifyCloud(currentOwner)
    }

    private suspend fun ensureState(currentOwner: DataOwner): MoneyStateEntity {
        dao.getState(currentOwner.localId)?.let { return it }
        val period = MoneyPeriodEntity(
            ownerId = currentOwner.localId,
            startDayKey = null,
            endDayKey = null,
            syncPending = currentOwner.usesCloud,
        )
        dao.upsertPeriod(period)
        return MoneyStateEntity(
            ownerId = currentOwner.localId,
            currentPeriodRemoteId = period.remoteId,
            periodStartDayKey = null,
            syncPending = currentOwner.usesCloud,
        ).also { dao.setState(it) }
    }

    private fun notifyCloud(changedOwner: DataOwner) {
        if (changedOwner == owner.value && changedOwner.usesCloud) onDataChanged()
    }

    companion object {
        suspend fun create(
            database: GuiderDatabase,
            scope: CoroutineScope,
            owner: StateFlow<DataOwner>,
            onDataChanged: () -> Unit,
        ): RoomMoneyRepository {
            val dao = database.moneyDao()
            val ledger = owner.flatMapLatest { activeOwner -> dao.observeLedger(activeOwner.localId) }
                .map { rows -> rows.toMoneyLedger() }
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomMoneyRepository(
                database = database,
                owner = owner,
                onDataChanged = onDataChanged,
                ledger = ledger,
            )
        }
    }
}

internal fun List<MoneyLedgerRow>.toMoneyLedger(): MoneyLedger {
    val spendings = mapNotNull(MoneyLedgerRow::toSpending)
    return MoneyLedger(
        spendings = spendings.toImmutableSnapshot(),
        periodStartDayKey = firstOrNull()?.periodStartDayKey,
        totalMinor = spendings.sumOf(Spending::amountMinor),
    )
}

private fun MoneyLedgerRow.toSpending(): Spending? {
    val id = spendingId ?: return null
    return Spending(
        id = id,
        title = spendingTitle ?: return null,
        amountMinor = spendingAmountMinor ?: return null,
        createdAtEpochMillis = spendingCreatedAtEpochMillis ?: return null,
    )
}
