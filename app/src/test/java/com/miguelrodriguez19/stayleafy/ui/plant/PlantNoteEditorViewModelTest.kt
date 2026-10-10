package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantNoteEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun create_blankBody_setsValidationErrorWithoutSaving() = runTest {
        val repository = FakeEditorNotesRepository()
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            null,
            repository,
            FakeEditorPlantRepository(),
        )
        advanceUntilIdle()

        viewModel.save()

        assertTrue(viewModel.state.value.bodyError)
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(repository.createdDrafts.isEmpty())
    }

    @Test
    fun create_saveTrimsBodyAndMarksSaved() = runTest {
        val repository = FakeEditorNotesRepository()
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            null,
            repository,
            FakeEditorPlantRepository(),
        )
        advanceUntilIdle()

        viewModel.updateBody("  New leaf appeared.  ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf(PlantNoteDraft("New leaf appeared.")), repository.createdDrafts)
        assertTrue(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun create_saveFailure_exposesLocalizedCategory() = runTest {
        val repository = FakeEditorNotesRepository().apply {
            createError = IllegalStateException("save failed")
        }
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            null,
            repository,
            FakeEditorPlantRepository(),
        )
        advanceUntilIdle()
        viewModel.updateBody("Observation")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(UiErrorCategory.NoteSave, viewModel.state.value.saveError)
        assertFalse(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun edit_existingNote_loadsBodyAndSavesUpdatedDraft() = runTest {
        val note = note()
        val repository = FakeEditorNotesRepository(note = note)
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            "note-1",
            repository,
            FakeEditorPlantRepository(),
        )
        advanceUntilIdle()

        assertEquals("Original observation", viewModel.state.value.body)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.notFound)

        viewModel.updateBody("  Updated observation  ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("plant-1", "note-1", PlantNoteDraft("Updated observation"))),
            repository.updatedDrafts,
        )
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun edit_missingNote_setsNotFound() = runTest {
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            "missing",
            FakeEditorNotesRepository(),
            FakeEditorPlantRepository(),
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.notFound)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(null, viewModel.state.value.loadError)
    }

    @Test
    fun edit_loadError_canBeRetried() = runTest {
        val repository = FakeEditorNotesRepository(note = note()).apply {
            failingObserveCalls += 1
        }
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            "note-1",
            repository,
            FakeEditorPlantRepository(),
        )

        advanceUntilIdle()
        assertEquals(UiErrorCategory.NoteLoad, viewModel.state.value.loadError)

        viewModel.retryLoad()
        advanceUntilIdle()

        assertEquals("Original observation", viewModel.state.value.body)
        assertEquals(null, viewModel.state.value.loadError)
        assertTrue(repository.observeCalls >= 2)
    }

    @Test
    fun edit_saveFailure_exposesLocalizedCategory() = runTest {
        val repository = FakeEditorNotesRepository(note = note()).apply {
            updateError = IllegalStateException("update failed")
        }
        val viewModel = PlantNoteEditorViewModel(
            "plant-1",
            "note-1",
            repository,
            FakeEditorPlantRepository(),
        )
        advanceUntilIdle()
        viewModel.updateBody("Updated")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(UiErrorCategory.NoteSave, viewModel.state.value.saveError)
        assertFalse(viewModel.state.value.saved)
    }

    @Test
    fun create_missingPlant_setsNotFound() = runTest {
        val viewModel = PlantNoteEditorViewModel(
            "missing",
            null,
            FakeEditorNotesRepository(),
            FakeEditorPlantRepository(plant = null),
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.notFound)
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun note() = PlantNote(
        id = "note-1",
        plantId = "plant-1",
        body = "Original observation",
        createdAt = 1L,
        updatedAt = 2L,
    )
}

private class FakeEditorPlantRepository(
    private val plant: Plant? = editorPlant(),
) : PlantRepository {
    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> = flowOf(emptyList())

    override fun observePlant(id: String): Flow<Plant?> =
        flowOf(plant?.takeIf { it.id == id })

    override suspend fun createPlant(draft: PlantDraft): String = error("unused")

    override suspend fun updatePlant(id: String, draft: PlantDraft) = Unit

    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

    override suspend fun deletePlant(id: String) = Unit
}

private fun editorPlant() = Plant(
    id = "plant-1",
    name = "Monstera",
    scientificName = null,
    location = null,
    acquiredEpochDay = null,
    description = null,
    mainPhotoPath = null,
    isFavorite = false,
    createdAt = 1L,
    updatedAt = 1L,
)

private class FakeEditorNotesRepository(
    private val note: PlantNote? = null,
) : PlantNoteRepository {
    val createdDrafts = mutableListOf<PlantNoteDraft>()
    val updatedDrafts = mutableListOf<Triple<String, String, PlantNoteDraft>>()
    val failingObserveCalls = mutableSetOf<Int>()
    var observeCalls = 0
        private set
    var createError: Throwable? = null
    var updateError: Throwable? = null

    override fun observeNotes(plantId: String): Flow<List<PlantNote>> = flow { emit(emptyList()) }

    override fun observeNote(plantId: String, noteId: String): Flow<PlantNote?> = flow {
        observeCalls += 1
        if (observeCalls in failingObserveCalls) error("load failed")
        emit(note?.takeIf { it.plantId == plantId && it.id == noteId })
    }

    override suspend fun createNote(plantId: String, draft: PlantNoteDraft): String {
        createError?.let { throw it }
        createdDrafts += draft
        return "created-note"
    }

    override suspend fun updateNote(plantId: String, noteId: String, draft: PlantNoteDraft) {
        updateError?.let { throw it }
        updatedDrafts += Triple(plantId, noteId, draft)
    }

    override suspend fun deleteNote(plantId: String, noteId: String) = Unit
}
