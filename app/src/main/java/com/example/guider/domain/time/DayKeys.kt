package com.example.guider.domain.time

import java.util.Calendar

object DayKeys {
    fun today(nowEpochMillis: Long = System.currentTimeMillis()): Int =
        fromEpochMillis(nowEpochMillis)

    fun fromEpochMillis(epochMillis: Long): Int = fromCalendar(dayCalendar(epochMillis))

    fun toEpochMillis(dayKey: Int): Long = calendar(dayKey).timeInMillis

    fun addDays(dayKey: Int, amount: Int): Int = fromCalendar(
        calendar(dayKey).apply { add(Calendar.DAY_OF_YEAR, amount) },
    )

    fun weekday(dayKey: Int): Int = calendar(dayKey).get(Calendar.DAY_OF_WEEK)

    fun inclusiveRange(startDayKey: Int, endDayKey: Int): List<Int> {
        if (startDayKey > endDayKey) return emptyList()
        val cursor = calendar(startDayKey)
        val end = calendar(endDayKey)
        return buildList {
            while (!cursor.after(end)) {
                add(fromCalendar(cursor))
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    fun inclusiveDayCount(startDayKey: Int, endDayKey: Int): Int =
        inclusiveRange(startDayKey, endDayKey).size

    fun millisUntilTomorrow(nowEpochMillis: Long = System.currentTimeMillis()): Long {
        val tomorrow = dayCalendar(nowEpochMillis).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return (tomorrow.timeInMillis - nowEpochMillis).coerceAtLeast(1L)
    }

    private fun calendar(dayKey: Int): Calendar = Calendar.getInstance().apply {
        clear()
        set(
            dayKey / 10_000,
            dayKey / 100 % 100 - 1,
            dayKey % 100,
        )
    }

    private fun dayCalendar(epochMillis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun fromCalendar(calendar: Calendar): Int =
        calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)
}
