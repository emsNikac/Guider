package com.nikac.guider.domain.money

import androidx.compose.runtime.Immutable
import com.nikac.guider.domain.collections.ImmutableListSnapshot

@Immutable
data class Spending(
    val id: Long,
    val title: String,
    val amountMinor: Long,
    val createdAtEpochMillis: Long,
)

@Immutable
data class MoneyLedger(
    val spendings: ImmutableListSnapshot<Spending> = ImmutableListSnapshot(emptyList()),
    val periodStartDayKey: Int? = null,
    val totalMinor: Long = 0L,
)
