package com.nikac.guider.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn

private const val DEFAULT_STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Warms a Room-backed flow with one query, keeps that subscription alive for the screen hand-off,
 * and stops observing after the last UI subscriber has been gone for [stopTimeoutMillis].
 */
internal suspend fun <T> Flow<T>.stateInWhileSubscribed(
    scope: CoroutineScope,
    stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MILLIS,
): StateFlow<T> {
    val shared = shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTimeoutMillis),
        replay = 1,
    )
    shared.first()
    return ReplayedStateFlow(shared)
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ReplayedStateFlow<T>(
    private val shared: SharedFlow<T>,
) : StateFlow<T>, SharedFlow<T> by shared {
    override val value: T
        get() = shared.replayCache.last()
}
