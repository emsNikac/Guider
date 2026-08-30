package com.example.guider.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTaskDao {
    @Query("SELECT * FROM daily_tasks ORDER BY id")
    fun observeAll(): Flow<List<DailyTaskEntity>>

    @Insert
    suspend fun insert(task: DailyTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<DailyTaskEntity>)

    @Query(
        """
        UPDATE daily_tasks
        SET isFinished = :finished,
            completedDayKey = CASE WHEN :finished THEN :dayKey ELSE NULL END
        WHERE id = :taskId
        """,
    )
    suspend fun setFinished(taskId: Long, finished: Boolean, dayKey: Int)

    @Query(
        """
        DELETE FROM daily_tasks
        WHERE isFinished = 1 AND completedDayKey IS NOT NULL AND completedDayKey < :dayKey
        """,
    )
    suspend fun removeCompletedBefore(dayKey: Int)

    @Query("UPDATE daily_tasks SET linkedGoalId = NULL WHERE linkedGoalId = :goalId")
    suspend fun clearGoalLink(goalId: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Query(
        """
        UPDATE goals
        SET achievedDayKey = CASE WHEN achievedDayKey IS NULL THEN :dayKey ELSE NULL END
        WHERE id = :goalId AND type = 'ONE_TIME'
        """,
    )
    suspend fun toggleAchievement(goalId: Long, dayKey: Int)

    @Query(
        """
        DELETE FROM goals
        WHERE type = 'ONE_TIME' AND achievedDayKey IS NOT NULL AND achievedDayKey < :dayKey
        """,
    )
    suspend fun removeCompletedBefore(dayKey: Int)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun delete(goalId: Long)
}

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY id")
    fun observeAll(): Flow<List<HabitRecord>>

    @Query("SELECT colorHue FROM habits ORDER BY id")
    suspend fun getUsedHues(): List<Float>

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeekdays(weekdays: List<HabitWeekdayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(completions: List<HabitCompletionEntity>)

    @Query(
        """
        SELECT h.activeStartDayKey,
               h.activeEndDayKey,
               EXISTS(
                   SELECT 1 FROM habit_weekdays w
                   WHERE w.habitId = h.id AND w.weekday = :weekday
               ) AS isScheduled,
               EXISTS(
                   SELECT 1 FROM habit_completions c
                   WHERE c.habitId = h.id AND c.dayKey = :dayKey
               ) AS isCompleted
        FROM habits h
        WHERE h.id = :habitId
        """,
    )
    suspend fun getToggleState(
        habitId: Long,
        dayKey: Int,
        weekday: String,
    ): HabitToggleState?

    @Query(
        """
        SELECT * FROM habit_completions
        WHERE dayKey BETWEEN :startDayKey AND :endDayKey
        ORDER BY habitId, dayKey
        """,
    )
    fun observeCompletionsBetween(
        startDayKey: Int,
        endDayKey: Int,
    ): Flow<List<HabitCompletionEntity>>

    @Query(
        """
        SELECT h.linkedGoalId AS goalId, COUNT(*) AS completedCheckIns
        FROM habit_completions c
        JOIN habits h ON h.id = c.habitId
        JOIN habit_weekdays w
          ON w.habitId = c.habitId
         AND w.weekday = c.weekday
        WHERE h.linkedGoalId IS NOT NULL
          AND (h.activeStartDayKey IS NULL OR c.dayKey >= h.activeStartDayKey)
          AND (h.activeEndDayKey IS NULL OR c.dayKey <= h.activeEndDayKey)
        GROUP BY h.linkedGoalId
        """,
    )
    fun observeGoalCompletionCounts(): Flow<List<GoalCompletionCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun deleteCompletion(habitId: Long, dayKey: Int)

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long)

    @Query("DELETE FROM habits WHERE linkedGoalId = :goalId")
    suspend fun deleteHabitsForGoal(goalId: Long)

    @Query(
        """
        UPDATE habits
        SET activeStartDayKey = :startDayKey, activeEndDayKey = :endDayKey
        WHERE linkedGoalId = :goalId
        """,
    )
    suspend fun setGoalPeriod(goalId: Long, startDayKey: Int, endDayKey: Int)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM active_sleep_session WHERE singletonId = 1")
    fun observeActiveSession(): Flow<ActiveSleepSessionEntity?>

    @Query("SELECT * FROM active_sleep_session WHERE singletonId = 1")
    suspend fun getActiveSession(): ActiveSleepSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveSession(session: ActiveSleepSessionEntity)

    @Query("DELETE FROM active_sleep_session")
    suspend fun clearActiveSession()

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM sleep_records
            ORDER BY endedAtEpochMillis DESC, id DESC
            LIMIT :limit
        )
        ORDER BY endedAtEpochMillis, id
        """,
    )
    fun observeHistory(limit: Int): Flow<List<SleepRecordEntity>>

    @Insert
    suspend fun insertRecord(record: SleepRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<SleepRecordEntity>)

    @Query(
        """
        DELETE FROM sleep_records
        WHERE id NOT IN (
            SELECT id FROM sleep_records ORDER BY endedAtEpochMillis DESC, id DESC LIMIT :limit
        )
        """,
    )
    suspend fun trimHistory(limit: Int)
}

@Dao
interface MoneyDao {
    @Query(
        """
        SELECT state.periodStartDayKey AS periodStartDayKey,
               spending.id AS spendingId,
               spending.title AS spendingTitle,
               spending.amountMinor AS spendingAmountMinor,
               spending.createdAtEpochMillis AS spendingCreatedAtEpochMillis
        FROM money_state state
        LEFT JOIN spendings spending ON spending.ledgerId = state.id
        WHERE state.id = 1
        ORDER BY spending.createdAtEpochMillis DESC, spending.id ASC
        """,
    )
    fun observeLedger(): Flow<List<MoneyLedgerRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: MoneyStateEntity)

    @Query(
        """
        UPDATE money_state
        SET periodStartDayKey = COALESCE(periodStartDayKey, :dayKey)
        WHERE id = 1
        """,
    )
    suspend fun setPeriodStartIfMissing(dayKey: Int)

    @Insert
    suspend fun insertSpending(spending: SpendingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpendings(spendings: List<SpendingEntity>)

    @Query("UPDATE spendings SET title = :title, amountMinor = :amountMinor WHERE id = :spendingId")
    suspend fun editSpending(spendingId: Long, title: String, amountMinor: Long)

    @Query("DELETE FROM spendings WHERE id = :spendingId")
    suspend fun deleteSpending(spendingId: Long)

    @Query("DELETE FROM spendings")
    suspend fun clearSpendings()
}

@Dao
interface AppMetadataDao {
    @Query("SELECT value FROM app_metadata WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(metadata: AppMetadataEntity)
}
