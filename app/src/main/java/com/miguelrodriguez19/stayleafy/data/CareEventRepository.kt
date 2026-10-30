package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventCreateResult
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator

interface CareEventRepository {
    fun observeEvents(plantId: String): Flow<List<CareEvent>>

    fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?>

    suspend fun createEvent(plantId: String, draft: CareEventDraft): CareEventCreateResult

    suspend fun recordBulkCare(
        plantIds: List<String>,
        careType: CareType,
        performedEpochDay: Long,
    ): BulkCareResult

    suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft)

    suspend fun deleteEvent(plantId: String, eventId: String)

    suspend fun deleteEventsByType(plantId: String, careType: CareType)
}

class DefaultCareEventRepository @Inject constructor(
    private val careEventDao: CareEventDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val notificationGateway: ReminderNotificationGateway,
    private val operations: DataOperationCoordinator = DataOperationCoordinator(),
) : CareEventRepository {

    override fun observeEvents(plantId: String): Flow<List<CareEvent>> =
        careEventDao.observeEvents(plantId).map { events -> events.map(CareEventEntity::toModel) }

    override fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?> =
        careEventDao.observeEvent(plantId, eventId).map { event -> event?.toModel() }

    override suspend fun createEvent(
        plantId: String,
        draft: CareEventDraft,
    ): CareEventCreateResult = operations.withOperation {
        val now = epochMillisProvider.now()
        require(draft.performedAt <= now) { "performedAt must not be in the future" }
        val zoneId = zoneIdProvider.currentZoneId()
        val performedEpochDay = epochDayAt(draft.performedAt, zoneId)
        val dayWindow = localDayWindow(performedEpochDay, zoneId)

        val id = uuidProvider.newUuid()
        val event = CareEventEntity(
            id = id,
            plantId = plantId,
            careType = draft.careType.name,
            performedAt = draft.performedAt,
            note = draft.note.normalizeNullable(),
            createdAt = now,
            updatedAt = now,
        )
        val result = careEventDao.recordEventsIfNotRecorded(
            events = listOf(event),
            dayStartInclusive = dayWindow.startInclusive,
            dayEndExclusive = dayWindow.endExclusive,
            performedEpochDay = performedEpochDay,
        )
        cancelResolvedNotifications(result.resolvedNoticeIds)
        if (result.recordedPlantIds.isEmpty()) {
            CareEventCreateResult.AlreadyRecorded
        } else {
            CareEventCreateResult.Created(id)
        }
    }

    override suspend fun recordBulkCare(
        plantIds: List<String>,
        careType: CareType,
        performedEpochDay: Long,
    ): BulkCareResult = operations.withOperation {
        val uniquePlantIds = plantIds.distinct()
        require(uniquePlantIds.isNotEmpty()) { "At least one plant id is required" }

        val now = epochMillisProvider.now()
        val zoneId = zoneIdProvider.currentZoneId()
        val nowZoned = Instant.ofEpochMilli(now).atZone(zoneId)
        val todayEpochDay = nowZoned.toLocalDate().toEpochDay()
        require(performedEpochDay <= todayEpochDay) { "performedEpochDay must not be in the future" }

        val performedAt = if (performedEpochDay == todayEpochDay) {
            now
        } else {
            LocalDate.ofEpochDay(performedEpochDay)
                .atTime(nowZoned.toLocalTime())
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }
        val dayWindow = localDayWindow(performedEpochDay, zoneId)
        val events = uniquePlantIds.map { plantId ->
            CareEventEntity(
                id = uuidProvider.newUuid(),
                plantId = plantId,
                careType = careType.name,
                performedAt = performedAt,
                note = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        val result = careEventDao.recordEventsIfNotRecorded(
            events = events,
            dayStartInclusive = dayWindow.startInclusive,
            dayEndExclusive = dayWindow.endExclusive,
            performedEpochDay = performedEpochDay,
        )
        cancelResolvedNotifications(result.resolvedNoticeIds)
        BulkCareResult(
            recordedPlantIds = result.recordedPlantIds,
            skippedPlantIds = result.skippedPlantIds,
        )
    }

    override suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft): Unit = operations.withOperation {
        val now = epochMillisProvider.now()
        require(draft.performedAt <= now) { "performedAt must not be in the future" }

        val updatedRows = careEventDao.updateEvent(
            plantId = plantId,
            eventId = eventId,
            careType = draft.careType.name,
            performedAt = draft.performedAt,
            note = draft.note.normalizeNullable(),
            updatedAt = now,
        )
        check(updatedRows == 1) { "Care event no longer exists" }
    }

    override suspend fun deleteEvent(plantId: String, eventId: String): Unit = operations.withOperation {
        val deletedRows = careEventDao.deleteEvent(plantId, eventId)
        check(deletedRows == 1) { "Care event no longer exists" }
    }

    override suspend fun deleteEventsByType(plantId: String, careType: CareType): Unit = operations.withOperation {
        careEventDao.deleteEventsByType(plantId, careType.name)
    }

    private fun cancelResolvedNotifications(noticeIds: List<String>) {
        noticeIds.forEach { noticeId -> runCatching { notificationGateway.cancel(noticeId) } }
    }
}

private data class LocalDayWindow(val startInclusive: Long, val endExclusive: Long)

private fun localDayWindow(epochDay: Long, zoneId: ZoneId): LocalDayWindow {
    val day = LocalDate.ofEpochDay(epochDay)
    return LocalDayWindow(
        startInclusive = day.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        endExclusive = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
    )
}

private fun String?.normalizeNullable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun CareEventEntity.toModel(): CareEvent =
    CareEvent(
        id = id,
        plantId = plantId,
        careType = CareType.valueOf(careType),
        performedAt = performedAt,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
