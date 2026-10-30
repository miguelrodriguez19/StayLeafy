package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.seasonalOccurrenceContaining
import kotlinx.coroutines.flow.Flow

@Dao
interface CareEventDao {
    @Query(
        """
        SELECT * FROM care_events
        WHERE plantId = :plantId
        ORDER BY performedAt DESC, id DESC
        """,
    )
    fun observeEvents(plantId: String): Flow<List<CareEventEntity>>

    @Query("SELECT * FROM care_events WHERE plantId = :plantId AND id = :eventId")
    fun observeEvent(plantId: String, eventId: String): Flow<CareEventEntity?>

    @Query("SELECT id FROM plants WHERE id IN (:plantIds)")
    suspend fun getExistingPlantIds(plantIds: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: CareEventEntity)

    @Query(
        """
        SELECT MAX(performedAt) FROM care_events
        WHERE plantId = :plantId AND careType = :careType
        """,
    )
    suspend fun getLatestPerformedAt(plantId: String, careType: String): Long?

    @Query(
        """
        SELECT COUNT(*) FROM care_events
        WHERE plantId = :plantId AND careType = :careType
          AND performedAt >= :startInclusive AND performedAt < :endExclusive
        """,
    )
    suspend fun countEventsInWindow(
        plantId: String,
        careType: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Int

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId AND careType = :careType AND scheduleType = 'INTERVAL'
          AND enabled = 1
        LIMIT 1
        """,
    )
    suspend fun getIntervalRule(plantId: String, careType: String): ReminderRuleEntity?

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId AND careType = :careType
          AND scheduleType = 'SEASONAL' AND enabled = 1
        LIMIT 1
        """,
    )
    suspend fun getSeasonalRule(plantId: String, careType: String): ReminderRuleEntity?

    @Query(
        """
        UPDATE reminder_rules
        SET nextDueEpochDay = :nextDueEpochDay, updatedAt = :updatedAt
        WHERE id = :ruleId
        """,
    )
    suspend fun updateRuleDueDate(ruleId: String, nextDueEpochDay: Long, updatedAt: Long): Int

    @Query(
        """
        UPDATE notices
        SET readAt = :readAt
        WHERE ruleId = :ruleId AND readAt IS NULL
        """,
    )
    suspend fun markRuleNoticesRead(ruleId: String, readAt: Long): Int

    @Query("SELECT id FROM notices WHERE ruleId = :ruleId AND readAt IS NULL")
    suspend fun getUnreadNoticeIds(ruleId: String): List<String>

    @Query(
        """
        UPDATE notices
        SET readAt = :readAt
        WHERE occurrenceKey = :occurrenceKey AND readAt IS NULL
        """,
    )
    suspend fun markOccurrenceNoticeRead(occurrenceKey: String, readAt: Long): Int

    @Query("SELECT id FROM notices WHERE occurrenceKey = :occurrenceKey AND readAt IS NULL")
    suspend fun getUnreadOccurrenceNoticeIds(occurrenceKey: String): List<String>

    @Transaction
    suspend fun recordEventsIfNotRecorded(
        events: List<CareEventEntity>,
        dayStartInclusive: Long,
        dayEndExclusive: Long,
        performedEpochDay: Long,
    ): CareEventDaoWriteResult {
        require(events.isNotEmpty()) { "At least one care event is required" }
        require(dayStartInclusive < dayEndExclusive) { "Care day must have a positive duration" }
        require(events.map(CareEventEntity::plantId).distinct().size == events.size) {
            "Care event plant ids must be unique"
        }

        val plantIds = events.map(CareEventEntity::plantId)
        val existingPlantIds = getExistingPlantIds(plantIds).toSet()
        val missingPlantIds = plantIds.filterNot(existingPlantIds::contains)
        require(missingPlantIds.isEmpty()) { "Plants do not exist: ${missingPlantIds.joinToString()}" }

        val recordedPlantIds = mutableListOf<String>()
        val skippedPlantIds = mutableListOf<String>()
        val resolvedNoticeIds = mutableListOf<String>()

        events.forEach { event ->
            if (countEventsInWindow(
                    plantId = event.plantId,
                    careType = event.careType,
                    startInclusive = dayStartInclusive,
                    endExclusive = dayEndExclusive,
                ) > 0
            ) {
                skippedPlantIds += event.plantId
            } else {
                val latestBeforeInsert = getLatestPerformedAt(event.plantId, event.careType)
                insertEvent(event)
                recordedPlantIds += event.plantId
                if (latestBeforeInsert == null || event.performedAt >= latestBeforeInsert) {
                    getIntervalRule(event.plantId, event.careType)?.let { rule ->
                        val intervalDays = requireNotNull(rule.intervalDays)
                        resolvedNoticeIds += getUnreadNoticeIds(rule.id)
                        markRuleNoticesRead(rule.id, event.updatedAt)
                        updateRuleDueDate(
                            ruleId = rule.id,
                            nextDueEpochDay = Math.addExact(performedEpochDay, intervalDays.toLong()),
                            updatedAt = event.updatedAt,
                        )
                    }
                }

                getSeasonalRule(event.plantId, event.careType)?.let { rule ->
                    val start = AnnualDate(
                        requireNotNull(rule.seasonStartMonth),
                        requireNotNull(rule.seasonStartDay),
                    )
                    val end = rule.seasonEndMonth?.let { month ->
                        AnnualDate(month, requireNotNull(rule.seasonEndDay))
                    }
                    seasonalOccurrenceContaining(performedEpochDay, start, end)?.let { occurrence ->
                        val key = "seasonal:${rule.id}:${occurrence.startEpochDay}"
                        resolvedNoticeIds += getUnreadOccurrenceNoticeIds(key)
                        markOccurrenceNoticeRead(key, event.updatedAt)
                    }
                }
            }
        }
        return CareEventDaoWriteResult(
            recordedPlantIds = recordedPlantIds,
            skippedPlantIds = skippedPlantIds,
            resolvedNoticeIds = resolvedNoticeIds.distinct(),
        )
    }

    @Query(
        """
        UPDATE care_events
        SET careType = :careType,
            performedAt = :performedAt,
            note = :note,
            updatedAt = :updatedAt
        WHERE plantId = :plantId AND id = :eventId
        """,
    )
    suspend fun updateEvent(
        plantId: String,
        eventId: String,
        careType: String,
        performedAt: Long,
        note: String?,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM care_events WHERE plantId = :plantId AND id = :eventId")
    suspend fun deleteEvent(plantId: String, eventId: String): Int

    @Query("DELETE FROM care_events WHERE plantId = :plantId AND careType = :careType")
    suspend fun deleteEventsByType(plantId: String, careType: String): Int
}

data class CareEventDaoWriteResult(
    val recordedPlantIds: List<String>,
    val skippedPlantIds: List<String>,
    val resolvedNoticeIds: List<String>,
)
