package com.example.guider.domain.money

import kotlinx.coroutines.flow.StateFlow

interface MoneyRepository {
    val ledger: StateFlow<MoneyLedger>

    fun addSpending(
        title: String,
        amountMinor: Long,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): Spending

    fun editSpending(spendingId: Long, title: String, amountMinor: Long)

    fun deleteSpending(spendingId: Long)

    fun restart(dayKey: Int)
}
