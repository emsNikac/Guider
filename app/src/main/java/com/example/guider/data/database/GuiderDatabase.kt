package com.example.guider.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
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
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_1_2)
            .build()

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habit_completions_new` (
                        `habitId` INTEGER NOT NULL,
                        `dayKey` INTEGER NOT NULL,
                        `weekday` TEXT NOT NULL,
                        PRIMARY KEY(`habitId`, `dayKey`),
                        FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO `habit_completions_new` (`habitId`, `dayKey`, `weekday`)
                    SELECT `habitId`,
                           `dayKey`,
                           CASE CAST(
                               strftime(
                                   '%w',
                                   printf(
                                       '%04d-%02d-%02d',
                                       `dayKey` / 10000,
                                       (`dayKey` / 100) % 100,
                                       `dayKey` % 100
                                   )
                               ) AS INTEGER
                           )
                               WHEN 0 THEN 'SUNDAY'
                               WHEN 1 THEN 'MONDAY'
                               WHEN 2 THEN 'TUESDAY'
                               WHEN 3 THEN 'WEDNESDAY'
                               WHEN 4 THEN 'THURSDAY'
                               WHEN 5 THEN 'FRIDAY'
                               WHEN 6 THEN 'SATURDAY'
                           END
                    FROM `habit_completions`
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE `habit_completions`")
                database.execSQL(
                    "ALTER TABLE `habit_completions_new` RENAME TO `habit_completions`",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_habit_completions_dayKey` " +
                        "ON `habit_completions` (`dayKey`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_habit_completions_habitId_weekday` " +
                        "ON `habit_completions` (`habitId`, `weekday`)",
                )
            }
        }
    }
}
