package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.compose.runtime.Immutable
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantListFilter

enum class UiErrorCategory {
    Load,
    Save,
    Favorite,
    Delete,
    NotesLoad,
    NoteLoad,
    NoteSave,
    NoteDelete,
    PhotoImport,
    PhotoDelete,
}

sealed interface PlantListUiState {
    data object Loading : PlantListUiState

    data class Error(val category: UiErrorCategory = UiErrorCategory.Load) : PlantListUiState

    data class Empty(val isFiltered: Boolean) : PlantListUiState

    data class Content(val plants: List<Plant>) : PlantListUiState
}

sealed interface PlantDetailUiState {
    data object Loading : PlantDetailUiState

    data class Error(val category: UiErrorCategory = UiErrorCategory.Load) : PlantDetailUiState

    data object NotFound : PlantDetailUiState

    data class Content(
        val mode: PlantDetailMode,
        val plant: Plant?,
        val form: PlantForm,
        val nameError: Boolean = false,
        val saveError: UiErrorCategory? = null,
        val isSaving: Boolean = false,
        val stagedFavorite: Boolean = false,
        val stagedNotes: List<StagedPlantNote> = emptyList(),
        val stagedPhoto: StagedPhoto? = null,
        val savedPlantId: String? = null,
    ) : PlantDetailUiState
}

enum class PlantDetailMode {
    Create,
    View,
    Edit,
}

@Immutable
data class PlantForm(
    val name: String = "",
    val scientificName: String = "",
    val location: String = "",
    val acquiredEpochDay: Long? = null,
    val description: String = "",
)

@Immutable
data class StagedPlantNote(
    val id: String,
    val body: String,
)

@Immutable
data class StagedPhoto(val token: String)

internal fun Plant.toPlantForm(): PlantForm = PlantForm(
    name = name,
    scientificName = scientificName.orEmpty(),
    location = location.orEmpty(),
    acquiredEpochDay = acquiredEpochDay,
    description = description.orEmpty(),
)

internal val PlantListFilter.isFiltered: Boolean
    get() = query.isNotBlank() || favoritesOnly
