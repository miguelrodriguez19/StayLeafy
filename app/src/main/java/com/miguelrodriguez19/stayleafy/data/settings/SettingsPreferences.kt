package com.miguelrodriguez19.stayleafy.data.settings

import java.time.LocalTime
import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class AppLanguage(val localeTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
}

@Serializable
data class SettingsPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val reminderMinuteOfDay: Int = DEFAULT_REMINDER_MINUTE_OF_DAY,
) {
    val reminderTime: LocalTime
        get() = LocalTime.of(reminderMinuteOfDay / MINUTES_PER_HOUR, reminderMinuteOfDay % MINUTES_PER_HOUR)

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        const val DEFAULT_REMINDER_MINUTE_OF_DAY = 9 * MINUTES_PER_HOUR
    }
}

fun LocalTime.toMinuteOfDay(): Int = hour * SettingsPreferences.MINUTES_PER_HOUR + minute
