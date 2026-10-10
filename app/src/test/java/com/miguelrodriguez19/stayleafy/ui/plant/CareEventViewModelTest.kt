package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantCareViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_startsLoading() {
        assertEquals(
            CareEventsUiState.Loading,
            PlantCareViewModel("plant-1", FakeCareRepository()).uiState.value,
        )
    }

    @Test
    fun uiState_emitsReverseOrderedContent() = runTest {
        val events = listOf(event("new", 20L), event("old", 10L))
        val viewModel = PlantCareViewModel("plant-1", FakeCareRepository(events))

        assertEquals(
            CareEventsUiState.Content(events),
            viewModel.uiState.first { it is CareEventsUiState.Content },
        )
    }

    @Test
    fun uiState_emitsEmpty() = runTest {
        val viewModel = PlantCareViewModel("plant-1", FakeCareRepository())

        assertEquals(CareEventsUiState.Empty, viewModel.uiState.first { it is CareEventsUiState.Empty })
    }

    @Test
    fun uiState_errorCanBeRetried() = runTest {
        val repository = FakeCareRepository(listOf(event())).apply { failingObserveCalls += 1 }
        val viewModel = PlantCareViewModel("plant-1", repository)

        assertTrue(viewModel.uiState.first { it is CareEventsUiState.Error } is CareEventsUiState.Error)
        viewModel.retry()

        assertEquals(
            CareEventsUiState.Content(listOf(event())),
            viewModel.uiState.first { it is CareEventsUiState.Content },
        )
        assertTrue(repository.observeCalls >= 2)
    }

    @Test
    fun delete_ignoresRepeatedCallsAndReportsFailure() = runTest {
        val repository = FakeCareRepository().apply { deleteError = IllegalStateException("failed") }
        val viewModel = PlantCareViewModel("plant-1", repository)

        viewModel.delete("event-1")
        viewModel.delete("event-1")
        advanceUntilIdle()

        assertTrue(repository.deletedEvents.isEmpty())
        assertEquals(UiErrorCategory.CareDelete, viewModel.mutationError.value)
        assertFalse(viewModel.isMutating.value)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CareEventEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun create_loadsPlantAndSavesTrimmedDraft() = runTest {
        val repository = FakeCareRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.updateCareType(CareType.PRUNING)
        viewModel.updateNote("  Trimmed note  ")
        viewModel.updatePerformedAt(900L)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf("plant-1" to CareEventDraft(CareType.PRUNING, 900L, "Trimmed note")),
            repository.createdEvents,
        )
        assertTrue(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun futureDate_setsLocalizedCategoryWithoutSaving() = runTest {
        val repository = FakeCareRepository()
        val viewModel = createViewModel(repository = repository, now = 1_000L)
        advanceUntilIdle()

        viewModel.updatePerformedAt(1_001L)
        viewModel.save()

        assertTrue(viewModel.state.value.futureError)
        assertEquals(UiErrorCategory.CareFuture, viewModel.state.value.saveError)
        assertTrue(repository.createdEvents.isEmpty())
    }

    @Test
    fun edit_loadsEventAndSavesUpdatedDraft() = runTest {
        val repository = FakeCareRepository(initialEvents = listOf(event("event-1", 500L, "Old")))
        val viewModel = createViewModel(repository = repository, eventId = "event-1")
        advanceUntilIdle()

        assertEquals("Old", viewModel.state.value.note)
        viewModel.updateCareType(CareType.FERTILIZING)
        viewModel.updateNote("  New  ")
        viewModel.updatePerformedAt(600L)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("plant-1", "event-1", CareEventDraft(CareType.FERTILIZING, 600L, "New"))),
            repository.updatedEvents,
        )
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun missingPlantOrEvent_setsNotFound() = runTest {
        val missingPlant = createViewModel(plant = null)
        advanceUntilIdle()
        assertTrue(missingPlant.state.value.notFound)

        val missingEvent = createViewModel(eventId = "missing")
        advanceUntilIdle()
        assertTrue(missingEvent.state.value.notFound)
    }

    @Test
    fun loadError_canBeRetried() = runTest {
        val plants = CareFakePlantRepository().apply { failObserveCalls += 1 }
        val viewModel = createViewModel(plantRepository = plants)
        advanceUntilIdle()
        assertEquals(UiErrorCategory.CareLoad, viewModel.state.value.loadError)

        viewModel.retryLoad()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.notFound)
        assertTrue(plants.observeCalls >= 2)
    }

    private fun createViewModel(
        repository: FakeCareRepository = FakeCareRepository(),
        plantRepository: CareFakePlantRepository = CareFakePlantRepository(),
        plant: Plant? = testPlant(),
        eventId: String? = null,
        now: Long = 1_000L,
    ): CareEventEditorViewModel {
        plantRepository.plant = plant
        return CareEventEditorViewModel(
            plantId = "plant-1",
            initialCareType = CareType.WATERING,
            eventId = eventId,
            repository = repository,
            plantRepository = plantRepository,
            epochMillisProvider = FixedEpochMillisProvider(now),
        )
    }
}

private class FakeCareRepository(
    initialEvents: List<CareEvent> = emptyList(),
) : CareEventRepository {
    private val events = MutableStateFlow(initialEvents)
    val createdEvents = mutableListOf<Pair<String, CareEventDraft>>()
    val updatedEvents = mutableListOf<Triple<String, String, CareEventDraft>>()
    val deletedEvents = mutableListOf<Pair<String, String>>()
    val failingObserveCalls = mutableSetOf<Int>()
    var observeCalls = 0
        private set
    var deleteError: Throwable? = null

    override fun observeEvents(plantId: String): Flow<List<CareEvent>> = flow {
        observeCalls += 1
        if (observeCalls in failingObserveCalls) error("load failed")
        emitAll(events)
    }

    override fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?> =
        flowOf(events.value.firstOrNull { it.plantId == plantId && it.id == eventId })

    override suspend fun createEvent(plantId: String, draft: CareEventDraft): String {
        createdEvents += plantId to draft
        return "created-event"
    }

    override suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft) {
        updatedEvents += Triple(plantId, eventId, draft)
    }

    override suspend fun deleteEvent(plantId: String, eventId: String) {
        deleteError?.let { throw it }
        deletedEvents += plantId to eventId
    }
}

private class CareFakePlantRepository : PlantRepository {
    var plant: Plant? = testPlant()
    val failObserveCalls = mutableSetOf<Int>()
    var observeCalls = 0

    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> = flowOf(emptyList())

    override fun observePlant(id: String): Flow<Plant?> = flow {
        observeCalls += 1
        if (observeCalls in failObserveCalls) error("load failed")
        emit(plant?.takeIf { it.id == id })
    }

    override suspend fun createPlant(draft: PlantDraft): String = error("unused")

    override suspend fun updatePlant(id: String, draft: PlantDraft) = Unit

    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

    override suspend fun deletePlant(id: String) = Unit
}

private class FixedEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    override fun now(): Long = value
}

private class FixedUuidProvider : UuidProvider {
    override fun newUuid(): String = "unused"
}

private fun event(
    id: String = "event-1",
    performedAt: Long = 100L,
    note: String? = null,
) = CareEvent(
    id = id,
    plantId = "plant-1",
    careType = CareType.WATERING,
    performedAt = performedAt,
    note = note,
    createdAt = performedAt,
    updatedAt = performedAt,
)

private fun testPlant() = Plant(
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
