package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderRuleDao {
    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId AND scheduleType = 'INTERVAL'
        ORDER BY careType ASC
        """,
    )
    fun observeRules(plantId: String): Flow<List<ReminderRuleEntity>>

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId AND scheduleType = 'SEASONAL'
        ORDER BY careType ASC
        """,
    )
    fun observeSeasonalRules(plantId: String): Flow<List<ReminderRuleEntity>>

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId AND scheduleType = 'INTERVAL'
        ORDER BY careType ASC
        """,
    )
    suspend fun getRules(plantId: String): List<ReminderRuleEntity>

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE plantId = :plantId
        ORDER BY careType ASC, scheduleType ASC
        """,
    )
    suspend fun getAllRules(plantId: String): List<ReminderRuleEntity>

    @Query("SELECT * FROM reminder_rules WHERE id = :ruleId")
    suspend fun getRule(ruleId: String): ReminderRuleEntity?

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE enabled = 1 AND scheduleType = 'INTERVAL'
        ORDER BY nextDueEpochDay ASC, careType ASC, id ASC
        """,
    )
    fun observeEnabledRules(): Flow<List<ReminderRuleEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM reminder_rules WHERE enabled = 1)")
    fun observeHasEnabledRules(): Flow<Boolean>

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE enabled = 1
          AND scheduleType = 'INTERVAL'
          AND nextDueEpochDay <= :todayEpochDay
        ORDER BY nextDueEpochDay ASC, careType ASC, id ASC
        """,
    )
    suspend fun getDueRules(todayEpochDay: Long): List<ReminderRuleEntity>

    @Query(
        """
        SELECT * FROM reminder_rules
        WHERE enabled = 1 AND scheduleType = 'SEASONAL'
        ORDER BY careType ASC, id ASC
        """,
    )
    suspend fun getEnabledSeasonalRules(): List<ReminderRuleEntity>

    @Upsert
    suspend fun upsertRule(rule: ReminderRuleEntity)

    @Query(
        """
        UPDATE notices
        SET readAt = :readAt
        WHERE ruleId = :ruleId AND readAt IS NULL
        """,
    )
    suspend fun markUnreadNoticesRead(ruleId: String, readAt: Long): Int

    @Query("SELECT id FROM notices WHERE ruleId = :ruleId AND readAt IS NULL")
    suspend fun getUnreadNoticeIds(ruleId: String): List<String>

    @Transaction
    suspend fun applyChanges(changes: List<ReminderRuleChange>, changedAt: Long): List<String> {
        val resolvedNoticeIds = mutableListOf<String>()
        changes.forEach { change ->
            if (change.resolveCurrentOccurrence) {
                resolvedNoticeIds += getUnreadNoticeIds(change.entity.id)
                markUnreadNoticesRead(change.entity.id, changedAt)
            }
            upsertRule(change.entity)
        }
        return resolvedNoticeIds.distinct()
    }
}

data class ReminderRuleChange(
    val entity: ReminderRuleEntity,
    val resolveCurrentOccurrence: Boolean,
)
