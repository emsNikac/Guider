package com.example.guider.ui.screens.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.guider.GuiderApplication
import com.example.guider.domain.habits.HabitRepository

class HabitsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HabitRepository =
        (application as GuiderApplication).habitRepository

    val habits = repository.habits

    fun addHabit(name: String) {
        if (name.isNotBlank()) repository.addHabit(name)
    }

    fun toggleCompletion(habitId: Long, dayKey: Int) {
        repository.toggleCompletion(habitId, dayKey)
    }

    fun deleteHabit(habitId: Long) {
        repository.deleteHabit(habitId)
    }
}
