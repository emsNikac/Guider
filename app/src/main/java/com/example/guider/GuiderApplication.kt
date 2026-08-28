package com.example.guider

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.guider.data.goals.SharedPreferencesGoalRepository
import com.example.guider.data.habits.SharedPreferencesHabitRepository
import com.example.guider.data.money.SharedPreferencesMoneyRepository
import com.example.guider.data.sleep.SharedPreferencesSleepRepository
import com.example.guider.data.tasks.SharedPreferencesDailyTaskRepository
import com.example.guider.domain.goals.GoalRepository
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.habits.HabitRepository
import com.example.guider.domain.money.MoneyRepository
import com.example.guider.domain.sleep.SleepRepository
import com.example.guider.domain.tasks.DailyTaskRepository
import com.example.guider.notifications.HibernationNotificationManager
import com.example.guider.notifications.HibernationPromptScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppFeature {
    DAILY_TASKS,
    SLEEP,
    HABITS,
    GOALS,
    MONEY,
}

enum class FeatureLoadStatus {
    NOT_REQUESTED,
    LOADING,
    READY,
    FAILED,
}

class GuiderApplication : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val featureJobs = mutableMapOf<AppFeature, Job>()
    private val mutableFeatureStatuses = MutableStateFlow(
        AppFeature.entries.associateWith { FeatureLoadStatus.NOT_REQUESTED },
    )
    val featureStatuses: StateFlow<Map<AppFeature, FeatureLoadStatus>> =
        mutableFeatureStatuses.asStateFlow()

    private val mutableGoalRepository = MutableStateFlow<GoalRepository?>(null)
    val goalRepositoryState: StateFlow<GoalRepository?> = mutableGoalRepository.asStateFlow()

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
        requestFeature(AppFeature.DAILY_TASKS)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    fun featureStatus(feature: AppFeature): FeatureLoadStatus =
        featureStatuses.value.getValue(feature)

    fun requestFeature(feature: AppFeature) {
        synchronized(featureJobs) {
            when (featureStatus(feature)) {
                FeatureLoadStatus.LOADING,
                FeatureLoadStatus.READY,
                -> return

                FeatureLoadStatus.NOT_REQUESTED,
                FeatureLoadStatus.FAILED,
                -> Unit
            }

            updateFeatureStatus(feature, FeatureLoadStatus.LOADING)
            val job = applicationScope.launch(start = CoroutineStart.LAZY) {
                runCatching { loadFeature(feature) }
                    .onSuccess {
                        updateFeatureStatus(feature, FeatureLoadStatus.READY)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Unable to load $feature", error)
                        updateFeatureStatus(feature, FeatureLoadStatus.FAILED)
                    }
                synchronized(featureJobs) {
                    featureJobs.remove(feature)
                }
            }
            featureJobs[feature] = job
            job.start()
        }
    }

    suspend fun awaitFeature(feature: AppFeature) {
        requestFeature(feature)
        when (
            featureStatuses.first { statuses ->
                statuses.getValue(feature) in TERMINAL_FEATURE_STATUSES
            }.getValue(feature)
        ) {
            FeatureLoadStatus.READY -> Unit
            FeatureLoadStatus.FAILED -> error("Unable to load $feature")
            else -> error("Unexpected non-terminal state for $feature")
        }
    }

    fun runWhenFeatureReady(
        feature: AppFeature,
        block: GuiderApplication.() -> Unit,
    ): Job = applicationScope.launch {
        awaitFeature(feature)
        block()
    }

    private suspend fun loadFeature(feature: AppFeature) {
        when (feature) {
            AppFeature.DAILY_TASKS -> loadDailyTasks()
            AppFeature.SLEEP -> loadSleep()
            AppFeature.HABITS -> loadHabits()
            AppFeature.GOALS -> loadGoals()
            AppFeature.MONEY -> loadMoney()
        }
    }

    private suspend fun loadDailyTasks() {
        dailyTaskRepository = withContext(Dispatchers.IO) {
            SharedPreferencesDailyTaskRepository(this@GuiderApplication, applicationScope)
        }
    }

    private suspend fun loadSleep() = coroutineScope {
        val sleep = async(Dispatchers.IO) {
            SharedPreferencesSleepRepository(this@GuiderApplication, applicationScope)
        }
        val notificationManager = async(Dispatchers.IO) {
            HibernationNotificationManager(this@GuiderApplication).also { manager ->
                runCatching(manager::createChannel)
                    .onFailure { error ->
                        Log.e(TAG, "Unable to create notification channels", error)
                    }
            }
        }

        sleepRepository = sleep.await()
        hibernationNotificationManager = notificationManager.await()
        hibernationPromptScheduler = HibernationPromptScheduler(
            context = this@GuiderApplication,
            backgroundScope = applicationScope,
        )
        sleepRepository.activeSession.value?.let(hibernationNotificationManager::showActiveSession)
    }

    private suspend fun loadHabits() {
        habitRepository = withContext(Dispatchers.IO) {
            SharedPreferencesHabitRepository(this@GuiderApplication, applicationScope)
        }
    }

    private suspend fun loadGoals() {
        awaitFeature(AppFeature.DAILY_TASKS)
        awaitFeature(AppFeature.HABITS)
        val repository = withContext(Dispatchers.IO) {
            SharedPreferencesGoalRepository(this@GuiderApplication, applicationScope)
        }
        repository.goals.value
            .filter { it.type == GoalType.PERIODIC }
            .forEach { goal ->
                val startDayKey = goal.startDayKey ?: goal.createdDayKey
                val endDayKey = goal.endDayKey ?: startDayKey
                habitRepository.setGoalPeriod(goal.id, startDayKey, endDayKey)
            }
        goalRepository = repository
        mutableGoalRepository.value = repository
    }

    private suspend fun loadMoney() {
        moneyRepository = withContext(Dispatchers.IO) {
            SharedPreferencesMoneyRepository(this@GuiderApplication, applicationScope)
        }
    }

    private fun updateFeatureStatus(feature: AppFeature, status: FeatureLoadStatus) {
        mutableFeatureStatuses.update { statuses -> statuses + (feature to status) }
    }

    private companion object {
        const val TAG = "GuiderApplication"
        val TERMINAL_FEATURE_STATUSES = setOf(
            FeatureLoadStatus.READY,
            FeatureLoadStatus.FAILED,
        )
    }
}
