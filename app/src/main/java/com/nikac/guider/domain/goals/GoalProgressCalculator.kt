package com.nikac.guider.domain.goals

import com.nikac.guider.domain.habits.Habit
import com.nikac.guider.domain.habits.HabitWeekday
import com.nikac.guider.domain.habits.isScheduledOn
import com.nikac.guider.domain.time.DayKeys

object GoalProgressCalculator {
    fun calculate(
        goal: Goal,
        linkedHabits: List<Habit>,
        todayDayKey: Int = DayKeys.today(),
    ): GoalProgress {
        if (goal.type != GoalType.PERIODIC || linkedHabits.isEmpty()) {
            return GoalProgress(completedCheckIns = 0, expectedCheckIns = 0)
        }

        val startDayKey = goal.startDayKey ?: goal.createdDayKey
        val endDayKey = goal.endDayKey ?: todayDayKey
        var completed = 0
        linkedHabits.forEach { habit ->
            habit.completedDayKeys.forEach { dayKey ->
                if (dayKey in startDayKey..minOf(endDayKey, todayDayKey)) {
                    val weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey))
                    if (habit.isScheduledOn(dayKey, weekday)) completed++
                }
            }
        }
        return calculateFromCompletedCount(
            goal = goal,
            linkedHabits = linkedHabits,
            completedCheckIns = completed,
            todayDayKey = todayDayKey,
        )
    }

    fun calculateFromCompletedCount(
        goal: Goal,
        linkedHabits: List<Habit>,
        completedCheckIns: Int,
        todayDayKey: Int = DayKeys.today(),
    ): GoalProgress {
        if (goal.type != GoalType.PERIODIC || linkedHabits.isEmpty()) {
            return GoalProgress(completedCheckIns = 0, expectedCheckIns = 0)
        }

        val goalStartDayKey = goal.startDayKey ?: goal.createdDayKey
        val goalEndDayKey = goal.endDayKey ?: todayDayKey
        var expected = 0
        linkedHabits.forEach { habit ->
            val startDayKey = maxOf(goalStartDayKey, habit.activeStartDayKey ?: goalStartDayKey)
            val endDayKey = minOf(goalEndDayKey, habit.activeEndDayKey ?: goalEndDayKey)
            expected += expectedOccurrences(habit, startDayKey, endDayKey)
        }
        return GoalProgress(
            completedCheckIns = completedCheckIns.coerceAtMost(expected),
            expectedCheckIns = expected,
        )
    }

    private fun expectedOccurrences(habit: Habit, startDayKey: Int, endDayKey: Int): Int {
        val dayCount = DayKeys.inclusiveDayCount(startDayKey, endDayKey)
        if (dayCount == 0) return 0

        val firstWeekday = DayKeys.weekday(startDayKey)
        return habit.scheduledWeekdays.sumOf { weekday ->
            val offset = Math.floorMod(weekday.calendarValue - firstWeekday, DAYS_PER_WEEK)
            if (offset >= dayCount) 0 else 1 + (dayCount - 1 - offset) / DAYS_PER_WEEK
        }
    }

    private const val DAYS_PER_WEEK = 7
}
