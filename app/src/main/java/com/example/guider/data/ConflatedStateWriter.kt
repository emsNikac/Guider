package com.example.guider.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Persists the newest complete repository snapshot without blocking the main thread. */
internal class ConflatedStateWriter<T>(
    scope: CoroutineScope,
    private val storageName: String,
    private val write: (T) -> Unit,
) {
    private val states = Channel<T>(capacity = Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (state in states) {
                runCatching { write(state) }
                    .onFailure { error ->
                        Log.e(TAG, "Unable to persist $storageName", error)
                    }
            }
        }
    }

    fun submit(state: T) {
        check(states.trySend(state).isSuccess) {
            "The $storageName state writer is no longer available"
        }
    }

    private companion object {
        const val TAG = "GuiderStateWriter"
    }
}
