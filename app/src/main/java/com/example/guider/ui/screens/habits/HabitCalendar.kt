package com.example.guider.ui.screens.habits

import androidx.compose.runtime.Immutable
import com.example.guider.domain.habits.HabitTrackerRange
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.util.LocalizedFormatters
import java.util.Calendar

@Immutable
internal data class HabitDay(
    val key: Int,
    val dayName: String,
    val dayNumber: String,
    val fullLabel: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val weekday: HabitWeekday,
)

@Immutable
internal data class HabitPeriod(
    val range: HabitTrackerRange,
    val offset: Int,
    val title: String,
    val days: List<HabitDay>,
) {
    val startDayKey: Int
        get() = days.first().key

    val endDayKey: Int
        get() = days.last().key
}

internal object HabitCalendar {
    fun period(
        range: HabitTrackerRange,
        offset: Int,
        nowEpochMillis: Long,
    ): HabitPeriod = when (range) {
        HabitTrackerRange.WEEK -> week(offset, nowEpochMillis)
        HabitTrackerRange.MONTH -> month(offset, nowEpochMillis)
    }

    private fun week(offset: Int, nowEpochMillis: Long): HabitPeriod {
        val today = dayCalendar(nowEpochMillis)
        val start = (today.clone() as Calendar).apply {
            val daysFromMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysFromMonday + offset * 7)
        }
        val days = buildDays(start = start, count = 7, today = today, includeLabels = true)
        val startMonth = LocalizedFormatters.formatDate("MMM", start.timeInMillis)
        val endCalendar = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        val endMonth = LocalizedFormatters.formatDate("MMM", endCalendar.timeInMillis)
        val title = if (startMonth == endMonth) {
            "$startMonth ${start.get(Calendar.DAY_OF_MONTH)}–${endCalendar.get(Calendar.DAY_OF_MONTH)}"
        } else {
            "$startMonth ${start.get(Calendar.DAY_OF_MONTH)} – " +
                "$endMonth ${endCalendar.get(Calendar.DAY_OF_MONTH)}"
        }
        return HabitPeriod(
            range = HabitTrackerRange.WEEK,
            offset = offset,
            title = title,
            days = days,
        )
    }

    private fun month(offset: Int, nowEpochMillis: Long): HabitPeriod {
        val today = dayCalendar(nowEpochMillis)
        val end = (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, offset * MONTH_VIEW_DAY_COUNT)
        }
        val start = (end.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -(MONTH_VIEW_DAY_COUNT - 1))
        }
        val title = "${LocalizedFormatters.formatDate("MMM d", start.timeInMillis)} – " +
            LocalizedFormatters.formatDate("MMM d", end.timeInMillis)
        return HabitPeriod(
            range = HabitTrackerRange.MONTH,
            offset = offset,
            title = title,
            days = buildDays(
                start = start,
                count = MONTH_VIEW_DAY_COUNT,
                today = today,
                includeLabels = false,
            ),
        )
    }

    private fun buildDays(
        start: Calendar,
        count: Int,
        today: Calendar,
        includeLabels: Boolean,
    ): List<HabitDay> {
        val todayKey = dayKey(today)
        return buildList(count) {
            val cursor = start.clone() as Calendar
            repeat(count) {
                val cursorKey = dayKey(cursor)
                add(
                    HabitDay(
                        key = cursorKey,
                        dayName = if (includeLabels) {
                            LocalizedFormatters.formatDate("EEE", cursor.timeInMillis).take(2)
                        } else {
                            ""
                        },
                        dayNumber = cursor.get(Calendar.DAY_OF_MONTH).toString(),
                        fullLabel = if (includeLabels) {
                            LocalizedFormatters.formatDate("EEEE, MMMM d", cursor.timeInMillis)
                        } else {
                            ""
                        },
                        isToday = cursorKey == todayKey,
                        isFuture = cursor.timeInMillis > today.timeInMillis,
                        weekday = HabitWeekday.fromCalendarValue(
                            cursor.get(Calendar.DAY_OF_WEEK),
                        ),
                    ),
                )
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private fun dayCalendar(epochMillis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun dayKey(calendar: Calendar): Int =
        calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)

    private const val MONTH_VIEW_DAY_COUNT = 30
}
