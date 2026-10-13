package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoticeDao {
    @Query(
        """
        SELECT
            notices.id AS id,
            notices.ruleId AS ruleId,
            notices.occurrenceKey AS occurrenceKey,
            reminder_rules.plantId AS plantId,
            plants.name AS plantName,
            notices.careType AS careType,
            reminder_rules.scheduleType AS scheduleType,
            notices.dueEpochDay AS dueEpochDay,
            notices.createdAt AS createdAt,
            notices.readAt AS readAt,
            notices.deliveryState AS deliveryState
        FROM notices
        INNER JOIN reminder_rules ON reminder_rules.id = notices.ruleId
        INNER JOIN plants ON plants.id = reminder_rules.plantId
        ORDER BY notices.createdAt DESC, notices.id DESC
        """,
    )
    fun observeNotices(): Flow<List<NoticeRecord>>

    @Query(
        """
        SELECT
            notices.id AS id,
            notices.ruleId AS ruleId,
            notices.occurrenceKey AS occurrenceKey,
            reminder_rules.plantId AS plantId,
            plants.name AS plantName,
            notices.careType AS careType,
            reminder_rules.scheduleType AS scheduleType,
            notices.dueEpochDay AS dueEpochDay,
            notices.createdAt AS createdAt,
            notices.readAt AS readAt,
            notices.deliveryState AS deliveryState
        FROM notices
        INNER JOIN reminder_rules ON reminder_rules.id = notices.ruleId
        INNER JOIN plants ON plants.id = reminder_rules.plantId
        WHERE notices.id = :noticeId
        """,
    )
    fun observeNotice(noticeId: String): Flow<NoticeRecord?>

    @Query("SELECT COUNT(*) FROM notices WHERE readAt IS NULL")
    fun observeUnreadCount(): Flow<Int>

    @Query(
        """
        SELECT
            notices.id AS id,
            notices.ruleId AS ruleId,
            notices.occurrenceKey AS occurrenceKey,
            reminder_rules.plantId AS plantId,
            plants.name AS plantName,
            notices.careType AS careType,
            reminder_rules.scheduleType AS scheduleType,
            notices.dueEpochDay AS dueEpochDay,
            notices.createdAt AS createdAt,
            notices.readAt AS readAt,
            notices.deliveryState AS deliveryState
        FROM notices
        INNER JOIN reminder_rules ON reminder_rules.id = notices.ruleId
        INNER JOIN plants ON plants.id = reminder_rules.plantId
        WHERE notices.deliveryState = 'PENDING'
          AND notices.readAt IS NULL
          AND reminder_rules.enabled = 1
          AND (
            (reminder_rules.scheduleType = 'INTERVAL' AND reminder_rules.nextDueEpochDay = notices.dueEpochDay)
            OR reminder_rules.scheduleType = 'SEASONAL'
          )
        ORDER BY notices.createdAt ASC, notices.id ASC
        """,
    )
    suspend fun getPendingNotices(): List<NoticeRecord>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotice(notice: NoticeEntity): Long

    @Query("SELECT * FROM reminder_rules WHERE id = :ruleId AND enabled = 1")
    suspend fun getEnabledRule(ruleId: String): ReminderRuleEntity?

    @Transaction
    suspend fun insertPendingNotice(
        id: String,
        ruleId: String,
        createdAt: Long,
        deliveryState: String,
    ): Boolean {
        val rule = getEnabledRule(ruleId) ?: return false
        if (rule.scheduleType != INTERVAL_SCHEDULE_TYPE) return false
        val due = requireNotNull(rule.nextDueEpochDay)
        return insertNotice(
            NoticeEntity(
                id = id,
                ruleId = rule.id,
                occurrenceKey = "interval:${rule.id}:$due",
                careType = rule.careType,
                dueEpochDay = due,
                createdAt = createdAt,
                readAt = null,
                deliveryState = deliveryState,
            ),
        ) != -1L
    }

    @Query("UPDATE notices SET readAt = :readAt WHERE id = :noticeId AND readAt IS NULL")
    suspend fun markRead(noticeId: String, readAt: Long): Int

    @Query("UPDATE notices SET readAt = :readAt WHERE readAt IS NULL")
    suspend fun markAllRead(readAt: Long): Int

    @Query("UPDATE notices SET deliveryState = :deliveryState WHERE id = :noticeId")
    suspend fun updateDeliveryState(noticeId: String, deliveryState: String): Int
}
