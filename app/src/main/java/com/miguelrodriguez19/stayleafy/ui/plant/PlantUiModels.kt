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

    data class Content(val plant: Plant) : PlantDetailUiState
}

enum class PlantEditorMode {
    Create,
    Edit,
}

@Immutable
data class PlantEditorForm(
    val displayName: String = "",
    val commonName: String = "",
    val scientificName: String = "",
    val location: String = "",
    val acquiredEpochDay: Long? = null,
    val description: String = "",
)

@Immutable
data class PlantEditorUiState(
    val mode: PlantEditorMode,
    val isLoading: Boolean,
    val form: PlantEditorForm,
    val displayNameError: Boolean = false,
    val loadError: UiErrorCategory? = null,
    val saveError: UiErrorCategory? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val notFound: Boolean = false,
)

internal fun Plant.toEditorForm(): PlantEditorForm = PlantEditorForm(
    displayName = displayName,
    commonName = commonName.orEmpty(),
    scientificName = scientificName.orEmpty(),
    location = location.orEmpty(),
    acquiredEpochDay = acquiredEpochDay,
    description = description.orEmpty(),
)

internal val PlantListFilter.isFiltered: Boolean
    get() = query.isNotBlank() || favoritesOnly
