package com.example.guider

import android.app.Application
import android.util.Log
import androidx.work.Configuration
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GuiderApplication : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableIsReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = mutableIsReady.asStateFlow()

    @Volatile
    lateinit var habitRepository: HabitRepository
        private set

    @Volatile
    lateinit var sleepRepository: SleepRepository
        private set

    @Volatile
    lateinit var goalRepository: GoalRepository
        private set

    @Volatile
    lateinit var dailyTaskRepository: DailyTaskRepository
        private set

    @Volatile
    lateinit var moneyRepository: MoneyRepository
        private set

    @Volatile
    lateinit var hibernationNotificationManager: HibernationNotificationManager
        private set

    @Volatile
    lateinit var hibernationPromptScheduler: HibernationPromptScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val components = loadComponents()
            habitRepository = components.habitRepository
            sleepRepository = components.sleepRepository
            goalRepository = components.goalRepository
            dailyTaskRepository = components.dailyTaskRepository
            moneyRepository = components.moneyRepository
            hibernationNotificationManager = components.notificationManager
            hibernationPromptScheduler = components.promptScheduler
            mutableIsReady.value = true

            components.sleepRepository.activeSession.value?.let { session ->
                components.notificationManager.showActiveSession(session)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    suspend fun awaitReady() {
        isReady.first { it }
    }

    fun runWhenReady(block: GuiderApplication.() -> Unit): Job = applicationScope.launch {
        awaitReady()
        block()
    }

    private suspend fun loadComponents(): AppComponents = coroutineScope {
        val habits = async(Dispatchers.IO) {
            SharedPreferencesHabitRepository(this@GuiderApplication, applicationScope)
        }
        val sleep = async(Dispatchers.IO) {
            SharedPreferencesSleepRepository(this@GuiderApplication, applicationScope)
        }
        val goals = async(Dispatchers.IO) {
            SharedPreferencesGoalRepository(this@GuiderApplication, applicationScope)
        }
        val dailyTasks = async(Dispatchers.IO) {
            SharedPreferencesDailyTaskRepository(this@GuiderApplication, applicationScope)
        }
        val money = async(Dispatchers.IO) {
            SharedPreferencesMoneyRepository(this@GuiderApplication, applicationScope)
        }
        val notifications = async(Dispatchers.IO) {
            HibernationNotificationManager(this@GuiderApplication).also { manager ->
                runCatching(manager::createChannel)
                    .onFailure { error ->
                        Log.e(TAG, "Unable to create notification channels", error)
                    }
            }
        }

        val habitRepository = habits.await()
        val goalRepository = goals.await()
        goalRepository.goals.value
            .filter { it.type == GoalType.PERIODIC }
            .forEach { goal ->
                val startDayKey = goal.startDayKey ?: goal.createdDayKey
                val endDayKey = goal.endDayKey ?: startDayKey
                habitRepository.setGoalPeriod(goal.id, startDayKey, endDayKey)
            }

        AppComponents(
            habitRepository = habitRepository,
            sleepRepository = sleep.await(),
            goalRepository = goalRepository,
            dailyTaskRepository = dailyTasks.await(),
            moneyRepository = money.await(),
            notificationManager = notifications.await(),
            promptScheduler = HibernationPromptScheduler(
                context = this@GuiderApplication,
                backgroundScope = applicationScope,
            ),
        )
    }

    private data class AppComponents(
        val habitRepository: HabitRepository,
        val sleepRepository: SleepRepository,
        val goalRepository: GoalRepository,
        val dailyTaskRepository: DailyTaskRepository,
        val moneyRepository: MoneyRepository,
        val notificationManager: HibernationNotificationManager,
        val promptScheduler: HibernationPromptScheduler,
    )

    private companion object {
        const val TAG = "GuiderApplication"
    }
}
