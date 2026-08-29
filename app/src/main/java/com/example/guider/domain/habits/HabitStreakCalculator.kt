package com.example.guider.domain.habits

object HabitStreakCalculator {
    /**
     * [dayKeysNewestFirst] starts with today, followed by yesterday and earlier days.
     * An unfinished current day does not break a streak until that day has passed.
     */
    fun currentStreak(
        completedDayKeys: Set<Int>,
        dayKeysNewestFirst: IntArray,
        allowIncompleteFirstDay: Boolean = true,
    ): Int {
        if (dayKeysNewestFirst.isEmpty()) return 0
        var index = if (
            allowIncompleteFirstDay && dayKeysNewestFirst[0] !in completedDayKeys
        ) 1 else 0
        var streak = 0
        while (
            index < dayKeysNewestFirst.size &&
            dayKeysNewestFirst[index] in completedDayKeys
        ) {
            streak++
            index++
        }
        return streak
    }
}
