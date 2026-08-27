package com.example.guider.data.sleep

import android.content.Context
import androidx.core.content.edit
import com.example.guider.data.ConflatedStateWriter
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepCycleCalculator
import com.example.guider.domain.sleep.SleepRecord
import com.example.guider.domain.sleep.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesSleepRepository(
    context: Context,
    persistenceScope: CoroutineScope,
) : SleepRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val mutableActiveSession = MutableStateFlow(readActiveSession())
    override val activeSession: StateFlow<ActiveSleepSession?> = mutableActiveSession.asStateFlow()

    private val mutableHistory = MutableStateFlow(readHistory())
    override val history: StateFlow<List<SleepRecord>> = mutableHistory.asStateFlow()
    private val stateWriter = ConflatedStateWriter<SleepStorageState>(
        scope = persistenceScope,
        storageName = PREFERENCES_NAME,
    ) { state ->
        writeState(state)
    }

    @Synchronized
    override fun startHibernation(activatedAtEpochMillis: Long): ActiveSleepSession {
        mutableActiveSession.value?.let { return it }

        val session = ActiveSleepSession(
            activatedAtEpochMillis = activatedAtEpochMillis,
            sleepStartsAtEpochMillis = SleepCycleCalculator.effectiveSleepStart(activatedAtEpochMillis),
        )
        mutableActiveSession.value = session
        persistCurrentState()
        return session
    }

    @Synchronized
    override fun finishHibernation(endedAtEpochMillis: Long): SleepRecord? {
        val session = mutableActiveSession.value ?: return null
        val record = SleepRecord(
            id = endedAtEpochMillis,
            activatedAtEpochMillis = session.activatedAtEpochMillis,
            sleepStartsAtEpochMillis = session.sleepStartsAtEpochMillis,
            endedAtEpochMillis = endedAtEpochMillis,
        )
        val updatedHistory = (mutableHistory.value + record).takeLast(MAX_HISTORY_RECORDS)

        mutableActiveSession.value = null
        mutableHistory.value = updatedHistory
        persistCurrentState()
        return record
    }

    private fun persistCurrentState() {
        stateWriter.submit(
            SleepStorageState(
                activeSession = mutableActiveSession.value,
                history = mutableHistory.value,
            ),
        )
    }

    private fun writeState(state: SleepStorageState) {
        preferences.edit {
            state.activeSession?.let { session ->
                putLong(KEY_ACTIVE_ACTIVATED_AT, session.activatedAtEpochMillis)
                putLong(KEY_ACTIVE_SLEEP_STARTS_AT, session.sleepStartsAtEpochMillis)
            } ?: run {
                remove(KEY_ACTIVE_ACTIVATED_AT)
                remove(KEY_ACTIVE_SLEEP_STARTS_AT)
            }
            putString(KEY_HISTORY, historyToJson(state.history).toString())
        }
    }

    private fun readActiveSession(): ActiveSleepSession? {
        if (!preferences.contains(KEY_ACTIVE_ACTIVATED_AT)) return null
        return ActiveSleepSession(
            activatedAtEpochMillis = preferences.getLong(KEY_ACTIVE_ACTIVATED_AT, 0L),
            sleepStartsAtEpochMillis = preferences.getLong(KEY_ACTIVE_SLEEP_STARTS_AT, 0L),
        )
    }

    private fun readHistory(): List<SleepRecord> {
        val json = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        SleepRecord(
                            id = item.getLong(JSON_ID),
                            activatedAtEpochMillis = item.getLong(JSON_ACTIVATED_AT),
                            sleepStartsAtEpochMillis = item.getLong(JSON_SLEEP_STARTS_AT),
                            endedAtEpochMillis = item.getLong(JSON_ENDED_AT),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun historyToJson(records: List<SleepRecord>): JSONArray = JSONArray().apply {
        records.forEach { record ->
            put(
                JSONObject()
                    .put(JSON_ID, record.id)
                    .put(JSON_ACTIVATED_AT, record.activatedAtEpochMillis)
                    .put(JSON_SLEEP_STARTS_AT, record.sleepStartsAtEpochMillis)
                    .put(JSON_ENDED_AT, record.endedAtEpochMillis),
            )
        }
    }

    private data class SleepStorageState(
        val activeSession: ActiveSleepSession?,
        val history: List<SleepRecord>,
    )

    private companion object {
        const val PREFERENCES_NAME = "sleep_tracking"
        const val KEY_ACTIVE_ACTIVATED_AT = "active_activated_at"
        const val KEY_ACTIVE_SLEEP_STARTS_AT = "active_sleep_starts_at"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY_RECORDS = 365

        const val JSON_ID = "id"
        const val JSON_ACTIVATED_AT = "activatedAt"
        const val JSON_SLEEP_STARTS_AT = "sleepStartsAt"
        const val JSON_ENDED_AT = "endedAt"
    }
}
