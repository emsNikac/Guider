package com.example.guider

import android.app.Application
import com.example.guider.data.habits.SharedPreferencesHabitRepository
import com.example.guider.data.sleep.SharedPreferencesSleepRepository
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.sleep.SleepRepository
import com.example.guider.notifications.HibernationNotificationManager
import com.example.guider.notifications.HibernationPromptScheduler

class GuiderApplication : Application() {
    lateinit var habitRepository: HabitRepository
        private set

    lateinit var sleepRepository: SleepRepository
        private set

    lateinit var hibernationNotificationManager: HibernationNotificationManager
        private set

    lateinit var hibernationPromptScheduler: HibernationPromptScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        habitRepository = SharedPreferencesHabitRepository(this)
        sleepRepository = SharedPreferencesSleepRepository(this)
        hibernationNotificationManager = HibernationNotificationManager(this).also {
            it.createChannel()
        }
        hibernationPromptScheduler = HibernationPromptScheduler(this)
        sleepRepository.activeSession.value?.let { session ->
            hibernationNotificationManager.showActiveSession(session)
        }
    }
}
