package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PlantNoteEditorState(
    val noteId: String?,
    val plantName: String? = null,
    val plantScientificName: String? = null,
    val plantPhotoPath: String? = null,
    val body: String = "",
    val initialBody: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val notFound: Boolean = false,
    val loadError: UiErrorCategory? = null,
    val saveError: UiErrorCategory? = null,
    val bodyError: Boolean = false,
)

const val MAX_NOTE_BODY_LENGTH = 500

@HiltViewModel(assistedFactory = PlantNoteEditorViewModel.Factory::class)
class PlantNoteEditorViewModel @AssistedInject constructor(
    @Assisted("plantId") private val plantId: String,
    @Assisted("noteId") private val noteId: String?,
    private val repository: PlantNoteRepository,
    private val plantRepository: PlantRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlantNoteEditorState(noteId = noteId))
    val state: StateFlow<PlantNoteEditorState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadNote()
    }

    fun updateBody(body: String) {
        _state.value = _state.value.copy(
            body = body.take(MAX_NOTE_BODY_LENGTH),
            bodyError = false,
        )
    }

    fun retryLoad() {
        loadNote()
    }

    fun save() {
        if (_state.value.isLoading || _state.value.notFound) return
        val body = _state.value.body.trim()
        if (body.isEmpty()) {
            _state.value = _state.value.copy(bodyError = true)
            return
        }
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            try {
                if (noteId == null) {
                    repository.createNote(plantId, PlantNoteDraft(body))
                } else {
                    repository.updateNote(plantId, noteId, PlantNoteDraft(body))
                }
                _state.value = _state.value.copy(isSaving = false, saved = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                val plantMissing = try {
                    plantRepository.observePlant(plantId).first() == null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }
                _state.value = _state.value.copy(
                    isSaving = false,
                    notFound = plantMissing,
                    saveError = UiErrorCategory.NoteSave.takeUnless { plantMissing },
                )
            }
        }
    }

    private fun loadNote() {
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, loadError = null, notFound = false)
        loadJob = viewModelScope.launch {
            try {
                val plant = plantRepository.observePlant(plantId).first()
                if (plant == null) {
                    _state.value = _state.value.copy(isLoading = false, notFound = true)
                } else if (noteId == null) {
                    _state.value = _state.value.copy(
                        plantName = plant.name,
                        plantScientificName = plant.scientificName,
                        plantPhotoPath = plant.mainPhotoPath,
                        isLoading = false,
                        notFound = false,
                    )
                } else {
                    val note = repository.observeNote(plantId, noteId).first()
                    _state.value = if (note == null) {
                        _state.value.copy(isLoading = false, notFound = true)
                    } else {
                        _state.value.copy(
                            plantName = plant.name,
                            plantScientificName = plant.scientificName,
                            plantPhotoPath = plant.mainPhotoPath,
                            isLoading = false,
                            body = note.body.take(MAX_NOTE_BODY_LENGTH),
                            initialBody = note.body.take(MAX_NOTE_BODY_LENGTH),
                            notFound = false,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    loadError = UiErrorCategory.NoteLoad,
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("plantId") plantId: String,
            @Assisted("noteId") noteId: String?,
        ): PlantNoteEditorViewModel
    }
}
