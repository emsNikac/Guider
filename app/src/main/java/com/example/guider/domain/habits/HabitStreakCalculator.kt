package com.example.guider.domain.habits

object HabitStreakCalculator {
    /**
     * [dayKeysNewestFirst] starts with today, followed by yesterday and earlier days.
     * An unfinished current day does not break a streak until that day has passed.
     */
    fun currentStreak(
        completedDayKeys: Set<Int>,
        dayKeysNewestFirst: List<Int>,
        allowIncompleteFirstDay: Boolean = true,
    ): Int {
        if (dayKeysNewestFirst.isEmpty()) return 0
        val daysToCount = if (
            allowIncompleteFirstDay && dayKeysNewestFirst.first() !in completedDayKeys
        ) {
            dayKeysNewestFirst.drop(1)
        } else {
            dayKeysNewestFirst
        }
        return daysToCount.takeWhile { it in completedDayKeys }.size
    }
}
