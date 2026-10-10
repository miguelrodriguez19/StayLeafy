package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlantViewModel @Inject constructor(
    private val plantRepository: PlantRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(PlantListFilter())
    val filter: StateFlow<PlantListFilter> = _filter.asStateFlow()

    private val _reload = MutableStateFlow(0)
    private val _mutationError = MutableStateFlow<UiErrorCategory?>(null)
    val mutationError: StateFlow<UiErrorCategory?> = _mutationError.asStateFlow()

    val uiState: StateFlow<PlantListUiState> = combine(_filter, _reload) { filter, _ -> filter }
        .flatMapLatest { filter ->
            plantRepository.observePlants(filter)
                .map<List<Plant>, PlantListUiState> { plants ->
                    if (plants.isEmpty()) {
                        PlantListUiState.Empty(filter.isFiltered)
                    } else {
                        PlantListUiState.Content(plants)
                    }
                }
                .onStart { emit(PlantListUiState.Loading) }
                .catch { emit(PlantListUiState.Error()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantListUiState.Loading)

    fun updateQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun updateFavoritesOnly(favoritesOnly: Boolean) {
        _filter.update { it.copy(favoritesOnly = favoritesOnly) }
    }

    fun clearFilters() {
        _filter.value = PlantListFilter()
    }

    fun retry() {
        _reload.update { it + 1 }
    }

    fun clearMutationError() {
        _mutationError.value = null
    }

    fun setFavorite(plantId: String, favorite: Boolean) {
        viewModelScope.launch {
            _mutationError.value = null
            runCatching { plantRepository.setFavorite(plantId, favorite) }
                .onFailure { _mutationError.value = UiErrorCategory.Favorite }
        }
    }
}
