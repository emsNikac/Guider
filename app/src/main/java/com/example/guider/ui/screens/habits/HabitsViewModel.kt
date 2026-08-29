package com.example.guider.ui.screens.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guider.GuiderApplication
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.habits.HabitWeekday
import kotlinx.coroutines.launch

class HabitsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HabitRepository =
        (application as GuiderApplication).habitRepository

    val habits = repository.habits

    fun addHabit(name: String, scheduledWeekdays: Set<HabitWeekday>) {
        if (name.isNotBlank() && scheduledWeekdays.isNotEmpty()) {
            viewModelScope.launch {
                repository.addHabit(name, scheduledWeekdays)
            }
        }
    }

    fun toggleCompletion(habitId: Long, dayKey: Int) {
        viewModelScope.launch {
            repository.toggleCompletion(habitId, dayKey)
        }
    }

    fun deleteHabit(habitId: Long) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
        }
    }
}
