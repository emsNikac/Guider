package com.example.guider.domain.goals

import androidx.compose.runtime.Immutable
import com.example.guider.domain.habits.HabitWeekday

enum class GoalType(val label: String) {
    ONE_TIME("One-time"),
    PERIODIC("Periodic"),
}

@Immutable
data class Goal(
    val id: Long,
    val title: String,
    val type: GoalType,
    val createdDayKey: Int,
    val achievedDayKey: Int? = null,
    val startDayKey: Int? = null,
    val endDayKey: Int? = null,
)

fun Goal.isActive(todayDayKey: Int): Boolean = when (type) {
    GoalType.ONE_TIME -> achievedDayKey == null
    GoalType.PERIODIC -> (endDayKey ?: Int.MAX_VALUE) >= todayDayKey
}

@Immutable
data class GoalHabitInput(
    val name: String,
    val scheduledWeekdays: Set<HabitWeekday>,
)

@Immutable
data class GoalProgress(
    val completedCheckIns: Int,
    val expectedCheckIns: Int,
) {
    val fraction: Float
        get() = if (expectedCheckIns == 0) 0f else {
            completedCheckIns.toFloat() / expectedCheckIns
        }

    val percentage: Int
        get() = (fraction * 100).toInt().coerceIn(0, 100)
}
