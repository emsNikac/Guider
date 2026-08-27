package com.example.guider.ui.screens.habits

import com.example.guider.domain.habits.HabitTrackerRange
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.util.LocalizedFormatters
import java.util.Calendar

internal data class HabitDay(
    val key: Int,
    val dayName: String,
    val dayNumber: String,
    val fullLabel: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val weekday: HabitWeekday,
)

internal data class HabitPeriod(
    val title: String,
    val days: List<HabitDay>,
)

internal object HabitCalendar {
    fun period(
        range: HabitTrackerRange,
        offset: Int,
        nowEpochMillis: Long,
    ): HabitPeriod = when (range) {
        HabitTrackerRange.WEEK -> week(offset, nowEpochMillis)
        HabitTrackerRange.MONTH -> month(offset, nowEpochMillis)
    }

    fun recentDayKeys(nowEpochMillis: Long, count: Int): List<Int> {
        val day = dayCalendar(nowEpochMillis)
        return buildList(count) {
            repeat(count) {
                add(dayKey(day))
                day.add(Calendar.DAY_OF_YEAR, -1)
            }
        }
    }

    fun dayKey(epochMillis: Long): Int = dayKey(dayCalendar(epochMillis))

    private fun week(offset: Int, nowEpochMillis: Long): HabitPeriod {
        val today = dayCalendar(nowEpochMillis)
        val start = (today.clone() as Calendar).apply {
            val daysFromMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysFromMonday + offset * 7)
        }
        val days = buildDays(start = start, count = 7, today = today)
        val startMonth = LocalizedFormatters.formatDate("MMM", start.timeInMillis)
        val endCalendar = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        val endMonth = LocalizedFormatters.formatDate("MMM", endCalendar.timeInMillis)
        val title = if (startMonth == endMonth) {
            "$startMonth ${start.get(Calendar.DAY_OF_MONTH)}–${endCalendar.get(Calendar.DAY_OF_MONTH)}"
        } else {
            "$startMonth ${start.get(Calendar.DAY_OF_MONTH)} – " +
                "$endMonth ${endCalendar.get(Calendar.DAY_OF_MONTH)}"
        }
        return HabitPeriod(title = title, days = days)
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
            title = title,
            days = buildDays(
                start = start,
                count = MONTH_VIEW_DAY_COUNT,
                today = today,
            ),
        )
    }

    private fun buildDays(
        start: Calendar,
        count: Int,
        today: Calendar,
    ): List<HabitDay> {
        return buildList(count) {
            val cursor = start.clone() as Calendar
            repeat(count) {
                add(
                    HabitDay(
                        key = dayKey(cursor),
                        dayName = LocalizedFormatters
                            .formatDate("EEE", cursor.timeInMillis)
                            .take(2),
                        dayNumber = cursor.get(Calendar.DAY_OF_MONTH).toString(),
                        fullLabel = LocalizedFormatters.formatDate(
                            "EEEE, MMMM d",
                            cursor.timeInMillis,
                        ),
                        isToday = dayKey(cursor) == dayKey(today),
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
