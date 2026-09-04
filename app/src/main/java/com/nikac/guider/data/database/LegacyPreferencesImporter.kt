package com.nikac.guider.data.database

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.nikac.guider.domain.goals.Goal
import com.nikac.guider.domain.collections.toImmutableSnapshot
import com.nikac.guider.domain.goals.GoalType
import com.nikac.guider.domain.habits.Habit
import com.nikac.guider.domain.habits.HabitWeekday
import com.nikac.guider.domain.money.MoneyLedger
import com.nikac.guider.domain.money.Spending
import com.nikac.guider.domain.sleep.ActiveSleepSession
import com.nikac.guider.domain.sleep.SleepRecord
import com.nikac.guider.domain.time.DayKeys
import com.nikac.guider.models.DailyTask
import com.nikac.guider.models.TaskCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Imports the pre-Room storage exactly once, including first-run starter content. */
class LegacyPreferencesImporter(private val context: Context) {
    suspend fun importIfNeeded(database: GuiderDatabase) {
        if (database.appMetadataDao().getValue(IMPORT_KEY) != null) return

        val snapshot = withContext(Dispatchers.IO) { readSnapshot() }
        database.withTransaction {
            if (database.appMetadataDao().getValue(IMPORT_KEY) != null) {
                return@withTransaction
            }

            val goalEntities = snapshot.goals.map(Goal::toEntity)
            database.goalDao().insertAll(goalEntities)
            val validGoalIds = goalEntities.mapTo(HashSet()) { it.id }

            database.dailyTaskDao().insertAll(
                snapshot.tasks.map { task ->
                    task.toEntity(
                        linkedGoalId = task.linkedGoalId?.takeIf(validGoalIds::contains),
                    )
                },
            )

            val habitEntities = snapshot.habits.map { habit ->
                habit.toEntity(
                    linkedGoalId = habit.linkedGoalId?.takeIf(validGoalIds::contains),
                )
            }
            database.habitDao().insertHabits(habitEntities)
            database.habitDao().insertWeekdays(
                snapshot.habits.flatMap { habit ->
                    habit.scheduledWeekdays.map { weekday ->
                        HabitWeekdayEntity(habitId = habit.id, weekday = weekday.name)
                    }
                },
            )
            database.habitDao().insertCompletions(
                snapshot.habits.flatMap { habit ->
                    habit.completedDayKeys.map { dayKey ->
                        HabitCompletionEntity(
                            habitId = habit.id,
                            dayKey = dayKey,
                            weekday = HabitWeekday.fromCalendarValue(DayKeys.weekday(dayKey)).name,
                        )
                    }
                },
            )

            snapshot.activeSleepSession?.let { session ->
                database.sleepDao().setActiveSession(session.toEntity())
            }
            database.sleepDao().insertRecords(snapshot.sleepHistory.map(SleepRecord::toEntity))
            database.sleepDao().trimHistory(MAX_SLEEP_HISTORY_RECORDS)

            database.moneyDao().setState(
                MoneyStateEntity(periodStartDayKey = snapshot.moneyLedger.periodStartDayKey),
            )
            database.moneyDao().insertSpendings(
                snapshot.moneyLedger.spendings.map(Spending::toEntity),
            )
            database.appMetadataDao().set(
                AppMetadataEntity(key = IMPORT_KEY, value = "1"),
            )
        }
    }

    private fun readSnapshot(): LegacySnapshot {
        val taskPreferences = preferences(TASK_PREFERENCES)
        val goalPreferences = preferences(GOAL_PREFERENCES)
        val habitPreferences = preferences(HABIT_PREFERENCES)
        val sleepPreferences = preferences(SLEEP_PREFERENCES)
        val moneyPreferences = preferences(MONEY_PREFERENCES)
        return LegacySnapshot(
            tasks = readTasks(taskPreferences),
            goals = readGoals(goalPreferences),
            habits = readHabits(habitPreferences),
            activeSleepSession = readActiveSleepSession(sleepPreferences),
            sleepHistory = readSleepHistory(sleepPreferences),
            moneyLedger = readMoneyLedger(moneyPreferences),
        )
    }

    private fun preferences(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun readTasks(preferences: SharedPreferences): List<DailyTask> {
        val encoded = preferences.getString(KEY_TASKS, null) ?: return starterTasks()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        DailyTask(
                            id = item.getLong("id"),
                            taskCategory = TaskCategory.valueOf(item.getString("category")),
                            title = item.getString("title"),
                            isFinished = item.getBoolean("finished"),
                            createdDayKey = item.getInt("createdDay"),
                            completedDayKey = item.nullableInt("completedDay"),
                            linkedGoalId = item.nullableLong("linkedGoalId"),
                        ),
                    )
                }
            }
        }.getOrElse { starterTasks() }
    }

    private fun readGoals(preferences: SharedPreferences): List<Goal> {
        val encoded = preferences.getString(KEY_GOALS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val type = GoalType.valueOf(item.getString("type"))
                    val createdDayKey = item.getInt("createdDay")
                    val startDayKey = if (type == GoalType.PERIODIC) {
                        item.optInt("startDay", createdDayKey)
                    } else {
                        null
                    }
                    add(
                        Goal(
                            id = item.getLong("id"),
                            title = item.getString("title"),
                            type = type,
                            createdDayKey = createdDayKey,
                            achievedDayKey = item.nullableInt("achievedDay"),
                            startDayKey = startDayKey,
                            endDayKey = if (type == GoalType.PERIODIC) {
                                item.optInt(
                                    "endDay",
                                    DayKeys.addDays(
                                        requireNotNull(startDayKey),
                                        DEFAULT_GOAL_PERIOD_DAYS - 1,
                                    ),
                                ).coerceAtLeast(requireNotNull(startDayKey))
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun readHabits(preferences: SharedPreferences): List<Habit> {
        val encoded = preferences.getString(KEY_HABITS, null) ?: return starterHabits
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val completedDays = item.getJSONArray("completedDays")
                    add(
                        Habit(
                            id = item.getLong("id"),
                            name = item.getString("name"),
                            colorHue = item.getDouble("colorHue").toFloat(),
                            completedDayKeys = buildSet {
                                repeat(completedDays.length()) { dayIndex ->
                                    add(completedDays.getInt(dayIndex))
                                }
                            },
                            scheduledWeekdays = item.optJSONArray("scheduledWeekdays")
                                ?.let(::readWeekdays)
                                ?.ifEmpty { HabitWeekday.entries.toSet() }
                                ?: HabitWeekday.entries.toSet(),
                            linkedGoalId = item.nullableLong("linkedGoalId")?.takeIf { it > 0L },
                            activeStartDayKey = item.nullableInt("activeStartDay"),
                            activeEndDayKey = item.nullableInt("activeEndDay"),
                        ),
                    )
                }
            }
        }.getOrElse { starterHabits }
    }

    private fun readActiveSleepSession(preferences: SharedPreferences): ActiveSleepSession? {
        if (!preferences.contains(KEY_ACTIVE_ACTIVATED_AT)) return null
        return ActiveSleepSession(
            activatedAtEpochMillis = preferences.getLong(KEY_ACTIVE_ACTIVATED_AT, 0L),
            sleepStartsAtEpochMillis = preferences.getLong(KEY_ACTIVE_SLEEP_STARTS_AT, 0L),
        )
    }

    private fun readSleepHistory(preferences: SharedPreferences): List<SleepRecord> {
        val encoded = preferences.getString(KEY_SLEEP_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        SleepRecord(
                            id = item.getLong("id"),
                            activatedAtEpochMillis = item.getLong("activatedAt"),
                            sleepStartsAtEpochMillis = item.getLong("sleepStartsAt"),
                            endedAtEpochMillis = item.getLong("endedAt"),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun readMoneyLedger(preferences: SharedPreferences): MoneyLedger {
        return runCatching {
            val encoded = preferences.getString(KEY_SPENDINGS, null)
            val spendings = if (encoded == null) {
                emptyList()
            } else {
                val array = JSONArray(encoded)
                buildList {
                    repeat(array.length()) { index ->
                        val item = array.getJSONObject(index)
                        add(
                            Spending(
                                id = item.getLong("id"),
                                title = item.getString("title"),
                                amountMinor = item.getLong("amountMinor"),
                                createdAtEpochMillis = item.getLong("createdAt"),
                            ),
                        )
                    }
                }
            }
            MoneyLedger(
                spendings = spendings.toImmutableSnapshot(),
                periodStartDayKey = preferences
                    .takeIf { it.contains(KEY_PERIOD_START) }
                    ?.getInt(KEY_PERIOD_START, 0)
                    ?.takeIf { it > 0 },
            )
        }.getOrElse { MoneyLedger() }
    }

    private fun readWeekdays(array: JSONArray): Set<HabitWeekday> = buildSet {
        repeat(array.length()) { index ->
            runCatching { HabitWeekday.valueOf(array.getString(index)) }
                .getOrNull()
                ?.let(::add)
        }
    }

    private fun starterTasks(): List<DailyTask> {
        val today = DayKeys.today()
        return listOf(
            DailyTask(1L, TaskCategory.HEALTH, "Drink water", false, today),
            DailyTask(2L, TaskCategory.HEALTH, "Take a 30 minute walk", false, today),
            DailyTask(3L, TaskCategory.WORK, "Write the project outline", false, today),
            DailyTask(4L, TaskCategory.MENTAL_HEALTH, "Meditate for 10 minutes", false, today),
            DailyTask(5L, TaskCategory.MENTAL_HEALTH, "Read before bed", false, today),
            DailyTask(6L, TaskCategory.OTHER, "Call family", false, today),
        )
    }

    private data class LegacySnapshot(
        val tasks: List<DailyTask>,
        val goals: List<Goal>,
        val habits: List<Habit>,
        val activeSleepSession: ActiveSleepSession?,
        val sleepHistory: List<SleepRecord>,
        val moneyLedger: MoneyLedger,
    )

    private companion object {
        const val MAX_SLEEP_HISTORY_RECORDS = 365
        const val IMPORT_KEY = "legacy_shared_preferences_imported"
        const val DEFAULT_GOAL_PERIOD_DAYS = 14

        const val TASK_PREFERENCES = "daily_task_tracking"
        const val KEY_TASKS = "tasks"
        const val GOAL_PREFERENCES = "goal_tracking"
        const val KEY_GOALS = "goals"
        const val HABIT_PREFERENCES = "habit_tracking"
        const val KEY_HABITS = "habits"
        const val SLEEP_PREFERENCES = "sleep_tracking"
        const val KEY_ACTIVE_ACTIVATED_AT = "active_activated_at"
        const val KEY_ACTIVE_SLEEP_STARTS_AT = "active_sleep_starts_at"
        const val KEY_SLEEP_HISTORY = "history"
        const val MONEY_PREFERENCES = "money_tracking"
        const val KEY_SPENDINGS = "spendings"
        const val KEY_PERIOD_START = "periodStart"

        val starterHabits = listOf(
            Habit(id = 1L, name = "Drink water", colorHue = 210f),
            Habit(id = 2L, name = "Move for 30 min", colorHue = 142f),
            Habit(id = 3L, name = "Read 10 pages", colorHue = 24f),
        )
    }
}

private fun org.json.JSONObject.nullableInt(key: String): Int? =
    if (isNull(key)) null else getInt(key)

private fun org.json.JSONObject.nullableLong(key: String): Long? =
    if (isNull(key)) null else getLong(key)

private fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    title = title,
    type = type.name,
    createdDayKey = createdDayKey,
    achievedDayKey = achievedDayKey,
    startDayKey = startDayKey,
    endDayKey = endDayKey,
)

private fun DailyTask.toEntity(linkedGoalId: Long?): DailyTaskEntity = DailyTaskEntity(
    id = id,
    category = taskCategory.name,
    title = title,
    isFinished = isFinished,
    createdDayKey = createdDayKey,
    completedDayKey = completedDayKey,
    linkedGoalId = linkedGoalId,
)

private fun Habit.toEntity(linkedGoalId: Long?): HabitEntity = HabitEntity(
    id = id,
    name = name,
    colorHue = colorHue,
    linkedGoalId = linkedGoalId,
    activeStartDayKey = activeStartDayKey,
    activeEndDayKey = activeEndDayKey,
)

private fun ActiveSleepSession.toEntity(): ActiveSleepSessionEntity = ActiveSleepSessionEntity(
    activatedAtEpochMillis = activatedAtEpochMillis,
    sleepStartsAtEpochMillis = sleepStartsAtEpochMillis,
)

private fun SleepRecord.toEntity(): SleepRecordEntity = SleepRecordEntity(
    id = id,
    activatedAtEpochMillis = activatedAtEpochMillis,
    sleepStartsAtEpochMillis = sleepStartsAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
)

private fun Spending.toEntity(): SpendingEntity = SpendingEntity(
    id = id,
    title = title,
    amountMinor = amountMinor,
    createdAtEpochMillis = createdAtEpochMillis,
)
