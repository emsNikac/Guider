package com.example.guider

import android.app.Application
import com.example.guider.data.habits.SharedPreferencesHabitRepository
import com.example.guider.data.sleep.SharedPreferencesSleepRepository
import com.example.guider.data.goals.SharedPreferencesGoalRepository
import com.example.guider.data.tasks.SharedPreferencesDailyTaskRepository
import com.example.guider.data.money.SharedPreferencesMoneyRepository
import com.example.guider.domain.goals.GoalRepository
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.sleep.SleepRepository
import com.example.guider.domain.tasks.DailyTaskRepository
import com.example.guider.domain.money.MoneyRepository
import com.example.guider.notifications.HibernationNotificationManager
import com.example.guider.notifications.HibernationPromptScheduler

class GuiderApplication : Application() {
    lateinit var habitRepository: HabitRepository
        private set

    lateinit var sleepRepository: SleepRepository
        private set

    lateinit var goalRepository: GoalRepository
        private set

    lateinit var dailyTaskRepository: DailyTaskRepository
        private set

    lateinit var moneyRepository: MoneyRepository
        private set

    lateinit var hibernationNotificationManager: HibernationNotificationManager
        private set

    lateinit var hibernationPromptScheduler: HibernationPromptScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        habitRepository = SharedPreferencesHabitRepository(this)
        sleepRepository = SharedPreferencesSleepRepository(this)
        goalRepository = SharedPreferencesGoalRepository(this)
        dailyTaskRepository = SharedPreferencesDailyTaskRepository(this)
        moneyRepository = SharedPreferencesMoneyRepository(this)
        goalRepository.goals.value
            .filter { it.type == GoalType.PERIODIC }
            .forEach { goal ->
                val startDayKey = goal.startDayKey ?: goal.createdDayKey
                val endDayKey = goal.endDayKey ?: startDayKey
                habitRepository.setGoalPeriod(goal.id, startDayKey, endDayKey)
            }
        hibernationNotificationManager = HibernationNotificationManager(this).also {
            it.createChannel()
        }
        hibernationPromptScheduler = HibernationPromptScheduler(this)
        sleepRepository.activeSession.value?.let { session ->
            hibernationNotificationManager.showActiveSession(session)
        }
    }
}
