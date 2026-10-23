package com.miguelrodriguez19.stayleafy.ui.settings

import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.AppLocaleController
import com.miguelrodriguez19.stayleafy.data.settings.AppVersionProvider
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.SettingsRepository
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialState_exposesDefaultsWithoutReapplyingLanguage() = runTest {
        val repository = FakeSettingsRepository()
        val locale = RecordingLocaleController()
        val viewModel = viewModel(repository, locale)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(SettingsPreferences(), viewModel.uiState.value.preferences)
        assertEquals("test-build", viewModel.uiState.value.appVersionName)
        assertTrue(locale.appliedLanguages.isEmpty())
    }

    @Test
    fun selectingTheme_persistsImmediately() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()

        assertEquals(ThemePreference.DARK, repository.value.value.theme)
        assertEquals(ThemePreference.DARK, viewModel.uiState.value.preferences.theme)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun selectingLanguage_persistsAndAppliesThroughInjectedController() = runTest {
        val repository = FakeSettingsRepository()
        val locale = RecordingLocaleController()
        val viewModel = viewModel(repository, locale)
        advanceUntilIdle()

        viewModel.selectLanguage(AppLanguage.SPANISH)
        advanceUntilIdle()

        assertEquals(AppLanguage.SPANISH, repository.value.value.language)
        assertEquals(AppLanguage.SPANISH, viewModel.uiState.value.preferences.language)
        assertEquals(listOf(AppLanguage.SPANISH), locale.appliedLanguages)
    }

    @Test
    fun selectingReminderTime_storesMinutesAfterMidnight() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.selectReminderTime(LocalTime.of(22, 45))
        advanceUntilIdle()

        assertEquals(22 * 60 + 45, repository.value.value.reminderMinuteOfDay)
        assertEquals(22 * 60 + 45, viewModel.uiState.value.preferences.reminderMinuteOfDay)
    }

    @Test
    fun notificationPermissionCanBeRefreshedByHostLifecycle() = runTest {
        val viewModel = viewModel(FakeSettingsRepository())
        advanceUntilIdle()

        val summary = NotificationPermissionSummary(
            status = NotificationPermissionStatus.BLOCKED,
            action = NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
        )
        viewModel.updateNotificationPermission(summary)

        assertEquals(summary, viewModel.uiState.value.notificationPermission)
    }

    @Test
    fun failedPreferenceWrite_exposesCategoryAndKeepsStateUsable() = runTest {
        val repository = FakeSettingsRepository().apply { failure = IllegalStateException("private") }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.selectTheme(ThemePreference.LIGHT)
        advanceUntilIdle()

        assertEquals(SettingsError.SAVE, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(ThemePreference.SYSTEM, viewModel.uiState.value.preferences.theme)
    }

    @Test
    fun retryLoad_recoversAfterRepositoryFlowFailure() = runTest {
        val repository = FakeSettingsRepository().apply { failObservations = true }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(SettingsError.LOAD, viewModel.uiState.value.error)

        repository.failObservations = false
        viewModel.retryLoad()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    private fun viewModel(
        repository: FakeSettingsRepository,
        localeController: RecordingLocaleController = RecordingLocaleController(),
    ) = SettingsViewModel(repository, localeController, FakeVersionProvider())
}

private class FakeSettingsRepository(
    initial: SettingsPreferences = SettingsPreferences(),
) : SettingsRepository {
    val value = MutableStateFlow(initial)
    var failure: Throwable? = null
    var failObservations: Boolean = false

    override val preferences: Flow<SettingsPreferences>
        get() = if (failObservations) kotlinx.coroutines.flow.flow {
            error("private flow failure")
        } else value

    override suspend fun setTheme(theme: ThemePreference) {
        failure?.let { throw it }
        value.value = value.value.copy(theme = theme)
    }

    override suspend fun replacePreferences(preferences: SettingsPreferences) {
        failure?.let { throw it }
        value.value = preferences
    }

    override suspend fun setLanguage(language: AppLanguage) {
        failure?.let { throw it }
        value.value = value.value.copy(language = language)
    }

    override suspend fun setReminderTime(time: LocalTime) {
        failure?.let { throw it }
        value.value = value.value.copy(
            reminderMinuteOfDay = time.hour * 60 + time.minute,
        )
    }
}

private class RecordingLocaleController : AppLocaleController {
    val appliedLanguages = mutableListOf<AppLanguage>()

    override fun apply(language: AppLanguage) {
        appliedLanguages += language
    }
}

private class FakeVersionProvider : AppVersionProvider {
    override val versionName: String = "test-build"
}
