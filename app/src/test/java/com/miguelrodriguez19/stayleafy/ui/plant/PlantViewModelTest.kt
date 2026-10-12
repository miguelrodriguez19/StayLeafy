package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_startsLoading() {
        val viewModel = PlantViewModel(FakePlantRepository())

        assertEquals(PlantListUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_emitsContentForPlants() = runTest {
        val plant = testPlant()
        val viewModel = PlantViewModel(FakePlantRepository(initialPlants = listOf(plant)))

        assertEquals(
            PlantListUiState.Content(listOf(plant)),
            viewModel.uiState.first { it is PlantListUiState.Content },
        )
    }

    @Test
    fun uiState_emitsUnfilteredEmptyState() = runTest {
        val viewModel = PlantViewModel(FakePlantRepository())

        assertEquals(
            PlantListUiState.Empty(isFiltered = false),
            viewModel.uiState.first { it is PlantListUiState.Empty },
        )
    }

    @Test
    fun uiState_emitsFilteredEmptyState() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantViewModel(repository)
        viewModel.updateQuery("Ficus")

        assertEquals(
            PlantListUiState.Empty(isFiltered = true),
            viewModel.uiState.first { it is PlantListUiState.Empty },
        )
        assertEquals(PlantListFilter(query = "Ficus"), repository.observedFilters.last())
    }

    @Test
    fun uiState_emitsErrorWhenRepositoryFails() = runTest {
        val repository = FakePlantRepository()
        repository.failingObserveCalls += 1
        val viewModel = PlantViewModel(repository)

        assertEquals(
            PlantListUiState.Error(),
            viewModel.uiState.first { it is PlantListUiState.Error },
        )
    }

    @Test
    fun retry_restartsObservationAfterError() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository(initialPlants = listOf(plant))
        repository.failingObserveCalls += 1
        val viewModel = PlantViewModel(repository)

        viewModel.uiState.first { it is PlantListUiState.Error }
        viewModel.retry()

        assertEquals(
            PlantListUiState.Content(listOf(plant)),
            viewModel.uiState.first { it is PlantListUiState.Content },
        )
        assertTrue(repository.observePlantsCalls >= 2)
    }

    @Test
    fun filters_updateAndCanBeCleared() = runTest {
        val repository = FakePlantRepository(initialPlants = listOf(testPlant()))
        val viewModel = PlantViewModel(repository)
        val stateCollection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.updateQuery(" Ficus ")
        viewModel.updateFavoritesOnly(true)
        viewModel.updateUpcomingOnly(true)
        viewModel.updateOverdueOnly(true)

        viewModel.uiState.first { it is PlantListUiState.Content }
        advanceUntilIdle()

        assertEquals(
            PlantListFilter(
                query = " Ficus ",
                favoritesOnly = true,
                upcomingOnly = true,
                overdueOnly = true,
            ),
            viewModel.filter.value,
        )
        assertEquals(viewModel.filter.value, repository.observedFilters.last())

        viewModel.clearFilters()

        assertEquals(PlantListFilter(), viewModel.filter.value)
        advanceUntilIdle()
        assertEquals(PlantListFilter(), repository.observedFilters.last())
        stateCollection.cancel()
    }

    @Test
    fun setFavorite_delegatesToRepository() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantViewModel(repository)

        viewModel.setFavorite("plant-1", favorite = true)
        advanceUntilIdle()

        assertEquals(listOf(FavoriteCall("plant-1", true)), repository.favoriteCalls)
        assertNull(viewModel.mutationError.value)
    }

    @Test
    fun setFavorite_exposesMutationErrorAndCanBeCleared() = runTest {
        val repository = FakePlantRepository()
        repository.favoriteError = IllegalStateException("favorite failed")
        val viewModel = PlantViewModel(repository)

        viewModel.setFavorite("plant-1", favorite = true)
        advanceUntilIdle()

        assertEquals(UiErrorCategory.Favorite, viewModel.mutationError.value)

        viewModel.clearMutationError()

        assertNull(viewModel.mutationError.value)
    }
}

internal class FakePlantRepository(
    initialPlants: List<Plant> = emptyList(),
) : PlantRepository {
    val plants = MutableStateFlow(initialPlants)
    val observedFilters = mutableListOf<PlantListFilter>()
    val favoriteCalls = mutableListOf<FavoriteCall>()
    val deletedIds = mutableListOf<String>()
    val createdDrafts = mutableListOf<PlantDraft>()
    val createCalls = mutableListOf<CreatePlantCall>()
    val updatedDrafts = mutableListOf<Pair<String, PlantDraft>>()
    val failingObserveCalls = mutableSetOf<Int>()
    var observePlantsCalls: Int = 0
        private set
    var favoriteError: Throwable? = null
    var deleteError: Throwable? = null
    var createError: Throwable? = null
    var updateError: Throwable? = null
    var createdId: String = "created-plant"
    var observedPlant: Plant? = null
    var observePlantError: Throwable? = null
    val failingObservePlantCalls = mutableSetOf<Int>()
    var observePlantCalls: Int = 0
        private set

    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> {
        observedFilters += filter
        observePlantsCalls += 1
        if (observePlantsCalls in failingObserveCalls) {
            return flow { throw IllegalStateException("load failed") }
        }
        return plants.map { it.toList() }
    }

    override fun observePlant(id: String): Flow<Plant?> = flow {
        observePlantCalls += 1
        if (observePlantCalls in failingObservePlantCalls) {
            throw IllegalStateException("load failed")
        }
        observePlantError?.let { throw it }
        emit(observedPlant)
    }

    override suspend fun createPlant(draft: PlantDraft): String {
        createError?.let { throw it }
        createdDrafts += draft
        return createdId
    }

    override suspend fun createPlant(
        draft: PlantDraft,
        favorite: Boolean,
        stagedPhotoToken: String?,
        notes: List<PlantNoteDraft>,
    ): String {
        createError?.let { throw it }
        createCalls += CreatePlantCall(draft, favorite, stagedPhotoToken, notes)
        return createdId
    }

    override suspend fun updatePlant(id: String, draft: PlantDraft) {
        updateError?.let { throw it }
        updatedDrafts += id to draft
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        favoriteError?.let { throw it }
        favoriteCalls += FavoriteCall(id, favorite)
    }

    override suspend fun deletePlant(id: String) {
        deleteError?.let { throw it }
        deletedIds += id
    }
}

internal data class FavoriteCall(val id: String, val favorite: Boolean)

internal data class CreatePlantCall(
    val draft: PlantDraft,
    val favorite: Boolean,
    val stagedPhotoToken: String?,
    val notes: List<PlantNoteDraft>,
)

internal fun testPlant(
    id: String = "plant-1",
    name: String = "Ficus",
    isFavorite: Boolean = false,
) = Plant(
    id = id,
    name = name,
    scientificName = "Ficus elastica",
    location = "Living room",
    acquiredEpochDay = 20_000L,
    description = "Description",
    mainPhotoPath = null,
    isFavorite = isFavorite,
    createdAt = 1_700_000_000_000L,
    updatedAt = 1_700_000_100_000L,
)
