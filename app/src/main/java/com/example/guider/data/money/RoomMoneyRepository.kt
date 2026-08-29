package com.example.guider.data.money

import androidx.room.withTransaction
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.MoneyLedgerRecord
import com.example.guider.data.database.MoneyStateEntity
import com.example.guider.data.database.SpendingEntity
import com.example.guider.data.database.toModel
import com.example.guider.domain.money.MoneyLedger
import com.example.guider.domain.money.MoneyRepository
import com.example.guider.domain.money.Spending
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoomMoneyRepository private constructor(
    private val database: GuiderDatabase,
    scope: CoroutineScope,
    initialLedger: MoneyLedger,
) : MoneyRepository {
    private val dao = database.moneyDao()

    override val ledger: StateFlow<MoneyLedger> = dao.observeLedger()
        .map { record -> record.toModel() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialLedger)

    override suspend fun addSpending(
        title: String,
        amountMinor: Long,
        createdAtEpochMillis: Long,
    ): Spending = database.withTransaction {
        require(title.isNotBlank())
        require(amountMinor > 0L)
        dao.setPeriodStartIfMissing(DayKeys.today(createdAtEpochMillis))
        val entity = SpendingEntity(
            title = title.trim(),
            amountMinor = amountMinor,
            createdAtEpochMillis = createdAtEpochMillis,
        )
        entity.copy(id = dao.insertSpending(entity)).toModel()
    }

    override suspend fun editSpending(spendingId: Long, title: String, amountMinor: Long) {
        require(title.isNotBlank())
        require(amountMinor > 0L)
        dao.editSpending(spendingId, title.trim(), amountMinor)
    }

    override suspend fun deleteSpending(spendingId: Long) {
        dao.deleteSpending(spendingId)
    }

    override suspend fun restart(dayKey: Int) {
        database.withTransaction {
            dao.clearSpendings()
            dao.setState(MoneyStateEntity(periodStartDayKey = dayKey))
        }
    }

    companion object {
        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomMoneyRepository =
            RoomMoneyRepository(
                database = database,
                scope = scope,
                initialLedger = database.moneyDao().getLedger().toModel(),
            )
    }
}
