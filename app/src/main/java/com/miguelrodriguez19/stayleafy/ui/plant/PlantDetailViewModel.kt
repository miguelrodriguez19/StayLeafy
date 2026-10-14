package com.miguelrodriguez19.stayleafy.ui.plant

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCapture
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlantDetailViewModel.Factory::class)
class PlantDetailViewModel @AssistedInject constructor(
    private val savedStateHandle: SavedStateHandle,
    @Assisted private val plantId: String?,
    private val plantRepository: PlantRepository,
    private val photoRepository: PlantPhotoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlantDetailUiState>(
        if (plantId == null) {
            PlantDetailUiState.Content(
                mode = PlantDetailMode.Create,
                plant = null,
                form = savedForm(),
                stagedFavorite = savedStateHandle[FAVORITE_KEY] ?: false,
                stagedPhoto = savedStagedPhoto(),
            )
        } else {
            PlantDetailUiState.Loading
        },
    )
    val uiState: StateFlow<PlantDetailUiState> = _uiState.asStateFlow()

    private val _mutationError = MutableStateFlow<UiErrorCategory?>(null)
    val mutationError: StateFlow<UiErrorCategory?> = _mutationError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _photoUiState = MutableStateFlow(PlantPhotoUiState())
    val photoUiState: StateFlow<PlantPhotoUiState> = _photoUiState.asStateFlow()

    private val _pendingCaptureToken = MutableStateFlow(
        savedStateHandle.get<String>(PENDING_CAPTURE_KEY),
    )
    val pendingCaptureToken: StateFlow<String?> = _pendingCaptureToken.asStateFlow()

    private var plantObservationJob: Job? = null

    init {
        if (plantId != null) observePlant(plantId)
    }

    fun retry() {
        if (plantId == null) return
        observePlant(plantId)
    }

    fun beginEdit() {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        if (state.plant != null) _uiState.value = state.copy(mode = PlantDetailMode.Edit, saveError = null)
    }

    fun cancelEdit() {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        val plant = state.plant ?: return
        _uiState.value = state.copy(
            mode = PlantDetailMode.View,
            form = plant.toPlantForm(),
            nameError = false,
            saveError = null,
        )
    }

    fun updateName(value: String) = updateForm { copy(name = value) }
    fun updateScientificName(value: String) = updateForm { copy(scientificName = value) }
    fun updateLocation(value: String) = updateForm { copy(location = value) }
    fun updateAcquiredEpochDay(value: Long?) = updateForm { copy(acquiredEpochDay = value) }
    fun updateDescription(value: String) = updateForm {
        copy(description = value.take(MAX_PLANT_DESCRIPTION_LENGTH))
    }

    fun setFavorite(favorite: Boolean) {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        if (state.mode == PlantDetailMode.Create) {
            savedStateHandle[FAVORITE_KEY] = favorite
            _uiState.value = state.copy(stagedFavorite = favorite)
            return
        }
        val id = plantId ?: return
        viewModelScope.launch {
            _mutationError.value = null
            runCatching { plantRepository.setFavorite(id, favorite) }
                .onFailure { _mutationError.value = UiErrorCategory.Favorite }
        }
    }

    fun save() {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        val name = state.form.name.trim()
        if (name.isEmpty()) {
            _uiState.value = state.copy(nameError = true)
            return
        }
        if (state.isSaving || state.mode == PlantDetailMode.View) return

        _uiState.value = state.copy(isSaving = true, nameError = false, saveError = null)
        viewModelScope.launch {
            try {
                if (plantId == null) {
                    saveNewPlant(state, name)
                } else {
                    plantRepository.updatePlant(plantId, state.form.toDraft(name))
                    _uiState.value = (_uiState.value as PlantDetailUiState.Content).copy(
                        mode = PlantDetailMode.View,
                        isSaving = false,
                        saveError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                val current = _uiState.value as? PlantDetailUiState.Content ?: return@launch
                _uiState.value = current.copy(isSaving = false, saveError = UiErrorCategory.Save)
            }
        }
    }

    fun delete() {
        val id = plantId ?: return
        if (_isDeleting.value) return
        _isDeleting.value = true
        _mutationError.value = null
        viewModelScope.launch {
            runCatching { plantRepository.deletePlant(id) }
                .onSuccess { _deleted.value = true }
                .onFailure {
                    _mutationError.value = UiErrorCategory.Delete
                    _isDeleting.value = false
                }
        }
    }

    fun prepareCameraCapture(): PendingPhotoCapture? {
        if (_photoUiState.value.isMutating || _pendingCaptureToken.value != null) return null
        return try {
            photoRepository.prepareCameraCapture().also { capture ->
                savedStateHandle[PENDING_CAPTURE_KEY] = capture.token
                _pendingCaptureToken.value = capture.token
                _photoUiState.value = PlantPhotoUiState()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _photoUiState.value = PlantPhotoUiState(error = UiErrorCategory.PhotoImport)
            null
        }
    }

    fun onCameraResult(success: Boolean) {
        val token = _pendingCaptureToken.value ?: return
        if (_photoUiState.value.isMutating) return
        if (!success) {
            mutatePhoto {
                photoRepository.discardCameraCapture(token)
                clearPendingCapture()
            }
            return
        }
        val currentPlantId = plantId
        if (currentPlantId == null) {
            mutatePhoto {
                val staged = photoRepository.stageCameraCapture(token)
                clearPendingCapture()
                setStagedPhoto(staged.token)
            }
            return
        }
        mutatePhoto {
            photoRepository.completeCameraCapture(currentPlantId, token)
            clearPendingCapture()
        }
    }

    fun onCameraLaunchFailed() {
        val token = _pendingCaptureToken.value
        if (token == null) {
            _photoUiState.value = PlantPhotoUiState(error = UiErrorCategory.PhotoImport)
            return
        }
        mutatePhoto {
            photoRepository.discardCameraCapture(token)
            clearPendingCapture()
        }
    }

    fun retryPendingCaptureCleanup() {
        if (_pendingCaptureToken.value != null) onCameraResult(success = false)
    }

    fun onPickerLaunchFailed() {
        _photoUiState.value = PlantPhotoUiState(error = UiErrorCategory.PhotoImport)
    }

    fun onPickedPhoto(uri: Uri) {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        if (state.mode == PlantDetailMode.Create) {
            mutatePhoto {
                val previousToken = state.stagedPhoto?.token
                val staged = photoRepository.stageImage(uri)
                setStagedPhoto(staged.token)
                if (previousToken != null) photoRepository.discardStagedPhoto(previousToken)
            }
            return
        }
        val id = plantId ?: return
        mutatePhoto {
            if (state.plant?.mainPhotoPath == null) {
                photoRepository.importImage(id, uri)
            } else {
                photoRepository.replacePhoto(id, uri)
            }
        }
    }

    fun removePhoto() {
        val id = plantId ?: return
        mutatePhoto { photoRepository.removePhoto(id) }
    }

    fun removeStagedPhoto() {
        val token = (_uiState.value as? PlantDetailUiState.Content)?.stagedPhoto?.token ?: return
        mutatePhoto {
            photoRepository.discardStagedPhoto(token)
            clearStagedPhoto()
        }
    }

    fun discardCreation(onDiscarded: () -> Unit) {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        if (state.mode != PlantDetailMode.Create || state.isSaving || _photoUiState.value.isMutating) return
        val token = state.stagedPhoto?.token
        if (token == null) {
            clearCreationDraft()
            onDiscarded()
            return
        }
        _photoUiState.value = PlantPhotoUiState(isMutating = true)
        viewModelScope.launch {
            try {
                photoRepository.discardStagedPhoto(token)
                clearCreationDraft()
                _photoUiState.value = PlantPhotoUiState()
                onDiscarded()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _photoUiState.value = PlantPhotoUiState(error = UiErrorCategory.PhotoDelete)
            }
        }
    }

    fun resolveStagedPhoto(token: String) = photoRepository.resolveStagedPhoto(token)

    private fun observePlant(id: String) {
        plantObservationJob?.cancel()
        _uiState.value = PlantDetailUiState.Loading
        plantObservationJob = viewModelScope.launch {
            plantRepository.observePlant(id)
                .catch { _uiState.value = PlantDetailUiState.Error() }
                .collectLatest { plant ->
                    if (plant == null) {
                        if (_uiState.value !is PlantDetailUiState.Error) {
                            _uiState.value = PlantDetailUiState.NotFound
                        }
                    } else {
                        val current = _uiState.value as? PlantDetailUiState.Content
                        val mode = current?.mode?.takeIf { it != PlantDetailMode.Create } ?: PlantDetailMode.View
                        _uiState.value = PlantDetailUiState.Content(
                            mode = mode,
                            plant = plant,
                            form = if (mode == PlantDetailMode.Edit && current != null) current.form else plant.toPlantForm(),
                            nameError = current?.nameError ?: false,
                            saveError = current?.saveError,
                            isSaving = current?.isSaving ?: false,
                            stagedFavorite = plant.isFavorite,
                            stagedPhoto = current?.stagedPhoto,
                        )
                    }
                }
        }
    }

    private suspend fun saveNewPlant(previous: PlantDetailUiState.Content, name: String) {
        val id = plantRepository.createPlant(
            draft = previous.form.toDraft(name),
            favorite = previous.stagedFavorite,
            stagedPhotoToken = previous.stagedPhoto?.token,
            notes = emptyList(),
        )
        clearCreationDraft()
        val createdPlant = plantRepository.observePlant(id).first()
            ?: error("Created plant disappeared before navigation")
        val current = _uiState.value as? PlantDetailUiState.Content ?: return
        _uiState.value = current.copy(
            mode = PlantDetailMode.View,
            plant = createdPlant,
            form = createdPlant.toPlantForm(),
            isSaving = false,
            stagedFavorite = createdPlant.isFavorite,
            stagedPhoto = null,
            savedPlantId = id,
        )
    }

    private fun updateForm(update: PlantForm.() -> PlantForm) {
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        if (state.mode == PlantDetailMode.View) return
        val form = update(state.form)
        if (state.mode == PlantDetailMode.Create) persistForm(form)
        _uiState.value = state.copy(form = form, nameError = false, saveError = null)
    }

    private fun setStagedPhoto(token: String) {
        savedStateHandle[STAGED_PHOTO_KEY] = token
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        _uiState.value = state.copy(stagedPhoto = StagedPhoto(token))
    }

    private fun clearStagedPhoto() {
        savedStateHandle[STAGED_PHOTO_KEY] = null
        val state = _uiState.value as? PlantDetailUiState.Content ?: return
        _uiState.value = state.copy(stagedPhoto = null)
    }

    private fun clearPendingCapture() {
        savedStateHandle[PENDING_CAPTURE_KEY] = null
        _pendingCaptureToken.value = null
    }

    private fun savedStagedPhoto(): StagedPhoto? = savedStateHandle.get<String>(STAGED_PHOTO_KEY)
        ?.takeIf { token -> photoRepository.resolveStagedPhoto(token) != null }
        ?.let(::StagedPhoto)

    private fun mutatePhoto(action: suspend () -> Any?) {
        if (_photoUiState.value.isMutating) return
        _photoUiState.value = PlantPhotoUiState(isMutating = true)
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _photoUiState.value = PlantPhotoUiState(error = UiErrorCategory.PhotoImport)
                return@launch
            }
            _photoUiState.value = PlantPhotoUiState()
        }
    }

    private fun savedForm() = PlantForm(
        name = savedStateHandle[NAME_KEY] ?: "",
        scientificName = savedStateHandle[SCIENTIFIC_NAME_KEY] ?: "",
        location = savedStateHandle[LOCATION_KEY] ?: "",
        acquiredEpochDay = savedStateHandle[ACQUIRED_DATE_KEY],
        description = (savedStateHandle[DESCRIPTION_KEY] ?: "").take(MAX_PLANT_DESCRIPTION_LENGTH),
    )

    private fun persistForm(form: PlantForm) {
        savedStateHandle[NAME_KEY] = form.name
        savedStateHandle[SCIENTIFIC_NAME_KEY] = form.scientificName
        savedStateHandle[LOCATION_KEY] = form.location
        savedStateHandle[ACQUIRED_DATE_KEY] = form.acquiredEpochDay
        savedStateHandle[DESCRIPTION_KEY] = form.description
    }

    private fun clearCreationDraft() {
        listOf(
            NAME_KEY,
            SCIENTIFIC_NAME_KEY,
            LOCATION_KEY,
            ACQUIRED_DATE_KEY,
            DESCRIPTION_KEY,
            FAVORITE_KEY,
            STAGED_PHOTO_KEY,
            PENDING_CAPTURE_KEY,
        ).forEach { key -> savedStateHandle.remove<Any?>(key) }
        _pendingCaptureToken.value = null
    }

    private fun PlantForm.toDraft(name: String): PlantDraft = PlantDraft(
        name = name,
        scientificName = scientificName.trimToNull(),
        location = location.trimToNull(),
        acquiredEpochDay = acquiredEpochDay,
        description = description.take(MAX_PLANT_DESCRIPTION_LENGTH).trimToNull(),
    )

    private fun String.trimToNull(): String? = trim().takeIf(String::isNotEmpty)

    data class PlantPhotoUiState(
        val isMutating: Boolean = false,
        val error: UiErrorCategory? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(plantId: String?): PlantDetailViewModel
    }

    private companion object {
        const val PENDING_CAPTURE_KEY = "pending_capture_token"
        const val STAGED_PHOTO_KEY = "staged_photo_source"
        const val NAME_KEY = "create_name"
        const val SCIENTIFIC_NAME_KEY = "create_scientific_name"
        const val LOCATION_KEY = "create_location"
        const val ACQUIRED_DATE_KEY = "create_acquired_epoch_day"
        const val DESCRIPTION_KEY = "create_description"
        const val FAVORITE_KEY = "create_favorite"
    }
}
