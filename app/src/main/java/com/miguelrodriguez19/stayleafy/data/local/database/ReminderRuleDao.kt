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
        WHERE plantId = :plantId AND scheduleType = 'INTERVAL'
        ORDER BY careType ASC
        """,
    )
    suspend fun getRules(plantId: String): List<ReminderRuleEntity>

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

    @Transaction
    suspend fun applyChanges(changes: List<ReminderRuleChange>, changedAt: Long) {
        changes.forEach { change ->
            if (change.resolveCurrentOccurrence) {
                markUnreadNoticesRead(change.entity.id, changedAt)
            }
            upsertRule(change.entity)
        }
    }
}

data class ReminderRuleChange(
    val entity: ReminderRuleEntity,
    val resolveCurrentOccurrence: Boolean,
)
