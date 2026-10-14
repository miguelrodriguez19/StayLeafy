package com.miguelrodriguez19.stayleafy.data.settings

import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface SettingsRepository {
    val preferences: Flow<SettingsPreferences>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setReminderTime(time: LocalTime)
}
