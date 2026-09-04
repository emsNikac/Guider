package com.nikac.guider.ui.screens.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nikac.guider.GuiderApplication
import com.nikac.guider.domain.sleep.SleepRepository
import kotlinx.coroutines.launch

class SleepViewModel(application: Application) : AndroidViewModel(application) {
    private val guiderApplication = application as GuiderApplication
    private val repository: SleepRepository = guiderApplication.sleepRepository

    val activeSession = repository.activeSession
    val history = repository.history

    fun activateHibernation(nowEpochMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val session = repository.startHibernation(nowEpochMillis)
            guiderApplication.hibernationNotificationManager.showActiveSession(session)
            guiderApplication.hibernationPromptScheduler.schedule(session, nowEpochMillis)
        }
    }

    fun finishHibernation(nowEpochMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.finishHibernation(nowEpochMillis)
            guiderApplication.hibernationNotificationManager.cancelActiveSession()
            guiderApplication.hibernationPromptScheduler.cancel()
        }
    }
}
