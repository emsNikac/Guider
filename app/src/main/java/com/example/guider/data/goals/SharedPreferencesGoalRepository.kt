package com.example.guider.data.goals

import android.content.Context
import androidx.core.content.edit
import com.example.guider.data.ConflatedStateWriter
import com.example.guider.domain.goals.Goal
import com.example.guider.domain.goals.GoalRepository
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesGoalRepository(
    context: Context,
    persistenceScope: CoroutineScope,
) : GoalRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableGoals = MutableStateFlow(readGoals())
    private val stateWriter = ConflatedStateWriter<List<Goal>>(
        scope = persistenceScope,
        storageName = PREFERENCES_NAME,
    ) { goals ->
        preferences.edit { putString(KEY_GOALS, goalsToJson(goals).toString()) }
    }

    override val goals: StateFlow<List<Goal>> = mutableGoals.asStateFlow()

    init {
        removeCompletedBefore(DayKeys.today())
    }

    @Synchronized
    override fun addGoal(
        title: String,
        type: GoalType,
        startDayKey: Int?,
        endDayKey: Int?,
    ): Goal {
        removeCompletedBefore(DayKeys.today())
        val periodicStart = if (type == GoalType.PERIODIC) {
            startDayKey ?: DayKeys.today()
        } else {
            null
        }
        val periodicEnd = if (type == GoalType.PERIODIC) {
            (endDayKey ?: DayKeys.addDays(periodicStart!!, DEFAULT_PERIOD_DAYS - 1))
                .coerceAtLeast(periodicStart!!)
        } else {
            null
        }
        val goal = Goal(
            id = (mutableGoals.value.maxOfOrNull(Goal::id) ?: 0L) + 1L,
            title = title.trim(),
            type = type,
            createdDayKey = DayKeys.today(),
            startDayKey = periodicStart,
            endDayKey = periodicEnd,
        )
        persist(mutableGoals.value + goal)
        return goal
    }

    @Synchronized
    override fun toggleAchievement(goalId: Long, dayKey: Int) {
        val updated = mutableGoals.value.map { goal ->
            if (goal.id != goalId || goal.type != GoalType.ONE_TIME) goal
            else goal.copy(achievedDayKey = if (goal.achievedDayKey == null) dayKey else null)
        }
        persist(updated)
    }

    @Synchronized
    override fun removeCompletedBefore(dayKey: Int) {
        val current = mutableGoals.value
        val updated = current.filterNot { goal ->
            goal.type == GoalType.ONE_TIME &&
                goal.achievedDayKey?.let { it < dayKey } == true
        }
        if (updated != current) persist(updated)
    }

    @Synchronized
    override fun deleteGoal(goalId: Long) {
        persist(mutableGoals.value.filterNot { it.id == goalId })
    }

    private fun readGoals(): List<Goal> {
        val encoded = preferences.getString(KEY_GOALS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val type = GoalType.valueOf(item.getString(JSON_TYPE))
                    val createdDayKey = item.getInt(JSON_CREATED_DAY)
                    val migratedStartDayKey = if (type == GoalType.PERIODIC) {
                        item.optInt(JSON_START_DAY, createdDayKey)
                    } else {
                        null
                    }
                    add(
                        Goal(
                            id = item.getLong(JSON_ID),
                            title = item.getString(JSON_TITLE),
                            type = type,
                            createdDayKey = createdDayKey,
                            achievedDayKey = if (item.isNull(JSON_ACHIEVED_DAY)) {
                                null
                            } else {
                                item.getInt(JSON_ACHIEVED_DAY)
                            },
                            startDayKey = migratedStartDayKey,
                            endDayKey = if (type == GoalType.PERIODIC) {
                                item.optInt(
                                    JSON_END_DAY,
                                    DayKeys.addDays(
                                        migratedStartDayKey!!,
                                        DEFAULT_PERIOD_DAYS - 1,
                                    ),
                                ).coerceAtLeast(migratedStartDayKey)
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun persist(goals: List<Goal>) {
        mutableGoals.value = goals
        stateWriter.submit(goals)
    }

    private fun goalsToJson(goals: List<Goal>): JSONArray = JSONArray().apply {
        goals.forEach { goal ->
            put(
                JSONObject()
                    .put(JSON_ID, goal.id)
                    .put(JSON_TITLE, goal.title)
                    .put(JSON_TYPE, goal.type.name)
                    .put(JSON_CREATED_DAY, goal.createdDayKey)
                    .put(JSON_ACHIEVED_DAY, goal.achievedDayKey ?: JSONObject.NULL)
                    .put(JSON_START_DAY, goal.startDayKey ?: JSONObject.NULL)
                    .put(JSON_END_DAY, goal.endDayKey ?: JSONObject.NULL),
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "goal_tracking"
        const val KEY_GOALS = "goals"
        const val JSON_ID = "id"
        const val JSON_TITLE = "title"
        const val JSON_TYPE = "type"
        const val JSON_CREATED_DAY = "createdDay"
        const val JSON_ACHIEVED_DAY = "achievedDay"
        const val JSON_START_DAY = "startDay"
        const val JSON_END_DAY = "endDay"
        const val DEFAULT_PERIOD_DAYS = 14
    }
}
