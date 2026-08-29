package com.example.guider.ui.screens.habits

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.guider.GuiderApplication
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.habits.HabitTrackerRange
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.habits.isScheduledOn
import com.example.guider.domain.time.DayKeys
import com.example.guider.ui.util.ImmutableListSnapshot
import com.example.guider.ui.util.ImmutableMapSnapshot
import com.example.guider.ui.util.toImmutableSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Immutable
internal data class HabitsUiState(
    val habits: ImmutableListSnapshot<Habit> = ImmutableListSnapshot(emptyList()),
    val streaksByHabitId: ImmutableMapSnapshot<Long, Int> = ImmutableMapSnapshot(emptyMap()),
    val range: HabitTrackerRange = HabitTrackerRange.WEEK,
    val periodOffset: Int = 0,
    val period: HabitPeriod,
)

private data class RecentDay(val key: Int, val weekday: HabitWeekday)

private data class VisiblePeriodData(
    val period: HabitPeriod,
    val completions: Map<Long, Set<Int>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository: HabitRepository =
        (application as GuiderApplication).habitRepository

    private val nowEpochMillis = System.currentTimeMillis()
    private val todayDayKey = DayKeys.today(nowEpochMillis)
    private val recentStartDayKey = DayKeys.addDays(todayDayKey, -(RECENT_DAY_COUNT - 1))
    private val selectedRange = savedStateHandle.getStateFlow(
        RANGE_KEY,
        HabitTrackerRange.WEEK,
    )
    private val selectedPeriodOffset = savedStateHandle.getStateFlow(PERIOD_OFFSET_KEY, 0)
    private val initialPeriod: HabitPeriod = HabitCalendar.period(
        range = selectedRange.value,
        offset = selectedPeriodOffset.value,
        nowEpochMillis = nowEpochMillis,
    )
    private val period: StateFlow<HabitPeriod> = combine(
        selectedRange,
        selectedPeriodOffset,
        ::Pair,
    ).drop(1).mapLatest { (range, offset) ->
        withContext(Dispatchers.Default) {
            HabitCalendar.period(range, offset, nowEpochMillis)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initialPeriod,
        )
    private val recentDays = List(RECENT_DAY_COUNT) { index ->
        val dayKey = DayKeys.addDays(todayDayKey, -index)
        RecentDay(
            key = dayKey,
            weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey)),
        )
    }

    private val recentCompletions = repository.recentCompletions
    private val visiblePeriodData = period
        .flatMapLatest { visiblePeriod ->
            if (visiblePeriod.startDayKey >= recentStartDayKey) {
                flowOf(VisiblePeriodData(visiblePeriod, emptyMap()))
            } else {
                repository.observeCompletionsBetween(
                    startDayKey = visiblePeriod.startDayKey,
                    endDayKey = minOf(visiblePeriod.endDayKey, todayDayKey),
                ).map { completions -> VisiblePeriodData(visiblePeriod, completions) }
            }
        }

    internal val uiState = combine(
        repository.habits,
        recentCompletions,
        visiblePeriodData,
    ) { habits, recent, visibleData ->
        withContext(Dispatchers.Default) {
            val completedByHabit = mergeCompletions(recent, visibleData.completions)
            val habitsWithCompletions = habits.map { habit ->
                habit.copy(completedDayKeys = completedByHabit[habit.id].orEmpty())
            }
            val streaks = habitsWithCompletions.associate { habit ->
                habit.id to currentStreak(habit)
            }
            HabitsUiState(
                habits = habitsWithCompletions.toImmutableSnapshot(),
                streaksByHabitId = streaks.toImmutableSnapshot(),
                range = visibleData.period.range,
                periodOffset = visibleData.period.offset,
                period = visibleData.period,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = HabitsUiState(
            habits = repository.habits.value.map { habit ->
                habit.copy(
                    completedDayKeys = repository.recentCompletions.value[habit.id].orEmpty(),
                )
            }.toImmutableSnapshot(),
            range = initialPeriod.range,
            periodOffset = initialPeriod.offset,
            period = initialPeriod,
        ),
    )

    fun selectRange(range: HabitTrackerRange) {
        savedStateHandle[PERIOD_OFFSET_KEY] = 0
        savedStateHandle[RANGE_KEY] = range
    }

    fun showPreviousPeriod() {
        savedStateHandle[PERIOD_OFFSET_KEY] = selectedPeriodOffset.value - 1
    }

    fun showNextPeriod() {
        savedStateHandle[PERIOD_OFFSET_KEY] = (selectedPeriodOffset.value + 1).coerceAtMost(0)
    }

    fun showCurrentPeriod() {
        savedStateHandle[PERIOD_OFFSET_KEY] = 0
    }

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

    private fun currentStreak(habit: Habit): Int {
        var streak = 0
        var scheduledIndex = 0
        val allowIncompleteFirstDay = recentDays.firstOrNull()?.let { today ->
            habit.isScheduledOn(today.key, today.weekday)
        } == true

        for (day in recentDays) {
            if (!habit.isScheduledOn(day.key, day.weekday)) continue
            if (day.key in habit.completedDayKeys) {
                streak++
            } else if (scheduledIndex != 0 || !allowIncompleteFirstDay) {
                break
            }
            scheduledIndex++
        }
        return streak
    }

    private fun mergeCompletions(
        recent: Map<Long, Set<Int>>,
        visible: Map<Long, Set<Int>>,
    ): Map<Long, Set<Int>> {
        if (visible.isEmpty()) return recent
        val merged = LinkedHashMap<Long, Set<Int>>(recent.size + visible.size)
        merged.putAll(recent)
        visible.forEach { (habitId, dayKeys) ->
            val recentKeys = merged[habitId]
            merged[habitId] = if (recentKeys == null) {
                dayKeys
            } else {
                buildSet(recentKeys.size + dayKeys.size) {
                    addAll(recentKeys)
                    addAll(dayKeys)
                }
            }
        }
        return merged
    }

    private companion object {
        const val RECENT_DAY_COUNT = 366
        const val RANGE_KEY = "habit_tracker_range"
        const val PERIOD_OFFSET_KEY = "habit_period_offset"
    }
}
