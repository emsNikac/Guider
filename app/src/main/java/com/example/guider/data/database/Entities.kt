package com.example.guider.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val type: String,
    val createdDayKey: Int,
    val achievedDayKey: Int?,
    val startDayKey: Int?,
    val endDayKey: Int?,
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
    indices = [Index("linkedGoalId")],
)
data class DailyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val title: String,
    val isFinished: Boolean,
    val createdDayKey: Int,
    val completedDayKey: Int?,
    val linkedGoalId: Long?,
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
    indices = [Index("linkedGoalId")],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorHue: Float,
    val linkedGoalId: Long?,
    val activeStartDayKey: Int?,
    val activeEndDayKey: Int?,
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
data class HabitWeekdayEntity(
    val habitId: Long,
    val weekday: String,
)

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
    indices = [Index("dayKey")],
)
data class HabitCompletionEntity(
    val habitId: Long,
    val dayKey: Int,
)

data class HabitRecord(
    @Embedded val habit: HabitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "habitId",
    )
    val weekdays: List<HabitWeekdayEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "habitId",
    )
    val completions: List<HabitCompletionEntity>,
)

@Entity(tableName = "active_sleep_session")
data class ActiveSleepSessionEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val activatedAtEpochMillis: Long,
    val sleepStartsAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "sleep_records", indices = [Index("endedAtEpochMillis")])
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val activatedAtEpochMillis: Long,
    val sleepStartsAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
)

@Entity(tableName = "money_state")
data class MoneyStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val periodStartDayKey: Int?,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "spendings",
    foreignKeys = [
        ForeignKey(
            entity = MoneyStateEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledgerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ledgerId"), Index("createdAtEpochMillis")],
)
data class SpendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ledgerId: Int = MoneyStateEntity.SINGLETON_ID,
    val title: String,
    val amountMinor: Long,
    val createdAtEpochMillis: Long,
)

data class MoneyLedgerRecord(
    @Embedded val state: MoneyStateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ledgerId",
    )
    val spendings: List<SpendingEntity>,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
