package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme

@Composable
fun PlantEditorRoute(
    plantId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlantEditorViewModel = hiltViewModel(
        creationCallback = { factory: PlantEditorViewModel.Factory -> factory.create(plantId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    PlantEditorScreen(
        state = state,
        onBack = onBack,
        onDisplayNameChange = viewModel::updateDisplayName,
        onCommonNameChange = viewModel::updateCommonName,
        onScientificNameChange = viewModel::updateScientificName,
        onLocationChange = viewModel::updateLocation,
        onAcquiredEpochDayChange = viewModel::updateAcquiredEpochDay,
        onDescriptionChange = viewModel::updateDescription,
        onRetryLoad = viewModel::retryLoad,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantEditorScreen(
    state: PlantEditorUiState,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onCommonNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAcquiredEpochDayChange: (Long?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(
        if (state.mode == PlantEditorMode.Create) R.string.add_plant else R.string.edit_plant,
    )
    val cancelDescription = stringResource(R.string.cancel)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = cancelDescription
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { LoadingContent(modifier = Modifier.padding(top = 64.dp)) }
            state.notFound -> NotFoundEditorContent(
                modifier = Modifier.padding(innerPadding),
                onBack = onBack,
            )
            state.loadError != null -> EditorLoadError(
                modifier = Modifier.padding(innerPadding),
                onRetry = onRetryLoad,
            )
            else -> EditorForm(
                state = state,
                innerPadding = innerPadding,
                showDatePicker = { showDatePicker = true },
                onBack = onBack,
                onDisplayNameChange = onDisplayNameChange,
                onCommonNameChange = onCommonNameChange,
                onScientificNameChange = onScientificNameChange,
                onLocationChange = onLocationChange,
                onAcquiredEpochDayChange = onAcquiredEpochDayChange,
                onDescriptionChange = onDescriptionChange,
                onSave = onSave,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = epochDayToPickerMillis(state.form.acquiredEpochDay),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            onAcquiredEpochDayChange(pickerMillisToEpochDay(it))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.select_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun EditorForm(
    state: PlantEditorUiState,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    showDatePicker: () -> Unit,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onCommonNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAcquiredEpochDayChange: (Long?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val acquisitionDateDescription = stringResource(R.string.acquisition_date)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.saveError?.let {
            Text(
                text = stringResource(R.string.save_plant_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = state.form.displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.display_name)) },
            supportingText = {
                if (state.displayNameError) {
                    Text(stringResource(R.string.display_name_required))
                }
            },
            isError = state.displayNameError,
            singleLine = true,
        )
        EditorTextField(
            value = state.form.commonName,
            onValueChange = onCommonNameChange,
            label = R.string.common_name,
        )
        EditorTextField(
            value = state.form.scientificName,
            onValueChange = onScientificNameChange,
            label = R.string.scientific_name,
        )
        EditorTextField(
            value = state.form.location,
            onValueChange = onLocationChange,
            label = R.string.location,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.form.acquiredEpochDay?.let(::formatEpochDay).orEmpty(),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.acquisition_date)) },
                placeholder = { Text(stringResource(R.string.select_date_hint)) },
                readOnly = true,
                enabled = !state.isSaving,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .semantics { contentDescription = acquisitionDateDescription }
                    .clickable(enabled = !state.isSaving, onClick = showDatePicker),
            )
        }
        if (state.form.acquiredEpochDay != null) {
            TextButton(
                onClick = { onAcquiredEpochDayChange(null) },
                enabled = !state.isSaving,
            ) {
                Text(stringResource(R.string.clear_date))
            }
        }
        OutlinedTextField(
            value = state.form.description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.description)) },
            minLines = 4,
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
        ) {
            Text(stringResource(if (state.isSaving) R.string.saving else R.string.save))
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
    )
}

@Composable
private fun EditorLoadError(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
    ) {
        Text(stringResource(R.string.load_plant_error))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun NotFoundEditorContent(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
    ) {
        Text(stringResource(R.string.plant_not_found), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.back_to_plants))
        }
    }
}

@Preview(name = "Create form - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Create form - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantEditorCreatePreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantEditorScreen(
            state = PlantEditorUiState(PlantEditorMode.Create, false, PlantEditorForm()),
            onBack = {},
            onDisplayNameChange = {},
            onCommonNameChange = {},
            onScientificNameChange = {},
            onLocationChange = {},
            onAcquiredEpochDayChange = {},
            onDescriptionChange = {},
            onRetryLoad = {},
            onSave = {},
        )
    }
}

@Preview(name = "Edit form - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Edit form - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantEditorEditPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantEditorScreen(
            state = PlantEditorUiState(
                mode = PlantEditorMode.Edit,
                isLoading = false,
                form = PlantEditorForm(displayName = "Ficus"),
                displayNameError = true,
            ),
            onBack = {},
            onDisplayNameChange = {},
            onCommonNameChange = {},
            onScientificNameChange = {},
            onLocationChange = {},
            onAcquiredEpochDayChange = {},
            onDescriptionChange = {},
            onRetryLoad = {},
            onSave = {},
        )
    }
}
