package com.nikac.guider.domain.money

import kotlinx.coroutines.flow.StateFlow

interface MoneyRepository {
    val ledger: StateFlow<MoneyLedger>

    suspend fun addSpending(
        title: String,
        amountMinor: Long,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): Spending

    suspend fun editSpending(spendingId: Long, title: String, amountMinor: Long)

    suspend fun deleteSpending(spendingId: Long)

    suspend fun restart(dayKey: Int)
}
