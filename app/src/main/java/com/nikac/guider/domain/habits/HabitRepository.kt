package com.nikac.guider.domain.habits

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    val habits: StateFlow<List<Habit>>
    val recentCompletions: StateFlow<Map<Long, Set<Int>>>

    fun observeCompletionsBetween(
        startDayKey: Int,
        endDayKey: Int,
    ): Flow<Map<Long, Set<Int>>>

    val goalCompletionCounts: Flow<Map<Long, Int>>

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
