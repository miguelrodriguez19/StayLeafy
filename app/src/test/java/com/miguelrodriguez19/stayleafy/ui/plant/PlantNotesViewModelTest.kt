package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantNotesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_startsLoading() {
        val viewModel = PlantNotesViewModel("plant-1", FakeNotesRepository())

        assertEquals(PlantNotesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_emitsContent() = runTest {
        val note = note()
        val viewModel = PlantNotesViewModel(
            plantId = "plant-1",
            repository = FakeNotesRepository(listOf(note)),
        )

        assertEquals(
            PlantNotesUiState.Content(listOf(note)),
            viewModel.uiState.firstContent(),
        )
    }

    @Test
    fun uiState_emitsEmptyWhenThereAreNoNotes() = runTest {
        val viewModel = PlantNotesViewModel("plant-1", FakeNotesRepository())

        assertEquals(PlantNotesUiState.Empty, viewModel.uiState.firstEmpty())
    }

    @Test
    fun uiState_emitsErrorAndRetryRestartsObservation() = runTest {
        val repository = FakeNotesRepository(listOf(note())).apply {
            failingObserveCalls += 1
        }
        val viewModel = PlantNotesViewModel("plant-1", repository)

        assertEquals(PlantNotesUiState.Error(), viewModel.uiState.firstError())

        viewModel.retry()

        assertEquals(
            PlantNotesUiState.Content(listOf(note())),
            viewModel.uiState.firstContent(),
        )
        assertTrue(repository.observeCalls >= 2)
    }

    @Test
    fun delete_delegatesToRepositoryAndClearsMutationError() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = PlantNotesViewModel("plant-1", repository)

        viewModel.delete("note-1")
        advanceUntilIdle()

        assertEquals(listOf("plant-1" to "note-1"), repository.deletedNotes)
        assertFalse(viewModel.isMutating.value)
        assertEquals(null, viewModel.mutationError.value)
    }

    @Test
    fun delete_failure_exposesLocalizedCategoryAndResetsMutationState() = runTest {
        val repository = FakeNotesRepository().apply {
            deleteError = IllegalStateException("delete failed")
        }
        val viewModel = PlantNotesViewModel("plant-1", repository)

        viewModel.delete("note-1")
        advanceUntilIdle()

        assertEquals(UiErrorCategory.NoteDelete, viewModel.mutationError.value)
        assertFalse(viewModel.isMutating.value)
    }

    private fun note() = PlantNote(
        id = "note-1",
        plantId = "plant-1",
        body = "New leaf appeared",
        createdAt = 10L,
        updatedAt = 10L,
    )
}

private class FakeNotesRepository(
    initialNotes: List<PlantNote> = emptyList(),
) : PlantNoteRepository {
    val notes = MutableStateFlow(initialNotes)
    val deletedNotes = mutableListOf<Pair<String, String>>()
    val failingObserveCalls = mutableSetOf<Int>()
    var observeCalls = 0
        private set
    var deleteError: Throwable? = null

    override fun observeNotes(plantId: String): Flow<List<PlantNote>> = flow {
        observeCalls += 1
        if (observeCalls in failingObserveCalls) error("load failed")
        emitAll(notes)
    }

    override fun observeNote(plantId: String, noteId: String): Flow<PlantNote?> = flow {
        emit(notes.value.firstOrNull { it.plantId == plantId && it.id == noteId })
    }

    override suspend fun createNote(plantId: String, draft: PlantNoteDraft): String = "created-note"

    override suspend fun updateNote(plantId: String, noteId: String, draft: PlantNoteDraft) = Unit

    override suspend fun deleteNote(plantId: String, noteId: String) {
        deleteError?.let { throw it }
        deletedNotes += plantId to noteId
    }
}

private suspend fun <T> Flow<T>.firstContent(): T = first { it is PlantNotesUiState.Content }

private suspend fun <T> Flow<T>.firstEmpty(): T = first { it is PlantNotesUiState.Empty }

private suspend fun <T> Flow<T>.firstError(): T = first { it is PlantNotesUiState.Error }
