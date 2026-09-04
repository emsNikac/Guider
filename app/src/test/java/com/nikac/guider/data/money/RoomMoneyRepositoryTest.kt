package com.nikac.guider.data.money

import com.nikac.guider.data.database.MoneyLedgerRow
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomMoneyRepositoryTest {
    @Test
    fun `ledger rows map to one consistent total and ordered list`() {
        val ledger = listOf(
            MoneyLedgerRow(
                periodStartDayKey = 20260801,
                spendingId = 2,
                spendingTitle = "Lunch",
                spendingAmountMinor = 1_250,
                spendingCreatedAtEpochMillis = 200,
            ),
            MoneyLedgerRow(
                periodStartDayKey = 20260801,
                spendingId = 1,
                spendingTitle = "Coffee",
                spendingAmountMinor = 350,
                spendingCreatedAtEpochMillis = 100,
            ),
        ).toMoneyLedger()

        assertEquals(20260801, ledger.periodStartDayKey)
        assertEquals(1_600L, ledger.totalMinor)
        assertEquals(listOf("Lunch", "Coffee"), ledger.spendings.map { it.title })
    }

    @Test
    fun `left join placeholder preserves period without creating a spending`() {
        val ledger = listOf(
            MoneyLedgerRow(
                periodStartDayKey = 20260801,
                spendingId = null,
                spendingTitle = null,
                spendingAmountMinor = null,
                spendingCreatedAtEpochMillis = null,
            ),
        ).toMoneyLedger()

        assertEquals(20260801, ledger.periodStartDayKey)
        assertEquals(0L, ledger.totalMinor)
        assertEquals(emptyList<Any>(), ledger.spendings)
    }
}
