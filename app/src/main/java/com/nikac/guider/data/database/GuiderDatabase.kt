package com.nikac.guider.data.database

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
        MoneyPeriodEntity::class,
        SpendingEntity::class,
        AppMetadataEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class GuiderDatabase : RoomDatabase() {
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun goalDao(): GoalDao
    abstract fun habitDao(): HabitDao
    abstract fun sleepDao(): SleepDao
    abstract fun moneyDao(): MoneyDao
    abstract fun ownershipDao(): OwnershipDao
    abstract fun appMetadataDao(): AppMetadataDao

    companion object {
        private const val DATABASE_NAME = "guider.db"

        fun create(context: Context): GuiderDatabase = Room.databaseBuilder(
            context.applicationContext,
            GuiderDatabase::class.java,
            DATABASE_NAME,
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.addSyncColumns("goals", includeArchive = true)
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_ownerId_remoteId` " +
                        "ON `goals` (`ownerId`, `remoteId`)",
                )

                database.addSyncColumns("daily_tasks", includeArchive = true)
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_tasks_ownerId_remoteId` " +
                        "ON `daily_tasks` (`ownerId`, `remoteId`)",
                )

                database.addSyncColumns("habits")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_habits_ownerId_remoteId` " +
                        "ON `habits` (`ownerId`, `remoteId`)",
                )

                database.execSQL(
                    "ALTER TABLE `habit_completions` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    "ALTER TABLE `habit_completions` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE `habit_completions` ADD COLUMN `deletedAtEpochMillis` INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE `habit_completions` ADD COLUMN `syncPending` INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "UPDATE `habit_completions` SET `remoteId` = lower(hex(randomblob(16)))",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_completions_remoteId` " +
                        "ON `habit_completions` (`remoteId`)",
                )

                database.addSyncColumns("sleep_records")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_records_ownerId_remoteId` " +
                        "ON `sleep_records` (`ownerId`, `remoteId`)",
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `active_sleep_session_new` (
                        `ownerId` TEXT NOT NULL DEFAULT 'guest',
                        `activatedAtEpochMillis` INTEGER NOT NULL,
                        `sleepStartsAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0,
                        `deletedAtEpochMillis` INTEGER,
                        `syncPending` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`ownerId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """INSERT INTO `active_sleep_session_new`
                       (`ownerId`, `activatedAtEpochMillis`, `sleepStartsAtEpochMillis`)
                       SELECT 'guest', `activatedAtEpochMillis`, `sleepStartsAtEpochMillis`
                       FROM `active_sleep_session`""",
                )
                database.execSQL("DROP TABLE `active_sleep_session`")
                database.execSQL(
                    "ALTER TABLE `active_sleep_session_new` RENAME TO `active_sleep_session`",
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `spendings_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerId` TEXT NOT NULL DEFAULT 'guest',
                        `remoteId` TEXT NOT NULL DEFAULT '',
                        `periodRemoteId` TEXT NOT NULL DEFAULT '',
                        `title` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0,
                        `deletedAtEpochMillis` INTEGER,
                        `syncPending` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """INSERT INTO `spendings_new`
                       (`id`, `ownerId`, `remoteId`, `periodRemoteId`, `title`, `amountMinor`,
                        `createdAtEpochMillis`)
                       SELECT `id`, 'guest', lower(hex(randomblob(16))), 'guest-initial-period',
                              `title`, `amountMinor`, `createdAtEpochMillis`
                       FROM `spendings`""",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `money_state_new` (
                        `ownerId` TEXT NOT NULL DEFAULT 'guest',
                        `currentPeriodRemoteId` TEXT NOT NULL DEFAULT '',
                        `periodStartDayKey` INTEGER,
                        `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0,
                        `syncPending` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`ownerId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """INSERT INTO `money_state_new`
                       (`ownerId`, `currentPeriodRemoteId`, `periodStartDayKey`)
                       SELECT 'guest', 'guest-initial-period', `periodStartDayKey`
                       FROM `money_state` WHERE `id` = 1""",
                )
                database.execSQL("DROP TABLE `spendings`")
                database.execSQL("DROP TABLE `money_state`")
                database.execSQL("ALTER TABLE `money_state_new` RENAME TO `money_state`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `money_periods` (
                        `remoteId` TEXT NOT NULL,
                        `ownerId` TEXT NOT NULL DEFAULT 'guest',
                        `startDayKey` INTEGER,
                        `endDayKey` INTEGER,
                        `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0,
                        `deletedAtEpochMillis` INTEGER,
                        `syncPending` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`remoteId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """INSERT INTO `money_periods` (`remoteId`, `ownerId`, `startDayKey`)
                       SELECT 'guest-initial-period', 'guest', `periodStartDayKey`
                       FROM `money_state` WHERE `ownerId` = 'guest'""",
                )
                database.execSQL("ALTER TABLE `spendings_new` RENAME TO `spendings`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_money_periods_ownerId` " +
                        "ON `money_periods` (`ownerId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_spendings_ownerId_periodRemoteId` " +
                        "ON `spendings` (`ownerId`, `periodRemoteId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_spendings_createdAtEpochMillis` " +
                        "ON `spendings` (`createdAtEpochMillis`)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_spendings_ownerId_remoteId` " +
                        "ON `spendings` (`ownerId`, `remoteId`)",
                )
                database.execSQL(
                    """INSERT OR REPLACE INTO `app_metadata` (`key`, `value`)
                       VALUES ('pre_partition_data_pending', '1')""",
                )
            }
        }

        private fun SupportSQLiteDatabase.addSyncColumns(
            table: String,
            includeArchive: Boolean = false,
        ) {
            execSQL("ALTER TABLE `$table` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT 'guest'")
            execSQL("ALTER TABLE `$table` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
            execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0")
            if (includeArchive) {
                execSQL("ALTER TABLE `$table` ADD COLUMN `archivedAtEpochMillis` INTEGER")
            }
            execSQL("ALTER TABLE `$table` ADD COLUMN `deletedAtEpochMillis` INTEGER")
            execSQL("ALTER TABLE `$table` ADD COLUMN `syncPending` INTEGER NOT NULL DEFAULT 0")
            execSQL("UPDATE `$table` SET `remoteId` = lower(hex(randomblob(16)))")
        }
    }
}
