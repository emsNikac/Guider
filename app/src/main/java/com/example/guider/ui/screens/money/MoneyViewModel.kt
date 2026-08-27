package com.example.guider.ui.screens.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.guider.GuiderApplication
import com.example.guider.domain.time.DayKeys

class MoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as GuiderApplication).moneyRepository

    val ledger = repository.ledger

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
