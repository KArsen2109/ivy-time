package com.ivy.time.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

enum class ThemeMode { System, Light, Dark, AmoledDark }

data class AppSettings(
    val theme: ThemeMode,
    val weekStart: DayOfWeek,
    val use24Hours: Boolean,
    val onboardingDone: Boolean,
)

private val Context.dataStore by preferencesDataStore(name = "ivy-time-settings")

class SettingsStore(
    private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme")
    private val weekStartKey = intPreferencesKey("week_start")
    private val use24HoursKey = booleanPreferencesKey("use_24_hours")
    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = ThemeMode.entries
                .firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.System,
            weekStart = DayOfWeek.of(prefs[weekStartKey] ?: DayOfWeek.MONDAY.value),
            use24Hours = prefs[use24HoursKey] ?: true,
            onboardingDone = prefs[onboardingKey] ?: false,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        context.dataStore.edit { it[weekStartKey] = day.value }
    }

    suspend fun setUse24Hours(use24Hours: Boolean) {
        context.dataStore.edit { it[use24HoursKey] = use24Hours }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[onboardingKey] = done }
    }
}
