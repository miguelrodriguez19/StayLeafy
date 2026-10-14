package com.miguelrodriguez19.stayleafy.ui.plant

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCapture
import com.miguelrodriguez19.stayleafy.data.photo.PhotoOrphanReconciliation
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.photo.StagedPhoto
import com.miguelrodriguez19.stayleafy.data.photo.StoredPhoto
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import java.io.File
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
    fun createMode_startsWithEditableContent() {
        val viewModel = createViewModel(plantId = null)

        assertEquals(
            PlantDetailUiState.Content(
                mode = PlantDetailMode.Create,
                plant = null,
                form = PlantForm(),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun description_isLimitedToFiveHundredCharacters() {
        val viewModel = createViewModel(plantId = null)

        viewModel.updateDescription("x".repeat(MAX_PLANT_DESCRIPTION_LENGTH + 1))

        val state = viewModel.uiState.value as PlantDetailUiState.Content
        assertEquals(MAX_PLANT_DESCRIPTION_LENGTH, state.form.description.length)
    }

    @Test
    fun existingPlant_emitsViewContentWithForm() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository().apply { observedPlant = plant }
        val viewModel = createViewModel("plant-1", repository)

        val state = viewModel.uiState.first { it is PlantDetailUiState.Content }
            as PlantDetailUiState.Content

        assertEquals(PlantDetailMode.View, state.mode)
        assertEquals(plant, state.plant)
        assertEquals(plant.toPlantForm(), state.form)
    }

    @Test
    fun missingPlant_emitsNotFound() = runTest {
        val viewModel = createViewModel("missing")

        assertEquals(
            PlantDetailUiState.NotFound,
            viewModel.uiState.first { it is PlantDetailUiState.NotFound },
        )
    }

    @Test
    fun loadError_canBeRetried() = runTest {
        val plant = testPlant()
        val repository = FakePlantRepository().apply {
            observedPlant = plant
            failingObservePlantCalls += 1
        }
        val viewModel = createViewModel("plant-1", repository)

        viewModel.uiState.first { it is PlantDetailUiState.Error }
        viewModel.retry()

        val state = viewModel.uiState.first { it is PlantDetailUiState.Content }
            as PlantDetailUiState.Content
        assertEquals(plant, state.plant)
        assertTrue(repository.observePlantCalls >= 2)
    }

    @Test
    fun setFavorite_delegatesToRepositoryForExistingPlant() = runTest {
        val repository = FakePlantRepository().apply { observedPlant = testPlant() }
        val viewModel = createViewModel("plant-1", repository)
        viewModel.uiState.first { it is PlantDetailUiState.Content }

        viewModel.setFavorite(true)
        advanceUntilIdle()

        assertEquals(listOf(FavoriteCall("plant-1", true)), repository.favoriteCalls)
        assertNull(viewModel.mutationError.value)
    }

    @Test
    fun create_savePassesNormalizedDraftFavoriteAndStagedPhotoWithoutNotes() = runTest {
        val repository = FakePlantRepository().apply {
            observedPlant = testPlant(id = "created-plant")
        }
        val photoRepository = FakePlantPhotoRepository()
        val viewModel = createViewModel(
            plantId = null,
            plantRepository = repository,
            photoRepository = photoRepository,
            savedStateHandle = SavedStateHandle(mapOf("staged_photo_source" to "photo-token")),
        )

        viewModel.updateName("  Ficus Living Room  ")
        viewModel.updateScientificName("  Ficus elastica  ")
        viewModel.updateLocation("  South window  ")
        viewModel.updateDescription("  Easy to care for  ")
        viewModel.setFavorite(true)
        assertEquals(
            StagedPhoto("photo-token"),
            (viewModel.uiState.value as PlantDetailUiState.Content).stagedPhoto,
        )

        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                CreatePlantCall(
                    draft = PlantDraft(
                        name = "Ficus Living Room",
                        scientificName = "Ficus elastica",
                        location = "South window",
                        description = "Easy to care for",
                    ),
                    favorite = true,
                    stagedPhotoToken = "photo-token",
                    notes = emptyList(),
                ),
            ),
            repository.createCalls,
        )
        val state = viewModel.uiState.value as PlantDetailUiState.Content
        assertEquals(PlantDetailMode.View, state.mode)
        assertEquals("created-plant", state.savedPlantId)
        assertNull(state.stagedPhoto)
    }

    @Test
    fun create_withBlankName_setsValidationErrorWithoutSaving() = runTest {
        val repository = FakePlantRepository()
        val viewModel = createViewModel(null, repository)

        viewModel.save()

        val state = viewModel.uiState.value as PlantDetailUiState.Content
        assertTrue(state.nameError)
        assertFalse(state.isSaving)
        assertTrue(repository.createCalls.isEmpty())
    }

    @Test
    fun create_restoresStagedPhotoTokenFromSavedStateHandle() {
        val viewModel = createViewModel(
            plantId = null,
            photoRepository = FakePlantPhotoRepository(),
            savedStateHandle = SavedStateHandle(mapOf("staged_photo_source" to "photo-token")),
        )

        val state = viewModel.uiState.value as PlantDetailUiState.Content
        assertEquals(StagedPhoto("photo-token"), state.stagedPhoto)
    }

    @Test
    fun edit_saveReturnsToViewAndDelegatesUpdatedDraft() = runTest {
        val repository = FakePlantRepository().apply { observedPlant = testPlant() }
        val viewModel = createViewModel("plant-1", repository)
        viewModel.uiState.first { it is PlantDetailUiState.Content }

        viewModel.beginEdit()
        viewModel.updateName("  New name ")
        viewModel.updateDescription("  Updated description ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "plant-1" to PlantDraft(
                    name = "New name",
                    scientificName = "Ficus elastica",
                    location = "Living room",
                    acquiredEpochDay = 20_000L,
                    description = "Updated description",
                ),
            ),
            repository.updatedDrafts,
        )
        val state = viewModel.uiState.value as PlantDetailUiState.Content
        assertEquals(PlantDetailMode.View, state.mode)
        assertFalse(state.isSaving)
    }

    @Test
    fun delete_successMarksPlantDeletedAndIgnoresRepeatedCalls() = runTest {
        val repository = FakePlantRepository()
        val viewModel = createViewModel("plant-1", repository)

        viewModel.delete()
        viewModel.delete()
        advanceUntilIdle()

        assertEquals(listOf("plant-1"), repository.deletedIds)
        assertTrue(viewModel.deleted.value)
        assertTrue(viewModel.isDeleting.value)
        assertNull(viewModel.mutationError.value)
    }

    @Test
    fun delete_failure_exposesErrorAndResetsDeletingState() = runTest {
        val repository = FakePlantRepository().apply {
            deleteError = IllegalStateException("delete failed")
        }
        val viewModel = createViewModel("plant-1", repository)

        viewModel.delete()
        advanceUntilIdle()

        assertFalse(viewModel.deleted.value)
        assertFalse(viewModel.isDeleting.value)
        assertEquals(UiErrorCategory.Delete, viewModel.mutationError.value)
    }

    private fun createViewModel(
        plantId: String?,
        plantRepository: FakePlantRepository = FakePlantRepository(),
        photoRepository: PlantPhotoRepository = FakePlantPhotoRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = PlantDetailViewModel(
        savedStateHandle = savedStateHandle,
        plantId = plantId,
        plantRepository = plantRepository,
        photoRepository = photoRepository,
    )
}

private class FakePlantPhotoRepository : PlantPhotoRepository {
    val stagedPhoto = StagedPhoto("photo-token", "pending/photo.jpg", File("/tmp/photo.jpg"))

    override fun prepareCameraCapture(): PendingPhotoCapture = error("unused")

    override suspend fun completeCameraCapture(plantId: String, token: String): StoredPhoto =
        error("unused")

    override suspend fun discardCameraCapture(token: String): Boolean = false

    override suspend fun importImage(plantId: String, sourceUri: Uri): StoredPhoto =
        error("unused")

    override suspend fun replacePhoto(plantId: String, sourceUri: Uri): StoredPhoto =
        error("unused")

    override suspend fun stageImage(sourceUri: Uri): StagedPhoto = stagedPhoto

    override suspend fun commitStagedPhoto(token: String): StoredPhoto =
        StoredPhoto("plant_photos/photo.jpg", File("/tmp/photo.jpg"))

    override suspend fun discardStagedPhoto(token: String): Boolean = true

    override fun resolveStagedPhoto(token: String): File? =
        stagedPhoto.file.takeIf { token == stagedPhoto.token }

    override suspend fun removePhoto(plantId: String): Boolean = true

    override fun resolvePhoto(relativePath: String): File? = null

    override suspend fun reconcileOrphans(): PhotoOrphanReconciliation =
        PhotoOrphanReconciliation(emptySet(), emptySet())
}
