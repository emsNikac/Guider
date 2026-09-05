package com.nikac.guider.data.habits

import androidx.room.withTransaction
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.HabitCompletionEntity
import com.nikac.guider.data.database.HabitEntity
import com.nikac.guider.data.database.HabitRecord
import com.nikac.guider.data.database.HabitWeekdayEntity
import com.nikac.guider.data.database.toModel
import com.nikac.guider.data.stateInWhileSubscribed
import com.nikac.guider.domain.habits.Habit
import com.nikac.guider.domain.habits.HabitRepository
import com.nikac.guider.domain.habits.HabitWeekday
import com.nikac.guider.domain.time.DayKeys
import com.nikac.guider.domain.sync.DataOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomHabitRepository private constructor(
    private val database: GuiderDatabase,
    private val owner: StateFlow<DataOwner>,
    private val onDataChanged: () -> Unit,
    override val habits: StateFlow<List<Habit>>,
    override val recentCompletions: StateFlow<Map<Long, Set<Int>>>,
) : HabitRepository {
    private val dao = database.habitDao()

    override fun observeCompletionsBetween(
        startDayKey: Int,
        endDayKey: Int,
    ): Flow<Map<Long, Set<Int>>> = owner
        .flatMapLatest { activeOwner ->
            dao.observeCompletionsBetween(activeOwner.localId, startDayKey, endDayKey)
        }
        .map(::completionMap)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override val goalCompletionCounts: Flow<Map<Long, Int>> = owner
        .flatMapLatest { activeOwner -> dao.observeGoalCompletionCounts(activeOwner.localId) }
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
        val currentOwner = owner.value
        val hue = mostDistinctHue(dao.getUsedHues(currentOwner.localId))
        val entity = HabitEntity(
            ownerId = currentOwner.localId,
            name = name.trim(),
            colorHue = hue,
            linkedGoalId = linkedGoalId,
            activeStartDayKey = activeStartDayKey,
            activeEndDayKey = activeEndDayKey,
            syncPending = currentOwner.usesCloud,
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
    }.also { notifyCloud(owner.value) }

    override suspend fun toggleCompletion(habitId: Long, dayKey: Int) {
        val currentOwner = owner.value
        database.withTransaction {
            val weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey))
            val state = dao.getToggleState(currentOwner.localId, habitId, dayKey, weekday.name)
                ?: return@withTransaction
            if (state.activeStartDayKey != null && dayKey < state.activeStartDayKey) return@withTransaction
            if (state.activeEndDayKey != null && dayKey > state.activeEndDayKey) return@withTransaction
            if (!state.isScheduled) return@withTransaction
            if (state.isCompleted) {
                dao.softDeleteCompletion(
                    habitId = habitId,
                    dayKey = dayKey,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    syncPending = currentOwner.usesCloud,
                )
            } else {
                dao.upsertCompletion(
                    HabitCompletionEntity(
                        habitId = habitId,
                        dayKey = dayKey,
                        weekday = weekday.name,
                        remoteId = state.completionRemoteId ?: com.nikac.guider.data.database.newRemoteId(),
                        deletedAtEpochMillis = null,
                        syncPending = currentOwner.usesCloud,
                    ),
                )
            }
        }
        notifyCloud(currentOwner)
    }

    override suspend fun deleteHabit(habitId: Long) {
        val currentOwner = owner.value
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.softDeleteCompletionsForHabit(habitId, now, currentOwner.usesCloud)
            dao.softDeleteHabit(
                ownerId = currentOwner.localId,
                habitId = habitId,
                updatedAtEpochMillis = now,
                syncPending = currentOwner.usesCloud,
            )
        }
        notifyCloud(currentOwner)
    }

    override suspend fun deleteHabitsForGoal(goalId: Long) {
        val currentOwner = owner.value
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.softDeleteCompletionsForGoal(
                currentOwner.localId,
                goalId,
                now,
                currentOwner.usesCloud,
            )
            dao.softDeleteForGoal(
                ownerId = currentOwner.localId,
                goalId = goalId,
                updatedAtEpochMillis = now,
                syncPending = currentOwner.usesCloud,
            )
        }
        notifyCloud(currentOwner)
    }

    override suspend fun setGoalPeriod(goalId: Long, startDayKey: Int, endDayKey: Int) {
        val currentOwner = owner.value
        dao.setGoalPeriod(
            ownerId = currentOwner.localId,
            goalId = goalId,
            startDayKey = startDayKey,
            endDayKey = endDayKey,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = currentOwner.usesCloud,
        )
        notifyCloud(currentOwner)
    }

    private fun notifyCloud(changedOwner: DataOwner) {
        if (changedOwner == owner.value && changedOwner.usesCloud) onDataChanged()
    }

    companion object {
        private const val FULL_CIRCLE_DEGREES = 360
        private const val DEFAULT_HUE = 210f

        suspend fun create(
            database: GuiderDatabase,
            scope: CoroutineScope,
            owner: StateFlow<DataOwner>,
            onDataChanged: () -> Unit,
        ): RoomHabitRepository {
            val dao = database.habitDao()
            val todayDayKey = DayKeys.today()
            val recentStartDayKey = DayKeys.addDays(todayDayKey, -(RECENT_DAY_COUNT - 1))
            return coroutineScope {
                val habits = async {
                    owner.flatMapLatest { activeOwner -> dao.observeAll(activeOwner.localId) }
                        .map { records -> records.map(HabitRecord::toModel) }
                        .distinctUntilChanged()
                        .stateInWhileSubscribed(scope)
                }
                val recentCompletions = async {
                    owner.flatMapLatest { activeOwner ->
                        dao.observeCompletionsBetween(
                            activeOwner.localId,
                            recentStartDayKey,
                            todayDayKey,
                        )
                    }
                        .map(::completionMap)
                        .distinctUntilChanged()
                        .flowOn(Dispatchers.Default)
                        .stateInWhileSubscribed(scope)
                }
                RoomHabitRepository(
                    database = database,
                    owner = owner,
                    onDataChanged = onDataChanged,
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
