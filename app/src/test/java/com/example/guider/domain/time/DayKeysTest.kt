package com.example.guider.domain.time

import java.util.Calendar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayKeysTest {
    @Test
    fun addDays_handlesLeapYearsAndYearBoundaries() {
        assertEquals(20240229, DayKeys.addDays(20240228, 1))
        assertEquals(20240301, DayKeys.addDays(20240229, 1))
        assertEquals(20240101, DayKeys.addDays(20231231, 1))
        assertEquals(20231231, DayKeys.addDays(20240101, -1))
    }

    @Test
    fun weekday_matchesKnownCalendarDays() {
        assertEquals(Calendar.THURSDAY, DayKeys.weekday(19700101))
        assertEquals(Calendar.THURSDAY, DayKeys.weekday(20240829))
        assertEquals(Calendar.THURSDAY, DayKeys.weekday(20240229))
        assertEquals(Calendar.THURSDAY, DayKeys.weekday(20260101))
    }

    @Test
    fun inclusiveRange_returnsEveryDayWithoutCalendarAllocationPerItem() {
        assertArrayEquals(
            intArrayOf(20240227, 20240228, 20240229, 20240301, 20240302),
            DayKeys.inclusiveRange(20240227, 20240302),
        )
        assertEquals(5, DayKeys.inclusiveDayCount(20240227, 20240302))
        assertTrue(DayKeys.inclusiveRange(20240302, 20240227).isEmpty())
    }

    @Test
    fun epochConversion_roundTripsAcrossSupportedApplicationDates() {
        val dates = listOf(19000101, 19991231, 20000229, 20260228, 21001231)

        dates.forEach { dayKey ->
            assertEquals(dayKey, DayKeys.addDays(DayKeys.addDays(dayKey, 10_000), -10_000))
        }
    }
}
