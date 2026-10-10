package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailRoute(
    plantId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    onAddNote: () -> Unit = {},
    onEditNote: (String) -> Unit = {},
) {
    val plantViewModel: PlantDetailViewModel = hiltViewModel(
        creationCallback = { factory: PlantDetailViewModel.Factory -> factory.create(plantId) },
    )
    val notesViewModel: PlantNotesViewModel = hiltViewModel(
        creationCallback = { factory: PlantNotesViewModel.Factory -> factory.create(plantId) },
    )
    val photoViewModel: PlantPhotoViewModel = hiltViewModel(
        creationCallback = { factory: PlantPhotoViewModel.Factory -> factory.create(plantId) },
    )
    val plantState by plantViewModel.uiState.collectAsStateWithLifecycle()
    val notesState by notesViewModel.uiState.collectAsStateWithLifecycle()
    val notesError by notesViewModel.mutationError.collectAsStateWithLifecycle()
    val notesMutating by notesViewModel.isMutating.collectAsStateWithLifecycle()
    val mutationError by plantViewModel.mutationError.collectAsStateWithLifecycle()
    val isDeleting by plantViewModel.isDeleting.collectAsStateWithLifecycle()
    val deleted by plantViewModel.deleted.collectAsStateWithLifecycle()
    val pendingToken by photoViewModel.pendingCaptureToken.collectAsStateWithLifecycle()
    val photoMutating by photoViewModel.isMutating.collectAsStateWithLifecycle()
    val photoMutationError by photoViewModel.mutationError.collectAsStateWithLifecycle()
    var showPhotoDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (pendingToken != null) photoViewModel.onCameraResult(success)
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(photoViewModel::importPhoto)
    }
    LaunchedEffect(deleted) { if (deleted) onDeleted() }

    PlantDetailScreen(
        state = plantState,
        notesState = notesState,
        notesMutationError = notesError,
        notesMutating = notesMutating,
        photoMutating = photoMutating || pendingToken != null,
        photoMutationError = photoMutationError,
        photoCleanupRetryEnabled = pendingToken != null && !photoMutating,
        mutationError = mutationError,
        isDeleting = isDeleting,
        onBack = onBack,
        onRetry = { plantViewModel.retry(); notesViewModel.retry() },
        onEdit = onEdit,
        onFavoriteClick = plantViewModel::setFavorite,
        onDelete = plantViewModel::delete,
        onAddNote = onAddNote,
        onEditNote = onEditNote,
        onDeleteNote = notesViewModel::delete,
        onRetryNotes = notesViewModel::retry,
        onTakePhoto = {
            photoViewModel.prepareCameraCapture()?.let { capture ->
                runCatching { cameraLauncher.launch(capture.contentUri) }
                    .onFailure { photoViewModel.onCameraLaunchFailed() }
            }
        },
        onPickPhoto = {
            runCatching {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }.onFailure { photoViewModel.onPickerLaunchFailed() }
        },
        onRemovePhotoRequest = { showPhotoDeleteDialog = true },
        onRetryPhotoCleanup = photoViewModel::retryPendingCaptureCleanup,
        modifier = modifier,
    )

    if (showPhotoDeleteDialog) {
        RemovePhotoDialog(
            isBusy = photoMutating,
            onDismiss = { if (!photoMutating) showPhotoDeleteDialog = false },
            onConfirm = {
                photoViewModel.removePhoto()
                showPhotoDeleteDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    state: PlantDetailUiState,
    mutationError: UiErrorCategory?,
    isDeleting: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    notesState: PlantNotesUiState = PlantNotesUiState.Empty,
    notesMutationError: UiErrorCategory? = null,
    notesMutating: Boolean = false,
    photoMutating: Boolean = false,
    photoMutationError: UiErrorCategory? = null,
    photoCleanupRetryEnabled: Boolean = false,
    onAddNote: () -> Unit = {},
    onEditNote: (String) -> Unit = {},
    onDeleteNote: (String) -> Unit = {},
    onRetryNotes: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onRemovePhotoRequest: () -> Unit = {},
    onRetryPhotoCleanup: () -> Unit = {},
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoSourceDialog by rememberSaveable { mutableStateOf(false) }
    val title = when (state) {
        is PlantDetailUiState.Content -> state.plant.displayName
        else -> stringResource(R.string.plant_details)
    }
    val backDescription = stringResource(R.string.back)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDescription }) {
                        Text(stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                PlantDetailUiState.Loading -> LoadingContent(modifier = Modifier.align(Alignment.Center))
                is PlantDetailUiState.Error -> DetailError(onRetry)
                PlantDetailUiState.NotFound -> NotFoundContent(onBack)
                is PlantDetailUiState.Content -> PlantDetailContent(
                    plant = state.plant,
                    notesState = notesState,
                    notesMutationError = notesMutationError,
                    notesMutating = notesMutating,
                    photoMutating = photoMutating,
                    photoMutationError = photoMutationError,
                    photoCleanupRetryEnabled = photoCleanupRetryEnabled,
                    mutationError = mutationError,
                    isDeleting = isDeleting,
                    onEdit = onEdit,
                    onFavoriteClick = onFavoriteClick,
                    onDeleteRequest = { showDeleteDialog = true },
                    onAddNote = onAddNote,
                    onEditNote = onEditNote,
                    onDeleteNote = onDeleteNote,
                    onRetryNotes = onRetryNotes,
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onReplacePhotoRequest = { showPhotoSourceDialog = true },
                    onRemovePhotoRequest = onRemovePhotoRequest,
                    onRetryPhotoCleanup = onRetryPhotoCleanup,
                )
            }
        }
    }
    if (showDeleteDialog) DeletePlantDialog(isDeleting, { if (!isDeleting) showDeleteDialog = false }, onDelete)
    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            isBusy = photoMutating,
            onDismiss = { if (!photoMutating) showPhotoSourceDialog = false },
            onTakePhoto = {
                showPhotoSourceDialog = false
                onTakePhoto()
            },
            onPickPhoto = {
                showPhotoSourceDialog = false
                onPickPhoto()
            },
        )
    }
}

@Composable
private fun PlantDetailContent(
    plant: Plant,
    notesState: PlantNotesUiState,
    notesMutationError: UiErrorCategory?,
    notesMutating: Boolean,
    photoMutating: Boolean,
    photoMutationError: UiErrorCategory?,
    photoCleanupRetryEnabled: Boolean,
    mutationError: UiErrorCategory?,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetryNotes: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onReplacePhotoRequest: () -> Unit,
    onRemovePhotoRequest: () -> Unit,
    onRetryPhotoCleanup: () -> Unit,
) {
    var noteToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlantPhotoBox(plant.mainPhotoPath, Modifier.fillMaxWidth().height(220.dp))
        if (plant.mainPhotoPath == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakePhoto, enabled = !isDeleting && !photoMutating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.take_photo)) }
                OutlinedButton(onClick = onPickPhoto, enabled = !isDeleting && !photoMutating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.choose_photo)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReplacePhotoRequest, enabled = !isDeleting && !photoMutating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.replace_photo)) }
                OutlinedButton(onClick = onRemovePhotoRequest, enabled = !isDeleting && !photoMutating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.remove_photo)) }
            }
        }
        photoMutationError?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (it == UiErrorCategory.PhotoDelete) R.string.photo_delete_error else R.string.photo_import_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (photoCleanupRetryEnabled) {
                    TextButton(onClick = onRetryPhotoCleanup) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onFavoriteClick(!plant.isFavorite) }, modifier = Modifier.weight(1f), enabled = !isDeleting) { Text(stringResource(if (plant.isFavorite) R.string.remove_favorite else R.string.add_favorite)) }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), enabled = !isDeleting) { Text(stringResource(R.string.edit_plant)) }
        }
        mutationError?.let { Text(stringResource(if (it == UiErrorCategory.Delete) R.string.delete_plant_error else R.string.favorite_update_error), color = MaterialTheme.colorScheme.error) }
        DetailField(R.string.display_name, plant.displayName)
        plant.commonName?.let { DetailField(R.string.common_name, it) }
        plant.scientificName?.let { DetailField(R.string.scientific_name, it) }
        plant.location?.let { DetailField(R.string.location, it) }
        plant.acquiredEpochDay?.let { DetailField(R.string.acquisition_date, formatEpochDay(it)) }
        plant.description?.let { DetailField(R.string.description, it) }
        NotesSection(notesState, notesMutationError, notesMutating, onAddNote, onEditNote, { noteToDelete = it }, onRetryNotes)
        OutlinedButton(onClick = onDeleteRequest, modifier = Modifier.fillMaxWidth(), enabled = !isDeleting) { Text(stringResource(R.string.delete_plant)) }
    }
    noteToDelete?.let { noteId ->
        AlertDialog(
            onDismissRequest = { if (!notesMutating) noteToDelete = null },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            dismissButton = { TextButton(onClick = { noteToDelete = null }, enabled = !notesMutating) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { Button(onClick = { onDeleteNote(noteId); noteToDelete = null }, enabled = !notesMutating) { Text(stringResource(R.string.delete_note)) } },
        )
    }
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
                is PlantNotesUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.load_notes_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
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
private fun PhotoSourceDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.replace_photo)) },
        text = { Text(stringResource(R.string.choose_photo_source)) },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(onClick = onTakePhoto, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.take_photo))
                }
                OutlinedButton(
                    onClick = onPickPhoto,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.choose_photo))
                }
                TextButton(onClick = onDismiss, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun RemovePhotoDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_photo_title)) },
        text = { Text(stringResource(R.string.remove_photo_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isBusy) {
                Text(stringResource(R.string.remove_photo))
            }
        },
    )
}

@Composable private fun DetailField(label: Int, value: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge); Text(value, Modifier.padding(top = 4.dp)) } } }
@Composable private fun DetailError(onRetry: () -> Unit) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.load_plant_error)); Button(onClick = onRetry, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) } } }
@Composable private fun NotFoundContent(onBack: () -> Unit) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.plant_not_found), style = MaterialTheme.typography.titleMedium); Button(onClick = onBack, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.back_to_plants)) } } }
@Composable private fun DeletePlantDialog(isDeleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.delete_plant_title)) }, text = { Text(stringResource(R.string.delete_plant_message)) }, dismissButton = { TextButton(onClick = onDismiss, enabled = !isDeleting) { Text(stringResource(R.string.cancel)) } }, confirmButton = { Button(onClick = onConfirm, enabled = !isDeleting) { Text(stringResource(R.string.delete_plant)) } }) }

@Preview(name = "Detail - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Detail - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PlantDetailPreview() { StayLeafyTheme(dynamicColor = false) { PlantDetailScreen(PlantDetailUiState.Content(previewPlants.first()), null, false, {}, {}, {}, {}, {}, notesState = PlantNotesUiState.Content(listOf(PlantNote("n", "p", "Watered today", 0, 0)))) } }

@Preview(name = "Notes empty - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notes empty - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PlantNotesEmptyPreview() { StayLeafyTheme(dynamicColor = false) { PlantDetailScreen(PlantDetailUiState.Content(previewPlants.last()), null, false, {}, {}, {}, {}, {}, notesState = PlantNotesUiState.Empty) } }

@Preview(name = "Notes error - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notes error - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PlantNotesErrorPreview() { StayLeafyTheme(dynamicColor = false) { PlantDetailScreen(PlantDetailUiState.Content(previewPlants.last()), null, false, {}, {}, {}, {}, {}, notesState = PlantNotesUiState.Error()) } }

@Preview(name = "Delete confirmation - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Delete confirmation - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PlantDeleteDialogPreview() { StayLeafyTheme(dynamicColor = false) { DeletePlantDialog(false, {}, {}) } }

@Preview(name = "Photo source - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Photo source - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PhotoSourceDialogPreview() { StayLeafyTheme(dynamicColor = false) { PhotoSourceDialog(false, {}, {}, {}) } }

@Preview(name = "Remove photo - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Remove photo - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun RemovePhotoDialogPreview() { StayLeafyTheme(dynamicColor = false) { RemovePhotoDialog(false, {}, {}) } }
