package com.miguelrodriguez19.stayleafy.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.ui.components.LeafyStateContent
import com.miguelrodriguez19.stayleafy.ui.components.leafyTopAppBarColors
import com.miguelrodriguez19.stayleafy.ui.theme.leafyLayoutTokens
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    notificationPermission: NotificationPermissionSummary = NotificationPermissionSummary(),
    onNotificationPermissionAction: (NotificationPermissionAction) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(notificationPermission) {
        viewModel.updateNotificationPermission(notificationPermission)
    }
    SettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onThemeSelected = viewModel::selectTheme,
        onLanguageSelected = viewModel::selectLanguage,
        onReminderTimeSelected = viewModel::selectReminderTime,
        onNotificationPermissionAction = onNotificationPermissionAction,
        onRetry = viewModel::retryLoad,
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )
}
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    onThemeSelected: (ThemePreference) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onReminderTimeSelected: (LocalTime) -> Unit,
    onNotificationPermissionAction: (NotificationPermissionAction) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDescription = stringResource(R.string.settings_back)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = leafyTopAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = backDescription
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = null,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.settings_loading),
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.error == SettingsError.LOAD -> {
                LeafyStateContent(
                    message = stringResource(R.string.settings_load_error),
                    action = stringResource(R.string.settings_retry),
                    onAction = onRetry,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .testTag("settings_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = leafyLayoutTokens.spacingLg,
                        vertical = leafyLayoutTokens.spacingMd,
                    ),
                    verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingMd),
                ) {
                    if (state.error != null) {
                        item {
                            SettingsErrorBanner(
                                error = state.error,
                                onDismiss = onDismissError,
                            )
                        }
                    }
                    item {
                        SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
                            SettingsOption(
                                label = stringResource(R.string.settings_theme_system),
                                testTag = "settings_theme_system",
                                selected = state.preferences.theme == ThemePreference.SYSTEM,
                                enabled = !state.isSaving,
                                onClick = { onThemeSelected(ThemePreference.SYSTEM) },
                            )
                            SettingsOption(
                                label = stringResource(R.string.settings_theme_light),
                                testTag = "settings_theme_light",
                                selected = state.preferences.theme == ThemePreference.LIGHT,
                                enabled = !state.isSaving,
                                onClick = { onThemeSelected(ThemePreference.LIGHT) },
                            )
                            SettingsOption(
                                label = stringResource(R.string.settings_theme_dark),
                                testTag = "settings_theme_dark",
                                selected = state.preferences.theme == ThemePreference.DARK,
                                enabled = !state.isSaving,
                                onClick = { onThemeSelected(ThemePreference.DARK) },
                            )
                        }
                    }
                    item {
                        SettingsSectionCard(title = stringResource(R.string.settings_language)) {
                            SettingsOption(
                                label = stringResource(R.string.settings_language_system),
                                testTag = "settings_language_system",
                                selected = state.preferences.language == AppLanguage.SYSTEM,
                                enabled = !state.isSaving,
                                onClick = { onLanguageSelected(AppLanguage.SYSTEM) },
                            )
                            SettingsOption(
                                label = stringResource(R.string.settings_language_english),
                                testTag = "settings_language_english",
                                selected = state.preferences.language == AppLanguage.ENGLISH,
                                enabled = !state.isSaving,
                                onClick = { onLanguageSelected(AppLanguage.ENGLISH) },
                            )
                            SettingsOption(
                                label = stringResource(R.string.settings_language_spanish),
                                testTag = "settings_language_spanish",
                                selected = state.preferences.language == AppLanguage.SPANISH,
                                enabled = !state.isSaving,
                                onClick = { onLanguageSelected(AppLanguage.SPANISH) },
                            )
                        }
                    }
                    item {
                        ReminderTimeSection(
                            minuteOfDay = state.preferences.reminderMinuteOfDay,
                            enabled = !state.isSaving,
                            onTimeSelected = onReminderTimeSelected,
                        )
                    }
                    item {
                        PermissionSection(
                            summary = state.notificationPermission,
                            onAction = onNotificationPermissionAction,
                        )
                    }
                    item {
                        AboutSection(appVersionName = state.appVersionName)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(leafyLayoutTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingsOption(
    label: String,
    testTag: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .testTag(testTag)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = leafyLayoutTokens.spacingSm),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
@Composable
private fun ReminderTimeSection(
    minuteOfDay: Int,
    enabled: Boolean,
    onTimeSelected: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val selectedTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val timeText = remember(selectedTime, locale) {
        selectedTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }

    SettingsSectionCard(title = stringResource(R.string.settings_reminders)) {
        Text(
            text = stringResource(R.string.settings_reminder_time_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onTimeSelected(LocalTime.of(hour, minute)) },
                        selectedTime.hour,
                        selectedTime.minute,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(leafyLayoutTokens.spacingLg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_reminder_time))
                Text(
                    text = timeText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun PermissionSection(
    summary: NotificationPermissionSummary,
    onAction: (NotificationPermissionAction) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.settings_permissions)) {
        PermissionRow(
            title = stringResource(R.string.settings_notifications),
            supporting = stringResource(
                when (summary.status) {
                    NotificationPermissionStatus.UNKNOWN -> R.string.settings_notification_status_unknown
                    NotificationPermissionStatus.ENABLED -> R.string.settings_notification_status_enabled
                    NotificationPermissionStatus.NEEDS_PERMISSION -> R.string.settings_notification_status_needs_permission
                    NotificationPermissionStatus.BLOCKED -> R.string.settings_notification_status_blocked
                },
            ),
        )
        if (summary.action != NotificationPermissionAction.NONE) {
            Button(onClick = { onAction(summary.action) }) {
                Text(
                    stringResource(
                        when (summary.action) {
                            NotificationPermissionAction.NONE -> R.string.settings_notification_action_none
                            NotificationPermissionAction.REQUEST_PERMISSION -> R.string.settings_allow_notifications
                            NotificationPermissionAction.OPEN_SYSTEM_SETTINGS -> R.string.settings_open_notification_settings
                        },
                    ),
                )
            }
        }
        HorizontalDivider()
        PermissionRow(
            title = stringResource(R.string.settings_camera),
            supporting = stringResource(R.string.settings_no_permission_required),
        )
        HorizontalDivider()
        PermissionRow(
            title = stringResource(R.string.settings_photos),
            supporting = stringResource(R.string.settings_no_permission_required),
        )
    }
}

@Composable
private fun PermissionRow(title: String, supporting: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutSection(appVersionName: String) {
    SettingsSectionCard(title = stringResource(R.string.settings_about)) {
        Text(
            text = stringResource(
                R.string.settings_version,
                appVersionName.ifBlank { stringResource(R.string.settings_version_unknown) },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_offline_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsErrorBanner(error: SettingsError, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    when (error) {
                        SettingsError.LOAD -> R.string.settings_load_error
                        SettingsError.SAVE -> R.string.settings_save_error
                        SettingsError.LANGUAGE_APPLY -> R.string.settings_language_apply_error
                    },
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_dismiss))
            }
        }
    }
}

private const val SETTINGS_PREVIEW_VERSION = "preview"

@Preview(name = "Settings light", showBackground = true)
@Preview(
    name = "Settings dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsPreview() {
    StayLeafyTheme {
        SettingsScreen(
            state = SettingsUiState(
                isLoading = false,
                appVersionName = SETTINGS_PREVIEW_VERSION,
                notificationPermission = NotificationPermissionSummary(
                    status = NotificationPermissionStatus.ENABLED,
                    action = NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
                ),
            ),
            onNavigateBack = {},
            onThemeSelected = {},
            onLanguageSelected = {},
            onReminderTimeSelected = {},
            onNotificationPermissionAction = {},
            onRetry = {},
            onDismissError = {},
        )
    }
}
