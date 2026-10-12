package com.miguelrodriguez19.stayleafy.platform.reminder

import androidx.room.withTransaction
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeDao
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeRecord
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleDao
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReminderDeliveryStore @Inject constructor(
    private val database: AppDatabase,
    private val reminderRuleDao: ReminderRuleDao,
    private val noticeDao: NoticeDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
) : ReminderDeliveryStore {

    override suspend fun preparePendingDeliveries(todayEpochDay: Long): List<PendingReminderDelivery> =
        database.withTransaction {
            val createdAt = epochMillisProvider.now()
            reminderRuleDao.getDueRules(todayEpochDay).forEach { rule ->
                noticeDao.insertNotice(
                    NoticeEntity(
                        id = uuidProvider.newUuid(),
                        ruleId = rule.id,
                        occurrenceKey = "interval:${rule.id}:${rule.nextDueEpochDay}",
                        careType = rule.careType,
                        dueEpochDay = rule.nextDueEpochDay,
                        createdAt = createdAt,
                        readAt = null,
                        deliveryState = NoticeDeliveryState.PENDING.name,
                    ),
                )
            }
            noticeDao.getPendingNotices().map(NoticeRecord::toPendingDelivery)
        }

    override suspend fun markDelivered(noticeId: String, deliveredAtEpochMillis: Long) {
        check(
            noticeDao.updateDeliveryState(noticeId, NoticeDeliveryState.DELIVERED.name) == 1,
        ) { "Notice no longer exists" }
    }

    override suspend fun markSuppressed(noticeId: String, suppressedAtEpochMillis: Long) {
        check(
            noticeDao.updateDeliveryState(noticeId, NoticeDeliveryState.SUPPRESSED.name) == 1,
        ) { "Notice no longer exists" }
    }
}

private fun NoticeRecord.toPendingDelivery(): PendingReminderDelivery = PendingReminderDelivery(
    noticeId = id,
    occurrenceKey = "interval:$ruleId:$dueEpochDay",
    plantId = plantId,
    plantName = plantName,
    careType = CareType.valueOf(careType),
    dueEpochDay = dueEpochDay,
)
