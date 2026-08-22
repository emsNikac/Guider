package com.example.guider.domain.sleep

import kotlinx.coroutines.flow.StateFlow

interface SleepRepository {
    val activeSession: StateFlow<ActiveSleepSession?>
    val history: StateFlow<List<SleepRecord>>

    fun startHibernation(activatedAtEpochMillis: Long): ActiveSleepSession

    fun finishHibernation(endedAtEpochMillis: Long): SleepRecord?
}
