package com.nikac.guider.domain.time

import java.util.Calendar

object DayKeys {
    fun today(nowEpochMillis: Long = System.currentTimeMillis()): Int =
        fromEpochMillis(nowEpochMillis)

    fun fromEpochMillis(epochMillis: Long): Int =
        fromCalendar(dayCalendar(epochMillis))

    fun toEpochMillis(dayKey: Int): Long =
        calendar(dayKey).timeInMillis

    fun addDays(dayKey: Int, amount: Int): Int =
        fromEpochDay(toEpochDay(dayKey) + amount)

    fun weekday(dayKey: Int): Int =
        Math.floorMod(toEpochDay(dayKey) + 4L, 7L).toInt() + Calendar.SUNDAY

    fun inclusiveRange(startDayKey: Int, endDayKey: Int): IntArray {
        val count = inclusiveDayCount(startDayKey, endDayKey)
        if (count == 0) return IntArray(0)

        val firstEpochDay = toEpochDay(startDayKey)
        return IntArray(count) { index ->
            fromEpochDay(firstEpochDay + index)
        }
    }

    fun inclusiveDayCount(startDayKey: Int, endDayKey: Int): Int {
        if (startDayKey > endDayKey) return 0

        val count = toEpochDay(endDayKey) - toEpochDay(startDayKey) + 1L
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun millisUntilTomorrow(nowEpochMillis: Long = System.currentTimeMillis()): Long {
        val tomorrow = dayCalendar(nowEpochMillis).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return (tomorrow.timeInMillis - nowEpochMillis).coerceAtLeast(1L)
    }

    private fun toEpochDay(dayKey: Int): Long {
        var year = dayKey / 10_000
        val month = dayKey / 100 % 100
        val day = dayKey % 100

        year -= if (month <= 2) 1 else 0
        val era = Math.floorDiv(year, 400)
        val yearOfEra = year - era * 400
        val shiftedMonth = month + if (month > 2) -3 else 9
        val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - DAYS_FROM_CIVIL_EPOCH_TO_UNIX_EPOCH
    }

    private fun fromEpochDay(epochDay: Long): Int {
        val adjustedDay = epochDay + DAYS_FROM_CIVIL_EPOCH_TO_UNIX_EPOCH
        val era = Math.floorDiv(adjustedDay, 146_097L)
        val dayOfEra = adjustedDay - era * 146_097L
        val yearOfEra =
            (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
        var year = (yearOfEra + era * 400L).toInt()
        val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
        val shiftedMonth = (5L * dayOfYear + 2L) / 153L
        val day = (dayOfYear - (153L * shiftedMonth + 2L) / 5L + 1L).toInt()
        val month = (shiftedMonth + if (shiftedMonth < 10L) 3L else -9L).toInt()
        if (month <= 2) year++

        return year * 10_000 + month * 100 + day
    }

    private fun calendar(dayKey: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(
                dayKey / 10_000,
                dayKey / 100 % 100 - 1,
                dayKey % 100,
                0,
                0,
                0,
            )
            set(Calendar.MILLISECOND, 0)
        }

    private fun dayCalendar(epochMillis: Long): Calendar =
        Calendar.getInstance().apply {
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

    private const val DAYS_FROM_CIVIL_EPOCH_TO_UNIX_EPOCH = 719_468L
}
