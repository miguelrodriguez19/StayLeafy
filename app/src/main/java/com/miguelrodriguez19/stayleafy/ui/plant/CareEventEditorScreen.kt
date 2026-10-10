package com.miguelrodriguez19.stayleafy.ui.plant

import android.text.format.DateFormat
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareEventEditorRoute(
    plantId: String,
    careType: CareType?,
    eventId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CareEventEditorViewModel = hiltViewModel(
        creationCallback = { factory: CareEventEditorViewModel.Factory ->
            factory.create(plantId, careType, eventId)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    CareEventEditorScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retryLoad,
        onCareTypeChange = viewModel::updateCareType,
        onPerformedAtChange = viewModel::updatePerformedAt,
        onNoteChange = viewModel::updateNote,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareEventEditorScreen(
    state: CareEventEditorState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCareTypeChange: (CareType) -> Unit,
    onPerformedAtChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(if (state.eventId == null) R.string.add_care_event else R.string.edit_care_event)
    val cancelLabel = stringResource(R.string.cancel)
    val saveLabel = stringResource(R.string.save)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = cancelLabel
                        },
                    ) { Text(cancelLabel) }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = !state.isLoading && !state.isSaving && !state.notFound,
                        modifier = Modifier.semantics { contentDescription = saveLabel },
                    ) { Text(stringResource(if (state.isSaving) R.string.saving else R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> Text(stringResource(R.string.loading))
                state.notFound -> NotFoundContent(onBack, R.string.care_event_not_found)
                state.loadError != null -> {
                    Text(stringResource(R.string.load_care_error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                else -> {
                    Text(stringResource(R.string.care_type), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CareType.values().forEach { type ->
                            FilterChip(
                                selected = state.careType == type,
                                onClick = { onCareTypeChange(type) },
                                label = { Text(type.label()) },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) {
                        Text(stringResource(R.string.care_date_value, formatEpochDay(careEpochDay(state.performedAt))))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) {
                        Text(stringResource(R.string.care_time_value, formatCareTime(state.performedAt)))
                    }
                    if (state.futureError) {
                        Text(stringResource(R.string.care_date_future_error), color = MaterialTheme.colorScheme.error)
                    }
                    state.saveError?.let { error ->
                        if (error != UiErrorCategory.CareFuture) {
                            Text(stringResource(R.string.save_care_error), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_note)) },
                        minLines = 4,
                        enabled = !state.isSaving,
                    )
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) { Text(stringResource(if (state.isSaving) R.string.saving else R.string.save)) }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = careEpochDay(state.performedAt).let { epochDayToPickerMillis(it) },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        val date = pickerMillisToEpochDay(selected)
                        val current = Instant.ofEpochMilli(state.performedAt).atZone(ZoneId.systemDefault())
                        onPerformedAtChange(
                            LocalDate.ofEpochDay(date).atTime(current.toLocalTime()).atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli(),
                        )
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }
    if (showTimePicker) {
        val local = Instant.ofEpochMilli(state.performedAt).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            local.hour,
            local.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.care_time)) },
            text = { TimeInput(state = timePickerState) },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    onPerformedAtChange(
                        local.toLocalDate().atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    )
                    showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
        )
    }
}

@Composable
private fun CareType.label(): String = when (this) {
    CareType.WATERING -> stringResource(R.string.care_watering)
    CareType.PRUNING -> stringResource(R.string.care_pruning)
    CareType.FERTILIZING -> stringResource(R.string.care_fertilizing)
}

private fun careEpochDay(epochMillis: Long): Long =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

private fun formatCareTime(epochMillis: Long): String =
    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .withLocale(java.util.Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@Composable
private fun NotFoundContent(onBack: () -> Unit, message: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(message), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.back_to_plant)) }
    }
}

@Preview(name = "Care editor - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Care editor - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CareEventEditorPreview() {
    StayLeafyTheme(dynamicColor = false) {
        CareEventEditorScreen(
            state = CareEventEditorState(
                eventId = null,
                performedAt = 1_735_689_600_000,
            ),
            onBack = {}, onRetry = {}, onCareTypeChange = {}, onPerformedAtChange = {}, onNoteChange = {}, onSave = {},
        )
    }
}
