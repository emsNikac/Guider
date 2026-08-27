package com.example.guider.domain.money

data class Spending(
    val id: Long,
    val title: String,
    val amountMinor: Long,
    val createdAtEpochMillis: Long,
)

data class MoneyLedger(
    val spendings: List<Spending> = emptyList(),
    val periodStartDayKey: Int? = null,
)
