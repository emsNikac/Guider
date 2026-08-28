package com.example.guider.domain.sleep

import androidx.compose.runtime.Immutable

@Immutable
data class SleepCycleSuggestion(
    val cycleCount: Int,
    val wakeAtEpochMillis: Long,
) {
    val isRecommended: Boolean
        get() = cycleCount >= 5
}

@Immutable
data class ActiveSleepSession(
    val activatedAtEpochMillis: Long,
    val sleepStartsAtEpochMillis: Long,
)

@Immutable
data class SleepRecord(
    val id: Long,
    val activatedAtEpochMillis: Long,
    val sleepStartsAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
) {
    val durationMillis: Long
        get() = (endedAtEpochMillis - sleepStartsAtEpochMillis).coerceAtLeast(0L)
}

enum class SleepHistoryRange(val dayCount: Int, val label: String) {
    WEEK(dayCount = 7, label = "Week"),
    MONTH(dayCount = 30, label = "Month"),
}
