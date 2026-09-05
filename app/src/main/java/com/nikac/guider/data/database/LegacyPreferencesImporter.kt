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

/** Imports real pre-Room user data exactly once. */
class LegacyPreferencesImporter(private val context: Context) {
    suspend fun importIfNeeded(database: GuiderDatabase) {
        removePreviouslySeededContentIfNeeded(database)
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

            val initialMoneyPeriodId = newRemoteId()
            database.moneyDao().upsertPeriod(
                MoneyPeriodEntity(
                    remoteId = initialMoneyPeriodId,
                    startDayKey = snapshot.moneyLedger.periodStartDayKey,
                    endDayKey = null,
                ),
            )
            database.moneyDao().setState(
                MoneyStateEntity(
                    currentPeriodRemoteId = initialMoneyPeriodId,
                    periodStartDayKey = snapshot.moneyLedger.periodStartDayKey,
                ),
            )
            database.moneyDao().insertSpendings(
                snapshot.moneyLedger.spendings.map { it.toEntity(initialMoneyPeriodId) },
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
        val encoded = preferences.getString(KEY_TASKS, null) ?: return emptyList()
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
        }.getOrElse { emptyList() }
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
        val encoded = preferences.getString(KEY_HABITS, null) ?: return emptyList()
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
        }.getOrElse { emptyList() }
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

    private suspend fun removePreviouslySeededContentIfNeeded(database: GuiderDatabase) {
        val metadata = database.appMetadataDao()
        if (metadata.getValue(STARTER_CONTENT_CLEANUP_KEY) != null) return

        val tasksWereSeeded = !containsValidArray(preferences(TASK_PREFERENCES), KEY_TASKS)
        val habitsWereSeeded = !containsValidArray(preferences(HABIT_PREFERENCES), KEY_HABITS)
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            val now = System.currentTimeMillis()
            if (tasksWereSeeded) {
                sql.execSQL(
                    """UPDATE daily_tasks
                       SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, ?),
                           updatedAtEpochMillis = ?, syncPending = 1
                       WHERE (id = 1 AND category = 'HEALTH' AND title = 'Drink water')
                          OR (id = 2 AND category = 'HEALTH' AND title = 'Take a 30 minute walk')
                          OR (id = 3 AND category = 'WORK' AND title = 'Write the project outline')
                          OR (id = 4 AND category = 'MENTAL_HEALTH' AND title = 'Meditate for 10 minutes')
                          OR (id = 5 AND category = 'MENTAL_HEALTH' AND title = 'Read before bed')
                          OR (id = 6 AND category = 'OTHER' AND title = 'Call family')""",
                    arrayOf(now, now),
                )
            }
            if (habitsWereSeeded) {
                val starterHabitFilter =
                    """(id = 1 AND name = 'Drink water' AND colorHue = 210.0)
                       OR (id = 2 AND name = 'Move for 30 min' AND colorHue = 142.0)
                       OR (id = 3 AND name = 'Read 10 pages' AND colorHue = 24.0)"""
                sql.execSQL(
                    """UPDATE habit_completions
                       SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, ?),
                           updatedAtEpochMillis = ?, syncPending = 1
                       WHERE habitId IN (SELECT id FROM habits WHERE $starterHabitFilter)""",
                    arrayOf(now, now),
                )
                sql.execSQL(
                    """UPDATE habits
                       SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, ?),
                           updatedAtEpochMillis = ?, syncPending = 1
                       WHERE $starterHabitFilter""",
                    arrayOf(now, now),
                )
            }
            metadata.set(AppMetadataEntity(STARTER_CONTENT_CLEANUP_KEY, "1"))
        }
    }

    private fun containsValidArray(preferences: SharedPreferences, key: String): Boolean {
        if (!preferences.contains(key)) return false
        return runCatching { JSONArray(requireNotNull(preferences.getString(key, null))) }.isSuccess
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
        const val IMPORT_KEY = "legacy_shared_preferences_imported"
        const val STARTER_CONTENT_CLEANUP_KEY = "starter_content_removed"
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

private fun Spending.toEntity(periodRemoteId: String): SpendingEntity = SpendingEntity(
    id = id,
    periodRemoteId = periodRemoteId,
    title = title,
    amountMinor = amountMinor,
    createdAtEpochMillis = createdAtEpochMillis,
)
