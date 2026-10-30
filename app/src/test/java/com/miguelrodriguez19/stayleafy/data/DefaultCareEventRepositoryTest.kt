package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDaoWriteResult
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareEventCreateResult
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.platform.reminder.PendingReminderDelivery
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationResult
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCareEventRepositoryTest {

    private val fixedNow = 1_725_000_000_000L
    private val fixedUuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun createEvent_trimsNoteAndUsesFixedTimestampAndUuid() = runTest {
        val dao = RecordingCareEventDao()
        val repository = createRepository(dao)

        val result = repository.createEvent(
            plantId = "plant-1",
            draft = CareEventDraft(
                careType = CareType.WATERING,
                performedAt = fixedNow - 1_000,
                note = "  Deep watering.  ",
            ),
        )

        assertEquals(CareEventCreateResult.Created(fixedUuid), result)
        assertEquals(
            CareEventEntity(
                id = fixedUuid,
                plantId = "plant-1",
                careType = CareType.WATERING.name,
                performedAt = fixedNow - 1_000,
                note = "Deep watering.",
                createdAt = fixedNow,
                updatedAt = fixedNow,
            ),
            dao.insertedEvent,
        )
    }

    @Test
    fun createEvent_blankNote_isNormalizedToNull() = runTest {
        val dao = RecordingCareEventDao()
        val repository = createRepository(dao)

        repository.createEvent(
            plantId = "plant-1",
            draft = CareEventDraft(CareType.PRUNING, fixedNow, " \t\n "),
        )

        assertEquals(null, dao.insertedEvent?.note)
    }

    @Test
    fun createEvent_futureTimestamp_isRejectedBeforeUuidAndInsert() = runTest {
        val dao = RecordingCareEventDao()
        val uuidProvider = RecordingUuidProvider(fixedUuid)
        val repository = createRepository(dao, uuidProvider)

        val error = runCatching {
            repository.createEvent(
                plantId = "plant-1",
                draft = CareEventDraft(CareType.FERTILIZING, fixedNow + 1, null),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("performedAt must not be in the future", error?.message)
        assertEquals(0, uuidProvider.calls)
        assertEquals(null, dao.insertedEvent)
    }

    @Test
    fun createEvent_latestCareAdvancesRuleAndResolvesCurrentOccurrence() = runTest {
        val rule = reminderRule(nextDueEpochDay = 20_000L)
        val dao = RecordingCareEventDao(
            initialEvents = listOf(eventEntity()),
            reminderRule = rule,
        )
        val repository = createRepository(dao)

        repository.createEvent(
            "plant-1",
            CareEventDraft(CareType.WATERING, fixedNow - 1_000L, null),
        )

        assertEquals(rule.id, dao.resolvedRuleId)
        assertEquals(
            epochDayAt(fixedNow - 1_000L, ZoneOffset.UTC) + requireNotNull(rule.intervalDays),
            dao.updatedRuleDueEpochDay,
        )
    }

    @Test
    fun createEvent_backdatedBeforeLatestCareDoesNotRegressRule() = runTest {
        val latest = eventEntity().copy(performedAt = fixedNow - 1_000L)
        val dao = RecordingCareEventDao(
            initialEvents = listOf(latest),
            reminderRule = reminderRule(nextDueEpochDay = 20_000L),
        )
        val repository = createRepository(dao)

        repository.createEvent(
            "plant-1",
            CareEventDraft(CareType.WATERING, fixedNow - 86_400_000L, null),
        )

        assertEquals(null, dao.updatedRuleDueEpochDay)
        assertEquals(null, dao.resolvedRuleId)
    }

    @Test
    fun createEvent_sameLocalDayReturnsAlreadyRecordedWithoutInsertingOrResolvingReminder() = runTest {
        val existing = eventEntity().copy(performedAt = fixedNow - 60_000L)
        val rule = reminderRule(nextDueEpochDay = 20_000L)
        val dao = RecordingCareEventDao(initialEvents = listOf(existing), reminderRule = rule)
        val repository = createRepository(dao)

        val result = repository.createEvent(
            plantId = existing.plantId,
            draft = CareEventDraft(CareType.WATERING, fixedNow, "Another watering"),
        )

        assertEquals(CareEventCreateResult.AlreadyRecorded, result)
        assertEquals(1, dao.events.size)
        assertEquals(null, dao.updatedRuleDueEpochDay)
        assertEquals(null, dao.resolvedRuleId)
    }

    @Test
    fun recordBulkCare_deduplicatesIdsAndUsesCurrentLocalTimeOnSelectedPastDay() = runTest {
        val now = java.time.ZonedDateTime.of(2025, 4, 1, 16, 45, 12, 0, java.time.ZoneId.of("Europe/Madrid"))
            .toInstant().toEpochMilli()
        val dao = RecordingCareEventDao()
        val countingNow = CountingEpochMillisProvider(now)
        val countingZone = CountingZoneIdProvider(ZoneId.of("Europe/Madrid"))
        val repository = createRepository(
            dao = dao,
            epochMillisProvider = countingNow,
            zoneIdProvider = countingZone,
            uuidProvider = SequenceCareEventUuidProvider(),
        )
        val chosenDay = java.time.LocalDate.of(2025, 3, 30).toEpochDay()

        val result = repository.recordBulkCare(
            plantIds = listOf("plant-1", "plant-2", "plant-1"),
            careType = CareType.WATERING,
            performedEpochDay = chosenDay,
        )

        assertEquals(BulkCareResult(listOf("plant-1", "plant-2"), emptyList()), result)
        assertEquals(2, dao.events.size)
        assertEquals(
            java.time.ZonedDateTime.of(2025, 3, 30, 16, 45, 12, 0, java.time.ZoneId.of("Europe/Madrid"))
                .toInstant().toEpochMilli(),
            dao.events.first().performedAt,
        )
        assertEquals(now, dao.events.first().createdAt)
        assertEquals(1, countingNow.calls)
        assertEquals(1, countingZone.calls)
        val dayWindow = requireNotNull(dao.recordCall)
        assertEquals(23 * 60 * 60 * 1_000L, dayWindow.dayEndExclusive - dayWindow.dayStartInclusive)
    }

    @Test
    fun recordBulkCare_reportsMixedAndAllDuplicatesWithoutChangingDuplicateHistory() = runTest {
        val today = epochDayAt(fixedNow, ZoneOffset.UTC)
        val existing = eventEntity().copy(plantId = "plant-1", performedAt = fixedNow - 1_000L)
        val dao = RecordingCareEventDao(initialEvents = listOf(existing))
        val repository = createRepository(dao, uuidProvider = SequenceCareEventUuidProvider())

        val mixed = repository.recordBulkCare(
            listOf("plant-1", "plant-2"),
            CareType.WATERING,
            today,
        )
        val allDuplicate = repository.recordBulkCare(
            listOf("plant-1", "plant-2"),
            CareType.WATERING,
            today,
        )

        assertEquals(BulkCareResult(listOf("plant-2"), listOf("plant-1")), mixed)
        assertEquals(BulkCareResult(emptyList(), listOf("plant-1", "plant-2")), allDuplicate)
        assertEquals(2, dao.events.size)
    }

    @Test
    fun recordBulkCare_rejectsEmptyOrFutureDayBeforeReadingProviders() = runTest {
        val countingNow = CountingEpochMillisProvider(fixedNow)
        val countingZone = CountingZoneIdProvider(ZoneOffset.UTC)
        val repository = createRepository(
            dao = RecordingCareEventDao(),
            epochMillisProvider = countingNow,
            zoneIdProvider = countingZone,
        )

        val emptyError = runCatching {
            repository.recordBulkCare(emptyList(), CareType.WATERING, 20_000L)
        }.exceptionOrNull()
        val futureError = runCatching {
            repository.recordBulkCare(listOf("plant-1"), CareType.WATERING, 30_000L)
        }.exceptionOrNull()

        assertTrue(emptyError is IllegalArgumentException)
        assertTrue(futureError is IllegalArgumentException)
        assertEquals(1, countingNow.calls)
        assertEquals(1, countingZone.calls)
    }

    @Test
    fun updateEvent_trimsNoteUsesCurrentTimestampAndDoesNotChangeCreatedAt() = runTest {
        val original = eventEntity()
        val dao = RecordingCareEventDao(listOf(original))
        val repository = createRepository(dao)

        repository.updateEvent(
            plantId = "plant-1",
            eventId = "event-1",
            draft = CareEventDraft(
                careType = CareType.FERTILIZING,
                performedAt = fixedNow - 2_000,
                note = "  Updated note  ",
            ),
        )

        assertEquals(
            CareEventUpdateCall(
                plantId = "plant-1",
                eventId = "event-1",
                careType = CareType.FERTILIZING.name,
                performedAt = fixedNow - 2_000,
                note = "Updated note",
                updatedAt = fixedNow,
            ),
            dao.updateCall,
        )
        assertEquals(original.createdAt, dao.currentEvent?.createdAt)
    }

    @Test
    fun updateEvent_futureTimestamp_isRejectedBeforeUpdate() = runTest {
        val dao = RecordingCareEventDao(listOf(eventEntity()))
        val repository = createRepository(dao)

        val error = runCatching {
            repository.updateEvent(
                plantId = "plant-1",
                eventId = "event-1",
                draft = CareEventDraft(CareType.WATERING, fixedNow + 1, null),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(null, dao.updateCall)
    }

    @Test
    fun observeEvents_mapsEntitiesAndPassesPlantId() = runTest {
        val entity = eventEntity()
        val dao = RecordingCareEventDao(listOf(entity))
        val repository = createRepository(dao)

        assertEquals(listOf(entity.toModelForTest()), repository.observeEvents("plant-1").first())
        assertEquals("plant-1", dao.lastObservedPlantId)
    }

    @Test
    fun observeEvent_mapsEntityAndMissingRows() = runTest {
        val entity = eventEntity()
        val dao = RecordingCareEventDao(listOf(entity))
        val repository = createRepository(dao)

        assertEquals(entity.toModelForTest(), repository.observeEvent("plant-1", "event-1").first())
        assertEquals(null, repository.observeEvent("plant-1", "missing").first())
        assertEquals(EventKey("plant-1", "missing"), dao.lastObservedEvent)
    }

    @Test
    fun deleteEvent_passesPlantAndEventIds() = runTest {
        val dao = RecordingCareEventDao(listOf(eventEntity()))
        val repository = createRepository(dao)

        repository.deleteEvent("plant-1", "event-1")

        assertEquals(EventKey("plant-1", "event-1"), dao.deletedEvent)
    }

    @Test
    fun deleteEventsByType_scopesDeletionToPlantAndCareType() = runTest {
        val dao = RecordingCareEventDao()
        val repository = createRepository(dao)

        repository.deleteEventsByType("plant-1", CareType.PRUNING)

        assertEquals("plant-1" to CareType.PRUNING.name, dao.deletedHistory)
    }

    @Test
    fun updateAndDeleteMissingEvents_areRejected() = runTest {
        val repository = createRepository(RecordingCareEventDao())

        val updateError = runCatching {
            repository.updateEvent(
                plantId = "plant-1",
                eventId = "missing",
                draft = CareEventDraft(CareType.WATERING, fixedNow, null),
            )
        }.exceptionOrNull()
        val deleteError = runCatching {
            repository.deleteEvent("plant-1", "missing")
        }.exceptionOrNull()

        assertTrue(updateError is IllegalStateException)
        assertTrue(deleteError is IllegalStateException)
    }

    private fun createRepository(
        dao: CareEventDao,
        uuidProvider: UuidProvider = FixedCareEventUuidProvider(fixedUuid),
        epochMillisProvider: EpochMillisProvider = FixedCareEventEpochMillisProvider(fixedNow),
        zoneIdProvider: ZoneIdProvider = FixedCareEventZoneIdProvider(ZoneOffset.UTC),
    ) = DefaultCareEventRepository(
        careEventDao = dao,
        epochMillisProvider = epochMillisProvider,
        uuidProvider = uuidProvider,
        zoneIdProvider = zoneIdProvider,
        notificationGateway = NoOpCareNotificationGateway,
    )
}

private class FixedCareEventZoneIdProvider(private val zoneId: ZoneId) : ZoneIdProvider {
    override fun currentZoneId(): ZoneId = zoneId
}

private class CountingZoneIdProvider(private val zoneId: ZoneId) : ZoneIdProvider {
    var calls = 0
    override fun currentZoneId(): ZoneId {
        calls += 1
        return zoneId
    }
}

private class FixedCareEventEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    override fun now(): Long = value
}

private class CountingEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    var calls = 0
    override fun now(): Long {
        calls += 1
        return value
    }
}

private class FixedCareEventUuidProvider(private val value: String) : UuidProvider {
    override fun newUuid(): String = value
}

private class RecordingUuidProvider(private val value: String) : UuidProvider {
    var calls: Int = 0
        private set

    override fun newUuid(): String {
        calls += 1
        return value
    }
}

private class SequenceCareEventUuidProvider : UuidProvider {
    private var nextId = 0
    override fun newUuid(): String = "bulk-${++nextId}"
}

private class RecordingCareEventDao(
    initialEvents: List<CareEventEntity> = emptyList(),
    private val reminderRule: ReminderRuleEntity? = null,
) : CareEventDao {
    private val data = MutableStateFlow(initialEvents)

    var insertedEvent: CareEventEntity? = null
        private set
    var updateCall: CareEventUpdateCall? = null
        private set
    var deletedEvent: EventKey? = null
        private set
    var deletedHistory: Pair<String, String>? = null
        private set
    var lastObservedPlantId: String? = null
        private set
    var lastObservedEvent: EventKey? = null
        private set
    var updatedRuleDueEpochDay: Long? = null
        private set
    var resolvedRuleId: String? = null
        private set

    var recordCall: CareEventRecordCall? = null
        private set

    val events: List<CareEventEntity>
        get() = data.value

    val currentEvent: CareEventEntity?
        get() = data.value.firstOrNull { it.id == "event-1" }

    override fun observeEvents(plantId: String): Flow<List<CareEventEntity>> {
        lastObservedPlantId = plantId
        return data.map { events -> events.filter { it.plantId == plantId } }
    }

    override fun observeEvent(plantId: String, eventId: String): Flow<CareEventEntity?> {
        lastObservedEvent = EventKey(plantId, eventId)
        return data.map { events -> events.firstOrNull { it.plantId == plantId && it.id == eventId } }
    }

    override suspend fun getExistingPlantIds(plantIds: List<String>): List<String> = plantIds

    override suspend fun insertEvent(event: CareEventEntity) {
        insertedEvent = event
        data.value = data.value + event
    }

    override suspend fun recordEventsIfNotRecorded(
        events: List<CareEventEntity>,
        dayStartInclusive: Long,
        dayEndExclusive: Long,
        performedEpochDay: Long,
    ): CareEventDaoWriteResult {
        recordCall = CareEventRecordCall(dayStartInclusive, dayEndExclusive, performedEpochDay)
        val recordedPlantIds = mutableListOf<String>()
        val skippedPlantIds = mutableListOf<String>()
        events.forEach { event ->
            val duplicate = data.value.any {
                it.plantId == event.plantId &&
                    it.careType == event.careType &&
                    it.performedAt >= dayStartInclusive && it.performedAt < dayEndExclusive
            }
            if (duplicate) {
                skippedPlantIds += event.plantId
            } else {
                val latestBeforeInsert = getLatestPerformedAt(event.plantId, event.careType)
                insertEvent(event)
                recordedPlantIds += event.plantId
                if (latestBeforeInsert == null || event.performedAt >= latestBeforeInsert) {
                    getIntervalRule(event.plantId, event.careType)?.let { rule ->
                        resolvedRuleId = rule.id
                        updatedRuleDueEpochDay = Math.addExact(performedEpochDay, requireNotNull(rule.intervalDays).toLong())
                    }
                }
            }
        }
        return CareEventDaoWriteResult(recordedPlantIds, skippedPlantIds, emptyList())
    }

    override suspend fun getLatestPerformedAt(plantId: String, careType: String): Long? =
        data.value
            .filter { it.plantId == plantId && it.careType == careType }
            .maxOfOrNull(CareEventEntity::performedAt)

    override suspend fun countEventsInWindow(
        plantId: String,
        careType: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Int = data.value.count {
        it.plantId == plantId && it.careType == careType && it.performedAt in startInclusive..<endExclusive
    }

    override suspend fun getIntervalRule(
        plantId: String,
        careType: String,
    ): ReminderRuleEntity? = reminderRule?.takeIf {
        it.plantId == plantId && it.careType == careType
    }

    override suspend fun getSeasonalRule(plantId: String, careType: String): ReminderRuleEntity? = null

    override suspend fun getUnreadNoticeIds(ruleId: String): List<String> = emptyList()

    override suspend fun markOccurrenceNoticeRead(occurrenceKey: String, readAt: Long): Int = 0

    override suspend fun getUnreadOccurrenceNoticeIds(occurrenceKey: String): List<String> = emptyList()

    override suspend fun updateRuleDueDate(
        ruleId: String,
        nextDueEpochDay: Long,
        updatedAt: Long,
    ): Int {
        updatedRuleDueEpochDay = nextDueEpochDay
        return if (reminderRule?.id == ruleId) 1 else 0
    }

    override suspend fun markRuleNoticesRead(ruleId: String, readAt: Long): Int {
        resolvedRuleId = ruleId
        return 1
    }

    override suspend fun updateEvent(
        plantId: String,
        eventId: String,
        careType: String,
        performedAt: Long,
        note: String?,
        updatedAt: Long,
    ): Int {
        updateCall = CareEventUpdateCall(plantId, eventId, careType, performedAt, note, updatedAt)
        val updated = data.value.count { it.plantId == plantId && it.id == eventId }
        data.value = data.value.map { event ->
            if (event.plantId == plantId && event.id == eventId) {
                event.copy(
                    careType = careType,
                    performedAt = performedAt,
                    note = note,
                    updatedAt = updatedAt,
                )
            } else {
                event
            }
        }
        return updated
    }

    override suspend fun deleteEvent(plantId: String, eventId: String): Int {
        deletedEvent = EventKey(plantId, eventId)
        val deleted = data.value.count { it.plantId == plantId && it.id == eventId }
        data.value = data.value.filterNot { it.plantId == plantId && it.id == eventId }
        return deleted
    }

    override suspend fun deleteEventsByType(plantId: String, careType: String): Int {
        deletedHistory = plantId to careType
        val deleted = data.value.count { it.plantId == plantId && it.careType == careType }
        data.value = data.value.filterNot { it.plantId == plantId && it.careType == careType }
        return deleted
    }
}

private data class CareEventUpdateCall(
    val plantId: String,
    val eventId: String,
    val careType: String,
    val performedAt: Long,
    val note: String?,
    val updatedAt: Long,
)

private data class EventKey(val plantId: String, val eventId: String)

private data class CareEventRecordCall(
    val dayStartInclusive: Long,
    val dayEndExclusive: Long,
    val performedEpochDay: Long,
)

private fun eventEntity() = CareEventEntity(
    id = "event-1",
    plantId = "plant-1",
    careType = CareType.WATERING.name,
    performedAt = 1_700_000_000_000L,
    note = "Initial watering",
    createdAt = 1_700_000_000_000L,
    updatedAt = 1_700_000_100_000L,
)

private fun reminderRule(nextDueEpochDay: Long) = ReminderRuleEntity(
    id = "rule-1",
    plantId = "plant-1",
    careType = CareType.WATERING.name,
    scheduleType = "INTERVAL",
    enabled = true,
    intervalDays = 7,
    nextDueEpochDay = nextDueEpochDay,
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

private object NoOpCareNotificationGateway : ReminderNotificationGateway {
    override fun ensureChannel() = Unit
    override fun canPostNotifications(): Boolean = true
    override fun publish(delivery: PendingReminderDelivery): ReminderNotificationResult =
        ReminderNotificationResult.POSTED
    override fun cancel(noticeId: String) = Unit
}

private fun CareEventEntity.toModelForTest() = com.miguelrodriguez19.stayleafy.model.CareEvent(
    id = id,
    plantId = plantId,
    careType = CareType.valueOf(careType),
    performedAt = performedAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
