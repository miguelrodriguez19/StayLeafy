package com.miguelrodriguez19.stayleafy.ui.reminder

import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareScheduleDraft
import com.miguelrodriguez19.stayleafy.model.CareScheduleMode
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import com.miguelrodriguez19.stayleafy.model.SeasonalCareSummary
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderRule
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class Phase4ViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun noticeList_reportsErrorAndRetryReturnsSortedContent() = runTest {
        val older = notice(id = "older", createdAt = 10)
        val newer = notice(id = "newer", createdAt = 20)
        val gateway = FakePhase4Gateway(notices = listOf(older, newer)).apply {
            noticeObserveFailures = 1
        }
        val viewModel = NoticeListViewModel(gateway)

        assertEquals(NoticeListUiState.Error, viewModel.uiState.firstMatching { it is NoticeListUiState.Error })

        viewModel.retry()

        val content = viewModel.uiState.firstMatching { it is NoticeListUiState.Content }
            as NoticeListUiState.Content
        assertEquals(listOf(newer, older), content.notices)
        assertEquals(2, gateway.noticeObserveCalls)
    }

    @Test
    fun noticeDetail_marksNoticeReadAndReportsMissingNotice() = runTest {
        val gateway = FakePhase4Gateway(notices = emptyList())
        val viewModel = NoticeDetailViewModel("missing", gateway)

        advanceUntilIdle()

        assertEquals(listOf("missing"), gateway.markedRead)
        assertEquals(
            NoticeDetailUiState.NotFound,
            viewModel.uiState.firstMatching { it is NoticeDetailUiState.NotFound },
        )
    }

    @Test
    fun careConfiguration_requiresExplicitSourceAndSavesAllTypesAtomically() = runTest {
        val gateway = FakePhase4Gateway()
        val viewModel = CareConfigurationViewModel(
            plantId = "plant",
            plantRepository = FakePlantRepository(plant()),
            careEventRepository = FakeCareEventRepository(),
            gateway = gateway,
            zoneIdProvider = FixedZoneIdProvider,
        )
        advanceUntilIdle()

        viewModel.setEnabled(CareType.WATERING, true)
        viewModel.setInterval(CareType.WATERING, "7")
        viewModel.save()

        val invalid = viewModel.uiState.value as CareConfigurationUiState.Content
        assertTrue(invalid.forms.single { it.careType == CareType.WATERING }.sourceError)
        assertTrue(gateway.savedSchedules.isEmpty())

        viewModel.setDate(CareType.WATERING, 21_000)
        viewModel.save()
        advanceUntilIdle()

        val (plantId, drafts) = gateway.savedSchedules.single()
        assertEquals("plant", plantId)
        assertEquals(CareType.values().size, drafts.size)
        assertEquals(
            CareScheduleDraft(
                careType = CareType.WATERING,
                mode = CareScheduleMode.INTERVAL,
                interval = IntervalReminderConfiguration(7, ReminderDueSource.Date(21_000)),
            ),
            drafts.single { it.careType == CareType.WATERING },
        )
        assertTrue(
            drafts.filterNot { it.careType == CareType.WATERING }
                .all { it.mode == CareScheduleMode.DISABLED },
        )
        assertTrue((viewModel.uiState.value as CareConfigurationUiState.Content).saved)
    }

    @Test
    fun careConfiguration_defaultsNewIntervalToLatestCareAndSupportsExplicitSwitch() = runTest {
        val latestCareDate = LocalDate.of(2026, 10, 10)
        val viewModel = CareConfigurationViewModel(
            plantId = "plant",
            plantRepository = FakePlantRepository(plant()),
            careEventRepository = FakeCareEventRepository(
                events = listOf(
                    CareEvent(
                        id = "watering-event",
                        plantId = "plant",
                        careType = CareType.WATERING,
                        performedAt = latestCareDate.atStartOfDay(FixedZoneIdProvider.currentZoneId()).toInstant().toEpochMilli(),
                        note = null,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
            gateway = FakePhase4Gateway(),
            zoneIdProvider = FixedZoneIdProvider,
        )
        advanceUntilIdle()

        val initial = viewModel.uiState.value as CareConfigurationUiState.Content
        assertEquals(
            ReminderDueSourceChoice.LatestCare,
            initial.forms.single { it.careType == CareType.WATERING }.dueSource,
        )

        viewModel.setSource(CareType.WATERING, ReminderDueSourceChoice.CustomDate)
        assertEquals(
            ReminderDueSourceChoice.CustomDate,
            (viewModel.uiState.value as CareConfigurationUiState.Content)
                .forms.single { it.careType == CareType.WATERING }.dueSource,
        )
        viewModel.setSource(CareType.WATERING, ReminderDueSourceChoice.LatestCare)
        assertEquals(
            ReminderDueSourceChoice.LatestCare,
            (viewModel.uiState.value as CareConfigurationUiState.Content)
                .forms.single { it.careType == CareType.WATERING }.dueSource,
        )
    }

    @Test
    fun careConfiguration_reportsMissingPlant() = runTest {
        val viewModel = CareConfigurationViewModel(
            plantId = "missing",
            plantRepository = FakePlantRepository(null),
            careEventRepository = FakeCareEventRepository(),
            gateway = FakePhase4Gateway(),
            zoneIdProvider = FixedZoneIdProvider,
        )

        advanceUntilIdle()

        assertEquals(CareConfigurationUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun careConfiguration_requiresSeasonStartAndSavesCrossYearSeason() = runTest {
        val gateway = FakePhase4Gateway()
        val viewModel = CareConfigurationViewModel(
            plantId = "plant",
            plantRepository = FakePlantRepository(plant()),
            careEventRepository = FakeCareEventRepository(),
            gateway = gateway,
            zoneIdProvider = FixedZoneIdProvider,
        )
        advanceUntilIdle()

        viewModel.setMode(CareType.PRUNING, CareScheduleMode.SEASONAL)
        viewModel.save()
        assertTrue(
            (viewModel.uiState.value as CareConfigurationUiState.Content)
                .forms.single { it.careType == CareType.PRUNING }.seasonStartError,
        )

        viewModel.setSeasonStart(CareType.PRUNING, AnnualDate(11, 15))
        viewModel.setSeasonEnd(CareType.PRUNING, AnnualDate(2, 10))
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            CareScheduleDraft(
                careType = CareType.PRUNING,
                mode = CareScheduleMode.SEASONAL,
                seasonal = SeasonalReminderConfiguration(AnnualDate(11, 15), AnnualDate(2, 10)),
            ),
            gateway.savedSchedules.single().second.single { it.careType == CareType.PRUNING },
        )
    }
}

private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.firstMatching(
    predicate: (T) -> Boolean,
): T = first(predicate)

private class FakePhase4Gateway(
    notices: List<AppNotice> = emptyList(),
) : Phase4UiGateway {
    private val noticeState = MutableStateFlow(notices)
    var noticeObserveFailures = 0
    var noticeObserveCalls = 0
        private set
    val markedRead = mutableListOf<String>()
    val savedRules = mutableListOf<Pair<String, List<IntervalReminderDraft>>>()
    val savedSchedules = mutableListOf<Pair<String, List<CareScheduleDraft>>>()

    override fun observeRules(plantId: String): Flow<List<IntervalReminderRule>> = flowOf(emptyList())

    override fun observeSeasonalRules(plantId: String): Flow<List<SeasonalReminderRule>> = flowOf(emptyList())

    override fun observeCareSummaries(): Flow<List<CareStatusSummary>> = flowOf(emptyList())

    override fun observeSeasonalSummaries(plantId: String): Flow<List<SeasonalCareSummary>> =
        flowOf(emptyList())

    override suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>) {
        savedRules += plantId to drafts
    }

    override suspend fun saveSchedules(plantId: String, drafts: List<CareScheduleDraft>) {
        savedSchedules += plantId to drafts
    }

    override fun observeNotices(): Flow<List<AppNotice>> = flow {
        noticeObserveCalls += 1
        if (noticeObserveFailures > 0) {
            noticeObserveFailures -= 1
            error("load failed")
        }
        emit(noticeState.value)
    }

    override fun observeNotice(noticeId: String): Flow<AppNotice?> =
        flowOf(noticeState.value.firstOrNull { it.id == noticeId })

    override fun observeUnreadCount(): Flow<Int> =
        flowOf(noticeState.value.count { it.readAt == null })

    override suspend fun markNoticeRead(noticeId: String) {
        markedRead += noticeId
    }

    override suspend fun markAllNoticesRead() = Unit

    override fun observeNotificationWarning(): Flow<Boolean> = flowOf(false)

    override suspend fun shouldShowPermissionEducation(): Boolean = false

    override suspend fun markPermissionEducationShown() = Unit
}

private class FakePlantRepository(initialPlant: Plant?) : PlantRepository {
    private val plant = MutableStateFlow(initialPlant)

    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> =
        flowOf(listOfNotNull(plant.value))

    override fun observePlant(id: String): Flow<Plant?> = plant

    override suspend fun createPlant(draft: PlantDraft): String = error("Not used")

    override suspend fun updatePlant(id: String, draft: PlantDraft) = Unit

    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

    override suspend fun deletePlant(id: String) = Unit
}

private class FakeCareEventRepository(
    private val events: List<CareEvent> = emptyList(),
) : CareEventRepository {
    override fun observeEvents(plantId: String): Flow<List<CareEvent>> = flowOf(events)

    override fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?> = flowOf(null)

    override suspend fun createEvent(plantId: String, draft: CareEventDraft): String = error("Not used")

    override suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft) = Unit

    override suspend fun deleteEvent(plantId: String, eventId: String) = Unit

    override suspend fun deleteEventsByType(plantId: String, careType: CareType) = Unit
}

private object FixedZoneIdProvider : ZoneIdProvider {
    override fun currentZoneId(): ZoneId = ZoneId.of("Europe/Madrid")
}

private fun plant() = Plant(
    id = "plant",
    name = "Monstera",
    scientificName = null,
    location = null,
    acquiredEpochDay = null,
    description = null,
    mainPhotoPath = null,
    isFavorite = false,
    createdAt = 1,
    updatedAt = 1,
)

private fun notice(id: String, createdAt: Long) = AppNotice(
    id = id,
    ruleId = "rule-$id",
    plantId = "plant",
    plantName = "Monstera",
    careType = CareType.WATERING,
    dueEpochDay = 21_000,
    createdAt = createdAt,
    readAt = null,
    deliveryState = NoticeDeliveryState.DELIVERED,
)
