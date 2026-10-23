package com.miguelrodriguez19.stayleafy.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalTime

interface SettingsRepository {
    val preferences: Flow<SettingsPreferences>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setReminderTime(time: LocalTime)

    /** Backup must fail on read errors, never journal a fallback value as the old preference. */
    suspend fun snapshotPreferences(): SettingsPreferences = preferences.first()

    /** Implementations must replace the three preferences in one durable update. */
    suspend fun replacePreferences(preferences: SettingsPreferences)
}
