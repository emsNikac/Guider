package com.example.guider.domain.habits

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitStreakCalculatorTest {
    private val newestDays = listOf(15, 14, 13, 12, 11)

    @Test
    fun `counts a streak ending today`() {
        val result = HabitStreakCalculator.currentStreak(
            completedDayKeys = setOf(15, 14, 13, 11),
            dayKeysNewestFirst = newestDays,
        )

        assertEquals(3, result)
    }

    @Test
    fun `keeps yesterday streak while today is still unfinished`() {
        val result = HabitStreakCalculator.currentStreak(
            completedDayKeys = setOf(14, 13, 12),
            dayKeysNewestFirst = newestDays,
        )

        assertEquals(3, result)
    }

    @Test
    fun `resets after a skipped completed day`() {
        val result = HabitStreakCalculator.currentStreak(
            completedDayKeys = setOf(13, 12),
            dayKeysNewestFirst = newestDays,
        )

        assertEquals(0, result)
    }

    @Test
    fun `a missed latest scheduled day resets when today is not scheduled`() {
        val result = HabitStreakCalculator.currentStreak(
            completedDayKeys = setOf(14, 13, 12),
            dayKeysNewestFirst = newestDays,
            allowIncompleteFirstDay = false,
        )

        assertEquals(0, result)
    }
}
