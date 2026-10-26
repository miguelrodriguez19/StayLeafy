package com.miguelrodriguez19.stayleafy.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
import com.miguelrodriguez19.stayleafy.ui.theme.leafyColors
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
    onBackupClick: () -> Unit = {},
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
        onBackupClick = onBackupClick,
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
    onBackupClick: () -> Unit = {},
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
                        style = MaterialTheme.typography.titleLarge,
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
                        SettingsSectionCard(
                            title = stringResource(R.string.settings_appearance),
                            icon = R.drawable.ic_leaf,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_theme_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            SettingsSegmentedControl(
                                options = listOf(
                                    SettingsSegment(
                                        key = THEME_SYSTEM,
                                        label = stringResource(R.string.settings_system),
                                        testTag = "settings_theme_system",
                                    ),
                                    SettingsSegment(
                                        key = THEME_LIGHT,
                                        label = stringResource(R.string.settings_theme_light),
                                        testTag = "settings_theme_light",
                                    ),
                                    SettingsSegment(
                                        key = THEME_DARK,
                                        label = stringResource(R.string.settings_theme_dark),
                                        testTag = "settings_theme_dark",
                                    ),
                                ),
                                selectedKey = state.preferences.theme.segmentKey,
                                enabled = !state.isSaving,
                                onSelected = { key ->
                                    onThemeSelected(
                                        when (key) {
                                            THEME_LIGHT -> ThemePreference.LIGHT
                                            THEME_DARK -> ThemePreference.DARK
                                            else -> ThemePreference.SYSTEM
                                        },
                                    )
                                },
                            )
                            Text(
                                text = stringResource(R.string.settings_language),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            SettingsSegmentedControl(
                                options = listOf(
                                    SettingsSegment(
                                        key = LANGUAGE_SYSTEM,
                                        label = stringResource(R.string.settings_system),
                                        testTag = "settings_language_system",
                                    ),
                                    SettingsSegment(
                                        key = LANGUAGE_ENGLISH,
                                        label = stringResource(R.string.settings_language_english),
                                        testTag = "settings_language_english",
                                    ),
                                    SettingsSegment(
                                        key = LANGUAGE_SPANISH,
                                        label = stringResource(R.string.settings_language_spanish),
                                        testTag = "settings_language_spanish",
                                    ),
                                ),
                                selectedKey = state.preferences.language.segmentKey,
                                enabled = !state.isSaving,
                                onSelected = { key ->
                                    onLanguageSelected(
                                        when (key) {
                                            LANGUAGE_ENGLISH -> AppLanguage.ENGLISH
                                            LANGUAGE_SPANISH -> AppLanguage.SPANISH
                                            else -> AppLanguage.SYSTEM
                                        },
                                    )
                                },
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
                        BackupSection(onClick = onBackupClick)
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
    icon: Int,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(leafyLayoutTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

private data class SettingsSegment(
    val key: String,
    val label: String,
    val testTag: String,
)

@Composable
private fun SettingsSegmentedControl(
    options: List<SettingsSegment>,
    selectedKey: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val selected = option.key == selectedKey
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.small)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                        ) { onSelected(option.key) }
                        .testTag(option.testTag),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
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

    SettingsSectionCard(
        title = stringResource(R.string.settings_reminders),
        icon = R.drawable.ic_water,
    ) {
        Text(
            text = stringResource(R.string.settings_reminder_time_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(leafyLayoutTokens.spacingLg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.settings_reminder_time))
                }
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
private fun BackupSection(onClick: () -> Unit) {
    SettingsSectionCard(
        title = stringResource(R.string.backup_settings_title),
        icon = R.drawable.ic_note,
    ) {
        Text(
            text = stringResource(R.string.backup_settings_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.backup_settings_action))
        }
    }
}

@Composable
private fun PermissionSection(
    summary: NotificationPermissionSummary,
    onAction: (NotificationPermissionAction) -> Unit,
) {
    val statusLabel = stringResource(
        when (summary.status) {
            NotificationPermissionStatus.UNKNOWN -> R.string.settings_notification_status_unknown
            NotificationPermissionStatus.ENABLED -> R.string.settings_notification_status_enabled
            NotificationPermissionStatus.NEEDS_PERMISSION -> R.string.settings_notification_status_needs_permission
            NotificationPermissionStatus.BLOCKED -> R.string.settings_notification_status_blocked
        },
    )
    val actionLabel = if (summary.action == NotificationPermissionAction.NONE) {
        null
    } else {
        stringResource(
            when (summary.action) {
                NotificationPermissionAction.NONE -> R.string.settings_notification_action_none
                NotificationPermissionAction.REQUEST_PERMISSION -> R.string.settings_allow_notifications
                NotificationPermissionAction.OPEN_SYSTEM_SETTINGS -> R.string.settings_open_notification_settings
            },
        )
    }
    SettingsSectionCard(
        title = stringResource(R.string.settings_permissions),
        icon = R.drawable.ic_bell,
    ) {
        PermissionRow(
            icon = R.drawable.ic_bell,
            title = stringResource(R.string.settings_notifications),
            supporting = if (summary.status == NotificationPermissionStatus.ENABLED) {
                stringResource(R.string.settings_notification_enabled_supporting)
            } else {
                statusLabel
            },
            trailingMetrics = PermissionRowTrailingMetrics(statusLabel, actionLabel),
            trailing = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PermissionStatusPill(
                        status = summary.status,
                        label = statusLabel,
                        modifier = Modifier.testTag("settings_notifications_status"),
                    )
                    if (summary.action != NotificationPermissionAction.NONE) {
                        TextButton(
                            onClick = { onAction(summary.action) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_notifications_action"),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(
                                actionLabel.orEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            },
            rowTestTag = "settings_notifications_row",
            iconTestTag = "settings_notifications_icon",
            titleTestTag = "settings_notifications_title",
            informationTestTag = "settings_notifications_information",
            trailingTestTag = "settings_notifications_trailing",
        )
        HorizontalDivider()
        PermissionRow(
            icon = R.drawable.ic_camera,
            title = stringResource(R.string.settings_camera),
            supporting = stringResource(R.string.settings_no_permission_required),
            rowTestTag = "settings_camera_row",
            iconTestTag = "settings_camera_icon",
            titleTestTag = "settings_camera_title",
        )
        HorizontalDivider()
        PermissionRow(
            icon = R.drawable.ic_image,
            title = stringResource(R.string.settings_photos),
            supporting = stringResource(R.string.settings_no_permission_required),
            rowTestTag = "settings_photos_row",
            iconTestTag = "settings_photos_icon",
            titleTestTag = "settings_photos_title",
        )
    }
}

@Composable
private fun PermissionRow(
    icon: Int,
    title: String,
    supporting: String,
    trailing: @Composable (() -> Unit)? = null,
    trailingMetrics: PermissionRowTrailingMetrics? = null,
    rowTestTag: String? = null,
    iconTestTag: String? = null,
    titleTestTag: String? = null,
    informationTestTag: String? = null,
    trailingTestTag: String? = null,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .withOptionalTestTag(rowTestTag),
    ) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val useHorizontalLayout = if (trailing == null || trailingMetrics == null) {
            false
        } else {
            val columnGap = with(density) { leafyLayoutTokens.spacingSm.toPx() }
            val availableColumnWidth = maxOf(0f, with(density) { maxWidth.toPx() } - columnGap)
            val infoWidth = availableColumnWidth * (2f / 3f)
            val trailingWidth = availableColumnWidth / 3f
            val iconAndGap = with(density) { (18.dp + leafyLayoutTokens.spacingSm).toPx() }
            val titleWidth = infoWidth - iconAndGap
            val titleRequiredWidth = textMeasurer.measure(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                constraints = Constraints(maxWidth = Constraints.Infinity),
            ).size.width.toFloat()
            fun fitsInTwoLines(text: String, style: TextStyle, width: Float): Boolean {
                if (width < 1f) return false
                val layout = textMeasurer.measure(
                    text = text,
                    style = style,
                    constraints = Constraints(maxWidth = width.toInt()),
                    maxLines = 2,
                )
                return !layout.hasVisualOverflow
            }

            val statusFits = fitsInTwoLines(
                text = trailingMetrics.statusLabel,
                style = MaterialTheme.typography.labelSmall,
                width = trailingWidth - with(density) { 20.dp.toPx() },
            )
            val actionFits = trailingMetrics.actionLabel?.let { label ->
                trailingWidth >= with(density) { 48.dp.toPx() } && fitsInTwoLines(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    width = trailingWidth,
                )
            } ?: true
            // Both labels may wrap to two lines in the side column. Move the complete status/action
            // block below the information if either needs more space; never split the pair.
            titleRequiredWidth <= titleWidth &&
                statusFits && actionFits
        }

        if (useHorizontalLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
                verticalAlignment = Alignment.Top,
            ) {
                PermissionRowInformation(
                    modifier = Modifier
                        .weight(2f)
                        .withOptionalTestTag(informationTestTag),
                    icon = icon,
                    title = title,
                    supporting = supporting,
                    iconTestTag = iconTestTag,
                    titleTestTag = titleTestTag,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .withOptionalTestTag(trailingTestTag),
                    contentAlignment = Alignment.Center,
                ) {
                    trailing?.invoke()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
            ) {
                PermissionRowInformation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .withOptionalTestTag(informationTestTag),
                    icon = icon,
                    title = title,
                    supporting = supporting,
                    iconTestTag = iconTestTag,
                    titleTestTag = titleTestTag,
                )
                if (trailing != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .withOptionalTestTag(trailingTestTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        trailing()
                    }
                }
            }
        }
    }
}

private data class PermissionRowTrailingMetrics(
    val statusLabel: String,
    val actionLabel: String?,
)

@Composable
private fun PermissionRowInformation(
    modifier: Modifier,
    icon: Int,
    title: String,
    supporting: String,
    iconTestTag: String?,
    titleTestTag: String?,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
        ) {
            PermissionRowIcon(
                icon = icon,
                modifier = Modifier.withOptionalTestTag(iconTestTag),
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .withOptionalTestTag(titleTestTag),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = supporting,
            modifier = Modifier.padding(start = 18.dp + leafyLayoutTokens.spacingSm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRowIcon(
    icon: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

private fun Modifier.withOptionalTestTag(tag: String?): Modifier =
    if (tag == null) this else testTag(tag)

@Composable
private fun PermissionStatusPill(
    status: NotificationPermissionStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.leafyColors
    val (containerColor, contentColor) = when (status) {
        NotificationPermissionStatus.ENABLED -> Pair(
            colors.upcomingContainer,
            colors.onUpcoming,
        )
        NotificationPermissionStatus.NEEDS_PERMISSION -> Pair(
            colors.dueContainer,
            colors.onDue,
        )
        NotificationPermissionStatus.BLOCKED -> Pair(
            colors.overdueContainer,
            colors.onOverdue,
        )
        NotificationPermissionStatus.UNKNOWN -> Pair(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AboutSection(appVersionName: String) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_about),
        icon = R.drawable.ic_info,
    ) {
        Text(
            text = stringResource(
                R.string.settings_version,
                appVersionName.ifBlank { stringResource(R.string.settings_version_unknown) },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_privacy_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.leafyColors.upcomingContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_leaf),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.leafyColors.onUpcoming,
                )
                Text(
                    text = stringResource(R.string.settings_private_by_design),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.leafyColors.onUpcoming,
                )
            }
        }
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
private const val THEME_SYSTEM = "theme_system"
private const val THEME_LIGHT = "theme_light"
private const val THEME_DARK = "theme_dark"
private const val LANGUAGE_SYSTEM = "language_system"
private const val LANGUAGE_ENGLISH = "language_english"
private const val LANGUAGE_SPANISH = "language_spanish"

private val ThemePreference.segmentKey: String
    get() = when (this) {
        ThemePreference.SYSTEM -> THEME_SYSTEM
        ThemePreference.LIGHT -> THEME_LIGHT
        ThemePreference.DARK -> THEME_DARK
    }

private val AppLanguage.segmentKey: String
    get() = when (this) {
        AppLanguage.SYSTEM -> LANGUAGE_SYSTEM
        AppLanguage.ENGLISH -> LANGUAGE_ENGLISH
        AppLanguage.SPANISH -> LANGUAGE_SPANISH
    }

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
            onBackupClick = {},
            onRetry = {},
            onDismissError = {},
        )
    }
}

@Preview(name = "Blocked EN light compact", widthDp = 320, showBackground = true)
@Preview(
    name = "Blocked EN dark compact",
    widthDp = 320,
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Blocked ES light compact", widthDp = 320, locale = "es", showBackground = true)
@Preview(
    name = "Blocked ES dark compact",
    widthDp = 320,
    locale = "es",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Blocked EN light large text", widthDp = 360, fontScale = 1.5f, showBackground = true)
@Preview(
    name = "Blocked EN dark large text",
    widthDp = 360,
    fontScale = 1.5f,
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Blocked ES light large text",
    widthDp = 360,
    locale = "es",
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(
    name = "Blocked ES dark large text",
    widthDp = 360,
    locale = "es",
    fontScale = 1.5f,
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsBlockedPreview() {
    StayLeafyTheme {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            PermissionSection(
                summary = NotificationPermissionSummary(
                    status = NotificationPermissionStatus.BLOCKED,
                    action = NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
                ),
                onAction = {},
            )
        }
    }
}
