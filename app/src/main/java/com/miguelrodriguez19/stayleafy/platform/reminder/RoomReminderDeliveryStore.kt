package com.miguelrodriguez19.stayleafy.platform.reminder

import androidx.room.withTransaction
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeDao
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeRecord
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleDao
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.model.ReminderScheduleType
import com.miguelrodriguez19.stayleafy.model.seasonalOccurrenceContaining
import com.miguelrodriguez19.stayleafy.data.optionalSeasonEnd
import com.miguelrodriguez19.stayleafy.data.requireSeasonStart
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReminderDeliveryStore @Inject constructor(
    private val database: AppDatabase,
    private val reminderRuleDao: ReminderRuleDao,
    private val careEventDao: CareEventDao,
    private val noticeDao: NoticeDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
    private val zoneIdProvider: ZoneIdProvider,
) : ReminderDeliveryStore {

    override suspend fun preparePendingDeliveries(todayEpochDay: Long): List<PendingReminderDelivery> =
        database.withTransaction {
            val createdAt = epochMillisProvider.now()
            reminderRuleDao.getDueRules(todayEpochDay).forEach { rule ->
                val due = requireNotNull(rule.nextDueEpochDay)
                noticeDao.insertNotice(
                    NoticeEntity(
                        id = uuidProvider.newUuid(),
                        ruleId = rule.id,
                        occurrenceKey = "interval:${rule.id}:$due",
                        careType = rule.careType,
                        dueEpochDay = due,
                        createdAt = createdAt,
                        readAt = null,
                        deliveryState = NoticeDeliveryState.PENDING.name,
                    ),
                )
            }
            val zoneId = zoneIdProvider.currentZoneId()
            val activeSeasonalOccurrenceKeys = mutableSetOf<String>()
            reminderRuleDao.getEnabledSeasonalRules().forEach { rule ->
                val occurrence = seasonalOccurrenceContaining(
                    todayEpochDay,
                    rule.requireSeasonStart(),
                    rule.optionalSeasonEnd(),
                ) ?: return@forEach
                val occurrenceKey = "seasonal:${rule.id}:${occurrence.startEpochDay}"
                activeSeasonalOccurrenceKeys += occurrenceKey
                val startMillis = LocalDate.ofEpochDay(occurrence.startEpochDay)
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endExclusiveMillis = LocalDate.ofEpochDay(occurrence.endEpochDay)
                    .plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val satisfied = careEventDao.countEventsInWindow(
                    rule.plantId,
                    rule.careType,
                    startMillis,
                    endExclusiveMillis,
                ) > 0
                if (!satisfied) {
                    noticeDao.insertNotice(
                        NoticeEntity(
                            id = uuidProvider.newUuid(),
                            ruleId = rule.id,
                            occurrenceKey = occurrenceKey,
                            careType = rule.careType,
                            dueEpochDay = occurrence.startEpochDay,
                            createdAt = createdAt,
                            readAt = null,
                            deliveryState = NoticeDeliveryState.PENDING.name,
                        ),
                    )
                }
            }
            val pending = noticeDao.getPendingNotices()
            pending.filter { record ->
                record.scheduleType == ReminderScheduleType.SEASONAL.name &&
                    record.dueEpochDay <= todayEpochDay &&
                    record.occurrenceKey !in activeSeasonalOccurrenceKeys
            }.forEach { record ->
                noticeDao.updateDeliveryState(record.id, NoticeDeliveryState.SUPPRESSED.name)
            }
            pending.filter { record ->
                record.dueEpochDay <= todayEpochDay &&
                    (record.scheduleType != ReminderScheduleType.SEASONAL.name ||
                        record.occurrenceKey in activeSeasonalOccurrenceKeys)
            }.map(NoticeRecord::toPendingDelivery)
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
    occurrenceKey = occurrenceKey,
    plantId = plantId,
    plantName = plantName,
    careType = CareType.valueOf(careType),
    scheduleType = ReminderScheduleType.valueOf(scheduleType),
    dueEpochDay = dueEpochDay,
)
