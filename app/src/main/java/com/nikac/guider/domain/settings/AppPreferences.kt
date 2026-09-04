package com.nikac.guider.domain.settings

enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val continueAsGuest: Boolean = false,
)

interface AppPreferencesStore {
    suspend fun read(): AppPreferences
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setContinueAsGuest(guest: Boolean)
}
