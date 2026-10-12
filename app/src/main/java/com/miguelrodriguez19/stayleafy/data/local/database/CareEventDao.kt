package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId AND careType = :careType AND scheduleType = 'INTERVAL'
        LIMIT 1
        """,
    )
    suspend fun getIntervalRule(plantId: String, careType: String): ReminderRuleEntity?

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

    @Transaction
    suspend fun insertEventAndAdvanceReminder(event: CareEventEntity, performedEpochDay: Long) {
        val latestBeforeInsert = getLatestPerformedAt(event.plantId, event.careType)
        insertEvent(event)
        if (latestBeforeInsert != null && event.performedAt < latestBeforeInsert) return

        val rule = getIntervalRule(event.plantId, event.careType) ?: return
        markRuleNoticesRead(rule.id, event.updatedAt)
        updateRuleDueDate(
            ruleId = rule.id,
            nextDueEpochDay = Math.addExact(performedEpochDay, rule.intervalDays.toLong()),
            updatedAt = event.updatedAt,
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
