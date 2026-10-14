package com.miguelrodriguez19.stayleafy.ui.settings

import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences

enum class SettingsError {
    LOAD,
    SAVE,
    LANGUAGE_APPLY,
}

enum class NotificationPermissionStatus {
    UNKNOWN,
    ENABLED,
    NEEDS_PERMISSION,
    BLOCKED,
}

enum class NotificationPermissionAction {
    NONE,
    REQUEST_PERMISSION,
    OPEN_SYSTEM_SETTINGS,
}

data class NotificationPermissionSummary(
    val status: NotificationPermissionStatus = NotificationPermissionStatus.UNKNOWN,
    val action: NotificationPermissionAction = NotificationPermissionAction.NONE,
)

data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: SettingsPreferences = SettingsPreferences(),
    val notificationPermission: NotificationPermissionSummary = NotificationPermissionSummary(),
    val appVersionName: String = "",
    val isSaving: Boolean = false,
    val error: SettingsError? = null,
)
