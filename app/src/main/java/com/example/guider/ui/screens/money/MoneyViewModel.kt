package com.example.guider.ui.screens.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guider.GuiderApplication
import com.example.guider.domain.money.MoneyCalculations
import com.example.guider.domain.money.MoneyLedger
import com.example.guider.domain.money.Spending
import com.example.guider.domain.time.DayKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

internal data class MoneyUiState(
    val ledger: MoneyLedger = MoneyLedger(),
    val totalMinor: Long = 0L,
    val sortedSpendings: List<Spending> = emptyList(),
)

class MoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as GuiderApplication).moneyRepository

    internal val uiState = repository.ledger
        .map { ledger ->
            withContext(Dispatchers.Default) {
                MoneyUiState(
                    ledger = ledger,
                    totalMinor = MoneyCalculations.totalMinor(ledger.spendings),
                    sortedSpendings = ledger.spendings
                        .sortedByDescending(Spending::createdAtEpochMillis),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MoneyUiState(),
        )

    fun addSpending(title: String, amountMinor: Long) {
        if (title.isNotBlank() && amountMinor > 0L) {
            repository.addSpending(title, amountMinor)
        }
    }

    fun editSpending(spendingId: Long, title: String, amountMinor: Long) {
        if (title.isNotBlank() && amountMinor > 0L) {
            repository.editSpending(spendingId, title, amountMinor)
        }
    }

    fun deleteSpending(spendingId: Long) {
        repository.deleteSpending(spendingId)
    }

    fun restart() {
        repository.restart(DayKeys.today())
    }
}
