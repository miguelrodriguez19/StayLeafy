package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.compose.runtime.Immutable
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.Plant

@Immutable
data class BulkCarePlantPreview(
    val id: String,
    val name: String,
    val photoPath: String?,
)

@Immutable
data class BulkCareCompletion(
    val careType: CareType,
    val performedEpochDay: Long,
    val result: BulkCareResult,
)

@Immutable
data class BulkCareUiState(
    val isSelectionMode: Boolean = false,
    val selectedPlantIds: List<String> = emptyList(),
    val selectedPlants: Map<String, BulkCarePlantPreview> = emptyMap(),
    val isFormOpen: Boolean = false,
    val isConfirming: Boolean = false,
    val careType: CareType? = null,
    val todayEpochDay: Long = 0L,
    val performedEpochDay: Long = 0L,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
    val completion: BulkCareCompletion? = null,
) {
    val selectedCount: Int get() = selectedPlantIds.size

    fun selectedPreviews(): List<BulkCarePlantPreview> = selectedPlantIds.mapNotNull(selectedPlants::get)
}

internal fun BulkCareUiState.addPlants(plants: List<Plant>): BulkCareUiState {
    val ids = selectedPlantIds.toMutableList()
    val previews = selectedPlants.toMutableMap()
    plants.forEach { plant ->
        if (plant.id !in ids) ids += plant.id
        previews[plant.id] = BulkCarePlantPreview(
            id = plant.id,
            name = plant.name,
            photoPath = plant.mainPhotoPath,
        )
    }
    return copy(selectedPlantIds = ids, selectedPlants = previews)
}
