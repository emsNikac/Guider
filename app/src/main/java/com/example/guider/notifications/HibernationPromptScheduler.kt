package com.example.guider.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepCycleCalculator
import java.util.concurrent.TimeUnit

class HibernationPromptScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

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

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "hibernation_wake_prompt"
        const val PROMPT_AFTER_CYCLES = 5
        const val MINUTE_MILLIS = 60_000L
    }
}
