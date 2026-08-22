package com.example.guider.domain.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCycleCalculatorTest {
    @Test
    fun `suggestions include fall asleep time and six ninety minute cycles`() {
        val start = 1_000_000L

        val suggestions = SleepCycleCalculator.suggestions(start)

        assertEquals(6, suggestions.size)
        assertEquals(start + minutesToMillis(105), suggestions[0].wakeAtEpochMillis)
        assertEquals(start + minutesToMillis(465), suggestions[4].wakeAtEpochMillis)
        assertEquals(start + minutesToMillis(555), suggestions[5].wakeAtEpochMillis)
    }

    @Test
    fun `only fifth and sixth visible cycles are recommended`() {
        val suggestions = SleepCycleCalculator.suggestions(0L)

        suggestions.take(4).forEach { assertFalse(it.isRecommended) }
        suggestions.drop(4).forEach { assertTrue(it.isRecommended) }
    }

    @Test
    fun `hibernation effective start is fifteen minutes after activation`() {
        val activation = 50_000L

        assertEquals(
            activation + minutesToMillis(15),
            SleepCycleCalculator.effectiveSleepStart(activation),
        )
    }

    @Test
    fun `sleep record duration excludes the fall asleep period`() {
        val record = SleepRecord(
            id = 1L,
            activatedAtEpochMillis = 0L,
            sleepStartsAtEpochMillis = minutesToMillis(15),
            endedAtEpochMillis = minutesToMillis(495),
        )

        assertEquals(minutesToMillis(480), record.durationMillis)
    }

    private fun minutesToMillis(minutes: Int): Long = minutes * 60_000L
}
