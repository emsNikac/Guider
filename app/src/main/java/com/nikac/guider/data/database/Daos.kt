package com.nikac.guider.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTaskDao {
    @Query(
        """SELECT * FROM daily_tasks
           WHERE ownerId = :ownerId AND archivedAtEpochMillis IS NULL AND deletedAtEpochMillis IS NULL
           ORDER BY id""",
    )
    fun observeAll(ownerId: String): Flow<List<DailyTaskEntity>>

    @Insert suspend fun insert(task: DailyTaskEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(tasks: List<DailyTaskEntity>)
    @Upsert suspend fun upsert(task: DailyTaskEntity)

    @Query("SELECT * FROM daily_tasks WHERE ownerId = :ownerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(ownerId: String, remoteId: String): DailyTaskEntity?

    @Query("SELECT * FROM daily_tasks WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPending(ownerId: String): List<DailyTaskEntity>

    @Query(
        """UPDATE daily_tasks SET syncPending = 0
           WHERE ownerId = :ownerId AND remoteId = :remoteId
             AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markSynced(ownerId: String, remoteId: String, updatedAtEpochMillis: Long)

    @Query(
        """UPDATE daily_tasks
           SET isFinished = :finished,
               completedDayKey = CASE WHEN :finished THEN :dayKey ELSE NULL END,
               updatedAtEpochMillis = :updatedAtEpochMillis,
               syncPending = :syncPending
           WHERE ownerId = :ownerId AND id = :taskId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun setFinished(
        ownerId: String,
        taskId: Long,
        finished: Boolean,
        dayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE daily_tasks
           SET archivedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis,
               syncPending = :syncPending
           WHERE ownerId = :ownerId AND isFinished = 1
             AND completedDayKey IS NOT NULL AND completedDayKey < :dayKey
             AND archivedAtEpochMillis IS NULL AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun archiveCompletedBefore(
        ownerId: String,
        dayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    ): Int

    @Query(
        """UPDATE daily_tasks
           SET linkedGoalId = NULL, updatedAtEpochMillis = :updatedAtEpochMillis,
               syncPending = :syncPending
           WHERE ownerId = :ownerId AND linkedGoalId = :goalId""",
    )
    suspend fun clearGoalLink(
        ownerId: String,
        goalId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )
}

@Dao
interface GoalDao {
    @Query(
        """SELECT * FROM goals
           WHERE ownerId = :ownerId AND archivedAtEpochMillis IS NULL AND deletedAtEpochMillis IS NULL
           ORDER BY id""",
    )
    fun observeAll(ownerId: String): Flow<List<GoalEntity>>

    @Insert suspend fun insert(goal: GoalEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(goals: List<GoalEntity>)
    @Upsert suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE ownerId = :ownerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(ownerId: String, remoteId: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE ownerId = :ownerId AND id = :id LIMIT 1")
    suspend fun getById(ownerId: String, id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPending(ownerId: String): List<GoalEntity>

    @Query(
        """UPDATE goals SET syncPending = 0
           WHERE ownerId = :ownerId AND remoteId = :remoteId
             AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markSynced(ownerId: String, remoteId: String, updatedAtEpochMillis: Long)

    @Query(
        """UPDATE goals
           SET achievedDayKey = CASE WHEN achievedDayKey IS NULL THEN :dayKey ELSE NULL END,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND id = :goalId AND type = 'ONE_TIME'
             AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun toggleAchievement(
        ownerId: String,
        goalId: Long,
        dayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE goals
           SET archivedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND type = 'ONE_TIME' AND achievedDayKey IS NOT NULL
             AND achievedDayKey < :dayKey AND archivedAtEpochMillis IS NULL
             AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun archiveCompletedBefore(
        ownerId: String,
        dayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    ): Int

    @Query(
        """UPDATE goals
           SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND id = :goalId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun softDelete(
        ownerId: String,
        goalId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )
}

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits WHERE ownerId = :ownerId AND deletedAtEpochMillis IS NULL ORDER BY id")
    fun observeAll(ownerId: String): Flow<List<HabitRecord>>

    @Query("SELECT colorHue FROM habits WHERE ownerId = :ownerId AND deletedAtEpochMillis IS NULL ORDER BY id")
    suspend fun getUsedHues(ownerId: String): List<Float>

    @Insert suspend fun insertHabit(habit: HabitEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHabits(habits: List<HabitEntity>)
    @Upsert suspend fun upsertHabit(habit: HabitEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeekdays(weekdays: List<HabitWeekdayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(completions: List<HabitCompletionEntity>)
    @Upsert suspend fun upsertCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_weekdays WHERE habitId = :habitId")
    suspend fun deleteWeekdays(habitId: Long)

    @Query("SELECT * FROM habits WHERE ownerId = :ownerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(ownerId: String, remoteId: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE ownerId = :ownerId AND id = :id LIMIT 1")
    suspend fun getById(ownerId: String, id: Long): HabitEntity?

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND dayKey = :dayKey LIMIT 1")
    suspend fun getCompletion(habitId: Long, dayKey: Int): HabitCompletionEntity?

    @Query("SELECT * FROM habits WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPendingHabits(ownerId: String): List<HabitEntity>

    @Query(
        """SELECT c.* FROM habit_completions c
           JOIN habits h ON h.id = c.habitId
           WHERE h.ownerId = :ownerId AND c.syncPending = 1""",
    )
    suspend fun getPendingCompletions(ownerId: String): List<HabitCompletionEntity>

    @Query("SELECT weekday FROM habit_weekdays WHERE habitId = :habitId ORDER BY weekday")
    suspend fun getWeekdays(habitId: Long): List<String>

    @Query(
        """UPDATE habits SET syncPending = 0
           WHERE ownerId = :ownerId AND remoteId = :remoteId
             AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markHabitSynced(ownerId: String, remoteId: String, updatedAtEpochMillis: Long)

    @Query(
        """UPDATE habit_completions SET syncPending = 0
           WHERE remoteId = :remoteId AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markCompletionSynced(remoteId: String, updatedAtEpochMillis: Long)

    @Query(
        """SELECT h.activeStartDayKey, h.activeEndDayKey,
               EXISTS(SELECT 1 FROM habit_weekdays w
                      WHERE w.habitId = h.id AND w.weekday = :weekday) AS isScheduled,
               (SELECT c.remoteId FROM habit_completions c
                WHERE c.habitId = h.id AND c.dayKey = :dayKey LIMIT 1) AS completionRemoteId,
               EXISTS(SELECT 1 FROM habit_completions c
                      WHERE c.habitId = h.id AND c.dayKey = :dayKey
                        AND c.deletedAtEpochMillis IS NULL) AS isCompleted
           FROM habits h WHERE h.ownerId = :ownerId AND h.id = :habitId
             AND h.deletedAtEpochMillis IS NULL""",
    )
    suspend fun getToggleState(
        ownerId: String,
        habitId: Long,
        dayKey: Int,
        weekday: String,
    ): HabitToggleState?

    @Query(
        """SELECT c.* FROM habit_completions c JOIN habits h ON h.id = c.habitId
           WHERE h.ownerId = :ownerId AND h.deletedAtEpochMillis IS NULL
             AND c.deletedAtEpochMillis IS NULL
             AND c.dayKey BETWEEN :startDayKey AND :endDayKey
           ORDER BY c.habitId, c.dayKey""",
    )
    fun observeCompletionsBetween(
        ownerId: String,
        startDayKey: Int,
        endDayKey: Int,
    ): Flow<List<HabitCompletionEntity>>

    @Query(
        """SELECT h.linkedGoalId AS goalId, COUNT(*) AS completedCheckIns
           FROM habit_completions c
           JOIN habits h ON h.id = c.habitId
           JOIN habit_weekdays w ON w.habitId = c.habitId AND w.weekday = c.weekday
           WHERE h.ownerId = :ownerId AND h.linkedGoalId IS NOT NULL
             AND h.deletedAtEpochMillis IS NULL AND c.deletedAtEpochMillis IS NULL
             AND (h.activeStartDayKey IS NULL OR c.dayKey >= h.activeStartDayKey)
             AND (h.activeEndDayKey IS NULL OR c.dayKey <= h.activeEndDayKey)
           GROUP BY h.linkedGoalId""",
    )
    fun observeGoalCompletionCounts(ownerId: String): Flow<List<GoalCompletionCount>>

    @Query(
        """UPDATE habit_completions
           SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE habitId = :habitId AND dayKey = :dayKey""",
    )
    suspend fun softDeleteCompletion(
        habitId: Long,
        dayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE habits SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND id = :habitId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun softDeleteHabit(
        ownerId: String,
        habitId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE habit_completions SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE habitId = :habitId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun softDeleteCompletionsForHabit(
        habitId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE habit_completions SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE habitId IN (SELECT id FROM habits WHERE ownerId = :ownerId AND linkedGoalId = :goalId)
             AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun softDeleteCompletionsForGoal(
        ownerId: String,
        goalId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE habits SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND linkedGoalId = :goalId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun softDeleteForGoal(
        ownerId: String,
        goalId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE habits SET activeStartDayKey = :startDayKey, activeEndDayKey = :endDayKey,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND linkedGoalId = :goalId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun setGoalPeriod(
        ownerId: String,
        goalId: Long,
        startDayKey: Int,
        endDayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM active_sleep_session WHERE ownerId = :ownerId AND deletedAtEpochMillis IS NULL")
    fun observeActiveSession(ownerId: String): Flow<ActiveSleepSessionEntity?>
    @Query("SELECT * FROM active_sleep_session WHERE ownerId = :ownerId")
    suspend fun getActiveSession(ownerId: String): ActiveSleepSessionEntity?
    @Upsert suspend fun setActiveSession(session: ActiveSleepSessionEntity)

    @Query(
        """UPDATE active_sleep_session
           SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId""",
    )
    suspend fun clearActiveSession(ownerId: String, updatedAtEpochMillis: Long, syncPending: Boolean)

    @Query(
        """SELECT * FROM (SELECT * FROM sleep_records
           WHERE ownerId = :ownerId AND deletedAtEpochMillis IS NULL
           ORDER BY endedAtEpochMillis DESC, id DESC LIMIT :limit)
           ORDER BY endedAtEpochMillis, id""",
    )
    fun observeHistory(ownerId: String, limit: Int): Flow<List<SleepRecordEntity>>

    @Insert suspend fun insertRecord(record: SleepRecordEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecords(records: List<SleepRecordEntity>)
    @Upsert suspend fun upsertRecord(record: SleepRecordEntity)

    @Query("SELECT * FROM sleep_records WHERE ownerId = :ownerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getRecordByRemoteId(ownerId: String, remoteId: String): SleepRecordEntity?
    @Query("SELECT * FROM sleep_records WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPendingRecords(ownerId: String): List<SleepRecordEntity>
    @Query("SELECT * FROM active_sleep_session WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPendingSession(ownerId: String): ActiveSleepSessionEntity?

    @Query(
        """UPDATE sleep_records SET syncPending = 0
           WHERE ownerId = :ownerId AND remoteId = :remoteId
             AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markRecordSynced(ownerId: String, remoteId: String, updatedAtEpochMillis: Long)

    @Query(
        """UPDATE active_sleep_session SET syncPending = 0
           WHERE ownerId = :ownerId AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markSessionSynced(ownerId: String, updatedAtEpochMillis: Long)
}

@Dao
interface MoneyDao {
    @Query(
        """SELECT state.periodStartDayKey AS periodStartDayKey,
               spending.id AS spendingId, spending.title AS spendingTitle,
               spending.amountMinor AS spendingAmountMinor,
               spending.createdAtEpochMillis AS spendingCreatedAtEpochMillis
           FROM money_state state LEFT JOIN spendings spending
             ON spending.ownerId = state.ownerId
            AND spending.periodRemoteId = state.currentPeriodRemoteId
            AND spending.deletedAtEpochMillis IS NULL
           WHERE state.ownerId = :ownerId
           ORDER BY spending.createdAtEpochMillis DESC, spending.id ASC""",
    )
    fun observeLedger(ownerId: String): Flow<List<MoneyLedgerRow>>

    @Upsert suspend fun setState(state: MoneyStateEntity)
    @Upsert suspend fun upsertPeriod(period: MoneyPeriodEntity)
    @Insert suspend fun insertSpending(spending: SpendingEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSpendings(spendings: List<SpendingEntity>)
    @Upsert suspend fun upsertSpending(spending: SpendingEntity)

    @Query("SELECT * FROM money_state WHERE ownerId = :ownerId")
    suspend fun getState(ownerId: String): MoneyStateEntity?
    @Query("SELECT * FROM money_periods WHERE ownerId = :ownerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getPeriod(ownerId: String, remoteId: String): MoneyPeriodEntity?
    @Query("SELECT * FROM spendings WHERE ownerId = :ownerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getSpending(ownerId: String, remoteId: String): SpendingEntity?

    @Query("SELECT * FROM money_state WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPendingState(ownerId: String): MoneyStateEntity?
    @Query("SELECT * FROM money_periods WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPendingPeriods(ownerId: String): List<MoneyPeriodEntity>
    @Query("SELECT * FROM spendings WHERE ownerId = :ownerId AND syncPending = 1")
    suspend fun getPendingSpendings(ownerId: String): List<SpendingEntity>

    @Query(
        """UPDATE spendings SET title = :title, amountMinor = :amountMinor,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND id = :spendingId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun editSpending(
        ownerId: String,
        spendingId: Long,
        title: String,
        amountMinor: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE spendings SET deletedAtEpochMillis = :updatedAtEpochMillis,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND id = :spendingId AND deletedAtEpochMillis IS NULL""",
    )
    suspend fun softDeleteSpending(
        ownerId: String,
        spendingId: Long,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE money_periods SET endDayKey = :endDayKey,
               updatedAtEpochMillis = :updatedAtEpochMillis, syncPending = :syncPending
           WHERE ownerId = :ownerId AND remoteId = :remoteId""",
    )
    suspend fun closePeriod(
        ownerId: String,
        remoteId: String,
        endDayKey: Int,
        updatedAtEpochMillis: Long,
        syncPending: Boolean,
    )

    @Query(
        """UPDATE money_state SET syncPending = 0
           WHERE ownerId = :ownerId AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markStateSynced(ownerId: String, updatedAtEpochMillis: Long)
    @Query(
        """UPDATE money_periods SET syncPending = 0
           WHERE ownerId = :ownerId AND remoteId = :remoteId
             AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markPeriodSynced(ownerId: String, remoteId: String, updatedAtEpochMillis: Long)
    @Query(
        """UPDATE spendings SET syncPending = 0
           WHERE ownerId = :ownerId AND remoteId = :remoteId
             AND updatedAtEpochMillis = :updatedAtEpochMillis""",
    )
    suspend fun markSpendingSynced(ownerId: String, remoteId: String, updatedAtEpochMillis: Long)
}

@Dao
interface OwnershipDao {
    @Query(
        """SELECT
           (SELECT COUNT(*) FROM goals WHERE ownerId = :ownerId) +
           (SELECT COUNT(*) FROM daily_tasks WHERE ownerId = :ownerId) +
           (SELECT COUNT(*) FROM habits WHERE ownerId = :ownerId) +
           (SELECT COUNT(*) FROM sleep_records WHERE ownerId = :ownerId) +
           (SELECT COUNT(*) FROM spendings WHERE ownerId = :ownerId)""",
    )
    suspend fun dataCount(ownerId: String): Long

    @Query("DELETE FROM active_sleep_session WHERE ownerId = :ownerId")
    suspend fun hardDeleteActiveSleep(ownerId: String)
    @Query("DELETE FROM money_state WHERE ownerId = :ownerId")
    suspend fun hardDeleteMoneyState(ownerId: String)

    @Query("UPDATE goals SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveGoals(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE daily_tasks SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveTasks(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE habits SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveHabits(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE habit_completions SET syncPending = 1, updatedAtEpochMillis = :now WHERE habitId IN (SELECT id FROM habits WHERE ownerId = :ownerId)")
    suspend fun markMovedCompletions(ownerId: String, now: Long)
    @Query("UPDATE sleep_records SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveSleepRecords(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE active_sleep_session SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveActiveSleep(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE money_periods SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveMoneyPeriods(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE spendings SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveSpendings(oldOwnerId: String, newOwnerId: String, now: Long)
    @Query("UPDATE money_state SET ownerId = :newOwnerId, syncPending = 1, updatedAtEpochMillis = :now WHERE ownerId = :oldOwnerId")
    suspend fun moveMoneyState(oldOwnerId: String, newOwnerId: String, now: Long)
}

@Dao
interface AppMetadataDao {
    @Query("SELECT value FROM app_metadata WHERE `key` = :key")
    suspend fun getValue(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(metadata: AppMetadataEntity)
}
