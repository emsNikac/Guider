package com.example.guider.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GoalEntity::class,
        DailyTaskEntity::class,
        HabitEntity::class,
        HabitWeekdayEntity::class,
        HabitCompletionEntity::class,
        ActiveSleepSessionEntity::class,
        SleepRecordEntity::class,
        MoneyStateEntity::class,
        SpendingEntity::class,
        AppMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GuiderDatabase : RoomDatabase() {
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun goalDao(): GoalDao
    abstract fun habitDao(): HabitDao
    abstract fun sleepDao(): SleepDao
    abstract fun moneyDao(): MoneyDao
    abstract fun appMetadataDao(): AppMetadataDao

    companion object {
        private const val DATABASE_NAME = "guider.db"

        fun create(context: Context): GuiderDatabase = Room.databaseBuilder(
            context.applicationContext,
            GuiderDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }
}
