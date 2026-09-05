package com.nikac.guider.data.database

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

const val GUEST_OWNER_ID = "guest"

fun newRemoteId(): String = UUID.randomUUID().toString()

@Entity(
    tableName = "goals",
    indices = [Index(value = ["ownerId", "remoteId"], unique = true)],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    @ColumnInfo(defaultValue = "''") val remoteId: String = newRemoteId(),
    val title: String,
    val type: String,
    val createdDayKey: Int,
    val achievedDayKey: Int?,
    val startDayKey: Int?,
    val endDayKey: Int?,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val archivedAtEpochMillis: Long? = null,
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(
    tableName = "daily_tasks",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedGoalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("linkedGoalId"),
        Index(value = ["ownerId", "remoteId"], unique = true),
    ],
)
data class DailyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    @ColumnInfo(defaultValue = "''") val remoteId: String = newRemoteId(),
    val category: String,
    val title: String,
    val isFinished: Boolean,
    val createdDayKey: Int,
    val completedDayKey: Int?,
    val linkedGoalId: Long?,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val archivedAtEpochMillis: Long? = null,
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(
    tableName = "habits",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedGoalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("linkedGoalId"),
        Index(value = ["ownerId", "remoteId"], unique = true),
    ],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    @ColumnInfo(defaultValue = "''") val remoteId: String = newRemoteId(),
    val name: String,
    val colorHue: Float,
    val linkedGoalId: Long?,
    val activeStartDayKey: Int?,
    val activeEndDayKey: Int?,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(
    tableName = "habit_weekdays",
    primaryKeys = ["habitId", "weekday"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HabitWeekdayEntity(val habitId: Long, val weekday: String)

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "dayKey"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("dayKey"),
        Index(value = ["habitId", "weekday"]),
        Index(value = ["remoteId"], unique = true),
    ],
)
data class HabitCompletionEntity(
    val habitId: Long,
    val dayKey: Int,
    val weekday: String,
    @ColumnInfo(defaultValue = "''") val remoteId: String = newRemoteId(),
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

data class HabitRecord(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val weekdays: List<HabitWeekdayEntity>,
)

data class HabitToggleState(
    val activeStartDayKey: Int?,
    val activeEndDayKey: Int?,
    val isScheduled: Boolean,
    val completionRemoteId: String?,
    val isCompleted: Boolean,
)

data class GoalCompletionCount(val goalId: Long, val completedCheckIns: Long)

@Entity(tableName = "active_sleep_session")
data class ActiveSleepSessionEntity(
    @PrimaryKey @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    val activatedAtEpochMillis: Long,
    val sleepStartsAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(
    tableName = "sleep_records",
    indices = [
        Index("endedAtEpochMillis"),
        Index(value = ["ownerId", "remoteId"], unique = true),
    ],
)
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    @ColumnInfo(defaultValue = "''") val remoteId: String = newRemoteId(),
    val activatedAtEpochMillis: Long,
    val sleepStartsAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(tableName = "money_state")
data class MoneyStateEntity(
    @PrimaryKey @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    @ColumnInfo(defaultValue = "''") val currentPeriodRemoteId: String,
    val periodStartDayKey: Int?,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(tableName = "money_periods", indices = [Index("ownerId")])
data class MoneyPeriodEntity(
    @PrimaryKey val remoteId: String = newRemoteId(),
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    val startDayKey: Int?,
    val endDayKey: Int?,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

@Entity(
    tableName = "spendings",
    indices = [
        Index(value = ["ownerId", "periodRemoteId"]),
        Index("createdAtEpochMillis"),
        Index(value = ["ownerId", "remoteId"], unique = true),
    ],
)
data class SpendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = GUEST_OWNER_ID,
    @ColumnInfo(defaultValue = "''") val remoteId: String = newRemoteId(),
    @ColumnInfo(defaultValue = "''") val periodRemoteId: String,
    val title: String,
    val amountMinor: Long,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)

data class MoneyLedgerRow(
    val periodStartDayKey: Int?,
    val spendingId: Long?,
    val spendingTitle: String?,
    val spendingAmountMinor: Long?,
    val spendingCreatedAtEpochMillis: Long?,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(@PrimaryKey val key: String, val value: String)
