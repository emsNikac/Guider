package com.example.guider.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyCalculationsTest {
    @Test
    fun `sums all documented spending amounts`() {
        val spendings = listOf(
            Spending(1L, "Lunch", 1_250L, 1L),
            Spending(2L, "Train ticket", 2_075L, 2L),
            Spending(3L, "Coffee", 325L, 3L),
        )

        assertEquals(3_650L, MoneyCalculations.totalMinor(spendings))
    }

    @Test
    fun `parses dot or comma decimal amounts into minor units`() {
        assertEquals(1_250L, MoneyCalculations.parseAmountToMinor("12.50"))
        assertEquals(1_250L, MoneyCalculations.parseAmountToMinor("12,50"))
        assertEquals(1_200L, MoneyCalculations.parseAmountToMinor("12"))
    }

    @Test
    fun `rejects invalid or non-positive amounts`() {
        assertNull(MoneyCalculations.parseAmountToMinor(""))
        assertNull(MoneyCalculations.parseAmountToMinor("0"))
        assertNull(MoneyCalculations.parseAmountToMinor("-4"))
        assertNull(MoneyCalculations.parseAmountToMinor("1.234"))
    }

    @Test
    fun `formats stored amount for editing without currency text`() {
        assertEquals("12.5", MoneyCalculations.minorToInput(1_250L))
    }
}
