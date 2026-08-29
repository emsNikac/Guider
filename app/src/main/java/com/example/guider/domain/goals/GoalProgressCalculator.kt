package com.example.guider.domain.goals

import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.habits.isScheduledOn
import com.example.guider.domain.time.DayKeys

object GoalProgressCalculator {
    fun calculate(
        goal: Goal,
        linkedHabits: List<Habit>,
        todayDayKey: Int = DayKeys.today(),
    ): GoalProgress {
        if (goal.type != GoalType.PERIODIC || linkedHabits.isEmpty()) {
            return GoalProgress(completedCheckIns = 0, expectedCheckIns = 0)
        }

        var completed = 0
        var expected = 0
        val startDayKey = goal.startDayKey ?: goal.createdDayKey
        val endDayKey = goal.endDayKey ?: todayDayKey
        for (dayKey in DayKeys.inclusiveRange(startDayKey, endDayKey)) {
            val weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey))
            for (habit in linkedHabits) {
                if (habit.isScheduledOn(dayKey, weekday)) {
                    expected += 1
                    if (dayKey <= todayDayKey && dayKey in habit.completedDayKeys) completed += 1
                }
            }
        }
        return GoalProgress(completedCheckIns = completed, expectedCheckIns = expected)
    }
}
