package com.example.guider.domain.habits

import kotlinx.coroutines.flow.StateFlow

interface HabitRepository {
    val habits: StateFlow<List<Habit>>

    suspend fun addHabit(
        name: String,
        scheduledWeekdays: Set<HabitWeekday> = HabitWeekday.entries.toSet(),
        linkedGoalId: Long? = null,
        activeStartDayKey: Int? = null,
        activeEndDayKey: Int? = null,
    ): Habit

    suspend fun toggleCompletion(habitId: Long, dayKey: Int)

    suspend fun deleteHabit(habitId: Long)

    suspend fun deleteHabitsForGoal(goalId: Long)

    suspend fun setGoalPeriod(goalId: Long, startDayKey: Int, endDayKey: Int)
}
