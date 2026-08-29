package com.example.guider.data.habits

import androidx.room.withTransaction
import com.example.guider.data.database.GuiderDatabase
import com.example.guider.data.database.HabitCompletionEntity
import com.example.guider.data.database.HabitEntity
import com.example.guider.data.database.HabitRecord
import com.example.guider.data.database.HabitWeekdayEntity
import com.example.guider.data.database.toModel
import com.example.guider.data.stateInWhileSubscribed
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs

class RoomHabitRepository private constructor(
    private val database: GuiderDatabase,
    override val habits: StateFlow<List<Habit>>,
    override val recentCompletions: StateFlow<Map<Long, Set<Int>>>,
) : HabitRepository {
    private val dao = database.habitDao()

    override fun observeCompletionsBetween(
        startDayKey: Int,
        endDayKey: Int,
    ): Flow<Map<Long, Set<Int>>> = dao
        .observeCompletionsBetween(startDayKey, endDayKey)
        .map(::completionMap)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override val goalCompletionCounts: Flow<Map<Long, Int>> = dao
        .observeGoalCompletionCounts()
        .map { counts ->
            counts.associate { count ->
                count.goalId to count.completedCheckIns.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

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
            val weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey))
            val state = dao.getToggleState(habitId, dayKey, weekday.name)
                ?: return@withTransaction
            if (state.activeStartDayKey != null && dayKey < state.activeStartDayKey) return@withTransaction
            if (state.activeEndDayKey != null && dayKey > state.activeEndDayKey) return@withTransaction
            if (!state.isScheduled) return@withTransaction
            if (state.isCompleted) {
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

        suspend fun create(database: GuiderDatabase, scope: CoroutineScope): RoomHabitRepository {
            val dao = database.habitDao()
            val todayDayKey = DayKeys.today()
            val recentStartDayKey = DayKeys.addDays(todayDayKey, -(RECENT_DAY_COUNT - 1))
            return coroutineScope {
                val habits = async {
                    dao.observeAll()
                        .map { records -> records.map(HabitRecord::toModel) }
                        .distinctUntilChanged()
                        .stateInWhileSubscribed(scope)
                }
                val recentCompletions = async {
                    dao.observeCompletionsBetween(recentStartDayKey, todayDayKey)
                        .map(::completionMap)
                        .distinctUntilChanged()
                        .flowOn(Dispatchers.Default)
                        .stateInWhileSubscribed(scope)
                }
                RoomHabitRepository(
                    database = database,
                    habits = habits.await(),
                    recentCompletions = recentCompletions.await(),
                )
            }
        }

        private fun completionMap(
            completions: List<HabitCompletionEntity>,
        ): Map<Long, Set<Int>> = completions
            .groupByTo(LinkedHashMap()) { it.habitId }
            .mapValues { (_, entries) ->
                entries.mapTo(LinkedHashSet(entries.size)) { it.dayKey }
            }

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

        private const val RECENT_DAY_COUNT = 366
    }
}
