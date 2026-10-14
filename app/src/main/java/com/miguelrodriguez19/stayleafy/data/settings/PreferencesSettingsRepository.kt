package com.miguelrodriguez19.stayleafy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val preferences: Flow<SettingsPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toSettingsPreferences)

    override suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { preferences -> preferences[THEME_KEY] = theme.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { preferences -> preferences[LANGUAGE_KEY] = language.name }
    }

    override suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit { preferences -> preferences[REMINDER_MINUTE_KEY] = time.toMinuteOfDay() }
    }

    private fun toSettingsPreferences(preferences: Preferences): SettingsPreferences {
        val storedMinute = preferences[REMINDER_MINUTE_KEY]
        return SettingsPreferences(
            theme = preferences[THEME_KEY].enumValueOrDefault(ThemePreference.SYSTEM),
            language = preferences[LANGUAGE_KEY].enumValueOrDefault(AppLanguage.SYSTEM),
            reminderMinuteOfDay = storedMinute
                ?.takeIf { it in 0 until SettingsPreferences.MINUTES_PER_DAY }
                ?: SettingsPreferences.DEFAULT_REMINDER_MINUTE_OF_DAY,
        )
    }

    private inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_preference")
        val LANGUAGE_KEY = stringPreferencesKey("app_language")
        val REMINDER_MINUTE_KEY = intPreferencesKey("reminder_minute_of_day")
    }
}
