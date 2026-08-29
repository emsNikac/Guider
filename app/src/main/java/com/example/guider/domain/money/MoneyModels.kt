package com.example.guider.domain.money

import androidx.compose.runtime.Immutable

@Immutable
data class Spending(
    val id: Long,
    val title: String,
    val amountMinor: Long,
    val createdAtEpochMillis: Long,
)

@Immutable
data class MoneyLedger(
    val spendings: List<Spending> = emptyList(),
    val periodStartDayKey: Int? = null,
    val totalMinor: Long = 0L,
)
