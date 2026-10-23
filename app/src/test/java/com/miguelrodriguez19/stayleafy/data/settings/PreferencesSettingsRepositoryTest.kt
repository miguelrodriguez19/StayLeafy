package com.miguelrodriguez19.stayleafy.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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

    @Test
    fun restoreReplacesAllPreferencesInOnePersistedSnapshot() = runTest {
        val repository = repository(File(temporaryFolder.root, "restore.preferences_pb"))
        repository.setTheme(ThemePreference.DARK)
        val restored = SettingsPreferences(ThemePreference.LIGHT, AppLanguage.SPANISH, 23 * 60 + 59)

        repository.replacePreferences(restored)

        assertEquals(restored, repository.preferences.first())
        assertEquals(restored, repository.snapshotPreferences())
        try {
            repository.replacePreferences(restored.copy(reminderMinuteOfDay = 1440))
            fail("Invalid restore time must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(restored, repository.snapshotPreferences())
        }
    }

    @Test
    fun backupReadDoesNotReplaceIoFailureWithUiDefaults() = runTest {
        val failingStore = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("Unavailable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("No write expected")
        }
        val repository = PreferencesSettingsRepository(failingStore)
        assertEquals(SettingsPreferences(), repository.preferences.first())
        try {
            repository.snapshotPreferences()
            fail("Backup must fail rather than journal fallback preferences")
        } catch (_: IOException) {
            // Expected: an unavailable old snapshot must never start replacement.
        }
    }

    private fun TestScope.repository(file: File): PreferencesSettingsRepository =
        PreferencesSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            ),
        )
}
