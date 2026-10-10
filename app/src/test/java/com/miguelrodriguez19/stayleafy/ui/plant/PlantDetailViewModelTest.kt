package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_emitsContentForExistingPlant() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository().apply { observedPlant = plant }
        val viewModel = PlantDetailViewModel("plant-1", repository)

        assertEquals(
            PlantDetailUiState.Content(plant),
            viewModel.uiState.first { it is PlantDetailUiState.Content },
        )
    }

    @Test
    fun uiState_emitsNotFoundWhenPlantIsMissing() = runTest {
        val viewModel = PlantDetailViewModel("missing", FakePlantRepository())

        assertEquals(
            PlantDetailUiState.NotFound,
            viewModel.uiState.first { it is PlantDetailUiState.NotFound },
        )
    }

    @Test
    fun uiState_emitsErrorAndRetryRestartsObservation() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository().apply {
            observedPlant = plant
            failingObservePlantCalls += 1
        }
        val viewModel = PlantDetailViewModel("plant-1", repository)

        viewModel.uiState.first { it is PlantDetailUiState.Error }
        viewModel.retry()

        assertEquals(
            PlantDetailUiState.Content(plant),
            viewModel.uiState.first { it is PlantDetailUiState.Content },
        )
        assertTrue(repository.observePlantCalls >= 2)
    }

    @Test
    fun setFavorite_delegatesToRepository() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantDetailViewModel("plant-1", repository)

        viewModel.setFavorite(true)
        advanceUntilIdle()

        assertEquals(listOf(FavoriteCall("plant-1", true)), repository.favoriteCalls)
        assertNull(viewModel.mutationError.value)
    }

    @Test
    fun setFavorite_exposesMutationError() = runTest {
        val repository = FakePlantRepository().apply {
            favoriteError = IllegalStateException("favorite failed")
        }
        val viewModel = PlantDetailViewModel("plant-1", repository)

        viewModel.setFavorite(true)
        advanceUntilIdle()

        assertEquals(UiErrorCategory.Favorite, viewModel.mutationError.value)
    }

    @Test
    fun delete_successMarksPlantDeleted() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantDetailViewModel("plant-1", repository)

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(listOf("plant-1"), repository.deletedIds)
        assertTrue(viewModel.deleted.value)
        assertTrue(viewModel.isDeleting.value)
        assertNull(viewModel.mutationError.value)
    }

    @Test
    fun delete_whileAlreadyDeletingOnlyRunsOnce() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantDetailViewModel("plant-1", repository)

        viewModel.delete()
        viewModel.delete()
        advanceUntilIdle()

        assertEquals(listOf("plant-1"), repository.deletedIds)
    }

    @Test
    fun delete_failureExposesErrorAndResetsDeletingState() = runTest {
        val repository = FakePlantRepository().apply {
            deleteError = IllegalStateException("delete failed")
        }
        val viewModel = PlantDetailViewModel("plant-1", repository)

        viewModel.delete()
        advanceUntilIdle()

        assertFalse(viewModel.deleted.value)
        assertFalse(viewModel.isDeleting.value)
        assertEquals(UiErrorCategory.Delete, viewModel.mutationError.value)
    }
}
