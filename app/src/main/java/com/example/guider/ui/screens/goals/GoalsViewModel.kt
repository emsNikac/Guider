package com.example.guider.ui.screens.goals

import android.app.Application
import androidx.compose.runtime.Immutable
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
import com.example.guider.ui.util.ImmutableListSnapshot
import com.example.guider.ui.util.ImmutableMapSnapshot
import com.example.guider.ui.util.toImmutableSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Immutable
internal data class GoalsUiState(
    val goals: ImmutableListSnapshot<Goal> = ImmutableListSnapshot(emptyList()),
    val oneTimeGoals: ImmutableListSnapshot<Goal> = ImmutableListSnapshot(emptyList()),
    val periodicGoals: ImmutableListSnapshot<Goal> = ImmutableListSnapshot(emptyList()),
    val activeGoalCount: Int = 0,
    val activeOneTimeGoalCount: Int = 0,
    val activePeriodicGoalCount: Int = 0,
    val linkedHabits: ImmutableMapSnapshot<Long, ImmutableListSnapshot<Habit>> =
        ImmutableMapSnapshot(emptyMap()),
    val periodicProgress: ImmutableMapSnapshot<Long, GoalProgress> =
        ImmutableMapSnapshot(emptyMap()),
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
                val linkedHabits = habits
                    .groupBy { habit -> habit.linkedGoalId ?: UNLINKED_GOAL_ID }
                    .mapValues { (_, linked) -> linked.toImmutableSnapshot() }
                val periodicProgress = periodicGoals.associate { goal ->
                    goal.id to
                    GoalProgressCalculator.calculate(
                        goal = goal,
                        linkedHabits = linkedHabits[goal.id].orEmpty(),
                        todayDayKey = todayDayKey,
                    )
                }
                GoalsUiState(
                    goals = goals.toImmutableSnapshot(),
                    oneTimeGoals = oneTimeGoals.toImmutableSnapshot(),
                    periodicGoals = periodicGoals.toImmutableSnapshot(),
                    activeGoalCount = goals.count { it.isActive(todayDayKey) },
                    activeOneTimeGoalCount = oneTimeGoals.count { it.isActive(todayDayKey) },
                    activePeriodicGoalCount = periodicGoals.count { it.isActive(todayDayKey) },
                    linkedHabits = linkedHabits.toImmutableSnapshot(),
                    periodicProgress = periodicProgress.toImmutableSnapshot(),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
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

        viewModelScope.launch {
            goalRepository.addGoal(
                title = title,
                type = type,
                startDayKey = startDayKey,
                endDayKey = endDayKey,
                habitInputs = validHabitInputs,
            )
        }
    }

    fun toggleAchievement(goalId: Long) {
        viewModelScope.launch {
            goalRepository.toggleAchievement(goalId, DayKeys.today())
        }
    }

    fun addDailyTask(goalId: Long, title: String, category: TaskCategory) {
        if (title.isNotBlank()) {
            viewModelScope.launch {
                taskRepository.addTask(title, category, goalId)
            }
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            // Room cascades linked habits and clears task links in the same transaction.
            goalRepository.deleteGoal(goalId)
        }
    }

    private companion object {
        const val UNLINKED_GOAL_ID = -1L
    }
}
