package com.example.guider.domain.goals

import kotlinx.coroutines.flow.StateFlow

interface GoalRepository {
    val goals: StateFlow<List<Goal>>

    fun addGoal(
        title: String,
        type: GoalType,
        startDayKey: Int? = null,
        endDayKey: Int? = null,
    ): Goal

    fun toggleAchievement(goalId: Long, dayKey: Int)

    fun removeCompletedBefore(dayKey: Int)

    fun deleteGoal(goalId: Long)
}
