package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventCreateResult
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BulkCareViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startAndSelection_useInjectedLocalDay_andKeepSelectionsAcrossVisibleSets() = runTest {
        val first = plant("fern", "Fern", photoPath = "plant_photos/fern.jpg")
        val second = plant("aloe", "Aloe")
        val third = plant("monstera", "Monstera")
        val viewModel = viewModel()

        viewModel.startSelection()
        assertEquals(LocalDate.of(2024, 4, 1).toEpochDay(), viewModel.uiState.value.performedEpochDay)
        viewModel.selectVisiblePlants(listOf(first, second))
        viewModel.selectVisiblePlants(listOf(third))
        viewModel.togglePlant(second)

        assertEquals(listOf("fern", "monstera"), viewModel.uiState.value.selectedPlantIds)
        assertEquals(listOf("Fern", "Monstera"), viewModel.uiState.value.selectedPreviews().map { it.name })
        assertEquals("plant_photos/fern.jpg", viewModel.uiState.value.selectedPreviews().first().photoPath)
        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selectedPlantIds.isEmpty())
    }

    @Test
    fun formRequiresSelectionAndCareType_andCancelKeepsDraft_andRejectsFutureDates() = runTest {
        val viewModel = viewModel()
        viewModel.startSelection()
        viewModel.continueToForm()
        assertFalse(viewModel.uiState.value.isFormOpen)

        viewModel.selectVisiblePlants(listOf(plant("fern", "Fern")))
        viewModel.continueToForm()
        viewModel.openConfirmation()
        assertFalse(viewModel.uiState.value.isConfirming)
        viewModel.setPerformedEpochDay(viewModel.uiState.value.todayEpochDay + 1)
        assertEquals(viewModel.uiState.value.todayEpochDay, viewModel.uiState.value.performedEpochDay)

        val pastDay = viewModel.uiState.value.todayEpochDay - 10
        viewModel.setPerformedEpochDay(pastDay)
        viewModel.setCareType(CareType.PRUNING)
        viewModel.openConfirmation()

        assertTrue(viewModel.uiState.value.isConfirming)
        assertEquals(pastDay, viewModel.uiState.value.performedEpochDay)
        viewModel.cancelConfirmation()
        assertFalse(viewModel.uiState.value.isConfirming)
        assertEquals(CareType.PRUNING, viewModel.uiState.value.careType)
        assertEquals(pastDay, viewModel.uiState.value.performedEpochDay)
        assertTrue(viewModel.uiState.value.isFormOpen)
    }

    @Test
    fun confirmIsGuardedAndSuccessClosesSelectionWithRecordedAndSkippedCounts() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = BulkCareTestCareRepository().apply {
            bulkGate = gate
            bulkResult = BulkCareResult(recordedPlantIds = listOf("fern"), skippedPlantIds = listOf("aloe"))
        }
        val viewModel = viewModel(repository)
        viewModel.startSelection()
        viewModel.selectVisiblePlants(listOf(plant("fern", "Fern"), plant("aloe", "Aloe")))
        viewModel.continueToForm()
        viewModel.setCareType(CareType.WATERING)
        viewModel.openConfirmation()

        viewModel.confirmSubmission()
        assertFalse(viewModel.uiState.value.isConfirming)
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.confirmSubmission()
        viewModel.openConfirmation()
        assertFalse(viewModel.uiState.value.isConfirming)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.bulkCalls.size)
        assertEquals(
            BulkCall(listOf("fern", "aloe"), CareType.WATERING, LocalDate.of(2024, 4, 1).toEpochDay()),
            repository.bulkCalls.single(),
        )
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(CareType.WATERING, viewModel.uiState.value.completion?.careType)
        assertEquals(LocalDate.of(2024, 4, 1).toEpochDay(), viewModel.uiState.value.completion?.performedEpochDay)
        assertEquals(1, viewModel.uiState.value.completion?.result?.recordedPlantIds?.size)
        assertEquals(1, viewModel.uiState.value.completion?.result?.skippedPlantIds?.size)
    }

    @Test
    fun failedSubmitKeepsDraft_andRetryUsesTheSameSelection() = runTest {
        val repository = BulkCareTestCareRepository().apply { bulkError = IllegalStateException("failed") }
        val viewModel = viewModel(repository)
        viewModel.startSelection()
        viewModel.selectVisiblePlants(listOf(plant("fern", "Fern")))
        viewModel.continueToForm()
        viewModel.setCareType(CareType.FERTILIZING)
        viewModel.openConfirmation()
        viewModel.confirmSubmission()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isConfirming)
        assertEquals(listOf("fern"), viewModel.uiState.value.selectedPlantIds)
        assertEquals(CareType.FERTILIZING, viewModel.uiState.value.careType)

        repository.bulkError = null
        viewModel.openConfirmation()
        assertTrue(viewModel.uiState.value.hasError)
        viewModel.cancelConfirmation()
        assertTrue(viewModel.uiState.value.hasError)
        viewModel.openConfirmation()
        viewModel.confirmSubmission()
        advanceUntilIdle()
        assertEquals(2, repository.bulkCalls.size)
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun ViewModelStoreRetainsDialogAndBannerAcrossOwnerRecreation_butLeavingPlantsClearsIt() = runTest {
        val repository = BulkCareTestCareRepository().apply {
            bulkResult = BulkCareResult(listOf("fern"), emptyList())
        }
        val dataOperations = DataOperationCoordinator()
        val store = ViewModelStore()
        val factory = factory(repository, dataOperations)
        val firstOwnerModel = ViewModelProvider(store, factory)[BulkCareViewModel::class.java]
        firstOwnerModel.startSelection()
        firstOwnerModel.selectVisiblePlants(listOf(plant("fern", "Fern")))
        firstOwnerModel.continueToForm()
        firstOwnerModel.setCareType(CareType.WATERING)
        firstOwnerModel.openConfirmation()

        val recreatedOwnerModel = ViewModelProvider(store, factory)[BulkCareViewModel::class.java]
        assertSame(firstOwnerModel, recreatedOwnerModel)
        assertEquals(listOf("fern"), recreatedOwnerModel.uiState.value.selectedPlantIds)
        assertEquals(CareType.WATERING, recreatedOwnerModel.uiState.value.careType)
        assertTrue(recreatedOwnerModel.uiState.value.isConfirming)
        recreatedOwnerModel.confirmSubmission()
        advanceUntilIdle()
        assertEquals(1, recreatedOwnerModel.uiState.value.completion?.result?.recordedPlantIds?.size)

        val sameStoreOwnerModel = ViewModelProvider(store, factory)[BulkCareViewModel::class.java]
        assertSame(recreatedOwnerModel, sameStoreOwnerModel)
        assertEquals(CareType.WATERING, sameStoreOwnerModel.uiState.value.completion?.careType)

        sameStoreOwnerModel.leavePlants()
        assertFalse(sameStoreOwnerModel.uiState.value.isSelectionMode)
        assertNull(sameStoreOwnerModel.uiState.value.completion)
        assertTrue(sameStoreOwnerModel.uiState.value.selectedPlantIds.isEmpty())
        store.clear()
        assertFalse(ViewModelProvider(ViewModelStore(), factory)[BulkCareViewModel::class.java]
            .uiState.value.isSelectionMode)

        val replacedStore = ViewModelStore()
        val replacedModel = ViewModelProvider(replacedStore, factory)[BulkCareViewModel::class.java]
        replacedModel.startSelection()
        replacedModel.selectVisiblePlants(listOf(plant("fern", "Fern")))
        dataOperations.withMaintenance { Unit }
        advanceUntilIdle()
        assertFalse(replacedModel.uiState.value.isSelectionMode)
        assertTrue(replacedModel.uiState.value.selectedPlantIds.isEmpty())
        replacedStore.clear()
    }

    @Test
    fun leavingPlantsWhileSubmissionRuns_suppressesCompletionOnReturn() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = BulkCareTestCareRepository().apply {
            bulkGate = gate
            bulkResult = BulkCareResult(listOf("fern"), emptyList())
        }
        val viewModel = viewModel(repository)
        viewModel.startSelection()
        viewModel.selectVisiblePlants(listOf(plant("fern", "Fern")))
        viewModel.continueToForm()
        viewModel.setCareType(CareType.WATERING)
        viewModel.openConfirmation()
        viewModel.confirmSubmission()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)

        viewModel.leavePlants()
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(null, viewModel.uiState.value.completion)
        assertEquals(1, repository.bulkCalls.size)
    }

    private fun viewModel(
        repository: BulkCareTestCareRepository = BulkCareTestCareRepository(),
        dataOperations: DataOperationCoordinator = DataOperationCoordinator(),
    ) = BulkCareViewModel(repository, FixedClock, FixedZone, dataOperations)

    private fun factory(
        repository: BulkCareTestCareRepository,
        dataOperations: DataOperationCoordinator,
    ) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            viewModel(repository, dataOperations) as T
    }
}

private data class BulkCall(val plantIds: List<String>, val careType: CareType, val epochDay: Long)

private class BulkCareTestCareRepository : CareEventRepository {
    val bulkCalls = mutableListOf<BulkCall>()
    var bulkResult = BulkCareResult(emptyList(), emptyList())
    var bulkError: Throwable? = null
    var bulkGate: CompletableDeferred<Unit>? = null

    override fun observeEvents(plantId: String): Flow<List<CareEvent>> = flowOf(emptyList())
    override fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?> = flowOf(null)
    override suspend fun createEvent(plantId: String, draft: CareEventDraft) = CareEventCreateResult.Created("event")
    override suspend fun recordBulkCare(plantIds: List<String>, careType: CareType, performedEpochDay: Long): BulkCareResult {
        bulkCalls += BulkCall(plantIds.toList(), careType, performedEpochDay)
        bulkGate?.await()
        bulkError?.let { throw it }
        return bulkResult
    }
    override suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft) = Unit
    override suspend fun deleteEvent(plantId: String, eventId: String) = Unit
    override suspend fun deleteEventsByType(plantId: String, careType: CareType) = Unit
}

private object FixedClock : EpochMillisProvider {
    override fun now(): Long = java.time.Instant.parse("2024-03-31T22:30:00Z").toEpochMilli()
}

private object FixedZone : ZoneIdProvider {
    override fun currentZoneId(): ZoneId = ZoneId.of("Europe/Madrid")
}

private fun plant(id: String, name: String, photoPath: String? = null) = Plant(
    id = id,
    name = name,
    scientificName = null,
    location = null,
    acquiredEpochDay = null,
    description = null,
    mainPhotoPath = photoPath,
    isFavorite = false,
    createdAt = 1L,
    updatedAt = 1L,
)
