package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCrop
import com.miguelrodriguez19.stayleafy.data.photo.PhotoCropResult
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.SeasonalCareState
import com.miguelrodriguez19.stayleafy.model.SeasonalCareSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.reminder.CareReminderUiState
import com.miguelrodriguez19.stayleafy.ui.reminder.PlantCareStatusViewModel
import com.miguelrodriguez19.stayleafy.ui.components.LeafyCareIcon
import com.miguelrodriguez19.stayleafy.ui.components.LeafyDestructiveButton
import com.miguelrodriguez19.stayleafy.ui.components.LeafyDestructiveOutlinedButton
import com.miguelrodriguez19.stayleafy.ui.components.LeafyDiscardChangesDialog
import com.miguelrodriguez19.stayleafy.ui.components.leafyTopAppBarColors
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import com.miguelrodriguez19.stayleafy.ui.theme.leafyColors
import com.miguelrodriguez19.stayleafy.ui.theme.leafyLayoutTokens
import java.io.File
import java.time.LocalDate

/** The application supplies the ActivityResultContract that opens uCrop with this fixed request. */
typealias PlantPhotoCropContract = ActivityResultContract<PendingPhotoCrop, PhotoCropResult>

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
    onConfigureCare: () -> Unit = {},
    photoCropContract: PlantPhotoCropContract? = null,
) {
    val resolvedPhotoCropContract = photoCropContract ?: remember { UCropPhotoContract() }
    val viewModel: PlantDetailViewModel = hiltViewModel(
        creationCallback = { factory: PlantDetailViewModel.Factory -> factory.create(plantId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val photoState by viewModel.photoUiState.collectAsStateWithLifecycle()
    val pendingCaptureToken by viewModel.pendingCaptureToken.collectAsStateWithLifecycle()
    val pendingPhotoCrop by viewModel.pendingPhotoCrop.collectAsStateWithLifecycle()
    val photoCropLaunchStarted by viewModel.photoCropLaunchStarted.collectAsStateWithLifecycle()
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
    var recordCare: (CareType, Long?) -> Unit = { _, _ -> }
    var clearCareHistory: (CareType) -> Unit = {}
    var reminderState: CareReminderUiState = CareReminderUiState.Content(emptyList())
    var retryReminders: () -> Unit = {}

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
        recordCare = careViewModel::record
        clearCareHistory = careViewModel::clearHistory
        val reminderViewModel: PlantCareStatusViewModel = hiltViewModel(
            creationCallback = { factory: PlantCareStatusViewModel.Factory -> factory.create(plantId) },
        )
        val observedReminderState by reminderViewModel.uiState.collectAsStateWithLifecycle()
        reminderState = observedReminderState
        retryReminders = reminderViewModel::retry
    }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            viewModel.onCameraResult(success)
        }
    val pickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onPickedPhoto)
        }
    val photoCropLauncher = rememberLauncherForActivityResult(resolvedPhotoCropContract) { result ->
        viewModel.onPhotoCropResult(result)
    }

    LaunchedEffect(pendingPhotoCrop, photoCropLaunchStarted, photoCropLauncher) {
        if (pendingPhotoCrop != null && !photoCropLaunchStarted) {
            viewModel.beginPhotoCropLaunch()?.let { request ->
                runCatching { photoCropLauncher.launch(request) }
                    .onFailure { viewModel.onPhotoCropLaunchFailed() }
            }
        }
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
        photoMutating = photoState.isMutating || pendingCaptureToken != null || pendingPhotoCrop != null,
        photoMutationError = photoState.error,
        photoCleanupRetryEnabled =
            (pendingCaptureToken != null || pendingPhotoCrop != null) && !photoState.isMutating,
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
        onRecordCare = recordCare,
        onClearCareHistory = clearCareHistory,
        reminderState = reminderState,
        onRetryReminders = retryReminders,
        onConfigureCare = onConfigureCare,
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
    onRecordCare: (CareType, Long?) -> Unit = { _, _ -> },
    onClearCareHistory: (CareType) -> Unit = {},
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemoveStagedPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryPhotoCleanup: () -> Unit,
    modifier: Modifier = Modifier,
    stagedPhotoFile: File? = null,
    onDiscardCreation: () -> Unit = onBack,
    reminderState: CareReminderUiState = CareReminderUiState.Content(emptyList()),
    onRetryReminders: () -> Unit = {},
    onConfigureCare: () -> Unit = {},
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val content = state as? PlantDetailUiState.Content
    val title = when {
        content?.mode == PlantDetailMode.Create -> stringResource(R.string.add_plant)
        content?.mode == PlantDetailMode.Edit -> stringResource(R.string.edit_plant)
        else -> stringResource(R.string.plant_details)
    }
    val navigationLabel = stringResource(
        if (content?.mode == PlantDetailMode.View) R.string.back else R.string.cancel,
    )
    val saveLabel = stringResource(R.string.save)
    val hasUnsavedEdit = content?.let {
        it.mode == PlantDetailMode.Edit && it.plant?.toPlantForm() != it.form
    } == true
    val requestBack = {
        if (!photoMutating) {
            when {
                content?.mode == PlantDetailMode.Edit && hasUnsavedEdit -> showDiscardDialog = true
                content?.mode == PlantDetailMode.Edit -> onCancelEdit()
                content?.mode == PlantDetailMode.Create && content.hasUnsavedCreation -> {
                    showDiscardDialog = true
                }

                else -> onBack()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = leafyTopAppBarColors(),
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        modifier = Modifier.semantics { contentDescription = navigationLabel },
                    ) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = null)
                    }
                },
                actions = {
                    when (content?.mode) {
                        PlantDetailMode.View -> IconButton(
                            onClick = onBeginEdit,
                            enabled = !isDeleting,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.edit_plant),
                            )
                        }

                        PlantDetailMode.Create, PlantDetailMode.Edit -> TextButton(
                            onClick = onSave,
                            enabled = !content.isSaving && !photoMutating,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.semantics { contentDescription = saveLabel },
                        ) {
                            Text(stringResource(if (content.isSaving) R.string.saving else R.string.save))
                        }

                        null -> Unit
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    onDeleteRequest = { showDeleteDialog = true },
                    onAddNote = onAddNote,
                    onEditNote = onEditNote,
                    onDeleteNote = onDeleteNote,
                    onRetryNotes = onRetryNotes,
                    careState = careState,
                    careMutationError = careMutationError,
                    careMutating = careMutating,
                    onRetryCare = onRetryCare,
                    onRecordCare = onRecordCare,
                    onClearCareHistory = onClearCareHistory,
                    reminderState = reminderState,
                    onRetryReminders = onRetryReminders,
                    onConfigureCare = onConfigureCare,
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
        val isCreating = content?.mode == PlantDetailMode.Create
        LeafyDiscardChangesDialog(
            title = stringResource(
                if (isCreating) R.string.discard_plant_title else R.string.discard_changes_title,
            ),
            message = stringResource(
                if (isCreating) R.string.discard_plant_message else R.string.discard_changes_message,
            ),
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                if (isCreating) onDiscardCreation() else onCancelEdit()
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
    onDeleteRequest: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetryNotes: () -> Unit,
    careState: CareEventsUiState,
    careMutationError: UiErrorCategory?,
    careMutating: Boolean,
    onRetryCare: () -> Unit,
    onRecordCare: (CareType, Long?) -> Unit,
    onClearCareHistory: (CareType) -> Unit,
    reminderState: CareReminderUiState,
    onRetryReminders: () -> Unit,
    onConfigureCare: () -> Unit,
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingNoteDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCareHistoryType by rememberSaveable { mutableStateOf<CareType?>(null) }
    var pendingCareDateType by rememberSaveable { mutableStateOf<CareType?>(null) }
    var pendingCareClearType by rememberSaveable { mutableStateOf<CareType?>(null) }
    val editable = state.mode != PlantDetailMode.View
    val plant = state.plant
    val favorite = plant?.isFavorite == true
    val hasPhoto =
        plant?.mainPhotoPath != null || state.stagedPhoto != null || stagedPhotoFile != null
    val editPhotoDescription = stringResource(R.string.edit_plant_photo)
    var photoExpanded by rememberSaveable { mutableStateOf(false) }
    val photoDescription = when {
        editable -> editPhotoDescription
        photoExpanded -> stringResource(R.string.collapse_plant_photo)
        else -> stringResource(R.string.expand_plant_photo)
    }
    LaunchedEffect(editable) {
        if (editable) photoExpanded = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 300))
                .then(
                    if (!editable && photoExpanded) {
                        Modifier.aspectRatio(1f)
                    } else {
                        Modifier.height(148.dp)
                    },
                )
                .then(
                    if (editable || hasPhoto) {
                        Modifier
                            .semantics(mergeDescendants = true) {
                                contentDescription = photoDescription
                            }
                            .clickable(
                                enabled = if (editable) !photoMutating && !state.isSaving else true,
                                onClick = {
                                    if (editable) {
                                        onReplacePhotoRequest()
                                    } else {
                                        photoExpanded = !photoExpanded
                                    }
                                },
                            )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = !editable && photoExpanded,
                animationSpec = tween(durationMillis = 300),
                modifier = Modifier.matchParentSize(),
                label = "plant photo content scale",
            ) { expanded ->
                PlantPhotoBox(
                    relativePath = plant?.mainPhotoPath,
                    stagedFile = stagedPhotoFile,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop,
                )
            }
            if (editable) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(leafyLayoutTokens.radiusMd))
                        .background(Color.Black.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        if (hasPhoto && editable) {
            if (state.mode == PlantDetailMode.Create) {
                LeafyDestructiveOutlinedButton(
                    text = stringResource(R.string.remove_photo),
                    onClick = onRemoveStagedPhoto,
                    enabled = !photoMutating && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LeafyDestructiveOutlinedButton(
                    text = stringResource(R.string.remove_photo),
                    onClick = { showPhotoDeleteDialog = true },
                    enabled = !photoMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        photoMutationError?.let { error ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (error == UiErrorCategory.PhotoDelete) R.string.photo_delete_error else R.string.photo_import_error),
                    color = MaterialTheme.leafyColors.accent,
                    modifier = Modifier.weight(1f)
                )
                if (photoCleanupRetryEnabled) TextButton(onClick = onRetryPhotoCleanup) {
                    Text(
                        stringResource(R.string.retry)
                    )
                }
            }
        }

        mutationError?.let { error ->
            Text(
                stringResource(if (error == UiErrorCategory.Delete) R.string.delete_plant_error else R.string.favorite_update_error),
                color = MaterialTheme.leafyColors.accent
            )
        }
        state.saveError?.let {
            Text(
                stringResource(R.string.save_plant_error),
                color = MaterialTheme.leafyColors.accent
            )
        }

        if (editable) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlantFormFields(
                        state,
                        onNameChange,
                        onScientificNameChange,
                        onLocationChange,
                        onAcquiredEpochDayChange,
                        onDescriptionChange
                    ) { showDatePicker = true }
                }
            }
        } else if (plant != null) {
            PlantIdentity(
                name = plant.name,
                scientificName = plant.scientificName,
                favorite = favorite,
                enabled = !isDeleting,
                onFavoriteClick = { onFavoriteClick(!favorite) },
            )
            PlantMetadata(
                location = plant.location,
                acquiredEpochDay = plant.acquiredEpochDay,
                description = plant.description,
            )
        }

        if (state.mode == PlantDetailMode.View) {
            CareSection(
                state = careState,
                mutationError = careMutationError,
                mutating = careMutating,
                onRecordNow = { type -> onRecordCare(type, null) },
                onPickDate = { type -> pendingCareDateType = type },
                onOpenHistory = { type -> selectedCareHistoryType = type },
                onRetry = onRetryCare,
                reminderState = reminderState,
                onRetryReminders = onRetryReminders,
                onConfigureCare = onConfigureCare,
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
            LeafyDestructiveOutlinedButton(
                text = stringResource(R.string.delete_plant),
                onClick = onDeleteRequest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDeleting,
            )
        }
    }

    if (showPhotoDeleteDialog) {
        RemovePhotoDialog(
            isBusy = photoMutating,
            onDismiss = { if (!photoMutating) showPhotoDeleteDialog = false },
            onConfirm = { showPhotoDeleteDialog = false; onRemovePhoto() },
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
                LeafyDestructiveButton(
                    text = stringResource(R.string.delete_note),
                    onClick = {
                        onDeleteNote(noteId)
                        pendingNoteDeleteId = null
                    },
                    enabled = !notesMutating,
                )
            },
        )
    }
    pendingCareClearType?.let { careType ->
        AlertDialog(
            onDismissRequest = { if (!careMutating) pendingCareClearType = null },
            title = {
                Text(
                    stringResource(
                        R.string.clear_care_history_title,
                        careType.historyLabel()
                    )
                )
            },
            text = { Text(stringResource(R.string.clear_care_history_message)) },
            dismissButton = {
                TextButton(onClick = { pendingCareClearType = null }, enabled = !careMutating) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                LeafyDestructiveButton(
                    text = stringResource(R.string.clear_history),
                    onClick = {
                        onClearCareHistory(careType)
                        pendingCareClearType = null
                    },
                    enabled = !careMutating,
                )
            },
        )
    }
    pendingCareDateType?.let { careType ->
        val todayPickerMillis = initialAcquisitionDatePickerMillis(null)
        val pastAndPresentDates = remember(todayPickerMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayPickerMillis
            }
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = todayPickerMillis,
            selectableDates = pastAndPresentDates,
        )
        DatePickerDialog(
            onDismissRequest = { pendingCareDateType = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            onRecordCare(careType, pickerMillisToEpochDay(selected))
                        }
                        pendingCareDateType = null
                    },
                    enabled = !careMutating,
                ) { Text(stringResource(R.string.record_care)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCareDateType = null }, enabled = !careMutating) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
    selectedCareHistoryType?.let { careType ->
        val events = (careState as? CareEventsUiState.Content)
            ?.events
            .orEmpty()
            .filter { it.careType == careType }
        CareHistorySheet(
            type = careType,
            events = events,
            mutating = careMutating,
            onDismiss = { selectedCareHistoryType = null },
            onClearRequest = { pendingCareClearType = careType },
        )
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialAcquisitionDatePickerMillis(state.form.acquiredEpochDay),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onAcquiredEpochDayChange(
                            pickerMillisToEpochDay(it)
                        )
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDatePicker = false
                }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@Composable
private fun PlantIdentity(
    name: String,
    scientificName: String?,
    favorite: Boolean,
    enabled: Boolean,
    onFavoriteClick: () -> Unit,
) {
    val favoriteDescription = stringResource(
        if (favorite) R.string.remove_favorite else R.string.add_favorite,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            scientificName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onFavoriteClick,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = favoriteDescription },
        ) {
            Icon(
                painterResource(if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                contentDescription = null,
                tint = MaterialTheme.leafyColors.accent,
            )
        }
    }
}

@Composable
private fun PlantMetadata(
    location: String?,
    acquiredEpochDay: Long?,
    description: String?,
) {
    if (location == null && acquiredEpochDay == null && description == null) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            location?.let {
                PlantMetadataRow(R.drawable.ic_location, it)
            }
            acquiredEpochDay?.let {
                if (location != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PlantMetadataRow(R.drawable.ic_calendar, formatEpochDay(it))
            }
            description?.let {
                if (location != null || acquiredEpochDay != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PlantMetadataRow(R.drawable.ic_note, it)
            }
        }
    }
}

@Composable
private fun PlantMetadataRow(icon: Int, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlantFormFields(
    state: PlantDetailUiState.Content,
    onNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAcquiredEpochDayChange: (Long?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    openDatePicker: () -> Unit,
) {
    val acquisitionDateDescription = stringResource(R.string.acquisition_date)
    val focusManager = LocalFocusManager.current
    val scientificNameFocusRequester = remember { FocusRequester() }
    val locationFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = state.form.name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.plant_name)) },
        leadingIcon = { FormFieldIcon(R.drawable.ic_leaf) },
        supportingText = if (state.nameError) ({ Text(stringResource(R.string.plant_name_required)) }) else null,
        isError = state.nameError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { scientificNameFocusRequester.requestFocus() }),
    )
    EditorTextField(
        value = state.form.scientificName,
        onValueChange = onScientificNameChange,
        label = R.string.scientific_name,
        icon = R.drawable.ic_science,
        focusRequester = scientificNameFocusRequester,
        onNext = { locationFocusRequester.requestFocus() },
    )
    EditorTextField(
        value = state.form.location,
        onValueChange = onLocationChange,
        label = R.string.location,
        icon = R.drawable.ic_location,
        focusRequester = locationFocusRequester,
        onNext = { descriptionFocusRequester.requestFocus() },
    )
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.form.acquiredEpochDay
                ?.let(::formatEpochDay)
                ?: formatEpochDay(LocalDate.now().toEpochDay()),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.acquisition_date)) },
            leadingIcon = { FormFieldIcon(R.drawable.ic_calendar) },
            placeholder = { Text(stringResource(R.string.select_date_hint)) },
            readOnly = true,
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = acquisitionDateDescription }
                .clickable(onClick = openDatePicker))
    }
    if (state.form.acquiredEpochDay != null) TextButton(onClick = { onAcquiredEpochDayChange(null) }) {
        Text(
            stringResource(R.string.clear_date)
        )
    }
    val description = state.form.description.take(MAX_PLANT_DESCRIPTION_LENGTH)
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = description,
            onValueChange = { onDescriptionChange(it.take(MAX_PLANT_DESCRIPTION_LENGTH)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(descriptionFocusRequester),
            label = { Text(stringResource(R.string.description)) },
            leadingIcon = { FormFieldIcon(R.drawable.ic_note) },
            minLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        Text(
            text = stringResource(
                R.string.description_character_count,
                description.length,
                MAX_PLANT_DESCRIPTION_LENGTH,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    icon: Int,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        label = { Text(stringResource(label)) },
        leadingIcon = { FormFieldIcon(icon) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
    )
}

@Composable
private fun FormFieldIcon(icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val PlantDetailUiState.Content.hasUnsavedCreation: Boolean
    get() = form != PlantForm() || stagedFavorite || stagedPhoto != null

@Composable
private fun CareSection(
    state: CareEventsUiState,
    mutationError: UiErrorCategory?,
    mutating: Boolean,
    onRecordNow: (CareType) -> Unit,
    onPickDate: (CareType) -> Unit,
    onOpenHistory: (CareType) -> Unit,
    onRetry: () -> Unit,
    reminderState: CareReminderUiState,
    onRetryReminders: () -> Unit,
    onConfigureCare: () -> Unit,
) {
    val events = (state as? CareEventsUiState.Content)?.events.orEmpty()
    val summaries = (reminderState as? CareReminderUiState.Content)?.summaries.orEmpty()
    val seasonalSummaries =
        (reminderState as? CareReminderUiState.Content)?.seasonalSummaries.orEmpty()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.care_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            if (state is CareEventsUiState.Empty || state is CareEventsUiState.Content) {
                CareType.values().forEachIndexed { index, type ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    val latest = events.firstOrNull { it.careType == type }
                    CareSummaryRow(
                        type = type,
                        latest = latest,
                        reminder = summaries.firstOrNull { it.careType == type },
                        seasonalReminder = seasonalSummaries.firstOrNull { it.careType == type },
                        onRecordNow = { onRecordNow(type) },
                        onPickDate = { onPickDate(type) },
                        onOpenHistory = { onOpenHistory(type) },
                        enabled = !mutating,
                    )
                }
            }
            when (reminderState) {
                CareReminderUiState.Loading -> Text(
                    stringResource(R.string.loading_care_schedule),
                    style = MaterialTheme.typography.bodySmall,
                )

                CareReminderUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.load_care_schedule_error),
                        Modifier.weight(1f),
                        color = MaterialTheme.leafyColors.accent,
                    )
                    TextButton(onClick = onRetryReminders) { Text(stringResource(R.string.retry)) }
                }

                is CareReminderUiState.Content -> Unit
            }
            when (state) {
                CareEventsUiState.Loading -> CircularProgressIndicator()
                is CareEventsUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.load_care_error),
                        Modifier.weight(1f),
                        color = MaterialTheme.leafyColors.accent
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }

                CareEventsUiState.Empty -> Unit
                is CareEventsUiState.Content -> Unit
            }
            if (mutationError != null) {
                Text(
                    stringResource(
                        if (mutationError == UiErrorCategory.CareDelete) {
                            R.string.clear_care_history_error
                        } else {
                            R.string.save_care_error
                        },
                    ),
                    color = MaterialTheme.leafyColors.accent,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onConfigureCare,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.configure_care),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CareSummaryRow(
    type: CareType,
    latest: CareEvent?,
    reminder: CareStatusSummary?,
    seasonalReminder: SeasonalCareSummary?,
    onRecordNow: () -> Unit,
    onPickDate: () -> Unit,
    onOpenHistory: () -> Unit,
    enabled: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpenHistory)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeafyCareIcon(
            careType = type,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(type.historyLabel(), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    R.string.care_last_time,
                    latest?.let { formatCareDate(it.performedAt) }
                        ?: stringResource(R.string.care_never),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reminder != null) {
                Text(
                    text = stringResource(
                        when (reminder.status) {
                            CareDueStatus.OVERDUE -> R.string.care_due_overdue
                            CareDueStatus.DUE_TODAY -> R.string.care_due_today
                            CareDueStatus.UPCOMING -> R.string.care_due_upcoming
                            CareDueStatus.LATER -> R.string.care_due_later
                        },
                        formatEpochDay(reminder.nextDueEpochDay),
                    ),
                    color = if (reminder.status == CareDueStatus.OVERDUE) {
                        MaterialTheme.leafyColors.accent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (seasonalReminder != null) {
                val range = if (seasonalReminder.startEpochDay == seasonalReminder.endEpochDay) {
                    formatEpochDay(seasonalReminder.startEpochDay)
                } else {
                    stringResource(
                        R.string.care_season_range,
                        formatEpochDay(seasonalReminder.startEpochDay),
                        formatEpochDay(seasonalReminder.endEpochDay),
                    )
                }
                Text(
                    stringResource(
                        when (seasonalReminder.state) {
                            SeasonalCareState.UPCOMING -> R.string.care_season_upcoming
                            SeasonalCareState.ACTIVE -> R.string.care_season_active
                            SeasonalCareState.SATISFIED -> R.string.care_season_satisfied
                        },
                        range,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (seasonalReminder.state == SeasonalCareState.ACTIVE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            } else {
                Text(
                    stringResource(R.string.care_not_scheduled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CareActionButton(
            label = type.actionLabel(),
            enabled = enabled,
            onClick = onRecordNow,
            onLongClick = onPickDate,
        )
    }
}

@Composable
private fun CareActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val chooseDateLabel = stringResource(R.string.choose_care_date)
    Surface(
        modifier = Modifier.combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = label,
            onLongClickLabel = chooseDateLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
    ) {
        Text(label, Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CareHistorySheet(
    type: CareType,
    events: List<CareEvent>,
    mutating: Boolean,
    onDismiss: () -> Unit,
    onClearRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CareHistoryContent(type, events, mutating, onClearRequest)
    }
}

@Composable
private fun CareHistoryContent(
    type: CareType,
    events: List<CareEvent>,
    mutating: Boolean,
    onClearRequest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val moreOptionsLabel = stringResource(R.string.more_options)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.care_type_history, type.historyLabel()),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = events.isNotEmpty() && !mutating,
                    modifier = Modifier.semantics {
                        contentDescription = moreOptionsLabel
                    },
                ) { Icon(painterResource(R.drawable.ic_more), contentDescription = null) }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.clear_history),
                                color = MaterialTheme.leafyColors.accent,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onClearRequest()
                        },
                    )
                }
            }
        }
        if (events.isEmpty()) {
            Text(stringResource(R.string.no_care_events_for_type))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = CareEvent::id) { event ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(formatCareDate(event.performedAt))
                            event.note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.notes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onAdd,
                enabled = !mutating
            ) { Text(stringResource(R.string.add_note)) }
        }
        when (state) {
            PlantNotesUiState.Loading -> CircularProgressIndicator()
            is PlantNotesUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.load_notes_error),
                    Modifier.weight(1f),
                    color = MaterialTheme.leafyColors.accent
                ); TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }

            PlantNotesUiState.Empty -> Text(
                stringResource(R.string.no_notes_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PlantNotesUiState.Content -> state.notes.forEach { note ->
                PlantNoteCard(
                    note = note,
                    mutating = mutating,
                    onEdit = { onEdit(note.id) },
                    onDelete = { onDeleteRequest(note.id) },
                )
            }
        }
        mutationError?.let {
            Text(
                stringResource(R.string.delete_note_error),
                color = MaterialTheme.leafyColors.accent
            )
        }
    }
}

@Composable
private fun PlantNoteCard(
    note: PlantNote,
    mutating: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatTimestamp(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(note.body, style = MaterialTheme.typography.bodyMedium)
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !mutating,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_more),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_note)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete_note),
                                color = MaterialTheme.leafyColors.accent,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_photo_source)) },
        text = {},
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(
                    onClick = onTakePhoto,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.take_photo)) }
                OutlinedButton(
                    onClick = onPickPhoto,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) { Text(stringResource(R.string.choose_photo)) }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.cancel)) }
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
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            LeafyDestructiveButton(
                text = stringResource(R.string.remove_photo),
                onClick = onConfirm,
                enabled = !isBusy,
            )
        },
    )
}

@Composable
private fun DetailError(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.load_plant_error)); Button(
        onClick = onRetry,
        Modifier.padding(top = 16.dp)
    ) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun NotFoundContent(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.plant_not_found),
            style = MaterialTheme.typography.titleMedium
        ); Button(
        onClick = onBack,
        Modifier.padding(top = 16.dp)
    ) { Text(stringResource(R.string.back_to_plants)) }
    }
}

@Composable
private fun DeletePlantDialog(isDeleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_plant_title)) },
        text = { Text(stringResource(R.string.delete_plant_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            LeafyDestructiveButton(
                text = stringResource(R.string.delete_plant),
                onClick = onConfirm,
                enabled = !isDeleting,
            )
        })
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
            onBack = {},
            onRetry = {},
            onBeginEdit = {},
            onCancelEdit = {},
            onNameChange = {},
            onScientificNameChange = {},
            onLocationChange = {},
            onAcquiredEpochDayChange = {},
            onDescriptionChange = {},
            onFavoriteClick = {},
            onSave = {},
            onDelete = {},
            onAddNote = {},
            onEditNote = {},
            onDeleteNote = {},
            onRetryNotes = {},
            onTakePhoto = {},
            onPickPhoto = {},
            onRemoveStagedPhoto = {},
            onRemovePhoto = {},
            onRetryPhotoCleanup = {},
        )
    }
}

@Preview(name = "Detail - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Detail - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantDetailPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantDetailScreen(
            state = PlantDetailUiState.Content(
                PlantDetailMode.View,
                previewPlants.first(),
                previewPlants.first().toPlantForm()
            ),
            notesState = PlantNotesUiState.Content(
                listOf(
                    PlantNote(
                        "n",
                        "p",
                        "Watered today",
                        0,
                        0
                    )
                )
            ),
            notesMutationError = null,
            notesMutating = false,
            photoMutating = false,
            photoMutationError = null,
            photoCleanupRetryEnabled = false,
            mutationError = null,
            isDeleting = false,
            onBack = {},
            onRetry = {},
            onBeginEdit = {},
            onCancelEdit = {},
            onNameChange = {},
            onScientificNameChange = {},
            onLocationChange = {},
            onAcquiredEpochDayChange = {},
            onDescriptionChange = {},
            onFavoriteClick = {},
            onSave = {},
            onDelete = {},
            onAddNote = {},
            onEditNote = {},
            onDeleteNote = {},
            onRetryNotes = {},
            onTakePhoto = {},
            onPickPhoto = {},
            onRemoveStagedPhoto = {},
            onRemovePhoto = {},
            onRetryPhotoCleanup = {},
            reminderState = CareReminderUiState.Content(
                summaries = emptyList(),
                seasonalSummaries = listOf(
                    SeasonalCareSummary(
                        plantId = previewPlants.first().id,
                        careType = CareType.PRUNING,
                        startEpochDay = 20_745,
                        endEpochDay = 20_835,
                        state = SeasonalCareState.ACTIVE,
                    ),
                ),
            ),
        )
    }
}

@Preview(
    name = "Care history - Light",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Care history - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CareHistoryPreview() {
    val events = listOf(
        CareEvent(
            id = "new",
            plantId = "plant",
            careType = CareType.WATERING,
            performedAt = 1_725_000_000_000L,
            note = null,
            createdAt = 1_725_000_000_000L,
            updatedAt = 1_725_000_000_000L,
        ),
        CareEvent(
            id = "old",
            plantId = "plant",
            careType = CareType.WATERING,
            performedAt = 1_720_000_000_000L,
            note = "Watered thoroughly",
            createdAt = 1_720_000_000_000L,
            updatedAt = 1_720_000_000_000L,
        ),
    )
    StayLeafyTheme(dynamicColor = false) {
        Surface {
            CareHistoryContent(
                type = CareType.WATERING,
                events = events,
                mutating = false,
                onClearRequest = {},
            )
        }
    }
}
