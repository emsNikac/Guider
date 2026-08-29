package com.example.guider.data.money

import androidx.room.withTransaction
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.MoneyStateEntity
import com.example.guider.data.database.SpendingEntity
import com.example.guider.data.database.toModel
import com.example.guider.data.stateInWhileSubscribed
import com.example.guider.domain.money.MoneyLedger
import com.example.guider.domain.money.MoneyRepository
import com.example.guider.domain.money.Spending
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class RoomMoneyRepository private constructor(
    private val database: GuiderDatabase,
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
        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomMoneyRepository {
            val dao = database.moneyDao()
            val ledger = combine(
                dao.observeSpendings(),
                dao.observeLedgerSummary(),
            ) { spendingEntities, summary ->
                MoneyLedger(
                    spendings = spendingEntities.map { it.toModel() },
                    periodStartDayKey = summary?.periodStartDayKey,
                    totalMinor = summary?.totalMinor ?: 0L,
                )
            }
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomMoneyRepository(
                database = database,
                ledger = ledger,
            )
        }
    }
}
