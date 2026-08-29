package com.example.guider.data.database

import com.example.guider.domain.goals.Goal
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.money.Spending
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepRecord
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory

fun DailyTaskEntity.toModel(): DailyTask = DailyTask(
    id = id,
    taskCategory = TaskCategory.valueOf(category),
    title = title,
    isFinished = isFinished,
    createdDayKey = createdDayKey,
    completedDayKey = completedDayKey,
    linkedGoalId = linkedGoalId,
)

fun GoalEntity.toModel(): Goal = Goal(
    id = id,
    title = title,
    type = GoalType.valueOf(type),
    createdDayKey = createdDayKey,
    achievedDayKey = achievedDayKey,
    startDayKey = startDayKey,
    endDayKey = endDayKey,
)

fun HabitRecord.toModel(): Habit = Habit(
    id = habit.id,
    name = habit.name,
    colorHue = habit.colorHue,
    scheduledWeekdays = weekdays.mapTo(HashSet(weekdays.size)) {
        HabitWeekday.valueOf(it.weekday)
    },
    linkedGoalId = habit.linkedGoalId,
    activeStartDayKey = habit.activeStartDayKey,
    activeEndDayKey = habit.activeEndDayKey,
)

fun ActiveSleepSessionEntity.toModel(): ActiveSleepSession = ActiveSleepSession(
    activatedAtEpochMillis = activatedAtEpochMillis,
    sleepStartsAtEpochMillis = sleepStartsAtEpochMillis,
)

fun SleepRecordEntity.toModel(): SleepRecord = SleepRecord(
    id = id,
    activatedAtEpochMillis = activatedAtEpochMillis,
    sleepStartsAtEpochMillis = sleepStartsAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
)

fun SpendingEntity.toModel(): Spending = Spending(
    id = id,
    title = title,
    amountMinor = amountMinor,
    createdAtEpochMillis = createdAtEpochMillis,
)
