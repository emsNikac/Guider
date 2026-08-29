package com.example.guider.data.habits

import androidx.room.withTransaction
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.HabitCompletionEntity
import com.example.guider.data.database.HabitEntity
import com.example.guider.data.database.HabitRecord
import com.example.guider.data.database.HabitWeekdayEntity
import com.example.guider.data.database.toModel
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

class RoomHabitRepository private constructor(
    private val database: GuiderDatabase,
    scope: CoroutineScope,
    initialHabits: List<Habit>,
) : HabitRepository {
    private val dao = database.habitDao()

    override val habits: StateFlow<List<Habit>> = dao.observeAll()
        .map { records -> records.map(HabitRecord::toModel) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialHabits)

    override suspend fun addHabit(
        name: String,
        scheduledWeekdays: Set<HabitWeekday>,
        linkedGoalId: Long?,
        activeStartDayKey: Int?,
        activeEndDayKey: Int?,
    ): Habit = database.withTransaction {
        val hue = mostDistinctHue(dao.getUsedHues())
        val entity = HabitEntity(
            name = name.trim(),
            colorHue = hue,
            linkedGoalId = linkedGoalId,
            activeStartDayKey = activeStartDayKey,
            activeEndDayKey = activeEndDayKey,
        )
        val id = dao.insertHabit(entity)
        val weekdays = scheduledWeekdays.ifEmpty { HabitWeekday.entries.toSet() }
        dao.insertWeekdays(
            weekdays.map { weekday -> HabitWeekdayEntity(id, weekday.name) },
        )
        Habit(
            id = id,
            name = entity.name,
            colorHue = hue,
            scheduledWeekdays = weekdays,
            linkedGoalId = linkedGoalId,
            activeStartDayKey = activeStartDayKey,
            activeEndDayKey = activeEndDayKey,
        )
    }

    override suspend fun toggleCompletion(habitId: Long, dayKey: Int) {
        database.withTransaction {
            val habit = dao.getHabit(habitId) ?: return@withTransaction
            if (habit.activeStartDayKey != null && dayKey < habit.activeStartDayKey) return@withTransaction
            if (habit.activeEndDayKey != null && dayKey > habit.activeEndDayKey) return@withTransaction

            val weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey))
            if (!dao.isScheduledWeekday(habitId, weekday.name)) return@withTransaction
            if (dao.isCompleted(habitId, dayKey)) {
                dao.deleteCompletion(habitId, dayKey)
            } else {
                dao.insertCompletion(HabitCompletionEntity(habitId, dayKey))
            }
        }
    }

    override suspend fun deleteHabit(habitId: Long) {
        dao.deleteHabit(habitId)
    }

    override suspend fun deleteHabitsForGoal(goalId: Long) {
        dao.deleteHabitsForGoal(goalId)
    }

    override suspend fun setGoalPeriod(goalId: Long, startDayKey: Int, endDayKey: Int) {
        dao.setGoalPeriod(goalId, startDayKey, endDayKey)
    }

    companion object {
        private const val FULL_CIRCLE_DEGREES = 360
        private const val DEFAULT_HUE = 210f

        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomHabitRepository =
            RoomHabitRepository(
                database = database,
                scope = scope,
                initialHabits = database.habitDao().getAll().map(HabitRecord::toModel),
            )

        fun mostDistinctHue(usedHues: List<Float>): Float {
            if (usedHues.isEmpty()) return DEFAULT_HUE
            return (0 until FULL_CIRCLE_DEGREES)
                .maxBy { candidate ->
                    usedHues.minOf { usedHue ->
                        val directDistance = abs(candidate - usedHue)
                        minOf(directDistance, FULL_CIRCLE_DEGREES - directDistance)
                    }
                }
                .toFloat()
        }
    }
}
