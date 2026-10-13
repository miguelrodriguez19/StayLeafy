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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.miguelrodriguez19.stayleafy.ui.plant.PlantPhotoBox
import com.miguelrodriguez19.stayleafy.ui.components.LeafyCareIcon
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.time.LocalDate
import java.time.Year
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
                title = {
                    Text(
                        stringResource(R.string.configure_care),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = content?.isSaving != true) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (content != null) {
                Button(
                    onClick = onSave,
                    enabled = !content.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(if (content.isSaving) R.string.saving else R.string.save))
                }
            }
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
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PlantPhotoBox(
                            relativePath = state.plantPhotoPath,
                            modifier = Modifier.size(56.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(text = state.plantName, style = MaterialTheme.typography.titleMedium)
                            state.plantScientificName?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.care_configuration_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var showAnnualPicker by rememberSaveable { mutableStateOf(false) }
    val selectMode = { mode: CareScheduleMode ->
        onEnabledChange(mode != CareScheduleMode.DISABLED)
        onModeChange(mode)
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LeafyCareIcon(
                    careType = form.careType,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(form.careType.localizedName(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CareScheduleMode.entries.forEach { mode ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
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
                IntervalStepper(
                    value = form.intervalDays,
                    enabled = enabled,
                    isError = form.intervalError,
                    onValueChange = onIntervalChange,
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
                val annualSelection = when {
                    form.seasonStart == null -> ""
                    form.seasonEnd == null -> formatAnnualDate(form.seasonStart)
                    else -> stringResource(
                        R.string.annual_selection_range,
                        formatAnnualDate(form.seasonStart),
                        formatAnnualDate(form.seasonEnd),
                    )
                }
                val annualFieldDescription = stringResource(
                    R.string.annual_field_accessibility,
                    annualSelection.ifEmpty { stringResource(R.string.select_annual_timing) },
                )
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = annualSelection,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {},
                        enabled = enabled,
                        readOnly = true,
                        singleLine = true,
                        isError = form.seasonStartError,
                        label = { Text(stringResource(R.string.annual_care_timing)) },
                        placeholder = { Text(stringResource(R.string.select_annual_timing)) },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                            )
                        },
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(enabled = enabled) { showAnnualPicker = true }
                            .clearAndSetSemantics {
                                contentDescription = annualFieldDescription
                                role = Role.Button
                                onClick {
                                    if (enabled) showAnnualPicker = true
                                    enabled
                                }
                            },
                    )
                }
                if (form.seasonStartError) {
                    Text(stringResource(R.string.annual_selection_required), color = MaterialTheme.colorScheme.error)
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
    if (showAnnualPicker) {
        val referenceYear = annualSelectionReferenceYear(form.seasonStart)
        val initialStartMillis = annualDatePickerMillis(form.seasonStart, referenceYear)
        val endYear = if (
            form.seasonStart != null && form.seasonEnd != null &&
            annualDateComesBefore(form.seasonEnd, form.seasonStart)
        ) {
            referenceYear + 1
        } else {
            referenceYear
        }
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialStartMillis,
            initialSelectedEndDateMillis = annualDatePickerMillis(form.seasonEnd, endYear),
        )
        DatePickerDialog(
            onDismissRequest = { showAnnualPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.toAnnualDate()
                            ?: return@TextButton
                        onSeasonStartChange(start)
                        onSeasonEndChange(pickerState.selectedEndDateMillis?.toAnnualDate())
                        showAnnualPicker = false
                    },
                    enabled = pickerState.selectedStartDateMillis != null,
                ) { Text(stringResource(R.string.confirm_annual_selection)) }
            },
            dismissButton = {
                TextButton(onClick = { showAnnualPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DateRangePicker(
                state = pickerState,
                showModeToggle = false,
                title = {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                        Text(
                            stringResource(R.string.annual_picker_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(R.string.annual_picker_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun IntervalStepper(
    value: String,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val parsedValue = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val decreaseDescription = stringResource(R.string.decrease_interval)
    val increaseDescription = stringResource(R.string.increase_interval)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.interval_every_days, parsedValue, parsedValue),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onValueChange((parsedValue - 1).coerceAtLeast(1).toString()) },
                        enabled = enabled && parsedValue > 1,
                        modifier = Modifier.semantics {
                            contentDescription = decreaseDescription
                        },
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    Text(
                        parsedValue.toString(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = {
                            if (parsedValue < Int.MAX_VALUE) onValueChange((parsedValue + 1).toString())
                        },
                        enabled = enabled && parsedValue < Int.MAX_VALUE,
                        modifier = Modifier.semantics {
                            contentDescription = increaseDescription
                        },
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
        if (isError) {
            Text(
                stringResource(R.string.interval_days_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun formatAnnualDate(date: AnnualDate): String {
    val pattern = stringResource(R.string.annual_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    return date.atYear(ANNUAL_DATE_REFERENCE_YEAR)
        .format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun annualDateComesBefore(left: AnnualDate, right: AnnualDate): Boolean =
    left.month < right.month || left.month == right.month && left.day < right.day

private fun annualSelectionReferenceYear(start: AnnualDate?): Int {
    val currentYear = LocalDate.now().year
    if (start?.month != 2 || start.day != 29 || Year.isLeap(currentYear.toLong())) return currentYear
    return generateSequence(currentYear + 1) { it + 1 }.first { Year.isLeap(it.toLong()) }
}

private fun annualDatePickerMillis(date: AnnualDate?, year: Int): Long? =
    date?.atYear(year)?.toEpochDay()?.let(::initialAcquisitionDatePickerMillis)

private fun Long.toAnnualDate(): AnnualDate =
    LocalDate.ofEpochDay(pickerMillisToEpochDay(this)).let { AnnualDate(it.monthValue, it.dayOfMonth) }

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
                plantScientificName = "Monstera deliciosa",
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
