package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlantEditorViewModel.Factory::class)
class PlantEditorViewModel @AssistedInject constructor(
    @Assisted private val plantId: String?,
    private val plantRepository: PlantRepository,
) : ViewModel() {

    private val mode = if (plantId == null) PlantEditorMode.Create else PlantEditorMode.Edit
    private val _state = MutableStateFlow(
        PlantEditorUiState(
            mode = mode,
            isLoading = plantId != null,
            form = PlantEditorForm(),
        ),
    )
    val uiState: StateFlow<PlantEditorUiState> = _state.asStateFlow()

    init {
        if (plantId != null) loadPlant(plantId)
    }

    private fun loadPlant(id: String) {
        viewModelScope.launch {
            runCatching { plantRepository.observePlant(id).first() }
                .onSuccess { plant ->
                    _state.value = if (plant == null) {
                        _state.value.copy(isLoading = false, notFound = true)
                    } else {
                        _state.value.copy(isLoading = false, form = plant.toEditorForm())
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        loadError = UiErrorCategory.Load,
                    )
                }
        }
    }

    fun updateDisplayName(value: String) = updateForm { copy(displayName = value) }
    fun updateCommonName(value: String) = updateForm { copy(commonName = value) }
    fun updateScientificName(value: String) = updateForm { copy(scientificName = value) }
    fun updateLocation(value: String) = updateForm { copy(location = value) }
    fun updateDescription(value: String) = updateForm { copy(description = value) }
    fun updateAcquiredEpochDay(value: Long?) = updateForm { copy(acquiredEpochDay = value) }

    private fun updateForm(update: PlantEditorForm.() -> PlantEditorForm) {
        _state.value = _state.value.copy(
            form = update(_state.value.form),
            displayNameError = false,
            saveError = null,
        )
    }

    fun save() {
        val current = _state.value
        val displayName = current.form.displayName.trim()
        if (displayName.isBlank()) {
            _state.value = current.copy(displayNameError = true)
            return
        }
        if (current.isSaving || current.isLoading || current.notFound) return

        val draft = PlantDraft(
            displayName = displayName,
            commonName = current.form.commonName.trimToNull(),
            scientificName = current.form.scientificName.trimToNull(),
            location = current.form.location.trimToNull(),
            acquiredEpochDay = current.form.acquiredEpochDay,
            description = current.form.description.trimToNull(),
        )
        _state.value = _state.value.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            runCatching {
                if (mode == PlantEditorMode.Create) {
                    plantRepository.createPlant(draft)
                } else {
                    plantRepository.updatePlant(checkNotNull(plantId), draft)
                }
            }
                .onSuccess { _state.value = _state.value.copy(isSaving = false, saved = true) }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        saveError = UiErrorCategory.Save,
                    )
                }
        }
    }

    fun retryLoad() {
        if (plantId != null) {
            _state.value = _state.value.copy(isLoading = true, loadError = null, notFound = false)
            loadPlant(plantId)
        }
    }

    private fun String.trimToNull(): String? = trim().takeIf(String::isNotEmpty)

    @AssistedFactory
    interface Factory {
        fun create(plantId: String?): PlantEditorViewModel
    }
}
