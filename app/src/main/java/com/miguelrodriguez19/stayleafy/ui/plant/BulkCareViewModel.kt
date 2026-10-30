package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.Plant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

@HiltViewModel
class BulkCareViewModel @Inject constructor(
    private val careEventRepository: CareEventRepository,
    private val epochMillisProvider: EpochMillisProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val dataOperations: DataOperationCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BulkCareUiState())
    val uiState: StateFlow<BulkCareUiState> = _uiState.asStateFlow()
    private var selectionGeneration = 0
    private var dataGeneration = dataOperations.state.value.generation

    init {
        viewModelScope.launch {
            dataOperations.state.collect { state ->
                if (state.generation != dataGeneration) {
                    dataGeneration = state.generation
                    leavePlants()
                }
            }
        }
    }

    fun startSelection() {
        if (_uiState.value.isSubmitting) return
        val today = todayEpochDay()
        _uiState.value = BulkCareUiState(
            isSelectionMode = true,
            todayEpochDay = today,
            performedEpochDay = today,
        )
    }

    fun togglePlant(plant: Plant) {
        _uiState.update { state ->
            if (!state.isSelectionMode || state.isFormOpen || state.isSubmitting) return@update state
            if (plant.id in state.selectedPlantIds) {
                state.copy(
                    selectedPlantIds = state.selectedPlantIds.filterNot { it == plant.id },
                    selectedPlants = state.selectedPlants - plant.id,
                )
            } else {
                state.addPlants(listOf(plant))
            }
        }
    }

    fun selectVisiblePlants(plants: List<Plant>) {
        _uiState.update { state ->
            if (!state.isSelectionMode || state.isFormOpen || state.isSubmitting) state
            else state.addPlants(plants)
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            if (state.isSubmitting) state else state.copy(
                selectedPlantIds = emptyList(),
                selectedPlants = emptyMap(),
            )
        }
    }

    fun exitSelection() {
        if (_uiState.value.isSubmitting) return
        _uiState.value = BulkCareUiState()
    }

    /** Drops transient UI and suppresses a completion message if navigation leaves the Plants tab. */
    fun leavePlants() {
        selectionGeneration++
        _uiState.value = BulkCareUiState()
    }

    fun continueToForm() {
        _uiState.update { state ->
            if (state.selectedPlantIds.isEmpty() || state.isSubmitting) state
            else state.copy(isFormOpen = true, isConfirming = false, hasError = false)
        }
    }

    fun returnToSelection() {
        _uiState.update { state ->
            if (state.isSubmitting) state else state.copy(
                isFormOpen = false,
                isConfirming = false,
                hasError = false,
            )
        }
    }

    fun openConfirmation() {
        _uiState.update { state ->
            if (!state.isFormOpen || state.isSubmitting || state.careType == null || state.selectedPlantIds.isEmpty()) {
                state
            } else {
                state.copy(isConfirming = true)
            }
        }
    }

    fun cancelConfirmation() {
        _uiState.update { state ->
            if (state.isSubmitting) state else state.copy(isConfirming = false)
        }
    }

    fun setCareType(careType: CareType) {
        _uiState.update { state ->
            if (state.isSubmitting) state else state.copy(careType = careType, hasError = false)
        }
    }

    fun setPerformedEpochDay(epochDay: Long) {
        _uiState.update { state ->
            if (epochDay > state.todayEpochDay) return@update state
            if (state.isSubmitting) state else state.copy(performedEpochDay = epochDay, hasError = false)
        }
    }

    fun confirmSubmission() {
        val state = _uiState.value
        val careType = state.careType ?: return
        if (!state.isFormOpen || !state.isConfirming || state.selectedPlantIds.isEmpty() || state.isSubmitting) return
        val plantIds = state.selectedPlantIds.toList()
        val generation = selectionGeneration
        _uiState.value = state.copy(isConfirming = false, isSubmitting = true, hasError = false)
        viewModelScope.launch {
            try {
                val result = careEventRepository.recordBulkCare(
                    plantIds = plantIds,
                    careType = careType,
                    performedEpochDay = state.performedEpochDay,
                )
                if (generation == selectionGeneration) {
                    _uiState.value = BulkCareUiState(
                        completion = BulkCareCompletion(
                            careType = careType,
                            performedEpochDay = state.performedEpochDay,
                            result = result,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (generation == selectionGeneration) {
                    _uiState.update { current -> current.copy(isSubmitting = false, hasError = true) }
                }
            }
        }
    }

    fun dismissCompletion() {
        _uiState.update { state -> if (state.completion == null) state else state.copy(completion = null) }
    }

    private fun todayEpochDay(): Long = Instant.ofEpochMilli(epochMillisProvider.now())
        .atZone(zoneIdProvider.currentZoneId())
        .toLocalDate()
        .toEpochDay()
}
