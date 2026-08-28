package com.example.guider.ui.screens.goals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guider.GuiderApplication
import com.example.guider.domain.goals.Goal
import com.example.guider.domain.goals.GoalHabitInput
import com.example.guider.domain.goals.GoalProgress
import com.example.guider.domain.goals.GoalProgressCalculator
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.goals.isActive
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.time.DayKeys
import com.example.guider.models.TaskCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

internal data class GoalsUiState(
    val goals: List<Goal> = emptyList(),
    val oneTimeGoals: List<Goal> = emptyList(),
    val periodicGoals: List<Goal> = emptyList(),
    val activeGoalCount: Int = 0,
    val activeOneTimeGoalCount: Int = 0,
    val activePeriodicGoalCount: Int = 0,
    val linkedHabits: Map<Long?, List<Habit>> = emptyMap(),
    val periodicProgress: Map<Long, GoalProgress> = emptyMap(),
)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val guiderApplication = application as GuiderApplication
    private val goalRepository = guiderApplication.goalRepository
    private val habitRepository = guiderApplication.habitRepository
    private val taskRepository = guiderApplication.dailyTaskRepository

    internal val uiState = combine(goalRepository.goals, habitRepository.habits, ::Pair)
        .map { (goals, habits) ->
            withContext(Dispatchers.Default) {
                val todayDayKey = DayKeys.today()
                val oneTimeGoals = goals.filter { it.type == GoalType.ONE_TIME }
                val periodicGoals = goals.filter { it.type == GoalType.PERIODIC }
                val linkedHabits = habits.groupBy(Habit::linkedGoalId)
                val periodicProgress = periodicGoals.associate { goal ->
                    goal.id to
                    GoalProgressCalculator.calculate(
                        goal = goal,
                        linkedHabits = linkedHabits[goal.id].orEmpty(),
                        todayDayKey = todayDayKey,
                    )
                }
                GoalsUiState(
                    goals = goals,
                    oneTimeGoals = oneTimeGoals,
                    periodicGoals = periodicGoals,
                    activeGoalCount = goals.count { it.isActive(todayDayKey) },
                    activeOneTimeGoalCount = oneTimeGoals.count { it.isActive(todayDayKey) },
                    activePeriodicGoalCount = periodicGoals.count { it.isActive(todayDayKey) },
                    linkedHabits = linkedHabits,
                    periodicProgress = periodicProgress,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = GoalsUiState(),
        )

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
