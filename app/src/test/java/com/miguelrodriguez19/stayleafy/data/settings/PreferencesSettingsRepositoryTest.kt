package com.miguelrodriguez19.stayleafy.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emptyStore_exposesDocumentedDefaults() = runTest {
        val repository = repository(File(temporaryFolder.root, "defaults.preferences_pb"))

        assertEquals(SettingsPreferences(), repository.preferences.first())
    }

    @Test
    fun changesPersistAndAreExposedThroughTheFlow() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = repository(file)

        repository.setTheme(ThemePreference.DARK)
        repository.setLanguage(AppLanguage.SPANISH)
        repository.setReminderTime(LocalTime.of(18, 45))

        assertEquals(
            SettingsPreferences(
                theme = ThemePreference.DARK,
                language = AppLanguage.SPANISH,
                reminderMinuteOfDay = 18 * 60 + 45,
            ),
            repository.preferences.first(),
        )
    }

    private fun TestScope.repository(file: File): PreferencesSettingsRepository =
        PreferencesSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            ),
        )
}
