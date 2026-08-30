package com.example.guider.ui.screens.money

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guider.GuiderApplication
import com.example.guider.domain.money.Spending
import com.example.guider.domain.time.DayKeys
import com.example.guider.domain.collections.ImmutableListSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
internal data class MoneyUiState(
    val totalMinor: Long = 0L,
    val periodStartDayKey: Int? = null,
    val sortedSpendings: ImmutableListSnapshot<Spending> = ImmutableListSnapshot(emptyList()),
)

class MoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as GuiderApplication).moneyRepository

    internal val uiState = repository.ledger
        .map { ledger ->
            MoneyUiState(
                totalMinor = ledger.totalMinor,
                periodStartDayKey = ledger.periodStartDayKey,
                sortedSpendings = ledger.spendings,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = MoneyUiState(),
        )

    fun addSpending(title: String, amountMinor: Long) {
        if (title.isNotBlank() && amountMinor > 0L) {
            viewModelScope.launch {
                repository.addSpending(title, amountMinor)
            }
        }
    }

    fun editSpending(spendingId: Long, title: String, amountMinor: Long) {
        if (title.isNotBlank() && amountMinor > 0L) {
            viewModelScope.launch {
                repository.editSpending(spendingId, title, amountMinor)
            }
        }
    }

    fun deleteSpending(spendingId: Long) {
        viewModelScope.launch {
            repository.deleteSpending(spendingId)
        }
    }

    fun restart() {
        viewModelScope.launch {
            repository.restart(DayKeys.today())
        }
    }
}
