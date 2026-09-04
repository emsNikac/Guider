package com.nikac.guider.domain.settings

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun `system theme follows both device modes`() {
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
    }

    @Test fun `explicit theme always overrides the device`() {
        listOf(true, false).forEach { deviceIsDark ->
            assertTrue(ThemeMode.DARK.isDark(deviceIsDark))
            assertFalse(ThemeMode.LIGHT.isDark(deviceIsDark))
        }
    }

    @Test fun `stored themes round trip and unknown values fall back to system`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStoredValue(it.storedValue)) }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("unknown"))
    }
}
