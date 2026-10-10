package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createMode_startsWithEmptyForm() {
        val viewModel = PlantEditorViewModel(null, FakePlantRepository())

        assertEquals(PlantEditorMode.Create, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(PlantEditorForm(), viewModel.uiState.value.form)
    }

    @Test
    fun save_withBlankDisplayNameSetsValidationError() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantEditorViewModel(null, repository)

        viewModel.save()

        assertTrue(viewModel.uiState.value.displayNameError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(repository.createdDrafts.isEmpty())
    }

    @Test
    fun create_saveTrimsFieldsAndMarksSaved() = runTest {
        val repository = FakePlantRepository()
        val viewModel = PlantEditorViewModel(null, repository)

        viewModel.updateDisplayName("  Ficus Living Room  ")
        viewModel.updateCommonName("  Rubber plant  ")
        viewModel.updateScientificName("   ")
        viewModel.updateLocation("  South window ")
        viewModel.updateAcquiredEpochDay(20_000L)
        viewModel.updateDescription("  Easy to care for  ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PlantDraft(
                    displayName = "Ficus Living Room",
                    commonName = "Rubber plant",
                    scientificName = null,
                    location = "South window",
                    acquiredEpochDay = 20_000L,
                    description = "Easy to care for",
                ),
            ),
            repository.createdDrafts,
        )
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.saved)
        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun create_saveFailureExposesSaveError() = runTest {
        val repository = FakePlantRepository().apply {
            createError = IllegalStateException("save failed")
        }
        val viewModel = PlantEditorViewModel(null, repository)
        viewModel.updateDisplayName("Ficus")

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.saved)
        assertEquals(UiErrorCategory.Save, viewModel.uiState.value.saveError)
        assertEquals("Ficus", viewModel.uiState.value.form.displayName)
    }

    @Test
    fun editMode_loadsExistingPlantIntoForm() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository().apply { observedPlant = plant }
        val viewModel = PlantEditorViewModel("plant-1", repository)

        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(PlantEditorMode.Edit, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            PlantEditorForm(
                displayName = "Ficus",
                commonName = "Rubber plant",
                scientificName = "Ficus elastica",
                location = "Living room",
                acquiredEpochDay = 20_000L,
                description = "Description",
            ),
            viewModel.uiState.value.form,
        )
    }

    @Test
    fun editMode_missingPlantSetsNotFound() = runTest {
        val viewModel = PlantEditorViewModel("missing", FakePlantRepository())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.notFound)
        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun editMode_loadFailureCanBeRetried() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository().apply {
            observedPlant = plant
            failingObservePlantCalls += 1
        }
        val viewModel = PlantEditorViewModel("plant-1", repository)

        advanceUntilIdle()
        assertEquals(UiErrorCategory.Load, viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.retryLoad()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Ficus", viewModel.uiState.value.form.displayName)
        assertTrue(repository.observePlantCalls >= 2)
    }

    @Test
    fun edit_saveDelegatesUpdatedDraft() = runTest {
        val repository = FakePlantRepository().apply { observedPlant = testPlant() }
        val viewModel = PlantEditorViewModel("plant-1", repository)
        advanceUntilIdle()

        viewModel.updateDisplayName("  New name ")
        viewModel.updateDescription("  Updated description ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "plant-1" to PlantDraft(
                    displayName = "New name",
                    commonName = "Rubber plant",
                    scientificName = "Ficus elastica",
                    location = "Living room",
                    acquiredEpochDay = 20_000L,
                    description = "Updated description",
                ),
            ),
            repository.updatedDrafts,
        )
        assertTrue(viewModel.uiState.value.saved)
    }
}
