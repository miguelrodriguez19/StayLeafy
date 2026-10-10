package com.miguelrodriguez19.stayleafy.ui.plant

import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        assertEquals(CareEventsUiState.Loading, viewModel(FakeCareRepository()).uiState.value)
    }

    @Test
    fun uiState_emitsReverseOrderedContent() = runTest {
        val events = listOf(event("new", 20L), event("old", 10L))
        val viewModel = viewModel(FakeCareRepository(events))

        assertEquals(
            CareEventsUiState.Content(events),
            viewModel.uiState.first { it is CareEventsUiState.Content },
        )
    }

    @Test
    fun uiState_emitsEmpty() = runTest {
        val viewModel = viewModel(FakeCareRepository())

        assertEquals(CareEventsUiState.Empty, viewModel.uiState.first { it is CareEventsUiState.Empty })
    }

    @Test
    fun uiState_errorCanBeRetried() = runTest {
        val repository = FakeCareRepository(listOf(event())).apply { failingObserveCalls += 1 }
        val viewModel = viewModel(repository)

        assertTrue(viewModel.uiState.first { it is CareEventsUiState.Error } is CareEventsUiState.Error)
        viewModel.retry()

        assertEquals(
            CareEventsUiState.Content(listOf(event())),
            viewModel.uiState.first { it is CareEventsUiState.Content },
        )
        assertTrue(repository.observeCalls >= 2)
    }

    @Test
    fun record_usesInjectedNowAndIgnoresRepeatedTap() = runTest {
        val repository = FakeCareRepository()
        val viewModel = viewModel(repository, now = 1_725_000_000_000L)

        viewModel.record(CareType.PRUNING)
        viewModel.record(CareType.PRUNING)
        advanceUntilIdle()

        assertEquals(
            listOf("plant-1" to CareEventDraft(CareType.PRUNING, 1_725_000_000_000L, null)),
            repository.createdEvents,
        )
        assertFalse(viewModel.isMutating.value)
    }

    @Test
    fun record_selectedDateKeepsCurrentLocalTime() = runTest {
        val now = 1_725_000_000_000L
        val selectedDate = LocalDate.of(2024, 1, 3)
        val currentTime = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
        val expected = selectedDate.atTime(currentTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val repository = FakeCareRepository()
        val viewModel = viewModel(repository, now)

        viewModel.record(CareType.FERTILIZING, selectedDate.toEpochDay())
        advanceUntilIdle()

        assertEquals(expected, repository.createdEvents.single().second.performedAt)
    }

    @Test
    fun record_failureIsLocalizedAndMutationUnlocks() = runTest {
        val repository = FakeCareRepository().apply { createError = IllegalStateException("failed") }
        val viewModel = viewModel(repository)

        viewModel.record(CareType.WATERING)
        advanceUntilIdle()

        assertEquals(UiErrorCategory.CareSave, viewModel.mutationError.value)
        assertFalse(viewModel.isMutating.value)
    }

    @Test
    fun clearHistory_targetsOnlyPlantAndTypeAndReportsFailure() = runTest {
        val repository = FakeCareRepository()
        val viewModel = viewModel(repository)

        viewModel.clearHistory(CareType.WATERING)
        advanceUntilIdle()

        assertEquals(listOf("plant-1" to CareType.WATERING), repository.clearedHistories)

        repository.deleteError = IllegalStateException("failed")
        viewModel.clearHistory(CareType.PRUNING)
        advanceUntilIdle()

        assertEquals(UiErrorCategory.CareDelete, viewModel.mutationError.value)
        assertFalse(viewModel.isMutating.value)
    }

    private fun viewModel(
        repository: FakeCareRepository,
        now: Long = 1_000L,
    ) = PlantCareViewModel("plant-1", repository, FixedEpochMillisProvider(now))
}

private class FakeCareRepository(
    initialEvents: List<CareEvent> = emptyList(),
) : CareEventRepository {
    private val events = MutableStateFlow(initialEvents)
    val createdEvents = mutableListOf<Pair<String, CareEventDraft>>()
    val clearedHistories = mutableListOf<Pair<String, CareType>>()
    val failingObserveCalls = mutableSetOf<Int>()
    var observeCalls = 0
        private set
    var createError: Throwable? = null
    var deleteError: Throwable? = null

    override fun observeEvents(plantId: String): Flow<List<CareEvent>> = flow {
        observeCalls += 1
        if (observeCalls in failingObserveCalls) error("load failed")
        emitAll(events)
    }

    override fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?> = flowOf(null)

    override suspend fun createEvent(plantId: String, draft: CareEventDraft): String {
        createError?.let { throw it }
        createdEvents += plantId to draft
        return "created-event"
    }

    override suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft) = Unit

    override suspend fun deleteEvent(plantId: String, eventId: String) = Unit

    override suspend fun deleteEventsByType(plantId: String, careType: CareType) {
        deleteError?.let { throw it }
        clearedHistories += plantId to careType
    }
}

private class FixedEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    override fun now(): Long = value
}

private fun event(
    id: String = "event-1",
    performedAt: Long = 100L,
) = CareEvent(
    id = id,
    plantId = "plant-1",
    careType = CareType.WATERING,
    performedAt = performedAt,
    note = null,
    createdAt = performedAt,
    updatedAt = performedAt,
)
