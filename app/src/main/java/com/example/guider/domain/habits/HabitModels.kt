package com.example.guider.domain.habits

data class Habit(
    val id: Long,
    val name: String,
    val colorHue: Float,
    val completedDayKeys: Set<Int> = emptySet(),
)

enum class HabitTrackerRange(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
}
