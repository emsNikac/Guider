package com.example.guider.ui.screens.goals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.guider.GuiderApplication
import com.example.guider.domain.goals.GoalHabitInput
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.time.DayKeys
import com.example.guider.models.TaskCategory

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val guiderApplication = application as GuiderApplication
    private val goalRepository = guiderApplication.goalRepository
    private val habitRepository = guiderApplication.habitRepository
    private val taskRepository = guiderApplication.dailyTaskRepository

    val goals = goalRepository.goals
    val habits = habitRepository.habits

    fun addGoal(
        title: String,
        type: GoalType,
        habitInputs: List<GoalHabitInput>,
        startDayKey: Int?,
        endDayKey: Int?,
    ) {
        if (title.isBlank()) return
        val validHabitInputs = habitInputs.filter {
            it.name.isNotBlank() && it.scheduledWeekdays.isNotEmpty()
        }
        if (type == GoalType.PERIODIC && validHabitInputs.isEmpty()) return
        if (
            type == GoalType.PERIODIC &&
            (startDayKey == null || endDayKey == null || endDayKey < startDayKey)
        ) return

        val goal = goalRepository.addGoal(title, type, startDayKey, endDayKey)
        if (type == GoalType.PERIODIC) {
            validHabitInputs.forEach { input ->
                    habitRepository.addHabit(
                        name = input.name,
                        scheduledWeekdays = input.scheduledWeekdays,
                        linkedGoalId = goal.id,
                        activeStartDayKey = goal.startDayKey,
                        activeEndDayKey = goal.endDayKey,
                    )
                }
        }
    }

    fun toggleAchievement(goalId: Long) {
        goalRepository.toggleAchievement(goalId, DayKeys.today())
    }

    fun addDailyTask(goalId: Long, title: String, category: TaskCategory) {
        if (title.isNotBlank()) taskRepository.addTask(title, category, goalId)
    }

    fun deleteGoal(goalId: Long) {
        goalRepository.deleteGoal(goalId)
        habitRepository.deleteHabitsForGoal(goalId)
        taskRepository.clearGoalLink(goalId)
    }
}
