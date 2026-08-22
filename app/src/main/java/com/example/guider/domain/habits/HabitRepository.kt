package com.example.guider.domain.habits

import kotlinx.coroutines.flow.StateFlow

interface HabitRepository {
    val habits: StateFlow<List<Habit>>

    fun addHabit(name: String): Habit

    fun toggleCompletion(habitId: Long, dayKey: Int)

    fun deleteHabit(habitId: Long)
}
