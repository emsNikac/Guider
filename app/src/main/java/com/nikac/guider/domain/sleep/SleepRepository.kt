package com.nikac.guider.domain.sleep

import kotlinx.coroutines.flow.StateFlow

interface SleepRepository {
    val activeSession: StateFlow<ActiveSleepSession?>
    val history: StateFlow<List<SleepRecord>>

    suspend fun startHibernation(activatedAtEpochMillis: Long): ActiveSleepSession

    suspend fun finishHibernation(endedAtEpochMillis: Long): SleepRecord?
}
