package com.example.guider.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.guider.GuiderApplication

class WakePromptWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val application = applicationContext as GuiderApplication
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
