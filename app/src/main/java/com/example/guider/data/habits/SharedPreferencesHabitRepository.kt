package com.example.guider.data.habits

import android.content.Context
import androidx.core.content.edit
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class SharedPreferencesHabitRepository(context: Context) : HabitRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableHabits = MutableStateFlow(readHabits())

    override val habits: StateFlow<List<Habit>> = mutableHabits.asStateFlow()

    @Synchronized
    override fun addHabit(name: String): Habit {
        val id = (mutableHabits.value.maxOfOrNull(Habit::id) ?: 0L) + 1L
        val habit = Habit(
            id = id,
            name = name.trim(),
            colorHue = mostDistinctHue(mutableHabits.value),
        )
        persist(mutableHabits.value + habit)
        return habit
    }

    @Synchronized
    override fun toggleCompletion(habitId: Long, dayKey: Int) {
        val updated = mutableHabits.value.map { habit ->
            if (habit.id != habitId) {
                habit
            } else {
                val completedDays = habit.completedDayKeys.toMutableSet().apply {
                    if (!add(dayKey)) remove(dayKey)
                }
                habit.copy(completedDayKeys = completedDays)
            }
        }
        persist(updated)
    }

    @Synchronized
    override fun deleteHabit(habitId: Long) {
        persist(mutableHabits.value.filterNot { it.id == habitId })
    }

    private fun readHabits(): List<Habit> {
        val encoded = preferences.getString(KEY_HABITS, null) ?: return starterHabits
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val completed = item.getJSONArray(JSON_COMPLETED_DAYS)
                    add(
                        Habit(
                            id = item.getLong(JSON_ID),
                            name = item.getString(JSON_NAME),
                            colorHue = item.getDouble(JSON_COLOR_HUE).toFloat(),
                            completedDayKeys = buildSet {
                                repeat(completed.length()) { dayIndex ->
                                    add(completed.getInt(dayIndex))
                                }
                            },
                        ),
                    )
                }
            }
        }.getOrElse { starterHabits }
    }

    private fun persist(habits: List<Habit>) {
        preferences.edit {
            putString(KEY_HABITS, habitsToJson(habits).toString())
        }
        mutableHabits.value = habits
    }

    private fun habitsToJson(habits: List<Habit>): JSONArray = JSONArray().apply {
        habits.forEach { habit ->
            put(
                JSONObject()
                    .put(JSON_ID, habit.id)
                    .put(JSON_NAME, habit.name)
                    .put(JSON_COLOR_HUE, habit.colorHue.toDouble())
                    .put(
                        JSON_COMPLETED_DAYS,
                        JSONArray().apply {
                            habit.completedDayKeys.sorted().forEach(::put)
                        },
                    ),
            )
        }
    }

    private fun mostDistinctHue(habits: List<Habit>): Float {
        if (habits.isEmpty()) return DEFAULT_HUE
        val usedHues = habits.map(Habit::colorHue)
        return (0 until FULL_CIRCLE_DEGREES)
            .maxBy { candidate ->
                usedHues.minOf { usedHue ->
                    val directDistance = abs(candidate - usedHue)
                    minOf(directDistance, FULL_CIRCLE_DEGREES - directDistance)
                }
            }
            .toFloat()
    }

    private companion object {
        const val PREFERENCES_NAME = "habit_tracking"
        const val KEY_HABITS = "habits"

        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_COLOR_HUE = "colorHue"
        const val JSON_COMPLETED_DAYS = "completedDays"

        const val FULL_CIRCLE_DEGREES = 360
        const val DEFAULT_HUE = 210f

        val starterHabits = listOf(
            Habit(id = 1L, name = "Drink water", colorHue = 210f),
            Habit(id = 2L, name = "Move for 30 min", colorHue = 142f),
            Habit(id = 3L, name = "Read 10 pages", colorHue = 24f),
        )
    }
}
