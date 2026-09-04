package com.nikac.guider

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.LegacyPreferencesImporter
import com.nikac.guider.data.goals.RoomGoalRepository
import com.nikac.guider.data.habits.RoomHabitRepository
import com.nikac.guider.data.money.RoomMoneyRepository
import com.nikac.guider.data.sleep.RoomSleepRepository
import com.nikac.guider.data.tasks.RoomDailyTaskRepository
import com.nikac.guider.domain.goals.GoalRepository
import com.nikac.guider.domain.habits.HabitRepository
import com.nikac.guider.domain.money.MoneyRepository
import com.nikac.guider.domain.sleep.SleepRepository
import com.nikac.guider.domain.tasks.DailyTaskRepository
import com.nikac.guider.notifications.HibernationNotificationManager
import com.nikac.guider.notifications.HibernationPromptScheduler
import com.nikac.guider.ui.theme.bodyFontFamily
import com.nikac.guider.ui.theme.displayFontFamily
import com.nikac.guider.util.LocalizedFormatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val regionalSettingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LocalizedFormatters.refreshConfiguration()
        }
    }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workerConfiguration by lazy(LazyThreadSafetyMode.NONE) {
        Configuration.Builder().build()
    }
    private val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GuiderDatabase.create(this)
    }
    private val initializedDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        applicationScope.async(start = CoroutineStart.LAZY) {
            LegacyPreferencesImporter(this@GuiderApplication).importIfNeeded(database)
            database
        }.also { it.start() }
    }
    private val featureJobs = mutableMapOf<AppFeature, Job>()
    private var featureWarmupJob: Job? = null
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
        LocalizedFormatters.refreshConfiguration()
        preloadFonts()
        ContextCompat.registerReceiver(
            this,
            regionalSettingsReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        requestFeature(AppFeature.DAILY_TASKS)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocalizedFormatters.refreshConfiguration()
    }

    override val workManagerConfiguration: Configuration
        get() = workerConfiguration

    private fun preloadFonts() {
        applicationScope.launch {
            runCatching {
                val resolver = createFontFamilyResolver(this@GuiderApplication)
                resolver.preload(bodyFontFamily)
                resolver.preload(displayFontFamily)
            }.onFailure { error ->
                Log.w(TAG, "Unable to preload bundled fonts", error)
            }
        }
    }

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

    fun warmFeaturesDuringIdle() {
        synchronized(featureJobs) {
            if (featureWarmupJob != null) return

            val job = applicationScope.launch(start = CoroutineStart.LAZY) {
                WARMUP_FEATURES.forEach { feature ->
                    runCatching { awaitFeature(feature) }
                        .onFailure { error ->
                            Log.w(TAG, "Unable to warm $feature", error)
                        }
                    delay(FEATURE_WARMUP_INTERVAL_MILLIS)
                }
            }
            featureWarmupJob = job
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
        block: suspend GuiderApplication.() -> Unit,
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
        dailyTaskRepository = RoomDailyTaskRepository.create(
            dao = initializedDatabase.await().dailyTaskDao(),
            scope = applicationScope,
        )
    }

    private suspend fun loadSleep() = coroutineScope {
        val sleep = async {
            RoomSleepRepository.create(initializedDatabase.await(), applicationScope)
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
        habitRepository = RoomHabitRepository.create(initializedDatabase.await(), applicationScope)
    }

    private suspend fun loadGoals() {
        awaitFeature(AppFeature.DAILY_TASKS)
        awaitFeature(AppFeature.HABITS)
        val repository = RoomGoalRepository.create(initializedDatabase.await(), applicationScope)
        goalRepository = repository
        mutableGoalRepository.value = repository
    }

    private suspend fun loadMoney() {
        moneyRepository = RoomMoneyRepository.create(initializedDatabase.await(), applicationScope)
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
        val WARMUP_FEATURES = listOf(
            AppFeature.SLEEP,
            AppFeature.HABITS,
            AppFeature.GOALS,
            AppFeature.MONEY,
        )
        const val FEATURE_WARMUP_INTERVAL_MILLIS = 500L
    }
}
