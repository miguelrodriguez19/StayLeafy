package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.Plant
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlantDetailViewModel.Factory::class)
class PlantDetailViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val plantRepository: PlantRepository,
) : ViewModel() {

    private val _mutationError = MutableStateFlow<UiErrorCategory?>(null)
    val mutationError: StateFlow<UiErrorCategory?> = _mutationError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _reload = MutableStateFlow(0)

    val uiState: StateFlow<PlantDetailUiState> = _reload.flatMapLatest {
        plantRepository.observePlant(plantId)
            .map<Plant?, PlantDetailUiState> { plant ->
                plant?.let(PlantDetailUiState::Content) ?: PlantDetailUiState.NotFound
            }
            .onStart { emit(PlantDetailUiState.Loading) }
            .catch { emit(PlantDetailUiState.Error()) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantDetailUiState.Loading)

    fun retry() {
        _reload.value += 1
    }

    fun setFavorite(favorite: Boolean) {
        viewModelScope.launch {
            _mutationError.value = null
            runCatching { plantRepository.setFavorite(plantId, favorite) }
                .onFailure { _mutationError.value = UiErrorCategory.Favorite }
        }
    }

    fun delete() {
        if (_isDeleting.value) return
        _isDeleting.value = true
        _mutationError.value = null
        viewModelScope.launch {
            runCatching { plantRepository.deletePlant(plantId) }
                .onSuccess { _deleted.value = true }
                .onFailure {
                    _mutationError.value = UiErrorCategory.Delete
                    _isDeleting.value = false
                }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(plantId: String): PlantDetailViewModel
    }
}
