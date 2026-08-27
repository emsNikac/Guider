package com.example.guider.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepCycleCalculator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class HibernationPromptScheduler(
    context: Context,
    backgroundScope: CoroutineScope,
) {
    private val applicationContext = context.applicationContext
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(applicationContext)
    }
    private val commands = Channel<SchedulerCommand>(capacity = Channel.UNLIMITED)

    init {
        backgroundScope.launch(Dispatchers.Default) {
            for (command in commands) {
                when (command) {
                    is SchedulerCommand.Schedule -> workManager.enqueueUniqueWork(
                        UNIQUE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        command.request,
                    )

                    SchedulerCommand.Cancel -> workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                }
            }
        }
    }

    fun schedule(session: ActiveSleepSession, nowEpochMillis: Long = System.currentTimeMillis()) {
        val promptAt = session.sleepStartsAtEpochMillis +
            PROMPT_AFTER_CYCLES * SleepCycleCalculator.CYCLE_MINUTES * MINUTE_MILLIS
        val request = OneTimeWorkRequestBuilder<WakePromptWorker>()
            .setInitialDelay((promptAt - nowEpochMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(WakePromptWorker.KEY_ACTIVATED_AT, session.activatedAtEpochMillis)
                    .build(),
            )
            .build()

        check(commands.trySend(SchedulerCommand.Schedule(request)).isSuccess)
    }

    fun cancel() {
        check(commands.trySend(SchedulerCommand.Cancel).isSuccess)
    }

    private sealed interface SchedulerCommand {
        data class Schedule(val request: androidx.work.OneTimeWorkRequest) : SchedulerCommand
        data object Cancel : SchedulerCommand
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "hibernation_wake_prompt"
        const val PROMPT_AFTER_CYCLES = 5
        const val MINUTE_MILLIS = 60_000L
    }
}
