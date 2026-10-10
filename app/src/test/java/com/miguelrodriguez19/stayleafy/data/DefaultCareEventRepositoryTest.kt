package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
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

        val id = repository.createEvent(
            plantId = "plant-1",
            draft = CareEventDraft(
                careType = CareType.WATERING,
                performedAt = fixedNow - 1_000,
                note = "  Deep watering.  ",
            ),
        )

        assertEquals(fixedUuid, id)
        assertEquals(
            CareEventEntity(
                id = id,
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
    ) = DefaultCareEventRepository(
        careEventDao = dao,
        epochMillisProvider = FixedCareEventEpochMillisProvider(fixedNow),
        uuidProvider = uuidProvider,
    )
}

private class FixedCareEventEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    override fun now(): Long = value
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

private class RecordingCareEventDao(initialEvents: List<CareEventEntity> = emptyList()) : CareEventDao {
    private val data = MutableStateFlow(initialEvents)

    var insertedEvent: CareEventEntity? = null
        private set
    var updateCall: CareEventUpdateCall? = null
        private set
    var deletedEvent: EventKey? = null
        private set
    var lastObservedPlantId: String? = null
        private set
    var lastObservedEvent: EventKey? = null
        private set

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

    override suspend fun insertEvent(event: CareEventEntity) {
        insertedEvent = event
        data.value = data.value + event
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

private fun eventEntity() = CareEventEntity(
    id = "event-1",
    plantId = "plant-1",
    careType = CareType.WATERING.name,
    performedAt = 1_700_000_000_000L,
    note = "Initial watering",
    createdAt = 1_700_000_000_000L,
    updatedAt = 1_700_000_100_000L,
)

private fun CareEventEntity.toModelForTest() = com.miguelrodriguez19.stayleafy.model.CareEvent(
    id = id,
    plantId = plantId,
    careType = CareType.valueOf(careType),
    performedAt = performedAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
