package com.nikac.guider.domain.goals

import com.nikac.guider.domain.habits.Habit
import com.nikac.guider.domain.habits.HabitWeekday
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressCalculatorTest {
    @Test
    fun `each scheduled occurrence in the full goal period contributes equally`() {
        val goal = Goal(
            id = 7L,
            title = "Gain muscle",
            type = GoalType.PERIODIC,
            createdDayKey = 20260817,
            startDayKey = 20260817,
            endDayKey = 20260830,
        )
        val gymHabit = Habit(
            id = 10L,
            name = "Go to the gym",
            colorHue = 210f,
            completedDayKeys = setOf(20260817, 20260821, 20260824),
            scheduledWeekdays = setOf(
                HabitWeekday.MONDAY,
                HabitWeekday.WEDNESDAY,
                HabitWeekday.FRIDAY,
            ),
            linkedGoalId = goal.id,
        )

        val progress = GoalProgressCalculator.calculate(
            goal = goal,
            linkedHabits = listOf(gymHabit),
            todayDayKey = 20260823,
        )

        assertEquals(2, progress.completedCheckIns)
        assertEquals(6, progress.expectedCheckIns)
        assertEquals(33, progress.percentage)
    }

    @Test
    fun `adds expected check-ins across multiple habits`() {
        val goal = Goal(
            id = 9L,
            title = "Feel healthier",
            type = GoalType.PERIODIC,
            createdDayKey = 20260822,
            startDayKey = 20260822,
            endDayKey = 20260823,
        )
        val dailyHabit = Habit(
            id = 11L,
            name = "Eat five times",
            colorHue = 120f,
            completedDayKeys = setOf(20260822, 20260823),
            linkedGoalId = goal.id,
        )
        val sundayHabit = Habit(
            id = 12L,
            name = "Plan meals",
            colorHue = 30f,
            scheduledWeekdays = setOf(HabitWeekday.SUNDAY),
            linkedGoalId = goal.id,
        )

        val progress = GoalProgressCalculator.calculate(
            goal = goal,
            linkedHabits = listOf(dailyHabit, sundayHabit),
            todayDayKey = 20260823,
        )

        assertEquals(2, progress.completedCheckIns)
        assertEquals(3, progress.expectedCheckIns)
    }

    @Test
    fun `one-time goals have no periodic consistency`() {
        val progress = GoalProgressCalculator.calculate(
            goal = Goal(
                id = 1L,
                title = "Finish portfolio",
                type = GoalType.ONE_TIME,
                createdDayKey = 20260823,
            ),
            linkedHabits = emptyList(),
            todayDayKey = 20260823,
        )

        assertEquals(0, progress.expectedCheckIns)
        assertEquals(0f, progress.fraction)
    }

    @Test
    fun `expected check-ins respect an active habit subrange without scanning every day`() {
        val goal = Goal(
            id = 13L,
            title = "Build consistency",
            type = GoalType.PERIODIC,
            createdDayKey = 20260101,
            startDayKey = 20260101,
            endDayKey = 20261231,
        )
        val habit = Habit(
            id = 21L,
            name = "Review the week",
            colorHue = 210f,
            scheduledWeekdays = setOf(HabitWeekday.MONDAY, HabitWeekday.FRIDAY),
            linkedGoalId = goal.id,
            activeStartDayKey = 20260817,
            activeEndDayKey = 20260830,
        )

        val progress = GoalProgressCalculator.calculateFromCompletedCount(
            goal = goal,
            linkedHabits = listOf(habit),
            completedCheckIns = 3,
            todayDayKey = 20260823,
        )

        assertEquals(3, progress.completedCheckIns)
        assertEquals(4, progress.expectedCheckIns)
        assertEquals(75, progress.percentage)
    }
}
