package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.model.PlantNote
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlantNotesUiState {
    data object Loading : PlantNotesUiState
    data class Error(val category: UiErrorCategory = UiErrorCategory.NotesLoad) : PlantNotesUiState
    data object Empty : PlantNotesUiState
    data class Content(val notes: List<PlantNote>) : PlantNotesUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlantNotesViewModel.Factory::class)
class PlantNotesViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val repository: PlantNoteRepository,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val _mutationError = MutableStateFlow<UiErrorCategory?>(null)
    val mutationError: StateFlow<UiErrorCategory?> = _mutationError.asStateFlow()
    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    val uiState: StateFlow<PlantNotesUiState> = reload.flatMapLatest {
        repository.observeNotes(plantId)
            .map<List<PlantNote>, PlantNotesUiState> { notes ->
                if (notes.isEmpty()) PlantNotesUiState.Empty else PlantNotesUiState.Content(notes)
            }
            .onStart { emit(PlantNotesUiState.Loading) }
            .catch { emit(PlantNotesUiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantNotesUiState.Loading)

    fun retry() {
        reload.value += 1
    }

    fun delete(noteId: String) {
        if (_isMutating.value) return
        _isMutating.value = true
        _mutationError.value = null
        viewModelScope.launch {
            runCatching { repository.deleteNote(plantId, noteId) }
                .onFailure { _mutationError.value = UiErrorCategory.NoteDelete }
            _isMutating.value = false
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(plantId: String): PlantNotesViewModel
    }
}
