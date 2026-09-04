package com.nikac.guider.domain.goals

import kotlinx.coroutines.flow.StateFlow

interface GoalRepository {
    val goals: StateFlow<List<Goal>>

    suspend fun addGoal(
        title: String,
        type: GoalType,
        startDayKey: Int? = null,
        endDayKey: Int? = null,
        habitInputs: List<GoalHabitInput> = emptyList(),
    ): Goal

    suspend fun toggleAchievement(goalId: Long, dayKey: Int)

    suspend fun removeCompletedBefore(dayKey: Int)

    suspend fun deleteGoal(goalId: Long)
}
