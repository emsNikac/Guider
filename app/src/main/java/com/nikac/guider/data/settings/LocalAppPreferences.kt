package com.nikac.guider.data.settings

import android.annotation.SuppressLint
import android.content.Context
import com.nikac.guider.domain.settings.AppPreferences
import com.nikac.guider.domain.settings.AppPreferencesStore
import com.nikac.guider.domain.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// The KTX helper discards commit's result; these writes must report persistence failures.
@SuppressLint("UseKtx")
class LocalAppPreferences(context: Context) : AppPreferencesStore {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        appContext.getSharedPreferences("guider_app_preferences", Context.MODE_PRIVATE)
    }

    override suspend fun read(): AppPreferences = withContext(Dispatchers.IO) {
        AppPreferences(
            themeMode = ThemeMode.fromStoredValue(preferences.getString("theme_mode", null)),
            continueAsGuest = preferences.getBoolean("continue_as_guest", false),
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("theme_mode", mode.storedValue).commit()) {
            throw IOException("Unable to save theme preference")
        }
    }

    override suspend fun setContinueAsGuest(guest: Boolean) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putBoolean("continue_as_guest", guest).commit()) {
            throw IOException("Unable to save guest preference")
        }
    }
}
