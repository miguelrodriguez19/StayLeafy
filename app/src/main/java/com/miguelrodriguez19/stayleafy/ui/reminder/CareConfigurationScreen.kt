package com.miguelrodriguez19.stayleafy.ui.reminder

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.CareScheduleMode
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.plant.formatEpochDay
import com.miguelrodriguez19.stayleafy.ui.plant.initialAcquisitionDatePickerMillis
import com.miguelrodriguez19.stayleafy.ui.plant.pickerMillisToEpochDay
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CareConfigurationRoute(
    plantId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val viewModel: CareConfigurationViewModel = hiltViewModel(
        creationCallback = { factory: CareConfigurationViewModel.Factory -> factory.create(plantId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if ((state as? CareConfigurationUiState.Content)?.saved == true) onSaved()
    }

    CareConfigurationScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onEnabledChange = viewModel::setEnabled,
        onModeChange = viewModel::setMode,
        onIntervalChange = viewModel::setInterval,
        onSourceChange = viewModel::setSource,
        onDateChange = viewModel::setDate,
        onSeasonStartChange = viewModel::setSeasonStart,
        onSeasonEndChange = viewModel::setSeasonEnd,
        onSave = viewModel::save,
        onPermissionEducationContinue = {
            onRequestNotificationPermission()
            viewModel.completePermissionEducation()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareConfigurationScreen(
    state: CareConfigurationUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEnabledChange: (CareType, Boolean) -> Unit,
    onIntervalChange: (CareType, String) -> Unit,
    onSourceChange: (CareType, ReminderDueSourceChoice) -> Unit,
    onDateChange: (CareType, Long) -> Unit,
    onSave: () -> Unit,
    onPermissionEducationContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onModeChange: (CareType, CareScheduleMode) -> Unit = { _, _ -> },
    onSeasonStartChange: (CareType, AnnualDate) -> Unit = { _, _ -> },
    onSeasonEndChange: (CareType, AnnualDate?) -> Unit = { _, _ -> },
) {
    val content = state as? CareConfigurationUiState.Content
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.configure_care)) },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = content?.isSaving != true) {
                        Text(stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (state) {
            CareConfigurationUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            CareConfigurationUiState.Error -> ConfigurationMessage(
                message = stringResource(R.string.load_care_configuration_error),
                action = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            CareConfigurationUiState.NotFound -> ConfigurationMessage(
                message = stringResource(R.string.plant_not_found),
                action = stringResource(R.string.back_to_plants),
                onAction = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            is CareConfigurationUiState.Content -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.plantName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.care_configuration_help),
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.forms.forEach { form ->
                    CareRuleCard(
                        form = form,
                        latestCareEpochDay = state.latestCareEpochDays[form.careType],
                        enabled = !state.isSaving,
                        onEnabledChange = { onEnabledChange(form.careType, it) },
                        onModeChange = { onModeChange(form.careType, it) },
                        onIntervalChange = { onIntervalChange(form.careType, it) },
                        onSourceChange = { onSourceChange(form.careType, it) },
                        onDateChange = { onDateChange(form.careType, it) },
                        onSeasonStartChange = { onSeasonStartChange(form.careType, it) },
                        onSeasonEndChange = { onSeasonEndChange(form.careType, it) },
                    )
                }
                if (state.saveError) {
                    Text(
                        stringResource(R.string.save_care_configuration_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (state.isSaving) R.string.saving else R.string.save))
                }
            }
        }
    }

    if (content?.showPermissionEducation == true) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_message)) },
            confirmButton = {
                Button(onClick = onPermissionEducationContinue) {
                    Text(stringResource(R.string.continue_action))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareRuleCard(
    form: CareRuleForm,
    latestCareEpochDay: Long?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (CareScheduleMode) -> Unit,
    onIntervalChange: (String) -> Unit,
    onSourceChange: (ReminderDueSourceChoice) -> Unit,
    onDateChange: (Long) -> Unit,
    onSeasonStartChange: (AnnualDate) -> Unit,
    onSeasonEndChange: (AnnualDate?) -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var seasonPicker by rememberSaveable { mutableStateOf<SeasonDateTarget?>(null) }
    val selectMode = { mode: CareScheduleMode ->
        onEnabledChange(mode != CareScheduleMode.DISABLED)
        onModeChange(mode)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(form.careType.localizedName(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CareScheduleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = form.mode == mode,
                        onClick = { selectMode(mode) },
                        enabled = enabled,
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        CareScheduleMode.DISABLED -> R.string.schedule_disabled
                                        CareScheduleMode.INTERVAL -> R.string.schedule_interval
                                        CareScheduleMode.SEASONAL -> R.string.schedule_seasonal
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            if (form.mode == CareScheduleMode.INTERVAL) {
                OutlinedTextField(
                    value = form.intervalDays,
                    onValueChange = onIntervalChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.interval_days)) },
                    supportingText = if (form.intervalError) {
                        { Text(stringResource(R.string.interval_days_error)) }
                    } else null,
                    isError = form.intervalError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(R.string.calculate_next_due_from), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.dueSource == ReminderDueSourceChoice.LatestCare,
                        onClick = { onSourceChange(ReminderDueSourceChoice.LatestCare) },
                        enabled = enabled && latestCareEpochDay != null,
                        label = { Text(stringResource(R.string.latest_care)) },
                    )
                    FilterChip(
                        selected = form.dueSource == ReminderDueSourceChoice.Date,
                        onClick = { onSourceChange(ReminderDueSourceChoice.Date) },
                        enabled = enabled,
                        label = { Text(stringResource(R.string.choose_due_date)) },
                    )
                }
                when (form.dueSource) {
                    ReminderDueSourceChoice.LatestCare -> Text(
                        text = latestCareEpochDay?.let {
                            stringResource(R.string.latest_care_date, formatEpochDay(it))
                        } ?: stringResource(R.string.no_latest_care),
                        color = if (form.sourceError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    ReminderDueSourceChoice.Date -> {
                        val dateLabel = form.selectedEpochDay?.let(::formatEpochDay)
                            ?: stringResource(R.string.select_due_date)
                        Card(
                            Modifier.fillMaxWidth().clickable(enabled = enabled) { showDatePicker = true },
                        ) {
                            Text(dateLabel, Modifier.padding(16.dp))
                        }
                        if (form.sourceError) {
                            Text(
                                stringResource(R.string.due_date_required),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    null -> Text(
                        text = stringResource(R.string.select_due_source),
                        color = if (form.sourceError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (form.mode == CareScheduleMode.SEASONAL) {
                Text(stringResource(R.string.seasonal_schedule_help), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.season_start), style = MaterialTheme.typography.labelLarge)
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        seasonPicker = SeasonDateTarget.Start
                    },
                ) {
                    Text(
                        if (form.seasonStart != null) {
                            formatAnnualDate(form.seasonStart)
                        } else {
                            stringResource(R.string.select_season_start)
                        },
                        Modifier.padding(16.dp),
                        color = if (form.seasonStartError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (form.seasonStartError) {
                    Text(stringResource(R.string.season_start_required), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.season_end_optional), style = MaterialTheme.typography.labelLarge)
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        seasonPicker = SeasonDateTarget.End
                    },
                ) {
                    Text(
                        if (form.seasonEnd != null) {
                            formatAnnualDate(form.seasonEnd)
                        } else {
                            stringResource(R.string.select_season_end)
                        },
                        Modifier.padding(16.dp),
                    )
                }
                if (form.seasonEnd != null) {
                    TextButton(onClick = { onSeasonEndChange(null) }, enabled = enabled) {
                        Text(stringResource(R.string.clear_season_end))
                    }
                }
                if (form.seasonStart != null && form.seasonEnd != null &&
                    annualDateComesBefore(form.seasonEnd, form.seasonStart)
                ) {
                    Text(
                        stringResource(R.string.season_crosses_year),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = initialAcquisitionDatePickerMillis(form.selectedEpochDay)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(pickerMillisToEpochDay(it)) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
    seasonPicker?.let { target ->
        val selected = if (target == SeasonDateTarget.Start) form.seasonStart else form.seasonEnd
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialAcquisitionDatePickerMillis(
                selected?.atYear(ANNUAL_DATE_REFERENCE_YEAR)?.toEpochDay(),
            ),
        )
        DatePickerDialog(
            onDismissRequest = { seasonPicker = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(::pickerMillisToEpochDay)?.let(LocalDate::ofEpochDay)
                        ?.let { date -> AnnualDate(date.monthValue, date.dayOfMonth) }
                        ?.let { annualDate ->
                            if (target == SeasonDateTarget.Start) {
                                onSeasonStartChange(annualDate)
                            } else {
                                onSeasonEndChange(annualDate)
                            }
                        }
                    seasonPicker = null
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = {
                TextButton(onClick = { seasonPicker = null }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

private enum class SeasonDateTarget { Start, End }

@Composable
private fun formatAnnualDate(date: AnnualDate): String {
    val pattern = stringResource(R.string.annual_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    return date.atYear(ANNUAL_DATE_REFERENCE_YEAR)
        .format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun annualDateComesBefore(left: AnnualDate, right: AnnualDate): Boolean =
    left.month < right.month || left.month == right.month && left.day < right.day

private const val ANNUAL_DATE_REFERENCE_YEAR = 2000

@Composable
private fun ConfigurationMessage(
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}

@Composable
internal fun CareType.localizedName(): String = when (this) {
    CareType.WATERING -> stringResource(R.string.care_watering)
    CareType.PRUNING -> stringResource(R.string.care_pruning)
    CareType.FERTILIZING -> stringResource(R.string.care_fertilizing)
}

@Preview(name = "Care configuration - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Care configuration - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CareConfigurationPreview() {
    StayLeafyTheme(dynamicColor = false) {
        CareConfigurationScreen(
            state = CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = listOf(
                    CareRuleForm(CareType.WATERING, true, "7", ReminderDueSourceChoice.LatestCare),
                    CareRuleForm(
                        careType = CareType.PRUNING,
                        enabled = true,
                        mode = CareScheduleMode.SEASONAL,
                        seasonStart = AnnualDate(11, 15),
                        seasonEnd = AnnualDate(2, 10),
                    ),
                    CareRuleForm(CareType.FERTILIZING, true, "30", ReminderDueSourceChoice.Date, 21_000),
                ),
                latestCareEpochDays = mapOf(CareType.WATERING to 20_990),
            ),
            onBack = {}, onRetry = {}, onEnabledChange = { _, _ -> }, onIntervalChange = { _, _ -> },
            onSourceChange = { _, _ -> }, onDateChange = { _, _ -> }, onSave = {},
            onPermissionEducationContinue = {},
        )
    }
}

@Preview(name = "Care configuration error - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Care configuration error - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CareConfigurationErrorPreview() {
    StayLeafyTheme(dynamicColor = false) {
        CareConfigurationScreen(
            state = CareConfigurationUiState.Error,
            onBack = {}, onRetry = {}, onEnabledChange = { _, _ -> }, onIntervalChange = { _, _ -> },
            onSourceChange = { _, _ -> }, onDateChange = { _, _ -> }, onSave = {},
            onPermissionEducationContinue = {},
        )
    }
}

@Preview(name = "Care configuration not found - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Care configuration not found - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CareConfigurationNotFoundPreview() {
    StayLeafyTheme(dynamicColor = false) {
        CareConfigurationScreen(
            state = CareConfigurationUiState.NotFound,
            onBack = {}, onRetry = {}, onEnabledChange = { _, _ -> }, onIntervalChange = { _, _ -> },
            onSourceChange = { _, _ -> }, onDateChange = { _, _ -> }, onSave = {},
            onPermissionEducationContinue = {},
        )
    }
}
