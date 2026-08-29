package com.example.guider.data.goals

import androidx.room.withTransaction
import com.example.guider.data.database.GoalEntity
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.HabitEntity
import com.example.guider.data.database.HabitWeekdayEntity
import com.example.guider.data.database.toModel
import com.example.guider.data.stateInWhileSubscribed
import com.example.guider.data.habits.RoomHabitRepository
import com.example.guider.domain.goals.Goal
import com.example.guider.domain.goals.GoalHabitInput
import com.example.guider.domain.goals.GoalRepository
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomGoalRepository private constructor(
    private val database: GuiderDatabase,
    override val goals: StateFlow<List<Goal>>,
) : GoalRepository {
    private val goalDao = database.goalDao()
    private val habitDao = database.habitDao()

    override suspend fun addGoal(
        title: String,
        type: GoalType,
        startDayKey: Int?,
        endDayKey: Int?,
        habitInputs: List<GoalHabitInput>,
    ): Goal = database.withTransaction {
        val periodicStart = if (type == GoalType.PERIODIC) startDayKey ?: DayKeys.today() else null
        val periodicEnd = if (type == GoalType.PERIODIC) {
            (endDayKey ?: DayKeys.addDays(requireNotNull(periodicStart), DEFAULT_PERIOD_DAYS - 1))
                .coerceAtLeast(requireNotNull(periodicStart))
        } else {
            null
        }
        val entity = GoalEntity(
            title = title.trim(),
            type = type.name,
            createdDayKey = DayKeys.today(),
            achievedDayKey = null,
            startDayKey = periodicStart,
            endDayKey = periodicEnd,
        )
        val goalId = goalDao.insert(entity)

        if (type == GoalType.PERIODIC) {
            val usedHues = habitDao.getUsedHues().toMutableList()
            habitInputs.forEach { input ->
                val hue = RoomHabitRepository.mostDistinctHue(usedHues)
                usedHues += hue
                val habitId = habitDao.insertHabit(
                    HabitEntity(
                        name = input.name.trim(),
                        colorHue = hue,
                        linkedGoalId = goalId,
                        activeStartDayKey = periodicStart,
                        activeEndDayKey = periodicEnd,
                    ),
                )
                habitDao.insertWeekdays(
                    input.scheduledWeekdays.map { weekday ->
                        HabitWeekdayEntity(habitId, weekday.name)
                    },
                )
            }
        }

        entity.copy(id = goalId).toModel()
    }

    override suspend fun toggleAchievement(goalId: Long, dayKey: Int) {
        goalDao.toggleAchievement(goalId, dayKey)
    }

    override suspend fun removeCompletedBefore(dayKey: Int) {
        goalDao.removeCompletedBefore(dayKey)
    }

    override suspend fun deleteGoal(goalId: Long) {
        goalDao.delete(goalId)
    }

    companion object {
        private const val DEFAULT_PERIOD_DAYS = 14

        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomGoalRepository {
            database.goalDao().removeCompletedBefore(DayKeys.today())
            val goals = database.goalDao().observeAll()
                .map { entities -> entities.map(GoalEntity::toModel) }
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomGoalRepository(
                database = database,
                goals = goals,
            )
        }
    }
}
