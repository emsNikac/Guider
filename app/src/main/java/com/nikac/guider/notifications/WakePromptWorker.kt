package com.nikac.guider.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nikac.guider.AppFeature
import com.nikac.guider.GuiderApplication

class WakePromptWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as GuiderApplication
        runCatching { application.awaitFeature(AppFeature.SLEEP) }
            .getOrElse { return Result.retry() }
        val expectedActivation = inputData.getLong(KEY_ACTIVATED_AT, Long.MIN_VALUE)
        val activeSession = application.sleepRepository.activeSession.value
        if (activeSession?.activatedAtEpochMillis != expectedActivation) return Result.success()

        application.hibernationNotificationManager.showWakePrompt()
        return Result.success()
    }

    companion object {
        const val KEY_ACTIVATED_AT = "activated_at"
    }
}
