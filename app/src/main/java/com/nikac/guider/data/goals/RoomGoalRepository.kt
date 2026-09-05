package com.nikac.guider.data.goals

import androidx.room.withTransaction
import com.nikac.guider.data.database.GoalEntity
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.HabitEntity
import com.nikac.guider.data.database.HabitWeekdayEntity
import com.nikac.guider.data.database.toModel
import com.nikac.guider.data.stateInWhileSubscribed
import com.nikac.guider.data.habits.RoomHabitRepository
import com.nikac.guider.domain.goals.Goal
import com.nikac.guider.domain.goals.GoalHabitInput
import com.nikac.guider.domain.goals.GoalRepository
import com.nikac.guider.domain.goals.GoalType
import com.nikac.guider.domain.time.DayKeys
import com.nikac.guider.domain.sync.DataOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomGoalRepository private constructor(
    private val database: GuiderDatabase,
    private val owner: StateFlow<DataOwner>,
    private val onDataChanged: () -> Unit,
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
        val currentOwner = owner.value
        val periodicStart = if (type == GoalType.PERIODIC) startDayKey ?: DayKeys.today() else null
        val periodicEnd = if (type == GoalType.PERIODIC) {
            (endDayKey ?: DayKeys.addDays(requireNotNull(periodicStart), DEFAULT_PERIOD_DAYS - 1))
                .coerceAtLeast(requireNotNull(periodicStart))
        } else {
            null
        }
        val entity = GoalEntity(
            ownerId = currentOwner.localId,
            title = title.trim(),
            type = type.name,
            createdDayKey = DayKeys.today(),
            achievedDayKey = null,
            startDayKey = periodicStart,
            endDayKey = periodicEnd,
            syncPending = true,
        )
        val goalId = goalDao.insert(entity)

        if (type == GoalType.PERIODIC) {
            val usedHues = habitDao.getUsedHues(currentOwner.localId).toMutableList()
            habitInputs.forEach { input ->
                val hue = RoomHabitRepository.mostDistinctHue(usedHues)
                usedHues += hue
                val habitId = habitDao.insertHabit(
                    HabitEntity(
                        ownerId = currentOwner.localId,
                        name = input.name.trim(),
                        colorHue = hue,
                        linkedGoalId = goalId,
                        activeStartDayKey = periodicStart,
                        activeEndDayKey = periodicEnd,
                        syncPending = true,
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
    }.also { notifyCloud(owner.value) }

    override suspend fun toggleAchievement(goalId: Long, dayKey: Int) {
        val currentOwner = owner.value
        goalDao.toggleAchievement(
            ownerId = currentOwner.localId,
            goalId = goalId,
            dayKey = dayKey,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = true,
        )
        notifyCloud(currentOwner)
    }

    override suspend fun removeCompletedBefore(dayKey: Int) {
        val currentOwner = owner.value
        val changed = goalDao.archiveCompletedBefore(
            ownerId = currentOwner.localId,
            dayKey = dayKey,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = true,
        )
        if (changed > 0) notifyCloud(currentOwner)
    }

    override suspend fun deleteGoal(goalId: Long) {
        val currentOwner = owner.value
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.dailyTaskDao().clearGoalLink(
                ownerId = currentOwner.localId,
                goalId = goalId,
                updatedAtEpochMillis = now,
                syncPending = true,
            )
            habitDao.softDeleteCompletionsForGoal(
                ownerId = currentOwner.localId,
                goalId = goalId,
                updatedAtEpochMillis = now,
                syncPending = true,
            )
            habitDao.softDeleteForGoal(
                ownerId = currentOwner.localId,
                goalId = goalId,
                updatedAtEpochMillis = now,
                syncPending = true,
            )
            goalDao.softDelete(
                ownerId = currentOwner.localId,
                goalId = goalId,
                updatedAtEpochMillis = now,
                syncPending = true,
            )
        }
        notifyCloud(currentOwner)
    }

    private fun notifyCloud(changedOwner: DataOwner) {
        if (changedOwner == owner.value && changedOwner.usesCloud) onDataChanged()
    }

    companion object {
        private const val DEFAULT_PERIOD_DAYS = 14

        suspend fun create(
            database: GuiderDatabase,
            scope: CoroutineScope,
            owner: StateFlow<DataOwner>,
            onDataChanged: () -> Unit,
        ): RoomGoalRepository {
            val currentOwner = owner.value
            val archived = database.goalDao().archiveCompletedBefore(
                ownerId = currentOwner.localId,
                dayKey = DayKeys.today(),
                updatedAtEpochMillis = System.currentTimeMillis(),
                syncPending = true,
            )
            if (archived > 0 && currentOwner.usesCloud) onDataChanged()
            val goals = owner.flatMapLatest { activeOwner ->
                database.goalDao().observeAll(activeOwner.localId)
            }
                .map { entities -> entities.map(GoalEntity::toModel) }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomGoalRepository(
                database = database,
                owner = owner,
                onDataChanged = onDataChanged,
                goals = goals,
            )
        }
    }
}
