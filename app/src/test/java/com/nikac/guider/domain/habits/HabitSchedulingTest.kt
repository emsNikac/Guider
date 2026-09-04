package com.nikac.guider.domain.habits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitSchedulingTest {
    private val habit = Habit(
        id = 1L,
        name = "Workout 30 minutes",
        colorHue = 210f,
        scheduledWeekdays = setOf(
            HabitWeekday.MONDAY,
            HabitWeekday.WEDNESDAY,
            HabitWeekday.FRIDAY,
        ),
        linkedGoalId = 2L,
        activeStartDayKey = 20260817,
        activeEndDayKey = 20260830,
    )

    @Test
    fun `scheduled weekday is available inside goal period`() {
        assertTrue(habit.isScheduledOn(20260817, HabitWeekday.MONDAY))
    }

    @Test
    fun `unscheduled weekday is unavailable inside goal period`() {
        assertFalse(habit.isScheduledOn(20260818, HabitWeekday.TUESDAY))
    }

    @Test
    fun `scheduled weekday is unavailable outside goal period`() {
        assertFalse(habit.isScheduledOn(20260831, HabitWeekday.MONDAY))
    }
}
