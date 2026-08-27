package com.example.guider.data.money

import android.content.Context
import androidx.core.content.edit
import com.example.guider.domain.money.MoneyLedger
import com.example.guider.domain.money.MoneyRepository
import com.example.guider.domain.money.Spending
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesMoneyRepository(context: Context) : MoneyRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableLedger = MutableStateFlow(readLedger())

    override val ledger: StateFlow<MoneyLedger> = mutableLedger.asStateFlow()

    @Synchronized
    override fun addSpending(
        title: String,
        amountMinor: Long,
        createdAtEpochMillis: Long,
    ): Spending {
        require(title.isNotBlank())
        require(amountMinor > 0L)
        val current = mutableLedger.value
        val spending = Spending(
            id = (current.spendings.maxOfOrNull(Spending::id) ?: 0L) + 1L,
            title = title.trim(),
            amountMinor = amountMinor,
            createdAtEpochMillis = createdAtEpochMillis,
        )
        persist(
            current.copy(
                spendings = current.spendings + spending,
                periodStartDayKey = current.periodStartDayKey
                    ?: DayKeys.today(createdAtEpochMillis),
            ),
        )
        return spending
    }

    @Synchronized
    override fun editSpending(spendingId: Long, title: String, amountMinor: Long) {
        require(title.isNotBlank())
        require(amountMinor > 0L)
        val current = mutableLedger.value
        val updated = current.spendings.map { spending ->
            if (spending.id == spendingId) {
                spending.copy(title = title.trim(), amountMinor = amountMinor)
            } else {
                spending
            }
        }
        if (updated != current.spendings) persist(current.copy(spendings = updated))
    }

    @Synchronized
    override fun deleteSpending(spendingId: Long) {
        val current = mutableLedger.value
        val updated = current.spendings.filterNot { it.id == spendingId }
        if (updated != current.spendings) persist(current.copy(spendings = updated))
    }

    @Synchronized
    override fun restart(dayKey: Int) {
        persist(MoneyLedger(periodStartDayKey = dayKey))
    }

    private fun readLedger(): MoneyLedger = runCatching {
        val encoded = preferences.getString(KEY_SPENDINGS, null)
        val spendings = if (encoded == null) {
            emptyList()
        } else {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        Spending(
                            id = item.getLong(JSON_ID),
                            title = item.getString(JSON_TITLE),
                            amountMinor = item.getLong(JSON_AMOUNT_MINOR),
                            createdAtEpochMillis = item.getLong(JSON_CREATED_AT),
                        ),
                    )
                }
            }
        }
        MoneyLedger(
            spendings = spendings,
            periodStartDayKey = preferences
                .takeIf { it.contains(KEY_PERIOD_START) }
                ?.getInt(KEY_PERIOD_START, 0)
                ?.takeIf { it > 0 },
        )
    }.getOrElse { MoneyLedger() }

    private fun persist(ledger: MoneyLedger) {
        preferences.edit {
            putString(
                KEY_SPENDINGS,
                JSONArray().apply {
                    ledger.spendings.forEach { spending ->
                        put(
                            JSONObject()
                                .put(JSON_ID, spending.id)
                                .put(JSON_TITLE, spending.title)
                                .put(JSON_AMOUNT_MINOR, spending.amountMinor)
                                .put(JSON_CREATED_AT, spending.createdAtEpochMillis),
                        )
                    }
                }.toString(),
            )
            ledger.periodStartDayKey?.let { putInt(KEY_PERIOD_START, it) }
                ?: remove(KEY_PERIOD_START)
        }
        mutableLedger.value = ledger
    }

    private companion object {
        const val PREFERENCES_NAME = "money_tracking"
        const val KEY_SPENDINGS = "spendings"
        const val KEY_PERIOD_START = "periodStart"
        const val JSON_ID = "id"
        const val JSON_TITLE = "title"
        const val JSON_AMOUNT_MINOR = "amountMinor"
        const val JSON_CREATED_AT = "createdAt"
    }
}
