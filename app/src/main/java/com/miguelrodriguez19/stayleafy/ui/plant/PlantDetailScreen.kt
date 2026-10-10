package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailRoute(
    plantId: String?,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddNote: () -> Unit = {},
    onEditNote: (String) -> Unit = {},
    onAddCare: (CareType) -> Unit = {},
    onEditCare: (String) -> Unit = {},
) {
    val viewModel: PlantDetailViewModel = hiltViewModel(
        creationCallback = { factory: PlantDetailViewModel.Factory -> factory.create(plantId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val photoState by viewModel.photoUiState.collectAsStateWithLifecycle()
    val pendingCaptureToken by viewModel.pendingCaptureToken.collectAsStateWithLifecycle()
    val stagedPhotoFile = (state as? PlantDetailUiState.Content)
        ?.stagedPhoto
        ?.let { staged -> viewModel.resolveStagedPhoto(staged.token) }

    var notesState: PlantNotesUiState = PlantNotesUiState.Empty
    var notesMutationError: UiErrorCategory? = null
    var notesMutating = false
    var retryNotes: () -> Unit = {}
    var deleteNote: (String) -> Unit = {}
    var careState: CareEventsUiState = CareEventsUiState.Empty
    var careMutationError: UiErrorCategory? = null
    var careMutating = false
    var retryCare: () -> Unit = {}
    var deleteCare: (String) -> Unit = {}

    if (plantId != null) {
        val notesViewModel: PlantNotesViewModel = hiltViewModel(
            creationCallback = { factory: PlantNotesViewModel.Factory -> factory.create(plantId) },
        )
        val observedNotes by notesViewModel.uiState.collectAsStateWithLifecycle()
        val observedNotesError by notesViewModel.mutationError.collectAsStateWithLifecycle()
        val observedNotesMutating by notesViewModel.isMutating.collectAsStateWithLifecycle()
        notesState = observedNotes
        notesMutationError = observedNotesError
        notesMutating = observedNotesMutating
        retryNotes = notesViewModel::retry
        deleteNote = notesViewModel::delete
        val careViewModel: PlantCareViewModel = hiltViewModel(
            creationCallback = { factory: PlantCareViewModel.Factory -> factory.create(plantId) },
        )
        val observedCare by careViewModel.uiState.collectAsStateWithLifecycle()
        val observedCareError by careViewModel.mutationError.collectAsStateWithLifecycle()
        val observedCareMutating by careViewModel.isMutating.collectAsStateWithLifecycle()
        careState = observedCare
        careMutationError = observedCareError
        careMutating = observedCareMutating
        retryCare = careViewModel::retry
        deleteCare = careViewModel::delete
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCameraResult(success)
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPickedPhoto)
    }

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }
    LaunchedEffect(state) {
        val savedId = (state as? PlantDetailUiState.Content)?.savedPlantId
        if (savedId != null) onCreated(savedId)
    }

    PlantDetailScreen(
        state = state,
        notesState = notesState,
        notesMutationError = notesMutationError,
        notesMutating = notesMutating,
        photoMutating = photoState.isMutating || pendingCaptureToken != null,
        photoMutationError = photoState.error,
        photoCleanupRetryEnabled = pendingCaptureToken != null && !photoState.isMutating,
        mutationError = mutationError,
        isDeleting = isDeleting,
        onBack = onBack,
        onRetry = viewModel::retry,
        onBeginEdit = viewModel::beginEdit,
        onCancelEdit = viewModel::cancelEdit,
        onNameChange = viewModel::updateName,
        onScientificNameChange = viewModel::updateScientificName,
        onLocationChange = viewModel::updateLocation,
        onAcquiredEpochDayChange = viewModel::updateAcquiredEpochDay,
        onDescriptionChange = viewModel::updateDescription,
        onFavoriteClick = viewModel::setFavorite,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onAddNote = onAddNote,
        onEditNote = onEditNote,
        onDeleteNote = deleteNote,
        onRetryNotes = retryNotes,
        careState = careState,
        careMutationError = careMutationError,
        careMutating = careMutating,
        onRetryCare = retryCare,
        onDeleteCare = deleteCare,
        onAddCare = onAddCare,
        onEditCare = onEditCare,
        onAddStagedNote = viewModel::addStagedNote,
        onUpdateStagedNote = viewModel::updateStagedNote,
        onRemoveStagedNote = viewModel::removeStagedNote,
        onTakePhoto = {
            viewModel.prepareCameraCapture()?.let { capture ->
                runCatching { cameraLauncher.launch(capture.contentUri) }
                    .onFailure { viewModel.onCameraLaunchFailed() }
            }
        },
        onPickPhoto = {
            runCatching {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }.onFailure { viewModel.onPickerLaunchFailed() }
        },
        onRemoveStagedPhoto = viewModel::removeStagedPhoto,
        onRemovePhoto = viewModel::removePhoto,
        onRetryPhotoCleanup = viewModel::retryPendingCaptureCleanup,
        stagedPhotoFile = stagedPhotoFile,
        onDiscardCreation = { viewModel.discardCreation(onBack) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    state: PlantDetailUiState,
    notesState: PlantNotesUiState,
    notesMutationError: UiErrorCategory?,
    notesMutating: Boolean,
    photoMutating: Boolean,
    photoMutationError: UiErrorCategory?,
    photoCleanupRetryEnabled: Boolean,
    mutationError: UiErrorCategory?,
    isDeleting: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onBeginEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAcquiredEpochDayChange: (Long?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetryNotes: () -> Unit,
    careState: CareEventsUiState = CareEventsUiState.Empty,
    careMutationError: UiErrorCategory? = null,
    careMutating: Boolean = false,
    onRetryCare: () -> Unit = {},
    onDeleteCare: (String) -> Unit = {},
    onAddCare: (CareType) -> Unit = {},
    onEditCare: (String) -> Unit = {},
    onAddStagedNote: (String) -> Unit,
    onUpdateStagedNote: (String, String) -> Unit,
    onRemoveStagedNote: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemoveStagedPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryPhotoCleanup: () -> Unit,
    stagedPhotoFile: File? = null,
    onDiscardCreation: () -> Unit = onBack,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val content = state as? PlantDetailUiState.Content
    val title = when {
        content?.mode == PlantDetailMode.Create -> stringResource(R.string.add_plant)
        content?.mode == PlantDetailMode.Edit -> stringResource(R.string.edit_plant)
        content?.plant != null -> content.plant.name
        else -> stringResource(R.string.plant_details)
    }
    val navigationLabel = stringResource(
        if (content?.mode == PlantDetailMode.View) R.string.back else R.string.cancel,
    )
    val saveLabel = stringResource(R.string.save)
    val requestBack = {
        when {
            content?.mode == PlantDetailMode.Edit -> onCancelEdit()
            content?.mode == PlantDetailMode.Create && content.hasUnsavedCreation -> {
                showDiscardDialog = true
            }
            else -> onBack()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(
                        onClick = requestBack,
                        modifier = Modifier.semantics { contentDescription = navigationLabel },
                    ) { Text(navigationLabel) }
                },
                actions = {
                    if (content?.mode == PlantDetailMode.Create || content?.mode == PlantDetailMode.Edit) {
                        TextButton(
                            onClick = onSave,
                            enabled = !content.isSaving,
                            modifier = Modifier.semantics { contentDescription = saveLabel },
                        ) {
                            Text(stringResource(if (content.isSaving) R.string.saving else R.string.save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                PlantDetailUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))
                is PlantDetailUiState.Error -> DetailError(onRetry)
                PlantDetailUiState.NotFound -> NotFoundContent(onBack)
                is PlantDetailUiState.Content -> PlantDetailContent(
                    state = state,
                    notesState = notesState,
                    notesMutationError = notesMutationError,
                    notesMutating = notesMutating,
                    photoMutating = photoMutating,
                    photoMutationError = photoMutationError,
                    photoCleanupRetryEnabled = photoCleanupRetryEnabled,
                    mutationError = mutationError,
                    isDeleting = isDeleting,
                    onBeginEdit = onBeginEdit,
                    onCancelEdit = onCancelEdit,
                    onNameChange = onNameChange,
                    onScientificNameChange = onScientificNameChange,
                    onLocationChange = onLocationChange,
                    onAcquiredEpochDayChange = onAcquiredEpochDayChange,
                    onDescriptionChange = onDescriptionChange,
                    onFavoriteClick = onFavoriteClick,
                    onSave = onSave,
                    onDeleteRequest = { showDeleteDialog = true },
                    onAddNote = onAddNote,
                    onEditNote = onEditNote,
                    onDeleteNote = onDeleteNote,
                    onRetryNotes = onRetryNotes,
                    careState = careState,
                    careMutationError = careMutationError,
                    careMutating = careMutating,
                    onRetryCare = onRetryCare,
                    onDeleteCare = onDeleteCare,
                    onAddCare = onAddCare,
                    onEditCare = onEditCare,
                    onAddStagedNote = onAddStagedNote,
                    onUpdateStagedNote = onUpdateStagedNote,
                    onRemoveStagedNote = onRemoveStagedNote,
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onReplacePhotoRequest = { showPhotoSourceDialog = true },
                    onRemoveStagedPhoto = onRemoveStagedPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onRetryPhotoCleanup = onRetryPhotoCleanup,
                    stagedPhotoFile = stagedPhotoFile,
                    onCancel = requestBack,
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeletePlantDialog(
            isDeleting = isDeleting,
            onDismiss = { if (!isDeleting) showDeleteDialog = false },
            onConfirm = onDelete,
        )
    }
    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            isBusy = photoMutating,
            onDismiss = { if (!photoMutating) showPhotoSourceDialog = false },
            onTakePhoto = { showPhotoSourceDialog = false; onTakePhoto() },
            onPickPhoto = { showPhotoSourceDialog = false; onPickPhoto() },
        )
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_plant_title)) },
            text = { Text(stringResource(R.string.discard_plant_message)) },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onDiscardCreation()
                    },
                ) { Text(stringResource(R.string.discard)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlantDetailContent(
    state: PlantDetailUiState.Content,
    notesState: PlantNotesUiState,
    notesMutationError: UiErrorCategory?,
    notesMutating: Boolean,
    photoMutating: Boolean,
    photoMutationError: UiErrorCategory?,
    photoCleanupRetryEnabled: Boolean,
    mutationError: UiErrorCategory?,
    isDeleting: Boolean,
    onBeginEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAcquiredEpochDayChange: (Long?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetryNotes: () -> Unit,
    careState: CareEventsUiState,
    careMutationError: UiErrorCategory?,
    careMutating: Boolean,
    onRetryCare: () -> Unit,
    onDeleteCare: (String) -> Unit,
    onAddCare: (CareType) -> Unit,
    onEditCare: (String) -> Unit,
    onAddStagedNote: (String) -> Unit,
    onUpdateStagedNote: (String, String) -> Unit,
    onRemoveStagedNote: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onReplacePhotoRequest: () -> Unit,
    onRemoveStagedPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryPhotoCleanup: () -> Unit,
    stagedPhotoFile: File?,
    onCancel: () -> Unit,
) {
    var showPhotoDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showNoteDialog by rememberSaveable { mutableStateOf(false) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var noteError by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingNoteDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCareDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = state.mode != PlantDetailMode.View
    val plant = state.plant
    val favorite = if (state.mode == PlantDetailMode.Create) state.stagedFavorite else plant?.isFavorite == true

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlantPhotoBox(
            relativePath = plant?.mainPhotoPath,
            stagedFile = stagedPhotoFile,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        if (plant?.mainPhotoPath == null && state.stagedPhoto == null) {
            PhotoActions(onTakePhoto, onPickPhoto, enabled = !photoMutating && !state.isSaving)
        } else if (state.mode == PlantDetailMode.Create) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReplacePhotoRequest, enabled = !photoMutating && !state.isSaving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.replace_photo)) }
                OutlinedButton(onClick = onRemoveStagedPhoto, enabled = !photoMutating && !state.isSaving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.remove_photo)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReplacePhotoRequest, enabled = !photoMutating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.replace_photo)) }
                OutlinedButton(onClick = { showPhotoDeleteDialog = true }, enabled = !photoMutating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.remove_photo)) }
            }
        }
        photoMutationError?.let { error ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (error == UiErrorCategory.PhotoDelete) R.string.photo_delete_error else R.string.photo_import_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                if (photoCleanupRetryEnabled) TextButton(onClick = onRetryPhotoCleanup) { Text(stringResource(R.string.retry)) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onFavoriteClick(!favorite) }, enabled = !state.isSaving, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite))
            }
            if (state.mode == PlantDetailMode.View) {
                OutlinedButton(onClick = onBeginEdit, enabled = !isDeleting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.edit_plant)) }
            } else {
                OutlinedButton(onClick = onCancel, enabled = !state.isSaving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
            }
        }
        mutationError?.let { error ->
            Text(stringResource(if (error == UiErrorCategory.Delete) R.string.delete_plant_error else R.string.favorite_update_error), color = MaterialTheme.colorScheme.error)
        }
        state.saveError?.let { Text(stringResource(R.string.save_plant_error), color = MaterialTheme.colorScheme.error) }

        if (editable) {
            PlantFormFields(state, showDatePicker, onNameChange, onScientificNameChange, onLocationChange, onAcquiredEpochDayChange, onDescriptionChange) { showDatePicker = true }
            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (state.isSaving) R.string.saving else R.string.save)) }
        } else if (plant != null) {
            DetailField(R.string.plant_name, plant.name)
            plant.scientificName?.let { DetailField(R.string.scientific_name, it) }
            plant.location?.let { DetailField(R.string.location, it) }
            plant.acquiredEpochDay?.let { DetailField(R.string.acquisition_date, formatEpochDay(it)) }
            plant.description?.let { DetailField(R.string.description, it) }
        }

        if (state.mode == PlantDetailMode.Create) {
            StagedNotesSection(
                notes = state.stagedNotes,
                onAdd = {
                    editingNoteId = null
                    noteDraft = ""
                    noteError = false
                    showNoteDialog = true
                },
                onEdit = { note ->
                    editingNoteId = note.id
                    noteDraft = note.body
                    noteError = false
                    showNoteDialog = true
                },
                onRemove = onRemoveStagedNote,
            )
        } else {
            CareSection(
                state = careState,
                mutationError = careMutationError,
                mutating = careMutating,
                onAddCare = onAddCare,
                onEditCare = onEditCare,
                onDeleteRequest = { pendingCareDeleteId = it },
                onRetry = onRetryCare,
            )
            NotesSection(
                notesState,
                notesMutationError,
                notesMutating,
                onAddNote,
                onEditNote,
                onDeleteRequest = { pendingNoteDeleteId = it },
                onRetry = onRetryNotes,
            )
        }
        if (state.mode == PlantDetailMode.View) {
            OutlinedButton(onClick = onDeleteRequest, modifier = Modifier.fillMaxWidth(), enabled = !isDeleting) { Text(stringResource(R.string.delete_plant)) }
        }
    }

    if (showPhotoDeleteDialog) {
        RemovePhotoDialog(
            isBusy = photoMutating,
            onDismiss = { if (!photoMutating) showPhotoDeleteDialog = false },
            onConfirm = { showPhotoDeleteDialog = false; onRemovePhoto() },
        )
    }
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { if (!notesMutating) showNoteDialog = false },
            title = {
                Text(stringResource(if (editingNoteId == null) R.string.add_note else R.string.edit_note))
            },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it; noteError = false },
                    label = { Text(stringResource(R.string.note_body)) },
                    isError = noteError,
                    supportingText = if (noteError) ({ Text(stringResource(R.string.note_required)) }) else null,
                    minLines = 4,
                )
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Button(onClick = {
                    if (noteDraft.trim().isEmpty()) noteError = true
                    else {
                        editingNoteId?.let { onUpdateStagedNote(it, noteDraft) }
                            ?: onAddStagedNote(noteDraft)
                        showNoteDialog = false
                    }
                }) {
                    Text(stringResource(if (editingNoteId == null) R.string.add_note else R.string.save))
                }
            },
        )
    }
    pendingNoteDeleteId?.let { noteId ->
        AlertDialog(
            onDismissRequest = { if (!notesMutating) pendingNoteDeleteId = null },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            dismissButton = {
                TextButton(
                    onClick = { pendingNoteDeleteId = null },
                    enabled = !notesMutating,
                ) { Text(stringResource(R.string.cancel)) }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNote(noteId)
                        pendingNoteDeleteId = null
                    },
                    enabled = !notesMutating,
                ) { Text(stringResource(R.string.delete_note)) }
            },
        )
    }
    pendingCareDeleteId?.let { eventId ->
        AlertDialog(
            onDismissRequest = { if (!careMutating) pendingCareDeleteId = null },
            title = { Text(stringResource(R.string.delete_care_event_title)) },
            text = { Text(stringResource(R.string.delete_care_event_message)) },
            dismissButton = {
                TextButton(onClick = { pendingCareDeleteId = null }, enabled = !careMutating) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCare(eventId)
                        pendingCareDeleteId = null
                    },
                    enabled = !careMutating,
                ) { Text(stringResource(R.string.delete_care_event)) }
            },
        )
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochDayToPickerMillis(state.form.acquiredEpochDay))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onAcquiredEpochDayChange(pickerMillisToEpochDay(it)) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@Composable
private fun PlantFormFields(
    state: PlantDetailUiState.Content,
    showDatePicker: Boolean,
    onNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAcquiredEpochDayChange: (Long?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    openDatePicker: () -> Unit,
) {
    val acquisitionDateDescription = stringResource(R.string.acquisition_date)
    OutlinedTextField(
        value = state.form.name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.plant_name)) },
        supportingText = if (state.nameError) ({ Text(stringResource(R.string.plant_name_required)) }) else null,
        isError = state.nameError,
        singleLine = true,
    )
    EditorTextField(state.form.scientificName, onScientificNameChange, R.string.scientific_name)
    EditorTextField(state.form.location, onLocationChange, R.string.location)
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.form.acquiredEpochDay?.let(::formatEpochDay).orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.acquisition_date)) },
            placeholder = { Text(stringResource(R.string.select_date_hint)) },
            readOnly = true,
        )
        Box(Modifier.matchParentSize().semantics { contentDescription = acquisitionDateDescription }.clickable(onClick = openDatePicker))
    }
    if (state.form.acquiredEpochDay != null) TextButton(onClick = { onAcquiredEpochDayChange(null) }) { Text(stringResource(R.string.clear_date)) }
    OutlinedTextField(value = state.form.description, onValueChange = onDescriptionChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.description)) }, minLines = 4)
}

@Composable
private fun EditorTextField(value: String, onValueChange: (String) -> Unit, label: Int) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = true)
}

@Composable
private fun PhotoActions(onTakePhoto: () -> Unit, onPickPhoto: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.take_photo)) }
        OutlinedButton(onClick = onPickPhoto, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.choose_photo)) }
    }
}

@Composable
private fun StagedNotesSection(
    notes: List<StagedPlantNote>,
    onAdd: () -> Unit,
    onEdit: (StagedPlantNote) -> Unit,
    onRemove: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text(stringResource(R.string.add_note)) }
            }
            if (notes.isEmpty()) Text(stringResource(R.string.no_notes_yet))
            notes.forEach { note ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(note.body, Modifier.weight(1f))
                        TextButton(onClick = { onEdit(note) }) { Text(stringResource(R.string.edit_note)) }
                        TextButton(onClick = { onRemove(note.id) }) { Text(stringResource(R.string.remove_note)) }
                    }
                }
            }
        }
    }
}

private val PlantDetailUiState.Content.hasUnsavedCreation: Boolean
    get() = form != PlantForm() || stagedFavorite || stagedNotes.isNotEmpty() || stagedPhoto != null

@Composable
private fun CareSection(
    state: CareEventsUiState,
    mutationError: UiErrorCategory?,
    mutating: Boolean,
    onAddCare: (CareType) -> Unit,
    onEditCare: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val events = (state as? CareEventsUiState.Content)?.events.orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.care_history), style = MaterialTheme.typography.titleMedium)
            if (state is CareEventsUiState.Empty || state is CareEventsUiState.Content) {
                CareType.values().forEach { type ->
                    val latest = events.firstOrNull { it.careType == type }
                    CareSummaryRow(
                        type = type,
                        latest = latest,
                        onAdd = { onAddCare(type) },
                        enabled = !mutating,
                    )
                }
            }
            when (state) {
                CareEventsUiState.Loading -> CircularProgressIndicator()
                is CareEventsUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.load_care_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                CareEventsUiState.Empty -> Text(stringResource(R.string.no_care_events))
                is CareEventsUiState.Content -> events.forEach { event ->
                    CareEventRow(event, mutating, onEditCare, onDeleteRequest)
                }
            }
            if (mutationError != null) {
                Text(stringResource(R.string.delete_care_error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CareSummaryRow(
    type: CareType,
    latest: CareEvent?,
    onAdd: () -> Unit,
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(type.summaryLabel(), style = MaterialTheme.typography.labelLarge)
            Text(
                latest?.let { formatTimestamp(it.performedAt) } ?: stringResource(R.string.care_never),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onAdd, enabled = enabled) { Text(type.actionLabel()) }
    }
}

@Composable
private fun CareEventRow(
    event: CareEvent,
    mutating: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.careType.historyLabel(), style = MaterialTheme.typography.titleSmall)
            Text(formatTimestamp(event.performedAt), style = MaterialTheme.typography.bodyMedium)
            event.note?.let { Text(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(event.id) }, enabled = !mutating) { Text(stringResource(R.string.edit_care_event)) }
                TextButton(onClick = { onDelete(event.id) }, enabled = !mutating) { Text(stringResource(R.string.delete_care_event)) }
            }
        }
    }
}

@Composable
private fun CareType.summaryLabel(): String = when (this) {
    CareType.WATERING -> stringResource(R.string.care_last_watering)
    CareType.PRUNING -> stringResource(R.string.care_last_pruning)
    CareType.FERTILIZING -> stringResource(R.string.care_last_fertilizing)
}

@Composable
private fun CareType.actionLabel(): String = when (this) {
    CareType.WATERING -> stringResource(R.string.add_watering)
    CareType.PRUNING -> stringResource(R.string.add_pruning)
    CareType.FERTILIZING -> stringResource(R.string.add_fertilizing)
}

@Composable
private fun CareType.historyLabel(): String = when (this) {
    CareType.WATERING -> stringResource(R.string.care_watering)
    CareType.PRUNING -> stringResource(R.string.care_pruning)
    CareType.FERTILIZING -> stringResource(R.string.care_fertilizing)
}

@Composable
private fun NotesSection(
    state: PlantNotesUiState,
    mutationError: UiErrorCategory?,
    mutating: Boolean,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd, enabled = !mutating) { Text(stringResource(R.string.add_note)) }
            }
            when (state) {
                PlantNotesUiState.Loading -> CircularProgressIndicator()
                is PlantNotesUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.load_notes_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
                PlantNotesUiState.Empty -> Text(stringResource(R.string.no_notes_yet))
                is PlantNotesUiState.Content -> state.notes.forEach { note ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(note.body)
                            Text(formatTimestamp(note.createdAt), style = MaterialTheme.typography.labelSmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onEdit(note.id) }, enabled = !mutating) { Text(stringResource(R.string.edit_note)) }
                                TextButton(onClick = { onDeleteRequest(note.id) }, enabled = !mutating) { Text(stringResource(R.string.delete_note)) }
                            }
                        }
                    }
                }
            }
            mutationError?.let { Text(stringResource(R.string.delete_note_error), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PhotoSourceDialog(isBusy: Boolean, onDismiss: () -> Unit, onTakePhoto: () -> Unit, onPickPhoto: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_photo_source)) },
        text = {},
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(onClick = onTakePhoto, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.take_photo)) }
                OutlinedButton(onClick = onPickPhoto, enabled = !isBusy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.choose_photo)) }
                TextButton(onClick = onDismiss, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun RemovePhotoDialog(isBusy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_photo_title)) },
        text = { Text(stringResource(R.string.remove_photo_message)) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button(onClick = onConfirm, enabled = !isBusy) { Text(stringResource(R.string.remove_photo)) } },
    )
}

@Composable
private fun DetailField(label: Int, value: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge); Text(value, Modifier.padding(top = 4.dp)) } }
}

@Composable
private fun DetailError(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.load_plant_error)); Button(onClick = onRetry, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) } }
}

@Composable
private fun NotFoundContent(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.plant_not_found), style = MaterialTheme.typography.titleMedium); Button(onClick = onBack, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.back_to_plants)) } }
}

@Composable
private fun DeletePlantDialog(isDeleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.delete_plant_title)) }, text = { Text(stringResource(R.string.delete_plant_message)) }, dismissButton = { TextButton(onClick = onDismiss, enabled = !isDeleting) { Text(stringResource(R.string.cancel)) } }, confirmButton = { Button(onClick = onConfirm, enabled = !isDeleting) { Text(stringResource(R.string.delete_plant)) } })
}

@Preview(name = "Create - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Create - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantDetailCreatePreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantDetailScreen(
            state = PlantDetailUiState.Content(PlantDetailMode.Create, null, PlantForm()),
            notesState = PlantNotesUiState.Empty,
            notesMutationError = null,
            notesMutating = false,
            photoMutating = false,
            photoMutationError = null,
            photoCleanupRetryEnabled = false,
            mutationError = null,
            isDeleting = false,
            onBack = {}, onRetry = {}, onBeginEdit = {}, onCancelEdit = {}, onNameChange = {}, onScientificNameChange = {}, onLocationChange = {}, onAcquiredEpochDayChange = {}, onDescriptionChange = {}, onFavoriteClick = {}, onSave = {}, onDelete = {}, onAddNote = {}, onEditNote = {}, onDeleteNote = {}, onRetryNotes = {}, onAddStagedNote = {}, onUpdateStagedNote = { _, _ -> }, onRemoveStagedNote = {}, onTakePhoto = {}, onPickPhoto = {}, onRemoveStagedPhoto = {}, onRemovePhoto = {}, onRetryPhotoCleanup = {},
        )
    }
}

@Preview(name = "Detail - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Detail - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantDetailPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantDetailScreen(
            state = PlantDetailUiState.Content(PlantDetailMode.View, previewPlants.first(), previewPlants.first().toPlantForm()),
            notesState = PlantNotesUiState.Content(listOf(PlantNote("n", "p", "Watered today", 0, 0))),
            notesMutationError = null, notesMutating = false, photoMutating = false, photoMutationError = null, photoCleanupRetryEnabled = false, mutationError = null, isDeleting = false,
            onBack = {}, onRetry = {}, onBeginEdit = {}, onCancelEdit = {}, onNameChange = {}, onScientificNameChange = {}, onLocationChange = {}, onAcquiredEpochDayChange = {}, onDescriptionChange = {}, onFavoriteClick = {}, onSave = {}, onDelete = {}, onAddNote = {}, onEditNote = {}, onDeleteNote = {}, onRetryNotes = {}, onAddStagedNote = {}, onUpdateStagedNote = { _, _ -> }, onRemoveStagedNote = {}, onTakePhoto = {}, onPickPhoto = {}, onRemoveStagedPhoto = {}, onRemovePhoto = {}, onRetryPhotoCleanup = {},
        )
    }
}
