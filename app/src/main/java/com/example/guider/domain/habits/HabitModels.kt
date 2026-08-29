package com.example.guider.domain.habits

import androidx.compose.runtime.Immutable

@Immutable
data class Habit(
    val id: Long,
    val name: String,
    val colorHue: Float,
    val completedDayKeys: Set<Int> = emptySet(),
    val scheduledWeekdays: Set<HabitWeekday> = HabitWeekday.entries.toSet(),
    val linkedGoalId: Long? = null,
    val activeStartDayKey: Int? = null,
    val activeEndDayKey: Int? = null,
)

fun Habit.isScheduledOn(dayKey: Int, weekday: HabitWeekday): Boolean =
    weekday in scheduledWeekdays &&
        (activeStartDayKey == null || dayKey >= activeStartDayKey) &&
        (activeEndDayKey == null || dayKey <= activeEndDayKey)

enum class HabitWeekday(
    val calendarValue: Int,
    val shortLabel: String,
) {
    MONDAY(java.util.Calendar.MONDAY, "M"),
    TUESDAY(java.util.Calendar.TUESDAY, "T"),
    WEDNESDAY(java.util.Calendar.WEDNESDAY, "W"),
    THURSDAY(java.util.Calendar.THURSDAY, "T"),
    FRIDAY(java.util.Calendar.FRIDAY, "F"),
    SATURDAY(java.util.Calendar.SATURDAY, "S"),
    SUNDAY(java.util.Calendar.SUNDAY, "S"),
    ;

    companion object {
        private val calendarValues = arrayOfNulls<HabitWeekday>(8).apply {
            entries.forEach { weekday -> this[weekday.calendarValue] = weekday }
        }

        fun fromCalendarValue(value: Int): HabitWeekday =
            requireNotNull(calendarValues.getOrNull(value)) {
                "Unsupported Calendar weekday value: $value"
            }
    }
}

enum class HabitTrackerRange(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
}
