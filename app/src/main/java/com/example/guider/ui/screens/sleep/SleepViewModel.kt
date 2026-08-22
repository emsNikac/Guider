package com.example.guider.ui.screens.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.guider.GuiderApplication
import com.example.guider.domain.sleep.SleepRepository

class SleepViewModel(application: Application) : AndroidViewModel(application) {
    private val guiderApplication = application as GuiderApplication
    private val repository: SleepRepository = guiderApplication.sleepRepository

    val activeSession = repository.activeSession
    val history = repository.history

    fun activateHibernation(nowEpochMillis: Long = System.currentTimeMillis()) {
        val session = repository.startHibernation(nowEpochMillis)
        guiderApplication.hibernationNotificationManager.showActiveSession(session)
        guiderApplication.hibernationPromptScheduler.schedule(session, nowEpochMillis)
    }

    fun finishHibernation(nowEpochMillis: Long = System.currentTimeMillis()) {
        repository.finishHibernation(nowEpochMillis)
        guiderApplication.hibernationNotificationManager.cancelActiveSession()
        guiderApplication.hibernationPromptScheduler.cancel()
    }
}
