package com.example.guider.domain.habits

import kotlinx.coroutines.flow.StateFlow

interface HabitRepository {
    val habits: StateFlow<List<Habit>>

    fun addHabit(
        name: String,
        scheduledWeekdays: Set<HabitWeekday> = HabitWeekday.entries.toSet(),
        linkedGoalId: Long? = null,
        activeStartDayKey: Int? = null,
        activeEndDayKey: Int? = null,
    ): Habit

    fun toggleCompletion(habitId: Long, dayKey: Int)

    fun deleteHabit(habitId: Long)

    fun deleteHabitsForGoal(goalId: Long)

    fun setGoalPeriod(goalId: Long, startDayKey: Int, endDayKey: Int)
}
