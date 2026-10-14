package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.NoticeDao
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeRecord
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.model.ReminderScheduleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface NoticeRepository {
    fun observeNotices(): Flow<List<AppNotice>>

    fun observeNotice(id: String): Flow<AppNotice?>

    fun observeUnreadCount(): Flow<Int>

    suspend fun createPendingNotice(ruleId: String): String?

    suspend fun setDeliveryState(id: String, state: NoticeDeliveryState)

    suspend fun markRead(id: String)

    suspend fun markAllRead()
}

class DefaultNoticeRepository @Inject constructor(
    private val noticeDao: NoticeDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
) : NoticeRepository {
    override fun observeNotices(): Flow<List<AppNotice>> =
        noticeDao.observeNotices().map { notices -> notices.map(NoticeRecord::toModel) }

    override fun observeNotice(id: String): Flow<AppNotice?> =
        noticeDao.observeNotice(id).map { notice -> notice?.toModel() }

    override fun observeUnreadCount(): Flow<Int> = noticeDao.observeUnreadCount()

    override suspend fun createPendingNotice(ruleId: String): String? {
        val id = uuidProvider.newUuid()
        val inserted = noticeDao.insertPendingNotice(
            id = id,
            ruleId = ruleId,
            createdAt = epochMillisProvider.now(),
            deliveryState = NoticeDeliveryState.PENDING.name,
        )
        return id.takeIf { inserted }
    }

    override suspend fun setDeliveryState(id: String, state: NoticeDeliveryState) {
        check(noticeDao.updateDeliveryState(id, state.name) == 1) { "Notice no longer exists" }
    }

    override suspend fun markRead(id: String) {
        noticeDao.markRead(id, epochMillisProvider.now())
    }

    override suspend fun markAllRead() {
        noticeDao.markAllRead(epochMillisProvider.now())
    }
}

private fun NoticeRecord.toModel(): AppNotice = AppNotice(
    id = id,
    ruleId = ruleId,
    plantId = plantId,
    plantName = plantName,
    plantPhotoPath = plantPhotoPath,
    careType = CareType.valueOf(careType),
    scheduleType = ReminderScheduleType.valueOf(scheduleType),
    dueEpochDay = dueEpochDay,
    createdAt = createdAt,
    readAt = readAt,
    deliveryState = NoticeDeliveryState.valueOf(deliveryState),
)
