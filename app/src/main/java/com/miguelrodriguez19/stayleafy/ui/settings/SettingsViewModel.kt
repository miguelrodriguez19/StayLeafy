package com.miguelrodriguez19.stayleafy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.AppLocaleController
import com.miguelrodriguez19.stayleafy.data.settings.AppVersionProvider
import com.miguelrodriguez19.stayleafy.data.settings.SettingsRepository
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.data.settings.toMinuteOfDay
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val localeController: AppLocaleController,
    appVersionProvider: AppVersionProvider,
    private val operations: DataOperationCoordinator = DataOperationCoordinator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(appVersionName = appVersionProvider.versionName),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var preferencesJob: Job? = null
    init {
        observePreferences()
    }

    fun selectTheme(theme: ThemePreference) {
        if (theme == _uiState.value.preferences.theme) return
        savePreference { repository.setTheme(theme) }
    }

    fun selectLanguage(language: AppLanguage) {
        if (language == _uiState.value.preferences.language) return
        savePreference {
            localeController.apply(language)
            repository.setLanguage(language)
        }
    }

    fun selectReminderTime(time: LocalTime) {
        if (time.toMinuteOfDay() == _uiState.value.preferences.reminderMinuteOfDay) return
        savePreference { repository.setReminderTime(time) }
    }

    fun updateNotificationPermission(summary: NotificationPermissionSummary) {
        _uiState.update { it.copy(notificationPermission = summary) }
    }

    fun retryLoad() {
        observePreferences()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observePreferences() {
        preferencesJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        preferencesJob = viewModelScope.launch {
            repository.preferences
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isLoading = false, error = SettingsError.LOAD) }
                }
                .collect { preferences ->
                    _uiState.update {
                        it.copy(isLoading = false, preferences = preferences, error = null)
                    }
                }
        }
    }

    private fun savePreference(change: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                operations.withOperation { change() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(error = SettingsError.SAVE) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
